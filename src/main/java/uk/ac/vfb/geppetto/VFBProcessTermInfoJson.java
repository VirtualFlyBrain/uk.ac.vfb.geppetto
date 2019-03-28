package uk.ac.vfb.geppetto;

import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	// Template space:
	String template = "";

	// START VFB term info schema https://github.com/VirtualFlyBrain/VFB_json_schema/blob/master/json_schema/

	class minimal_entity_info {
		String short_form;
		String iri;
		String label;
		private List<String> types;

		public String intLink() {
			return this.intLink(Collections.<String>emptyList());
		}

		public String extLink() {
			return this.extLink(Collections.<String>emptyList());
		}

		public String intLink(List<String> showTypes) {
			return "<a href=\"#\" data-instancepath=\"" + this.short_form + "\">" + this.label + "</a> "
					+ this.types(showTypes);
		}

		public String extLink(List<String> showTypes) {
			return "<a href=\"" + this.iri + "\" target=\"_blank\">" + this.label + "</a> " + this.types(showTypes);
		}

		public String types(List<String> show) {
			if (this.types.size() > 0 && show.size() > 0) {
				String result = "<span class=\"label types\">";
				for (String type : types) {
					if (show.contains(type)) {
						result = result + "<span class=\"label label-" + type + "\">" + type.replace("_", " ") + "</span> ";
					}
				}
				return result + "</span>";
			}
			return "";
		}

		public List<String> typeList() {
			return this.types;
		}
	}

	class minimal_edge_info {
		private String short_form;
		private String iri;
		private String label;
		private String type;

		public String toString() {
			return String.format("<a href=\"%s\" target=\"_blank\">%s</a>", this.iri, this.label);
		}

		public String label() {
			return this.label;
		}

		public String type() {
			return this.type;
		}
	}

	class term {
		minimal_entity_info core;
		private String iri;
		private List<String> description;
		private List<String> comment;

		public String definition() {
			if ((this.description != null && this.description.size() > 0)
					|| (this.comment != null && this.comment.size() > 0)) {
				return this.description() + " <br /> " + this.comment();
			}
			return "";
		}

		public String description() {
			if (this.description != null && this.description.size() > 0) {
				return "<span class=\"terminfo-description\">" + String.join(" <br /> ", this.description) + "</span>";
			}
			return "";
		}

		public String comment() {
			if (this.comment != null && this.comment.size() > 0) {
				return "<span class=\"terminfo-comment\">" + String.join(" <br /> ", this.description) + "</span>";
			}
			return "";
		}
	}

	class rel {
		private minimal_edge_info relation;
		private minimal_entity_info object;

		public String intLink() {
			return this.intLink(Collections.<String>emptyList());
		}

		public String intLink(List<String> showTypes) {
			return this.relation.label() + " " + this.object.intLink(showTypes);
		}
	}

	class image {
		String image_folder;
		private List<Integer> index;
		private minimal_entity_info template_channel;
		minimal_entity_info template_anatomy;
	}

	class channel_image {
		image image;
		minimal_entity_info channel;
		private minimal_entity_info imaging_technique;
	}

	class anatomy_channel_image {
		minimal_entity_info anatomy;
		channel_image channel_image;
	}

	class domain {
		private List<Integer> index;
		private String center;
		private String folder;
		private minimal_entity_info anatomical_individual;
		private minimal_entity_info anatomical_type;
	}

	class template_channel {
		private List<Integer> index;
		private List<Integer> center;
		private List<Integer> extent;
		private List<Integer> voxel;
		private String orientation;
		String image_folder;
		private minimal_entity_info channel;
	}

	class xref {
		private String link;
		private String link_text;
		private String icon;
		private minimal_entity_info site;

		public String extLink() {
			return extLink(Collections.<String>emptyList());
		}

		public String extLink(List<String> showTypes) {
			String result = "<a href=\"" + this.link + "\" target=\"_blank\">";
			result = result + this.link_text;
			// tack site link as comment on xref for later sorting
			String site = this.site.extLink(showTypes);
			result = "<!--" + site + "-->" + result;
			// also if icon exists then add here:
			// TODO: is this per site or per xref?
			if (this.icon != null && !this.icon.equals("")) {
				result = result + "<img class=\"terminfo-siteicon\" src=\"" + this.icon + "\" />";
			}
			return result;
		}
	}

	class dataset {
		private minimal_entity_info core;
		private String link;
		private String icon;
		private String catmaid_annotation_id;
	}

	class license {
		private minimal_entity_info core;
		private String link;
		private String icon;
		private boolean is_bespoke;
	}

	class dataset_license {
		private dataset dataset;
		private license license;
	}

	class pub {
		private minimal_entity_info core;
		private String microref;
		private String PubMed;
		private String FlyBase;
		private String DOI;
		private String ISBN;

		public String miniref() {
			String result = "";
			String links = "";
			Map<String, String> siteLinks = new HashMap<String, String>();
			// publication links:
			siteLinks.put("FlyBase",
					" <a href=\"http://flybase.org/reports/$ID\" target=\"_blank\" ><i class=\"popup-icon-link gpt-fly\" title=\"FlyBase:$ID\" aria-hidden=\"true\"></i></a>");
			siteLinks.put("DOI",
					" <a href=\"https://doi.org/$ID\" target=\"_blank\" ><i class=\"popup-icon-link gpt-doi\" title=\"doi:$ID\" aria-hidden=\"true\"></i></a>");
			siteLinks.put("PubMed",
					" <a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=$ID\" target=\"_blank\" ><i class=\"popup-icon-link gpt-pubmed\" title=\"PMID:$ID\" aria-hidden=\"true\"></i></a>");
			// TODO: ISBN
			result = core.intLink();
			if (this.FlyBase != null && !this.FlyBase.equals("")) {
				links = links + siteLinks.get("FlyBase").replace("$ID", this.FlyBase);
			}
			if (this.DOI != null && !this.DOI.equals("")) {
				links = links + siteLinks.get("DOI").replace("$ID", this.DOI);
			}
			if (this.PubMed != null && !this.PubMed.equals("")) {
				links = links + siteLinks.get("PubMed").replace("$ID", this.PubMed);
			}
			if (!links.equals("")) {
				links = "<span class=\"terminfo-pubxref\">" + links + "</span>";
				result = result + links;
			}
			result = result + this.core.types(Arrays.asList("Pub"));
			return result;
		}

		public String microref() {
			return this.microref;
		}
	}

	class synonym {
		private String label;
		private String scope;
		private String type;
		// TODO: handle type?

		public String toString() {
			return this.scope.replaceAll("([^_A-Z])([A-Z])", "$1 $2").replace("has ", "") + ": " + this.label;
		}
	}

	class pub_syn {
		private synonym synonym;
		private pub pub;

		public String toString() {
			if (this.pub != null) {
				return this.synonym.toString() + " (" + this.pub.microref() + ")";
			}
			return this.synonym.toString();
		}
	}

	class template_domains {
		private List<domain> domains;
	}

	class vfb_terminfo {
		term term;
		private List<anatomy_channel_image> anatomy_channel_image;
		private List<xref> xrefs;
		private List<pub_syn> pub_syn;
		private List<pub> def_pubs;
		private List<license> license;
		private List<dataset_license> dataset_license;
		private List<rel> relationships;
		private List<rel> related_individuals;
		private List<minimal_entity_info> parents;
		private List<channel_image> channel_image;
		private template_domains template_domains;
		private template_channel template_channel;

		public String definition() {
			if (this.def_pubs != null && this.def_pubs.size() > 0) {
				return this.term.definition() + "<br />" + this.def_pubs.join(", ");
			}
			return this.term.definition();
		}

		public String compileList(String name, Object entity, List<String> showTypes) {
			String result = "<ul class=\"terminfo-" + name + "\">";
			for (Object rel : this.relationships) {
				result = result + "<li>" + rel.intLink(showTypes) + "</li>";
			}
			result = result + "</ul>";
			return result;
		}

		public String xrefList() {
			// turning xrefs into list of html with link for xrefs.
			List<String> results = new ArrayList<>();
			// process xrefs
			for (xrefs xref : this.xrefs) {
				results.add(xref.extLink(Arrays.asList("Site")));
			}
			// sort xrefs alphabetically (by site)
			java.util.Collections.sort(results);
			// itterate to create html list:
			String result = "<ul class=\"terminfo-xrefs\">";
			String site = "";
			for (String xref : results) {
				if (xref.substring(25).equals(site)) {
					result = result + "<li>" + xref + "</li>";
				} else if (site == "") {
					// embed first sites xrefs
					result = result + "<li>" + xref.replace("-->", "<ul><li>").replace("<!--", "") + "</li>";
				} else {
					// close previous and start next site xrefs
					result = result + "</ul></li><li>" + xref.replace("-->", "<ul><li>").replace("<!--", "") + "</li>";
				}
			}
			result = result + "</ul></li></ul>";
			return result;
		}

		public ArrayValue thumbnails() {
			ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
			int j = 0;
			int f = his.channel_image.size();
			for (channel_image image : this.channel_image) {
				// add same template to the begining and others at the end.
				if (template == image.image.template_anatomy.short_form) {
					addImage(image.image.image_folder + "thumbnailT.png",
							image.channel.label.replace("_c", "").replace("-c", ""),
							image.channel.short_form.replace("_c", "").replace("-c", ""), imageArray, j);
					j++;
				} else {
					f--;
					addImage(image.image.image_folder + "thumbnailT.png",
							image.channel.label.replace("_c", "").replace("-c", ""),
							image.channel.short_form.replace("_c", "").replace("-c", ""), imageArray, f);
				}
			}
			return imageArray;
		}

		public ArrayValue examples() {
			ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
			int j = 0;
			int f = his.channel_image.size();
			for (anatomy_channel_image anat : this.anatomy_channel_image) {
				// add same template to the begining and others at the end.
				if (template == anat.channel_image.image.template_anatomy.short_form) {
					addImage(anat.channel_image.image.image.image_folder + "thumbnailT.png",
					anat.channel_image.anatomy.label,
					anat.channel_image.anatomy.short_form, imageArray, j);
					j++;
				} else {
					f--;
					addImage(anat.channel_image.image.image.image_folder + "thumbnailT.png",
					anat.channel_image.anatomy.label,
					anat.channel_image.anatomy.short_form, imageArray, f);
				}
				
			}
			return imageArray;
		}

		public ArrayValue thumbnail() {
			ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
			addImage(this.template_channel.image_folder + "thumbnailT.png",
			this.term.core.label,
			this.term.core.short_form, imageArray, 0);
			return imageArray;
		}

		public String imageFile(List<channel_image> images, String filename) {
			for (channel_image image : images) {
				if (checkURL(image.image.image_folder + filename)) {
					return image.image.image_folder + filename;
				}
			}
			return null;
		}

		public String imageFile(template_channel template, String filename) {
			if (checkURL(template.image_folder + filename)) {
				return template.image_folder + filename;
			}
			return null;
		}

	}

	// END VFB term info schema



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

			List<String> superTypes = Arrays.asList();
			List<String> showTypes = Arrays.asList("Class","Individual","Anatomy","Template","Motor_neuron","Cell","Neuron"); // TODO: Fill in with passed types
			String tempData = "";
			String parentId = "";

			System.out.println("Processing JSON...");
			try{
				Gson gson = new GsonBuilder().create();
				String json = gson.toJson(results);
			}catch (Exception e) {
				System.out.println("Error extracting JSON: " + e.toString());
				e.printStackTrace();
			}

			try{
				vfb_terminfo vfbTerm = new gson().fromJson(json , vfb_terminfo.class);
			}catch (Exception e) {
				System.out.println("Error mapping JSON: " + e.toString());
				e.printStackTrace();
			}
			
			// Note: core already handled by VFBProcessTermInfoCore except types labels

			try{
				// Types
				header = "types";
				superTypes = vfbTerm.term.core.typeList();
				addModelHtml(vfbTerm.term.core.types(showTypes), "Types", "types", metadataType, geppettoModelAccess);

				// Description
				header = "description";
				tempData = vfbTerm.definition();
				if (!tempData.equals("")) {
					addModelHtml(tempData, "Description", "description", metadataType, geppettoModelAccess);
				}

				// parents
				header = "parents";
				if (vfbTerm.parents != null && vfbTerm.parents.size() > 0) {
					tempData = vfbTerm.compileList(header, vfbTerm.parents, showTypes);
					addModelHtml(tempData, "Parents", "type", metadataType, geppettoModelAccess);
					// store first parent as parent type for 3D slice viewer
					parentId = vfbTerm.Parents[0].short_form;
				}

				// relationships
				header = "relationships";
				if (vfbTerm.realtionships != null && vfbTerm.realtionships.size() > 0) {
					tempData = vfbTerm.compileList(header, vfbTerm.realtionships, showTypes);
					addModelHtml(tempData, "Relationships", header, metadataType, geppettoModelAccess);
				}

				// xrefs
				header = "xrefs";
				if (vfbTerm.xrefs != null && vfbTerm.xrefs.size() > 0) {
					tempData = vfbTerm.xrefList();
					addModelHtml(tempData, "Cross References", header, metadataType, geppettoModelAccess);
				}

			


				// Images:
				header = "parentType";
				// retrieving the parent composite type for new image variables
				CompositeType parentType = (CompositeType) variable.getAnonymousTypes().get(0);

				header = "channel_image";
				if (vfbTerm.channel_image != null && vfbTerm.channel_image.size() > 0) {
					// Recording Aligned Template
					if (!template.equals("")){
						template = vfbTerm.channel_image.image.template_anatomy.short_form;
						addModelHtml(vfbTerm.channel_image.image.template_anatomy.intLink(), "Aligned to", "template", metadataType, geppettoModelAccess);
					}
					// thumbnail
					addModelThumbnails(vfbTerm.thumbnails(), "Thumbnail", "thumbnail", metadataType, geppettoModelAccess);
					// OBJ - 3D mesh
					tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume_man.obj");
					if (tempData == null){
						tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume.obj");
					}
					if (tempData != null){
						addModelObj(tempData, "3D volume", variable.getId() + "_obj", parentType, geppettoModelAccess, dataSource);
					}
				
					// SWC - 3D mesh
					tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume.swc");
					if (tempData != null){
						addModelSwc(tempData, "3D Skeleton", variable.getId() + "_swc", parentType, geppettoModelAccess, dataSource);
					}
				
					// Slices - 3D slice viewer
					tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume.wlz");
					if (tempData != null){
						// if (!superTypes.contains("Template")) {
							addModelSlices(tempData, "Stack Viewer Slices", variable.getId() + "_slices", parentType, geppettoModelAccess, dataSource, loadBasicDomain(variable.getName(), variable.getId(), parentId));
						// }
						System.out.println(vfbTerm.channel_image, "Adding WLZ: " + tempData);
					}
				}

				header = "template_channel";
				if (vfbTerm.template_channel != null && vfbTerm.template_channel.image_folder != null && !vfbTerm.template_channel.image_folder.equals("")) {
					// Recording Aligned Template
					if (!template.equals("")){
						template = variable.getId();
					}
					addModelHtml(vfbTerm.term.core.intLink(), "Aligned to", "template", metadataType, geppettoModelAccess);
					// thumbnail
					addModelThumbnails(vfbTerm.thumbnail(), "Thumbnail", "thumbnail", metadataType, geppettoModelAccess);
					// OBJ - 3D mesh
					tempData = vfbTerm.imageFile(vfbTerm.template_channel, "volume_man.obj");
					if (tempData == null){
						tempData = vfbTerm.imageFile(vfbTerm.template_channel, "volume.obj");
					}
					if (tempData != null){
						addModelObj(tempData, "3D volume", variable.getId() + "_obj", parentType, geppettoModelAccess, dataSource);
					}
				
					// SWC - 3D mesh
					tempData = vfbTerm.imageFile(vfbTerm.template_channel, "volume.swc");
					if (tempData != null){
						addModelSwc(tempData, "3D Skeleton", variable.getId() + "_swc", parentType, geppettoModelAccess, dataSource);
					}
				
					// Slices - 3D slice viewer
					tempData = vfbTerm.imageFile(vfbTerm.template_channel, "volume.wlz");
					if (tempData != null){
						// if (!superTypes.contains("Template")) {
							addModelSlices(tempData, "Stack Viewer Slices", variable.getId() + "_slices", parentType, geppettoModelAccess, dataSource, loadBasicDomain(variable.getName(), variable.getId(), parentId));
						// }
						System.out.println(vfbTerm.template_channel, "Adding WLZ: " + tempData);
					}
				}
			
				// examples
				header = "anatomy_channel_image";
				if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
					addModelThumbnails(vfbTerm.examples(), "Examples", "examples", metadataType, geppettoModelAccess);
				}

				// references
				header = "references";
				if (!references.equals("")) {
					addModelHtml(references, "References", "references", metadataType, geppettoModelAccess);
				}

			}catch (Exception e) {
				System.out.println("Error creating " + header + ": " + e.toString());
				e.printStackTrace();
			}

			// String tempId = "";
			// // String tempData = "";
			// String parentId = "";
			// String header = "";
			// String references = "";
			// // List<String> superTypes = Arrays.asList();
			// // List<String> showTypes = Arrays.asList("Class","Individual","Anatomy","Template","Motor_neuron","Cell","Neuron"); // TODO: Fill in with passed types
			
			// // term
			// if (results.getValue("term", 0) != null) {
			// 	Map<String, Object> term = (Map<String, Object>) results.getValue("term", 0);
			// 	// Note: core already handled by VFBProcessTermInfoCore except types labels
			// 	// Types
			// 	superTypes = ((List<String>) ((Map<String, Object>) term.get("core")).get("types"));
			// 	addModelHtml(loadTypes(superTypes, showTypes), "Types", "types", metadataType, geppettoModelAccess);
				
			// 	// Description
			// 	try{
			// 		tempData = "";
			// 		header = "description";
			// 		if (term.get(header) != null && !term.get(header).toString().equals("[]")) {
			// 			tempData = "<span class=\"terminfo-description\">";
			// 			if (((String) term.get(header)).contains("[")) {
			// 				tempData = tempData + loadString((List<String>) term.get(header));
			// 			} else {
			// 				tempData = tempData + loadString((String) term.get(header));
			// 			}
			// 			tempData = tempData + "</span><br />";
			// 		}
			// 		// Comment
			// 		header = "comment";
			// 		if (term.get(header) != null && !term.get(header).toString().equals("[]")) {
			// 			tempData = "<span class=\"terminfo-comment\">";
			// 			if (term.get(header) instanceof String) {
			// 				tempData = tempData + loadString((String) term.get(header));
			// 			} else {
			// 				tempData = tempData + loadString((List<String>) term.get(header));
			// 			}
			// 			tempData = tempData + "</span><br />";
			// 		}
			// 		// Add description references:
			// 		header = "def_pubs";
			// 		if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
			// 			references = loadPublication((List<Object>) results.getValue(header, 0));
			// 			tempData = tempData + "<br />" + references;
			// 		}
			// 		// Adding to model
			// 		if (!"".equals(tempData)) {
			// 			addModelHtml(tempData, "Description", "description", metadataType, geppettoModelAccess);
			// 		}
			// 		term = null;
			// 	}
			// 	catch (Exception e) 
			// 	{
			// 		System.out.println("Error creating description: " + e.toString());
			// 		e.printStackTrace();
			// 	}
			// }
			
			// // parents
			// header = "parents";
			// if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
			// 	tempData = loadEntitys((List<Object>) results.getValue(header, 0), showTypes, header);
			// 	addModelHtml(tempData, "Parents", "type", metadataType, geppettoModelAccess);
			// 	// store first parent as parent type for 3D slice viewer
			// 	parentId = (String) ((Map<String, Object>) ((List<Object>) results.getValue(header, 0)).get(0)).get("short_form");
			// }
			
			// // relationships
			// header = "relationships";
			// if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
			// 	tempData = loadRelationships((List<Object>) results.getValue(header, 0), showTypes);
			// 	addModelHtml(tempData, "Relationships", header, metadataType, geppettoModelAccess);
			// }
			
			// // xrefs
			// header = "xrefs";
			// if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
			// 	tempData = loadXrefs((List<Object>) results.getValue(header, 0));
			// 	addModelHtml(tempData, "Cross References", header, metadataType, geppettoModelAccess);
			// }

			// // Images:

			// // retrieving the parent composite type for new image variables
			// CompositeType parentType = (CompositeType) variable.getAnonymousTypes().get(0);

			// header = "channel_image";
			// if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
			// 	// Recording Aligned Template
			// 	if (!template.equals("")){
			// 		tempData = addAlignedTemplate(((List<Object>) results.getValue(header, 0)), parentType, geppettoModelAccess, dataSource);
			// 		addModelHtml(tempData, "Aligned to", "template", metadataType, geppettoModelAccess);
			// 	}
			// 	// thumbnail
			// 	addModelThumbnails(loadThumbnails(((List<Object>) results.getValue(header, 0))), "Thumbnail", "thumbnail", metadataType, geppettoModelAccess);
			// 	// OBJ - 3D mesh
			// 	tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "volume_man.obj");
			// 	if (tempData == null){
			// 		tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "volume.obj");
			// 	}
			// 	if (tempData != null){
			// 		addModelObj(tempData, "3D volume", variable.getId() + "_obj", parentType, geppettoModelAccess, dataSource);
			// 		System.out.println("Adding OBJ: " + tempData);
			// 	}
			
			// 	// SWC - 3D mesh
			// 	tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "volume.swc");
			// 	if (tempData != null){
			// 		addModelSwc(tempData, "3D Skeleton", variable.getId() + "_swc", parentType, geppettoModelAccess, dataSource);
			// 		System.out.println("Adding SWC: " + tempData);
			// 	}
			
			// 	// Slices - 3D slice viewer
			// 	tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "volume.wlz");
			// 	if (tempData != null){
			// 		// if (!superTypes.contains("Template")) {
			// 			addModelSlices(tempData, "Stack Viewer Slices", variable.getId() + "_slices", parentType, geppettoModelAccess, dataSource, loadBasicDomain(variable.getName(), variable.getId(), parentId));
			// 		// }
			// 		System.out.println("Adding WLZ: " + tempData);
			// 	}
			// }

			// header = "template_channel";
			// if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
			// 	// Recording Aligned Template
			// 	if (!template.equals("")){
			// 		tempData = addAlignedTemplate(((List<Object>) results.getValue(header, 0)), parentType, geppettoModelAccess, dataSource);
			// 		addModelHtml(tempData, "Aligned to", "template", metadataType, geppettoModelAccess);
			// 	}
			// 	// thumbnail
			// 	addModelThumbnails(loadThumbnails(((List<Object>) results.getValue(header, 0))), "Thumbnail", "thumbnail", metadataType, geppettoModelAccess);
			// 	// OBJ - 3D mesh
			// 	tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "volume_man.obj");
			// 	if (tempData == null){
			// 		tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "volume.obj");
			// 	}
			// 	if (tempData != null){
			// 		addModelObj(tempData, "3D volume", variable.getId() + "_obj", parentType, geppettoModelAccess, dataSource);
			// 		System.out.println("Adding OBJ: " + tempData);
			// 	}
			
			// 	// SWC - 3D mesh
			// 	tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "volume.swc");
			// 	if (tempData != null){
			// 		addModelSwc(tempData, "3D Skeleton", variable.getId() + "_swc", parentType, geppettoModelAccess, dataSource);
			// 		System.out.println("Adding SWC: " + tempData);
			// 	}
			
			// 	// Slices - 3D slice viewer
			// 	tempData = loadImageFile(((List<Object>) results.getValue(header, 0)), "volume.wlz");
			// 	if (tempData != null){
			// 		// if (!superTypes.contains("Template")) {
			// 			addModelSlices(tempData, "Stack Viewer Slices", variable.getId() + "_slices", parentType, geppettoModelAccess, dataSource, loadBasicDomain(variable.getName(), variable.getId(), parentId));
			// 		// }
			// 		System.out.println("Adding WLZ: " + tempData);
			// 	}
			// }

			// examples
			// header = "anatomy_channel_image";
			// if (results.getValue(header, 0) != null && !results.getValue(header, 0).toString().equals("[]")) {
			// 	addModelThumbnails(loadThumbnails(((List<Object>) results.getValue(header, 0))), "Examples", "examples", metadataType, geppettoModelAccess);
			// }

		// 	// references
		// 	if (!references.equals("")) {
		// 		addModelHtml(references, "References", "references", metadataType, geppettoModelAccess);
		// 	}

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
	
	// /**
	//  * @param label
	//  * @param reference
	//  * @param parentId
	//  * @return List<List<String>>
	//  */
	// private List<List<String>> loadBasicDomain(String label, String reference, String parentId)
	// {
	// 	try{
	// 		List<List<String>> domains = new ArrayList(new ArrayList());
	// 		domains.add(Arrays.asList(new String[]{"0","0","0",null}));
	// 		domains.add(Arrays.asList(reference));
	// 		domains.add(Arrays.asList(label));
	// 		domains.add(Arrays.asList(parentId));
	// 		domains.add(Arrays.asList("[511, 255, 108]"));
	// 		return domains;
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error creating basic domains for (" + label.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 	}
	// 	return null;
	// }

	// /**
	//  * @param strings
	//  * @return String
	//  */
	// private String loadString(List<String> strings)
	// {
	// 	try{
	// 		// Merging sting list
	// 		return String.join(" <br /> ", strings);
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading strings (" + strings.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 		return "";
	// 	}
	// }
	
	// /**
	//  * @param string
	//  * @return String
	//  */
	// private String loadString(String string)
	// {
	// 	try{
	// 		// Returning String
	// 		return string;
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading string (" + string + ") " + e.toString());
	// 		e.printStackTrace();
	// 		return "";
	// 	}
	// }

	// /**
	//  * @param refs
	//  * @return String
	//  */
	// private String loadPublication(List<Object> refs)
	// {
	// 	try{
	// 		String result = "";
	// 		String links = "";
	// 		Map<String,String> siteLinks = new HashMap<String, String>();
	// 		// publication links:
	// 		siteLinks.put("FlyBase", " <a href=\"http://flybase.org/reports/$ID\" target=\"_blank\" ><i class=\"popup-icon-link gpt-fly\" title=\"FlyBase:$ID\" aria-hidden=\"true\"></i></a>");
	// 		siteLinks.put("DOI", " <a href=\"https://doi.org/$ID\" target=\"_blank\" ><i class=\"popup-icon-link gpt-doi\" title=\"doi:$ID\" aria-hidden=\"true\"></i></a>");
	// 		siteLinks.put("PubMed", " <a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=$ID\" target=\"_blank\" ><i class=\"popup-icon-link gpt-pubmed\" title=\"PMID:$ID\" aria-hidden=\"true\"></i></a>");
	// 		Set<String> sites = siteLinks.keySet();
	// 		for (Object ref:refs){
	// 			if (!result.equals("")){
	// 				result = result + "<br />";
	// 			}
	// 			result = result + loadEntity(((Map<String,Object>) ((Map<String,Object>) ref).get("core")), true, Arrays.asList((String) null));
	// 			links = "";
	// 			for (String site:sites) {
	// 				if (((String) ((Map<String,Object>) ref).get(site)) != null && !((String) ((Map<String,Object>) ref).get(site)).equals("")) {
	// 					links = links + siteLinks.get(site).replace("$ID",((String) ((Map<String,Object>) ref).get(site)));
	// 				}
	// 			}
	// 			if (!links.equals("")) {
	// 				links = "<span class=\"terminfo-pubxref\">" + links + "</span>";
	// 				result = result + links;
	// 			}
	// 			result = result + loadTypes((List<String>) ((Map<String,Object>) ((Map<String,Object>) ref).get("core")).get("types"), Arrays.asList("Pub"));
	// 		}
	// 		// Getting publication ref:
	// 		return result;
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading publications (" + refs.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 		return "";
	// 	}
	// }

	// /**
	//  * @param images
	//  * @return ArrayValue
	//  */
	// private ArrayValue loadThumbnails(List<Object> images)
	// {
	// 	ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
	// 	try{
	// 		int j = 0;
	// 		int f = images.size();
	// 		String url = "";
	// 		String name = "";
	// 		String reference = "";
	// 		for (Object image:images){
	// 			url = ((String) ((Map<String,Object>) ((Map<String,Object>) image).get("image")).get("image_folder")) + "thumbnailT.png";
	// 			// TODO: replace with anatomy values rather than regex from channel:
	// 			name = ((String) ((Map<String,Object>) ((Map<String,Object>) image).get("channel")).get("label")).replace("_c", "").replace("-c", "");
	// 			reference = ((String) ((Map<String,Object>) ((Map<String,Object>) image).get("channel")).get("short_form")).replace("_c", "").replace("-c", "");
	// 			if (template == ""){
	// 				template = ((String) ((Map<String,Object>) ((Map<String,Object>) ((Map<String,Object>) image).get("image")).get("template_anatomy")).get("short_form"));
	// 			}
	// 			if (template == ((String) ((Map<String,Object>) ((Map<String,Object>) ((Map<String,Object>) image).get("image")).get("template_anatomy")).get("short_form"))) {
	// 				addImage(url, name, reference, imageArray, j);
	// 				j++;
	// 			} else {
	// 				f--;
	// 				addImage(url, name, reference, imageArray, f);
	// 			}
	// 		}
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading images (" + images.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 	}
	// 	if (imageArray.getElements().size() > 0) {
	// 		return imageArray;
	// 	}
	// 	return null;
	// }

	// /**
	//  * @param images
	//  * @param filename
	//  * @return String
	//  */
	// private String loadImageFile(List<Object> images, String filename)
	// {
	// 	// find the first (should only be one record in images) that contains filename:
	// 	String imageUrl = "";
	// 	try{
	// 		int j = 0;
	// 		for (Object image:images){
	// 			imageUrl = ((String) ((Map<String,Object>) ((Map<String,Object>) image).get("image")).get("image_folder")) + filename;
	// 			if (checkURL(imageUrl)) {
	// 				return imageUrl;
	// 			}
	// 		}
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading image files (" + images.toString() + filename + ") " + e.toString());
	// 		e.printStackTrace();
	// 	}
	// 	return null;
	// }

	// /**
	//  * @param xrefs
	//  * @return String
	//  */
	// private String loadXrefs(List<Object> xrefs)
	// {
	// 	try{
	// 		// turning xrefs into list of html with link for xrefs.
	// 		List<String> results = new ArrayList<>();
	// 		// process xrefs
	// 		for (Object xref:xrefs) {
	// 			results.add(loadXref((Map<String, Object>) xref));
	// 		}
	// 		// sort xrefs alphabetically (by site) 
	// 		java.util.Collections.sort(results);
	// 		// itterate to create html list:
	// 		String result = "<ul class=\"terminfo-xrefs\">";
	// 		String site = "";
	// 		for (String xref:results) {
	// 			if (xref.substring(25).equals(site)){
	// 				result = result + "<li>" + xref + "</li>";
	// 			}else if (site == ""){
	// 				// embed first sites xrefs
	// 				result = result + "<li>" + xref.replace("-->","<ul><li>").replace("<!--","") + "</li>";
	// 			}else{
	// 				// close previous and start next site xrefs
	// 				result = result + "</ul></li><li>" + xref.replace("-->","<ul><li>").replace("<!--","") + "</li>";
	// 			}
	// 		}
	// 		result = result + "</ul></li></ul>";
			
	// 		return result;
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading xrefs (" + xrefs.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 		return "";
	// 	}
	// }
	
	// /**
	//  * @param xref
	//  * @return String
	//  */
	// private String loadXref(Map<String, Object> xref)
	// {
	// 	try{
	// 		// turning xref into html link.
	// 		// add link (String):
	// 		String result = "<a href=\"" + (String) xref.get("link") + "\" target=\"_blank\">";
	// 		result = result + (String) xref.get("link_text");
	// 		// tack site link as comment on xref for later sorting
	// 		String site = loadEntity((Map<String, Object>) xref.get("site"), false, Arrays.asList("Site"));
	// 		result = "<!--" + site + "-->" + result;
	// 		// also if icon exists then add here:
	// 		// TODO: is this per site or per xref?
	// 		if (!xref.get("icon").equals("")){
	// 			result = result + "<img class=\"terminfo-siteicon\" src=\"" + xref.get("icon") + "\" />";	
	// 		}
	// 		return result;
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading xref (" + xref.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 		return "";
	// 	}
	// }
	
	// /**
	//  * @param entitys
	//  * @param showTypes
	//  * @param subclass
	//  * @return String
	//  */
	// private String loadEntitys(List<Object> entitys, List<String> showTypes, String subclass)
	// {
	// 	try{
	// 		// turning entity list into list of html links for entitys.
	// 		String result = "<ul class=\"terminfo-" + subclass + "\">";
	// 		// itterate to create html list:
	// 		for (Object entity:entitys) {
	// 			// TODO: check if entity is really always internal? If not how to differenciate from iri/short_form?  
	// 			result = result + "<li>" + loadEntity((Map<String,Object>) entity, true, showTypes) + "</li>";
	// 		}
	// 		result = result + "</ul>";
	// 		return result;
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading entitys (" + entitys.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 		return "";
	// 	}
	// }
	
	// /**
	//  * @param rels
	//  * @param showTypes
	//  * @return String
	//  */
	// private String loadRelationships(List<Object> rels, List<String> showTypes)
	// {
	// 	try{
	// 		// turning relationships into list of html with link for entity.
	// 		String result = "<ul class=\"terminfo-rels\">";
	// 		// itterate to create html list:
	// 		for (Object rel:rels) {
	// 			result = result + "<li>" + loadRelationship((Map<String, Object>) rel, showTypes) + "</li>";
	// 		}
	// 		result = result + "</ul>";
	// 		return result;
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading realtionships (" + rels.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 		return "";
	// 	}
	// }
	
	// /**
	//  * @param rel
	//  * @param showTypes
	//  * @return String
	//  */
	// private String loadRelationship(Map<String, Object> rel, List<String> showTypes)
	// {
	// 	try{
	// 		// turning relationship into html with link for entity.
	// 		// add relation (edge):
	// 		String result = loadEdge((Map<String, Object>) rel.get("relation"), false);
	// 		// add object (entity) link:
	// 		result = result + " " + loadEntity((Map<String, Object>) rel.get("object"), true, showTypes);
	// 		return result;
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading realtionship (" + rel.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 		return "";
	// 	}
	// }
	
	// /**
	//  * @param entity
	//  * @param internal
	//  * @param showTypes
	//  * @return String
	//  */
	// private String loadEntity(Map<String, Object> entity, boolean internal, List<String> showTypes)
	// {
	// 	try{
	// 		// turning entity into html link [label](short_form|iri) with type labels span. 
	// 		String short_form = (String) entity.get("short_form");
	// 		String label = (String) entity.get("label");
	// 		String iri = (String) entity.get("iri");
	// 		String types = loadTypes((List<String>) entity.get("types"), showTypes);
		
	// 		if (internal) {
	// 			return "<a href=\"#\" data-instancepath=\"" + short_form + "\">" + label + "</a> " + types;
	// 		}
	// 		return "<a href=\"" + iri + "\" target=\"_blank\">" + label + "</a>" + types;
			
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading entity (" + entity.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 		return "";
	// 	}
	// }
	// /**
	//  * @param types
	//  * @param show
	//  * @return String
	//  */
	// private String loadTypes(List<String> types, List<String> show)
	// {
	// 	try{
	// 		// turning types list into type labels span for thouse in show list.
	// 		if (types.size() > 0 && show.size() > 0){
	// 			String result = "<span class=\"label types\">";
	// 			for (String type:types){
	// 				if (show.contains(type)){
	// 					result = result + "<span class=\"label label-" + type + "\">" + type.replace("_"," ") + "</span> ";
	// 				}
	// 			}
	// 			return result + "</span>";
	// 		}
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading types (" + types.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 	}
	// 	return "";
	// }
	// /**
	//  * @param edge
	//  * @param link
	//  * @return String
	//  */
	// private String loadEdge(Map<String, Object> edge, boolean link)
	// {
	// 	try{
	// 		// turning edge into string or html link [label](iri). 
	// 		String iri = (String) edge.get("iri");
	// 		String label = (String) edge.get("label");
	// 		if (link) {
	// 			return "<a href=\"" + iri + "\" target=\"_blank\">" + label + "</a>";
	// 		}
	// 		return label;
	// 	}
	// 	catch (Exception e)
	// 	{
	// 		System.out.println("Error handling JSON loading edge (" + edge.toString() + ") " + e.toString());
	// 		e.printStackTrace();
	// 		return "";
	// 	}
	// }


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
			slicesValue.setData(new Gson().toJson(new IIPJSON(0, "https://www.virtualflybrain.org/fcgi/wlziipsrv.fcgi", url.replace("https://", "http://").replace("www.virtualflybrain.org","virtualflybrain.org").replace("http://virtualflybrain.org/data/", "/disk/data/VFB/IMAGE_DATA/"), domains)));
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
	 * @param images
	 * @param parentType
	 * @param geppettoModelAccess
	 * @param dataSource
	 * @return
	 */
	private String addAlignedTemplate(List<Object> images, CompositeType parentType, GeppettoModelAccess geppettoModelAccess, DataSource dataSource) 
	{
		String tempLink = null;
		try{
			List<GeppettoLibrary> dependenciesLibrary = dataSource.getDependenciesLibrary();
			if (template.equals("")){
				template = ((String) ((Map<String,Object>) ((Map<String,Object>) ((Map<String,Object>) images.get(0)).get("image")).get("template_anatomy")).get("short_form"));
				System.out.println("Aligned to " + template);
			}
			String tempName = ((String) ((Map<String,Object>) ((Map<String,Object>) ((Map<String,Object>) images.get(0)).get("image")).get("template_anatomy")).get("label"));
			tempLink = "<a href=\"#\" data-instancepath=\"" + template + "\">" + tempName + "</a>";
			// add template short_form as supertype
			parentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(template, dependenciesLibrary));
		} catch (Exception e) {
			System.out.println("Error adding aligned template:");
			e.printStackTrace();
		}
		return tempLink;
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
			importType.setName(reference);
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
			System.out.println("Error adding HTML (" + data + ") " + e.toString());
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
			
			if (images.getElements().size() > 1){
				imageVariable.getInitialValues().put(imageType, images);
			}else{
				imageVariable.getInitialValues().put(imageType, images.getElements().get(0).getInitialValue());
			}
		}
		catch(GeppettoVisitingException e)
		{
			System.out.println("Error adding Thumbnails (" + name + ") " + e.toString());
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
			System.out.println("Error adding String (" + data + ") " + e.toString());
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
