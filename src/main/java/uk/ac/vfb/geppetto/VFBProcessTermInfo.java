package uk.ac.vfb.geppetto;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.datasources.QueryChecker;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.GeppettoSerializer;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.GeppettoPackage;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DataSourceLibraryConfiguration;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.Query;
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
import org.geppetto.core.model.GeppettoSerializer;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        String tempId = "xxxxx";
//		Name: fubar (fbbt_1234567) (all on one line)
        String tempName = "not found";
//		Alt_names: barfu (microref), BARFUS (microref) - comma separate (microrefs link down to ref list). Hover-over => scope
        List<String> synonyms = new ArrayList<>();
//      Thumbnail
        Variable thumbnailVar = VariablesFactory.eINSTANCE.createVariable();
        Image thumbnailValue = ValuesFactory.eINSTANCE.createImage();
        Boolean thumb = false;
//		Examples
        ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();
        String imageName = "Thumbnail";
        String tempLink = "";
        List<List<String>> domains = new ArrayList(new ArrayList());
        List<String> addedExamples = new ArrayList<>();
//		Types
        String types = "";
        String depictedType = "";
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
        List<String> refs = new ArrayList<>();
//		Linkouts
        String links = "";
//      Download
        String downloadLink = "";
//      SuperTypes
        String superTypes = "";
        boolean template = false;
        boolean synapticNP = false;
        boolean cluster = false;
        boolean individual = false;
        
