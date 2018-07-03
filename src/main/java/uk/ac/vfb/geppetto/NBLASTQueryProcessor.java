
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

        processedResults.getHeader().add("ID");
        processedResults.getHeader().add("Score");

		List<String> ids = new ArrayList<String>();
		if (idIndex > -1){
			for(AQueryResult result : results.getResults())
			{
	            SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
	
	            String id = ((QueryResult) result).getValues().get(idIndex).toString();
	            processedResult.getValues().add(id);
	            
	            String score = ((QueryResult) result).getValues().get(scoreIndex).toString();
	            processedResult.getValues().add(score);
	            
				ids.add("'" + id + "'");
	            processedResults.getResults().add(processedResult);
			}
		}

		processingOutputMap.put("ARRAY_ID_RESULTS", ids);

		System.out.println("NBLASTQueryProcessor returning " + Integer.toString(ids.size()) + " rows");

		return processedResults;
	}

	@Override
	public Map<String, Object> getProcessingOutputMap()
	{
		return processingOutputMap;
	}
}
