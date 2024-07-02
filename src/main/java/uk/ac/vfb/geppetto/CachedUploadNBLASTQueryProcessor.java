package uk.ac.vfb.geppetto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DatasourcesFactory;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.datasources.SerializableQueryResult;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.variables.VariablesFactory;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.util.ModelUtility;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.types.Type;

import com.google.gson.Gson;

public class CachedUploadNBLASTQueryProcessor extends AQueryProcessor
{
    private Map<String, Object> processingOutputMap = new HashMap<>();

    private Boolean debug = true;

    // Define the row class to match the structure of the data from SOLR
    class NBLASTRow {
        Core core;
        String mId;
        String queryType;
        List<ImageChannel> imageChannels;
        List<TypeInfo> types;
        double score;

        class Core {
            String symbol;
            String iri;
            List<String> types;
            String short_form;
            List<String> unique_facets;
            String label;
        }

        class ImageChannel {
            ImageInfo image;
            EntityInfo channel;
            EntityInfo imaging_technique;

            class ImageInfo {
                EntityInfo template_channel;
                List<Double> index;
                EntityInfo template_anatomy;
                String image_folder;
            }
        }

        class EntityInfo {
            String symbol;
            String iri;
            List<String> types;
            String short_form;
            List<String> unique_facets;
            String label;
        }

        class TypeInfo {
            String symbol;
            String iri;
            List<String> types;
            String short_form;
            String label;
        }
    }

    @Override
    public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException {
        long startTime = System.currentTimeMillis(); // Start timing
        try {
            if (results == null) {
                throw new GeppettoDataSourceException("Results input to " + query.getName() + "is null");
            }
            QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();

            if (debug) {
                System.out.println("CachedUploadNBLASTQueryProcessor processing " + results.getResults().size() + " rows");
            }

            // Set headers
            processedResults.getHeader().add("ID");
            processedResults.getHeader().add("Name");
            processedResults.getHeader().add("Type");
            processedResults.getHeader().add("Gross_Type");
            processedResults.getHeader().add("Template_Space");
            processedResults.getHeader().add("Imaging_Technique");
            processedResults.getHeader().add("Images");
            processedResults.getHeader().add("Score");

            // Initialize Gson
            Gson gson = new Gson();

            // Initialize necessary types
            Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
            Variable imageVariable = VariablesFactory.eINSTANCE.createVariable();

            // Process each result
            for (int i = 0; i < results.getResults().size(); i++) {
                SerializableQueryResult result = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();

                String jsonData = results.getResults().get(i).getValues().get(0).toString();
                NBLASTRow row = gson.fromJson(jsonData, NBLASTRow.class);

                // ID
                result.getValues().add(row.core.short_form);

                // Name
                result.getValues().add(row.core.label);

                // Type
                result.getValues().add(String.join(", ", row.core.types));

                // Gross_Type
                result.getValues().add(String.join(", ", row.core.unique_facets));

                // Template_Space and Imaging_Technique
                String templateSpace = "";
                String imagingTechnique = "";
                ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();
                int index = 0;

                for (NBLASTRow.ImageChannel imageChannel : row.imageChannels) {
                    templateSpace = imageChannel.image.template_anatomy.label;
                    imagingTechnique = imageChannel.imaging_technique.label;

                    // Images
                    String imageUrl = imageChannel.image.image_folder + "thumbnailT.png";
                    Image image = ValuesFactory.eINSTANCE.createImage();
                    image.setName(row.core.label);
                    image.setData(imageUrl.replace("http://", "https://"));
                    image.setReference(row.core.short_form);
                    image.setFormat(ImageFormat.PNG);
                    ArrayElement element = ValuesFactory.eINSTANCE.createArrayElement();
                    element.setIndex(index++);
                    element.setInitialValue(image);
                    images.getElements().add(element);
                }

                result.getValues().add(templateSpace);
                result.getValues().add(imagingTechnique);

                if (!images.getElements().isEmpty()) {
                    imageVariable.getTypes().add(imageType);
                    imageVariable.getInitialValues().put(imageType, images);
                    result.getValues().add(GeppettoSerializer.serializeToJSON(imageVariable));
                } else {
                    result.getValues().add("");
                }

                // Score
                result.getValues().add(String.valueOf(row.score));

                // Add the processed result
                processedResults.getResults().add(result);
            }

            long endTime = System.currentTimeMillis(); // End timing
            long duration = endTime - startTime; // Compute duration
            System.out.println("Processing time: " + duration + " milliseconds");

            return processedResults;

        } catch (Exception e) {
            e.printStackTrace();
            throw new GeppettoDataSourceException(e);
        }
    }

    @Override
    public Map<String, Object> getProcessingOutputMap() {
        return processingOutputMap;
    }
}
