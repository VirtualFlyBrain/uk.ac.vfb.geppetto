package uk.ac.vfb.geppetto;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.util.ModelUtility;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.HTML;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;

/**
 * @author robertcourt
 *
 */

public class VFBProcessTermInfoJson extends AQueryProcessor
{
	// Synonym Icons are set here:
	private enum SynonymIcons
	{
		// Amend icon class here:
		EXACT("fa-bullseye"), BROAD("fa-expand"), NARROW("fa-compress"), RELATED("fa-link"), DEFAULT("fa-question");

		private SynonymIcons(final String text)
		{
			this.text = text;
		}

		private final String text;

		public String getIcon()
		{
			return text;
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.geppetto.core.datasources.IQueryProcessor#process(org.geppetto.model.ProcessQuery, org.geppetto.model.variables.Variable, org.geppetto.model.QueryResults)
	 */
	@Override
	public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException
	{

		try
		{

			// retrieving the metadatatype
			CompositeType metadataType = (CompositeType) ModelUtility.getTypeFromLibrary(variable.getId() + "_metadata", dataSource.getTargetLibrary());

			Type textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
			Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);

			System.out.println("Processing term info...");
			
			
			
			// term
			if (results.getValue("term", 0) != null) {
				Map<String, Object> term = (Map<String, Object>) results.getValue("term", 0);
				//core
				if (term.get("core") != null) {
					Map<String, Object> core = (Map<String, Object>) term.get("core");
					//ID/short_form
					if (core.get("short_form") != null) {
						if (String.valueOf(variable.getId()).equals((String) core.get("short_form"))) {
							tempId = (String) core.get("short_form");
						} else {
							System.out.println("ERROR: Called ID: " + String.valueOf(variable.getId()) + " does not match returned ID: " + (String) core.get("short_form"));
							tempId = (String) core.get("short_form");
						}
					}
					//label
					if (core.get("label") != null) {
						tempName = (String) core.get("label");
					}
			
			// running through returned items - references.
			if(results.getValue("relationship", 0) != null)
			{

				String synonymLinks = "";
				String defRefs = "";
				String relat = "";
				String temp = "";
				String typeLink = "";

				int i = 0;
				while(results.getValue("relationship", i) != null)
				{ // synonyms and refs:
					if((String) ((Map) results.getValue("relationship", i)).get("synonym") != null)
					{
						synonymLinks += "<a href=\"#\" instancepath=\"" + variable.getId() + "\">";
						if(((Map) results.getValue("relationship", i)).get("scope") != null)
						{
							temp = (String) ((Map) results.getValue("relationship", i)).get("scope");
							String icon = SynonymIcons.DEFAULT.getIcon();
							try
							{
								icon = SynonymIcons.valueOf(temp).getIcon();
							}
							catch(IllegalArgumentException ex)
							{
							}

							synonymLinks += "<i class=\"popup-icon fa " + icon + "\" title=\"synonym scope: " + temp + "\" aria-hidden=\"true\"></i>";
						}
						synonymLinks += (String) ((Map) results.getValue("relationship", i)).get("synonym") + "</a>";
						if(results.getValue("relRef", i) != null)
						{
							synonymLinks += " (" + (String) results.getValue("relRef", i) + ")";
							if(results.getValue("relFBrf", i) != null)
							{
								synonymLinks += " <a href=\"http://flybase.org/reports/" + (String) results.getValue("relFBrf", i) + "\" target=\"_blank\" >"
										+ "<i class=\"popup-icon-link gpt-fly\" title=\"FlyBase:" + (String) results.getValue("relFBrf", i) + "\" aria-hidden=\"true\"></i></a>";
							}
							if(results.getValue("relPMID", i) != null)
							{
								synonymLinks += " <a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=" + (String) results.getValue("relPMID", i) + "\" target=\"_blank\" >"
										+ "<i class=\"popup-icon-link gpt-pubmed\" title=\"PMID:" + (String) results.getValue("relPMID", i) + "\" aria-hidden=\"true\"></i></a>";
							}
							if(results.getValue("relDOI", i) != null)
							{
								synonymLinks += " <a href=\" https://doi.org/" + (String) results.getValue("relDOI", i) + "\" target=\"_blank\" >"
										+ "<i class=\"popup-icon-link gpt-doi\" title=\"doi:" + (String) results.getValue("relDOI", i) + "\" aria-hidden=\"true\"></i></a>";
							}

						}
						synonymLinks += "<br/>";
					}
					else
					{
						// definition refs:
						if("def".equals((String) ((Map) results.getValue("relationship", i)).get("typ")))
						{
							if(results.getValue("relRef", i) != null)
							{
								defRefs += (String) results.getValue("relRef", i);
								if(results.getValue("relFBrf", i) != null)
								{
									defRefs += " <a href=\"http://flybase.org/reports/" + (String) results.getValue("relFBrf", i) + "\" target=\"_blank\" >"
											+ "<i class=\"popup-icon-link gpt-fly\" title=\"FlyBase:" + (String) results.getValue("relFBrf", i) + "\" aria-hidden=\"true\"></i></a>";
								}
								if(results.getValue("relPMID", i) != null)
								{
									defRefs += " <a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=" + (String) results.getValue("relPMID", i) + "\" target=\"_blank\" >"
											+ "<i class=\"popup-icon-link gpt-pubmed\" title=\"PMID:" + (String) results.getValue("relPMID", i) + "\" aria-hidden=\"true\"></i></a>";
								}
								if(results.getValue("relDOI", i) != null)
								{
									defRefs += " <a href=\" https://doi.org/" + (String) results.getValue("relDOI", i) + "\" target=\"_blank\" >"
											+ "<i class=\"popup-icon-link gpt-doi\" title=\"doi:" + (String) results.getValue("relDOI", i) + "\" aria-hidden=\"true\"></i></a>";
								}
								defRefs += "<br/>";
							}
							else if(results.getValue("relLink", i) != null)
							{
								defRefs += " <a href=\"http:" + (String) results.getValue("relLink", i) + "\" target=\"_blank\" >";
								defRefs += ((String) results.getValue("relLink", i)).replaceAll("//","").replaceAll("http:","") + "</a>";
								defRefs += "<br/>";
							}
						}
						else
						{
							// relationships and refs:
							if("Related".equals((String) ((Map) results.getValue("relationship", i)).get("__type__")))
							{
								if(((Map) results.getValue("relationship", i)).get("label") != null)
								{
									temp = ((String) ((Map) results.getValue("relationship", i)).get("label")).replace("_", " ");
									relat += temp.substring(0, 1).toUpperCase() + temp.substring(1) + " ";
								}
								if(results.getValue("relName", i) != null)
								{
									if(results.getValue("relId", i) != null)
									{
										relat += "<a href=\"#\" instancepath=\"" + (String) results.getValue("relId", i) + "\">" + (String) results.getValue("relName", i) + "</a> ";
									}
									else
									{
										relat += (String) results.getValue("relName", i) + " ";
									}
								}
								if(results.getValue("relRef", i) != null)
								{
									relat += "(" + (String) results.getValue("relRef", i) + ")";
									if(results.getValue("relFBrf", i) != null)
									{
										relat += " <a href=\"http://flybase.org/reports/" + (String) results.getValue("relFBrf", i) + "\" target=\"_blank\" >"
												+ "<i class=\"popup-icon-link gpt-fly\" title=\"FlyBase:" + (String) results.getValue("relFBrf", i) + "\" aria-hidden=\"true\"></i></a>";
									}
									if(results.getValue("relPMID", i) != null)
									{
										relat += " <a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=" + (String) results.getValue("relPMID", i) + "\" target=\"_blank\" >"
												+ "<i class=\"popup-icon-link gpt-pubmed\" title=\"PMID:" + (String) results.getValue("relPMID", i) + "\" aria-hidden=\"true\"></i></a>";
									}
									if(results.getValue("relDOI", i) != null)
									{
										relat += " <a href=\" https://doi.org/" + (String) results.getValue("relDOI", i) + "\" target=\"_blank\" >"
												+ "<i class=\"popup-icon-link gpt-doi\" title=\"doi:" + (String) results.getValue("relDOI", i) + "\" aria-hidden=\"true\"></i>";
									}
								}
								relat += "<br/>";
							}
							else if("SubClassOf".equals((String) ((Map) results.getValue("relationship", i)).get("__type__")))
							{
								typeLink += "<a href=\"#\" instancepath=\"" + (String) results.getValue("relId", i) + "\">";
								typeLink += (String) results.getValue("relName", i) + "</a>";
								typeLink += "<br/>";
							}
							else if("type".equals((String) ((Map) results.getValue("relationship", i)).get("label")) || "is a".equals((String) ((Map) results.getValue("relationship", i)).get("label")))
							{ // parent type:
								typeLink += "<a href=\"#\" instancepath=\"" + (String) results.getValue("relId", i) + "\">";
								typeLink += (String) results.getValue("relName", i) + "</a>";
								typeLink += "<br/>";
							}
							else
							{
								// relationships:
								if(((Map) results.getValue("relationship", i)).get("label") != null)
								{
									temp = ((String) ((Map) results.getValue("relationship", i)).get("label")).replace("_", " ");
									relat += temp.substring(0, 1).toUpperCase() + temp.substring(1) + " ";
								}
								if(results.getValue("relName", i) != null)
								{
									if(results.getValue("relId", i) != null)
									{
										relat += "<a href=\"#\" instancepath=\"" + (String) results.getValue("relId", i) + "\">" + (String) results.getValue("relName", i) + "</a> ";
									}
									else
									{
										relat += (String) results.getValue("relName", i) + " ";
									}
								}
								if(results.getValue("relFBrf", i) != null)
								{
									relat += "[<a href=\"http://flybase.org/reports/" + (String) results.getValue("relFBrf", i) + "\" target=\"_blank\" >FlyBase:" + (String) results.getValue("relFBrf", i)
											+ "</a>]";
								}
								relat += "<br/>";
							}
						}
					}
					i++;
				}

				// set Definition references:
				if(!"".equals(defRefs))
				{
					System.out.println("References:\n" + defRefs);
					Variable defReferences = VariablesFactory.eINSTANCE.createVariable();
					defReferences.setId("references");
					defReferences.setName("Description References");
					defReferences.getTypes().add(htmlType);
					geppettoModelAccess.addVariableToType(defReferences, metadataType);
					HTML defReferencesValue = ValuesFactory.eINSTANCE.createHTML();
					defReferencesValue.setHtml(defRefs);
					defReferences.getInitialValues().put(htmlType, defReferencesValue);
				}

				// set Synonyms with any related references:
				if(!"".equals(synonymLinks))
				{
					System.out.println("Synonyms:\n" + synonymLinks);
					Variable synonyms = VariablesFactory.eINSTANCE.createVariable();
					synonyms.setId("synonyms");
					synonyms.setName("Alternative names");
					synonyms.getTypes().add(htmlType);
					geppettoModelAccess.addVariableToType(synonyms, metadataType);
					HTML synonymsValue = ValuesFactory.eINSTANCE.createHTML();
					synonymsValue.setHtml(synonymLinks);
					synonyms.getInitialValues().put(htmlType, synonymsValue);
				}

				// set parent Type:
				if(!"".equals(typeLink))
				{
					System.out.println("Type:\n" + typeLink);
					Variable type = VariablesFactory.eINSTANCE.createVariable();
					type.setId("type");
					type.setName("Type");
					type.getTypes().add(htmlType);
					geppettoModelAccess.addVariableToType(type, metadataType);
					HTML typeValue = ValuesFactory.eINSTANCE.createHTML();
					typeValue.setHtml(typeLink);
					type.getInitialValues().put(htmlType, typeValue);
				}

				// set Relationships with any related references:
				if(!"".equals(relat))
				{
					System.out.println("Relationships:\n" + relat);
					Variable relationships = VariablesFactory.eINSTANCE.createVariable();
					relationships.setId("relationships");
					relationships.setName("Relationships");
					relationships.getTypes().add(htmlType);
					geppettoModelAccess.addVariableToType(relationships, metadataType);
					HTML relationshipsValue = ValuesFactory.eINSTANCE.createHTML();
					relationshipsValue.setHtml(relat);
					relationships.getInitialValues().put(htmlType, relationshipsValue);
				}

				if("<a href=\"#\" instancepath=\"VFB_10000005\">cluster</a><br/>".equals(typeLink))
				{
					i = 0;
					String tempId = "";
					String tempThumb = "";
					String tempName = "";

					int j = 0;
					Variable exampleVar = VariablesFactory.eINSTANCE.createVariable();
					exampleVar.setId("examples");
					exampleVar.setName("Members");
					exampleVar.getTypes().add(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE));
					geppettoModelAccess.addVariableToType(exampleVar, metadataType);
					ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();
					while(results.getValue("relationship", i) != null)
					{
						if("has_member".equals((String) ((Map) results.getValue("relationship", i)).get("label")))
						{
							if(results.getValue("relName", i) != null)
							{
								tempId = (String) results.getValue("relId", i);
								tempThumb = "http://www.virtualflybrain.org/data/VFB/i/" + tempId.substring(4, 8) + "/" + tempId.substring(8) + "/thumbnailT.png";
								tempName = (String) results.getValue("relName", i);
								System.out.println("Adding Cluster Image: " + tempId + " " + tempName + " " + tempThumb);
								if(checkURL(tempThumb))
								{
									addImage(tempThumb, tempName, tempId, images, j);
									j++;
								}
							}
						}
						i++;
					}
					exampleVar.getInitialValues().put(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE), images);
				}

			}

		}
		catch(GeppettoVisitingException e)
		{
			System.out.println(e);
			throw new GeppettoDataSourceException(e);
		}
		catch(Exception e)
		{
			System.out.println(e);
		}

		return results;
	}
	
