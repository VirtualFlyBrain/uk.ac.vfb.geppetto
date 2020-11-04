
package uk.ac.vfb.geppetto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.AQueryResult;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DatasourcesFactory;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResult;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.datasources.SerializableQueryResult;
import org.geppetto.model.variables.Variable;

/**
 * @author dariodelpiano
 *
 */


public class NBLASTQueryProcessor extends AQueryProcessor
{

	private Map<String, Object> processingOutputMap = new HashMap<String, Object>();

	Boolean debug=false;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.geppetto.core.datasources.IQueryProcessor#process(org.geppetto.model.ProcessQuery, org.geppetto.model.variables.Variable, org.geppetto.model.QueryResults)
	 */
	@Override
	public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException
	{
		if(results == null)
		{
			throw new GeppettoDataSourceException("Results input to " + query.getName() + "is null");
		}
        QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();
        int idIndex = results.getHeader().indexOf("id");
        int scoreIndex = results.getHeader().indexOf("score");

        // processedResults.getHeader().add("ID");
        // processedResults.getHeader().add("Score");

		List<String> ids = new ArrayList<String>();
		String scores = ", [";
		if (idIndex > -1){
			for(AQueryResult result : results.getResults())
			{
	            		SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
				try{
	            			String id = ((QueryResult) result).getValues().get(idIndex).toString();
	            			// processedResult.getValues().add(id);
							String score = ((QueryResult) result).getValues().get(scoreIndex).toString();
	            			// processedResult.getValues().add(score);
							ids.add("\"" + id + "\"");
							scores += "{short_form: \"" + id + "\",Score:\"" + score + "\"},";
	            			// processedResults.getResults().add(processedResult);
	            		}catch (Exception e){
					System.out.println("Error finding id: " + e.toString());
					e.printStackTrace();
					System.out.println("id index [" + Integer.toString(idIndex) + "]");
					System.out.println("Values: " + ((QueryResult) result).getValues().toString());
				}

			}
		}

		processingOutputMap.put("ARRAY_ID_RESULTS", ids);

		if (!scores.equals(", [")) {
			scores += "] AS extra_columns";
			scores = scores.replace("},]", "}]");
			processingOutputMap.put("EXTRA_RESULT_COLUMNS", scores);
		}else{
			processingOutputMap.put("EXTRA_RESULT_COLUMNS", "");
		}

		if (debug) {
			System.out.println("NBLASTQueryProcessor returning " + Integer.toString(ids.size()) + " rows");
			System.out.println("NBLASTQueryProcessor returning " + ids);
		}
		return processedResults;
	}

	@Override
	public Map<String, Object> getProcessingOutputMap()
	{
		return processingOutputMap;
	}
}
