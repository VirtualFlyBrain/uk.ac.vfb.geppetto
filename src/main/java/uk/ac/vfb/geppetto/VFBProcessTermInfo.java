package uk.ac.vfb.geppetto;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.GeppettoSerializer;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.GeppettoPackage;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.values.HTML;
import org.geppetto.model.values.Text;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;

import java.util.List;
import java.util.Map;

/**
 * @author RobertCourt
 */
public class VFBProcessTermInfo extends AQueryProcessor {

    /*
     * (non-Javadoc)
     *
     * @see org.geppetto.core.datasources.IQueryProcessor#process(org.geppetto.model.ProcessQuery, org.geppetto.model.variables.Variable, org.geppetto.model.QueryResults)
     */
    @Override
    public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException {

//		Generic Anatomy Term Info
        String tempId = "";
//		Name: fubar (fbbt_1234567) (all on one line)
        String tempName = "";
//		Alt_names: barfu (microref), BARFUS (microref) - comma separate (microrefs link down to ref list). Hover-over => scope
        List<String> synonyms;
//		Examples
//		Types
        String types = "";
//		Relationships
        String relationships = "";
//		Queries
        String querys = "";
//		Description
        String desc = "";
//		References
        String refs = "";
//		Linkouts
        String links = "";

        int i = 0;

        try {
            Type textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
            Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
            Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);

            // Extract metadata
            if (results.getValue("node", 0) != null) {
                Map<String, Object> resultNode = (Map<String, Object>) results.getValue("node", 0);
                String labelLink = "";
                if (resultNode.get("label") != null) {
                    tempName = (String) resultNode.get("label");
                } else if (resultNode.get("name") != null) {
                    tempName = (String) resultNode.get("name");
                }
                if (resultNode.get("short_form") != null) {
                    tempId = (String) resultNode.get("short_form");
                } else {
                    tempId = variable.getId();
                }
                labelLink = "<a href=\"#\" instancepath=\"" + tempId + "\">" + tempName + "</a>";
                labelLink = "<h4>" + labelLink + "</h4> (" + tempId + ")";



                System.out.println("Creating Metadata for " + tempName + "...");

                geppettoModelAccess.setObjectAttribute(variable, GeppettoPackage.Literals.NODE__NAME, tempName);
                CompositeType type = TypesFactory.eINSTANCE.createCompositeType();
                type.setId(variable.getId());
                variable.getAnonymousTypes().add(type);

                // add supertypes
                boolean template = false;
                List<GeppettoLibrary> dependenciesLibrary = dataSource.getDependenciesLibrary();
                if (results.getValue("labels", 0) != null) {
                    List<String> supertypes = (List<String>) results.getValue("labels", 0);

                    for (String supertype : supertypes) {
                        if (!supertype.startsWith("_")) { // ignore supertypes starting with _
                            type.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
                            System.out.println("Adding to SuperType: " + supertype);
                        }
                        if (supertype.equals("Template")) {
                            template = true;
                        }
                    }
                } else {
                    type.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType("Orphan", dependenciesLibrary));
                }

                // Load initial metadata

                // Create new Variable
                Variable metaDataVar = VariablesFactory.eINSTANCE.createVariable();
                metaDataVar.setId("metaDataVar");
                CompositeType metaData = TypesFactory.eINSTANCE.createCompositeType();
                metaDataVar.getTypes().add(metaData);
                metaDataVar.setId(variable.getId() + "_meta");
                metaData.setId(variable.getId() + "_metadata");
                metaData.setName("Info");
                metaDataVar.setName(variable.getName());

                // set meta label/name:
                Variable label = VariablesFactory.eINSTANCE.createVariable();
                label.setId("label");
                label.setName("Name");
                label.getTypes().add(htmlType);
                metaData.getVariables().add(label);
                HTML labelValue = ValuesFactory.eINSTANCE.createHTML();
                htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
                label.getInitialValues().put(htmlType, labelValue);
                labelValue.setHtml(labelLink);

                // get alt names
                if (resultNode.get("synonym") != null) {
                    synonyms = (List<String>) resultNode.get("synonym");
                }


                // get description:
                if (resultNode.get("description") != null) {
                    desc = ((List<String>) resultNode.get("description")).get(0);
                    if (desc == ".") {
                        desc = "";
                    }
                }
                // get description comment:
                if (resultNode.get("comment") != null) {
                    desc = desc + "<br><h5>Comment<h5><br>" + highlightLinks(((List<String>) resultNode.get("comment")).get(0));
                }


                if (results.getValue("links", 0) != null) {
                    List<Object> resultLinks = (List<Object>) results.getValue("links", 0);
                    String edge = "";
                    i = 0;
                    while (resultLinks.get(i) != null) {
                        try {
                            Map<String, Object> resultLink = (Map<String, Object>) resultLinks.get(i);
                            edge = (String) resultLink.get("types");
                            switch (edge) {
                                case "INSTANCEOF":
                                    if (((String) resultLink.get("start")) == "node") {
                                        System.out.println("INSTANCEOF from node " + String.valueOf(resultLinks));
                                    } else {
                                        System.out.println("INSTANCEOF to node " + String.valueOf(resultLinks));
                                    }
                                default:
                                    System.out.println("Can't handle node link: " + String.valueOf(resultLinks));
                            }
                        } catch (Exception e) {
                            System.out.println("Error processing node links: " + e.getMessage());
                            System.out.println(String.valueOf(resultLinks));
                        }
                        i++;
                    }
                }


                if (desc != "") {
                    Variable description = VariablesFactory.eINSTANCE.createVariable();
                    description.setId("description");
                    description.setName("Description");
                    description.getTypes().add(textType);
                    metaData.getVariables().add(description);
                    Text descriptionValue = ValuesFactory.eINSTANCE.createText();
                    desc = highlightLinks(desc);
                    descriptionValue.setText(desc);
                    description.getInitialValues().put(textType, descriptionValue);
                }
                // set comment:
                if (resultNode.get("comment") != null) {
                    Variable comment = VariablesFactory.eINSTANCE.createVariable();
                    comment.setId("comment");
                    comment.setName("Notes");
                    comment.getTypes().add(textType);
                    metaData.getVariables().add(comment);
                    Text commentValue = ValuesFactory.eINSTANCE.createText();
                    commentValue.setText(highlightLinks(((List<String>) resultNode.get("comment")).get(0)));
                    comment.getInitialValues().put(textType, commentValue);
                }
                type.getVariables().add(metaDataVar);
                geppettoModelAccess.addTypeToLibrary(metaData, dataSource.getTargetLibrary());

            }


        } catch (GeppettoVisitingException e) {
            throw new GeppettoDataSourceException(e);
        }

        return results;
    }


    /**
     * @param text
     */
    private String highlightLinks(String text) {
        try {
            text = text.replaceAll("([F,V,G][A-z]*)[:,_](\\d{5}[0-9]*\\b)", "<a href=\"#\" instancepath=\"$1_$2\">$1_$2</a>");
            return text;
        } catch (Exception e) {
            System.out.println("Error highlighting links in (" + text + ") " + e.toString());
            return text;
        }
    }

    private boolean contains(List<String> myList, String search) {
        for (String str : myList) {
            if (str.trim().contains(search)) return true;
        }
        return false;
    }

}