	/**
	 * @param xref
	 * @param showTypes
	 */
	private String loadXref(Map<String, Object> xref)
	{
		try{
			// turning xref into html link.
			// add link (String):
			String result = "<a href=\"" + (String) xref.get("link") + "\" target=\"_blank\">";
			result = result + (String) xref.get("link_text");
			// tack site link as comment on xref for later sorting
			String site = loadEntity((Map<String, Object>) xref.get("site"), false, ["Site"]);
			result = "<!--" + site + "-->" + result;
			// also if icon exists then add here:
			// TODO: is this per site or per xref?
			if (!xref.get("icon").equals("")){
				result = result + "<img class=\"terminfo-siteicon\" src=\"" + xref.get("icon") + "\" />";	
			}
			return result;
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON for " + variable.getId() + " loading realtionship (" + rel.toString() + ") " + e.toString());
			return "";
		}
	}
	
	/**
	 * @param entitys
	 * @param showTypes
	 * @param subclass
	 */
	private String loadEntitys(List<Object> entitys, List<String> showTypes, String subclass)
	{
		try{
			// turning entity list into list of html links for entitys.
			String result = "<ul class=\"terminfo-" + subclass + "\">";
			// itterate to create html list:
			for (Map<String, Object> entity:entitys) {
				// TODO: check if entity is really always internal? If not how to differenciate from iri/short_form?  
				result = result + "<li>" + loadEntity(entity, true, showTypes) + "</li>";
			}
			result = result + "</ul>";
			return result;
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON for " + variable.getId() + " loading entitys (" + entitys.toString() + ") " + e.toString());
			return "";
		}
	}
	
