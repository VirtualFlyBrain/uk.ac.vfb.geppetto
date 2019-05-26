
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


public class OWLeryQueryProcessor3 extends AQueryProcessor
{

	private Map<String, Object> processingOutputMap = new HashMap<String, Object>();

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
			throw new GeppettoDataSourceException("Results input to " + query.getName() + " is null");
		}
		
		String queryID = dataSource.getId();
		
		QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();
		int idIndex = -1;
		
		List<String> ids = new ArrayList<String>();
		
		switch(queryID) 
		{
			case "owleryDataSourceSubclass":
				idIndex = results.getHeader().indexOf("superClassOf");					
				
				break;
			case "owleryDataSourceRealise":
				idIndex = results.getHeader().indexOf("hasInstance");					
				
				break;
			default:
				throw new GeppettoDataSourceException("Results header not in hasInstance, subClassOf");
				
		}

		processedResults.getHeader().add("ID");
	
		if (idIndex > -1){
			for(AQueryResult result : results.getResults())
			{
				List<String> idsList = (ArrayList)((QueryResult) result).getValues().get(idIndex);
				System.out.println(idsList);
				for(String id : idsList) {
					String subID = id.substring((id.lastIndexOf('/')+1) , id.length()).toString();
					ids.add("'" + subID + "'");
				}
			}
		}
		
		if (processingOutputMap.keySet().contains("ARRAY_ID_RESULTS")) {
			ArrayList<ArrayList<String>> concatIds = new ArrayList<ArrayList<String>>();
			if (processingOutputMap.get("ARRAY_ID_RESULTS") instanceof List && processingOutputMap.get("ARRAY_ID_RESULTS").size() > 0 && processingOutputMap.get("ARRAY_ID_RESULTS").get(0) instanceof String) {
				concatIds.add((List<String>) processingOutputMap.get("ARRAY_ID_RESULTS"));
			} else if (processingOutputMap.get("ARRAY_ID_RESULTS") instanceof List && processingOutputMap.get("ARRAY_ID_RESULTS").size() > 0 && processingOutputMap.get("ARRAY_ID_RESULTS").get(0) instanceof List) {
				concatIds = (List<List<String>>) processingOutputMap.get("ARRAY_ID_RESULTS");	
			}else{
				System.out.println("Error identifying perevious result type! ");
			}
			concatIds.add(ids);
			processingOutputMap.clear();
			processingOutputMap.put("ARRAY_ID_RESULTS", concatIds);
		}else{
			processingOutputMap.put("ARRAY_ID_RESULTS", ids);
		}
		return processedResults;
	}

	@Override
	public Map<String, Object> getProcessingOutputMap()
	{
		return processingOutputMap;
	}

}
