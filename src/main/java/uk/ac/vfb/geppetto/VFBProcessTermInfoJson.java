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

import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.util.ModelUtility;
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

	Boolean debug=false;
	
	// START VFB term info schema https://github.com/VirtualFlyBrain/VFB_json_schema/blob/master/json_schema/

	class minimal_entity_info {
		String short_form;
		String iri;
		String label;
		private List<String> types;

		public String intLink() {
			return this.intLink(false);
		}

		public String extLink() {
			return this.extLink(false);
		}

		public String intLink(Boolean showTypes) {
			return "<a href=\"#\" data-instancepath=\"" + this.short_form + "\">" + this.label + "</a>"
					+ this.types(showTypes);
		}

		public String extLink(Boolean showTypes) {
			return "<a href=\"" + this.iri + "\" target=\"_blank\">" + this.label + "</a>" + this.types(showTypes);
		}

		public String types(Boolean show) {
			if (show && this.types != null) {
				return " " + this.returnType(this.types);
			}
			return "";
		}

		public List<String> typeList() {
			return this.types;
		}

		public String returnType(List<String> types) {
			if (types.size() > 0) {
				if (types.contains("Obsolete")){
					this.returnType(types, Arrays.asList("Obsolete"));
				}
				if (types.contains("Motor_neuron")){
					return this.returnType(types, Arrays.asList("GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Motor_neuron"));
				}
				if (types.contains("Sensory_neuron")){
					return this.returnType(types, Arrays.asList("GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Sensory_neuron"));
				}
				if (types.contains("Sensory_neuron")){
					return this.returnType(types, Arrays.asList("GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Sensory_neuron"));
				}
				if (types.contains("Peptidergic_neuron")){
					return this.returnType(types, Arrays.asList("GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Peptidergic_neuron"));
				}
				if (types.contains("Neuron")){
					return this.returnType(types, Arrays.asList("GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Neuron"));
				}
				if (types.contains("Glial_cell")){
					return this.returnType(types, Arrays.asList("GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Glial_cell"));
				}
				if (types.contains("Cell")){
					return this.returnType(types, Arrays.asList("GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Cell"));
				}
				if (types.contains("Neuron_projection_bundle")){
					return this.returnType(types, Arrays.asList("Neuron_projection_bundle"));
				}
				if (types.contains("Split")){
					return this.returnType(types, Arrays.asList("Split","Expression_pattern","GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Glial_cell"));
				}
				if (types.contains("Expression_pattern")){
					return "<span class=\"label types\">" + "<span class=\"label label-Expression_pattern\">Expression Pattern</span> ";
				}
				if (types.contains("pub")){
					return "<span class=\"label types\">" + "<span class=\"label label-pub\">Publication</span> ";
				}
				return this.returnType(types, Arrays.asList("Person","License","Synaptic_neuropil","Template","Property","Anatomy","Ganglion","Clone","DataSet","Neuromere","Resource","Site"));
			}
			return "";
		}
	
		private String returnType(List<String> types, List<String> show) {
			if (types.size() > 0 && show.size() > 0) {
				String result = "<span class=\"label types\">";
				for (String type : show) {
					if (types.contains(type)) {
						result += "<span class=\"label label-" + type + "\">" + type.replace("_", " ") + "</span> ";
					}
				}
				return result + "</span>";
			}
			return "";
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
			if (this.label != null && !this.label.equals("")){
				return this.label;
			}
			return this.type();
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
		private String link;
		private String icon;

		public String definition() {
			String result = "";
			if (this.description != null && this.description.size() > 0) {
				result += this.description();
			}
			if (this.comment != null && this.comment.size() > 0) {
				result += "<br /><span class=\"terminfo-comment-title\">Comment</span><br />" + this.comment();
			}
			return result;
		}

		private String description() {
			if (this.description != null && this.description.size() > 0) {
				return "<span class=\"terminfo-description\">" + this.highlightLinks(this.encode(String.join(" <br /> ", this.description))) + "</span>";
			}
			return "";
		}

		private String comment() {
			if (this.comment != null && this.comment.size() > 0) {
				return "<span class=\"terminfo-comment\">" + this.highlightLinks(this.encode(String.join(" <br /> ", this.comment))) + "</span>";
			}
			return "";
		}

		private String encode(String text){
			return text.replace("\\\"","&quot;").replace("\\\'","&apos;").replace("\"","&quot;").replace("\'","&apos;");
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

		public String logo() {
			String result = "";
			if (this.icon != null && !this.icon.equals("")) {
				if (this.link != null && !this.link.equals("")) {
					result = "<span class=\"terminfo-logo\"><a href=\"" + this.link + "\" target=\"_blank\" ><img class=\"terminfo-logo\" src=\"" + this.icon + "\" /></a></span>";
				}else{
					result = "<span class=\"terminfo-logo\"><img class=\"terminfo-logo\" src=\"" + this.icon + "\" /></span>";
				}
			}
			return result;
		}

		public String link() {
			String result = "";
			if (this.link != null && !this.link.equals("")) {
				result = "<span class=\"terminfo-link\"><a href=\"" + this.link + "\" target=\"_blank\" ><i class=\"popup-icon-link fa fa-external-link\"></i> " + this.link + "</a></span>";
			}
			return result;
		}
	}

	class rel {
		private minimal_edge_info relation;
		private minimal_entity_info object;

		public String intLink() {
			return this.intLink(false);
		}

		public String intLink(Boolean showTypes) {
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
				result += post;
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
		public String homepage;
		public String link_base;
		private String link_postfix;
		String accession; 
		private String link_text;
		private String icon;
		private minimal_entity_info site;

		public String extLink() {
			return extLink(false);
		}

		public String link() {
			if (this.accession != null && !this.accession.equals("None") && !this.accession.equals("")) {
				return this.link_base + this.accession + this.link_postfix;
			}
			if (this.homepage != null && this.homepage.equals("")) {
				return this.homepage;
			}
			return this.site.iri;
		}

		public String extLink(Boolean showTypes) {
			String result = "<a href=\"" + this.link() + "\" target=\"_blank\">";
			result += this.link_text;
			// tack site link as comment on xref for later sorting
			String site = this.site.extLink(showTypes);
			if (this.homepage != null && this.homepage.equals("")) {
				site = site.replace(this.site.iri,this.homepage);
			}
			if (this.icon != null && !this.icon.equals("")) {
				site += "<a href=\"" + this.link() + "\" target=\"_blank\">" + "<img class=\"terminfo-siteicon\" src=\"" + secureUrl(this.icon) + "\" /></a>";
			}
			result += "</a>";
			result = "<!--" + site + "-->" + result;
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
				huc.setConnectTimeout(5000);
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

		public String extLink() {
			String result = "<a href=\"" + this.link + "\" target=\"_blank\">";
			if (this.icon != null && !this.icon.equals("")) {
				result += "<img class=\"terminfo-dataseticon\" src=\"" + secureUrl(this.icon) + "\" title=\"" + this.core.label + "\"/>";
			}else{
				result += this.core.label;
			}
			result += "</a>";	
			return result;
		}

		public String intLink() {
			String result = this.core.intLink(false);
			if (this.icon != null && !this.icon.equals("")) {
				result += result.replace(this.core.label,this.core.label + " <img class=\"terminfo-dataseticon\" src=\"" + secureUrl(this.icon) + "\" title=\"" + this.core.label + "\"/>");
			}
			if (this.link != null && !this.link.equals("") && !this.link.equals("unspec")){
				if (this.link.toLowerCase().contains("flybase.org")) {
					result += "<a href=\"" + this.link + "\" target=\"_blank\"><i class=\"popup-icon-link gpt-fly\"></i></a>";
				}else if (this.link.toLowerCase().contains("nih.gov")) {
					result += "<a href=\"" + this.link + "\" target=\"_blank\"><i class=\"popup-icon-link gpt-pubmed\"></i></a>";
				}else if (this.link.toLowerCase().contains("doi.org")) {
					result += "<a href=\"" + this.link + "\" target=\"_blank\"><i class=\"popup-icon-link gpt-doi\"></i></a>";
				}else{
					result += "<a href=\"" + this.link + "\" target=\"_blank\"><i class=\"popup-icon-link fa fa-external-link\"></i></a>";
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
				result += this.core.label + "<img class=\"terminfo-licenseicon\" src=\"" + this.icon + "\" title=\"" + this.core.label + "\"/>";
			}else{
				result += this.core.label;
			}
			result += "</a>";
			return result;
		}

		public String intLink() {
			String result = this.core.intLink(false);
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
			if (core.short_form.equals("Unattributed")) {
				return result;
			}
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
				result += links;
			}
			result += this.core.types(true);
			return result;
		}

		public String microref() {
			if (this.microref != null){
				return this.core.intLink().replace(this.core.label,this.microref);
			}
			//if microref doesn't exist create one from the label:
			if (this.core.label != null){
				if (this.core.label != null && !this.core.label.equals("")){
					if (this.core.label.contains(",")){
						this.microref = this.core.label.split(",")[0] + "," + this.core.label.split(",")[1];
						return this.core.intLink().replace(this.core.label,this.microref);
					}else{
						return this.core.label;
					}
				}
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
		private List<pub> pubs;

		public String toString() {
			if (this.pub != null && this.pub.microref() != null) {
				return this.synonym.toString() + " (" + this.pub.microref() + ")";
			}
			if (this.pubs != null && this.pubs.size() > 0) {
				return this.synonym.toString() + " " + this.microrefs();
			}
			return this.synonym.toString();
		}
		
		private String microrefs() {
			String result="(";
			for (pub pub:pubs) {
				result += this.pub.microref() + ", ";
			}
			result += ")";
			return result.replace(", )",")");
		}
	}

	class vfb_terminfo {
		term term;
		public String query;
		public String version;
		private List<anatomy_channel_image> anatomy_channel_image;
		public List<xref> xrefs;
		private List<pub_syn> pub_syn;
		private List<pub> def_pubs;
		private List<pub> pubs;
		private pub pub;
		private List<license> license;
		private List<dataset_license> dataset_license;
		private List<rel> relationships;
		private List<rel> related_individuals;
		private List<minimal_entity_info> parents;
		private List<channel_image> channel_image;
		private List<domain> template_domains;
		private template_channel template_channel;
		private List<minimal_entity_info> targeting_splits; 
		private List<minimal_entity_info> target_neurons; 

		public String getSource() {
			String result = "";
			if (dataset_license != null && dataset_license.size() > 0) {
				result += "<span class=\"terminfo-source\">";
				for (dataset_license dsl:dataset_license) {
					if (!result.equals("<span class=\"terminfo-source\">")){
						result += "<BR />";
					}
					if (this.term.core.short_form.equals(dsl.dataset.core.short_form)){
						if (!result.contains(dsl.dataset.extLink())){
							result += dsl.dataset.extLink();
						}
					}else{
						if (!result.contains(dsl.dataset.intLink())){
							result += dsl.dataset.intLink();
						}
					}
				}
				result += "</span>";
			} 
			if (result.equals("<span class=\"terminfo-source\"></span>")) return "";
			return result;
		}

		public String getLicense() {
			String result = "";
			if (dataset_license != null && dataset_license.size() > 0) {
				result += "<span class=\"terminfo-license\">";
				for (dataset_license dsl:dataset_license) {
					if (!result.equals("<span class=\"terminfo-license\">")){
						result += "<BR />";
					}
					if (this.term.core.short_form.equals(dsl.dataset.core.short_form)){
						if (!result.contains(dsl.license.extLink())){
							result += dsl.license.extLink();
						}
					}else{
						if (!result.contains(dsl.license.intLink())){
							result += dsl.license.intLink();
						}
					}
				}
				result += "</span>";
			} else if (license != null && license.size() > 0) {
				result += "<span class=\"terminfo-license\">";
				for (license l:license) {
					if (!result.equals("<span class=\"terminfo-license\">")){
						result += "<BR />";
					}
					if (this.term.core.short_form.equals(l.core.short_form)){
						if (!result.contains(l.extLink())){
							result += l.extLink();
						}
					}else{
						if (!result.contains(l.intLink())){
							result += l.intLink();
						}
					}
				}
				result += "</span>";
			}
			if (result.equals("<span class=\"terminfo-license\"></span>")) return "";
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

		public String targetingSplits() {
			String result = "";
			if (this.targeting_splits != null && this.targeting_splits.size() > 0) {
				result += "<ul class=\"terminfo-targetingSplits\">";
				for (minimal_entity_info split:targeting_splits) {
					result = addUniqueToString(result, "<li>" + split.intLink(true) + "</li>");
				}
				result += "</ul>";
			}
			return result;
		}

		public String targetingNeurons() {
			String result = "";
			if (this.target_neurons != null && this.target_neurons.size() > 0) {
				result += "<ul class=\"terminfo-targetNeurons\">";
				for (minimal_entity_info neuron:target_neurons) {
					result = addUniqueToString(result, "<li>" + neuron.intLink(true) + "</li>");
				}
				result += "</ul>";
			}
			return result;
		}

		public String minirefs(List<pub> pubs, String sep) {
			String result = "";
			for (pub pub:pubs) {
				if (pub.microref() != null){
					if (!result.equals("")) {
						result += sep;
					}
					result += pub.microref();
				}
			}
			return result;
		}

		public String synonyms() {
			String result = "";
			if (this.pub_syn != null && this.pub_syn.size() > 0) {
				result += "<ul class=\"terminfo-synonyms\">";
				for (pub_syn syn:pub_syn) {
					result = addUniqueToString(result, "<li>" + syn.toString() + "</li>");
				}
				result += "</ul>";
			}
			return result;
		}

		public String getReferences() {
			String result = "";
			if ((this.def_pubs != null && this.def_pubs.size() > 0) || (this.pub_syn != null && this.pub_syn.size() > 0) || (this.pubs != null && this.pubs.size() > 0) || (this.pub != null)) {
				result += "<ul class=\"terminfo-references\">";
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
				if (this.pubs != null && this.pubs.size() > 0) {
					for (pub pub:pubs) {
						result = addUniqueToString(result, "<li>" + pub.miniref() + "</li>");
					}
				}
				if (this.pub != null) {
					result = addUniqueToString(result, "<li>" + this.pub.miniref() + "</li>");
				}
				result += "</ul>";
			}
			if (result.equals("<ul class=\"terminfo-references\"></ul>")) return "";
			return result;
		}

		private String addUniqueToString(String concatList, String newItem) {
			if (concatList.indexOf(newItem) > -1){
				return concatList;
			}
			if (newItem.length() < 10){
				return concatList;
			}
			return concatList + newItem;
		}

		public String relList(String name, List<rel> entitys, Boolean showTypes) {
			String result = "<ul class=\"terminfo-" + name + "\">";
			for (rel rel : entitys) {
				if (result.indexOf(rel.intLink(showTypes))<0){
					result += "<li>" + rel.intLink(showTypes) + "</li>";
				}
			}
			result += "</ul>";
			return result;
		}

		public String compileList(String name, List<minimal_entity_info> entitys, Boolean showTypes) {
			String result = "<ul class=\"terminfo-" + name + "\">";
			for (minimal_entity_info entity : entitys) {
				if (result.indexOf(entity.intLink(showTypes))<0){
					result += "<li>" + entity.intLink(showTypes) + "</li>";
				}
			}
			result += "</ul>";
			return result;
		}

		public String xrefList() {
			// turning xrefs into list of html with link for xrefs.
			List<String> results = new ArrayList<>();
			// process xrefs
			for (xref xref : this.xrefs) {
				results.add(xref.extLink());
			}
			// sort xrefs alphabetically (by site)
			java.util.Collections.sort(results);
			// itterate to create html list:
			String result = "<ul class=\"terminfo-xrefs\">";
			String site = "";
			for (String xref : results) {
				if (result.indexOf(xref)<0){
					if (xref.substring(25).equals(site)) {
						result += "<li>" + xref + "</li>";
					} else if (site == "") {
						// embed first sites xrefs
						result += "<li>" + xref.replace("-->", "<ul><li>").replace("<!--", "") + "</li>";
					} else {
						// close previous and start next site xrefs
						result += "</ul></li><li>" + xref.replace("-->", "<ul><li>").replace("<!--", "") + "</li>";
					}
					site = xref.substring(25);
				}
			}
			result += "</ul></li></ul>";
			return result;
		}

		public ArrayValue thumbnails(String template) {
			ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
			try{
				if (template == null || template.equals("")){
					//default to JFRC2 
					template = "VFB_00017894";
				}
				int j = 0;
				int f = this.channel_image.size();
				for (channel_image ci : this.channel_image) {
					// add same template to the begining and others at the end.
					if (ci != null && ci.image != null && ci.image.template_anatomy != null && ci.image.template_anatomy.short_form != null && template.equals(ci.image.template_anatomy.short_form)) {
						addImage(ci.getUrl("", "thumbnailT.png"), ci.channel.label.replace("_c", "").replace("-c", ""), ci.channel.short_form.replace("VFBc_", "VFB_"), imageArray, j);
						j++;
					} 
				}
				for (channel_image ci : this.channel_image) {
					// add same template to the begining and others at the end.
					if (ci != null && ci.image != null && ci.image.template_anatomy != null && ci.image.template_anatomy.short_form != null && !template.equals(ci.image.template_anatomy.short_form)) {
						addImage(ci.getUrl("", "thumbnailT.png"), ci.channel.label.replace("_c", "").replace("-c", ""), ci.channel.short_form.replace("VFBc_", "VFB_"), imageArray, j);
						j++;
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
				if (template == null || template.equals("")){
					//default to JFRC2 
					template = "VFB_00017894";
				}
				int j = 0;
				int f = this.anatomy_channel_image.size();
				for (anatomy_channel_image anat : this.anatomy_channel_image) {
					// add same template to the begining and others at the end.
					if (anat.channel_image != null && anat.channel_image.image != null && anat.channel_image.image.template_anatomy != null && anat.channel_image.image.template_anatomy.short_form != null && template.equals(anat.channel_image.image.template_anatomy.short_form)) {
						addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, j);
						j++;
					}
				}
				for (anatomy_channel_image anat : this.anatomy_channel_image) {
					// add same template to the begining and others at the end.
					if (anat.channel_image != null && anat.channel_image.image != null && anat.channel_image.image.template_anatomy != null && anat.channel_image.image.template_anatomy.short_form != null && !template.equals(anat.channel_image.image.template_anatomy.short_form)) {
						addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, j);
						j++;
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

		public ArrayValue clusterImage() {
			ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
			try{
				addImage(this.xrefs.get(0).link_base + this.xrefs.get(0).accession + "/snapshot.png", this.term.core.label, this.term.core.short_form, imageArray, 0);
			}catch (Exception e) {
				System.out.println("Error in vfbTerm.clusterImage(): " + e.toString());
				e.printStackTrace();
			}
			return imageArray;
		}

		public String imageFile(List<channel_image> images, String filename) {
			try{
				for (channel_image ci : images) {
					if (checkURL(ci.getUrl("", filename))) {
						return ci.getUrl("", filename);
					}
				}
			} catch (Exception e) {
				System.out.println("Error in vfbTerm.imageFile: " + e.toString());
				e.printStackTrace();
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
			if (urlString.indexOf(":") > 0) 
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
				}
			}
			return false;
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
		String json = "{";
		vfb_terminfo vfbTerm = null;
		try
		{
			// Template space:
			String template = "";

			// retrieving the metadatatype
			CompositeType metadataType = (CompositeType) ModelUtility.getTypeFromLibrary(variable.getId() + "_metadata", dataSource.getTargetLibrary());

			try {
				// checking the template
				System.out.println("Checking template");
				List<Type> templateTypes = (List<Type>) ModelUtility.getAllTypesOf("VFB_00101384");
				System.out.println(templateTypes[0].getSuperType());
				System.out.println(templateTypes);
			} catch (Exception e) {
				System.out.println("Error");
				System.out.println(e);	
			}
			
			// provide access to libary of types either dynamically added (as bellow) or loaded from xmi
			List<GeppettoLibrary> dependenciesLibrary = dataSource.getDependenciesLibrary();

			Type textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
			Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);

			List<String> superTypes = Arrays.asList();
			Boolean showTypes = true; //Arrays.asList("Class","Individual","Anatomy","Template","Motor_neuron","Cell","Neuron","pub","License","Ganglion","Expression_pattern","Neuromere","DataSet","Cluster","Synaptic_neuropil_block","Synaptic_neuropil_subdomain","Synaptic_neuropil_domain","Synaptic_neuropil","Clone","Neuron_projection_bundle","Sensory_neuron","Site","Serotonergic","Person","Peptidergic_neuron","Painted_domain","Octopaminergic","Neuroblast","Motor_neuron","Glutamatergic","Glial_cell","Ganglion","GABAergic","Dopaminergic","Cholinergic"); // TODO: Fill in with passed types
			String tempData = "";
			String header = "loading";
			String references = ""; 

			//	Queries
			String querys = "";
			Variable classVariable = VariablesFactory.eINSTANCE.createVariable();
			CompositeType classParentType = TypesFactory.eINSTANCE.createCompositeType();
			classVariable.setId("notSet");

			if (debug) System.out.println("Processing JSON...");
			try{
				header = "results>JSON";
				if (debug) System.out.println("{");
				
				for (String key:results.getHeader()) {
					if (!json.equals("{")) {
						json = json + ", ";
					}
					tempData = new Gson().toJson(results.getValue(key, 0));
					json = json + "\"" + key  + "\":" + tempData;
					if (debug){
						if (tempData.length() > 1000){
							System.out.println("\"" + key  + "\":" + tempData.replace("}","}\n") + ",");
						}else{
							System.out.println("\"" + key  + "\":" + tempData + ",");
						}	
					}
				}
				json = json + "}";
				if (debug) System.out.println("}");

				header = "JSON>Schema";
				vfbTerm = new Gson().fromJson(json , vfb_terminfo.class);

				if (vfbTerm.term == null || vfbTerm.term.core == null){
					System.out.println("ERROR: term:core missing from JSON for " + variable.getId());
					System.out.println(json.replace("}","}\n"));
					return results;
				}

				// Label: {label} ({short_form}) TYPES (all on one line)
				header = "label";
				tempData = "<b>" + vfbTerm.term.core.label + "</b> (" + vfbTerm.term.core.short_form + ") " + vfbTerm.term.core.types(showTypes);
				addModelHtml(tempData, "Name", header, metadataType, geppettoModelAccess);

				// Logo
				header = "logo";
				tempData = vfbTerm.term.logo();
				if (!tempData.equals("")) {
					addModelHtml(tempData, "Logo", header, metadataType, geppettoModelAccess);
				}

				// Link
				header = "link";
				tempData = vfbTerm.term.link();
				if (!tempData.equals("")) {
					addModelHtml(tempData, "Link", header, metadataType, geppettoModelAccess);
				}

				// Types
				header = "types";
				superTypes = vfbTerm.term.core.typeList();

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

				// Classification
				header = "Classification";
				if (vfbTerm.parents != null && vfbTerm.parents.size() > 0) {
					tempData = vfbTerm.compileList(header, vfbTerm.parents, showTypes);
					addModelHtml(tempData, "Classification", "type", metadataType, geppettoModelAccess);
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

				// related individuals
				header = "related_individuals";
				if (vfbTerm.related_individuals != null && vfbTerm.related_individuals.size() > 0) {
					tempData = vfbTerm.relList(header, vfbTerm.related_individuals, showTypes);
					addModelHtml(tempData, "Related Individuals", header, metadataType, geppettoModelAccess);
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
					classParentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(vfbTerm.channel_image.get(0).image.template_anatomy.short_form, dependenciesLibrary));
					// thumbnail
					if (vfbTerm.thumbnails(template) != null){
						addModelThumbnails(vfbTerm.thumbnails(template), "Thumbnail", "thumbnail", metadataType, geppettoModelAccess);
					}
					// OBJ - 3D mesh
					tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume_man.obj");
					if (tempData == null){
						if (debug) System.out.println("OBJ " + tempData);
						tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume.obj");
					}
					if (tempData != null){
						addModelObj(tempData.replace("https://","http://"), "3D volume", variable.getId(), parentType, geppettoModelAccess, dataSource);
					}
				
					// SWC - 3D mesh
					tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume.swc");
					if (tempData != null){
						if (debug) System.out.println("SWC " + tempData);
						addModelSwc(tempData.replace("https://","http://"), "3D Skeleton", variable.getId(), parentType, geppettoModelAccess, dataSource);
					}
				
					// Slices - 3D slice viewer
					tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume.wlz");
					if (tempData != null){
						if (debug) System.out.println("WLZ " + tempData);
						addModelSlices(tempData.replace("http://","https://"), "Stack Viewer Slices", variable.getId(), parentType, geppettoModelAccess, dataSource, vfbTerm.getDomains());
					}
					
					// Download - NRRD stack
					tempData = vfbTerm.imageFile(vfbTerm.channel_image, "volume.nrrd");
					if (tempData != null){
						if (debug) System.out.println("NRRD " + tempData);
						addModelHtml("Aligned Image: <a download=\"" + variable.getId() + ".nrrd\" href=\"" + tempData.replace("http://","https://").replace("https://www.virtualflybrain.org/data/","/data/") + "\">" + variable.getId() + ".nrrd</a><br>Note: see source & license above for terms of reuse and correct attribution.", "Downloads", "downloads", metadataType, geppettoModelAccess);
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
					if (debug) System.out.println("OBJ " + tempData);
			
					// Slices - 3D slice viewer
					tempData = vfbTerm.imageFile(vfbTerm.template_channel, "volume.wlz");
					if (tempData != null){
						addModelSlices(tempData.replace("http://","https://"), "Stack Viewer Slices", variable.getId(), parentType, geppettoModelAccess, dataSource, vfbTerm.getDomains());
					}
					if (debug) System.out.println("WLZ " + tempData);
					
					// Download - NRRD stack
					tempData = vfbTerm.imageFile(vfbTerm.template_channel, "volume.nrrd");
					if (debug) System.out.println("NRRD " + tempData);
					if (tempData != null){
						addModelHtml("Aligned Image: <a download=\"" + variable.getId() + ".nrrd\" href=\"" + tempData.replace("http://","https://").replace("https://www.virtualflybrain.org/data/","/data/") + "\">" + variable.getId() + ".nrrd</a><br>Note: see source & license above for terms of reuse and correct attribution.", "Downloads", "downloads", metadataType, geppettoModelAccess);
					}
				}
			
				// examples
				header = "anatomy_channel_image";
				if (vfbTerm.anatomy_channel_image != null && vfbTerm.anatomy_channel_image.size() > 0 && vfbTerm.examples(template) != null) {
					addModelThumbnails(vfbTerm.examples(template), "Examples", "examples", metadataType, geppettoModelAccess);
					parentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType("hasExamples", dependenciesLibrary));
				}

				// NBLAST Cluster
				header = "cluster";
				if (vfbTerm.xrefs != null && vfbTerm.xrefs.size() > 0 && vfbTerm.xrefs.get(0).link_base.indexOf("flybrain.mrc-lmb.cam.ac.uk/vfb/fc/clusterv/3") > -1) {
					addModelThumbnails(vfbTerm.clusterImage(), "Thumbnail", "thumbnail", metadataType, geppettoModelAccess);
				}

				// references
				header = "references";
				references = vfbTerm.getReferences();
				if (!references.equals("")) {
					addModelHtml(references, "References", "references", metadataType, geppettoModelAccess);
				}

				// set queries
				String badge = "<i class=\"popup-icon-link fa fa-quora\" ></i>";
				Boolean classAdded = false;
				String queryExpressedInX = "";
				for(Query runnableQuery : geppettoModelAccess.getQueries())
				{
					if(QueryChecker.check(runnableQuery, variable))
					{
						querys += badge + "<a href=\"#\" data-instancepath=\"" + (String) runnableQuery.getPath() + "," + variable.getId() + "," + variable.getName() + "\">" + runnableQuery.getDescription().replace("$NAME", variable.getName()) + "</a></br>";
						if (runnableQuery.getPath().equals("ExpOverlapsX")) {
							queryExpressedInX = "<a href=\"#\" data-instancepath=\"" + (String) runnableQuery.getPath() + "," + variable.getId() + "," + variable.getName() + "\">" + runnableQuery.getDescription().replace("$NAME", variable.getName()) + "</a></br>";
						}else if (runnableQuery.getPath().equals("TransgeneExpInX")) {
							queryExpressedInX = "<a href=\"#\" data-instancepath=\"" + (String) runnableQuery.getPath() + "," + variable.getId() + "," + variable.getName() + "\">" + runnableQuery.getDescription().replace("$NAME", variable.getName()) + "</a></br>";
						}
					}else if ((superTypes.contains("Painted_domain") || superTypes.contains("Synaptic_neuropil_domain") || superTypes.contains("Neuron_projection_bundle") || superTypes.contains("Split") || superTypes.contains("Expression_pattern")) && superTypes.contains("Individual") && classVariable.getId()!="notSet"){
						if(QueryChecker.check(runnableQuery, classVariable)){
							querys += badge + "<a href=\"#\" data-instancepath=\"" + (String) runnableQuery.getPath() + "," + classVariable.getId() + "," + classVariable.getName() + "\">" + runnableQuery.getDescription().replace("$NAME", classVariable.getName()) + "</a></br>";
						}
						if (!classAdded) {
							addModelString(classVariable.getId(), "ClassQueriesFrom", "classqueriesfrom", metadataType, geppettoModelAccess);
							classAdded = true;
						}
					}
				}
				
				if (debug && superTypes.contains("Template")){
					badge = "<i class=\"popup-icon-link fa gpt-shapeshow\" ></i>";
					querys += badge + "<a href=\"\" title=\"Hide template boundary and show all painted neuroanatomy\" onclick=\"" + variable.getId() + ".hide();$('body').css('cursor', 'progress');window.addVfbId(JSON.parse(" + variable.getId() + "." + variable.getId() + "_slices.getValue().getWrappedObj().value.data).subDomains[1].filter(function(n){ return n != null }));$('body').css('cursor', 'default');return false;\">Show All Anatomy</a><br/>";
				}

				if (querys != "") {
					addModelHtml(querys, "Query for", "queries", metadataType, geppettoModelAccess);
				}

				//Indenting embeded queries
				badge = "<i class=\"popup-icon-link fa fa-quora\" style=\"text-indent:18px;\"></i>";

				// Targeting Splits
				header = "targetingSplits";
				tempData = vfbTerm.targetingSplits();
				if (tempData != null && !tempData.equals("")) {
					tempData += badge + queryExpressedInX;
					// Remove emptry record:
					tempData = tempData.replace("<li><a href=\"#\" data-instancepath=\"null\"></a></li>", "");
					addModelHtml(tempData, "Targeting Splits", header, metadataType, geppettoModelAccess);
				}

				// Targeting Neurons
				header = "targetingNeurons";
				tempData = vfbTerm.targetingNeurons();
				if (tempData != null && !tempData.equals("")) {
					tempData += badge + queryExpressedInX;
					// Remove emptry record:
					tempData = tempData.replace("<li><a href=\"#\" data-instancepath=\"null\"></a></li>", "");
					addModelHtml(tempData, "Targeted Neurons", header, metadataType, geppettoModelAccess);
				}

				//debug query version to term info
				if (debug) {
					addModelHtml(vfbTerm.query + " (" + vfbTerm.version + ")<br>" + json, "Debug", "debug", metadataType, geppettoModelAccess);
				}

			}catch (Exception e) {
				System.out.println("Error creating " + header + ": " + e.toString());
				e.printStackTrace();
				System.out.println(json.replace("}","}\n"));
				//debug query version to term info
				if (debug) {
					if (vfbTerm!=null && vfbTerm.query!=null) {	
						addModelHtml(vfbTerm.query + " (" + vfbTerm.version + ")" + "<br>" + json + "<br>" + e.toString(), "Debug", "debug", metadataType, geppettoModelAccess);
					}else{
						addModelHtml(json + "<br>" + e.toString(), "Debug", "debug", metadataType, geppettoModelAccess);
					}
				}
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