	/**
	 * @param rels
	 * @param showTypes
	 */
	private String loadRelationships(List<Object> rels, List<String> showTypes)
	{
		try{
			// turning relationships into list of html with link for entity.
			String result = "<ul class=\"terminfo-rels\">";
			// itterate to create html list:
			for (Map<String, Object> rel:rels) {
				result = result + "<li>" + loadRelationship(rel, showTypes) + "</li>";
			}
			result = result + "</ul>";
			return result;
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON for " + variable.getId() + " loading realtionships (" + rels.toString() + ") " + e.toString());
			return "";
		}
	}
	
	/**
	 * @param rel
	 * @param showTypes
	 */
	private String loadRelationship(Map<String, Object> rel, List<String> showTypes)
	{
		try{
			// turning relationship into html with link for entity.
			// add relation (edge):
			String result = loadEdge((Map<String, Object>) rel.get("relation"), false);
			// add object (entity) link:
			result = result + " " + loadEntity((Map<String, Object>) rel.get("object"), true, showTypes);
			return result;
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON for " + variable.getId() + " loading realtionship (" + rel.toString() + ") " + e.toString());
			return "";
		}
	}
	
	/**
	 * @param entity
	 * @param internal
	 * @param showTypes
	 */
	private String loadEntity(Map<String, Object> entity, boolean internal, List<String> showTypes)
	{
		try{
			// turning entity into html link [label](short_form|iri) with type labels span. 
			String short_form = entity.get("short_form");
			String label = entity.get("label");
			String types = loadTypes((List<String>) entity.get("types"), showTypes);
		
			if (internal) {
				return "<a href=\"#\" data-instancepath=\"" + short_from + "\">" + label + "</a> " + types;
			}
			return "<a href=\"" + iri + "\" target=\"_blank\">" + label + "</a>" + types;
			
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON for " + variable.getId() + " loading entity (" + entity.toString() + ") " + e.toString());
			return "";
		}
	}
	/**
	 * @param types
	 * @param show
	 */
	private String loadTypes(List<String> types, List<String> show)
	{
		try{
			// turning types list into type labels span for thouse in show list.
			String result = "<span class=\"label types\">";
			for (String type:types){
				if (show.contains(type)){
					result = result + "<span class=\"label label-" + type + "\">" + type + "</span> ";
				}
			}
			return result + "</span>";
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON for " + variable.getId() + " loading types (" + types.toString() + ") " + e.toString());
			return "";
		}
	}
	/**
	 * @param edge
	 * @param link
	 */
	private String loadEdge(Map<String, Object> edge, boolean link)
	{
		try{
			// turning edge into string or html link [label](iri). 
			String iri = entity.get("iri");
			String label = entity.get("label");
			if (link) {
				return "<a href=\"" + iri + "\" target=\"_blank\">" + label + "</a>";
			}
			return label;
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON for " + variable.getId() + " loading edge (" + edge.toString() + ") " + e.toString());
			return "";
		}
	}
	/**
	 * @param data
	 * @param name
	 * @param images
	 * @param i
	 */
	private void addImage(String data, String name, String reference, ArrayValue images, int i)
	{
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
	private boolean checkURL(String urlString)
	{
		try
		{
			urlString = urlString.replace("http://www.virtualflybrain.org/data/VFB","http://www.virtualflybrain.org/data/VFB");
			URL url = new URL(urlString);
			HttpURLConnection huc = (HttpURLConnection) url.openConnection();
			huc.setRequestMethod("HEAD");
			huc.setInstanceFollowRedirects(false);
			return (huc.getResponseCode() == HttpURLConnection.HTTP_OK);
		}
		catch(Exception e)
		{
			System.out.println("Error checking url (" + urlString + ") " + e.toString());
			return false;
		}
	}

}
