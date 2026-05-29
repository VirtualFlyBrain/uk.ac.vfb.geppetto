package uk.ac.vfb.geppetto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.GeppettoSerializer;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DatasourcesFactory;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.datasources.SerializableQueryResult;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;

/**
 * Converts the QueryResults emitted by VFBqueryResponseProcessor (one row per
 * VFBquery `rows` entry, values still typed as Object and addressed by column
 * header name) into the SerializableQueryResult shape the geppetto-vfb
 * frontend expects (string-formatted cells, composite IDs for connectivity
 * tables, synthesised "queried term" column where the v2 chain produced both
 * Upstream_Class and Downstream_Class regardless of direction).
 *
 * Output shape is byte-equivalent to the existing SOLRQueryProcessor output
 * for hasClassConnectivity, so no frontend change is needed for the pilot.
 *
 * For non-connectivity VFBquery responses this processor degrades to a
 * straight Object-to-String pass-through of every column in header order,
 * which is the right behaviour for the Shape-A single-step migrations that
 * come next.
 *
 * Value access deliberately mirrors SOLRQueryProcessor.process(): iterate by
 * row index and pull each cell via results.getValue(headerName, rowIdx),
 * rather than calling .getValues() on the AQueryResult loop variable (which
 * the abstract parent does not expose).
 *
 * @author robertcourt
 */
public class VFBqueryJsonProcessor extends AQueryProcessor
{

	// Spacing here is significant: the geppetto-vfb Dockerfile runs a sed
	// 's@Boolean debug=.*;@Boolean debug=$DEBUG;@g' against this bundle on
	// dev builds. The pattern requires NO SPACES around the `=` — leave it.
	private Boolean debug=false;

	private static final String DELIM = "----";

	// VFBqueryResponseProcessor emits the API column id (the dict key) as the
	// QueryResults header. Below are the keys for class-connectivity columns.
	private static final String COL_ID = "id";
	private static final String COL_UPSTREAM = "upstream_class";
	private static final String COL_DOWNSTREAM = "downstream_class";
	private static final String COL_TOTAL_N = "total_n";
	private static final String COL_CONNECTED_N = "connected_n";
	private static final String COL_PERCENT = "percent_connected";
	private static final String COL_PAIRWISE = "pairwise_connections";
	private static final String COL_TOTAL_WEIGHT = "total_weight";
	private static final String COL_AVG_WEIGHT = "avg_weight";

