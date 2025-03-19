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
import org.geppetto.model.variables.Variable;

public class Neo4jToSOLRidQueryProcessor extends AQueryProcessor {

    private Map<String, Object> processingOutputMap = new HashMap<>();
    private Boolean debug = true;

    @Override
    public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException {

        if (results == null) {
            throw new GeppettoDataSourceException("Results input to " + query.getName() + " is null");
        }

        String queryID = dataSource.getId();

        QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();
        int idIndex = -1;

        if (debug) System.out.println("Processing Neo4j to SOLR ID Query Processor. Query ID: " + queryID);
        processedResults.getHeader().add("ID");

        List<String> ids = new ArrayList<String>();

        if (debug) System.out.println(results.getHeader());

        // Determine which column contains the IDs based on query type
        switch (queryID) {
            case "neo4jDataSourceInstances":
                idIndex = results.getHeader().indexOf("id");
                if (debug) System.out.println("Looking for id column");
                break;
            case "neo4jDataSourceRelationships":
                idIndex = results.getHeader().indexOf("targetId");
                if (debug) System.out.println("Looking for targetId column");
                break;
            case "neo4JDataSourceService":
                idIndex = results.getHeader().indexOf("ids");
                if (debug) System.out.println("Looking for ids column");
                break;
            default:
                // Try to find columns named 'id' or 'ids' if they exist
                idIndex = results.getHeader().indexOf("id");
                if (idIndex == -1) {
                    // Check for plural 'ids' column
                    idIndex = results.getHeader().indexOf("ids");
                    if (idIndex == -1) {
                        throw new GeppettoDataSourceException("No ID column found in Neo4j results for query: " + queryID);
                    }
                }
        }

        if (idIndex > -1) {
            for (AQueryResult result : results.getResults()) {
                Object value = ((QueryResult) result).getValues().get(idIndex);
                
                // Handle both single IDs and collections of IDs
                if (value instanceof List) {
                    // Neo4j IDs are already short forms, just add them directly
                    ids.addAll((List<String>)value);
                } else if (value instanceof String) {
                    // Add single ID directly
                    ids.add((String)value);
                }
            }
        }

        String joinedIds = "";
        // Check if ids is not empty
        if (!ids.isEmpty()) {
            // Join the list of IDs into a single string with commas
            joinedIds = String.join(",", ids);
        } else {
            processingOutputMap.put("ARRAY_ID_RESULTS", "");
            processingOutputMap.put("EXTRA_RESULT_COLUMNS", "");
            if (debug) System.out.println("No IDs found to process from Neo4j results.");
            return processedResults;
        }
        
        if (debug) System.out.println("Neo4j passing ids to SOLR:");
        // Save the joined IDs string in the processing output map
        processingOutputMap.put("ARRAY_ID_RESULTS", joinedIds);
        processingOutputMap.put("EXTRA_RESULT_COLUMNS", "");
        
        if (debug) {
            final int MAX_LENGTH = 100;
            System.out.println(ids.size() + " IDs found from Neo4j.");
            if (joinedIds.length() > MAX_LENGTH) {
                System.out.println(joinedIds.substring(0, MAX_LENGTH) + "...");
            } else {
                System.out.println(joinedIds);
            }
        }
        
        if (debug) {
            System.out.println("Processing output map contents:");
            for (Map.Entry<String, Object> entry : processingOutputMap.entrySet()) {
                System.out.println(entry.getKey() + " = " + entry.getValue());
            }
        }
        
        return processedResults;
    }

    @Override
    public Map<String, Object> getProcessingOutputMap() {
        return processingOutputMap;
    }

    // Add this helper method
    private void processId(String id, List<String> ids) {
        if (id.contains("/")) {
            // If ID contains path separator, extract the last part
            String subID = id.substring((id.lastIndexOf('/')+1), id.length());
            ids.add(subID);
        } else {
            // Otherwise use the ID as is
            ids.add(id);
        }
    }
}