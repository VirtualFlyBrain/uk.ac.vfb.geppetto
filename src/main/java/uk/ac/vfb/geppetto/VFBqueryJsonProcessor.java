package uk.ac.vfb.geppetto;

import java.util.HashMap;
import java.util.Map;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.AQueryResult;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DatasourcesFactory;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.datasources.SerializableQueryResult;
import org.geppetto.model.variables.Variable;

/**
 * Converts the QueryResults emitted by VFBqueryResponseProcessor (one row per
 * VFBquery `rows` entry, values still typed as Object) into the
 * SerializableQueryResult shape the geppetto-vfb frontend expects (string-
 * formatted cells, composite IDs for connectivity tables, synthesised
 * "queried term" column where the v2 chain produced both Upstream_Class and
 * Downstream_Class regardless of direction).
 *
 * Output shape is byte-equivalent to the existing SOLRQueryProcessor output
 * for hasClassConnectivity, so no frontend change is needed for the pilot.
 *
 * For non-connectivity VFBquery responses this processor degrades to a
 * straight Object→String stringification per cell, which is the right
 * behaviour for the Shape-A single-step migrations that come next.
 *
 * @author robertcourt
 */
public class VFBqueryJsonProcessor extends AQueryProcessor
{

	private Boolean debug = false;

	private static final String DELIM = "----";

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

		// Detect connectivity-style responses by header names. The upstream-class
		// VFBquery call returns headers ["ID", "Upstream Class", "Total N", ...]
		// (titles, not raw col ids); downstream returns ["ID", "Downstream Class", ...].
		// We synthesise the missing column so output matches the v2 9-column shape.
		boolean isUpstreamClassConnectivity = headerContains(results, "Upstream Class")
				&& !headerContains(results, "Downstream Class");
		boolean isDownstreamClassConnectivity = headerContains(results, "Downstream Class")
				&& !headerContains(results, "Upstream Class");
		boolean isClassConnectivity = isUpstreamClassConnectivity || isDownstreamClassConnectivity;

		if (isClassConnectivity)
		{
			buildClassConnectivityRows(variable, results, out, isUpstreamClassConnectivity);
		}
		else
		{
			buildGenericRows(results, out);
		}

		if (debug)
		{
			System.out.println("VFBqueryJsonProcessor: " + out.getResults().size() + " rows in "
					+ (System.currentTimeMillis() - t0) + " ms (mode="
					+ (isClassConnectivity ? (isUpstreamClassConnectivity ? "upstream-class" : "downstream-class") : "generic")
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

		// Resolve the column positions we need from the input header.
		int idxId = headerIndex(in, "ID");
		int idxClass = upstreamCall ? headerIndex(in, "Upstream Class") : headerIndex(in, "Downstream Class");
		int idxTotalN = headerIndex(in, "Total N");
		int idxConnectedN = headerIndex(in, "Connected N");
		int idxPercent = headerIndex(in, "% Connected");
		int idxPairwise = headerIndex(in, "Pairwise Connections");
		int idxTotalWeight = headerIndex(in, "Total Weight");
		int idxAvgWeight = headerIndex(in, "Avg Weight");

		String queriedId = variable != null ? variable.getId() : "";
		String queriedLabel = variable != null ? variable.getName() : "";
		if (queriedLabel == null || queriedLabel.isEmpty())
		{
			queriedLabel = queriedId;
		}
		// Markdown link form matches the rest of the VFB table column format
		// (the V3 frontend renders these as in-app navigation links).
		String queriedMarkdown = "[" + queriedLabel + "](" + queriedId + ")";

		for (AQueryResult row : in.getResults())
		{
			SerializableQueryResult r = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();

			String partnerId = stringAt(row, idxId);
			String partnerMarkdown = stringAt(row, idxClass);
			String upstreamId = upstreamCall ? partnerId : queriedId;
			String downstreamId = upstreamCall ? queriedId : partnerId;
			String upstreamMarkdown = upstreamCall ? partnerMarkdown : queriedMarkdown;
			String downstreamMarkdown = upstreamCall ? queriedMarkdown : partnerMarkdown;

			r.getValues().add(upstreamId + DELIM + downstreamId);
			r.getValues().add(upstreamMarkdown);
			r.getValues().add(downstreamMarkdown);
			r.getValues().add(formatInt(valueAt(row, idxTotalN), 6));
			r.getValues().add(formatInt(valueAt(row, idxConnectedN), 6));
			r.getValues().add(formatPercent(valueAt(row, idxPercent)));
			r.getValues().add(formatInt(valueAt(row, idxPairwise), 8));
			r.getValues().add(formatInt(valueAt(row, idxTotalWeight), 9));
			r.getValues().add(formatFloat(valueAt(row, idxAvgWeight), 7, 1));

			out.getResults().add(r);
		}
	}

	/**
	 * Generic Object→String stringification for non-connectivity VFBquery
	 * responses. Used by Shape-A migrations (single-step Cypher queries) once
	 * those land. Header titles are passed through unchanged.
	 */
	private void buildGenericRows(QueryResults in, QueryResults out)
	{
		out.getHeader().addAll(in.getHeader());
		for (AQueryResult row : in.getResults())
		{
			SerializableQueryResult r = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
			for (Object v : row.getValues())
			{
				r.getValues().add(v == null ? "" : v.toString());
			}
			out.getResults().add(r);
		}
	}

	private static boolean headerContains(QueryResults r, String title)
	{
		return r.getHeader().indexOf(title) >= 0;
	}

	private static int headerIndex(QueryResults r, String title)
	{
		return r.getHeader().indexOf(title);
	}

	private static Object valueAt(AQueryResult row, int idx)
	{
		if (idx < 0 || idx >= row.getValues().size())
		{
			return null;
		}
		return row.getValues().get(idx);
	}

	private static String stringAt(AQueryResult row, int idx)
	{
		Object v = valueAt(row, idx);
		return v == null ? "" : v.toString();
	}

	private static String formatInt(Object v, int width)
	{
		if (v == null)
		{
			return "";
		}
		long n;
		if (v instanceof Number)
		{
			n = ((Number) v).longValue();
		}
		else
		{
			try
			{
				n = (long) Double.parseDouble(v.toString());
			}
			catch (NumberFormatException e)
			{
				return v.toString();
			}
		}
		return String.format("%1$" + width + "d", n);
	}

	private static String formatPercent(Object v)
	{
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

	private static String formatFloat(Object v, int width, int precision)
	{
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
