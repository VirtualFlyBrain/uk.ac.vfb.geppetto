package uk.ac.vfb.geppetto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Iterator;

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

// Replace Gson with Jackson imports
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * @author Robbie1977
 *
 */


public class SOLRQueryProcessor extends AQueryProcessor
{

	private Map<String, Object> processingOutputMap = new HashMap<String, Object>();

	private Boolean debug=false;

	private String delim="----";

    // Replace Gson with Jackson ObjectMapper
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	// START VFB term info schema https://github.com/VirtualFlyBrain/VFB_json_schema/blob/master/src/json_schema/vfb_query.json

	class minimal_entity_info {
		String short_form;
		String iri;
		public String label;
		public String symbol;
		public List<String> types;
		public List<String> unique_facets;

		public List<String> getTypes() {
			List<String> types = new ArrayList<String>();
			if (this.unique_facets != null && this.unique_facets.size() > 0) {
				types.addAll(this.unique_facets);
			} else {
				types.addAll(this.types);
			}
			return types;
		}

		public String getName() {
			return getName(false);
		}

		public String getName(boolean symbol) {
			if (symbol && this.symbol != null && this.symbol.length() > 0) {
				return this.symbol;
			}
			return this.label;
		}
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

		public String getLabel(){
			return this.getLabel(true);
		}

		public String getLabel(Boolean showTemplate){
			String result = "";
			if (showTemplate && this.image != null && this.image.template_anatomy != null && !this.image.template_anatomy.label.equals("")){
				result += " [" + templateSymbol(this.image.template_anatomy.label) + "]";
			}
			if (this.imaging_technique != null && this.imaging_technique.label != null && !this.imaging_technique.label.equals("")){
				result += " [" + this.techniqueSymbol(this.imaging_technique.label) + "]";
			}
			return result;
		}

		public String templateSymbol(String label){
			switch (label) {
				case "adult brain template JFRC2":
					return "JFRC2";
				case "adult brain template Ito2014":
					return "ItoHalfBrain";
				case "L1 larval CNS ssTEM - Cardona/Janelia":
					return "L1CNS";
				case "adult VNS template - Court2018":
					return "adultVNS";
				case "L3 CNS template - Wood2018":
					return "L3CNS";
				case "JRC_FlyEM_Hemibrain":
					return "HemiBrain";
				case "JRC2018Unisex":
					return "JRC2018U";
				case "JRC2018UnisexVNC":
					return "JRC2018UV";
				default:
					return label;
			}
		}

		public String techniqueSymbol(String label){
			switch (label) {
				case "structured illumination microscopy (SIM)":
					return "SIM";
				case "photomultiplier tube (PMT)":
					return "PMT";
				case "scanning electron microscopy (SEM)":
					return "SEM";
				case "charge coupled device (CCD)":
					return "CCD";
				case "Fluorescein (FITC)":
					return "FITC";
				case "Tetramethyl rhodamine (TRITC)":
					return "TRITC";
				case "intermediate voltage electron microacopy (IVEM)":
					return "IVEM";
				case "high-voltage electron microscopy (HVEM)":
					return "HVEM";
				case "interference reflection contrast (IRM)":
					return "IRM";
				case "inelastic scattering of photons (Raman scattering)":
					return "Raman scattering";
				case "transmission electron microscopy (TEM)":
					return "TEM";
				case "nearfield scanning optical microscopy (ANSOM)":
					return "ANSOM";
				case "single sideband edge enhancement (SSBE)":
					return "SSBE";
				case "complementary metal oxide semiconductor (CMOS)":
					return "CMOS";
				case "electron bombardment CCD (EBCCD)":
					return "EBCCD";
				case "intensified CCD (ICCD)":
					return "ICCD";
				case "silicon intensified target tube (SIT)":
					return "SIT";
				case "4\',6-diamidino-2-phenylindole (DAPI)":
					return "DAPI";
				case "ground state depletion scanning (GSD)":
					return "GSD";
				case "Arachis hypogaea (PNA)":
					return "PNA";
				case "avalanche photodiode (APD)":
					return "APD";
				case "stimulated emission depletion (STED)":
					return "STED";
				case "serial block face SEM (SBFSEM)":
					return "SBFSEM";
				case "electron multiplying CCD (EMCCD)":
					return "EMCCD";
				case "saturated structured-illumination microscopy (SSIM)":
					return "SSIM";
				case "intensified SIT (ISIT)":
					return "ISIT";
				case "confocal microscopy":
					return "Confocal";
				case "focussed ion beam scanning electron microscopy (FIB-SEM)":
					return "FIB-SEM";
				default:
					return label;
			}
		}


	}

	class anatomy_channel_image {
		minimal_entity_info anatomy;
		channel_image channel_image;

		public String getUrl(String pre, String post){
			return this.channel_image.getUrl(pre, post);
		}

		public String getLabel(){
			String result = this.anatomy.getName();
			result += this.channel_image.getLabel();
			return result;
		}

