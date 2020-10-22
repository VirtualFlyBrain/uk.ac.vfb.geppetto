
package uk.ac.vfb.geppetto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.AQueryResult;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DatasourcesFactory;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResult;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.datasources.SerializableQueryResult;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.Image;
import org.geppetto.core.model.GeppettoSerializer;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.variables.VariablesFactory;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.util.ModelUtility;

import com.google.gson.Gson;

/**
 * @author dariodelpiano
 *
 */


public class NEO4JQueryProcessor extends AQueryProcessor
{

	private Map<String, Object> processingOutputMap = new HashMap<String, Object>();

	private Boolean debug=false;

	// START VFB term info schema https://github.com/VirtualFlyBrain/VFB_json_schema/blob/master/src/json_schema/vfb_query.json

	class minimal_entity_info {
		String short_form;
		String iri;
		public String label;
		public List<String> types;
	}

	class minimal_edge_info {
		String short_form;
		private String iri;
		String label;
		String type;
	}

	class term {
		public minimal_entity_info core;
		public List<String> description;
		public List<String> comment;
	}

	class image {
		String image_folder;
		private List<Double> index;
		public minimal_entity_info template_channel;
		public minimal_entity_info template_anatomy;
	}

	class channel_image {
		image image;
		minimal_entity_info channel;
		minimal_entity_info imaging_technique;

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

	class license {
		public minimal_entity_info core;
		private String link;
		private String icon;
		private boolean is_bespoke;
	}

	class pub {
		public minimal_entity_info core;
		public String microref;
		private String PubMed;
		private String FlyBase;
		private String DOI;
		private String ISBN;
	}

	class dataset_counts{
		public Integer images;
		public Integer types;
	}

	class type {
		public String iri;
      	public String symbol;
      	public List<String> types;
      	public String label;
      	public String short_form;
	}

	class vfb_query {
		private minimal_entity_info anatomy;
		public String query;
		public String version;
		private List<anatomy_channel_image> anatomy_channel_image;
		private List<pub> pubs;
		private pub pub;
		private minimal_entity_info dataset;
		private dataset_counts dataset_counts;
		private List<license> license;
		private List<minimal_entity_info> stages;
		private minimal_entity_info expression_pattern;
		private List<anatomy_channel_image> expressed_in;
		private List<channel_image> channel_image;
		private term term;
		private List<type> types;

		public String id(){
			String delim = "----";
			String result = "undefined";
			if (this.expression_pattern != null){
				result = this.expression_pattern.short_form;
			}else if (this.dataset != null){
				result = this.dataset.short_form;
			}else if (this.anatomy != null) {
				result = this.anatomy.short_form;
			}
			if (this.anatomy != null) {
				result += delim + this.anatomy.short_form;
			}else if (this.license != null && this.license.size() > 0){
				// single license per DataSet assumed:
				result += delim + this.license.get(0).core.short_form;
			}else{
				result += delim + "undefined";
			}
			if (this.pub != null) result += delim + this.pub.core.short_form;
			if (this.pubs != null && this.pubs.size() == 1) result += delim + this.pubs.get(0).core.short_form;
			if (this.pubs != null && this.pubs.size() > 1) {
				for (pub pub:this.pubs){
					result += delim + pub.core.short_form;
				}

			}
			if (this.term != null) {
				result = this.term.core.short_form;
				if (this.types != null && this.types.size() > 0 && this.types.get(0).short_form != null) {
					result += delim + this.types.get(0).short_form;
				}
			}
			return result;
		}

		public String name(){
			if (this.expression_pattern != null) return this.expression_pattern.label;
			if (this.dataset != null) return this.dataset.label;
			if (this.term != null) return this.term.core.label;
			return this.anatomy.label;
		}

		public String grossTypes(){
			List<String> types = new ArrayList<String>();
			if (this.expression_pattern != null) types.addAll(this.expression_pattern.types);
			if (this.dataset != null) types.addAll(this.dataset.types);
			if (this.term != null) types.addAll(this.term.core.types);
			if (this.anatomy != null) types.addAll(this.anatomy.types);
			return this.returnType(types);
		}

