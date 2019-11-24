
package uk.ac.vfb.geppetto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		String label;
		private List<String> types;
	}

	class minimal_edge_info {
		private String short_form;
		private String iri;
		private String label;
		private String type;
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

	class dataset {
		public minimal_entity_info core;
		private String link;
		private String icon;

	}

	class license {
		public minimal_entity_info core;
		private String link;
		private String icon;
		private boolean is_bespoke;
	}

	class dataset_license {
		dataset dataset;
		license license;
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

	class vfb_query {
		private minimal_entity_info anatomy;
		public String query;
		public String version;
		private List<anatomy_channel_image> anatomy_channel_image;
		private List<pub> pubs;
		private pub pub;
		private dataset dataset;
		private dataset_counts dataset_counts;
		private List<license> license;
		private List<minimal_entity_info> stages;
		private minimal_entity_info expression_pattern;
		private List<anatomy_channel_image> expressed_in;
	
		public String id(){
			String delim = "----";
			String mainID = "";
			if (this.expression_pattern != null) mainID = this.expression_pattern.short_form;
			if (this.dataset != null) mainID = this.dataset.core.short_form;
			if (this.anatomy != null) mainID += delim + this.anatomy.short_form;
			if (this.anatomy != null && this.pub != null) return mainID + delim + this.pub.core.short_form;
			if (this.anatomy != null && this.pubs != null && this.pubs.size() == 1) return mainID + delim + this.pubs.get(0).core.short_form;
			if (this.anatomy != null && this.pubs != null && this.pubs.size() > 1) {
				String result = mainID;
				for (pub pub:this.pubs){
					result += delim + pub.core.short_form;
				}
				return result;
			}
			if (this.expression_pattern != null) return mainID;
			return this.anatomy.short_form;
		}

		public String name(){
			if (this.expression_pattern != null) return this.expression_pattern.label;
			if (this.dataset != null) return this.dataset.core.label;
			return this.anatomy.label;
		}

		public String expressed_in(){
			if (this.expression_pattern != null) return this.anatomy.label;
			return "";
		}

		public String licenseLabel(){
			if (this.license != null) return this.license.core.label;
			return "";
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

		public ArrayValue images() {
			return this.images("");
		}

		public ArrayValue images(String template) {
			ArrayValue imageArray = ValuesFactory.eINSTANCE.createArrayValue();
			try{
				if (template == null || template.equals("")){
					//default to JFRC2 
					template = "VFB_00017894";
				}
				int j = 0;
				int f = 0;
				int c = 0;
				if (this.anatomy_channel_image != null) {
					f = this.anatomy_channel_image.size();
					c = f;
					for (anatomy_channel_image anat : this.anatomy_channel_image) {
						// add same template to the begining and others at the end.
						if (anat.channel_image != null && anat.channel_image.image != null && anat.channel_image.image.template_anatomy != null && anat.channel_image.image.template_anatomy.short_form != null && template.equals(anat.channel_image.image.template_anatomy.short_form)) {
							addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, j);
							j++;
						} else {
							f--;
							addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, f);
						}
					}
				}
				if (this.expressed_in != null) {
					j = c;
					f = c + this.expressed_in.size();
					for (anatomy_channel_image anat : this.expressed_in) {
						// add same template to the begining and others at the end.
						if (anat.channel_image != null && anat.channel_image.image != null && anat.channel_image.image.template_anatomy != null && anat.channel_image.image.template_anatomy.short_form != null && template.equals(anat.channel_image.image.template_anatomy.short_form)) {
							addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, j);
							j++;
						} else {
							f--;
							addImage(anat.getUrl("", "thumbnailT.png"), anat.anatomy.label, anat.anatomy.short_form, imageArray, f);
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
			List<vfb_query> table = new ArrayList<vfb_query>();
			vfb_query vfbQuery = null;

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
								break;
							case "expression_pattern":
								hasId = true;
								hasName = true;
								hasExpressed_in=true;
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
								break;
							case "expressed_in":
								hasImage = true;
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
			if (hasExpressed_in) processedResults.getHeader().add("Expressed_in");
			if (hasLicense) processedResults.getHeader().add("License");
			if (hasReference) processedResults.getHeader().add("Reference");
			if (hasStage) processedResults.getHeader().add("Stage");
			if (hasImage) processedResults.getHeader().add("Images");
			if (!hasImages && hasDatasetCount) processedResults.getHeader().add("Images");

			for (vfb_query row:table){
				try{
					SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
					if (hasId) processedResult.getValues().add(row.id());
					if (hasName) processedResult.getValues().add(row.name());
					if (hasExpressed_in) processedResult.getValues().add(row.expressed_in());
					if (hasLicense) processedResult.getValues().add(row.licenseLabel());
					if (hasReference) processedResult.getValues().add(row.reference());
					if (hasStage) processedResult.getValues().add(row.stages());
					if (hasImage){
						Variable exampleVar = VariablesFactory.eINSTANCE.createVariable();
						exampleVar.setId("images");
						exampleVar.setName("Images");
						exampleVar.getTypes().add(imageType);
						ArrayValue images = row.images();
						if (!images.getElements().isEmpty())
						{
							if (images.getElements().size() > 1){
								exampleVar.getInitialValues().put(imageType, images);
							}else{
								exampleVar.getInitialValues().put(imageType, images.getElements().get(0).getInitialValue());
							}
							processedResult.getValues().add(GeppettoSerializer.serializeToJSON(exampleVar));
							if (debug) System.out.println("DEBUG: Image: " + GeppettoSerializer.serializeToJSON(exampleVar) );
						}
						else
						{
							processedResult.getValues().add("");
						}
					}
					if (!hasImages && hasDatasetCount) processedResult.getValues().add(row.dataset_counts.images);
					processedResults.getResults().add(processedResult);
				}catch (Exception e) {
					System.out.println("Error creating results row: " + count.toString() + " - " + e.toString());
					e.printStackTrace();				
				}
				count ++;
			}
			System.out.println("NEO4JQueryProcessor returning " + count.toString() + " rows");

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