//      Template Domain data:
        String wlzUrl = "";
        String[] domainId = new String[255];
        String[] domainName = new String[255];
        String[] domainType = new String[255];
        String[] domainCentre = new String[255];
        String[] voxelSize = new String[4];
        
        
        int i = 0;
        int j = 0;
        int r = 0;

        System.out.println("Creating Variable from:");

        try {
            Type textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
            Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
            Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
            System.out.println("checking Node...");
            
            // Extract metadata
            if (results.getValue("node", 0) != null) {
                System.out.println("Extracting Metadata...");
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
                CompositeType metaDataType = TypesFactory.eINSTANCE.createCompositeType();
                metaDataType.setId(tempId);
                variable.getAnonymousTypes().add(metaDataType);
                
                Variable typeVariable = VariablesFactory.eINSTANCE.createVariable();
                CompositeType typeMetaDataType = TypesFactory.eINSTANCE.createCompositeType();
                typeVariable.getAnonymousTypes().add(typeMetaDataType);

                // add supertypes
                
                List<GeppettoLibrary> dependenciesLibrary = dataSource.getDependenciesLibrary();
                if (results.getValue("labels", 0) != null) {
                    List<String> supertypes = (List<String>) results.getValue("labels", 0);

                    for (String supertype : supertypes) {
                        if (!supertype.startsWith("_")) { // ignore supertypes starting with _
                        	metaDataType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
                            superTypes += supertype + ", ";
                        }
                        if ("Template".equals(supertype)) {
                            template = true;
                        }
                        if ("Individual".equals(supertype)) {
                            individual = true;
                        }
                        if ("Synaptic_neuropil_domain".equals(supertype)) {
                            synapticNP = true;
                        }
                        if ("Cluster".equals(supertype)) {
                            cluster = true;
                        }
                    }
                } else {
                	metaDataType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType("Orphan", dependenciesLibrary));
                }
                System.out.println("SuperTypes: " + superTypes + " - " + String.valueOf(template) + String.valueOf(individual) + String.valueOf(synapticNP) + String.valueOf(cluster));

                // Load initial metadata

                // Create new Variable
                Variable metaDataVar = VariablesFactory.eINSTANCE.createVariable();
                metaDataVar.setId("metaDataVar");
                CompositeType metaData = TypesFactory.eINSTANCE.createCompositeType();
                metaDataVar.getTypes().add(metaData);
                metaDataVar.setId(tempId + "_meta");
                metaData.setId(tempId + "_metadata");
                metaData.setName("Info");
                metaDataVar.setName(tempName);
                metaDataType.getVariables().add(metaDataVar);


                // set meta label/name:
                Variable label = VariablesFactory.eINSTANCE.createVariable();
                label.setId("label");
                label.setName("Name");
                label.getTypes().add(htmlType);
                metaData.getVariables().add(label);
                HTML labelValue = ValuesFactory.eINSTANCE.createHTML();
                label.getInitialValues().put(htmlType, labelValue);
                labelValue.setHtml(labelLink);
                metaData.getVariables().add(label);	
                System.out.println(labelLink);


                // get alt names
                if (resultNode.get("synonym") != null) {
                    synonyms = (List<String>) resultNode.get("synonym");
                }


                


                while (results.getValue("links", r) != null && (synapticNP || cluster || r < 1)) {
                    List<Object> resultLinks = (List<Object>) results.getValue("links", r);
                    String edge = "";
                    String edgeLabel = "";
                    i = 0;
                    j = 0;
                    resultNode = (Map<String, Object>) results.getValue("node", r);
                    // get description:
                    if (r == 0 || (synapticNP && desc.length() < 2)){
	                    if (resultNode.get("description") != null) {
	                        desc = ((List<String>) resultNode.get("description")).get(0);
	                        if (".".equals(desc)) {
	                            desc = "";
	                        }
	                    }
	                    // get description comment:
	                    if (resultNode.get("comment") != null) {
	                        desc = desc + "<br><h5>Comment<h5><br>" + highlightLinks(((List<String>) resultNode.get("comment")).get(0));
	                    }
                    }
                    System.out.println(resultLinks.size());
                    while (i < resultLinks.size()) {
                        try {
                            Map<String, Object> resultLink = (Map<String, Object>) resultLinks.get(i);
                            edge = (String) resultLink.get("types");
                            System.out.println(String.valueOf(i) + " - " + edge + " - " + String.valueOf(resultLink)); 
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
                                        	depictedType += "<a href=\"#\" instancepath=\"" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form") + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + " (" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form") + ")</a><br/>";
                                        	if (synapticNP && individual){
                                        		try{
	                                        		typeVariable.setId(((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form"));
	                                        		typeVariable.setName(((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label"));
	                                        		for (String supertype : ((List<String>) ((Map<String, Object>) resultLinks.get(i)).get("labels"))) {
	                                                    if (!supertype.startsWith("_")) { // ignore supertypes starting with _
	                                                    	typeMetaDataType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
	                                                    }
	                                        		}
                                        		}catch (Exception e) {
                                                    System.out.println("Error creating temp type variable for " + depictedType + " - " + e.toString());
                                                    e.printStackTrace();
                                                }
                                        	}
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
                                        if ("has_exemplar".equals(edgeLabel)){
                                        	if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")) != null) {
                                                edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("iri"));
                                            } else {
                                                edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("iri"));
                                            }
                                        	//TODO: remove fix for old iri:
                                            edgeLabel = edgeLabel.replace("/owl/VFBc_", "/reports/VFB_");
                                            String fileUrl = checkURL(edgeLabel + "/thumbnailT.png");
                                            if (fileUrl != null) {
                                            	addImage(fileUrl, "Exemplar: " + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")), ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")), images, 0);
                                            	System.out.println("Adding exemplar: " + fileUrl);
                                                edgeLabel = "http://flybrain.mrc-lmb.cam.ac.uk/vfb/fc/clusterv/3/" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")) + "/snapshot.png";
                                            	thumbnailVar.setId("thumbnail");
                                                thumbnailVar.setName("Thumbnail");
                                                thumbnailVar.getTypes().add(imageType);
                                                thumbnailValue.setName(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")));
                                                thumbnailValue.setData(edgeLabel);
                                                thumbnailValue.setReference(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")));
                                                thumbnailValue.setFormat(ImageFormat.PNG);
                                                thumbnailVar.getInitialValues().put(imageType, thumbnailValue);
                                                thumb = true;
                                            }
                                        	break;
                                        }
                                        if ("has_member".equals(edgeLabel)){
                                        	if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")) != null) {
                                                edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("iri"));
                                            } else {
                                                edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("iri"));
                                            }
                                        	//TODO: remove fix for old iri:
                                            edgeLabel = edgeLabel.replace("/owl/VFBc_", "/reports/VFB_");
                                            String fileUrl = checkURL(edgeLabel + "/thumbnailT.png");
                                            if (j < 1){
                                            	j=1; // ensure exemplar is first image;
                                            }
                                            if (fileUrl != null) {
                                            	if (!listContains(addedExamples, ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")))){
	                                            	addImage(fileUrl, "Member: " + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")), ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")), images, j);
	                                            	System.out.println("Adding member: " + fileUrl);
	                                            	j++;
	                                            	addedExamples.add(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")));
                                            	}
                                            }
                                        	break;
                                        }
                                        relationships = relationships + edgeLabel.replace("_", " ") + " <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>";
                                        break;
                                    case "has_reference":
                                        edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("typ"));
                                        if ("syn".equals(edgeLabel)) {
                                        	if (!listContains(synonyms,(String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("synonym"))){
                                        		synonyms.add(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("synonym")));
                                        	}
                                        	if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) != null){
                                        		if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("microref")) != null){
                                        			edgeLabel = "<span title=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) + "\">" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("microref")) + "</span>";
                                        		}else{
                                        			edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref"));
                                        		}
                                        		if(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("FlyBase")) != null)
            									{
                                        			edgeLabel += " <a href=\"http://flybase.org/reports/" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("FlyBase")) + "\" target=\"_blank\" >"
            												+ "<i class=\"popup-icon-link gpt-fly\" title=\"FlyBase:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("FlyBase")) + "\" aria-hidden=\"true\"></i></a>";
            									}
            									if(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) != null)
            									{
            										edgeLabel += " <a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "\" target=\"_blank\" >"
            												+ "<i class=\"popup-icon-link gpt-pubmed\" title=\"PMID:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "\" aria-hidden=\"true\"></i></a>";
            									}
            									if(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) != null)
            									{
            										edgeLabel += " <a href=\"http://dx.doi.org/" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) + "\" target=\"_blank\" >"
            												+ "<i class=\"popup-icon-link gpt-doi\" title=\"doi:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) + "\" aria-hidden=\"true\"></i></a>";
            									}
                                        		for (int s = 0; s < synonyms.size(); s++) {
                                                    if (synonyms.get(s).equals((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("synonym"))) {
                                                        if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).containsKey("microref")) {
                                                            synonyms.set(s, synonyms.get(s) + " (" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("microref")) + ")"); // TODO: add hyperlink
                                                        } else if ((!"null".equals((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref"))) && (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref") != null)) {
                                                            synonyms.set(s, synonyms.get(s) + " (" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) + ")"); // TODO: add hyperlink
                                                        } 
                                                    }
                                                }
                                        	}else if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("http")) != null) {
                                        		edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("http"));
                                        		if ("//".equals(edgeLabel.substring(0,2))){
                                        			edgeLabel = "http:" + edgeLabel;
                                        		}
                                        		// TODO check link works!? (grey out if broken?)
                                        		String[] bits = edgeLabel.replace("http://", "").split("/");
                                        		edgeLabel = "<a href=\"" + edgeLabel + "\" target=\"_blank\" title=\""+edgeLabel+"\">"
                                        				+ bits[0] + "<i class=\"popup-icon-link fa fa-external-link\" aria-hidden=\"true\"></i>" + "</a>";
                                        		for (int s = 0; s < synonyms.size(); s++) {
                                                    if (synonyms.get(s).equals((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("synonym"))) {
                                                    	synonyms.set(s, synonyms.get(s) + " (" + edgeLabel + ")"); 
                                                    }
                                                }
                                        	}else if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) != null) {
                                        		edgeLabel = "<a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "\" target=\"_blank\" >"
        												+ "PMID:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "</a>";
                                        		for (int s = 0; s < synonyms.size(); s++) {
                                                    if (synonyms.get(s).equals((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("synonym"))) {
                                                    	synonyms.set(s, synonyms.get(s) + " (" + edgeLabel + ")"); 
                                                    }
                                                }
                                        	}
                                            if (!"syn".equals(edgeLabel)){
                                            	refs.add(edgeLabel);
                                            }
                                        } else if ("def".equals(edgeLabel)) {
                                        	if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) != null){
                                        		if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("microref")) != null){
                                        			edgeLabel = "<span title=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) + "\">" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("microref")) + "</span>";
                                        		}else{
                                        			edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref"));
                                        		}
                                        		if(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("FlyBase")) != null)
            									{
                                        			edgeLabel += " <a href=\"http://flybase.org/reports/" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("FlyBase")) + "\" target=\"_blank\" >"
            												+ "<i class=\"popup-icon-link gpt-fly\" title=\"FlyBase:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("FlyBase")) + "\" aria-hidden=\"true\"></i></a>";
            									}
            									if(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) != null)
            									{
            										edgeLabel += " <a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "\" target=\"_blank\" >"
            												+ "<i class=\"popup-icon-link gpt-pubmed\" title=\"PMID:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "\" aria-hidden=\"true\"></i></a>";
            									}
            									if(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) != null)
            									{
            										edgeLabel += " <a href=\"http://dx.doi.org/" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) + "\" target=\"_blank\" >"
            												+ "<i class=\"popup-icon-link gpt-doi\" title=\"doi:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) + "\" aria-hidden=\"true\"></i></a>";
            									}
            									if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).containsKey("microref")) {
                                                    desc += " (" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("microref")) + ")"; // TODO: add hyperlink
                                                } else {
                                                    desc += " (" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) + ")"; // TODO: add hyperlink
                                                }
                                        	}else if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("http")) != null) {
                                        		edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("http"));
                                        		if ("//".equals(edgeLabel.substring(0,2))){
                                        			edgeLabel = "http:" + edgeLabel;
                                        		}
                                        		// TODO check link works!? (grey out if broken?)
                                        		String[] bits = edgeLabel.replace("http://", "").split("/");
                                        		edgeLabel = "<a href=\"" + edgeLabel + "\" target=\"_blank\" title=\""+edgeLabel+"\">"
                                        				+ bits[0] + "<i class=\"popup-icon-link fa fa-external-link\" aria-hidden=\"true\"></i>" + "</a>";
                                        		desc += " (" + edgeLabel + ")";
                                        	}else if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) != null) {
                                        		edgeLabel = "<a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "\" target=\"_blank\" >"
        												+ "PMID:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "</a>";
                                        		desc += " (" + edgeLabel + ")";
                                        	}
                                        	if (!"def".equals(edgeLabel)){
                                            	refs.add(edgeLabel);
                                            }
                                        } else {
                                        	edgeLabel = "";
                                        	if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) != null){
                                        		if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("microref")) != null){
                                        			edgeLabel = "<span title=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) + "\">" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("microref")) + "</span>";
                                        		}else{
                                        			edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref"));
                                        		}
                                        		if(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("FlyBase")) != null)
            									{
                                        			edgeLabel += " <a href=\"http://flybase.org/reports/" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("FlyBase")) + "\" target=\"_blank\" >"
            												+ "<i class=\"popup-icon-link gpt-fly\" title=\"FlyBase:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("FlyBase")) + "\" aria-hidden=\"true\"></i></a>";
            									}
            									if(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) != null)
            									{
            										edgeLabel += " <a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "\" target=\"_blank\" >"
            												+ "<i class=\"popup-icon-link gpt-pubmed\" title=\"PMID:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "\" aria-hidden=\"true\"></i></a>";
            									}
            									if(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) != null)
            									{
            										edgeLabel += " <a href=\"http://dx.doi.org/" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) + "\" target=\"_blank\" >"
            												+ "<i class=\"popup-icon-link gpt-doi\" title=\"doi:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) + "\" aria-hidden=\"true\"></i></a>";
            									}
                                        	}else if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("http")) != null) {
                                        		edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("http"));
                                        		if ("//".equals(edgeLabel.substring(0,2))){
                                        			edgeLabel = "http:" + edgeLabel;
                                        		}
                                        		// TODO check link works!? (grey out if broken?)
                                        		String[] bits = edgeLabel.replace("http://", "").split("/");
                                        		edgeLabel = "<a href=\"" + edgeLabel + "\" target=\"_blank\" title=\""+edgeLabel+"\">"
                                        				+ bits[0] + "<i class=\"popup-icon-link fa fa-external-link\" aria-hidden=\"true\"></i>" + "</a>";
                                        		
                                        	}else if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) != null) {
                                        		edgeLabel = "<a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "\" target=\"_blank\" >"
        												+ "PMID:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("PMID")) + "</a>";
                                        		
                                        	}
                                        	if (!"".equals(edgeLabel)){
                                            	refs.add(edgeLabel);
                                            }
                                        }
                                        break;
                                    default:
                                    	relationships = addUniqueToString(relationships, edge.replace("_", " ") + " <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>");

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
                                        if ("type".equals(edgeLabel)) {
                                            instanceOf += 1;
                                            if (r == 0 && j < 10 && listContains(((List<String>) ((Map<String, Object>) resultLinks.get(i)).get("labels")), "Individual")) {
                                            	if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")) != null) {
                                                    edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("iri"));
                                                } else {
                                                    edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("iri"));
                                                }
                                            	//TODO: remove fix for old iri:
                                                edgeLabel = edgeLabel.replace("/owl/VFBc_", "/reports/VFB_");
                                                String fileUrl = checkURL(edgeLabel + "/thumbnailT.png");
                                                if (fileUrl != null) {
                                                	if (!listContains(addedExamples, ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")))){
    	                                            	addImage(fileUrl, ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")), ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")), images, j);
    	                                            	System.out.println("Adding example image: " + fileUrl);
    	                                            	j++;
    	                                            	addedExamples.add(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")));
                                                	}
                                                }
                                            }else if (j < 10){
                                            	System.out.println("INSTANCEOF type to node " + String.valueOf(resultLinks.get(i)));
                                            }
                                        } else {
                                            System.out.println("INSTANCEOF to node " + String.valueOf(resultLinks.get(i)));
                                        }
                                        break;
                                    case "SUBCLASSOF":
                                        edgeLabel = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("label");
                                        if ("is a".equals(edgeLabel) || "is_a".equals(edgeLabel)) {
                                            subClassOf += 1;
                                        } else {
                                            System.out.println("SUBCLASSOF to node " + edgeLabel + " " + String.valueOf(resultLinks.get(i)));
                                        }
                                        break;
                                    case "Related":
                                        edgeLabel = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("label");
                                        if ("depicts".equals(edgeLabel)) {
                                        	System.out.println(((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index"));
                                        	if (template && ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index") != null){
                                        		if ("0".equals(((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index"))){
                                        			domainId[0] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("temp")).get("short_form");
                                        			domainName[0] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label");
                                        			domainType[0] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form");
                                        			if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("center") != null){
                                        				domainCentre[0] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("center");
                                            		}
                                        			if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("voxel") != null){
                                        				voxelSize = (String[]) ((Map<String, Object>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("voxel");
                                            		}else{ // default - should not be used:
                                            			voxelSize[0] = "0.622088"; // X
                                            			voxelSize[1] = "0.622088"; // Y
                                            			voxelSize[2] = "0.622088"; // Z
                                            		}
                                        		}else{
                                        			domainId[Integer.parseInt(((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index"))] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("temp")).get("short_form");
                                        			domainName[Integer.parseInt(((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index"))] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label");
                                        			domainType[Integer.parseInt(((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index"))] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form");
                                        			if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("center") != null){
                                        				domainCentre[Integer.parseInt(((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index"))] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("center");
                                            		}
                                        		}
                                        		System.out.println("Adding domain data: " + String.valueOf(((Map<String, Object>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm"))));
                                        	}else{
	                                            if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")) != null) {
	                                                edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("iri"));
	                                            } else {
	                                                edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("iri"));
	                                            }
	                                            //TODO: remove fix for old iri:
	                                            edgeLabel = edgeLabel.replace("/owl/VFBc_", "/reports/VFB_");
	                                            String fileUrl = checkURL(edgeLabel + "/thumbnailT.png");
	                                            if (fileUrl != null) {
	                                                System.out.println("Adding thumbnail " + fileUrl);
	                                                thumbnailVar.setId("thumbnail");
	                                                thumbnailVar.setName("Thumbnail");
	                                                thumbnailVar.getTypes().add(imageType);
	                                                thumbnailValue.setName(tempName);
	                                                thumbnailValue.setData(fileUrl.replace("http://", "https://"));
	                                                thumbnailValue.setReference(tempId);
	                                                thumbnailValue.setFormat(ImageFormat.PNG);
	                                                thumbnailVar.getInitialValues().put(imageType, thumbnailValue);
	                                                thumb = true;
	                                            } else {
	                                                fileUrl = checkURL(edgeLabel + "/thumbnail.png");
	                                                if (fileUrl != null) {
	                                                    System.out.println("Adding thumbnail " + fileUrl);
	                                                    thumbnailVar.setId("thumbnail");
	                                                    thumbnailVar.setName("Thumbnail");
	                                                    thumbnailVar.getTypes().add(imageType);
	                                                    thumbnailValue.setName(tempName);
	                                                    thumbnailValue.setData(fileUrl.replace("http://", "https://"));
	                                                    thumbnailValue.setReference(tempId);
	                                                    thumbnailValue.setFormat(ImageFormat.PNG);
	                                                    thumbnailVar.getInitialValues().put(imageType, thumbnailValue);
	                                                    thumb = true;
	                                                }
	                                            }
	                                            fileUrl = checkURL(edgeLabel + "/volume_man.obj");
                                                if (fileUrl != null) {
                                                    System.out.println("Adding man OBJ " + fileUrl);
                                                    Variable objVar = VariablesFactory.eINSTANCE.createVariable();
                                                    ImportType objImportType = TypesFactory.eINSTANCE.createImportType();
                                                    objImportType.setUrl(fileUrl);
                                                    objImportType.setId(tempId + "_obj");
                                                    objImportType.setModelInterpreterId("objModelInterpreterService");
                                                    objVar.getTypes().add(objImportType);
                                                    geppettoModelAccess.addTypeToLibrary(objImportType, getLibraryFor(dataSource, "obj"));
                                                    objVar.setId(tempId + "_obj");
                                                    objVar.setName("3D Volume");
//                            						metaDataType.getVariables().add(objVar);
                                                    geppettoModelAccess.addVariableToType(objVar, metaDataType);
                                                } else {
                                                    fileUrl = checkURL(edgeLabel + "/volume.obj");
                                                    if (fileUrl != null) {
                                                        System.out.println("Adding OBJ " + fileUrl);
                                                        Variable objVar = VariablesFactory.eINSTANCE.createVariable();
                                                        ImportType objImportType = TypesFactory.eINSTANCE.createImportType();
                                                        objImportType.setUrl(fileUrl);
                                                        objImportType.setId(tempId + "_obj");
                                                        objImportType.setModelInterpreterId("objModelInterpreterService");
                                                        objVar.getTypes().add(objImportType);
                                                        geppettoModelAccess.addTypeToLibrary(objImportType, getLibraryFor(dataSource, "obj"));
                                                        objVar.setId(tempId + "_obj");
                                                        objVar.setName("3D Volume");
                                                        geppettoModelAccess.addVariableToType(objVar, metaDataType);
                                                    }
                                                }
                                                fileUrl = checkURL(edgeLabel + "/volume.swc");
                                                if (fileUrl != null) {
                                                    System.out.println("Adding SWC " + fileUrl);
                                                    Variable swcVar = VariablesFactory.eINSTANCE.createVariable();
                                                    ImportType swcImportType = TypesFactory.eINSTANCE.createImportType();
                                                    swcImportType.setUrl(fileUrl);
                                                    swcImportType.setId(tempId + "_swc");
                                                    swcImportType.setModelInterpreterId("swcModelInterpreter");
                                                    swcVar.getTypes().add(swcImportType);
                                                    geppettoModelAccess.addTypeToLibrary(swcImportType, getLibraryFor(dataSource, "swc"));
                                                    swcVar.setName("3D Skeleton");
                                                    swcVar.setId(tempId + "_swc");
                                                    geppettoModelAccess.addVariableToType(swcVar, metaDataType);

                                                }
                                                fileUrl = checkURL(edgeLabel + "/volume.wlz");
                                                if (fileUrl != null) {
                                                    System.out.println("Adding Woolz " + fileUrl);
                                                    wlzUrl = fileUrl;
                                                }
                                                if (((Map<String, Object>) resultLinks.get(i)).get("temp") != null) {
                                                	String supertype = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("temp")).get("short_form");
                                                	tempLink = "<a href=\"#\" instancepath=\"" + supertype + "\">" + (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("temp")).get("label") + "</a>";
                                                }
                                                fileUrl = checkURL(edgeLabel + "/volume.nrrd");
                                                if (fileUrl != null) {
                                                    System.out.println("Adding NRRD " + fileUrl);
                                                    downloadLink = "Aligned Image: ​<a download=\"" + (String) tempId + ".nrrd\" href=\"" + fileUrl + "\">" + (String) tempId + ".nrrd</a><br/>​​​​​​​​​​​​​​​​​​​​​​​​​​​";
                                                    downloadLink += "Note: see licensing section for reuse and attribution info.";
                                                }
                                        	}

                                        } else if ("overlaps".equals(edgeLabel)) {
                                            overlapedBy += 1;
                                        } else if ("part_of".equals(edgeLabel) || "part of".equals(edgeLabel)) {
                                            partOf += 1;
                                        } else if ("has_presynaptic_terminal_in".equals(edgeLabel)) {
                                            hasPreSynap += 1;
                                        } else if ("has_postsynaptic_terminal_in".equals(edgeLabel)) {
                                            hasPostSynap += 1;
                                        } else if ("has_synaptic_terminal_in".equals(edgeLabel)) {
                                            hasSynap += 1;
                                        } else if ("has_synaptic_terminals_of".equals(edgeLabel)) {
                                            hasSynap += 1;
                                        } else if ("connected_to".equals(edgeLabel) || "connected to".equals(edgeLabel)) {
                                        	relationships = addUniqueToString(relationships, "connected to <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>");
                                        } else if ("innervates".equals(edgeLabel)) {
                                        	relationships = addUniqueToString(relationships, "innervated by <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>");
                                        } else if ("has_member".equals(edgeLabel)) {
                                            //System.out.println("Ignoring reciprocal relationship");
                                        } else {
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
                                    	relationships = addUniqueToString(relationships, "connected to <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>");
                                        break;
                                    case "innervates":
                                        relationships = addUniqueToString(relationships, "innervated by <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>");
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
                    r++;
                }
                
                // set slices
                if (wlzUrl != null) {
                    System.out.println("Adding Woolz " + wlzUrl);
                    
                    domains.add(Arrays.asList(voxelSize));
                    domains.add(Arrays.asList(domainId));
                    domains.add(Arrays.asList(domainName));
                    domains.add(Arrays.asList(domainType));
                    domains.add(Arrays.asList(domainCentre));
                    
                    Variable slicesVar = VariablesFactory.eINSTANCE.createVariable();
                    Image slicesValue = ValuesFactory.eINSTANCE.createImage();
                    slicesValue.setData(new Gson().toJson(new IIPJSON(0, "https://www.virtualflybrain.org/fcgi/wlziipsrv.fcgi", wlzUrl.replace("http://www.virtualflybrain.org/data/", "/disk/data/VFB/IMAGE_DATA/"), domains)));
                    slicesValue.setFormat(ImageFormat.IIP);
                    slicesValue.setReference(tempId);
                    slicesVar.setId(tempId + "_slices");
                    slicesVar.setName("Stack Viewer Slices");
                    slicesVar.getTypes().add(imageType);
                    slicesVar.getInitialValues().put(imageType, slicesValue);
                    metaDataType.getVariables().add(slicesVar);
                }


                // set depictedType:
                if (depictedType != "" && !cluster) {
                    Variable depictsVar = VariablesFactory.eINSTANCE.createVariable();
                    depictsVar.setId("type");
                    depictsVar.setName("Depicts");
                    depictsVar.getTypes().add(htmlType);
                    HTML depictsValue = ValuesFactory.eINSTANCE.createHTML();
                    depictsValue.setHtml(depictedType);
                    depictsVar.getInitialValues().put(htmlType, depictsValue);
                    metaData.getVariables().add(depictsVar);
                    System.out.println(depictedType);
                }
	            	
               
                
                // set alt names:
                if (synonyms.size() > 0) {
                    Variable synVar = VariablesFactory.eINSTANCE.createVariable();
                    synVar.setId("synonym");
                    synVar.setName("Alternative Names");
                    synVar.getTypes().add(htmlType);
                    HTML synValue = ValuesFactory.eINSTANCE.createHTML();
                    synValue.setHtml(StringUtils.join(synonyms, ", "));
                    synVar.getInitialValues().put(htmlType, synValue);
                    metaData.getVariables().add(synVar);
                    System.out.println(StringUtils.join(synonyms, ", "));
                }

             
                
                // set thumbnails:
                if (thumb) {
                    geppettoModelAccess.addVariableToType(thumbnailVar, metaData);
                }
                
                // set examples:
                if (images.getElements().size() > 0) {
                	Variable exampleVar = VariablesFactory.eINSTANCE.createVariable();
                	exampleVar.setId("examples");
                	if (cluster){
                		exampleVar.setName("Cluster");
                	}else{
                		exampleVar.setName("Examples");
                	}
    				exampleVar.getTypes().add(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE));
    				geppettoModelAccess.addVariableToType(exampleVar, metaData);
    				exampleVar.getInitialValues().put(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE), images);
    				metaDataType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType("hasExamples", dependenciesLibrary));
                }
                
                if (cluster) {
                	
                	// set exemplar details:
	                	String tempHtml = "";
	                	if (results.getValue("node", 1) != null) {
	                		resultNode = (Map<String, Object>) results.getValue("node", 1);
	                		if (resultNode.get("name") != null) {
	                			tempHtml += "<b>Name:</b> " + (String) resultNode.get("name") + " (" + (String) resultNode.get("short_form") + ")<br/>";	                			
	                		}
	                		if (types != "") {
	                			tempHtml += "<b>Types:</b> " + types + "<br/>";
	                		}
	                		if (relationships != "") {
	                			tempHtml += "<b>Relationships:</b><br/>" + relationships;
	                		}
	                	}
	                    Variable typesVar = VariablesFactory.eINSTANCE.createVariable();
	                    typesVar.setId("exemplar");
	                    typesVar.setName("Most typical neuron (exemplar)");
	                    typesVar.getTypes().add(htmlType);
	                    HTML typesValue = ValuesFactory.eINSTANCE.createHTML();
	                    typesValue.setHtml(tempHtml);
	                    typesVar.getInitialValues().put(htmlType, typesValue);
	                    metaData.getVariables().add(typesVar);
	                    System.out.println(tempHtml);
	                    
	                    if (("".equalsIgnoreCase(desc)) || (".".equals(desc))) { 
	                    	desc = "A Cluster of morphologically similar neurons using affinity propogation clustering based on NBLAST neuron similarity scores (Costa et al., 2016).";
	                    }
	                
                	
                }else{
                
	                // set types:
	                if (types != "") {
	                    Variable typesVar = VariablesFactory.eINSTANCE.createVariable();
	                    typesVar.setId("type");
	                    typesVar.setName("Type");
	                    typesVar.getTypes().add(htmlType);
	                    HTML typesValue = ValuesFactory.eINSTANCE.createHTML();
	                    typesValue.setHtml(types);
	                    typesVar.getInitialValues().put(htmlType, typesValue);
	                    metaData.getVariables().add(typesVar);
	                    System.out.println(types);
	                }
	
	                // set relationships
	                
	                if (relationships != "") {
	                    Variable relVar = VariablesFactory.eINSTANCE.createVariable();
	                    relVar.setId("relationships");
	                    relVar.setName("Relationships");
	                    relVar.getTypes().add(htmlType);
	                    metaData.getVariables().add(relVar);
	                    HTML relValue = ValuesFactory.eINSTANCE.createHTML();
	                    relValue.setHtml(relationships);
	                    relVar.getInitialValues().put(htmlType, relValue);
	                    metaData.getVariables().add(relVar);
	                    System.out.println(relationships);
	                }
	
	                // set queries
	                String badge = "";
	                for(Query runnableQuery : geppettoModelAccess.getQueries())
	    			{
	    				if(QueryChecker.check(runnableQuery, variable))
	    				{
	    					badge = "<i class=\"popup-icon-link fa fa-quora on fa-square\" />";
	    					querys += badge + "<a href=\"#\" instancepath=\"" + (String) runnableQuery.getPath() + "\">" + runnableQuery.getDescription().replace("$NAME", variable.getName()) + "</a></br>";
	    				}else if (synapticNP && individual){
	    					if(QueryChecker.check(runnableQuery, typeVariable)){
	    						badge = "<i class=\"popup-icon-link fa fa-quora on fa-square\" />";
		    					querys += badge + "<a href=\"#\" instancepath=\"" + (String) runnableQuery.getPath() + "," + typeVariable.getId() + "," + typeVariable.getName() + "\">" + runnableQuery.getDescription().replace("$NAME", typeVariable.getName()) + "</a></br>";
	    					}
	    				}
	    			}
	
                }
	                
                if (querys != "") {
                    Variable queryVar = VariablesFactory.eINSTANCE.createVariable();
                    queryVar.setId("queries");
                    queryVar.setName("Query for");
                    queryVar.getTypes().add(htmlType);
                    HTML queryValue = ValuesFactory.eINSTANCE.createHTML();
                    queryValue.setHtml(querys);
                    queryVar.getInitialValues().put(htmlType, queryValue);
                    metaData.getVariables().add(queryVar);
                    System.out.println(querys);
                }


                // set description:
                if ((!"".equalsIgnoreCase(desc)) && (!".".equals(desc))) {
                    Variable description = VariablesFactory.eINSTANCE.createVariable();
                    description.setId("description");
                    description.setName("Description");
                    description.getTypes().add(htmlType);
                    HTML descriptionValue = ValuesFactory.eINSTANCE.createHTML();
                    desc = highlightLinks(desc).replaceAll("[)] [(]", "; ");
                    descriptionValue.setHtml(desc);
                    description.getInitialValues().put(htmlType, descriptionValue);
                    geppettoModelAccess.addVariableToType(description, metaData);
                    System.out.println(desc);
                }

                // set references:
                if (refs.size() > 0){
                	Set<String> hs = new HashSet<>();
                	hs.addAll(refs);
                	refs.clear();
                	refs.addAll(hs);
                	String references = StringUtils.join(refs, "<br/>");
                	Variable refVar = VariablesFactory.eINSTANCE.createVariable();
                	refVar.setId("references");
                	refVar.setName("References");
                	refVar.getTypes().add(htmlType);
                    HTML refValue = ValuesFactory.eINSTANCE.createHTML();
                    refValue.setHtml(references);
                    refVar.getInitialValues().put(htmlType, refValue);
                    metaData.getVariables().add(refVar);
                    System.out.println(references);
                }
                

                // set template space:
                if (tempLink != "") {
                    Variable tempVar = VariablesFactory.eINSTANCE.createVariable();
                    tempVar.setId("template");
                    tempVar.setName("Aligned to");
                    tempVar.getTypes().add(htmlType);
                    HTML tempValue = ValuesFactory.eINSTANCE.createHTML();
                    tempValue.setHtml(tempLink);
                    tempVar.getInitialValues().put(htmlType, tempValue);
                    metaData.getVariables().add(tempVar);
                    System.out.println(tempLink);
                }

                // set licensing:


                // set downloads:
                if (downloadLink != "") {
                    Variable downloads = VariablesFactory.eINSTANCE.createVariable();
                    downloads.setId("downloads");
                    downloads.setName("Downloads");
                    downloads.getTypes().add(htmlType);
//					metaData.getVariables().add(downloads);	
                    HTML downloadValue = ValuesFactory.eINSTANCE.createHTML();
                    downloadValue.setHtml(downloadLink);
                    downloads.getInitialValues().put(htmlType, downloadValue);
                    metaData.getVariables().add(downloads);
                    System.out.println(downloadLink);
                }
                

                // set linkouts:

            
                metaDataType.getVariables().add(metaDataVar);
                geppettoModelAccess.addTypeToLibrary(metaData, dataSource.getTargetLibrary());
                System.out.println("MetaData Creation Finished");
            } else {
                System.out.println("Error node not returned: " + results.eAllContents().toString());
            }


        } catch (GeppettoVisitingException e) {
            System.out.println("Error creating metadata: " + e.toString());
            e.printStackTrace();
            throw new GeppettoDataSourceException(e);
        }

        return results;
    }


    /**
     * @param text
     */
    private String highlightLinks(String text) {
        try {
            text = text.replaceAll("([F,V,G][A-z]*)[:,_](\\d{5}[0-9]*\\b)", "<a href=\"#\" instancepath=\"$1_$2\" title=\"$1_$2\" ><i class=\"fa fa-info-circle\" aria-hidden=\"true\"></i></a>");
            return text;
        } catch (Exception e) {
            System.out.println("Error highlighting links in (" + text + ") " + e.toString());
            return text;
        }
    }
    
    private String addUniqueToString(String concatList, String newItem) {
        if (concatList.indexOf(newItem) > -1){
        	return concatList;
        }
        return concatList + newItem;
    }

    private class IIPJSON {
        int indexNumber;
        String serverUrl;
        String fileLocation;
        List<List<String>> subDomains;

        public IIPJSON(int indexNumber, String serverUrl, String fileLocation, List<List<String>> subDomains) {
            this.indexNumber = indexNumber;
            this.fileLocation = fileLocation;
            this.serverUrl = serverUrl;
            this.subDomains = subDomains;
        }
    }

    private boolean listContains(List<String> myList, String search) {
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
    private String checkURL(String urlString) {
        try {
            urlString = urlString.replace("https://", "http://").replace(":5000", "");
            //System.out.println("Checking image: " + urlString);
            URL url = new URL(urlString);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("HEAD");
            huc.setInstanceFollowRedirects(true);
            int response = huc.getResponseCode();
            //System.out.println("Reponse: " + response);
            if (response == HttpURLConnection.HTTP_OK) {
                return urlString;
            } else if (response == HttpURLConnection.HTTP_MOVED_TEMP || response == HttpURLConnection.HTTP_MOVED_PERM) {
                return checkURL(huc.getHeaderField("Location"));
            }
            return null;
        } catch (Exception e) {
            System.out.println("Error checking url (" + urlString + ") " + e.toString());
            return null;
        }
    }

    /**
     * @param dataSource
     * @param format
     * @return
     */
    private GeppettoLibrary getLibraryFor(DataSource dataSource, String format) {
        for (DataSourceLibraryConfiguration lc : dataSource.getLibraryConfigurations()) {
            if (lc.getFormat().equals(format)) {
                return lc.getLibrary();
            }
        }
        System.out.println(format + " Not Found!");
        return null;
    }

}
