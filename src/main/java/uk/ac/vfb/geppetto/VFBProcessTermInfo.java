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
import java.util.Collections;

import org.apache.commons.lang.StringUtils;
import org.eclipse.emf.ecore.util.EcoreUtil;

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

		//	Generic Anatomy Term Info
		String tempId = "xxxxx";
		//	Name: fubar (fbbt_1234567) (all on one line)
		String tempName = "not found";
		//	Alt_names: barfu (microref), BARFUS (microref) - comma separate (microrefs link down to ref list). Hover-over => scope
		List<String> synonyms = new ArrayList<>();
		//      Thumbnail
		Variable thumbnailVar = VariablesFactory.eINSTANCE.createVariable();
		Image thumbnailValue = ValuesFactory.eINSTANCE.createImage();
		Boolean thumb = false;
		Boolean imagesChecked = false;
		//	Examples
		ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();
		String imageName = "Thumbnail";
		String tempLink = "";
		String vfbFileUrl = "";
		List<List<String>> domains = new ArrayList(new ArrayList());
		List<String> addedExamples = new ArrayList<>();
		//	Types
		String types = "";
		String depictedType = "";
		//	Relationships
		String relationships = "";
		//	Queries
		String querys = "";
		Variable classVariable = VariablesFactory.eINSTANCE.createVariable();
		CompositeType classParentType = TypesFactory.eINSTANCE.createCompositeType();
		classVariable.setId("notSet");
		//	Description
		String desc = "";
		//	References
		List<String> refs = new ArrayList<>();
		//	Linkouts
		String links = "";
		//      Download
		String downloadLink = "";
		//      SuperTypes
		String superTypes = "";
		boolean template = false;
		boolean synapticNP = false;
		boolean tract = false;
		boolean cluster = false;
		boolean individual = false;
		boolean NBLAST = false;

		//      Template Domain data:
		String wlzUrl = "";
		String[] domainId = new String[600];
		String[] domainName = new String[600];
		String[] domainType = new String[600];
		String[] domainCentre = new String[600];
		String[] voxelSize = new String[4];


		int i = 0;
		int j = 0;
		int r = 0;

		System.out.println("Creating Variable for: " + String.valueOf(variable.getId()));

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

				geppettoModelAccess.setObjectAttribute(variable, GeppettoPackage.Literals.NODE__NAME, tempName);
				CompositeType parentType = TypesFactory.eINSTANCE.createCompositeType();
				parentType.setId(tempId);
				variable.getAnonymousTypes().add(parentType);

				// add supertypes

				List<GeppettoLibrary> dependenciesLibrary = dataSource.getDependenciesLibrary();
				if (results.getValue("labels", 0) != null) {
					List<String> supertypes = (List<String>) results.getValue("labels", 0);

					for (String supertype : supertypes) {
						if (!supertype.startsWith("_")) { // ignore supertypes starting with _
							parentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
							superTypes += supertype + ", ";
						}
						if ("Template".equals(supertype)) {
							template = true;
						}
						if ("Individual".equals(supertype)) {
							individual = true;
						}
						if ("Painted_domain".equals(supertype)) {
							synapticNP = true;
						}
						if ("Synaptic_neuropil_domain".equals(supertype)) {
							synapticNP = true;
						}
						if ("Neuron_projection_bundle".equals(supertype)) {
							tract = true;
						}
						if ("Cluster".equals(supertype)) {
							cluster = true;
						}
					}
				} else {
					parentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType("Orphan", dependenciesLibrary));
				}

				// Load initial metadata

				// Create new composite variable for metadata
				Variable metaDataVar = VariablesFactory.eINSTANCE.createVariable();
				CompositeType metaDataType = TypesFactory.eINSTANCE.createCompositeType();
				metaDataVar.getTypes().add(metaDataType);
				metaDataVar.setId(tempId + "_meta");
				metaDataType.setId(tempId + "_metadata");
				metaDataType.setName("Info");
				metaDataVar.setName(tempName);
				geppettoModelAccess.addVariableToType(metaDataVar, parentType);

				// set meta label/name:
				Variable label = VariablesFactory.eINSTANCE.createVariable();
				label.setId("label");
				label.setName("Name");
				label.getTypes().add(htmlType);
				HTML labelValue = ValuesFactory.eINSTANCE.createHTML();
				label.getInitialValues().put(htmlType, labelValue);
				labelValue.setHtml(labelLink);
				geppettoModelAccess.addVariableToType(label, metaDataType);	


				// get alt names
				if (resultNode.get("synonym") != null) {
					synonyms = (List<String>) resultNode.get("synonym");
				}

				while (results.getValue("links", r) != null && (synapticNP || cluster || tract || r < 1)) {
					List<Object> resultLinks = (List<Object>) results.getValue("links", r);
					String edge = "";
					String edgeLabel = "";
					i = 0;
					j = 0;
					if (cluster && r>0){
						depictedType = "";
					}
					resultNode = (Map<String, Object>) results.getValue("node", r);
					// get description:
					if (r == 0 || ((synapticNP || tract) && desc.length() < 2)){
						if (resultNode.get("description") != null) {
							try{
								desc = ((List<String>) resultNode.get("description")).get(0);
								if (".".equals(desc)) {
									desc = "";
								}
							}catch (Exception e) {
								System.out.println("Error processing node desc: " + e.toString());
								e.printStackTrace();
								System.out.println(tempName + " (" + tempId + ")");	
								desc = (String) resultNode.get("description");
							}
						}
						// get description comment:
						if (resultNode.get("annotation-comment") != null) {
							desc = desc + "<br><h5>Comment<h5><br>" + highlightLinks(((List<String>) resultNode.get("annotation-comment")).get(0));
						}
					}
					while (i < resultLinks.size()) {
						try {
							Map<String, Object> resultLink = (Map<String, Object>) resultLinks.get(i);
							edge = (String) resultLink.get("types");
							if ("node".equals(((String) resultLink.get("start")))) {
								// edge from term
								switch (edge) {
								case "INSTANCEOF":
									edgeLabel = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("label");
									if ("type".equals(edgeLabel)) {
										depictedType += "<a href=\"#\" instancepath=\"" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form") + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + " (" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form") + ")</a><br/>";
										if ((synapticNP || tract) && individual){
											try{
												classVariable.setId(((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form"));
												classVariable.setName(((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label"));
												classParentType.setId(classVariable.getId());
												classVariable.getAnonymousTypes().add(classParentType);
												for (String supertype : ((List<String>) ((Map<String, Object>) resultLinks.get(i)).get("labels"))) {
													if (!supertype.startsWith("_")) { // ignore supertypes starting with _
														classParentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
													}
												}
											}catch (Exception e) {
												System.out.println("Error creating temp type variable for " + depictedType + " - " + e.toString());
												e.printStackTrace();
											}
										}
									}
									break;
								case "SUBCLASSOF":
									edgeLabel = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("label");
									if ("is a".equals(edgeLabel) || "is_a".equals(edgeLabel)) {
										types += "<a href=\"#\" instancepath=\"" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form") + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>";
									}
									break;
								case "has_exemplar":
									if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")) != null) {
										edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("folder"));
									} else {
										edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("iri"));
									}
									//TODO: remove fix for old iri:
									edgeLabel = edgeLabel.replace("/owl/VFBc_", "/reports/VFB_");
									vfbFileUrl = checkURL(edgeLabel + "/thumbnailT.png");
									if (vfbFileUrl != null && vfbFileUrl.indexOf("?") < 0) {
										addImage(vfbFileUrl.replace("http:","https:"), "Exemplar: " + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")), ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")), images, 0);
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
								case "has_member":
									if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")) != null) {
										edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("folder"));
									} else {
										edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("iri"));
									}
									//TODO: remove fix for old iri:
									edgeLabel = edgeLabel.replace("/owl/VFBc_", "/reports/VFB_");
									vfbFileUrl = checkURL(edgeLabel + "/thumbnailT.png");
									if (j < 1){
										j=1; // ensure exemplar is first image;
									}
									if (vfbFileUrl != null && vfbFileUrl.indexOf("?") < 0) {
										if (!listContains(addedExamples, ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")))){
											addImage(vfbFileUrl.replace("http:","https:"), "Member: " + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")), ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")), images, j);
											j++;
											addedExamples.add(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")));
										}
									}
									break;
								case "has_reference":
									try{
									    edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("typ"));
                                        if ("syn".equals(edgeLabel)) {
                                            if (!listContains(synonyms,(String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("synonym"))){
                                                synonyms.add(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("synonym")));
                                            }
                                            if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) != null){
                                                if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")) != null){
                                                    edgeLabel = "<span title=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) + "\">" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")) + "</span>";
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
                                                    edgeLabel += " <a href=\"https://doi.org/" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) + "\" target=\"_blank\" >"
                                                            + "<i class=\"popup-icon-link gpt-doi\" title=\"doi:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) + "\" aria-hidden=\"true\"></i></a>";
                                                }
                                                for (int s = 0; s < synonyms.size(); s++) {
                                                    if (synonyms.get(s).equals((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("synonym"))) {
                                                        if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).containsKey("label")) {
                                                            synonyms.set(s, synonyms.get(s) + " (" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")) + ")"); // TODO: add hyperlink
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
                                                if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")) != null){
                                                    edgeLabel = "<span title=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) + "\">" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")) + "</span>";
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
                                                    edgeLabel += " <a href=\"https://doi.org/" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) + "\" target=\"_blank\" >"
                                                            + "<i class=\"popup-icon-link gpt-doi\" title=\"doi:" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) + "\" aria-hidden=\"true\"></i></a>";
                                                }
                                                if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).containsKey("label")) {
                                                    desc += " (" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")) + ")"; // TODO: add hyperlink
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
                                                if (((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")) != null){
                                                    edgeLabel = "<span title=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("miniref")) + "\">" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")) + "</span>";
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
                                                    edgeLabel += " <a href=\"https://doi.org/" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("DOI")) + "\" target=\"_blank\" >"
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
                                    }catch (Exception e) {
                                        System.out.println("Error creating reference: " + e.toString());
			                            e.printStackTrace();
                                    }
									break;
								case "REFERSTO":
									//Ignoring Refers To data
									break;
								case "RelatedTree":
									//Ignoring RelatedTree data
									break;
								case "hasDbXref":
									if ((((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("accession")) == null || "None".equals((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("accession"))){
										refs.add("<i class=\"popup-icon-link fa fa-external-link\" /> <a href=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("iri")) + "\" target=\"_blank\" >" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a>");		
									}else{
										refs.add("<i class=\"popup-icon-link fa fa-external-link\" /> <a href=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("link_base")) + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("accession")) + "\" target=\"_blank\" >" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + " (" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("edge")).get("accession")) + ")</a>");		
									}
									break;
								case "member_of":
									NBLAST = true;
									// then run default:
								default:
									relationships = addUniqueToString(relationships, edge.replace("_", " ") + " <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>");
								}
							} else {
								// edge towards term
								switch (edge) {
								case "expresses":
								case "INSTANCEOF":
									if (r == 0 && j < 10 && listContains(((List<String>) ((Map<String, Object>) resultLinks.get(i)).get("labels")), "Individual")) {
										if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")) != null) {
											edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("folder"));
										} else {
											edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("iri"));
										}
										//TODO: remove fix for old iri:
										edgeLabel = edgeLabel.replace("/owl/VFBc_", "/reports/VFB_");
										vfbFileUrl = checkURL(edgeLabel + "/thumbnailT.png");
										if (vfbFileUrl != null && vfbFileUrl.indexOf("?") < 0) {
											if (!listContains(addedExamples, ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")))){
												addImage(vfbFileUrl.replace("http:","https:"), ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")), ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")), images, j);
												j++;
												addedExamples.add(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")));
											}
										}
									}
									break;
								case "depicts":
									if (!imagesChecked || template){
										try{
											if (template && ((Map<String, Object>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index") != null){
												if (1 > (((Map<String, Double>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index")).intValue()){
													domainId[0] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("temp")).get("short_form");
													domainName[0] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label");
													domainType[0] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form");
													if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("center") != null){
														domainCentre[0] = String.valueOf(((Map<String, ArrayList>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("center"));
													}
													if (((Map<String, Object>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("voxel") != null){
														voxelSize[0] = String.valueOf(((Map<String, ArrayList>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("voxel").get(0));
														voxelSize[1] = String.valueOf(((Map<String, ArrayList>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("voxel").get(1));
														voxelSize[2] = String.valueOf(((Map<String, ArrayList>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("voxel").get(2));
													}else{ // default - should not be used:
														System.out.println("Failure to load voxel size!");
														System.out.println((String) ((Map<String, Object>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("folder"));
														voxelSize[0] = "0.622088"; // X
														voxelSize[1] = "0.622088"; // Y
														voxelSize[2] = "0.622088"; // Z
													}
												}else{
													domainId[(((Map<String, Double>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index")).intValue()] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("temp")).get("short_form");
													domainName[(((Map<String, Double>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index")).intValue()] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label");
													domainType[(((Map<String, Double>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index")).intValue()] = ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form");
													if (((Map<String, Object>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("center") != null){
														domainCentre[(((Map<String, Double>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index")).intValue()] = String.valueOf(((Map<String, ArrayList>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("center"));
													}
												}
											}
										}catch (Exception e){
											System.out.println("Error adding domain metadata:");
											e.printStackTrace();
										}
										try{
											if ((!template && !imagesChecked) || (template && 1 > (((Map<String, Double>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("index")).intValue())){
												if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")) != null) {
													edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("folder"));
												} else {
													edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("iri"));
												}
												//TODO: remove fix for old iri:
												edgeLabel = edgeLabel.replace("/owl/VFBc_", "/reports/VFB_");
												vfbFileUrl = checkURL(edgeLabel + "/thumbnailT.png");
												if (vfbFileUrl != null && vfbFileUrl.indexOf("?") < 0) {
													thumbnailVar.setId("thumbnail");
													thumbnailVar.setName("Thumbnail");
													thumbnailVar.getTypes().add(imageType);
													thumbnailValue.setName(tempName);
													thumbnailValue.setData(vfbFileUrl.replace("http:","https:"));
													thumbnailValue.setReference(tempId);
													thumbnailValue.setFormat(ImageFormat.PNG);
													thumbnailVar.getInitialValues().put(imageType, thumbnailValue);
													thumb = true;
												} else {
													vfbFileUrl = checkURL(edgeLabel + "/thumbnail.png");
													if (vfbFileUrl != null && vfbFileUrl.indexOf("?") < 0) {
														thumbnailVar.setId("thumbnail");
														thumbnailVar.setName("Thumbnail");
														thumbnailVar.getTypes().add(imageType);
														thumbnailValue.setName(tempName);
														thumbnailValue.setData(vfbFileUrl.replace("http://", "https://"));
														thumbnailValue.setReference(tempId);
														thumbnailValue.setFormat(ImageFormat.PNG);
														thumbnailVar.getInitialValues().put(imageType, thumbnailValue);
														thumb = true;
													}
												}		                                                
												if (r==0){
													vfbFileUrl = checkURL(edgeLabel + "/volume_man.obj");
													Variable objVar = VariablesFactory.eINSTANCE.createVariable();
													ImportType objImportType = TypesFactory.eINSTANCE.createImportType();
													if (vfbFileUrl != null && vfbFileUrl.indexOf("?") < 0) {
														objImportType.setUrl(vfbFileUrl);
														objImportType.setId(tempId + "_obj");
														objImportType.setModelInterpreterId("objModelInterpreterService");
														objVar.getTypes().add(objImportType);
														objVar.setId(tempId + "_obj");
														objVar.setName("3D Volume");
														parentType.getVariables().add(objVar);
														geppettoModelAccess.addTypeToLibrary(objImportType, getLibraryFor(dataSource, "obj"));
													} else {
														vfbFileUrl = checkURL(edgeLabel + "/volume.obj");
														if (vfbFileUrl != null && vfbFileUrl.indexOf("?") < 0) {
															objImportType.setUrl(vfbFileUrl);
															objImportType.setId(tempId + "_obj");
															objImportType.setModelInterpreterId("objModelInterpreterService");
															objVar.getTypes().add(objImportType);
															objVar.setId(tempId + "_obj");
															objVar.setName("3D Volume");
															parentType.getVariables().add(objVar);
															geppettoModelAccess.addTypeToLibrary(objImportType, getLibraryFor(dataSource, "obj"));
														}
													}
												}
												vfbFileUrl = checkURL(edgeLabel + "/volume.swc");
												if (vfbFileUrl != null && r == 0) {
													Variable swcVar = VariablesFactory.eINSTANCE.createVariable();
													ImportType swcImportType = TypesFactory.eINSTANCE.createImportType();
													swcImportType.setUrl(vfbFileUrl);
													swcImportType.setId(tempId + "_swc");
													swcImportType.setModelInterpreterId("swcModelInterpreter");
													swcVar.getTypes().add(swcImportType);
													swcVar.setName("3D Skeleton");
													swcVar.setId(tempId + "_swc");
													geppettoModelAccess.addVariableToType(swcVar, parentType);
													geppettoModelAccess.addTypeToLibrary(swcImportType, getLibraryFor(dataSource, "swc"));
												}
												vfbFileUrl = checkURL(edgeLabel + "/volume.wlz");
												if (vfbFileUrl != null && wlzUrl == "") {
													wlzUrl = vfbFileUrl;
												}
												if (((Map<String, Object>) resultLinks.get(i)).get("temp") != null) {
													String supertype = (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("temp")).get("short_form");
													tempLink = "<a href=\"#\" instancepath=\"" + supertype + "\">" + (String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("temp")).get("label") + "</a>";
												}
												vfbFileUrl = checkURL(edgeLabel + "/volume.nrrd");
												if (vfbFileUrl != null && downloadLink == "") {
													downloadLink = "Aligned Image: ​<a download=\"" + (String) tempId + ".nrrd\" href=\"" + vfbFileUrl + "\">" + (String) tempId + ".nrrd</a><br/>​​​​​​​​​​​​​​​​​​​​​​​​​​​";
													downloadLink += "Note: see license (under relationships) as well as references for terms of reuse and correct attribution.";
												}
												imagesChecked = true;
											}
										}catch (Exception e){
											System.out.println("Error adding images:");
											e.printStackTrace();
										}
									}
									break;
								case "has_license":
									relationships = addUniqueToString(relationships, "applies to <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>");
									break;	
								case "connected_to":
									relationships = addUniqueToString(relationships, "connected to <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>");
									break;
								case "innervates":
									relationships = addUniqueToString(relationships, "innervated by <a href=\"#\" instancepath=\"" + ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")) + "\">" + ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label") + "</a><br/>");
									break;
								case "SUBCLASSOF":
									//                                    	Ignore SUBCLASSOF
									break;
								case "has_source":
									if (r == 0 && j < 10 && listContains(((List<String>) ((Map<String, Object>) resultLinks.get(i)).get("labels")), "Individual")) {
										if (((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")) != null) {
											edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("tempIm")).get("folder"));
										} else {
											edgeLabel = ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("iri"));
										}
										//TODO: remove fix for old iri:
										edgeLabel = edgeLabel.replace("/owl/VFBc_", "/reports/VFB_");
										vfbFileUrl = checkURL(edgeLabel + "/thumbnailT.png");
										if (vfbFileUrl != null && vfbFileUrl.indexOf("?") < 0) {
											if (!listContains(addedExamples, ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")))){
												addImage(vfbFileUrl.replace("http:","https:"), ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("label")), ((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")), images, j);
												j++;
												addedExamples.add(((String) ((Map<String, String>) ((Map<String, Object>) resultLinks.get(i)).get("to")).get("short_form")));
											}
										}
									}
									break;
								
								default:
									System.out.println("Unhandled link to node: " + edge + " " + String.valueOf(resultLinks.get(i)));
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
				if (wlzUrl != "") {
					try{
						if (template){
							domains.add(Arrays.asList(voxelSize));
							domains.add(Arrays.asList(domainId));
							domains.add(Arrays.asList(domainName));
							domains.add(Arrays.asList(domainType));
							domains.add(Arrays.asList(domainCentre));
						}else{
							domains.add(Arrays.asList("0","0","0"));
							domains.add(Arrays.asList(tempId));
							domains.add(Arrays.asList(tempName));
							if (depictedType.indexOf('(') > -1){
								domains.add(Arrays.asList(((depictedType.split("[(]")[1]).split("[)]")[0])));
							}else{
								domains.add(Arrays.asList(""));
							}
							domains.add(Arrays.asList("0","0","0"));
						}
						Variable slicesVar = VariablesFactory.eINSTANCE.createVariable();
						Image slicesValue = ValuesFactory.eINSTANCE.createImage();
						slicesValue.setData(new Gson().toJson(new IIPJSON(0, "https://www.virtualflybrain.org/fcgi/wlziipsrv.fcgi", wlzUrl.replace("http://www.virtualflybrain.org/data/", "/disk/data/VFB/IMAGE_DATA/"), domains)));
						slicesValue.setFormat(ImageFormat.IIP);
						slicesValue.setReference(tempId);
						slicesVar.setId(tempId + "_slices");
						slicesVar.setName("Stack Viewer Slices");
						slicesVar.getTypes().add(imageType);
						slicesVar.getInitialValues().put(imageType, slicesValue);
						geppettoModelAccess.addVariableToType(slicesVar, parentType);
					} catch (Exception e) {
						System.out.println("Error adding slices:");
						e.printStackTrace();
					}
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
					geppettoModelAccess.addVariableToType(depictsVar, metaDataType);
				}



				// set alt names:
				if (synonyms.size() > 0) {
					Collections.sort(synonyms);
					Variable synVar = VariablesFactory.eINSTANCE.createVariable();
					synVar.setId("synonym");
					synVar.setName("Alternative Names");
					synVar.getTypes().add(htmlType);
					HTML synValue = ValuesFactory.eINSTANCE.createHTML();
					synValue.setHtml(StringUtils.join(synonyms, ", "));
					synVar.getInitialValues().put(htmlType, synValue);
					geppettoModelAccess.addVariableToType(synVar, metaDataType);
				}



				// set thumbnails:
				if (thumb) {
					geppettoModelAccess.addVariableToType(thumbnailVar, metaDataType);
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
					geppettoModelAccess.addVariableToType(exampleVar, metaDataType);
					exampleVar.getInitialValues().put(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE), images);
					parentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType("hasExamples", dependenciesLibrary));
				}

				if (cluster) {

					// set exemplar details:
					String tempHtml = "";
					if (results.getValue("node", 1) != null) {
						resultNode = (Map<String, Object>) results.getValue("node", 1);
						if (resultNode.get("label") != null) {
							tempHtml += "<b>Name:</b> <a href=\"#\" instancepath=\"" + (String) resultNode.get("short_form") + "\">" + (String) resultNode.get("label") + " (" + (String) resultNode.get("short_form") + ")</a><br/>";	                			
						}
						if (depictedType != "") {
							tempHtml += "<b>Types:</b> " + depictedType;
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
					geppettoModelAccess.addVariableToType(typesVar, metaDataType);
					if (("".equalsIgnoreCase(desc)) || (".".equals(desc))) { 
						desc = "A Cluster of morphologically similar neurons using affinity propogation clustering based on NBLAST neuron similarity scores (Costa et al., 2016).";
					}


				}else{

					// set types:
					if (types != "") {
						Variable typesVar = VariablesFactory.eINSTANCE.createVariable();
						if (depictedType != ""){
							typesVar.setId("parentType");
						}else{
							typesVar.setId("type");
						}
						typesVar.setName("Type");
						typesVar.getTypes().add(htmlType);
						HTML typesValue = ValuesFactory.eINSTANCE.createHTML();
						typesValue.setHtml(types);
						typesVar.getInitialValues().put(htmlType, typesValue);
						geppettoModelAccess.addVariableToType(typesVar, metaDataType);
					}

					// set relationships

					if (relationships != "") {
						
// 						List<String> rela = Arrays.asList(StringUtils.split(relationships, "<br/>"));
// 						Collections.sort(rela);
// 						relationships = StringUtils.join(rela, "<br/>");
						Variable relVar = VariablesFactory.eINSTANCE.createVariable();
						relVar.setId("relationships");
						relVar.setName("Relationships");
						relVar.getTypes().add(htmlType);
						HTML relValue = ValuesFactory.eINSTANCE.createHTML();
						relValue.setHtml(relationships);
						relVar.getInitialValues().put(htmlType, relValue);
						geppettoModelAccess.addVariableToType(relVar, metaDataType);
					}

				}

				// set queries
				String badge = "";
				for(Query runnableQuery : geppettoModelAccess.getQueries())
				{
					if(QueryChecker.check(runnableQuery, variable))
					{
						badge = "<i class=\"popup-icon-link fa fa-quora\" />";
						querys += badge + "<a href=\"#\" instancepath=\"" + (String) runnableQuery.getPath() + "\">" + runnableQuery.getDescription().replace("$NAME", variable.getName()) + "</a></br>";
					}else if ((synapticNP || tract) && individual && classVariable.getId()!="notSet"){
						if(QueryChecker.check(runnableQuery, classVariable)){
							badge = "<i class=\"popup-icon-link fa fa-quora\" />";
							querys += badge + "<a href=\"#\" instancepath=\"" + (String) runnableQuery.getPath() + "," + classVariable.getId() + "," + classVariable.getName() + "\">" + runnableQuery.getDescription().replace("$NAME", classVariable.getName()) + "</a></br>";
						}
					}
				}
				
				if (template){
					badge = "<i class=\"popup-icon-link fa gpt-shapeshow\" />";
					querys += badge + "<a href=\"\" title=\"Hide template boundary and show all painted neuroanatomy\" onclick=\""+tempId+".hide();window.addVfbId(JSON.parse("+tempId+"."+tempId+"_slices.getValue().getWrappedObj().value.data).subDomains[1].filter(function(n){ return n != null }));return false;\">Show All Anatomy</a><br/>";
				}



				if (querys != "") {
					Variable queryVar = VariablesFactory.eINSTANCE.createVariable();
					queryVar.setId("queries");
					queryVar.setName("Query for");
					queryVar.getTypes().add(htmlType);
					HTML queryValue = ValuesFactory.eINSTANCE.createHTML();
					queryValue.setHtml(querys);
					queryVar.getInitialValues().put(htmlType, queryValue);
					geppettoModelAccess.addVariableToType(queryVar, metaDataType);
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
					geppettoModelAccess.addVariableToType(description, metaDataType);
				}

				// set references:
				if (refs.size() > 0){
					Set<String> hs = new HashSet<>();
					hs.addAll(refs);
					refs.clear();
					refs.addAll(hs);
					Collections.sort(refs);
					String references = StringUtils.join(refs, "<br/>");
					Variable refVar = VariablesFactory.eINSTANCE.createVariable();
					refVar.setId("references");
					refVar.setName("References");
					refVar.getTypes().add(htmlType);
					HTML refValue = ValuesFactory.eINSTANCE.createHTML();
					refValue.setHtml(references);
					refVar.getInitialValues().put(htmlType, refValue);
					geppettoModelAccess.addVariableToType(refVar, metaDataType);
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
					geppettoModelAccess.addVariableToType(tempVar, metaDataType);
				}

				// set licensing:


				// set downloads:
				if (downloadLink != "") {
					Variable downloads = VariablesFactory.eINSTANCE.createVariable();
					downloads.setId("downloads");
					downloads.setName("Downloads");
					downloads.getTypes().add(htmlType);
					HTML downloadValue = ValuesFactory.eINSTANCE.createHTML();
					downloadValue.setHtml(downloadLink);
					downloads.getInitialValues().put(htmlType, downloadValue);
					geppettoModelAccess.addVariableToType(downloads, metaDataType);
				}

				// set linkouts:

				geppettoModelAccess.addTypeToLibrary(metaDataType, dataSource.getTargetLibrary());

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
			urlString = urlString.replace("//virtualflybrain.org","//www.virtualflybrain.org");
			URL url = new URL(urlString);
			HttpURLConnection huc = (HttpURLConnection) url.openConnection();
			huc.setRequestMethod("HEAD");
			huc.setInstanceFollowRedirects(true);
			int response = huc.getResponseCode();
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
