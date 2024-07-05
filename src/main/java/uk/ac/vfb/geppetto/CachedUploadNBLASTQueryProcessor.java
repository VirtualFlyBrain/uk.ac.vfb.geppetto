package uk.ac.vfb.geppetto;

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
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.types.Type;
import org.geppetto.core.model.GeppettoSerializer;
import org.geppetto.model.datasources.AQueryResult;

import com.google.gson.Gson;

import uk.ac.vfb.geppetto.CachedUploadNBLASTQueryProcessor.NBLASTRow.Term.Core;

public class CachedUploadNBLASTQueryProcessor extends AQueryProcessor {
    private Map<String, Object> processingOutputMap = new HashMap<>();

    private Boolean debug = true;

    // Define the row class to match the structure of the data from SOLR
    class NBLASTRow {
        Term term;
        String version;
        String query;
        List<ImageChannel> imageChannels;
        List<TypeInfo> grossTypes;
        double score;
        List<Core> parents;

        class Term {
            Core core;
            List<String> description;
            List<String> comment;

            class Core {
                String symbol;
                String iri;
                List<String> types;
                String short_form;
                List<String> unique_facets;
                String label;
            }
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
        QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();

        try {
            System.out.println("CachedUploadNBLASTQueryProcessor started");
            if (results == null) {
                throw new GeppettoDataSourceException("Results input to " + query.getName() + " is null");
            }

            if (debug) {
                System.out.println("CachedUploadNBLASTQueryProcessor processing " + results.getResults().size() + " rows");
                System.out.println("CachedUploadNBLASTQueryProcessor loaded: " + results.getValue("upload_nblast_query", 0).toString());
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
            int count = 0;
            for (AQueryResult resultData : results.getResults()) {
                try {
                    // Expecting each result to be a JSON string representing an object
                    String json = results.getValue("upload_nblast_query", count).toString();
                    if (debug) System.out.println("JSON passed: " + json);

                    NBLASTRow row = gson.fromJson(json, NBLASTRow.class);
                    
                    SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();

                    // ID
                    try {
                        processedResult.getValues().add(row.term.core.short_form != null ? row.term.core.short_form : "");
                    } catch (Exception e) {
                        e.printStackTrace();
                        processedResult.getValues().add("");
                        System.out.println("Error processing ID: " + e.getMessage());
                    }

                    // Name
                    try {
                        processedResult.getValues().add(row.term.core.label != null ? row.term.core.label : "");
                    } catch (Exception e) {
                        e.printStackTrace();
                        processedResult.getValues().add("");
                        System.out.println("Error processing Name: " + e.getMessage());
                    }

                    // Type
                    try {
                        StringBuilder typeLabels = new StringBuilder();
                        if (row.parents != null && !row.parents.isEmpty()) {
                            for (Core parent : row.parents) {
                                if (typeLabels.length() > 0) {
                                    typeLabels.append("|");
                                }
                                typeLabels.append(parent.label);
                            }
                            processedResult.getValues().add(typeLabels.toString());
                        } else {
                            processedResult.getValues().add("");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        processedResult.getValues().add("");
                        System.out.println("Error processing Type: " + e.getMessage());
                    }

                    // Gross_Type
                    try {
                        processedResult.getValues().add(row.term.core.unique_facets != null ? String.join(", ", row.term.core.unique_facets) : "");
                    } catch (Exception e) {
                        e.printStackTrace();
                        processedResult.getValues().add("");
                        System.out.println("Error processing Gross_Type: " + e.getMessage());
                    }

                    // Template_Space and Imaging_Technique
                    String templateSpace = "";
                    String imagingTechnique = "";
                    ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();
                    int index = 0;

                    for (NBLASTRow.ImageChannel imageChannel : row.imageChannels) {
                        try {
                            templateSpace = imageChannel.image.template_anatomy != null ? imageChannel.image.template_anatomy.label : "";
                            imagingTechnique = imageChannel.imaging_technique != null ? imageChannel.imaging_technique.label : "";

                            // Images
                            String imageUrl = imageChannel.image.image_folder + "thumbnailT.png";
                            Image image = ValuesFactory.eINSTANCE.createImage();
                            image.setName(row.term.core.label != null ? row.term.core.label : "");
                            image.setData(imageUrl.replace("http://", "https://"));
                            image.setReference(row.term.core.short_form != null ? row.term.core.short_form : "");
                            image.setFormat(ImageFormat.PNG);
                            ArrayElement element = ValuesFactory.eINSTANCE.createArrayElement();
                            element.setIndex(index++);
                            element.setInitialValue(image);
                            images.getElements().add(element);
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("Error processing ImageChannel: " + e.getMessage());
                        }
                    }

                    try {
                        processedResult.getValues().add(templateSpace);
                    } catch (Exception e) {
                        e.printStackTrace();
                        processedResult.getValues().add("");
                        System.out.println("Error processing Template_Space: " + e.getMessage());
                    }

                    try {
                        processedResult.getValues().add(imagingTechnique);
                    } catch (Exception e) {
                        e.printStackTrace();
                        processedResult.getValues().add("");
                        System.out.println("Error processing Imaging_Technique: " + e.getMessage());
                    }

                    try {
                        if (!images.getElements().isEmpty()) {
                            imageVariable.getTypes().add(imageType);
                            imageVariable.getInitialValues().put(imageType, images);
                            processedResult.getValues().add(GeppettoSerializer.serializeToJSON(imageVariable));
                        } else {
                            processedResult.getValues().add("");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        processedResult.getValues().add("");
                        System.out.println("Error processing Images: " + e.getMessage());
                    }

                    // Score
                    try {
                        processedResult.getValues().add(String.valueOf(row.score));
                    } catch (Exception e) {
                        e.printStackTrace();
                        processedResult.getValues().add("");
                        System.out.println("Error processing Score: " + e.getMessage());
                    }

                    // Add the processed result
                    processedResults.getResults().add(processedResult);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error processing result row: " + e.getMessage());
                }
                count++;
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