		public String returnType(List<String> types) {
			if (types.size() > 0) {
				if (types.contains("Obsolete")){
					return this.returnType(types, Arrays.asList("Obsolete"));
				}
				if (types.contains("Deprecated")){
					return this.returnType(types, Arrays.asList("Deprecated"));
				}
				if (types.contains("Template")){
					return this.returnType(types, Arrays.asList("Adult","Larva","Template"));
				}
				if (types.contains("Motor_neuron")){
					return this.returnType(types, Arrays.asList("Adult","Larva","GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Motor_neuron"));
				}
				if (types.contains("Sensory_neuron")){
					return this.returnType(types, Arrays.asList("Adult","Larva","GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Sensory_neuron"));
				}
				if (types.contains("Peptidergic_neuron")){
					return this.returnType(types, Arrays.asList("Adult","Larva","GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Peptidergic_neuron"));
				}
				if (types.contains("Neuron")){
					return this.returnType(types, Arrays.asList("Adult","Larva","GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Neuron"));
				}
				if (types.contains("Glial_cell")){
					return this.returnType(types, Arrays.asList("Adult","Larva","GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Glial_cell"));
				}
				if (types.contains("GMC")){
					return this.returnType(types, Arrays.asList("Adult","Larva","Split","Expression_pattern","GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","GMC"));
				}
				if (types.contains("Cell")){
					return this.returnType(types, Arrays.asList("Adult","Larva","GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Neuroblast","Muscle","Cell"));
				}
				if (types.contains("Neuron_projection_bundle")){
					return this.returnType(types, Arrays.asList("Adult","Larva","Neuron_projection_bundle"));
				}
				if (types.contains("Split")){
					return this.returnType(types, Arrays.asList("Adult","Larva","Split","Expression_pattern","GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Glial_cell"));
				}
				if (types.contains("Clone")){
					return this.returnType(types, Arrays.asList("Adult","Larva","Split","Expression_pattern","GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Clone"));
				}
				if (types.contains("Cluster")){
					return this.returnType(types, Arrays.asList("Adult","Larva","Split","Expression_pattern","GABAergic","Dopaminergic","Cholinergic","Glutamatergic","Octopaminergic","Serotonergic","Cluster"));
				}
				if (types.contains("Expression_pattern")){
					return "Expression Pattern";
				}
				if (types.contains("pub")){
					return "Publication";
				}
				if (types.contains("Synaptic_neuropil")){
					return this.returnType(types, Arrays.asList("Adult","Larva","Synaptic_neuropil"));
				}
				if (types.contains("Neuromere")){
					return this.returnType(types, Arrays.asList("Adult","Larva","Neuromere"));
				}
				if (types.contains("Ganglion")){
					return this.returnType(types, Arrays.asList("Adult","Larva","Ganglion"));
				}
				return this.returnType(types, Arrays.asList("Adult","Larva","Person","License","Synaptic_neuropil","Template","Property","Anatomy","Ganglion","Clone","DataSet","Neuromere","Resource","Site","UnknownType","API"));
			}
			return "";
		}

		public String types(){
			String result = "";
			if (this.types != null && this.types.size() > 0){
				for (type type:this.types){
					if (result.equals("")){
						result += type.label;
					} else {
						result += "; " + type.label;
					}
				}
			}
			return result;
		}

		private String returnType(List<String> types, List<String> show) {
			if (types.size() > 0 && show.size() > 0) {
				String result = "";
				for (String type : show) {
					if (types.contains(type)) {
						if (result.equals("")){
							result += type;
						} else {
							result += "; " + type;
						}
					}
				}
				return result;
			}
			return "";
		}

		public String expressed_in(){
			if (this.expression_pattern != null) return this.anatomy.label;
			return "";
		}

		public String licenseLabel(){
			String result = "";
			if (this.license != null) {
				for (license l:this.license){
					if (!result.equals("")) result += "; ";
					result += l.core.label;
				}
			}
			return result;
		}

