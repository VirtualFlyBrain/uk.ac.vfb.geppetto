package uk.ac.vfb.geppetto;

import java.util.ArrayList;
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
		List<String> headers = in.getHeader();
		for (String col : headers)
		{
			out.getHeader().add(mapHeader(col));
		}
		int n = in.getResults().size();
		// Pass 1: per-column numeric width analysis. For any column whose
		// every non-null value parses as a "real" number (rejecting ID-shaped
		// huge integers — see analyseNumericColumns) we capture the maximum
		// integer-digit width and the maximum fractional-digit width so the
		// emit pass can left-pad the integer side and right-pad the fractional
		// side to a single column-wide string template. The table component
		// sorts cells as strings, so without this `47` sorts BEFORE `5`. Pad
		// rule (matches Robbie's spec):
		//   integer 9 in a column whose max is 999  -> "  9"
		//   integer 1 in a column whose max frac=3  -> "1    " (1 + space-dot + 3 spaces)
		//   "0.009" in same column                  -> "0.009"
		// allNumeric=false for any column with a mixed/string cell, any
		// known string-shaped column (tags, pubs, thumbnail, …), or any
		// magnitude / digit-count signature suggesting an ID rather than a
		// count.
		int nCols = headers.size();
		boolean[] allNumeric = new boolean[nCols];
		int[] intWidths = new int[nCols];
		int[] fracWidths = new int[nCols];
		analyseNumericColumns(in, n, allNumeric, intWidths, fracWidths);

		for (int i = 0; i < n; i++)
		{
			SerializableQueryResult r = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
			// Track where the `id` column lands in this row's values list
			// + accumulate per-clickable-column entity ids so we can
			// replace the id-column value with a DELIM-packed string at
			// the end of the row. This mirrors SOLRQueryProcessor's
			// approach (e.g. row.cluster.short_form + delim + row.term...
			// + delim + row.pubs.get(0)... + delim + row.dataset...):
			// griddle reads the click target via
			//   path.split(entityDelimiter)[entityIndex]
			// where path is the id-column value. With per-cell
			// `[label](id)` markdown, the natural API-column order
			// (excluding `id`) maps 1:1 onto the entityIndex values
			// declared in queryBuilderConfiguration.js (name=0,
			// expressed_in=1, reference=2, dataset=3, ...).
			int idColIdx = -1;
			StringBuilder packedIds = new StringBuilder();
			for (int ci = 0; ci < nCols; ci++)
			{
				String col = headers.get(ci);
				Object cellValue = safeGetValue(in, col, i);
				if (col.equals(COL_ID))
				{
					idColIdx = ci;
				}
				else
				{
					// Capture this column's slot(s) in the packed id BEFORE
					// any thumbnail-wrapping or numeric-padding rewrites
					// alter the markdown. Three shapes to handle:
					//
					//   (a) plain markdown `[label](id)` → ONE slot with id
					//   (b) `;`-separated list of `[label](id)` items
					//        (pubs from v1.14.7+) → N slots, ONE PER ITEM —
					//        matches legacy SOLRQueryProcessor.id() at
					//        lines 402-407 where pubs.size() > 1 loops
					//        per-pub. Safe when the list column is followed
					//        only by columns whose customComponent reads
					//        the cell value rather than the id slot.
					//   (c) anything else (plain text, image markdown,
					//        numeric) → ONE empty slot so positional
					//        alignment for non-list columns is preserved.
					List<String> slotIds = extractMarkdownLinkIds(cellValue);
					if (slotIds == null || slotIds.isEmpty())
					{
						if (packedIds.length() > 0) packedIds.append(DELIM);
						packedIds.append("");
					}
					else
					{
						for (String slotId : slotIds)
						{
							if (packedIds.length() > 0) packedIds.append(DELIM);
							packedIds.append(slotId);
						}
					}
				}
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
				if (allNumeric[ci])
				{
					r.getValues().add(padNumericCell(cellValue, intWidths[ci], fracWidths[ci]));
				}
				else
				{
					r.getValues().add(formatGenericCell(col, cellValue, imageType));
				}
			}
			// Replace the id-column slot with the packed-id string per
			// SOLRQueryProcessor's pattern. Only override when we found
			// the id column AND collected at least one extracted slot —
			// otherwise the API's existing id stays as-is so single-
			// clickable-column queries that just want the row's primary
			// id (e.g. legacy callers) still behave.
			if (idColIdx >= 0 && packedIds.length() > 0)
			{
				r.getValues().set(idColIdx, packedIds.toString());
			}
			out.getResults().add(r);
		}
	}

	/**
	 * Extract the `id` halves from each `[label](id)` markdown link in a
	 * cell, returning a list of ids in left-to-right order. Used by the
	 * row loop to build the SOLRQueryProcessor-style packed id column.
	 *
	 * Handles three shapes:
	 *   - single `[label](id)` → list of size 1
	 *   - `;`-separated list of `[label](id)` items (pubs from
	 *     VFBquery v1.14.7+) → list with one entry per item, matching
	 *     the legacy SOLRQueryProcessor.id() per-pub loop at lines
	 *     402-407
	 *   - anything else (plain text, image markdown `[![…](…)](ref)`,
	 *     numeric, etc.) → empty list
	 *
	 * Image-wrapped markdown is deliberately rejected per item — those
	 * cells aren't clickable navigation links.
	 */
	private static List<String> extractMarkdownLinkIds(Object value)
	{
		List<String> ids = new ArrayList<>();
		if (!(value instanceof String)) return ids;
		String s = ((String) value).trim();
		if (s.length() < 4) return ids;
		// Pre-check: must start with `[` AND end with `)`. A `;`-joined
		// list satisfies this because each item ends with `)` and items
		// are joined by `; `, so the full string ends with the last
		// item's `)`.
		if (s.charAt(0) != '[' || s.charAt(s.length() - 1) != ')') return ids;

		// Per-item parse. We split on the literal `; ` delimiter
		// between markdown items (mirroring Cypher's
		// apoc.text.join(..., '; ')). The split is precise enough that
		// labels containing `;` (uncommon in pub titles) don't get
		// shredded as long as the markdown brackets are well-formed.
		for (String part : s.split(";\\s*"))
		{
			String item = part.trim();
			if (item.length() < 4) continue;
			if (item.charAt(0) != '[' || item.charAt(item.length() - 1) != ')') continue;
			// Reject image-wrapped markdown.
			if (item.charAt(1) == '!') continue;
			int close = item.indexOf("](");
			if (close <= 0) continue;
			ids.add(item.substring(close + 2, item.length() - 1));
		}
		return ids;
	}

	/**
	 * Maximum int-part digit count we will treat as a "real" count column.
	 * 10^10 = 10 digits covers every real VFB count (synapses, weights, etc.
	 * top out at low millions). Anything bigger is almost certainly an ID
	 * embedded as a Number — FlyWire root ids are ~18 digits — and must not
	 * be padded as a numeric column.
	 */
	private static final int NUMERIC_COLUMN_MAX_INT_DIGITS = 10;

	/**
	 * Same threshold expressed as a magnitude, used to short-circuit columns
	 * that contain an ID disguised as a numeric value. 1e10 is well above any
	 * VFB count column and far below any FlyWire / hemibrain body id.
	 */
	private static final double NUMERIC_COLUMN_MAX_MAGNITUDE = 1.0e10;

	/**
	 * Pass 1 of buildGenericRows. Populates allNumeric / intWidths / fracWidths
	 * for every column. A column is flagged numeric only if EVERY non-null
	 * value parses as a finite number under the safety caps above, AND the
	 * column isn't one of the known string-shaped types (tags / pubs /
	 * thumbnail / gross_type). Columns where every cell is null stay
	 * non-numeric so the emit pass falls back to formatGenericCell's
	 * empty-string handling (no whitespace cells where there's nothing
	 * meaningful to align).
	 */
	private static void analyseNumericColumns(QueryResults in, int rowCount,
			boolean[] allNumeric, int[] intWidths, int[] fracWidths)
	{
		List<String> headers = in.getHeader();
		for (int ci = 0; ci < headers.size(); ci++)
		{
			String col = headers.get(ci);
			if (isTagsColumn(col) || isPubsColumn(col) || isThumbnailColumn(col))
			{
				allNumeric[ci] = false;
				continue;
			}
			boolean numeric = true;
			boolean seenAny = false;
			int maxInt = 0;
			int maxFrac = 0;
			for (int i = 0; i < rowCount; i++)
			{
				Object v = safeGetValue(in, col, i);
				if (v == null) continue;
				Double d = parseNumericCell(v);
				if (d == null)
				{
					numeric = false;
					break;
				}
				if (Math.abs(d) > NUMERIC_COLUMN_MAX_MAGNITUDE)
				{
					numeric = false;
					break;
				}
				seenAny = true;
				String s = bareNumberString(d);
				int dot = s.indexOf('.');
				int iw = dot < 0 ? s.length() : dot;
				int fw = dot < 0 ? 0 : s.length() - dot - 1;
				// signed values: strip a leading '-' from the integer width
				// so the digit count is what we left-pad against; the sign
				// rides along inside the integer part naturally.
				if (s.length() > 0 && s.charAt(0) == '-') iw -= 1;
				if (iw > maxInt) maxInt = iw;
				if (fw > maxFrac) maxFrac = fw;
				if (maxInt > NUMERIC_COLUMN_MAX_INT_DIGITS)
				{
					numeric = false;
					break;
				}
			}
			allNumeric[ci] = numeric && seenAny;
			intWidths[ci] = maxInt;
			fracWidths[ci] = maxFrac;
		}
	}

	/**
	 * Best-effort number parse. Accepts java.lang.Number directly; otherwise
	 * tries Double.parseDouble against the string form. Returns null for
	 * anything that isn't finite, anything that contains characters Double
	 * won't accept (markdown, short_forms, hex), or anything blank.
	 */
	private static Double parseNumericCell(Object v)
	{
		if (v instanceof Number)
		{
			double d = ((Number) v).doubleValue();
			return Double.isFinite(d) ? Double.valueOf(d) : null;
		}
		String s = v.toString().trim();
		if (s.length() == 0) return null;
		// Reject anything with a non-numeric character early — Double.parseDouble
		// is permissive enough to accept hex literals on some JDKs ("0x…").
		for (int k = 0; k < s.length(); k++)
		{
			char c = s.charAt(k);
			if (!(Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E'))
			{
				return null;
			}
		}
		try
		{
			double d = Double.parseDouble(s);
			return Double.isFinite(d) ? Double.valueOf(d) : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * String form for column-width analysis: whole numbers are integer
	 * strings; non-whole numbers use Double.toString(), which preserves the
	 * user-supplied precision in the common case (the API hands us doubles
	 * that round-trip via JSON). Returns no leading/trailing whitespace.
	 */
	private static String bareNumberString(double d)
	{
		if (d == Math.floor(d) && !Double.isInfinite(d))
		{
			return Long.toString((long) d);
		}
		return Double.toString(d);
	}

	/**
	 * Format a single numeric cell against the column's pre-computed integer
	 * and fractional widths so column-wide string sort matches column-wide
	 * numeric sort. Padding rules:
	 *   - Integer side: left-pad the integer digits with spaces to intWidth.
	 *     `9` in a column whose max is `999` becomes `"  9"`.
	 *   - Fractional side: if the column has any decimals (fracWidth > 0) and
	 *     this value has none, append `1 + fracWidth` trailing spaces to fill
	 *     the slot a `"."xxx` would have occupied.
	 *   - Fractional side: if the column has any decimals and this value has
	 *     fewer than max, right-pad with spaces.
	 *   - null or unparseable cells emit an all-spaces string of the column's
	 *     full width so the column visual width stays uniform.
	 * The output preserves user-typed precision: `1` stays `1` rather than
	 * being expanded to `1.000`; the missing decimal slot becomes whitespace.
	 */
	private static String padNumericCell(Object v, int intWidth, int fracWidth)
	{
		int totalWidth = intWidth + (fracWidth > 0 ? 1 + fracWidth : 0);
		if (v == null)
		{
			return repeatSpace(totalWidth);
		}
		Double d = parseNumericCell(v);
		if (d == null)
		{
			// Shouldn't happen — the pass-1 analyser already rejected non-numeric
			// columns — but be defensive: drop through to a plain stringify.
			return v.toString();
		}
		String s = bareNumberString(d.doubleValue());
		int dot = s.indexOf('.');
		int iw = dot < 0 ? s.length() : dot;
		// Strip a leading '-' from the integer-width calculation; the sign
		// will ride along inside the digits when we emit.
		boolean negative = s.length() > 0 && s.charAt(0) == '-';
		if (negative) iw -= 1;
		int fw = dot < 0 ? 0 : s.length() - dot - 1;
		StringBuilder sb = new StringBuilder(totalWidth);
		// Left-pad the integer side.
		for (int k = 0; k < intWidth - iw; k++) sb.append(' ');
		sb.append(s);
		// Right-pad the fractional side.
		if (fracWidth > 0)
		{
			if (dot < 0)
			{
				// Value has no decimal but the column does — fill the entire
				// `.xxx…` slot with whitespace.
				for (int k = 0; k < 1 + fracWidth; k++) sb.append(' ');
			}
			else
			{
				for (int k = 0; k < fracWidth - fw; k++) sb.append(' ');
			}
		}
		return sb.toString();
	}

	private static String repeatSpace(int n)
	{
		if (n <= 0) return "";
		StringBuilder sb = new StringBuilder(n);
		for (int k = 0; k < n; k++) sb.append(' ');
		return sb.toString();
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
		// composite-ID-driven frontend linker can render plain text +
		// links.
		//
		// Multi-item list shape `[label1](id1); [label2](id2); ...`
		// (pubs from VFBquery v1.14.7+): strip each item and re-join
		// with `; ` so QueryLinkArrayComponent can split it back into
		// chips. The corresponding id-column slots are added by the
		// row loop via extractMarkdownLinkIds.
		if (s.length() > 2 && s.charAt(0) == '['
				&& s.indexOf(");") > 0)
		{
			StringBuilder out = new StringBuilder();
			for (String part : s.split(";\\s*"))
			{
				String item = part.trim();
				if (item.length() == 0) continue;
				String label = stripMarkdownLink(item);
				if (out.length() > 0) out.append("; ");
				out.append(label);
			}
			if (out.length() > 0) return out.toString();
		}
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
	// URL group is lazy-empty-permissive and forbids both quote styles so it
	// can't gobble the title. The optional title accepts EITHER
	// 'title' (Cypher-emitted, single-quoted apoc.text.format output) OR
	// "title" (post-processed shape after vfb_queries.encode_markdown_links
	// re-emits with double quotes via the secure_image_url replacement at
	// vfb_queries.py:340-355). Both pair-styles seen in production responses
	// from vfbquery.virtualflybrain.org.
	//
	// Title body is `.*?` (lazy any-char) with the closing quote tied to the
	// opener via backreference \3. That accepts apostrophes inside the title
	// — e.g. Kenyon-cell labels like `KCa'b'-ap1_R` or dopaminergic PAM
	// labels like `PAM03(B2B'2a)_L` — which the previous `[^'"]*` body
	// silently rejected, emitting empty-string thumbnails for every row
	// whose label contained a `'`. Group numbers preserved: 1=alt, 2=url,
	// 4=ref (group 3 is now the quote-delimiter, was the unused title body).
	private static final Pattern IMAGE_MARKDOWN = Pattern.compile(
			"\\[!\\[([^\\]]*)\\]\\(([^'\"]*?)(?:\\s+(['\"]).*?\\3\\s*)?\\)\\]\\(([^)]+)\\)");

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