	/**
	 * Mapping from VFBquery API field id (the dict key returned by the API)
	 * to the V2 frontend's expected backend column name — which matches the
	 * `displayName` values in queryBuilderConfiguration.js so the existing
	 * table renderer keeps its custom components, click handlers, sort
	 * direction and CSS classes.
	 *
	 * The frontend table renderer matches column headers against
	 * `displayName` (verified against the live behaviour where the legacy
	 * SOLR-blob pipeline emitted "Outputs"/"Inputs" headers and the config
	 * has `columnName: "downstream"`/`"upstream"` with those displayNames).
	 * So we emit the V2 displayName string as the header column name.
	 *
	 * For VFBquery API ids that already match a config columnName (id,
	 * upstream_class, downstream_class, total_n, …) we pass through. For
	 * names the V2 config doesn't know about (label, outputs, tags, etc.)
	 * we map to the closest existing legacy name.
	 */
	private static final Map<String, String> COL_HEADER_MAP = new HashMap<String, String>();
	static
	{
		// Identity / known matches
		COL_HEADER_MAP.put("id", "ID");
		COL_HEADER_MAP.put("upstream_class", "Upstream_Class");
		COL_HEADER_MAP.put("downstream_class", "Downstream_Class");
		COL_HEADER_MAP.put("total_n", "Total_N");
		COL_HEADER_MAP.put("connected_n", "Connected_N");
		COL_HEADER_MAP.put("percent_connected", "Percent_Connected");
		COL_HEADER_MAP.put("pairwise_connections", "Pairwise_Connections");
		COL_HEADER_MAP.put("total_weight", "Total_Weight");
		COL_HEADER_MAP.put("avg_weight", "Avg_Weight");
		COL_HEADER_MAP.put("region", "Region");
		COL_HEADER_MAP.put("score", "Score");
		// VFBquery -> legacy V2 aliases
		COL_HEADER_MAP.put("label", "Name");
		COL_HEADER_MAP.put("name", "Name");
		COL_HEADER_MAP.put("outputs", "Outputs");
		COL_HEADER_MAP.put("inputs", "Inputs");
		COL_HEADER_MAP.put("presynaptic_terminals", "Outputs");
		COL_HEADER_MAP.put("postsynaptic_terminals", "Inputs");
		COL_HEADER_MAP.put("tags", "Gross_Type");
		COL_HEADER_MAP.put("thumbnail", "Images");
		COL_HEADER_MAP.put("pubs", "Reference");
		COL_HEADER_MAP.put("publications", "Reference");
		COL_HEADER_MAP.put("partner_neuron", "Name");
		COL_HEADER_MAP.put("dataset", "Dataset");
		COL_HEADER_MAP.put("template", "Template_Space");
		COL_HEADER_MAP.put("cell_type", "Cell type");
		COL_HEADER_MAP.put("cluster", "Cluster");
		COL_HEADER_MAP.put("gene", "Gene");
		COL_HEADER_MAP.put("level", "Level");
		COL_HEADER_MAP.put("extent", "Extent");
		COL_HEADER_MAP.put("stage", "Stage");
		COL_HEADER_MAP.put("license", "License");
		COL_HEADER_MAP.put("technique", "Imaging_Technique");
		COL_HEADER_MAP.put("description", "Definition");
		COL_HEADER_MAP.put("definition", "Definition");

		// Lower-frequency fields that appear in specific queries — verified
		// against VFBquery/src/vfbquery/vfb_queries.py preview_columns lists
		// (the full set of column ids returned by any /run_query endpoint).
		COL_HEADER_MAP.put("anatomy", "Expressed_in");
		COL_HEADER_MAP.put("expression_level", "Level");
		COL_HEADER_MAP.put("expression_extent", "Extent");
		// Some VFBquery responses capitalise these column ids (ribbon-format
		// outputs like NeuronInputsTo). Map both cases since Java map lookup
		// is case-sensitive.
		COL_HEADER_MAP.put("Neurotransmitter", "Type");
		COL_HEADER_MAP.put("neurotransmitter", "Type");
		COL_HEADER_MAP.put("Weight", "Weight");
		COL_HEADER_MAP.put("weight", "Weight");
		// Additional V2 columnNames that may appear in less common queries.
		COL_HEADER_MAP.put("type", "Type");
		COL_HEADER_MAP.put("parent", "Parent");
		COL_HEADER_MAP.put("expressed_in", "Expressed_in");
		COL_HEADER_MAP.put("reference", "Reference");
		COL_HEADER_MAP.put("function", "Function");
		COL_HEADER_MAP.put("tbars", "Outputs (Tbars)");
		COL_HEADER_MAP.put("controls", "Controls");
		COL_HEADER_MAP.put("images", "Images");
		COL_HEADER_MAP.put("image_count", "Image_count");
		COL_HEADER_MAP.put("neuron_A", "Neuron_A");
		COL_HEADER_MAP.put("neuron_a", "Neuron_A");
		COL_HEADER_MAP.put("neuron_B", "Partner_Neuron");
		COL_HEADER_MAP.put("neuron_b", "Partner_Neuron");
		COL_HEADER_MAP.put("target", "Target");
		// preview_columns naming sometimes uses <entity>_label / <entity>_id
		// for the simpler queries (NeuronClassesFasciculatingHere etc.).
		// Map all *_label to Name and *_id to ID so they integrate with the
		// V2 frontend's name + selection_id columns.
		COL_HEADER_MAP.put("neuron_label", "Name");
		COL_HEADER_MAP.put("neuron_id", "ID");
		COL_HEADER_MAP.put("tract_label", "Name");
		COL_HEADER_MAP.put("tract_id", "ID");
		COL_HEADER_MAP.put("clone_label", "Name");
		COL_HEADER_MAP.put("clone_id", "ID");
	}

	private static String mapHeader(String apiId)
	{
		if (apiId == null) return "";
		String mapped = COL_HEADER_MAP.get(apiId);
		return mapped != null ? mapped : apiId;
	}

	private final Map<String, Object> processingOutputMap = new HashMap<String, Object>();