		public String stages(){
			String result = "";
			if (this.stages != null && this.stages.size() > 0) {
				for (minimal_entity_info stage:this.stages){
					if (!result.equals("")) result += "; ";
					result += stage.label;
				}
			}
			return result;
		}

		public String reference(){
			String result = "";
			if (this.pub != null) result += this.pub.core.label;
			if (this.pubs != null && this.pubs.size() > 0) {
				for (pub pub:this.pubs){
					if (!result.equals("")) result += "; ";
					result += pub.core.label;
				}
			}
			return result;
		}

		public String technique(){
			String result = "";
			if (this.channel_image != null && this.channel_image.size() > 0) {
				for (channel_image ci:this.channel_image){
					if (ci.imaging_technique.label != null && result.indexOf(ci.imaging_technique.label) > -1){
						if (!result.equals("")) result += "; ";
						result += ci.imaging_technique.label;
					}
				}
			}
			if (this.anatomy_channel_image != null && this.anatomy_channel_image.size() > 0) {
				for (anatomy_channel_image aci:this.anatomy_channel_image){
					if (aci.channel_image.imaging_technique.label != null && result.indexOf(aci.channel_image.imaging_technique.label) > -1){
						if (!result.equals("")) result += "; ";
						result += aci.channel_image.imaging_technique.label;
					}
				}
			}
			if (debug) System.out.println("Technique:" + result.toString());
			return result;
		}

		public String template(){
			String result = "";
			if (this.channel_image != null && this.channel_image.size() > 0) {
				for (channel_image ci:this.channel_image){
					if (ci.image.template_anatomy.label != null && result.indexOf(ci.image.template_anatomy.label) > -1){
						if (!result.equals("")) result += "; ";
						result += ci.image.template_anatomy.label;
					}
				}
			}
			if (this.anatomy_channel_image != null && this.anatomy_channel_image.size() > 0) {
				for (anatomy_channel_image aci:this.anatomy_channel_image){
					if (aci.channel_image.image.template_anatomy.label != null && result.indexOf(aci.channel_image.image.template_anatomy.label) > -1){
						if (!result.equals("")) result += "; ";
						result += aci.channel_image.image.template_anatomy.label;
					}
				}
			}
			if (debug) System.out.println("Template:" + result.toString());
			return result;
		}

		public ArrayValue images() {
			return this.images("");
		}

