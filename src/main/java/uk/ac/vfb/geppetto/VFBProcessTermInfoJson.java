package uk.ac.vfb.geppetto;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.util.ModelUtility;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.Type;
import org.geppetto.model.values.HTML;
import org.geppetto.model.values.Image;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.model.values.Text;
import org.geppetto.model.types.ImportType;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.datasources.DataSourceLibraryConfiguration;

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

			System.out.println("Processing JSON...");
			
			String tempId = "";
			String tempData = "";
			String parentId = null;
			String header = "";
			List<String> superTypes = Arrays.asList();
			List<String> showTypes = Arrays.asList("Class","Individual","Anatomy","Template","Motor_neuron","Cell","Neuron"); // TODO: Fill in with passed types
			
			// term
			if (results.getValue("term", 0) != null) {
				Map<String, Object> term = (Map<String, Object>) results.getValue("term", 0);
				// Note: core already handled by VFBProcessTermInfoCore except types labels
				// Types
				superTypes = ((List<String>) ((Map<String, Object>) term.get("core")).get("types"));
				addModelHtml(loadTypes(superTypes, showTypes), "Types", "types", metadataType, geppettoModelAccess);
				
				// Description
				try{
					tempData = "";
					header = "description";
					if (term.get(header) != null && !term.get(header).toString().equals("[]")) {
						tempData = "<span class=\"terminfo-description\">";
						if (((String) term.get(header)).contains("[")) {
							tempData = tempData + loadString((List<String>) term.get(header));
						} else {
							tempData = tempData + loadString((String) term.get(header));
						}
						tempData = tempData + "</span><br />";
					}
					// Comment
					header = "comment";
					if (term.get(header) != null && !term.get(header).toString().equals("[]")) {
						tempData = "<span class=\"terminfo-comment\">";
						if (term.get(header) instanceof String) {
							tempData = tempData + loadString((String) term.get(header));
						} else {
							tempData = tempData + loadString((List<String>) term.get(header));
						}
						tempData = tempData + "</span><br />";
					}
					// Adding to model
					if (!"".equals(tempData)) {
						addModelHtml(tempData, "Description", "description", metadataType, geppettoModelAccess);
					}
					term = null;
				}
				catch (Exception e) 
				{
					System.out.println("Error creating description: " + e.toString());
					e.printStackTrace();
				}
			}
			
			// parents
			header = "parents";
			if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
				tempData = loadEntitys((List<Object>) results.getValue(header, 0), showTypes, header);
				addModelHtml(tempData, "Parents", header, metadataType, geppettoModelAccess);
				// store first parent as parent type for 3D slice viewer
				parentId = (String) ((Map<String, Object>) ((List<Object>) results.getValue(header, 0)).get(0)).get("short_form");
			}
			
			// relationships
			header = "relationships";
			if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
				tempData = loadRelationships((List<Object>) results.getValue(header, 0), showTypes);
				addModelHtml(tempData, "Relationships", header, metadataType, geppettoModelAccess);
			}
			
			// xrefs
			header = "xrefs";
			if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
				tempData = loadXrefs((List<Object>) results.getValue(header, 0));
				addModelHtml(tempData, "Cross References", header, metadataType, geppettoModelAccess);
			}

			// Images:

			// retrieving the parent composite type for new image variables
			CompositeType parentType = (CompositeType) variable.getAnonymousTypes().get(0);
			header = "channel_image";
			if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
				// thumbnail
				addModelThumbnails(loadThumbnails(((List<Object>) results.getValue(header, 0))), "Thumbnail", "thumbnail", metadataType, geppettoModelAccess);
				// OBJ - 3D mesh
				tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "/volume_man.obj");
				if (tempData == null){
					tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "/volume.obj");
				}
				if (tempData != null){
					addModelObj(tempData, "3D volume", variable.getId() + "_obj", parentType, geppettoModelAccess, dataSource);
				}
			
				// SWC - 3D mesh
				tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "/volume.swc");
				if (tempData != null){
					addModelSwc(tempData, "3D Skeleton", variable.getId() + "_swc", parentType, geppettoModelAccess, dataSource);
				}
			
				// Slices - 3D slice viewer
				tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "/volume.wlz");
				if (tempData != null){
					if (!superTypes.contains("Template")) {
						addModelSlices(tempData, "3D Stack", variable.getId() + "_wlz", parentType, geppettoModelAccess, dataSource, loadBasicDomain(variable.getName(), variable.getId(), parentId));
					}
				}
			}

			// examples
			header = "anatomy_channel_image";
			if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
				addModelThumbnails(loadThumbnails(((List<Object>) results.getValue(header, 0))), "Examples", "examples", metadataType, geppettoModelAccess);
			}

		}
		catch(GeppettoVisitingException e)
		{
			System.out.println(e);
			throw new GeppettoDataSourceException(e);
		}
		catch (Exception e) 
		{
			System.out.println("Error creating metadata: " + e.toString());
			e.printStackTrace();
		}

		return results;
	}
	
	/**
	 * @param label
	 * @param reference
	 * @param parentId
	 * @return List<List<String>>
	 */
	private List<List<String>> loadBasicDomain(String label, String reference, String parentId)
	{
		try{
			List<List<String>> domains = new ArrayList(new ArrayList());
			domains.add(Arrays.asList("0","0","0"));
			domains.add(Arrays.asList(reference));
			domains.add(Arrays.asList(label));
			domains.add(Arrays.asList(parentId));
			domains.add(Arrays.asList("0","0","0"));
			return domains;
		}
		catch (Exception e)
		{
			System.out.println("Error creating basic domains for (" + label.toString() + ") " + e.toString());
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param strings
	 * @return String
	 */
	private String loadString(List<String> strings)
	{
		try{
			// Merging sting list
			return String.join(" <br /> ", strings);
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON loading strings (" + strings.toString() + ") " + e.toString());
			e.printStackTrace();
			return "";
		}
	}
	
	/**
	 * @param string
	 * @return String
	 */
	private String loadString(String string)
	{
		try{
			// Returning String
			return string;
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON loading string (" + string + ") " + e.toString());
			e.printStackTrace();
			return "";
		}
	}

	/**
	 * @param images
	 * @return ArrayValue
	 */
	private ArrayValue loadThumbnails(List<Object> images)
	{
		ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
		try{
			int j = 0;
			String url = "";
			String name = "";
			String reference = "";
			for (Object image:images){
				url = ((String) ((Map<String,Object>) ((Map<String,Object>) image).get("image")).get("image_folder")) + "thumbnail.png";
				// TODO: replace with anatomy values rather than regex from channel:
				name = ((String) ((Map<String,Object>) ((Map<String,Object>) image).get("channel")).get("label")).replace("_c", "").replace("-c", "");
				reference = ((String) ((Map<String,Object>) ((Map<String,Object>) image).get("channel")).get("short_form")).replace("VFBc_","VFB_");
				addImage(url, name, reference, imageArray, j);
			}
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON loading images (" + images.toString() + ") " + e.toString());
			e.printStackTrace();
		}
		if (imageArray.getElements().size() > 0) {
			return imageArray;
		}
		return null;
	}

	/**
	 * @param images
	 * @param filename
	 * @return String
	 */
	private String loadImageFile(List<Object> images, String filename)
	{
		// find the first (should only be one record in images) that contains filename:
		String imageUrl = "";
		try{
			int j = 0;
			for (Object image:images){
				imageUrl = ((String) ((Map<String,Object>) ((Map<String,Object>) image).get("image")).get("image_folder")) + filename;
				if (checkURL(imageUrl)) {
					return imageUrl;
				}
			}
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON loading image files (" + images.toString() + filename + ") " + e.toString());
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param xrefs
	 * @return String
	 */
	private String loadXrefs(List<Object> xrefs)
	{
		try{
			// turning xrefs into list of html with link for xrefs.
			List<String> results = new ArrayList<>();
			// process xrefs
			for (Object xref:xrefs) {
				results.add(loadXref((Map<String, Object>) xref));
			}
			// sort xrefs alphabetically (by site) 
			java.util.Collections.sort(results);
			// itterate to create html list:
			String result = "<ul class=\"terminfo-xrefs\">";
			String site = "";
			for (String xref:results) {
				if (xref.substring(25).equals(site)){
					result = result + "<li>" + xref + "</li>";
				}else if (site == ""){
					// embed first sites xrefs
					result = result + "<li>" + xref.replace("-->","<ul><li>").replace("<!--","") + "</li>";
				}else{
					// close previous and start next site xrefs
					result = result + "</ul></li><li>" + xref.replace("-->","<ul><li>").replace("<!--","") + "</li>";
				}
			}
			result = result + "</ul></li></ul>";
			
			return result;
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON loading xrefs (" + xrefs.toString() + ") " + e.toString());
			e.printStackTrace();
			return "";
		}
	}
	
	/**
	 * @param xref
	 * @return String
	 */
	private String loadXref(Map<String, Object> xref)
	{
		try{
			// turning xref into html link.
			// add link (String):
			String result = "<a href=\"" + (String) xref.get("link") + "\" target=\"_blank\">";
			result = result + (String) xref.get("link_text");
			// tack site link as comment on xref for later sorting
			String site = loadEntity((Map<String, Object>) xref.get("site"), false, Arrays.asList("Site"));
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
			System.out.println("Error handling JSON loading xref (" + xref.toString() + ") " + e.toString());
			e.printStackTrace();
			return "";
		}
	}
	
	/**
	 * @param entitys
	 * @param showTypes
	 * @param subclass
	 * @return String
	 */
	private String loadEntitys(List<Object> entitys, List<String> showTypes, String subclass)
	{
		try{
			// turning entity list into list of html links for entitys.
			String result = "<ul class=\"terminfo-" + subclass + "\">";
			// itterate to create html list:
			for (Object entity:entitys) {
				// TODO: check if entity is really always internal? If not how to differenciate from iri/short_form?  
				result = result + "<li>" + loadEntity((Map<String,Object>) entity, true, showTypes) + "</li>";
			}
			result = result + "</ul>";
			return result;
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON loading entitys (" + entitys.toString() + ") " + e.toString());
			e.printStackTrace();
			return "";
		}
	}
	
	/**
	 * @param rels
	 * @param showTypes
	 * @return String
	 */
	private String loadRelationships(List<Object> rels, List<String> showTypes)
	{
		try{
			// turning relationships into list of html with link for entity.
			String result = "<ul class=\"terminfo-rels\">";
			// itterate to create html list:
			for (Object rel:rels) {
				result = result + "<li>" + loadRelationship((Map<String, Object>) rel, showTypes) + "</li>";
			}
			result = result + "</ul>";
			return result;
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON loading realtionships (" + rels.toString() + ") " + e.toString());
			e.printStackTrace();
			return "";
		}
	}
	
	/**
	 * @param rel
	 * @param showTypes
	 * @return String
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
			System.out.println("Error handling JSON loading realtionship (" + rel.toString() + ") " + e.toString());
			e.printStackTrace();
			return "";
		}
	}
	
	/**
	 * @param entity
	 * @param internal
	 * @param showTypes
	 * @return String
	 */
	private String loadEntity(Map<String, Object> entity, boolean internal, List<String> showTypes)
	{
		try{
			// turning entity into html link [label](short_form|iri) with type labels span. 
			String short_form = (String) entity.get("short_form");
			String label = (String) entity.get("label");
			String iri = (String) entity.get("iri");
			String types = loadTypes((List<String>) entity.get("types"), showTypes);
		
			if (internal) {
				return "<a href=\"#\" data-instancepath=\"" + short_form + "\">" + label + "</a> " + types;
			}
			return "<a href=\"" + iri + "\" target=\"_blank\">" + label + "</a>" + types;
			
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON loading entity (" + entity.toString() + ") " + e.toString());
			e.printStackTrace();
			return "";
		}
	}
	/**
	 * @param types
	 * @param show
	 * @return String
	 */
	private String loadTypes(List<String> types, List<String> show)
	{
		try{
			// turning types list into type labels span for thouse in show list.
			String result = "<span class=\"label types\">";
			for (String type:types){
				if (show.contains(type)){
					result = result + "<span class=\"label label-" + type + "\">" + type.replace("_"," ") + "</span> ";
				}
			}
			return result + "</span>";
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON loading types (" + types.toString() + ") " + e.toString());
			e.printStackTrace();
			return "";
		}
	}
	/**
	 * @param edge
	 * @param link
	 * @return String
	 */
	private String loadEdge(Map<String, Object> edge, boolean link)
	{
		try{
			// turning edge into string or html link [label](iri). 
			String iri = (String) edge.get("iri");
			String label = (String) edge.get("label");
			if (link) {
				return "<a href=\"" + iri + "\" target=\"_blank\">" + label + "</a>";
			}
			return label;
		}
		catch (Exception e)
		{
			System.out.println("Error handling JSON loading edge (" + edge.toString() + ") " + e.toString());
			e.printStackTrace();
			return "";
		}
	}


	/**
	 * @param url
	 * @param name
	 * @param reference
	 * @param parentType
	 * @return
	 */
	private void addModelSlices(String url, String name, String reference, CompositeType parentType, GeppettoModelAccess geppettoModelAccess, DataSource dataSource, List<List<String>> domains) throws GeppettoVisitingException
	{
		try{
			Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
			Variable slicesVar = VariablesFactory.eINSTANCE.createVariable();
			Image slicesValue = ValuesFactory.eINSTANCE.createImage();
			slicesValue.setData(new Gson().toJson(new IIPJSON(0, "https://www.virtualflybrain.org/fcgi/wlziipsrv.fcgi", url.replace("http://www.virtualflybrain.org/data/", "/disk/data/VFB/IMAGE_DATA/"), domains)));
			slicesValue.setFormat(ImageFormat.IIP);
			slicesValue.setReference(reference);
			slicesVar.setId(reference);
			slicesVar.setName(name);
			slicesVar.getTypes().add(imageType);
			slicesVar.getInitialValues().put(imageType, slicesValue);
			geppettoModelAccess.addVariableToType(slicesVar, parentType);
		} catch (Exception e) {
			System.out.println("Error adding slices:");
			e.printStackTrace();
		}
	}

	/**
	 * @param url
	 * @param name
	 * @param reference
	 * @param parentType
	 * @return
	 */
	private void addModelSwc(String url, String name, String reference, CompositeType parentType, GeppettoModelAccess geppettoModelAccess, DataSource dataSource)
	{
		try{
			Variable Variable = VariablesFactory.eINSTANCE.createVariable();
			ImportType importType = TypesFactory.eINSTANCE.createImportType();
			importType.setUrl(url);
			importType.setId(reference);
			importType.setModelInterpreterId("swcModelInterpreter");
			Variable.getTypes().add(importType);
			Variable.setId(reference);
			Variable.setName(name);
			parentType.getVariables().add(Variable);
			geppettoModelAccess.addTypeToLibrary(importType, getLibraryFor(dataSource, "swc"));
		}
		catch (Exception e)
		{
			System.out.println("Error adding SWC to model (" + reference + ") " + e.toString());
			e.printStackTrace();
		}
	}

	/**
	 * @param url
	 * @param name
	 * @param reference
	 * @param parentType
	 * @return
	 */
	private void addModelObj(String url, String name, String reference, CompositeType parentType, GeppettoModelAccess geppettoModelAccess, DataSource dataSource)
	{
		try{
			Variable Variable = VariablesFactory.eINSTANCE.createVariable();
			ImportType importType = TypesFactory.eINSTANCE.createImportType();
			importType.setUrl(url);
			importType.setId(reference);
			importType.setModelInterpreterId("objModelInterpreterService");
			Variable.getTypes().add(importType);
			Variable.setId(reference);
			Variable.setName(name);
			parentType.getVariables().add(Variable);
			geppettoModelAccess.addTypeToLibrary(importType, getLibraryFor(dataSource, "obj"));
		}
		catch (Exception e)
		{
			System.out.println("Error adding OBJ to model (" + reference + ") " + e.toString());
			e.printStackTrace();
		}
	}

	/**
	 * @param data
	 * @param name
	 * @param reference
	 * @param metadataType
	 * @return
	 */
	private void addModelHtml(String data, String name, String reference, CompositeType metadataType, GeppettoModelAccess geppettoModelAccess) throws GeppettoVisitingException
	{
		try{
			Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			Variable label = VariablesFactory.eINSTANCE.createVariable();
			label.setId(reference);
			label.setName(name);
			label.getTypes().add(htmlType);
			HTML labelValue = ValuesFactory.eINSTANCE.createHTML();
			label.getInitialValues().put(htmlType, labelValue);
			labelValue.setHtml(data);
			geppettoModelAccess.addVariableToType(label, metadataType);
		}
		catch(GeppettoVisitingException e)
		{
			System.out.println(e);
			e.printStackTrace();
			throw new GeppettoVisitingException(e);
		}
	}


	/**
	 * @param images
	 * @param name
	 * @param reference
	 * @param metadataType
	 * @return
	 */
	private void addModelThumbnails(ArrayValue images, String name, String reference, CompositeType metadataType, GeppettoModelAccess geppettoModelAccess) throws GeppettoVisitingException
	{
		try{
			Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
			Variable imageVariable = VariablesFactory.eINSTANCE.createVariable();
			imageVariable.setId(reference);
			imageVariable.setName(name);
			imageVariable.getTypes().add(imageType);
			geppettoModelAccess.addVariableToType(imageVariable, metadataType);
			imageVariable.getInitialValues().put(imageType, images);
		}
		catch(GeppettoVisitingException e)
		{
			System.out.println(e);
			e.printStackTrace();
			throw new GeppettoVisitingException(e);
		}
	}
	
	/**
	 * @param data
	 * @param name
	 * @param reference
	 * @param metadataType
	 * @return
	 */
	private void addModelString(String data, String name, String reference, CompositeType metadataType, GeppettoModelAccess geppettoModelAccess) throws GeppettoVisitingException
	{
		try
		{
			Type textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
			Variable label = VariablesFactory.eINSTANCE.createVariable();
			label.setId(reference);
			label.setName(name);
			label.getTypes().add(textType);
			Text labelValue = ValuesFactory.eINSTANCE.createText();
			label.getInitialValues().put(textType, labelValue);
			labelValue.setText(data);
			geppettoModelAccess.addVariableToType(label, metadataType);
		}
		catch(GeppettoVisitingException e)
		{
			System.out.println(e);
			e.printStackTrace();
			throw new GeppettoVisitingException(e);
		}
	}
	

	/**
	 * @param data
	 * @param name
	 * @param reference
	 * @param images
	 * @param i
	 * @return
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
	 * @return boolean
	 */
	private boolean checkURL(String urlString)
	{
		try
		{
			URL url = new URL(urlString);
			HttpURLConnection huc = (HttpURLConnection) url.openConnection();
			huc.setRequestMethod("HEAD");
			huc.setInstanceFollowRedirects(false);
			return (huc.getResponseCode() == HttpURLConnection.HTTP_OK);
		}
		catch(Exception e)
		{
			System.out.println("Error checking url (" + urlString + ") " + e.toString());
			e.printStackTrace();
			return false;
		}
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