	/*
	 * (non-Javadoc)
	 *
	 * @see org.geppetto.core.datasources.IQueryProcessor#process(
	 *   org.geppetto.model.ProcessQuery,
	 *   org.geppetto.model.datasources.DataSource,
	 *   org.geppetto.model.variables.Variable,
	 *   org.geppetto.model.datasources.QueryResults,
	 *   org.geppetto.core.model.GeppettoModelAccess)
	 */
	@Override
	public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable,
			QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException
	{
		long t0 = System.currentTimeMillis();

		if (results == null)
		{
			throw new GeppettoDataSourceException("Null input QueryResults to " + (query != null ? query.getName() : "VFBqueryJsonProcessor"));
		}

		QueryResults out = DatasourcesFactory.eINSTANCE.createQueryResults();

		if (debug)
		{
			System.out.println("VFBqueryJsonProcessor.process: variable="
					+ (variable != null ? variable.getId() : "null")
					+ ", inputRows=" + results.getResults().size()
					+ ", inputHeader=" + results.getHeader());
		}

		// Detect connectivity-style responses by API column id. The upstream-class
		// VFBquery call returns headers [id, upstream_class, total_n, ...];
		// downstream returns [id, downstream_class, ...]. We synthesise the
		// missing column so output matches the v2 9-column shape.
		// (Headers are API ids — VFBqueryResponseProcessor emits the dict key
		// not the human title, so mapping is stable across server-side title
		// changes.)
		boolean isUpstreamCall = results.getHeader().contains(COL_UPSTREAM)
				&& !results.getHeader().contains(COL_DOWNSTREAM);
		boolean isDownstreamCall = results.getHeader().contains(COL_DOWNSTREAM)
				&& !results.getHeader().contains(COL_UPSTREAM);
		boolean isClassConnectivity = isUpstreamCall || isDownstreamCall;

		if (debug)
		{
			System.out.println("VFBqueryJsonProcessor.process: isUpstreamCall=" + isUpstreamCall
					+ ", isDownstreamCall=" + isDownstreamCall
					+ ", isClassConnectivity=" + isClassConnectivity);
		}

		if (isClassConnectivity)
		{
			buildClassConnectivityRows(variable, results, out, isUpstreamCall);
		}
		else
		{
			// Resolve the geppetto IMAGE type once per call so the generic path
			// can convert markdown-image cells into the JSON Variable form the
			// V2 frontend renders (matching SOLRQueryProcessor.java:1131-1146).
			// Unconditionally log the outcome — if this comes back null the
			// thumbnail column will silently downgrade to empty strings and the
			// only way to know is from the server log.
			Type imageType = null;
			try
			{
				imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
			}
			catch (Exception e)
			{
				System.out.println("VFBqueryJsonProcessor: getType(IMAGE_TYPE) threw " + e);
			}
			System.out.println("VFBqueryJsonProcessor.process: imageType="
					+ (imageType == null ? "null" : imageType.getId())
					+ ", header=" + results.getHeader());
			buildGenericRows(results, out, imageType);
		}

		if (debug)
		{
			System.out.println("VFBqueryJsonProcessor: " + out.getResults().size() + " rows in "
					+ (System.currentTimeMillis() - t0) + " ms (mode="
					+ (isClassConnectivity ? (isUpstreamCall ? "upstream-class" : "downstream-class") : "generic")
					+ ")");
		}

		return out;
	}

