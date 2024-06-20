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
 * Author: [Your Name]
 *
 */

public class CachedUploadNBLASTQueryProcessor extends AQueryProcessor {

    private Map<String, Object> processingOutputMap = new HashMap<String, Object>();

    Boolean debug = false;

    /*
     * (non-Javadoc)
     *
     * @see org.geppetto.core.datasources.IQueryProcessor#process(org.geppetto.model.ProcessQuery, org.geppetto.model.variables.Variable, org.geppetto.model.QueryResults)
     */
    @Override
    public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException {
        if (results == null) {
            throw new GeppettoDataSourceException("Results input to " + query.getName() + " is null");
        }
        QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();
        int idIndex = results.getHeader().indexOf("neuron_id");
        int targetIdIndex = results.getHeader().indexOf("target_neuron_id");
        int scoreIndex = results.getHeader().indexOf("score");
        int neuronNameIndex = results.getHeader().indexOf("neuron_name");
        int targetNeuronNameIndex = results.getHeader().indexOf("target_neuron_name");
        int alignmentUrlIndex = results.getHeader().indexOf("alignment_url");

        processedResults.getHeader().add("Neuron ID");
        processedResults.getHeader().add("Target Neuron ID");
        processedResults.getHeader().add("Score");
        processedResults.getHeader().add("Neuron Name");
        processedResults.getHeader().add("Target Neuron Name");
        processedResults.getHeader().add("Alignment URL");

        for (AQueryResult result : results.getResults()) {
            SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
            try {
                String neuronId = ((QueryResult) result).getValues().get(idIndex).toString();
                String targetNeuronId = ((QueryResult) result).getValues().get(targetIdIndex).toString();
                String score = ((QueryResult) result).getValues().get(scoreIndex).toString();
                String neuronName = ((QueryResult) result).getValues().get(neuronNameIndex).toString();
                String targetNeuronName = ((QueryResult) result).getValues().get(targetNeuronNameIndex).toString();
                String alignmentUrl = ((QueryResult) result).getValues().get(alignmentUrlIndex).toString();

                processedResult.getValues().add(neuronId);
                processedResult.getValues().add(targetNeuronId);
                processedResult.getValues().add(score);
                processedResult.getValues().add(neuronName);
                processedResult.getValues().add(targetNeuronName);
                processedResult.getValues().add(alignmentUrl);

                processedResults.getResults().add(processedResult);
            } catch (Exception e) {
                System.out.println("Error processing result: " + e.toString());
                e.printStackTrace();
                System.out.println("Result values: " + ((QueryResult) result).getValues().toString());
            }
        }

        if (debug) {
            System.out.println("CachedUploadNBLASTQueryProcessor returning " + processedResults.getResults().size() + " rows");
        }
        return processedResults;
    }

    @Override
    public Map<String, Object> getProcessingOutputMap() {
        return processingOutputMap;
    }
}
