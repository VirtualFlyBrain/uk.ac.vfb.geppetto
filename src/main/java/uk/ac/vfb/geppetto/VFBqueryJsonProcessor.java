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

	// Header titles emitted by VFBqueryResponseProcessor for class connectivity.
	// These are the `title` fields from the VFBquery /run_query response.
	private static final String COL_ID = "ID";
	private static final String COL_UPSTREAM = "Upstream Class";
	private static final String COL_DOWNSTREAM = "Downstream Class";
	private static final String COL_TOTAL_N = "Total N";
	private static final String COL_CONNECTED_N = "Connected N";
	private static final String COL_PERCENT = "% Connected";
	private static final String COL_PAIRWISE = "Pairwise Connections";
	private static final String COL_TOTAL_WEIGHT = "Total Weight";
	private static final String COL_AVG_WEIGHT = "Avg Weight";

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

		// Detect connectivity-style responses by header titles. The upstream-class
		// VFBquery call returns ["ID", "Upstream Class", "Total N", ...]; downstream
		// returns ["ID", "Downstream Class", ...]. We synthesise the missing column
		// so output matches the v2 9-column shape.
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
			Type imageType = null;
			try
			{
				imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
			}
			catch (Exception e)
			{
				if (debug) System.out.println("VFBqueryJsonProcessor: could not resolve IMAGE_TYPE: " + e);
			}
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
		out.getHeader().addAll(in.getHeader());
		int n = in.getResults().size();
		for (int i = 0; i < n; i++)
		{
			SerializableQueryResult r = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
			for (String col : in.getHeader())
			{
				r.getValues().add(formatGenericCell(safeGetValue(in, col, i), imageType));
			}
			out.getResults().add(r);
		}
	}

	private static String formatGenericCell(Object v, Type imageType)
	{
		if (v == null)
		{
			return "";
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
		if (s.length() > 2 && s.charAt(0) == '[' && s.charAt(1) == '!')
		{
			String json = imageMarkdownToVariableJson(s, imageType);
			if (json != null) return json;
		}
		// Plain markdown link `[label](id)` — strip to label so the
		// composite-ID-driven frontend linker can render plain text + links.
		return stripMarkdownLink(s);
	}

	/**
	 * Match a markdown-image-wrapped-link cell as VFBquery emits it:
	 *   [![ALT](URL 'TITLE')](REF)
	 *   [![ALT](URL)](REF)
	 *
	 * The 'TITLE' (in single quotes) is optional; URL is anything not
	 * whitespace or `)`; REF is anything up to the final `)`.
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
	private static final Pattern IMAGE_MARKDOWN = Pattern.compile(
			"\\[!\\[([^\\]]*)\\]\\(([^\\s)]+)(?:\\s+'[^']*')?\\)\\]\\(([^)]+)\\)");

	private static String imageMarkdownToVariableJson(String s, Type imageType)
	{
		if (imageType == null) return null;
		Matcher m = IMAGE_MARKDOWN.matcher(s);
		if (!m.find()) return null;
		String alt = m.group(1);
		String url = m.group(2);
		String ref = m.group(3);

		try
		{
			ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();
			Image image = ValuesFactory.eINSTANCE.createImage();
			image.setName(alt == null ? "" : alt);
			image.setData(url);
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
