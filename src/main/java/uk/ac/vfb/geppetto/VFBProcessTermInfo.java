package uk.ac.vfb.geppetto;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.GeppettoSerializer;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.GeppettoPackage;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DataSourceLibraryConfiguration;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.ImageType;
import org.geppetto.model.types.ImportType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.HTML;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.Text;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;

import com.google.gson.Gson;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;

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
        List<String> synonyms = new ArrayList<>();
//		Examples
        ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();
        String imageName = "Thumbnail";
        String tempLink = "";
        List<List<String>> domains;
//		Types
        String types = "";
//		Relationships
        String relationships = "";
//		Queries
        String querys = "";
        int overlapedBy = 0;
        int partOf = 0;
        int instanceOf = 0;
        int hasPreSynap = 0;
        int hasPostSynap = 0;
        int hasSynap = 0;
        int subClassOf = 0;
//		Description
        String desc = "";
//		References
        String refs = "";
//		Linkouts
        String links = "";
//      Download
        String downloadLink = "";

        int i = 0;
        int j = 0;

        System.out.println("Creating Variable from " + String.valueOf(results));

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
                labelLink = "<b>" + tempName + "</b> (" + tempId + ")";


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
                    String edgeLabel = "";
                    i = 0;
                    j = 0;
                    while (i < resultLinks.size()) {
                        try {
                            Map<String, Object> resultLink = (Map<String, Object>) resultLinks.get(i);
                            edge = (String) resultLink.get("types");
                            if ("node".equals(((String) resultLink.get("start")))) {
                            	// edge from term
                                switch (edge) {
                                    case "REFERSTO":
                                        //System.out.println("Ignoring Refers To data...");
                                        break;
                                    case "RelatedTree":
                                        //System.out.println("Ignoring RelatedTree data...");
                                        break;
                                    case "INSTANCEOF":
                                    	edgeLabel = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("label");
                                        if ("type".equals(edgeLabel)) {
                                            types += "<a href=\"#\" instancepath=\"" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form") + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>";
                                        } else {
                                            System.out.println("INSTANCEOF from node " + String.valueOf(resultLinks.get(i)));
                                        }
                                        break;
                                    case "SUBCLASSOF":
                                    	edgeLabel = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("label");
                                        if ("is a".equals(edgeLabel) || "is_a".equals(edgeLabel)) {
                                            types += "<a href=\"#\" instancepath=\"" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form") + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>";
                                        } else {
                                            System.out.println("SUBCLASSOF from node " + String.valueOf(resultLinks.get(i)));
                                        }
                                        break;
                                    case "Related":
                                    	edgeLabel = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("label");
                                        relationships = relationships + edgeLabel.replace("_", " ") + " <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>";
                                        break;
                                    case "has_reference":
                                    	edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("typ"));
                                    	if ("syn".equals(edgeLabel)){
	                                    	for (int s = 0; s < synonyms.size(); s++){
	                                    		if (synonyms.get(s).equals((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("synonym"))){
	                                    			if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).containsKey("microref")){
	                                    				synonyms.set(s, synonyms.get(s) + " (" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("microref")) + ")"); // TODO: add hyperlink
	                                    			}else if (!"null".equals((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref"))){
	                                    				synonyms.set(s, synonyms.get(s) + " (" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) + ")"); // TODO: add hyperlink
		                                    		}
	                                    		}
	                                    	}
                                    	}else if ("def".equals(edgeLabel)){
                                    		if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).containsKey("microref")){
                                				desc += " (" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("microref")) + ")"; // TODO: add hyperlink
                                			}else{
                                				desc += " (" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) + ")"; // TODO: add hyperlink
                                    		}
                                    	}else{
                                    		System.out.println("Has_reference from node " + String.valueOf(resultLinks.get(i)));
                                    	}
                                    	break;
                                    default:
                                    	relationships += edgeLabel.replace("_", " ") + " <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>";
                                        
                                }
                            } else { 
                            	// edge towards term
                                switch (edge) {
                                    case "REFERSTO":
                                        //System.out.println("Ignoring Refers To data...");
                                        break;
                                    case "RelatedTree":
                                    	//System.out.println("Ignoring RelatedTree data...");
                                        break;
                                    case "INSTANCEOF":
                                    	edgeLabel = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("label");
                                        if ("type".equals(edgeLabel)){
                                        	instanceOf += 1;
                                        }else{
                                        	System.out.println("INSTANCEOF to node " + String.valueOf(resultLinks.get(i)));
                                        }
                                        break;
                                    case "SUBCLASSOF":
                                    	edgeLabel = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("label");
                                        if ("is a".equals(edgeLabel) || "is_a".equals(edgeLabel)){
                                        	subClassOf += 1;
                                        }else{
                                        	System.out.println("SUBCLASSOF to node " + edgeLabel + " " + String.valueOf(resultLinks.get(i)));
                                        }
                                        break;
                                    case "Related":
                                    	edgeLabel = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("label");
                                        if ("depicts".equals(edgeLabel)) {
                                        	if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")) != null){
                                        		edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("iri"));
                                        		domains = (List<List<String>>) ((Map<String, Object>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("domains");
                                        	}else{
                                        		edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("iri"));
                                        		domains = Arrays.asList(Arrays.asList(""));
                                        	}
                                        	//TODO: remove fix for old iri:
                                            edgeLabel = edgeLabel.replace("/owl/VFBc_", "/reports/VFB_"); 
                                            String fileUrl = checkURL(edgeLabel + "/thumbnail.png");
                                            if (fileUrl != null){
                                            	System.out.println("Adding example " + String.valueOf(j) + "...");
                                        		addImage( fileUrl, tempName, tempId, images, j);
                                            	j++;
                                        	}
                                            if (j > 1) {
                                            	imageName = "Examples";
                                            }else{
                                            	fileUrl = checkURL(edgeLabel + "/volume_man.obj");
                                            	if (fileUrl != null){
                                            		System.out.println("Adding man OBJ...");
                            						Variable objVar = VariablesFactory.eINSTANCE.createVariable();
                            						ImportType objImportType = TypesFactory.eINSTANCE.createImportType();
                            						objImportType.setUrl(fileUrl);
                            						objImportType.setId(variable.getId() + "_obj");
                            						objImportType.setModelInterpreterId("objModelInterpreterService");
                            						objVar.getTypes().add(objImportType);	
                            						geppettoModelAccess.addTypeToLibrary(objImportType, getLibraryFor(dataSource, "obj"));
                            						objVar.setId(variable.getId() + "_obj");
                            						objVar.setName("3D Volume");
                            						type.getVariables().add(objVar);
                                            	}else{
                                            		fileUrl = checkURL(edgeLabel + "/volume.obj");
                                            		if (fileUrl != null){
                                            			System.out.println("Adding OBJ...");
	                            						Variable objVar = VariablesFactory.eINSTANCE.createVariable();
	                            						ImportType objImportType = TypesFactory.eINSTANCE.createImportType();
	                            						objImportType.setUrl(fileUrl);
	                            						objImportType.setId(variable.getId() + "_obj");
	                            						objImportType.setModelInterpreterId("objModelInterpreterService");
	                            						objVar.getTypes().add(objImportType);
	                            						geppettoModelAccess.addTypeToLibrary(objImportType, getLibraryFor(dataSource, "obj"));
	                            						objVar.setId(variable.getId() + "_obj");
	                            						objVar.setName("3D Volume");
	                            						type.getVariables().add(objVar);
	                                            	}
                                            	}
                                            	fileUrl = checkURL(edgeLabel + "/volume.swc");
                                            	if (fileUrl != null){
                                            		System.out.println("Adding SWC...");
                                					Variable swcVar = VariablesFactory.eINSTANCE.createVariable();
                                					ImportType swcImportType = TypesFactory.eINSTANCE.createImportType();
                                					swcImportType.setUrl(fileUrl);
                                					swcImportType.setId(variable.getId() + "_swc");
                                					swcImportType.setModelInterpreterId("swcModelInterpreter");
                                					swcVar.getTypes().add(swcImportType);
                                					swcVar.setName("3D Skeleton");
                                					geppettoModelAccess.addTypeToLibrary(swcImportType, getLibraryFor(dataSource, "swc"));
                                					swcVar.setId(variable.getId() + "_swc");
                                					type.getVariables().add(swcVar);
                                            	}
                                            	fileUrl = checkURL(edgeLabel + "/volume.wlz");
                                            	if(fileUrl != null)
                                				{
                                					System.out.println("Adding Woolz...");
                                					Variable slicesVar = VariablesFactory.eINSTANCE.createVariable();
                                					ImageType slicesType = (ImageType) geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
                                					Image slicesValue = ValuesFactory.eINSTANCE.createImage();
                                					slicesValue.setData(new Gson().toJson(new IIPJSON(0,"https://www.virtualflybrain.org/fcgi/wlziipsrv.fcgi", fileUrl.replace("http://www.virtualflybrain.org/data/", "/disk/data/VFB/IMAGE_DATA/"), domains)));
                                					slicesValue.setFormat(ImageFormat.IIP);
                                					slicesValue.setReference(variable.getId());
                                					slicesVar.setId(variable.getId() + "_slices");
                                					slicesVar.setName("Stack Viewer Slices");
                                					slicesVar.getTypes().add(slicesType);
                                					slicesVar.getInitialValues().put(slicesType, slicesValue);
                                					type.getVariables().add(slicesVar);
                                					System.out.println(slicesVar);
                                				}
                                				if (((Map<String, Object>) resultLinks.get(i)).get("tempIm") != null)
                                				{
                                					System.out.println("Adding Template Space...");
                                					
                                					tempLink = "<a href=\"#\" instancepath=\"" + (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("short_form") + "\">" + (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("label") + "</a>";

                                					// Add template ID as supertype:

                                					String supertype = (String) results.getValue("tempId", 0);
                                					type.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
                                					System.out.println("Adding to SuperType: " + supertype);
                                				}
                                				fileUrl = checkURL(edgeLabel + "/volume.nrrd");
                                            	if (fileUrl != null){
                                            		System.out.println("Adding NRRD...");
                                					downloadLink = "Aligned Image: ​<a download=\"" + (String) variable.getId() + ".nrrd\" href=\"" + fileUrl + "\">" + (String) variable.getId() + ".nrrd</a><br/>​​​​​​​​​​​​​​​​​​​​​​​​​​​";
                                					downloadLink += "Note: see licensing section for reuse and attribution info."; 
                                            	}
                                            }
                                            
                                        }else if ("overlaps".equals(edgeLabel)){
                                        	overlapedBy += 1;
                                        }else if ("part_of".equals(edgeLabel) || "part of".equals(edgeLabel)) {
                                        	partOf += 1;
                                        }else if ("has_presynaptic_terminal_in".equals(edgeLabel)){
                                        	hasPreSynap += 1;
                                        }else if ("has_postsynaptic_terminal_in".equals(edgeLabel)){
                                        	hasPostSynap += 1;
                                        }else if ("has_synaptic_terminal_in".equals(edgeLabel)){
                                            hasSynap += 1;
                                        }else if ("has_synaptic_terminals_of".equals(edgeLabel)){
                                            hasSynap += 1;
                                        }else if ("connected_to".equals(edgeLabel) || "connected to".equals(edgeLabel)){
                                        	relationships += "connected to <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>";
                                        }else if ("innervates".equals(edgeLabel)){
                                        	relationships += "innervated by <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>";
                                        }else if ("has_member".equals(edgeLabel)){
                                        	//System.out.println("Ignoring reciprocal relationship");
                                        }else{
                                        	System.out.println("Related to node " + edgeLabel + " " + String.valueOf(resultLinks.get(i)));
                                        }
                                        break;
                                    case "part_of":
                                    	partOf += 1;
                                    	break;
                                    case "overlaps":
                                        overlapedBy += 1;
                                        break;
                                    case "has_presynaptic_terminal_in":
                                    	hasPreSynap += 1;
                                    	break;
                                    case "has_postsynaptic_terminal_in":
                                    	hasPostSynap += 1;
                                    	break;
                                    case "has_synaptic_terminal_in":
                                    	hasSynap += 1;
                                    	break;
                                    case "has_synaptic_terminals_of":
                                    	hasSynap += 1;
                                    	break;	
                                    case "connected_to":
                                    	relationships += "connected to <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>";
                                    	break;
                                    case "innervates":
                                    	relationships += "innervated by <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>";
                                        break;
                                    default:
                                        System.out.println("Can't handle link to node: " + edge + " " + String.valueOf(resultLinks.get(i)));
                                }
                            }
                        } catch (Exception e) {
                        	System.out.println("Error processing node links: " + e.toString());
                        	e.printStackTrace();
                        	System.out.println(String.valueOf(resultLinks));
                            System.out.println(String.valueOf(resultLinks.get(i)));
                            System.out.println(tempName + " (" + tempId + ")");
                        }
                        i++;
                    }
                }
             
                
                // set alt names:
                if (synonyms.size() > 0){
                	Variable synVar = VariablesFactory.eINSTANCE.createVariable();
                	synVar.setId("synonym");
                	synVar.setName("Alternative Names");
                	synVar.getTypes().add(htmlType);
                    metaData.getVariables().add(synVar);
                    HTML synValue = ValuesFactory.eINSTANCE.createHTML();
                    synValue.setHtml(StringUtils.join(synonyms,", "));
                    synVar.getInitialValues().put(htmlType, synValue);
                }

                // set examples:
               if (j > 0) {
                    Variable exampleVar = VariablesFactory.eINSTANCE.createVariable();
                    exampleVar.setId("thumbnail");
                    exampleVar.setName(imageName);
                    exampleVar.getTypes().add(imageType);
                    geppettoModelAccess.addVariableToType(exampleVar, metaData);
                    exampleVar.getInitialValues().put(imageType, images);
                }

                // set types:
                if (types != "") {
                    Variable typesVar = VariablesFactory.eINSTANCE.createVariable();
                    typesVar.setId("type");
                    typesVar.setName("Type");
                    typesVar.getTypes().add(htmlType);
                    metaData.getVariables().add(typesVar);
                    HTML typesValue = ValuesFactory.eINSTANCE.createHTML();
                    typesValue.setHtml(types);
                    typesVar.getInitialValues().put(htmlType, typesValue);
                }

                // set relationships
             // set types:
                if (relationships != "") {
                    Variable relVar = VariablesFactory.eINSTANCE.createVariable();
                    relVar.setId("relationships");
                    relVar.setName("Relationships");
                    relVar.getTypes().add(htmlType);
                    metaData.getVariables().add(relVar);
                    HTML relValue = ValuesFactory.eINSTANCE.createHTML();
                    relValue.setHtml(relationships);
                    relVar.getInitialValues().put(htmlType, relValue);
                }

                // set queries
