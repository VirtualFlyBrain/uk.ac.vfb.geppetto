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
import java.util.Collections;

import com.google.gson.Gson;
import com.sun.org.apache.bcel.internal.generic.Select;

import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.datasources.QueryChecker;
import org.geppetto.model.datasources.Query;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DataSourceLibraryConfiguration;

import org.geppetto.core.model.GeppettoModelAccess;

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
import org.geppetto.model.values.Text;
import org.geppetto.model.types.ImportType;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.GeppettoLibrary;

import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.GeppettoSerializer;

/**
 * @author robertcourt
 *
 */



public class VFBProcessTermInfoJson extends AQueryProcessor
{

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
			String result = "";
			if (this.description != null && this.description.size() > 0) {
				result = result + this.description();
			}
			if (this.comment != null && this.comment.size() > 0) {
				result = result + "<br /><span class=\"terminfo-comment-title\">Comment</span><br />" + this.comment();
			}
			return result;
		}

		private String description() {
			if (this.description != null && this.description.size() > 0) {
				return "<span class=\"terminfo-description\">" + this.highlightLinks(String.join(" <br /> ", this.description)) + "</span>";
			}
			return "";
		}

		private String comment() {
			if (this.comment != null && this.comment.size() > 0) {
				return "<span class=\"terminfo-comment\">" + this.highlightLinks(String.join(" <br /> ", this.comment)) + "</span>";
			}
			return "";
		}

		/**
		 * @param text
		 */
		private String highlightLinks(String text) {
			try {
				text = text.replaceAll("([F,V,G][A-z]*)[:,_](\\d{5}[0-9]*\\b)", "<a href=\"#\" data-instancepath=\"$1_$2\" title=\"$1_$2\" ><i class=\"fa fa-info-circle\"></i></a>");
				return text;
			} catch (Exception e) {
				System.out.println("Error highlighting links in (" + text + ") " + e.toString());
				return text;
			}
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
		private List<Double> index;
		private minimal_entity_info template_channel;
		minimal_entity_info template_anatomy;
	}

	class channel_image {
		image image;
		minimal_entity_info channel;
		private minimal_entity_info imaging_technique;

		public String getUrl(String pre, String post){
			String result = "";
			if (this.image != null && this.image.image_folder != null && !this.image.image_folder.equals("")){
				result = this.image.image_folder.replace("http://","https://");
			}
			if (pre != null && !pre.equals("")){
				result = pre + result;
			}
			if (post != null && !post.equals("")){
				result = result + post;
			}
			return result;
		}
	}

	class anatomy_channel_image {
		minimal_entity_info anatomy;
		channel_image channel_image;

		public String getUrl(String pre, String post){
			return channel_image.getUrl(pre, post);
		}
	}

	class domain {
		private List<Double> index;
		private List<Double> center;
		private String folder;
		private minimal_entity_info anatomical_individual;
		private minimal_entity_info anatomical_type;
	}

	class template_channel {
		private List<Double> index;
		private List<Double> center;
		private List<Double> extent;
		private List<Double> voxel;
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
				result = result + "<img class=\"terminfo-siteicon\" src=\"" + secureUrl(this.icon) + "\" />";
			}
			result = result + "</a>";
			return result;
		}

		private String secureUrl(String url) {
			try{
				if (checkURL(url.replace("http://","https://"))){
					return url.replace("http://","https://");
				}
			}catch(Exception e){
				System.out.println("Error securing url (" + url + ") " + e.toString());
				e.printStackTrace();
			}
			return url;
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

	}

	class dataset {
		private minimal_entity_info core;
		private String link;
		private String icon;
		private String catmaid_annotation_id;

		public String extLink() {
			String result = "<a href=\"" + this.link + "\" target=\"_blank\">";
			if (this.icon != null && !this.icon.equals("")) {
				result = result + "<img class=\"terminfo-dataseticon\" src=\"" + secureUrl(this.icon) + "\" title=\"" + this.core.label + "\"/>";
			}else{
				result = result + this.core.label;
			}
			result = result + "</a>";	
			return result;
		}

		public String intLink() {
			String result = this.core.intLink();
			if (this.icon != null && !this.icon.equals("")) {
				result = result.replace(this.core.label,this.core.label + " <img class=\"terminfo-dataseticon\" src=\"" + secureUrl(this.icon) + "\" title=\"" + this.core.label + "\"/>");
			}
			if (this.link != null && !this.link.equals("")){
				if (this.link.toLowerCase().contains("flybase.org")) {
					result = result + "<a href=\"" + this.link + "\" target=\"_blank\"><i class=\"popup-icon-link gpt-fly\"></i></a>";
				}else if (this.link.toLowerCase().contains("nih.gov")) {
					result = result + "<a href=\"" + this.link + "\" target=\"_blank\"><i class=\"popup-icon-link gpt-pubmed\"></i></a>";
				}else if (this.link.toLowerCase().contains("doi.org")) {
					result = result + "<a href=\"" + this.link + "\" target=\"_blank\"><i class=\"popup-icon-link gpt-doi\"></i></a>";
				}else{
					result = result + "<a href=\"" + this.link + "\" target=\"_blank\"><i class=\"popup-icon-link fa fa-external-link\"></i></a>";
				}
			}
			return result;
		}

		private String secureUrl(String url) {
			try{
				if (checkURL(url.replace("http://","https://"))){
					return url.replace("http://","https://");
				}
			}catch(Exception e){
				System.out.println("Error securing url (" + url + ") " + e.toString());
				e.printStackTrace();
			}
			return url;
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

	}

	class license {
		private minimal_entity_info core;
		private String link;
		private String icon;
		private boolean is_bespoke;

		public String extLink() {
			String result = "<a href=\"" + this.link + "\" target=\"_blank\">";
			if (this.icon != null && !this.icon.equals("")) {
				result = result + this.core.label + "<img class=\"terminfo-licenseicon\" src=\"" + this.icon + "\" title=\"" + this.core.label + "\"/>";
			}else{
				result = result + this.core.label;
			}
			result = result + "</a>";
			return result;
		}

		public String intLink() {
			String result = this.core.intLink();
			if (this.icon != null && !this.icon.equals("")) {
				result = result.replace(this.core.label,this.core.label + " <img class=\"terminfo-licenseicon\" src=\"" + this.icon + "\" title=\"" + this.core.label + "\"/>");
			}
			return result;
		}

	}

	class dataset_license {
		dataset dataset;
		license license;
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
			if (this.microref != null){
				return this.microref;
			}
			//if microref doesn't exist create one from the label:
			if (this.core.label != null){
				this.microref = this.core.label.split(",")[0] + "," + this.core.label.split(",")[1];
				return this.microref;
			}
			return null;
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
			if (this.pub != null && this.pub.microref() != null) {
				return this.synonym.toString() + " (" + this.pub.microref() + ")";
			}
			return this.synonym.toString();
		}
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
		private List<domain> template_domains;
		private template_channel template_channel;

		public String getSource() {
			String result = "";
			if (dataset_license != null && dataset_license.size() > 0) {
				result = result + "<span class=\"terminfo-source\">";
				for (dataset_license dsl:dataset_license) {
					if (this.term.core.short_form.equals(dsl.dataset.core.short_form)){
						result = result + dsl.dataset.extLink();
					}else{
						result = result + dsl.dataset.intLink();
					}
				}
				result = result + "</span>";
			}
			return result;
		}

		public String getLicense() {
			String result = "";
			if (dataset_license != null && dataset_license.size() > 0) {
				result = result + "<span class=\"terminfo-license\">";
				for (dataset_license dsl:dataset_license) {
					if (this.term.core.short_form.equals(dsl.dataset.core.short_form)){
						result = result + dsl.license.extLink();
					}else{
						result = result + dsl.license.intLink();
					}
				}
				result = result + "</span>";
			}
			return result;
		}

		public List<List<String>> getDomains(){
			List<List<String>> domains = new ArrayList(new ArrayList());
			try{
				if (this.term.core.types.contains("Template") && this.template_channel != null && this.template_domains != null){
					String wlzUrl = "";
					String[] domainId = new String[600];
					String[] domainName = new String[600];
					String[] domainType = new String[600];
					String[] domainCentre = new String[600];
					String[] voxelSize = new String[4];
					domainId[0] = this.term.core.short_form;
					domainName[0] = this.parents.get(0).label;
					domainType[0] = this.parents.get(0).short_form;
					voxelSize[0] = String.valueOf(this.template_channel.voxel.get(0));
					voxelSize[1] = String.valueOf(this.template_channel.voxel.get(1));
					voxelSize[2] = String.valueOf(this.template_channel.voxel.get(2));
					domainCentre[0] = String.valueOf(this.template_channel.center);
					for (domain domain:this.template_domains){
						domainId[domain.index.get(0).intValue()] = domain.anatomical_individual.short_form;
						domainName[domain.index.get(0).intValue()] = domain.anatomical_type.label;
						domainType[domain.index.get(0).intValue()] = domain.anatomical_type.short_form;
						if (domain.center != null && domain.center.size() > 0){
							domainCentre[domain.index.get(0).intValue()] = String.valueOf(domain.center);
						}
					}
					domains.add(Arrays.asList(voxelSize));
					domains.add(Arrays.asList(domainId));
					domains.add(Arrays.asList(domainName));
					domains.add(Arrays.asList(domainType));
					domains.add(Arrays.asList(domainCentre));
				}else{
					domains.add(Arrays.asList(new String[]{"0.622088","0.622088","0.622088",null}));
					domains.add(Arrays.asList(this.term.core.short_form));
					domains.add(Arrays.asList(this.term.core.label));
					domains.add(Arrays.asList(this.term.core.short_form));
					domains.add(Arrays.asList("[511, 255, 108]"));
				}
			}catch (Exception e) {
				System.out.println("Error in vfbTerm.getDomains(): " + e.toString());
				e.printStackTrace();
			}
			return domains;
		}

		public String definition() {
			if (this.def_pubs != null && this.def_pubs.size() > 0) {
				return this.term.definition() + "<br />(" + this.minirefs(this.def_pubs, ", ") + ")";
			}
			return this.term.definition();
		}

		public String minirefs(List<pub> pubs, String sep) {
			String result = "";
			for (pub pub:pubs) {
				if (pub.microref() != null){
					if (!result.equals("")) {
						result = result + sep;
					}
					result = result + pub.microref();
				}
			}
			return result;
		}

		public String synonyms() {
			String result = "";
			if (this.pub_syn != null && this.pub_syn.size() > 0) {
				result = result + "<ul class=\"terminfo-synonyms\">";
				for (pub_syn syn:pub_syn) {
					result = addUniqueToString(result, syn.toString());
				}
				result = result + "</ul>";
			}
			return result;
		}

		public String getReferences() {
			String result = "";
			if ((this.def_pubs != null && this.def_pubs.size() > 0) || (this.pub_syn != null && this.pub_syn.size() > 0)) {
				result = result + "<ul class=\"terminfo-references\">";
				if (this.def_pubs != null && this.def_pubs.size() > 0) {
					for (pub pub:def_pubs) {
						result = addUniqueToString(result, "<li>" + pub.miniref() + "</li>");
					}
				}
				if (this.pub_syn != null && this.pub_syn.size() > 0) {
					for (pub_syn syn:pub_syn) {
						result = addUniqueToString(result, "<li>" + syn.pub.miniref() + "</li>");
					}
				}
				result = result + "</ul>";
			}
			return result;
		}

		private String addUniqueToString(String concatList, String newItem) {
			if (concatList.indexOf(newItem) > -1){
				return concatList;
			}
			return concatList + newItem;
		}

		public String relList(String name, List<rel> entitys, List<String> showTypes) {
			String result = "<ul class=\"terminfo-" + name + "\">";
			for (rel rel : entitys) {
				result = result + "<li>" + rel.intLink(showTypes) + "</li>";
			}
			result = result + "</ul>";
			return result;
		}

		public String compileList(String name, List<minimal_entity_info> entitys, List<String> showTypes) {
			String result = "<ul class=\"terminfo-" + name + "\">";
			for (minimal_entity_info entity : entitys) {
				result = result + "<li>" + entity.intLink(showTypes) + "</li>";
			}
			result = result + "</ul>";
			return result;
		}

		public String xrefList() {
			// turning xrefs into list of html with link for xrefs.
			List<String> results = new ArrayList<>();
			// process xrefs
			for (xref xref : this.xrefs) {
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

		public ArrayValue thumbnails(String template) {
			ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
			try{
				if (template.equals("")){
					//default to JFRC2 
					template = "VFB_00017894";
				}
				int j = 0;
				int f = this.channel_image.size();
				for (channel_image ci : this.channel_image) {
					// add same template to the begining and others at the end.
					if (ci != null && ci.image != null && ci.image.template_anatomy != null && ci.image.template_anatomy.short_form != null && template == ci.image.template_anatomy.short_form) {
						addImage(ci.getUrl("", "thumbnailT.png"), ci.channel.label.replace("_c", "").replace("-c", ""), ci.channel.short_form.replace("VFBc_", "VFB_"), imageArray, j);
						j++;
					} else {
						f--;
						addImage(ci.getUrl("", "thumbnailT.png"), ci.channel.label.replace("_c", "").replace("-c", ""), ci.channel.short_form.replace("VFBc_", "VFB_"), imageArray, f);
					}
				}
			}catch (Exception e) {
				System.out.println("Error in vfbTerm.thumbnails(): " + e.toString());
				e.printStackTrace();
				return null;
			}
			return imageArray;
		}

		public ArrayValue examples(String template) {
			ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
			try{
				if (template.equals("")){
					//default to JFRC2 
					template = "VFB_00017894";
				}
				int j = 0;
				int f = this.anatomy_channel_image.size();
				for (anatomy_channel_image anat : this.anatomy_channel_image) {
					// add same template to the begining and others at the end.
					if (anat.channel_image != null && anat.channel_image.image != null && anat.channel_image.image.template_anatomy != null && anat.channel_image.image.template_anatomy.short_form != null && template == anat.channel_image.image.template_anatomy.short_form) {
						addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, j);
						j++;
					} else {
						f--;
						addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, f);
					}
				}
			}catch (Exception e) {
				System.out.println("Error in vfbTerm.examples(): " + e.toString());
				e.printStackTrace();
				return null;
			}
			return imageArray;
		}

		public ArrayValue thumbnail() {
			ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
			try{
				addImage(this.template_channel.image_folder + "thumbnailT.png", this.term.core.label, this.term.core.short_form, imageArray, 0);
			}catch (Exception e) {
				System.out.println("Error in vfbTerm.thumbnails(): " + e.toString());
				e.printStackTrace();
			}
			return imageArray;
		}

		public String imageFile(List<channel_image> images, String filename) {
			for (channel_image ci : images) {
				if (checkURL(ci.getUrl("", filename))) {
					return ci.getUrl("", filename);
				}
			}
			System.out.println("Failed to find: " + filename);
			return null;
		}

		public String imageFile(template_channel template, String filename) {
			if (checkURL(template.image_folder + filename)) {
				return template.image_folder + filename;
			}
			return null;
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
			image.setData(secureUrl(data));
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

		private String secureUrl(String url) {
			try{
				if (checkURL(url.replace("http://","https://"))){
					return url.replace("http://","https://");
				}
			}catch(Exception e){
				System.out.println("Error securing url (" + url + ") " + e.toString());
				e.printStackTrace();
			}
			return url;
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
			// Template space:
			String template = "";

			// retrieving the metadatatype
			CompositeType metadataType = (CompositeType) ModelUtility.getTypeFromLibrary(variable.getId() + "_metadata", dataSource.getTargetLibrary());

			// provide access to libary of types either dynamically added (as bellow) or loaded from xmi
			List<GeppettoLibrary> dependenciesLibrary = dataSource.getDependenciesLibrary();

			Type textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
			Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);

			List<String> superTypes = Arrays.asList();
			List<String> showTypes = Arrays.asList("Class","Individual","Anatomy","Template","Motor_neuron","Cell","Neuron"); // TODO: Fill in with passed types
			String tempData = "";
			String header = "loading";
			String references = ""; 

			//	Queries
			String querys = "";
			Variable classVariable = VariablesFactory.eINSTANCE.createVariable();
			CompositeType classParentType = TypesFactory.eINSTANCE.createCompositeType();
			classVariable.setId("notSet");

			System.out.println("Processing JSON...");
			try{
				header = "results>JSON";
				System.out.println("{");
				String json = "{";
				for (String key:results.getHeader()) {
					if (!json.equals("{")) {
						json = json + ", ";
					}
					tempData = new Gson().toJson(results.getValue(key, 0));
					json = json + "\"" + key  + "\":" + tempData;
					System.out.println(key + ":" + tempData + ",");
				}
				json = json + "}";
				System.out.println("}");

				header = "JSON>Schema";
				vfb_terminfo vfbTerm = new Gson().fromJson(json , vfb_terminfo.class);

				if (vfbTerm.term == null || vfbTerm.term.core == null){
					System.out.println("ERROR: term:core missing from JSON for " + variable.getId());
					return results;
				}

				// Note: term:core already handled by VFBProcessTermInfoCore except types labels

				// Types
				header = "types";
				superTypes = vfbTerm.term.core.typeList();

				// Types
				header = "types";
				tempData = vfbTerm.term.core.types(showTypes);
				if (!tempData.equals("")) {
					addModelHtml(vfbTerm.term.core.types(showTypes), "Types", header, metadataType, geppettoModelAccess);
				}

				// Description
				header = "description";
				tempData = vfbTerm.definition();
				if (!tempData.equals("")) {
					addModelHtml(tempData, "Description", header, metadataType, geppettoModelAccess);
				}

				// Synonyms
				header = "synonyms";
				tempData = vfbTerm.synonyms();
				if (!tempData.equals("")) {
					addModelHtml(tempData, "Alternative Names", header, metadataType, geppettoModelAccess);
				}

				// Source
				header = "source";
				tempData = vfbTerm.getSource();
				if (!tempData.equals("")) {
					addModelHtml(tempData, "Source", header, metadataType, geppettoModelAccess);
				}
				
				// License
				header = "license";
				tempData = vfbTerm.getLicense();
				if (!tempData.equals("")) {
					addModelHtml(tempData, "License", header, metadataType, geppettoModelAccess);
				}

				// parents
				header = "parents";
				if (vfbTerm.parents != null && vfbTerm.parents.size() > 0) {
					tempData = vfbTerm.compileList(header, vfbTerm.parents, showTypes);
					addModelHtml(tempData, "Parents", "type", metadataType, geppettoModelAccess);
					// store first parent as parent type for neuropil/tract queries
					classVariable.setId(vfbTerm.parents.get(0).short_form);
					classVariable.setName(vfbTerm.parents.get(0).label);
					classParentType.setId(classVariable.getId());
					classVariable.getAnonymousTypes().add(classParentType);
					for (String supertype : vfbTerm.parents.get(0).types) {
						if (!supertype.startsWith("_")) { // ignore supertypes starting with _
							classParentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
						}
					}
				}

				// relationships
				header = "relationships";
				if (vfbTerm.relationships != null && vfbTerm.relationships.size() > 0) {
					tempData = vfbTerm.relList(header, vfbTerm.relationships, showTypes);
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
					if (template.equals("")){
						template = vfbTerm.channel_image.get(0).image.template_anatomy.short_form;
					}
					addModelHtml(vfbTerm.channel_image.get(0).image.template_anatomy.intLink(), "Aligned to", "template", metadataType, geppettoModelAccess);
					// thumbnail
					if (vfbTerm.thumbnails(template) != null){
						addModelThumbnails(vfbTerm.thumbnails(template), "Thumbnail", "thumbnail", metadataType, geppettoModelAccess);
					}
					// OBJ - 3D mesh
					tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume_man.obj");
					if (tempData == null){
						tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume.obj");
					}
					System.out.println("OBJ " + tempData);
					if (tempData != null){
						addModelObj(tempData.replace("https://","http://"), "3D volume", variable.getId(), parentType, geppettoModelAccess, dataSource);
					}
				
					// SWC - 3D mesh
					tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume.swc");
					System.out.println("SWC " + tempData);
					if (tempData != null){
						addModelSwc(tempData.replace("https://","http://"), "3D Skeleton", variable.getId(), parentType, geppettoModelAccess, dataSource);
					}
				
					// Slices - 3D slice viewer
					tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume.wlz");
					System.out.println("WLZ " + tempData);
					if (tempData != null){
						addModelSlices(tempData.replace("http://","https://"), "Stack Viewer Slices", variable.getId(), parentType, geppettoModelAccess, dataSource, vfbTerm.getDomains());
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
						addModelObj(tempData.replace("https://","http://"), "3D volume", variable.getId(), parentType, geppettoModelAccess, dataSource);
					}
					System.out.println("OBJ " + tempData);
			
					// Slices - 3D slice viewer
					tempData = vfbTerm.imageFile(vfbTerm.template_channel, "volume.wlz");
					if (tempData != null){
						addModelSlices(tempData.replace("http://","https://"), "Stack Viewer Slices", variable.getId(), parentType, geppettoModelAccess, dataSource, vfbTerm.getDomains());
					}
					System.out.println("WLZ " + tempData);
				}
			
				// examples
				header = "anatomy_channel_image";
				if (vfbTerm.anatomy_channel_image != null && vfbTerm.anatomy_channel_image.size() > 0 && vfbTerm.examples(template) != null) {
					addModelThumbnails(vfbTerm.examples(template), "Examples", "examples", metadataType, geppettoModelAccess);
					parentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType("hasExamples", dependenciesLibrary));
				}

				// references
				header = "references";
				references = vfbTerm.getReferences();
				if (!references.equals("")) {
					addModelHtml(references, "References", "references", metadataType, geppettoModelAccess);
				}

				// set queries
				String badge = "";
				for(Query runnableQuery : geppettoModelAccess.getQueries())
				{
					if(QueryChecker.check(runnableQuery, variable))
					{
						badge = "<i class=\"popup-icon-link fa fa-quora\" ></i>";
						querys += badge + "<a href=\"#\" data-instancepath=\"" + (String) runnableQuery.getPath() + "," + variable.getId() + "," + variable.getName() + "\">" + runnableQuery.getDescription().replace("$NAME", classVariable.getName()) + "\">" + runnableQuery.getDescription().replace("$NAME", variable.getName()) + "</a></i></br>";
					}else if (((superTypes.contains("Painted_domain") || superTypes.contains("Synaptic_neuropil_domain")) || superTypes.contains("Neuron_projection_bundle")) && superTypes.contains("Individual") && classVariable.getId()!="notSet"){
						if(QueryChecker.check(runnableQuery, classVariable)){
							badge = "<i class=\"popup-icon-link fa fa-quora\" ></i>";
							querys += badge + "<a href=\"#\" data-instancepath=\"" + (String) runnableQuery.getPath() + "," + classVariable.getId() + "," + classVariable.getName() + "\">" + runnableQuery.getDescription().replace("$NAME", classVariable.getName()) + "</a></i></br>";
						}
					}
				}
				
				if (superTypes.contains("Template")){
					badge = "<i class=\"popup-icon-link fa gpt-shapeshow\" ></i>";
					querys += badge + "<a href=\"\" title=\"Hide template boundary and show all painted neuroanatomy\" onclick=\"" + variable.getId() + ".hide();window.addVfbId(JSON.parse(" + variable.getId() + "." + variable.getId() + "_slices.getValue().getWrappedObj().value.data).subDomains[1].filter(function(n){ return n != null }));return false;\">Show All Anatomy</a><br/>";
				}

				if (querys != "") {
					addModelHtml(querys, "Query for", "queries", metadataType, geppettoModelAccess);
				}

			}catch (Exception e) {
				System.out.println("Error creating " + header + ": " + e.toString());
				e.printStackTrace();
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
			slicesVar.setId(reference + "_slices");
			slicesVar.setName("Stack Viewer Slices");
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
			importType.setId(reference + "_swc");
			importType.setModelInterpreterId("swcModelInterpreter");
			Variable.getTypes().add(importType);
			Variable.setId(reference + "_swc");
			Variable.setName("3D Skeleton");
			geppettoModelAccess.addVariableToType(Variable, parentType);
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
			importType.setId(reference + "_obj");
			importType.setModelInterpreterId("objModelInterpreterService");
			Variable.getTypes().add(importType);
			Variable.setId(reference + "_obj");
			Variable.setName("3D Volume");
			geppettoModelAccess.addVariableToType(Variable, parentType);
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
