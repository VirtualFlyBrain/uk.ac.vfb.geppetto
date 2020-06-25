/*******************************************************************************
 * The MIT License (MIT)
 * <p>
 * Copyright (c) 2011 - 2015 OpenWorm.
 * http://openworm.org
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the MIT License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/MIT
 * <p>
 * Contributors:
 * OpenWorm - http://openworm.org/people.html
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package uk.ac.vfb.geppetto;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.GeppettoSerializer;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.*;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.values.*;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.util.ModelUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.lang.reflect.Array;

/**
 * @author robertcourt
 *
 */
public class CreateResultListForIndividualsForQueryResultsQueryProcessor extends AQueryProcessor
{
	private int count=0;

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
			int i = 0;
			QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();
			processedResults.getHeader().add("ID");
			processedResults.getHeader().add("Name");
			processedResults.getHeader().add("Definition");
			processedResults.getHeader().add("Type");
			processedResults.getHeader().add("Images");

			Boolean debug=false;

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

			while(results.getValue("id", i) != null)
			{
				SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
				String id = (String) results.getValue("id", i);
				processedResult.getValues().add(id);

				String name = (String) results.getValue("name", i);
				processedResult.getValues().add(name);

				String def = (String) results.getValue("def", i);
				if (def != null && def.length() > 250){
					def = def.substring(0, 250) + "...";
				}
				processedResult.getValues().add(def);
				try{
					String type = cleanType((List<String>) results.getValue("type", i));
					processedResult.getValues().add(type);
				}catch (Exception e){
					System.out.println(e);
					e.printStackTrace();
					processedResult.getValues().add("");
				}
				Variable exampleVar = VariablesFactory.eINSTANCE.createVariable();
				exampleVar.setId("images");
				exampleVar.setName("Images");

				exampleVar.getTypes().add(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE));
				ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();

				//Check is single file or list of individuals
				if (results.getValue("file", i) != null) {
					if ((results.getValue("file", i)).getClass() == ArrayList.class) {
						List<String> files = (List<String>) results.getValue("file", i);
						int j = 0;
						files = files.Sort();
						if (loadedTemplate != "" && files.contains(loadedTemplate)) {
							for (String f : files) {
								if (f.contains(loadedTemplate)) {
									addImage(f, name, id, images, j);
									j++;
								}
							}
						} else {
							for (String f : files) {
								if (!f.contains(loadedTemplate)) {
									// Forcing selected template loasding where 2 options exist:
									// if (f.indexOf("VFB_") > 0) {
									// 	addImage(f, name, f.substring(f.indexOf("VFB_"), (f.indexOf("VFB_") + 12)) + "," + id, images, j);
									// }else{
									// 	addImage(f, name, id, images, j);
									// }
									addImage(f, name, id, images, j);
									j++;
								}
							}
						}
					} else {
						String file = (String) results.getValue("file", i);
						addImage(file, name, id, images, 0);
					}

				}else if (results.getValue("inds", i) != null){
					List<Object> currentObjects = (List<Object>) results.getValue("inds", i);
					for(int j = 0; j < currentObjects.size(); j++)
					{
						Map<String, Object> currentObject = (Map<String, Object>) currentObjects.get(j);
						String tempId = (String) currentObject.get("image_id");
						if(tempId != null)
						{
							String tempThumb = (String) currentObject.get("image_thumb");
							String tempName = (String) currentObject.get("image_name");
							addImage(tempThumb, tempName, tempId, images, j);
						}
					}
				}

				if(!images.getElements().isEmpty())
				{
					exampleVar.getInitialValues().put(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE), images);
					processedResult.getValues().add(GeppettoSerializer.serializeToJSON(exampleVar));
					//System.out.println("DEBUG: Image: " + GeppettoSerializer.serializeToJSON(exampleVar) );
				}
				else
				{
					processedResult.getValues().add("");
				}

				processedResults.getResults().add(processedResult);

				i++;
			}
            System.out.println("CreateResultListForIndividualsForQueryResultsQueryProcessor returning " + Integer.toString(i) + " rows");
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

		return results;
	}

	/**
	 * @param data
	 * @param name
	 * @param images
	 * @param i
	 */
	private void addImage(String data, String name, String reference, ArrayValue images, int i)
	{
//		System.out.println("Adding image "+ count++ + " "+name);
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

	private String cleanType(List<String> types){
		String type="";
		for( int i = 0; i < types.size(); i++)
		{
			if (i>0){
				type+=", ";
			}
			type+=types.get(i);
		}
		return type;
	}

}
