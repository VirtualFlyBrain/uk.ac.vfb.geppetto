package uk.ac.vfb.geppetto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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

/**
 * Variant of {@link OWLtoSOLRidQueryProcessor} that always includes the
 * queried term's own short_form in the resulting ID list. OWLERY's
 * /subclasses endpoint returns strict subclasses (not the queried term
 * itself), so leaf classes produce an empty subclass list. For Solr chains
 * that look up a precomputed per-class field on the focus term's own doc
 * (e.g. downstream_connectivity_query), that empty list silently drops to
 * zero results. Injecting the focus term restores the leaf-class case while
 * staying a no-op duplicate when OWLERY also returns the term itself.
 */
public class OWLtoSOLRidWithSelfQueryProcessor extends AQueryProcessor {

    private Map<String, Object> processingOutputMap = new HashMap<>();

    private Boolean debug = false;

    @Override
    public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException {

        if (results == null) {
            throw new GeppettoDataSourceException("Results input to " + query.getName() + " is null");
        }

        String queryID = dataSource.getId();

        QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();
        int idIndex = -1;

        if (debug) System.out.println("Processing OWL to SOLR ID (with self) Query Processor. Query ID: " + queryID);
        processedResults.getHeader().add("ID");

        // Use a LinkedHashSet to preserve insertion order while deduping —
        // the focus term goes in first so it's always present, OWLERY
        // subclasses (if any) follow.
        LinkedHashSet<String> ids = new LinkedHashSet<>();

        String selfId = variable != null ? variable.getId() : null;
        if (selfId != null && !selfId.isEmpty()) {
            ids.add(selfId);
        }

        switch (queryID) {
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

        if (idIndex > -1) {
            for (AQueryResult result : results.getResults()) {
                List<String> idsList = (ArrayList) ((QueryResult) result).getValues().get(idIndex);
                for (String id : idsList) {
                    String subID = id.substring((id.lastIndexOf('/') + 1), id.length()).toString();
                    ids.add(subID);
                }
            }
        }

        // ids is guaranteed non-empty here whenever the focus term has a
        // non-null/non-empty id, which is the normal case. Keep the same
        // placeholder fallback as the sibling processor for defensive parity
        // with downstream consumers.
        if (ids.isEmpty()) {
            ids.add("NO_RESULTS_PLACEHOLDER");
            processingOutputMap.put("NO_RESULTS", true);
        }

        String joinedIds = String.join(",", ids);
        processingOutputMap.put("ARRAY_ID_RESULTS", joinedIds);
        processingOutputMap.put("EXTRA_RESULT_COLUMNS", "");

        if (debug) {
            final int MAX_LENGTH = 100;
            System.out.println(ids.size() + " IDs found (including focus term).");
            if (joinedIds.length() > MAX_LENGTH) {
                System.out.println(joinedIds.substring(0, MAX_LENGTH) + "...");
            } else {
                System.out.println(joinedIds);
            }
        }

        return processedResults;
    }

    @Override
    public Map<String, Object> getProcessingOutputMap() {
        if (debug) {
            System.out.println("Processing output map contents from " + this.getClass().getName());
            for (Map.Entry<String, Object> entry : processingOutputMap.entrySet()) {
                System.out.println(entry.getKey() + " = " + entry.getValue());
            }
        }
        return processingOutputMap;
    }
}