	/**
	 * Rebuild the v2 9-column class-connectivity table from the VFBquery
	 * one-direction response. The queried term occupies the column the API
	 * didn't return (Downstream_Class for an upstream query and vice versa).
	 *
	 * Output header order matches SOLRQueryProcessor.java:996-1005:
	 *   ID | Upstream_Class | Downstream_Class | Total_N | Connected_N |
	 *   Percent_Connected | Pairwise_Connections | Total_Weight | Avg_Weight
	 */
	private void buildClassConnectivityRows(Variable variable, QueryResults in, QueryResults out, boolean upstreamCall)
	{
		out.getHeader().add("ID");
		out.getHeader().add("Upstream_Class");
		out.getHeader().add("Downstream_Class");
		out.getHeader().add("Total_N");
		out.getHeader().add("Connected_N");
		out.getHeader().add("Percent_Connected");
		out.getHeader().add("Pairwise_Connections");
		out.getHeader().add("Total_Weight");
		out.getHeader().add("Avg_Weight");

		String queriedId = variable != null && variable.getId() != null ? variable.getId() : "";
		// V2 SOLRQueryProcessor emits PLAIN LABEL TEXT in the Upstream_Class /
		// Downstream_Class columns (SOLRQueryProcessor.java:1087-1088), not
		// markdown — the frontend reads the composite ID column
		// (upstream_id----downstream_id) and applies its own linking on top of
		// the plain label text. VFBquery's API returns the partner column as
		// markdown ("[label](id)"); strip that to the label only so the v2
		// frontend can link both label columns from the composite ID.
		// For the queried-term column we synthesise the label from the
		// Variable's Node.getName() (the human label, set when the term-info
		// processor created the variable). Fall back to the id if the name
		// is null/empty.
		String queriedName = variable != null ? variable.getName() : null;
		String queriedLabel = (queriedName != null && !queriedName.isEmpty()) ? queriedName : queriedId;

		String partnerColumn = upstreamCall ? COL_UPSTREAM : COL_DOWNSTREAM;

		int n = in.getResults().size();
		if (debug)
		{
			System.out.println("VFBqueryJsonProcessor.buildClassConnectivityRows: queriedId=" + queriedId
					+ ", partnerColumn=" + partnerColumn + ", n=" + n);
			if (n > 0)
			{
				System.out.println("VFBqueryJsonProcessor.buildClassConnectivityRows: first row probe -- "
						+ "ID=" + safeGetValue(in, COL_ID, 0)
						+ ", " + partnerColumn + "=" + safeGetValue(in, partnerColumn, 0)
						+ ", " + COL_TOTAL_N + "=" + safeGetValue(in, COL_TOTAL_N, 0));
			}
		}
		for (int i = 0; i < n; i++)
		{
			SerializableQueryResult r = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();

			String partnerId = stringValue(in, COL_ID, i);
			String partnerLabel = stripMarkdownLink(stringValue(in, partnerColumn, i));
			String upstreamId = upstreamCall ? partnerId : queriedId;
			String downstreamId = upstreamCall ? queriedId : partnerId;
			String upstreamLabel = upstreamCall ? partnerLabel : queriedLabel;
			String downstreamLabel = upstreamCall ? queriedLabel : partnerLabel;

			r.getValues().add(upstreamId + DELIM + downstreamId);
			r.getValues().add(upstreamLabel);
			r.getValues().add(downstreamLabel);
			r.getValues().add(formatInt(in, COL_TOTAL_N, i, 6));
			r.getValues().add(formatInt(in, COL_CONNECTED_N, i, 6));
			r.getValues().add(formatPercent(in, COL_PERCENT, i));
			r.getValues().add(formatInt(in, COL_PAIRWISE, i, 8));
			r.getValues().add(formatInt(in, COL_TOTAL_WEIGHT, i, 9));
			r.getValues().add(formatFloat(in, COL_AVG_WEIGHT, i, 7, 1));

			out.getResults().add(r);
		}
	}

	/**
	 * Generic value-to-String pass for non-connectivity VFBquery responses.
	 * Used by Shape-A migrations (single-step Cypher queries like neuron-
	 * neuron and neuron-region connectivity). Header titles are passed through
	 * unchanged.
	 *
	 * Per-cell formatting rules (duck-typed against the parsed JSON value):
	 *   - null               -> ""
	 *   - java.util.List     -> pipe-joined elements ("Adult|Nervous_system|...")
	 *                           matching the v2 SOLRQueryProcessor convention
	 *                           for tag columns (see SOLRQueryProcessor.java
	 *                           row.grossTypes()).
	 *   - java.lang.Number   -> integer string if the value is whole; else
	 *                           a plain Double.toString(). Avoids "2.0" /
	 *                           "31.0" in counter columns.
	 *   - anything else      -> Object.toString() pass-through (markdown,
	 *                           short_form ids, etc.).
	 */
	private void buildGenericRows(QueryResults in, QueryResults out, Type imageType)
	{
		// Map each input API-id header to its V2 legacy name so the frontend
		// table renderer (which matches against queryBuilderConfiguration.js
		// displayName entries) picks up the right customComponent, click
		// handlers, sort direction and CSS class for each column.
		// Unknown ids pass through unchanged so future VFBquery fields don't
		// disappear — they just render as plain text under their raw API name.
		for (String col : in.getHeader())
		{
			out.getHeader().add(mapHeader(col));
		}
		int n = in.getResults().size();
		for (int i = 0; i < n; i++)
		{
			SerializableQueryResult r = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
			for (String col : in.getHeader())
			{
				Object cellValue = safeGetValue(in, col, i);
				// Some VFBquery functions (PaintedDomains is the canonical
				// example) return `thumbnail` as a plain URL string rather
				// than the `[![alt](url 'alt')](ref)` markdown form that
				// formatGenericCell's image-cell branch knows how to handle.
				// SlideshowImageComponent JSON.parses every Images-column
				// cell unconditionally, so a raw URL crashes the whole page.
				// Synthesise the markdown wrapper here so the existing
				// formatGenericCell path can convert it cleanly. Uses the
				// row's id as the ref (template is parsed from the URL when
				// present — the canonical path is
				// .../data/VFB/i/XXXX/YYYY/VFB_template/thumbnail*.png).
				if (isThumbnailColumn(col) && cellValue instanceof String)
				{
					String urlOrMd = (String) cellValue;
					if (urlOrMd.length() > 0 && urlOrMd.charAt(0) != '[' && (urlOrMd.startsWith("http://") || urlOrMd.startsWith("https://")))
					{
						Object idObj = safeGetValue(in, COL_ID, i);
						String imageId = idObj != null ? idObj.toString() : "";
						cellValue = wrapPlainUrlAsImageMarkdown(urlOrMd, imageId);
					}
				}
				r.getValues().add(formatGenericCell(col, cellValue, imageType));
			}
			out.getResults().add(r);
		}
	}

