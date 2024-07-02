package uk.ac.vfb.geppetto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.AQueryResult;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DatasourcesFactory;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.datasources.SerializableQueryResult;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;
import org.geppetto.core.model.GeppettoSerializer;

/**
 * Processor for CachedUploadNBLASTQuery.
 */
public class CachedUploadNBLASTQueryProcessor extends AQueryProcessor {

    private Map<String, Object> processingOutputMap = new HashMap<>();
    private Boolean debug = true;

    @Override
    public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException {
        long startTime = System.currentTimeMillis();

        if (debug) {
            System.out.println("Starting process method for CachedUploadNBLASTQueryProcessor");
        }

        try {
            if (results == null) {
                throw new GeppettoDataSourceException("Results input to " + query.getName() + " is null");
            }

            QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();
            Gson gson = new Gson();
            List<NBLASTResult> nblastResults = new ArrayList<>();

            String keyName = determineKeyName(results.getHeader());

            if (debug) {
                System.out.println("Key name determined: " + keyName);
            }

            for (AQueryResult result : results.getResults()) {
                String json = results.getValue(keyName, results.getResults().indexOf(result)).toString();
                
                if (debug) {
                    System.out.println("Processing result JSON: " + json);
                }

                NBLASTResult nblastResult = gson.fromJson(json, NBLASTResult.class);
                nblastResults.add(nblastResult);
            }

            setHeaders(processedResults);

            if (debug) {
                System.out.println("Headers set: " + processedResults.getHeader());
            }

            for (NBLASTResult result : nblastResults) {
                SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
                processedResult.getValues().add(result.row.core.short_form);
                processedResult.getValues().add(result.row.core.label);
                processedResult.getValues().add(result.row.core.types.toString());
                processedResult.getValues().add(result.row.core.unique_facets.toString());
                processedResult.getValues().add(result.row.imageChannels.get(0).image.template_anatomy.label);
                processedResult.getValues().add(result.row.imageChannels.get(0).imaging_technique.label);
                processedResult.getValues().add(serializeImages(result.row.imageChannels));
                processedResult.getValues().add(result.score.toString());

                if (debug) {
                    System.out.println("Processed result: " + processedResult);
                }

                processedResults.getResults().add(processedResult);
            }

            long endTime = System.currentTimeMillis();
            System.out.println("Processing time: " + (endTime - startTime) + " milliseconds");

            return processedResults;

        } catch (Exception e) {
            e.printStackTrace();
            throw new GeppettoDataSourceException(e);
        }
    }

    private String determineKeyName(List<String> headers) {
        if (debug) {
            System.out.println("Determining key name from headers: " + headers);
        }

        for (String key : headers) {
            if ("upload_nblast_query".equals(key)) {
                if (debug) {
                    System.out.println("Key name found: " + key);
                }
                return key;
            }
        }
        return "";
    }

    private void setHeaders(QueryResults results) {
        results.getHeader().add("ID");
        results.getHeader().add("Name");
        results.getHeader().add("Type");
        results.getHeader().add("Gross_Type");
        results.getHeader().add("Template_Space");
        results.getHeader().add("Imaging_Technique");
        results.getHeader().add("Images");
        results.getHeader().add("Score");

        if (debug) {
            System.out.println("Headers set in QueryResults: " + results.getHeader());
        }
    }

    private String serializeImages(List<ImageChannel> imageChannels) {
        ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
        int i = 0;
        for (ImageChannel imageChannel : imageChannels) {
            Image image = ValuesFactory.eINSTANCE.createImage();
            image.setName(imageChannel.image.template_anatomy.label);
            image.setData(imageChannel.image.image_folder + "thumbnailT.png");
            image.setFormat(ImageFormat.PNG);
            ArrayElement element = ValuesFactory.eINSTANCE.createArrayElement();
            element.setIndex(i++);
            element.setInitialValue(image);
            imageArray.getElements().add(element);
        }
        Variable exampleVar = VariablesFactory.eINSTANCE.createVariable();
        exampleVar.setId("images");
        exampleVar.setName("Images");
        exampleVar.getTypes().add(TypesPackage.Literals.IMAGE_TYPE);
        exampleVar.getInitialValues().put(TypesPackage.Literals.IMAGE_TYPE, imageArray);
        return GeppettoSerializer.serializeToJSON(exampleVar);
    }

    @Override
    public Map<String, Object> getProcessingOutputMap() {
        return processingOutputMap;
    }

    class NBLASTResult {
        NBLASTRow row;
        Double score;
    }

    class NBLASTRow {
        MinimalEntityInfo core;
        String description;
        String comment;
        List<ImageChannel> imageChannels;
        List<MinimalEntityInfo> types;
    }

    class MinimalEntityInfo {
        String symbol;
        String iri;
        List<String> types;
        String short_form;
        List<String> unique_facets;
        String label;
    }

    class ImageChannel {
        ImageInfo image;
        MinimalEntityInfo channel;
        MinimalEntityInfo imaging_technique;
    }

    class ImageInfo {
        MinimalEntityInfo template_channel;
        List<Double> index;
        MinimalEntityInfo template_anatomy;
        String image_folder;
    }
}
