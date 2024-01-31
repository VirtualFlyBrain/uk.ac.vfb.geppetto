package uk.ac.vfb.geppetto;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.variables.Variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OWLtoSOLRidQueryProcessor extends AQueryProcessor {

    private Map<String, Object> processingOutputMap = new HashMap<>();

    private Boolean debug=false;

    @Override
    public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException {

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
				if (debug) System.out.println("superClassOf");
				break;
			case "owleryDataSourceRealise":
				idIndex = results.getHeader().indexOf("hasInstance");
				if (debug) System.out.println("hasInstance");
				break;
			default:
				throw new GeppettoDataSourceException("Results header not in hasInstance, subClassOf");

		}
		processedResults.getHeader().add("ID");

		if (idIndex > -1){
			for(AQueryResult result : results.getResults())
			{
				List<String> idsList = (ArrayList)((QueryResult) result).getValues().get(idIndex);
				for(String id : idsList) {
					String subID = id.substring((id.lastIndexOf('/')+1) , id.length()).toString();
					ids.add(subID);
				}
			}
		}

        String joinedIdsWithOr = "";
        // Check if ids is not empty
        if (!ids.isEmpty()) {
            // Join the list of IDs into a single string with ' OR ' as separator
            joinedIdsWithOr = String.join(" OR ", ids);

            
            
        } else {
            // Handle the case where ids is empty, if necessary
            throw new GeppettoDataSourceException("No IDs found to process.");
        }
        if (debug) System.out.println("OWL passing ids to SOLR:");
        // Replace the list of IDs in processingOutputMap with the joined string
        processingOutputMap.put("ARRAY_ID_RESULTS", joinedIdsWithOr);
        processingOutputMap.put("EXTRA_RESULT_COLUMNS", "");
        if (debug) System.out.println(joinedIdsWithOr);
        return processedResults;
    }

    @Override
    public Map<String, Object> getProcessingOutputMap() {
        return processingOutputMap;
    }
}
