package uk.ac.vfb.geppetto;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.openmbean.CompositeType;

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
import org.geppetto.model.util.ModelUtility;
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
    class NBLASTRows {
        List<NBLASTRow> rows;
    }
    class NBLASTRow {
        Term term;
        String version;
        String query;
        List<ImageChannel> channel_images;
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

        // Template space:
        String template = "";
        String loadedTemplate = "";

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

            // Determine loaded template
            CompositeType testTemplate = null;
            List<String> availableTemplates = Arrays.asList("VFB_00101567","VFB_00200000","VFB_00017894","VFB_00101384","VFB_00050000","VFB_00049000","VFB_00100000","VFB_00030786","VFB_00110000","VFB_00120000");
            for (String at : availableTemplates) {
                try {
                    testTemplate = (CompositeType) ModelUtility.getTypeFromLibrary(at + "_metadata", dataSource.getTargetLibrary());
                } catch (Exception e) {
                    testTemplate = null;
                }
                if (testTemplate != null) {
                    template = at;
                    loadedTemplate = at;
                    if (debug) System.out.println("Template detected: " + at);
                    break;
                } else {
                    loadedTemplate = "VFB_00101567";
                }
            }

            // Process the result
            String jsonList = results.getValue("upload_nblast_query", 0).toString();
            NBLASTRows jsonResults = gson.fromJson(jsonList, NBLASTRows.class);

            for (NBLASTRow row : jsonResults.rows) {
                try {
                    if (debug) System.out.println("JSON passed: " + row.toString());

                    SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();

                    //Parent Type extractions
                    StringBuilder typeLabels = new StringBuilder();
                    StringBuilder typeIds = new StringBuilder();
                    try {
                        if (row.parents != null && !row.parents.isEmpty()) {
                            for (Core parent : row.parents) {
                                if (typeLabels.length() > 0) {
                                    typeLabels.append("|");
                                }
                                typeIds.append("----");
                                typeIds.append(parent.short_form);
                                typeLabels.append(parent.label);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("Error extracting Types: " + e.getMessage());
                    }

                    // ID
                    try {
                        if (typeLabels.length() > 0) {
                            processedResult.getValues().add(row.term.core.short_form + typeIds.toString());
                        } else {
                            processedResult.getValues().add(row.term.core.short_form != null ? row.term.core.short_form : "");
                        }
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
                        if (typeLabels.length() > 0) {
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

                    for (NBLASTRow.ImageChannel channel_image : row.channel_images) {
                        try {
                            if (channel_image.image.template_anatomy.short_form.equals(loadedTemplate)) {
                                templateSpace = channel_image.image.template_anatomy != null ? channel_image.image.template_anatomy.label : "";
                                imagingTechnique = channel_image.imaging_technique != null ? channel_image.imaging_technique.label : "";

                                // Images
                                String imageUrl = channel_image.image.image_folder + "thumbnailT.png";
                                Image image = ValuesFactory.eINSTANCE.createImage();
                                image.setName(row.term.core.label != null ? row.term.core.label : "");
                                image.setData(imageUrl.replace("http://", "https://"));
                                image.setReference(row.term.core.short_form != null ? row.term.core.short_form : "");
                                image.setFormat(ImageFormat.PNG);
                                ArrayElement element = ValuesFactory.eINSTANCE.createArrayElement();
                                element.setIndex(index++);
                                element.setInitialValue(image);
                                images.getElements().add(element);
                            }
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
            }

            long endTime = System.currentTimeMillis(); // End timing
            long duration = endTime - startTime; // Compute duration
            System.out.println("Processing time: " + duration + " milliseconds");

        } catch (Exception e) {
            System.out.println("Error processing results: " + e.getMessage());
            e.printStackTrace();
        }
        return processedResults;
    }

    @Override
    public Map<String, Object> getProcessingOutputMap() {
        return processingOutputMap;
    }
}