//                String querys = "";
//                int overlapedBy = 0;
//                int partOf = 0;
//                int instanceOf = 0;
//                int hasPreSynap = 0;
//                int hasPostSynap = 0;
//                int hasSynap = 0;
//                int subClassOf = 0;
               
                if (overlapedBy > 0){
                	querys += "<span class=\"badge\">" + String.valueOf(overlapedBy) + "</span> Overlap Query"; //TODO add actual query;
                }
                if (partOf > 0){
                	querys += "<span class=\"badge\">" + String.valueOf(partOf) + "</span> SubParts Query"; //TODO add actual query;
                }
                if (instanceOf > 0){
                	querys += "<span class=\"badge\">" + String.valueOf(instanceOf) + "</span> instancesOf Query"; //TODO add actual query;
                }
                if (hasPreSynap > 0){
                	querys += "<span class=\"badge\">" + String.valueOf(hasPreSynap) + "</span> PreSynaptic terminals in Query"; //TODO add actual query;
                }
                if (hasPostSynap > 0){
                	querys += "<span class=\"badge\">" + String.valueOf(hasPostSynap) + "</span> PostSynaptic terminals in Query"; //TODO add actual query;
                }
                if (hasSynap > 0){
                	querys += "<span class=\"badge\">" + String.valueOf(hasSynap) + "</span> Synaptic terminals in Query"; //TODO add actual query;
                }
                if (subClassOf > 0){
                	querys += "<span class=\"badge\">" + String.valueOf(subClassOf) + "</span> SubClass Query"; //TODO add actual query;
                }
                
                if (querys != ""){
                	Variable queryVar = VariablesFactory.eINSTANCE.createVariable();
                	queryVar.setId("queries");
                	queryVar.setName("Query for");
                	queryVar.getTypes().add(htmlType);
					metaData.getVariables().add(queryVar);	
					HTML queryValue = ValuesFactory.eINSTANCE.createHTML();
					queryValue.setHtml(tempLink);
					queryVar.getInitialValues().put(htmlType, queryValue);
                }
                
                
                // set description:
                if (desc != "" && desc != ".") {
                    Variable description = VariablesFactory.eINSTANCE.createVariable();
                    description.setId("description");
                    description.setName("Description");
                    description.getTypes().add(htmlType);
                    metaData.getVariables().add(description);
                    HTML descriptionValue = ValuesFactory.eINSTANCE.createHTML();
                    desc = highlightLinks(desc);
                    descriptionValue.setHtml(desc);
                    description.getInitialValues().put(htmlType, descriptionValue);
                }

                // set references:

                // set template space:
                if (tempLink != "") {
	                Variable tempVar = VariablesFactory.eINSTANCE.createVariable();
					tempVar.setId("template");
					tempVar.setName("Aligned to");
					tempVar.getTypes().add(htmlType);
					metaData.getVariables().add(tempVar);	
					HTML tempValue = ValuesFactory.eINSTANCE.createHTML();
					tempValue.setHtml(tempLink);
					tempVar.getInitialValues().put(htmlType, tempValue);
                }
				
                // set licensing:
                
                
                // set downloads:
                if (downloadLink != "") {
                	Variable downloads = VariablesFactory.eINSTANCE.createVariable();
					downloads.setId("downloads");
					downloads.setName("Downloads");
					downloads.getTypes().add(htmlType);
					metaData.getVariables().add(downloads);	
					HTML downloadValue = ValuesFactory.eINSTANCE.createHTML();
					downloadValue.setHtml(downloadLink);
					downloads.getInitialValues().put(htmlType, downloadValue);
                }
                
                // set linkouts:

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

    private class IIPJSON{
		int indexNumber;
		String serverUrl;
		String fileLocation;
		List<List<String>> subDomains;
		public IIPJSON(int indexNumber, String serverUrl, String fileLocation, List<List<String>> subDomains)
		{
			this.indexNumber=indexNumber;
			this.fileLocation=fileLocation;
			this.serverUrl=serverUrl;
			this.subDomains=subDomains;
		}
	}
    
    private boolean contains(List<String> myList, String search) {
        for (String str : myList) {
            if (str.trim().contains(search)) return true;
        }
        return false;
    }

    /**
     * @param data
     * @param name
     * @param images
     * @param i
     */
    private void addImage(String data, String name, String reference, ArrayValue images, int i) {
        Image image = ValuesFactory.eINSTANCE.createImage();
        image.setName(name);
        image.setData(data);
        image.setReference(reference);
        image.setFormat(ImageFormat.PNG);
        ArrayElement element = ValuesFactory.eINSTANCE.createArrayElement();
        element.setIndex(i);
        element.setInitialValue(image);
        images.getElements().add(element);
    }
    
    /**
	 * @param urlString
	 */
	private String checkURL(String urlString)
	{
		try
		{
			urlString = urlString.replace("https://","http://").replace(":5000", "");
			//System.out.println("Checking image: " + urlString);
			URL url = new URL(urlString);
			HttpURLConnection huc = (HttpURLConnection) url.openConnection();
			huc.setRequestMethod("HEAD");
			huc.setInstanceFollowRedirects(true);
			int response = huc.getResponseCode();
			//System.out.println("Reponse: " + response);
			if (response == HttpURLConnection.HTTP_OK) {
				return urlString;
			}else if (response == HttpURLConnection.HTTP_MOVED_TEMP || response == HttpURLConnection.HTTP_MOVED_PERM){
				return checkURL(huc.getHeaderField("Location"));
			}
			return null;
		}
		catch(Exception e)
		{
			System.out.println("Error checking url (" + urlString + ") " + e.toString());
			return null;
		}
	}
	
	/**
	 * @param dataSource
	 * @param format
	 * @return
	 */
	private GeppettoLibrary getLibraryFor(DataSource dataSource, String format)
	{
		for(DataSourceLibraryConfiguration lc : dataSource.getLibraryConfigurations())
		{
			if(lc.getFormat().equals(format))
			{
				return lc.getLibrary();
			}
		}
		return null;
	}

}