		public ArrayValue images(String template) {
			ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
			try{
				if (template == null || template.equals("")){
					//default to JRC2018U
					template = "VFB_00101567";
				}
				int j = 0;
				int f = 0;
				int c = 0;
				List<String> loaded = new ArrayList<String>();
				if (this.anatomy_channel_image != null) {
					f = this.anatomy_channel_image.size();
					c = f;
					for (anatomy_channel_image anat : this.anatomy_channel_image) {
						// add same template to the begining and others at the end.
						if (anat.channel_image != null && anat.channel_image.image != null && anat.channel_image.image.template_anatomy != null && anat.channel_image.image.template_anatomy.short_form != null && template.equals(anat.channel_image.image.template_anatomy.short_form)) {
							if (loaded.contains(anat.anatomy.short_form)) {
								imageArray = ValuesFactory.eINSTANCE.createArrayValue();
								j = 0;
							}
							addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, j);
							if (loaded.contains(anat.anatomy.short_form)) {
								return imageArray;
							}
							loaded.add(anat.anatomy.short_form);
							j++;
						} else {
							if (!loaded.contains(anat.anatomy.short_form)) {
								f--;
								addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, f);
								loaded.add(anat.anatomy.short_form);
							}
						}
					}
				}
				if (this.expressed_in != null) {
					j = c;
					f = c + this.expressed_in.size();
					for (anatomy_channel_image anat : this.expressed_in) {
						// add same template to the begining and others at the end.
						if (anat.channel_image != null && anat.channel_image.image != null && anat.channel_image.image.template_anatomy != null && anat.channel_image.image.template_anatomy.short_form != null && template.equals(anat.channel_image.image.template_anatomy.short_form)) {
							if (loaded.contains(anat.anatomy.short_form)) {
								imageArray = ValuesFactory.eINSTANCE.createArrayValue();
								j = 0;
							}
							addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, j);
							if (loaded.contains(anat.anatomy.short_form)) {
								return imageArray;
							}
							loaded.add(anat.anatomy.short_form);
							j++;
						} else {
							f--;
							addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, f);
							loaded.add(anat.anatomy.short_form);
						}
					}
				}
				if (this.channel_image != null) {
					f = this.channel_image.size();
					c = f;
					for (channel_image anat : this.channel_image) {
						// add same template to the begining and others at the end.
						if (anat != null && anat.image != null && anat.image.template_anatomy != null && anat.image.template_anatomy.short_form != null && template.equals(anat.image.template_anatomy.short_form)) {
							if (loaded.contains(this.term.core.short_form)) {
								imageArray = ValuesFactory.eINSTANCE.createArrayValue();
								j = 0;
							}
							addImage(anat.getUrl("", "thumbnailT.png"), this.term.core.label, this.term.core.short_form, imageArray, j);
							if (loaded.contains(this.term.core.short_form)) {
								return imageArray;
							}
							loaded.add(this.term.core.short_form);
							j++;
						} else {
							if (!loaded.contains(this.term.core.short_form)) {
								f--;
								addImage(anat.getUrl("", "thumbnailT.png"), this.term.core.label, this.term.core.short_form, imageArray, f);
								loaded.add(this.term.core.short_form);
							}
						}
					}
				}
			}catch (Exception e) {
				System.out.println("Error in vfbQuery.images(): " + e.toString());
				e.printStackTrace();
				return null;
			}
			return imageArray;
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

		private String secureUrl(String url) {
			return url.replace("http://","https://");
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
		try{
			if(results == null)
			{
				throw new GeppettoDataSourceException("Results input to " + query.getName() + "is null");
			}
			QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();

			String json = "{";
			String tempData = "";
			String header = "start";
			Integer count = 0;
			Boolean	hasId = false;
			Boolean	hasName = false;
			Boolean hasLicense = false;
			Boolean hasDatasetCount = false;
			Boolean	hasExpressed_in = false;
			Boolean	hasReference = false;
			Boolean	hasStage = false;
			Boolean hasImage = false;
			Boolean hasTypes = false;
			Boolean hasGrossType = false;
			Boolean hasTemplate = false;
			Boolean hasTechnique = false;
			List<vfb_query> table = new ArrayList<vfb_query>();
			vfb_query vfbQuery = null;

			// Template space:
			String template = "";
			String loadedTemplate = "";

			// Determine loaded template
			CompositeType testTemplate = null;
			List<String> availableTemplates = Arrays.asList("VFB_00017894","VFB_00101567","VFB_00101384","VFB_00050000","VFB_00049000","VFB_00100000","VFB_00030786");
			for (String at:availableTemplates) {
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
				}
			}

			Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);Variable imageVariable = VariablesFactory.eINSTANCE.createVariable();

			if (debug) System.out.println("Processing JSON...");
			// Match to vfb_query schema:
			for(AQueryResult result : results.getResults()){
				try{

					header = "results>JSON";
					json = "{";
					if (debug) System.out.println("{");

					for (String key:results.getHeader()) {
						if (!json.equals("{")) {
							json = json + ", ";
						}
						switch(key) {
							case "anatomy":
								hasId = true;
								hasName = true;
								hasGrossType = true;
								break;
							case "term":
								hasId = true;
								hasName = true;
								hasGrossType = true;
								break;
							case "dataset":
								hasId = true;
								hasName = true;
								hasGrossType = true;
								break;
							case "expression_pattern":
								hasId = true;
								hasName = true;
								hasExpressed_in=true;
								hasGrossType = true;
								break;
							case "pubs":
								hasReference = true;
								break;
							case "pub":
								hasReference = true;
								break;
							case "license":
								hasLicense = true;
								break;
							case "dataset_counts":
								hasDatasetCount = true;
								break;
							case "stages":
								hasStage = true;
								break;
							case "anatomy_channel_image":
								hasImage = true;
								hasTemplate = true;
								hasTechnique = true;
								break;
							case "expressed_in":
								hasImage = true;
								break;
							case "channel_image":
								hasImage = true;
								hasTemplate = true;
								hasTechnique = true;
								break;
							case "types":
								hasTypes = true;
								break;
						}
						tempData = new Gson().toJson(results.getValue(key, count));
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
					vfbQuery = new Gson().fromJson(json , vfb_query.class);

					table.add(vfbQuery);

				}catch (Exception e) {
					System.out.println("Row: " + count.toString() + " Error creating " + header + ": " + e.toString());
					e.printStackTrace();
					System.out.println(json.replace("}","}\n"));
				}
				count ++;
			}
			vfbQuery = null;
			count = 0;

			// set headers
			processedResults.getHeader().add("ID");
			if (hasName) processedResults.getHeader().add("Name");
			if (hasTypes) processedResults.getHeader().add("Type");
			if (hasGrossType) processedResults.getHeader().add("Gross_Type");
			if (hasExpressed_in) processedResults.getHeader().add("Expressed_in");
			if (hasLicense) processedResults.getHeader().add("License");
			if (hasReference) processedResults.getHeader().add("Reference");
			if (hasStage) processedResults.getHeader().add("Stage");
			if (hasTemplate) processedResults.getHeader().add("Template_Space");
			if (hasTechnique) processedResults.getHeader().add("Imaging_Technique");
			if (hasImage) processedResults.getHeader().add("Images");
			if (hasDatasetCount) processedResults.getHeader().add("Image_count");

			for (vfb_query row:table){
				try{
					SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
					String length = "8";
					if (hasId) processedResult.getValues().add(row.id());
					if (hasName) processedResult.getValues().add(row.name());
					if (hasTypes) processedResult.getValues().add(row.types());
					if (hasGrossType) processedResult.getValues().add(row.grossTypes());
					if (hasExpressed_in) processedResult.getValues().add(row.expressed_in());
					if (hasLicense) processedResult.getValues().add(row.licenseLabel());
					if (hasReference) processedResult.getValues().add(row.reference());
					if (hasStage) processedResult.getValues().add(row.stages());
					if (hasTemplate) processedResult.getValues().add(row.template());
					if (hasTechnique) processedResult.getValues().add(row.technique());
					if (hasImage){
						Variable exampleVar = VariablesFactory.eINSTANCE.createVariable();
						exampleVar.setId("images");
						exampleVar.setName("Images");
						exampleVar.getTypes().add(imageType);
						ArrayValue images = row.images(template);
						if (!images.getElements().isEmpty())
						{
							if (images.getElements().size() > 1){
								exampleVar.getInitialValues().put(imageType, images);
							}else{
								exampleVar.getInitialValues().put(imageType, images.getElements().get(0).getInitialValue());
							}
							processedResult.getValues().add(GeppettoSerializer.serializeToJSON(exampleVar));
							if (false && debug) System.out.println("DEBUG: Image: " + GeppettoSerializer.serializeToJSON(exampleVar) );
						}
						else
						{
							processedResult.getValues().add("");
						}
					}
					if (hasDatasetCount) processedResult.getValues().add(String.format("%1$" + length + "s", row.dataset_counts.images.toString()));
					processedResults.getResults().add(processedResult);
				}catch (Exception e) {
					System.out.println("Error creating results row: " + count.toString() + " - " + e.toString());
					e.printStackTrace();
				}
				count ++;
			}
			if (debug) System.out.println("NEO4JQueryProcessor returning " + count.toString() + " rows");

			return processedResults;

		}
		catch(GeppettoVisitingException e)
		{
			System.out.println(e);
			e.printStackTrace();
			throw new GeppettoDataSourceException(e);
		}
		catch(Exception e)
		{
			System.out.println(e);
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public Map<String, Object> getProcessingOutputMap()
	{
		return processingOutputMap;
	}
}
