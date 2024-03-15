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

        if (debug) System.out.println("Processing OWL to SOLR ID Query Processor. Query ID: " + queryID);
        processedResults.getHeader().add("ID");

		List<String> ids = new ArrayList<String>();

        if (debug) System.out.println(results.getHeader());

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
            joinedIdsWithOr = String.join(",", ids);
        } else {
            processingOutputMap.put("ARRAY_ID_RESULTS", "");
            processingOutputMap.put("EXTRA_RESULT_COLUMNS", "");
            if (debug) System.out.println("No IDs found to process.");
            return processedResults;
        }
        if (debug) System.out.println("OWL passing ids to SOLR:");
        // Replace the list of IDs in processingOutputMap with the joined string
        processingOutputMap.put("ARRAY_ID_RESULTS", joinedIdsWithOr);
        processingOutputMap.put("EXTRA_RESULT_COLUMNS", "");
        if (debug) {
            // Set the maximum length for the string
            final int MAX_LENGTH = 100;
            System.out.println(ids.size() + " IDs found.");
            // Check if the string exceeds the maximum length
            if (joinedIdsWithOr.length() > MAX_LENGTH) {
                // If it does, clip the string to the maximum length and append "..." to indicate it's clipped
                System.out.println(joinedIdsWithOr.substring(0, MAX_LENGTH) + "...");
            } else {
                // If it doesn't exceed the maximum length, print the entire string
                System.out.println(joinedIdsWithOr);
            }
        }
        return processedResults;
    }

    @Override
    public Map<String, Object> getProcessingOutputMap() {
        return processingOutputMap;
    }
}