	/**
	 * Some VFBquery columns map to V2 frontend custom components that expect a
	 * specific in-cell delimiter, which is NOT the same as the API's wire
	 * format. The "tags"/Gross_Type column is the canonical example:
	 * GrossTypeLabelsComponent.split(';') is hard-wired (matching
	 * SOLRQueryProcessor.grossTypes() which joins with "; "), but VFBquery
	 * emits tags pipe-joined. Without re-delimiting we get one giant chip
	 * instead of one chip per tag.
	 */
	private static boolean isTagsColumn(String apiCol)
	{
		return apiCol != null && (apiCol.equals("tags") || apiCol.equals("gross_type"));
	}

	private static boolean isThumbnailColumn(String apiCol)
	{
		return apiCol != null && apiCol.equals("thumbnail");
	}

	private static boolean isPubsColumn(String apiCol)
	{
		return apiCol != null && (apiCol.equals("pubs") || apiCol.equals("publications"));
	}

	/**
	 * Format VFBquery's `pubs` field — a List of dicts shaped as
	 *   {core: {iri, symbol, types, short_form, label}, FlyBase, PubMed, DOI}
	 * — into the form the V2 reference column expects: each pub's
	 * {@code core.label} joined by `"; "`, exactly matching
	 * SOLRQueryProcessor.reference() (the legacy v2 prod processor):
	 *
	 *   if (!result.equals("")) result += "; ";
	 *   result += pub.core.getName();
	 *
	 * The V2 Reference column's customComponent (QueryLinkArrayComponent)
	 * splits on `";"` per queryBuilderConfiguration.js:151, and falls back
	 * to plain-text rendering when the per-item value doesn't contain the
	 * entityDelimiter `"----"` — which is the case for both prod's output
	 * and ours. Matching this format gives v2-dev the same plain-text
	 * reference column styling as v2 prod.
	 *
	 * Defensive: if an element isn't a Map (unexpected shape), fall back to
	 * Object.toString() rather than dumping a HashMap.
	 */
	@SuppressWarnings("unchecked")
	private static String formatPubsList(List<?> pubs)
	{
		StringBuilder sb = new StringBuilder();
		for (Object e : pubs)
		{
			if (e == null) continue;
			String formatted = null;
			if (e instanceof Map)
			{
				Map<String, Object> pub = (Map<String, Object>) e;
				Object coreObj = pub.get("core");
				if (coreObj instanceof Map)
				{
					Map<String, Object> core = (Map<String, Object>) coreObj;
					Object labelObj = core.get("label");
					String label = labelObj != null ? labelObj.toString().trim() : "";
					if (label.length() > 0)
					{
						formatted = label;
					}
					else
					{
						Object symbolObj = core.get("symbol");
						String symbol = symbolObj != null ? symbolObj.toString().trim() : "";
						if (symbol.length() > 0)
						{
							formatted = symbol;
						}
					}
				}
			}
			if (formatted == null)
			{
				// Defensive — if the shape is unexpected, fall back to a
				// readable representation rather than dumping the dict.
				formatted = e.toString();
			}
			if (sb.length() > 0) sb.append("; ");
			sb.append(formatted);
		}
		return sb.toString();
	}