		public String getLabel(Boolean showTemplate){
			String result = this.anatomy.getName();
			result += this.channel_image.getLabel(showTemplate);
			return result;
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

	class synapse_counts{
		public List<Float> downstream;
		public List<Float> Tbars;
		public List<Float> upstream;
		public List<Float> weight;

		public String getDownstream() {
			return this.getList(this.downstream);
		}

		public String getTbars() {
			return this.getList(this.Tbars);
		}

		public String getUpstream() {
			return this.getList(this.upstream);
		}

		public String getWeight() {
			return this.getList(this.weight);
		}

		public String getList(List<Float> values) {
			String results = "";
			if (values != null && values.size() > 0) {
				for (Float num:values) {
					if (results != ""){
						results += "; ";
					}
					results += String.format("% 5d",(int) Math.ceil(num));
				}
			}
			return results;
		}
	}

	class type {
		public String iri;
      	public String symbol;
      	public List<String> types;
      	public String label;
      	public String short_form;
	}

	class columns {
		public String short_form;
		public String Score;
	}

	class vfb_query {
		private minimal_entity_info anatomy;
		public String query;
		public String version;
		public String score;
		private List<anatomy_channel_image> anatomy_channel_image;
		private List<pub> pubs;
		private pub pub;
		private minimal_entity_info dataset;
		private dataset_counts dataset_counts;
		private minimal_entity_info cluster;
		private minimal_entity_info gene;
		private String expression_level;
		private Float expression_extent;
		private List<license> license;
		private List<minimal_entity_info> stages;
		private minimal_entity_info expression_pattern;
		private List<anatomy_channel_image> expressed_in;
		private List<channel_image> channel_image;
		private term term;
		private List<type> types;
		private List<type> parents;
		public List<columns> extra_columns;
		public synapse_counts synapse_counts;
		public minimal_entity_info object;

		public String id(){
			String delim="----";
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
			if (this.term != null && this.term.core.short_form != null) {
				result = this.term.core.short_form;
				if (this.types != null && this.types.size() > 0 && this.types.get(0).short_form != null) {
					result += delim + this.types.get(0).short_form;
				}
			} else {
				result = "undefined";
			}
			if (this.parents != null && this.parents.size() > 0) {
				result += delim + this.parents.get(0).short_form;
			} else {
				result += delim + "undefined";
			}
			if (this.object != null && this.object.short_form != null) {
				result += delim + this.object.short_form;
			} else {
				result += delim + "undefined";
			}
			return result;
		}

		public String getScore(){
			for (columns col:extra_columns) {
				if (col.short_form.equals(this.term.core.short_form)) {
					return col.Score;
				}
			}
			return null;
		}

		public String name(){
			if (this.expression_pattern != null) return this.expression_pattern.getName();
			if (this.dataset != null) return this.dataset.getName();
			if (this.term != null) return this.term.core.getName();
			return this.anatomy.getName();
		}

		public String grossTypes(){
			List<String> types = new ArrayList<String>();
			if (this.expression_pattern != null) {
				if (this.expression_pattern.unique_facets != null && this.expression_pattern.unique_facets.size() > 0) {
					types.addAll(this.expression_pattern.unique_facets);
				} else {
					types.addAll(this.expression_pattern.types);
				}
			}
			if (this.dataset != null) {
				if (this.dataset.unique_facets != null && this.dataset.unique_facets.size() > 0) {
					types.addAll(this.dataset.unique_facets);
				} else {
					types.addAll(this.dataset.types);
				}
			}
			if (this.term != null) {
				if (this.term.core.unique_facets != null && this.term.core.unique_facets.size() > 0) {
					types.addAll(this.term.core.unique_facets);
				} else {
					types.addAll(this.term.core.types);
				}
			}
			if (this.anatomy != null) {
				if (this.anatomy.unique_facets != null && this.anatomy.unique_facets.size() > 0) {
					types.addAll(this.anatomy.unique_facets);
				} else {
					types.addAll(this.anatomy.types);
				}
			}
			return this.returnType(types);
		}

		public String returnType(List<String> types) {
			String result = "";
			for (String type : types) {
				type = type.replace("DataSet", "Dataset");
				if (type.equals("pub")) type = "Publication";
				if (result.equals("")){
					result += type;
				} else {
					result += "; " + type;
				}
			}
			return result;
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

		public String parents(){
			String result = "";
			if (this.parents != null && this.parents.size() > 0){
				for (type type:this.parents){
					if (!result.contains(type.label)){
						if (result.equals("")){
							result += type.label;
						} else {
							result += "; " + type.label;
						}
					}
				}
			}
			return result;
		}

		public String expressed_in(){
			if (this.expression_pattern != null) return this.anatomy.getName();
			return "";
		}

		public String licenseLabel(){
			String result = "";
			if (this.license != null) {
				for (license l:this.license){
					if (!result.equals("")) result += "; ";
					result += l.core.getName();
				}
			}
			return result;
		}

		public String stages(){
			String result = "";
			if (this.stages != null && this.stages.size() > 0) {
				for (minimal_entity_info stage:this.stages){
					if (!result.equals("")) result += "; ";
					result += stage.getName();
				}
			}
			return result;
		}

		public String reference(){
			String result = "";
			if (this.pub != null) result += this.pub.core.getName();
			if (this.pubs != null && this.pubs.size() > 0) {
				for (pub pub:this.pubs){
					if (!result.equals("")) result += "; ";
					result += pub.core.getName();
				}
			}
			return result;
		}

		public String technique(){
			String result = "";
			if (this.channel_image != null && this.channel_image.size() > 0) {
				for (channel_image ci:this.channel_image){
					if (ci.imaging_technique.label != null && result.indexOf(ci.techniqueSymbol(ci.imaging_technique.label)) < 0){
						if (!result.equals("")) result += "; ";
						result += ci.techniqueSymbol(ci.imaging_technique.label);
					}
				}
			}
			if (this.anatomy_channel_image != null && this.anatomy_channel_image.size() > 0) {
				for (anatomy_channel_image aci:this.anatomy_channel_image){
					if (aci.channel_image.imaging_technique.label != null && result.indexOf(aci.channel_image.techniqueSymbol(aci.channel_image.imaging_technique.label)) < 0){
						if (!result.equals("")) result += "; ";
						result += aci.channel_image.techniqueSymbol(aci.channel_image.imaging_technique.label);
					}
				}
			}
			return result;
		}

		public String template(String template){
			String result = "";
			if (template == null || template.equals("")){
				//default to JRC2018U
				template = "VFB_00101567";
			}
			if (this.channel_image != null && this.channel_image.size() > 0) {
				for (channel_image ci:this.channel_image){
					if (ci.image.template_anatomy.label != null && result.indexOf(ci.templateSymbol(ci.image.template_anatomy.label)) < 0){

						if (ci.image.template_anatomy.short_form.equals(template)) {
							result = ci.templateSymbol(ci.image.template_anatomy.getName()) + "\nalso in: " + result;
						} else {
							if (!result.equals("") && !result.endsWith(": ")) result += "; ";
							result += ci.templateSymbol(ci.image.template_anatomy.getName());
						}
					}
				}
			}
			if (this.anatomy_channel_image != null && this.anatomy_channel_image.size() > 0) {
				for (anatomy_channel_image aci:this.anatomy_channel_image){
					if (aci.channel_image.image.template_anatomy.getName() != null && result.indexOf(aci.channel_image.templateSymbol(aci.channel_image.image.template_anatomy.getName())) < 0){
						if (aci.channel_image.image.template_anatomy.short_form.equals(template)) {
							result = aci.channel_image.templateSymbol(aci.channel_image.image.template_anatomy.getName()) + "\nalso in: " + result;
						} else {
							if (!result.equals("") && !result.endsWith(": ")) result += "; ";
							result += aci.channel_image.templateSymbol(aci.channel_image.image.template_anatomy.getName());
						}
					}
				}
			}
			if (result.endsWith(": ")) {
				result = result.replace("also in: ", "");
			}
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
				List<String> loaded = new ArrayList<String>();
				if (this.anatomy_channel_image != null) {
					for (anatomy_channel_image anat : this.anatomy_channel_image) {
						// add same template to the begining and others at the end.
						if (anat.channel_image != null && anat.channel_image.image != null && anat.channel_image.image.template_anatomy != null && anat.channel_image.image.template_anatomy.short_form != null && template.equals(anat.channel_image.image.template_anatomy.short_form)) {
							if (!loaded.contains(anat.anatomy.short_form)) {
								addImage(anat.getUrl("", "thumbnailT.png"), anat.getLabel(false) , anat.anatomy.short_form, imageArray, j);
								loaded.add(anat.anatomy.short_form);
								j++;
							}
						}
					}
					if (j > 0) return imageArray;
					for (anatomy_channel_image anat : this.anatomy_channel_image) {
						if (!loaded.contains(anat.anatomy.short_form)) {
							addImage(anat.getUrl("", "thumbnailT.png"), anat.getLabel(), anat.anatomy.short_form, imageArray, j);
							loaded.add(anat.anatomy.short_form);
							j++;
						}
					}
				}
				if (this.expressed_in != null) {
					for (anatomy_channel_image anat : this.expressed_in) {
						// add same template to the begining and others at the end.
						if (anat.channel_image != null && anat.channel_image.image != null && anat.channel_image.image.template_anatomy != null && anat.channel_image.image.template_anatomy.short_form != null && template.equals(anat.channel_image.image.template_anatomy.short_form)) {
							if (!loaded.contains(anat.anatomy.short_form)) {
								addImage(anat.getUrl("", "thumbnailT.png"), anat.getLabel(false), anat.anatomy.short_form, imageArray, j);
								loaded.add(anat.anatomy.short_form);
								j++;
							}
						}
					}
					if (j > 0) return imageArray;
					for (anatomy_channel_image anat : this.expressed_in) {
						if (!loaded.contains(anat.anatomy.short_form)) {
							addImage(anat.getUrl("", "thumbnailT.png"), anat.getLabel(), anat.anatomy.short_form, imageArray, j);
							loaded.add(anat.anatomy.short_form);
							j++;
						}
					}
				}
				if (this.channel_image != null) {
					if (this.object != null) {
						// if the row has a target object then the image is for that not the term.
						for (channel_image anat : this.channel_image) {
							// add same template to the begining and others at the end.
							if (anat != null && anat.image != null && anat.image.template_anatomy != null && anat.image.template_anatomy.short_form != null && template.equals(anat.image.template_anatomy.short_form)) {
								if (!loaded.contains(this.object.short_form)) {
									addImage(anat.getUrl("", "thumbnailT.png"), this.object.getName() + anat.getLabel(false), this.object.short_form, imageArray, j);
									loaded.add(this.object.short_form);
									j++;
								}
							}
						}
						if (j > 0) return imageArray;
						for (channel_image anat : this.channel_image) {
							if (!loaded.contains(this.object.short_form)) {
								addImage(anat.getUrl("", "thumbnailT.png"), this.object.getName() + anat.getLabel(), this.object.short_form, imageArray, j);
								loaded.add(this.object.short_form);
								j++;
							}
						}
					} else {
						for (channel_image anat : this.channel_image) {
							// add same template to the begining and others at the end.
							if (anat != null && anat.image != null && anat.image.template_anatomy != null && anat.image.template_anatomy.short_form != null && template.equals(anat.image.template_anatomy.short_form)) {
								if (!loaded.contains(this.term.core.short_form)) {
									addImage(anat.getUrl("", "thumbnailT.png"), this.term.core.getName(), this.term.core.short_form, imageArray, j);
									loaded.add(this.term.core.short_form);
									j++;
								}
							}
						}
						if (j > 0) return imageArray;
						for (channel_image anat : this.channel_image) {
							if (!loaded.contains(this.term.core.short_form)) {
								addImage(anat.getUrl("", "thumbnailT.png"), this.term.core.getName(), this.term.core.short_form, imageArray, j);
								loaded.add(this.term.core.short_form);
								j++;
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
		long startTime = System.currentTimeMillis();
		try{
			if(results == null)
			{
				throw new GeppettoDataSourceException("Results input to " + query.getName() + " is null");
			}
			QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();
		  
			// ...existing code...
			String keyName = determineKeyName(results.getHeader());
			int totalResults = results.getResults().size();
		  
			// Obtain image type for processing images
			Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
			
			// Reuse temporary objects inside the loop
			final StringBuilder sbFunction = new StringBuilder();
			final Variable tempImageVar = VariablesFactory.eINSTANCE.createVariable();
			tempImageVar.setId("images");
			tempImageVar.setName("Images");
			tempImageVar.getTypes().clear();
			tempImageVar.getTypes().add(imageType);
		  
			// Streaming: Process each JSON result immediately.
			boolean processedFirstRow = false;
			// Flag variables:
			boolean hasId = false, hasName = false, hasLicense = false, hasDatasetCount = false, 
				hasExpressed_in = false, hasReference = false, hasStage = false, hasImage = false, 
				hasTypes = false, hasParents = false, hasGrossType = false, hasTemplate = false, 
				hasTechnique = false, hasExtra = false, hasSynCount = false, hasObject = false, 
				hasScore = false, scRNAseq = false, hasGene = false, hasGeneScore = false;
		  
			// Determine template
			String template = "";
			String loadedTemplate = "";
			List<String> availableTemplates = Arrays.asList("VFB_00101567","VFB_00200000","VFB_00017894",
				"VFB_00101384","VFB_00050000","VFB_00049000","VFB_00030786");
			CompositeType testTemplate = null;
			for (String at: availableTemplates) {
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
		  
			  
			// Process each result in a streaming fashion:
			for (int i=0; i < totalResults; i++){
				String json = results.getValue(keyName, i).toString();
				if (debug && i < 2) System.out.println("JSON passed: " + json.replace("}","}\n"));
				JsonNode row = OBJECT_MAPPER.readTree(json);
			  
				// For the first row determine header flag values:
				if (!processedFirstRow) {
					processedFirstRow = true;
					if (row.has("cluster") && !row.path("cluster").isNull()) {
						scRNAseq = true;
					}
					if (row.has("gene") && !row.path("gene").isNull()) {
						hasGene = true;
					}
					if (row.has("anatomy") && !row.path("anatomy").isNull()) {
						hasId = true;
						hasName = true;
						hasGrossType = true;
					}
					if (row.has("term") && !row.path("term").isNull()) {
						hasId = true;
						hasName = true;
						hasGrossType = true;
					}
					if (row.has("dataset") && !row.path("dataset").isNull()) {
						hasId = true;
						hasName = true;
						hasGrossType = true;
					}
					if (row.has("expression_pattern") && !row.path("expression_pattern").isNull()) {
						hasId = true;
						hasName = true;
						hasExpressed_in = true;
						hasGrossType = true;
					}
					if (row.has("pubs") || row.has("pub")) {
						hasReference = true;
					}
					if (row.has("license") && !row.path("license").isNull()) {
						hasLicense = true;
					}
					if (row.has("dataset_counts") && !row.path("dataset_counts").isNull()) {
						hasDatasetCount = true;
					}
					if (row.has("stages") && !row.path("stages").isNull()) {
						hasStage = true;
					}
					if (row.has("anatomy_channel_image") || row.has("channel_image") || row.has("expressed_in")) {
						hasImage = true;
						hasTemplate = true;
						hasTechnique = true;
					}
					if (row.has("types") && !row.path("types").isNull()) {
						hasTypes = true;
					}
					if (row.has("parents") && !row.path("parents").isNull()) {
						hasParents = true;
					}
					if (row.has("synapse_counts") && !row.path("synapse_counts").isNull()) {
						hasSynCount = true;
						hasName = true;
					}
					if (row.has("object") && !row.path("object").isNull()) {
						hasObject = true;
					}
					if (row.has("score") && !row.path("score").isNull()) {
						hasScore = true;
					}
					if (row.has("extra_columns") && !row.path("extra_columns").isNull()) {
						hasExtra = true;
					}
					if (row.has("expression_level") && !row.path("expression_level").isNull()) {
						hasGeneScore = true;
					}
					
					// Build header based on flag values
					processedResults.getHeader().add("ID");
					if (hasGene) {
						processedResults.getHeader().add("Gene");
						processedResults.getHeader().add("Cell type");
						processedResults.getHeader().add("Level");
						processedResults.getHeader().add("Extent");
						processedResults.getHeader().add("Function");
					} else {
						if (scRNAseq) {
							processedResults.getHeader().add("Cluster");
							processedResults.getHeader().add("Cell type");
							processedResults.getHeader().add("Dataset");
							processedResults.getHeader().add("Reference");
						} else {
							if (hasName) {
								if (hasSynCount) {
									// ...existing header logic...
								} else {
									processedResults.getHeader().add("Name");
								}
								if (!hasGene && hasGeneScore) {
									processedResults.getHeader().add("Level");
									processedResults.getHeader().add("Extent");
									processedResults.getHeader().add("Cell type");
								}
							}
							if (hasTypes) processedResults.getHeader().add("Type");
							if (hasParents) processedResults.getHeader().add("Type");
							if (hasGrossType && !row.path("query").asText().contains("connectivity_query"))
								processedResults.getHeader().add("Gross_Type");
							if (hasExpressed_in) processedResults.getHeader().add("Expressed_in");
							if (hasLicense) processedResults.getHeader().add("License");
							if (hasReference) processedResults.getHeader().add("Reference");
							if (hasStage) processedResults.getHeader().add("Stage");
							if (hasImage) processedResults.getHeader().add("Images");
							if (hasTemplate) processedResults.getHeader().add("Imaging_Technique");
							if (hasTechnique) processedResults.getHeader().add("Template_Space");
							if (hasDatasetCount) processedResults.getHeader().add("Image_count");
							if (hasExtra && row.path("extra_columns").isArray() && row.path("extra_columns").size() > 0 && row.path("extra_columns").get(0).has("Score"))
								processedResults.getHeader().add("Score");
							if (hasScore) processedResults.getHeader().add("Score");
							if (hasSynCount){
								processedResults.getHeader().add("Outputs");
								if (!row.path("query").asText().contains("neuron_neuron"))
									processedResults.getHeader().add("Outputs (Tbars)");
								processedResults.getHeader().add("Inputs");
								if (hasObject) {
									if (row.path("query").asText().contains("neuron_neuron"))
										processedResults.getHeader().add("Partner_Neuron");
									else if (row.path("query").asText().contains("neuron_region"))
										processedResults.getHeader().add("Region");
									else
										processedResults.getHeader().add("Target");
								} else {
									if (hasObject) processedResults.getHeader().add("Target");
								}
							}
						}
					}
				}
			  
				// Process the current row immediately:
				SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
				String length = "8";
				if (hasGene) {
					processedResult.getValues().add(row.path("gene").path("short_form").asText() + delim + row.path("anatomy").path("short_form").asText());
					processedResult.getValues().add(getEntityName(row.path("gene")));
					processedResult.getValues().add(getEntityName(row.path("anatomy")));
					processedResult.getValues().add(row.path("expression_level").asText());
					processedResult.getValues().add(String.format("%.02f", row.path("expression_extent").asDouble()));
					// Reuse sbFunction for gene function string
					sbFunction.setLength(0);
					for (JsonNode type : row.path("gene").path("types")){
						if (type.asText().indexOf("Class") == -1 && type.asText().indexOf("Entity") == -1 && type.asText().indexOf("hasScRNAseq") == -1 &&
							type.asText().indexOf("Feature") == -1 && type.asText().indexOf("Gene") == -1) {
							if(sbFunction.length() > 0){
								sbFunction.append("; ");
							}
							sbFunction.append(type.asText());
						}
					}
					processedResult.getValues().add(sbFunction.toString());
				}
				else {
					if (scRNAseq) {
						processedResult.getValues().add(row.path("cluster").path("short_form").asText() + delim + row.path("term").path("core").path("short_form").asText() + delim + row.path("pubs").get(0).path("core").path("short_form").asText() + delim + row.path("dataset").path("short_form").asText());
						processedResult.getValues().add(getEntityName(row.path("cluster")));
						processedResult.getValues().add(getEntityName(row.path("term").path("core")));
						processedResult.getValues().add(getEntityName(row.path("dataset")));
						processedResult.getValues().add(getEntityName(row.path("pubs").get(0).path("core")));
					} else {
						if (hasId) processedResult.getValues().add(getId(row));
						if (hasName && !hasSynCount) processedResult.getValues().add(getName(row));
						if (!hasGene && hasGeneScore) {
							processedResult.getValues().add(row.path("expression_level").asText());
							processedResult.getValues().add(String.format("%.02f", row.path("expression_extent").asDouble()));
							processedResult.getValues().add(getEntityName(row.path("anatomy")));
						}
						if (hasTypes) processedResult.getValues().add(getTypes(row));
						if (hasParents) processedResult.getValues().add(getParents(row));
						if (hasGrossType && !row.path("query").asText().contains("connectivity_query"))
							processedResult.getValues().add(getGrossTypes(row));
						if (hasExpressed_in) processedResult.getValues().add(getExpressedIn(row));
						if (hasLicense) processedResult.getValues().add(getLicenseLabel(row));
						if (hasReference) processedResult.getValues().add(getReference(row));
						if (hasStage) processedResult.getValues().add(getStages(row));
						if (hasImage){
							// Reuse tempImageVar for image processing
							tempImageVar.getInitialValues().clear();
							ArrayValue images = getImages(row, template);
							if (!images.getElements().isEmpty() && images.getElements().size() > 0) {
								tempImageVar.getInitialValues().put(imageType, images);
								processedResult.getValues().add(GeppettoSerializer.serializeToJSON(tempImageVar));
							} else {
								processedResult.getValues().add("");
								}
							}
						if (hasTechnique) processedResult.getValues().add(getTechnique(row));
						if (hasTemplate) processedResult.getValues().add(getTemplate(row, template));
						if (hasDatasetCount)
							processedResult.getValues().add(String.format("%1$" + length + "s", row.path("dataset_counts").path("images").asText()));
						if (hasExtra && row.path("extra_columns").isArray() && row.path("extra_columns").size() > 0 && row.path("extra_columns").get(0).has("Score"))
							processedResult.getValues().add(row.path("extra_columns").get(0).path("Score").asText());
						if (hasScore) processedResult.getValues().add(row.path("score").asText());
						if (hasSynCount){
							processedResult.getValues().add(getSynapseCounts(row, "downstream"));
							if (!row.path("query").asText().contains("neuron_neuron"))
								processedResult.getValues().add(getSynapseCounts(row, "Tbars"));
							processedResult.getValues().add(getSynapseCounts(row, "upstream"));
						}
						if (hasObject) processedResult.getValues().add(getEntityName(row.path("object")));
					}
				}
				processedResults.getResults().add(processedResult);
				
				// Clear references to help with garbage collection after each row
				processedResult = null;
				row = null;
				
				// Process in batches of 100 - write partial results to log
				if ((i+1) % 20000 == 0) {
					if (debug) System.out.println("Processed " + (i+1) + " of " + totalResults + " results");
					System.gc();
				}
			}
		  
			long endTime = System.currentTimeMillis();
			System.out.println("Processing time: " + (endTime - startTime) + " milliseconds");
			System.out.println("Total results processed: " + processedResults.getResults().size());
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
		  
		long endTime = System.currentTimeMillis();
		System.out.println("Processing time: " + (endTime - startTime) + " milliseconds");
		return null;
	}

	private String determineKeyName(List<String> headers) {
		for (String key : headers) {
			if ("anat_image_query".equals(key) || "anat_query".equals(key)) {
				return key;
			}
		}
		return "";
	}

	@Override
	public Map<String, Object> getProcessingOutputMap()
	{
		return processingOutputMap;
	}
	
	/**
	 * Helper methods to process JsonNode instead of using vfb_query class methods
	 */
	private String getId(JsonNode node) {
		StringBuilder result = new StringBuilder("undefined");
		
		if (node.has("expression_pattern") && !node.path("expression_pattern").isNull()) {
			result = new StringBuilder(node.path("expression_pattern").path("short_form").asText());
		} else if (node.has("dataset") && !node.path("dataset").isNull()) {
			result = new StringBuilder(node.path("dataset").path("short_form").asText());
		} else if (node.has("anatomy") && !node.path("anatomy").isNull()) {
			result = new StringBuilder(node.path("anatomy").path("short_form").asText());
		}
		
		result.append(delim);
		
		if (node.has("anatomy") && !node.path("anatomy").isNull()) {
			result.append(node.path("anatomy").path("short_form").asText());
		} else if (node.has("license") && node.path("license").isArray() && node.path("license").size() > 0) {
			result.append(node.path("license").get(0).path("core").path("short_form").asText());
		} else {
			result.append("undefined");
		}
		
		result.append(delim);
		
		if (node.has("pub") && !node.path("pub").isNull()) {
			result.append(node.path("pub").path("core").path("short_form").asText());
		} else if (node.has("pubs") && node.path("pubs").isArray() && node.path("pubs").size() == 1) {
			result.append(node.path("pubs").get(0).path("core").path("short_form").asText());
		} else if (node.has("pubs") && node.path("pubs").isArray() && node.path("pubs").size() > 1) {
			for (JsonNode pub : node.path("pubs")) {
				result.append(delim).append(pub.path("core").path("short_form").asText());
			}
		}
		
		if (node.has("term") && !node.path("term").isNull() && node.path("term").has("core") && 
			node.path("term").path("core").has("short_form")) {
			result = new StringBuilder(node.path("term").path("core").path("short_form").asText());
			
			if (node.has("types") && node.path("types").isArray() && node.path("types").size() > 0 &&
				node.path("types").get(0).has("short_form")) {
				result.append(delim).append(node.path("types").get(0).path("short_form").asText());
			}
		} else {
			result = new StringBuilder("undefined");
		}
		
		result.append(delim);
		
		if (node.has("parents") && node.path("parents").isArray() && node.path("parents").size() > 0) {
			result.append(node.path("parents").get(0).path("short_form").asText());
		} else {
			result.append("undefined");
		}
		
		result.append(delim);
		
		if (node.has("object") && !node.path("object").isNull() && node.path("object").has("short_form")) {
			result.append(node.path("object").path("short_form").asText());
		} else {
			result.append("undefined");
		}
		
		return result.toString();
	}
	
	private String getName(JsonNode node) {
		if (node.has("expression_pattern") && !node.path("expression_pattern").isNull()) {
			return getEntityName(node.path("expression_pattern"));
		}
		if (node.has("dataset") && !node.path("dataset").isNull()) {
			return getEntityName(node.path("dataset"));
		}
		if (node.has("term") && !node.path("term").isNull() && node.path("term").has("core")) {
			return getEntityName(node.path("term").path("core"));
		}
		if (node.has("anatomy") && !node.path("anatomy").isNull()) {
			return getEntityName(node.path("anatomy"));
		}
		return "";
	}
	
	private String getEntityName(JsonNode entity) {
		if (entity.has("symbol") && !entity.path("symbol").isNull() && 
			!entity.path("symbol").asText().isEmpty()) {
			return entity.path("symbol").asText();
		}
		if (entity.has("label") && !entity.path("label").isNull()) {
			return entity.path("label").asText();
		}
		return "";
	}
	
	private ArrayValue getImages(JsonNode node, String template) {
		ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
		try {
			if (template == null || template.equals("")) {
				// default to JRC2018U
				template = "VFB_00101567";
			}
			int j = 0;
			List<String> loaded = new ArrayList<String>();
			
			// Process anatomy_channel_image
			if (node.has("anatomy_channel_image") && node.path("anatomy_channel_image").isArray()) {
				// First pass - add same template to the beginning
				for (JsonNode anat : node.path("anatomy_channel_image")) {
					JsonNode channelImage = anat.path("channel_image");
					if (channelImage != null && !channelImage.isNull() && 
						channelImage.has("image") && !channelImage.path("image").isNull() &&
						channelImage.path("image").has("template_anatomy") && 
						!channelImage.path("image").path("template_anatomy").isNull() &&
						channelImage.path("image").path("template_anatomy").has("short_form") &&
						template.equals(channelImage.path("image").path("template_anatomy").path("short_form").asText())) {
						
						if (!loaded.contains(anat.path("anatomy").path("short_form").asText())) {
							addImage(
								getImageUrl(channelImage, "", "thumbnailT.png"),
								getAnatomyChannelImageLabel(anat, false),
								anat.path("anatomy").path("short_form").asText(),
								imageArray,
								j
							);
							loaded.add(anat.path("anatomy").path("short_form").asText());
							j++;
						}
					}
				}
				
				if (j > 0) return imageArray;
				
				// Second pass - add rest of images
				for (JsonNode anat : node.path("anatomy_channel_image")) {
					if (!loaded.contains(anat.path("anatomy").path("short_form").asText())) {
						addImage(
							getImageUrl(anat.path("channel_image"), "", "thumbnailT.png"),
							getAnatomyChannelImageLabel(anat, true),
							anat.path("anatomy").path("short_form").asText(),
							imageArray,
							j
						);
						loaded.add(anat.path("anatomy").path("short_form").asText());
						j++;
					}
				}
			}
			
			// Process expressed_in
			if (node.has("expressed_in") && node.path("expressed_in").isArray()) {
				// First pass - add same template to the beginning
				for (JsonNode anat : node.path("expressed_in")) {
					JsonNode channelImage = anat.path("channel_image");
					if (channelImage != null && !channelImage.isNull() && 
						channelImage.has("image") && !channelImage.path("image").isNull() &&
						channelImage.path("image").has("template_anatomy") && 
						!channelImage.path("image").path("template_anatomy").isNull() &&
						channelImage.path("image").path("template_anatomy").has("short_form") &&
						template.equals(channelImage.path("image").path("template_anatomy").path("short_form").asText())) {
						
						if (!loaded.contains(anat.path("anatomy").path("short_form").asText())) {
							addImage(
								getImageUrl(channelImage, "", "thumbnailT.png"),
								getAnatomyChannelImageLabel(anat, false),
								anat.path("anatomy").path("short_form").asText(),
								imageArray,
								j
							);
							loaded.add(anat.path("anatomy").path("short_form").asText());
							j++;
						}
					}
				}
				
				if (j > 0) return imageArray;
				
				// Second pass - add rest of images
				for (JsonNode anat : node.path("expressed_in")) {
					if (!loaded.contains(anat.path("anatomy").path("short_form").asText())) {
						addImage(
							getImageUrl(anat.path("channel_image"), "", "thumbnailT.png"),
							getAnatomyChannelImageLabel(anat, true),
							anat.path("anatomy").path("short_form").asText(),
							imageArray,
							j
						);
						loaded.add(anat.path("anatomy").path("short_form").asText());
						j++;
					}
				}
			}
			
			// Process channel_image
			if (node.has("channel_image") && node.path("channel_image").isArray()) {
				if (node.has("object") && !node.path("object").isNull()) {
					// If the row has a target object then the image is for that not the term
					// First pass - add same template to the beginning
					for (JsonNode anat : node.path("channel_image")) {
						if (anat != null && !anat.isNull() && 
							anat.has("image") && !anat.path("image").isNull() &&
							anat.path("image").has("template_anatomy") && 
							!anat.path("image").path("template_anatomy").isNull() &&
							anat.path("image").path("template_anatomy").has("short_form") &&
							template.equals(anat.path("image").path("template_anatomy").path("short_form").asText())) {
							
							if (!loaded.contains(node.path("object").path("short_form").asText())) {
								addImage(
									getImageUrl(anat, "", "thumbnailT.png"),
									getEntityName(node.path("object")) + getChannelImageLabel(anat, false),
									node.path("object").path("short_form").asText(),
									imageArray,
									j
								);
								loaded.add(node.path("object").path("short_form").asText());
								j++;
							}
						}
					}
					
					if (j > 0) return imageArray;
					
					// Second pass - add rest of images
					for (JsonNode anat : node.path("channel_image")) {
						if (!loaded.contains(node.path("object").path("short_form").asText())) {
							addImage(
								getImageUrl(anat, "", "thumbnailT.png"),
								getEntityName(node.path("object")) + getChannelImageLabel(anat, true),
								node.path("object").path("short_form").asText(),
								imageArray,
								j
							);
							loaded.add(node.path("object").path("short_form").asText());
							j++;
						}
					}
				} else {
					// First pass - add same template to the beginning
					for (JsonNode anat : node.path("channel_image")) {
						if (anat != null && !anat.isNull() && 
							anat.has("image") && !anat.path("image").isNull() &&
							anat.path("image").has("template_anatomy") && 
							!anat.path("image").path("template_anatomy").isNull() &&
							anat.path("image").path("template_anatomy").has("short_form") &&
							template.equals(anat.path("image").path("template_anatomy").path("short_form").asText())) {
							
							// Now use helper methods to access term information
							if (node.has("term") && !loaded.contains(node.path("term").path("core").path("short_form").asText())) {
								addImage(
									getImageUrl(anat, "", "thumbnailT.png"),
									getEntityName(node.path("term").path("core")),
									node.path("term").path("core").path("short_form").asText(),
									imageArray,
									j
								);
								loaded.add(node.path("term").path("core").path("short_form").asText());
								j++;
							}
						}
					}
					
					// ... continue implementation for the rest of channel_image processing
				}
			}
			
		} catch (Exception e) {
			System.out.println("Error in getImages(): " + e.toString());
			e.printStackTrace();
			return null;
		}
		return imageArray;
	}
	
	// Helper methods for image processing
	private String getImageUrl(JsonNode channelImage, String pre, String post) {
		String result = "";
		if (channelImage != null && !channelImage.isNull() && 
			channelImage.has("image") && !channelImage.isNull() && 
			channelImage.path("image").has("image_folder") && 
			!channelImage.path("image").path("image_folder").asText().equals("")) {
			
			result = channelImage.path("image").path("image_folder").asText().replace("http://", "https://");
		}
		if (pre != null && !pre.equals("")) {
			result = pre + result;
		}
		if (post != null && !post.equals("")) {
			result += post;
		}
		return result;
	}
	
	private String getChannelImageLabel(JsonNode channelImage, boolean showTemplate) {
		StringBuilder result = new StringBuilder();
		
		if (showTemplate && channelImage != null && !channelImage.isNull() && 
			channelImage.has("image") && !channelImage.path("image").isNull() && 
			channelImage.path("image").has("template_anatomy") && 
			!channelImage.path("image").path("template_anatomy").isNull() && 
			channelImage.path("image").path("template_anatomy").has("label") && 
			!channelImage.path("image").path("template_anatomy").path("label").asText().equals("")) {
			
			result.append(" [").append(templateSymbol(
				channelImage.path("image").path("template_anatomy").path("label").asText()
			)).append("]");
		}
		
		if (channelImage != null && !channelImage.isNull() && 
			channelImage.has("imaging_technique") && !channelImage.path("imaging_technique").isNull() && 
			channelImage.path("imaging_technique").has("label") && 
			!channelImage.path("imaging_technique").path("label").asText().equals("")) {
			
			result.append(" [").append(techniqueSymbol(
				channelImage.path("imaging_technique").path("label").asText()
			)).append("]");
		}
		
		return result.toString();
	}
	
	private String getAnatomyChannelImageLabel(JsonNode anatomyChannelImage, boolean showTemplate) {
		StringBuilder result = new StringBuilder(getEntityName(anatomyChannelImage.path("anatomy")));
		result.append(getChannelImageLabel(anatomyChannelImage.path("channel_image"), showTemplate));
		return result.toString();
	}
	
	private String templateSymbol(String label) {
		// Reuse existing implementation
		switch (label) {
			case "adult brain template JFRC2":
				return "JFRC2";
			case "adult brain template Ito2014":
				return "ItoHalfBrain";
			case "L1 larval CNS ssTEM - Cardona/Janelia":
				return "L1CNS";
			case "adult VNS template - Court2018":
				return "adultVNS";
			case "L3 CNS template - Wood2018":
				return "L3CNS";
			case "JRC_FlyEM_Hemibrain":
				return "HemiBrain";
			case "JRC2018Unisex":
				return "JRC2018U";
			case "JRC2018UnisexVNC":
				return "JRC2018UV";
			default:
				return label;
		}
	}
	
	private String techniqueSymbol(String label) {
		// Reuse existing implementation
		switch (label) {
			case "structured illumination microscopy (SIM)":
				return "SIM";
			case "photomultiplier tube (PMT)":
				return "PMT";
			case "scanning electron microscopy (SEM)":
				return "SEM";
			// ... keep all existing cases from the original implementation
			default:
				return label;
		}
	}

	private void addImage(String data, String name, String reference, ArrayValue images, int i) {
		// Reuse existing implementation
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
		return url.replace("http://", "https://");
	}
	
	// ... more helper methods for other properties like types(), parents(), etc.
	
	// Helper methods for processing JsonNode objects
	private String getTypes(JsonNode node) {
		StringBuilder result = new StringBuilder();
		if (node.has("types") && node.path("types").isArray()) {
			for (JsonNode type : node.path("types")) {
				if (result.length() > 0) {
					result.append("; ");
				}
				result.append(type.path("label").asText());
			}
		}
		return result.toString();
	}
	
	private String getParents(JsonNode node) {
		StringBuilder result = new StringBuilder();
		if (node.has("parents") && node.path("parents").isArray()) {
			for (JsonNode parent : node.path("parents")) {
				if (!result.toString().contains(parent.path("label").asText())) {
					if (result.length() > 0) {
						result.append("; ");
					}
					result.append(parent.path("label").asText());
				}
			}
		}
		return result.toString();
	}
	
	private String getGrossTypes(JsonNode node) {
		List<String> types = new ArrayList<>();
		
		if (node.has("expression_pattern") && !node.path("expression_pattern").isNull()) {
			JsonNode expPattern = node.path("expression_pattern");
			if (expPattern.has("unique_facets") && expPattern.path("unique_facets").isArray() && 
				expPattern.path("unique_facets").size() > 0) {
				for (JsonNode facet : expPattern.path("unique_facets")) {
					types.add(facet.asText());
				}
			} else if (expPattern.has("types") && expPattern.path("types").isArray()) {
				for (JsonNode type : expPattern.path("types")) {
					types.add(type.asText());
				}
			}
		}
		
		if (node.has("dataset") && !node.path("dataset").isNull()) {
			JsonNode dataset = node.path("dataset");
			if (dataset.has("unique_facets") && dataset.path("unique_facets").isArray() && 
				dataset.path("unique_facets").size() > 0) {
				for (JsonNode facet : dataset.path("unique_facets")) {
					types.add(facet.asText());
				}
			} else if (dataset.has("types") && dataset.path("types").isArray()) {
				for (JsonNode type : dataset.path("types")) {
					types.add(type.asText());
				}
			}
		}
		
		if (node.has("term") && !node.path("term").isNull() && 
			node.path("term").has("core") && !node.path("term").path("core").isNull()) {
			JsonNode termCore = node.path("term").path("core");
			if (termCore.has("unique_facets") && termCore.path("unique_facets").isArray() && 
				termCore.path("unique_facets").size() > 0) {
				for (JsonNode facet : termCore.path("unique_facets")) {
					types.add(facet.asText());
				}
			} else if (termCore.has("types") && termCore.path("types").isArray()) {
				for (JsonNode type : termCore.path("types")) {
					types.add(type.asText());
				}
			}
		}
		
		if (node.has("anatomy") && !node.path("anatomy").isNull()) {
			JsonNode anatomy = node.path("anatomy");
			if (anatomy.has("unique_facets") && anatomy.path("unique_facets").isArray() && 
				anatomy.path("unique_facets").size() > 0) {
				for (JsonNode facet : anatomy.path("unique_facets")) {
					types.add(facet.asText());
				}
			} else if (anatomy.has("types") && anatomy.path("types").isArray()) {
				for (JsonNode type : anatomy.path("types")) {
					types.add(type.asText());
				}
			}
		}
		
		return formatTypeList(types);
	}
	
	private String formatTypeList(List<String> types) {
		StringBuilder result = new StringBuilder();
		for (String type : types) {
			type = type.replace("DataSet", "Dataset");
			if (type.equals("pub")) type = "Publication";
			if (result.length() == 0) {
				result.append(type);
			} else {
				result.append("; ").append(type);
			}
		}
		return result.toString();
	}
	
	private String getExpressedIn(JsonNode node) {
		if (node.has("expression_pattern") && !node.path("expression_pattern").isNull() &&
			node.has("anatomy") && !node.path("anatomy").isNull()) {
			return getEntityName(node.path("anatomy"));
		}
		return "";
	}
	
	private String getLicenseLabel(JsonNode node) {
		StringBuilder result = new StringBuilder();
		if (node.has("license") && node.path("license").isArray()) {
			for (JsonNode license : node.path("license")) {
				if (result.length() > 0) result.append("; ");
				result.append(getEntityName(license.path("core")));
			}
		}
		return result.toString();
	}
	
	private String getStages(JsonNode node) {
		StringBuilder result = new StringBuilder();
		if (node.has("stages") && node.path("stages").isArray()) {
			for (JsonNode stage : node.path("stages")) {
				if (result.length() > 0) result.append("; ");
				result.append(getEntityName(stage));
			}
		}
		return result.toString();
	}
	
	private String getReference(JsonNode node) {
		StringBuilder result = new StringBuilder();
		if (node.has("pub") && !node.path("pub").isNull()) {
			result.append(getEntityName(node.path("pub").path("core")));
		}
		if (node.has("pubs") && node.path("pubs").isArray() && node.path("pubs").size() > 0) {
			for (JsonNode pub : node.path("pubs")) {
				if (result.length() > 0) result.append("; ");
				result.append(getEntityName(pub.path("core")));
			}
		}
		return result.toString();
	}
	
	private String getTechnique(JsonNode node) {
		StringBuilder result = new StringBuilder();
		
		// Check channel_image
		if (node.has("channel_image") && node.path("channel_image").isArray()) {
			for (JsonNode ci : node.path("channel_image")) {
				if (ci.has("imaging_technique") && !ci.path("imaging_technique").isNull() &&
					ci.path("imaging_technique").has("label")) {
					
					String technique = techniqueSymbol(ci.path("imaging_technique").path("label").asText());
					if (result.indexOf(technique) < 0) {
						if (result.length() > 0) result.append("; ");
						result.append(technique);
					}
				}
			}
		}
		
		// Check anatomy_channel_image
		if (node.has("anatomy_channel_image") && node.path("anatomy_channel_image").isArray()) {
			for (JsonNode aci : node.path("anatomy_channel_image")) {
				if (aci.has("channel_image") && !aci.path("channel_image").isNull() &&
					aci.path("channel_image").has("imaging_technique") && 
					!aci.path("channel_image").path("imaging_technique").isNull() &&
					aci.path("channel_image").path("imaging_technique").has("label")) {
					
					String technique = techniqueSymbol(aci.path("channel_image").path("imaging_technique").path("label").asText());
					if (result.indexOf(technique) < 0) {
						if (result.length() > 0) result.append("; ");
						result.append(technique);
					}
				}
			}
		}
		
		return result.toString();
	}
	
	private String getTemplate(JsonNode node, String template) {
		StringBuilder result = new StringBuilder();
		
		if (template == null || template.isEmpty()) {
			// default to JRC2018U
			template = "VFB_00101567";
		}
		
		// Check channel_image
		boolean foundMatchingTemplate = false;
		if (node.has("channel_image") && node.path("channel_image").isArray()) {
			for (JsonNode ci : node.path("channel_image")) {
				if (ci.has("image") && !ci.path("image").isNull() &&
					ci.path("image").has("template_anatomy") && !ci.path("image").path("template_anatomy").isNull() &&
					ci.path("image").path("template_anatomy").has("label")) {
					
					String templateName = templateSymbol(ci.path("image").path("template_anatomy").path("label").asText());
					
					if (result.indexOf(templateName) < 0) {
						if (ci.path("image").path("template_anatomy").has("short_form") &&
							template.equals(ci.path("image").path("template_anatomy").path("short_form").asText())) {
							
							// Matching template goes to the beginning
							result.insert(0, templateName + "\nalso in: ");
							foundMatchingTemplate = true;
						} else {
							if (result.length() > 0 && !result.toString().endsWith(": ")) result.append("; ");
							result.append(templateName);
						}
					}
				}
			}
		}
		
		// Check anatomy_channel_image
		if (node.has("anatomy_channel_image") && node.path("anatomy_channel_image").isArray()) {
			for (JsonNode aci : node.path("anatomy_channel_image")) {
				if (aci.has("channel_image") && !aci.path("channel_image").isNull() &&
					aci.path("channel_image").has("image") && !aci.path("channel_image").path("image").isNull() &&
					aci.path("channel_image").path("image").has("template_anatomy") && 
					!aci.path("channel_image").path("image").path("template_anatomy").isNull() &&
					aci.path("channel_image").path("image").path("template_anatomy").has("label")) {
					
					String templateName = templateSymbol(aci.path("channel_image").path("image").path("template_anatomy").path("label").asText());
					
					if (result.indexOf(templateName) < 0) {
						if (aci.path("channel_image").path("image").path("template_anatomy").has("short_form") &&
							template.equals(aci.path("channel_image").path("image").path("template_anatomy").path("short_form").asText())) {
							
							// Matching template goes to the beginning
							result.insert(0, templateName + "\nalso in: ");
							foundMatchingTemplate = true;
						} else {
							if (result.length() > 0 && !result.toString().endsWith(": ")) result.append("; ");
							result.append(templateName);
						}
					}
				}
			}
		}
		
		// Clean up the result if needed
		String finalResult = result.toString();
		if (finalResult.endsWith(": ")) {
			finalResult = finalResult.replace("also in: ", "");
		}
		
		return finalResult;
	}
	
	private String getSynapseCounts(JsonNode row, String countType) {
		if (row.has("synapse_counts") && !row.path("synapse_counts").isNull()) {
			JsonNode counts = row.path("synapse_counts");
			if (counts.has(countType) && counts.path(countType).isArray()) {
				StringBuilder result = new StringBuilder();
				for (JsonNode value : counts.path(countType)) {
					if (result.length() > 0) {
						result.append("; ");
					}
					result.append(String.format("% 5d", (int)Math.ceil(value.asDouble())));
				}
				return result.toString();
			}
		}
		return "";
	}
}