	/**
	 * Wrap a plain thumbnail URL in the canonical `[![alt](url 'alt')](ref)`
	 * markdown form so formatGenericCell can route it through the existing
	 * image-markdown → Variable JSON converter. The template short_form is
	 * parsed from the canonical VFB URL layout
	 * ({@code .../data/VFB/i/XXXX/YYYY/VFB_template/thumbnail*.png}); ref is
	 * built as {@code template,imageId} when found, otherwise just
	 * {@code imageId}. Always preserves the original URL.
	 */
	private static final Pattern VFB_THUMBNAIL_URL_TEMPLATE = Pattern.compile(".*?/i/[^/]+/[^/]+/+([^/]+)/+thumbnail[^/]*\\.(?:png|jpg|jpeg|gif)$");

	private static String wrapPlainUrlAsImageMarkdown(String url, String imageId)
	{
		String ref = imageId == null ? "" : imageId;
		Matcher m = VFB_THUMBNAIL_URL_TEMPLATE.matcher(url);
		if (m.matches())
		{
			String template = m.group(1);
			if (template != null && template.length() > 0)
			{
				ref = template + (imageId != null && imageId.length() > 0 ? ("," + imageId) : "");
			}
		}
		// Alt is empty — the V2 image card renderer falls back to the cell's
		// reference for tooltip text, and we have no human-readable label
		// for the thumbnail itself at this point in the row processing.
		return "[![](" + url + ")](" + ref + ")";
	}

	private static String formatGenericCell(String apiCol, Object v, Type imageType)
	{
		if (v == null)
		{
			return "";
		}
		// Tag/Gross_Type column: re-delimit to "; " so GrossTypeLabelsComponent
		// (split(';')) renders one chip per tag instead of one chip per row.
		// Handles both String input ("Adult|Nervous_system|..." — the VFBquery
		// API's wire format) and List input (defensive, if a future API
		// returns a JSON array).
		if (isTagsColumn(apiCol))
		{
			if (v instanceof List)
			{
				StringBuilder sb = new StringBuilder();
				for (Object e : (List<?>) v)
				{
					if (e == null) continue;
					if (sb.length() > 0) sb.append("; ");
					sb.append(e.toString());
				}
				return sb.toString();
			}
			return v.toString().replace("|", "; ");
		}
		// Publications column (`pubs` / `publications`): VFBquery returns a
		// List of nested dicts:
		//   [{core:{iri,symbol,types,short_form,label}, FlyBase, PubMed, DOI}, ...]
		// The generic List branch below would join them with `|` and rely on
		// Map.toString() per element, producing the unreadable
		// "{core={iri=..., ...}, FlyBase=..., PubMed=..., DOI=...}" output
		// seen on TransgeneExpressionHere's Reference column.
		// Match SOLRQueryProcessor.reference() exactly: emit plain `core.label`
		// (or `core.symbol` fallback) joined by `"; "` — V2's Reference column
		// custom component (QueryLinkArrayComponent) uses stringDelimiter=";"
		// and falls back to plain text when no entityDelimiter "----" is
		// present, so this renders identically to v2 prod.
		if (isPubsColumn(apiCol) && v instanceof List)
		{
			return formatPubsList((List<?>) v);
		}
		if (v instanceof List)
		{
			StringBuilder sb = new StringBuilder();
			for (Object e : (List<?>) v)
			{
				if (e == null)
				{
					continue;
				}
				if (sb.length() > 0)
				{
					sb.append('|');
				}
				sb.append(e.toString());
			}
			return sb.toString();
		}
		if (v instanceof Number)
		{
			double d = ((Number) v).doubleValue();
			if (d == Math.floor(d) && !Double.isInfinite(d))
			{
				return Long.toString((long) d);
			}
			return v.toString();
		}
		String s = v.toString();
		// Image-markdown form: `[![alt](url 'alt')](ref)` — convert into the
		// serialised JSON Variable form the V2 frontend renders as an image
		// card. Matches SOLRQueryProcessor.java:1131-1146 output shape.
		//
		// The V2 SlideshowImageComponent does JSON.parse(cell) unconditionally
		// for columns whose displayName maps to "Images" (queryBuilderConfiguration.js).
		// So once a cell looks like an image markdown, ONLY two outputs are
		// safe: the serialised Variable JSON, or the empty string. Anything
		// else — including the raw markdown — will throw SyntaxError in
		// SlideshowImageComponent.buildCarousel.
		if (s.length() > 2 && s.charAt(0) == '[' && s.charAt(1) == '!')
		{
			String json = imageMarkdownToVariableJson(s, imageType);
			if (json != null) return json;
			// Converter returned null (imageType unresolvable, regex miss, or
			// serialiser exception). Emit empty string to match
			// SOLRQueryProcessor.java:1144-1145's empty-images branch.
			System.out.println("VFBqueryJsonProcessor.formatGenericCell: image-markdown cell could not be"
					+ " converted to Variable JSON (imageType="
					+ (imageType == null ? "null" : "resolved")
					+ ", cell-prefix=" + s.substring(0, Math.min(60, s.length())) + ") — emitting empty string");
			return "";
		}
		// Plain markdown link `[label](id)` — strip to label so the
		// composite-ID-driven frontend linker can render plain text + links.
		return stripMarkdownLink(s);
	}

	/**
	 * Match a markdown-image-wrapped-link cell as VFBquery emits it:
	 *   [![ALT](URL 'TITLE')](REF)
	 *   [![ALT](URL)](REF)
	 *   [![ALT]( 'TITLE')](REF)      empty URL, common when thumbnail not
	 *                                materialised for a neuron — title still
	 *                                present because the Cypher
	 *                                apoc.text.format always emits it
	 *
	 * The 'TITLE' (in single quotes) is optional; URL is anything up to the
	 * optional ` 'title'` part or the closing `)`; REF is anything up to the
	 * final `)`. URL group can be empty/whitespace, in which case the
	 * surrounding caller treats the cell as "no image" (returns empty string
	 * for the Images column).
	 *
	 * Builds a Variable carrying an ArrayValue of one Image with
	 *   - data      = URL
	 *   - name      = ALT
	 *   - reference = REF
	 *   - format    = PNG
	 * and returns GeppettoSerializer.serializeToJSON(variable).
	 *
	 * Returns {@code null} if the cell isn't an image-markdown form or if
	 * we couldn't build the JSON (e.g. imageType wasn't resolvable) — the
	 * caller then falls back to stripMarkdownLink or pass-through.
	 */
	// URL group is lazy-empty-permissive ([^']*?) so we tolerate cells where
	// the API returned an empty URL (no thumbnail materialised) — those come
	// through as `[![alt]( 'alt')](ref)` because the Cypher's apoc.text.format
	// always emits the title slot. The optional title part stops the lazy URL
	// match cleanly. Trailing `\s*` swallows any whitespace before `)` for
	// URL-only cells like `[![alt](url )](ref)`.
	private static final Pattern IMAGE_MARKDOWN = Pattern.compile(
			"\\[!\\[([^\\]]*)\\]\\(([^']*?)(?:\\s+'([^']*)')?\\s*\\)\\]\\(([^)]+)\\)");

	private static String imageMarkdownToVariableJson(String s, Type imageType)
	{
		if (imageType == null) return null;
		Matcher m = IMAGE_MARKDOWN.matcher(s);
		if (!m.find()) return null;
		String alt = m.group(1);
		String url = m.group(2);
		String ref = m.group(4);
		// Cells where the API has no thumbnail URL but still produces the
		// `[![alt]( 'alt')](ref)` form — return "" so the caller emits an
		// empty Images cell (matches SOLRQueryProcessor empty-images branch).
		// Distinct from "regex didn't match" (returns null) so the caller can
		// suppress the warning log for this expected case.
		if (url == null || url.trim().length() == 0)
		{
			return "";
		}
		url = url.trim();

		try
		{
			ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();
			Image image = ValuesFactory.eINSTANCE.createImage();
			image.setName(alt == null ? "" : alt);
			// Match SOLRQueryProcessor.secureUrl: any http:// URL gets promoted
			// to https:// so the v2 frontend (HTTPS-served) doesn't break mixed-
			// content blocking when rendering the thumbnail.
			image.setData(url == null ? "" : url.replace("http://", "https://"));
			image.setReference(ref);
			image.setFormat(ImageFormat.PNG);
			ArrayElement element = ValuesFactory.eINSTANCE.createArrayElement();
			element.setIndex(0);
			element.setInitialValue(image);
			images.getElements().add(element);

			Variable v = VariablesFactory.eINSTANCE.createVariable();
			v.setId("images");
			v.setName("Images");
			v.getTypes().add(imageType);
			v.getInitialValues().put(imageType, images);
			return GeppettoSerializer.serializeToJSON(v);
		}
		catch (Exception e)
		{
			// Don't blow up the row over a single bad thumbnail — return null
			// so the caller falls back to text rendering.
			return null;
		}
	}

	/**
	 * Strip a markdown link wrapper {@code [label](id)} down to just the label
	 * text. VFBquery returns its `markdown`-typed columns in that wrapped form
	 * (because V3 renders them as markdown), but the v2 frontend's table
	 * renderer is plain-text + splits the composite ID column
	 * ({@code upstream_id----downstream_id}) to apply linking on top — so the
	 * label columns must NOT contain markdown.
	 *
	 * Pattern: starts with '[', has a "](" somewhere in the middle, ends with
	 * ')'. Anything not matching is passed through unchanged so plain-text
	 * cells (e.g. the `label` column on NeuronNeuronConnectivityQuery, which
	 * is already plain) are unaffected.
	 */
	private static String stripMarkdownLink(String s)
	{
		if (s == null || s.length() < 4) return s == null ? "" : s;
		if (s.charAt(0) != '[' || s.charAt(s.length() - 1) != ')') return s;
		// Don't touch image-wrapped markdown links of the form
		//   [![alt](url 'alt')](link)
		// (Shape-B thumbnail cells use this form; stripping them would
		// corrupt the image rendering). Detect by the "[!" prefix.
		if (s.length() > 1 && s.charAt(1) == '!') return s;
		int close = s.indexOf("](");
		if (close <= 0) return s;
		return s.substring(1, close);
	}

	private static Object safeGetValue(QueryResults in, String col, int rowIdx)
	{
		try
		{
			Object v = in.getValue(col, rowIdx);
			return v;
		}
		catch (RuntimeException e)
		{
			// QueryResults.getValue throws if the column name isn't in the header.
			// In normal flow this should never fire (we only ask for columns we
			// know are present), so log loudly when it does — it's a header-name
			// mismatch we'd otherwise lose silently.
			System.out.println("VFBqueryJsonProcessor.safeGetValue: column '" + col
					+ "' row " + rowIdx + " threw " + e + " — header is " + in.getHeader());
			return null;
		}
	}

	private static String stringValue(QueryResults in, String col, int rowIdx)
	{
		Object v = safeGetValue(in, col, rowIdx);
		return v == null ? "" : v.toString();
	}

	private static String formatInt(QueryResults in, String col, int rowIdx, int width)
	{
		Object v = safeGetValue(in, col, rowIdx);
		if (v == null)
		{
			return "";
		}
		long ln;
		if (v instanceof Number)
		{
			ln = ((Number) v).longValue();
		}
		else
		{
			try
			{
				ln = (long) Double.parseDouble(v.toString());
			}
			catch (NumberFormatException e)
			{
				return v.toString();
			}
		}
		return String.format("%1$" + width + "d", ln);
	}

	private static String formatPercent(QueryResults in, String col, int rowIdx)
	{
		Object v = safeGetValue(in, col, rowIdx);
		if (v == null)
		{
			return "";
		}
		double d;
		if (v instanceof Number)
		{
			d = ((Number) v).doubleValue();
		}
		else
		{
			try
			{
				d = Double.parseDouble(v.toString());
			}
			catch (NumberFormatException e)
			{
				return v.toString();
			}
		}
		return String.format("%5.1f%%", d);
	}

	private static String formatFloat(QueryResults in, String col, int rowIdx, int width, int precision)
	{
		Object v = safeGetValue(in, col, rowIdx);
		if (v == null)
		{
			return "";
		}
		double d;
		if (v instanceof Number)
		{
			d = ((Number) v).doubleValue();
		}
		else
		{
			try
			{
				d = Double.parseDouble(v.toString());
			}
			catch (NumberFormatException e)
			{
				return v.toString();
			}
		}
		return String.format("%1$" + width + "." + precision + "f", d);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.geppetto.datasources.AQueryProcessor#getProcessingOutputMap()
	 */
	@Override
	public Map<String, Object> getProcessingOutputMap()
	{
		return processingOutputMap;
	}

}
