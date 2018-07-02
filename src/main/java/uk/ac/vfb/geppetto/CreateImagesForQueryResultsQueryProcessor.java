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

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.GeppettoSerializer;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DatasourcesFactory;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.datasources.SerializableQueryResult;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;

/**
 * @author robertcourt
 *
 */
public class CreateImagesForQueryResultsQueryProcessor extends AQueryProcessor
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
			while(results.getValue("inds", i) != null)
			{
				List<Object> currentObjects = (List<Object>) results.getValue("inds", i);
				SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
				String id = (String) results.getValue("class_Id", i);
				processedResult.getValues().add(id);
				String name = null;
				try{
					name = (String) results.getValue("class_Name", i);
				}catch (Exception e){
					System.out.println(e);
					e.printStackTrace();
					System.out.println(results.toString());
				}
				String desc = null;
				try{
					desc = (String) results.getValue("class_Desc", i);
				}catch (Exception e){
					System.out.println(e);
					e.printStackTrace();
					System.out.println(results.toString());
				}
				String type = null;
				try{
					type = cleanType((List<String>) results.getValue("class_Type", i));
				}catch (Exception e){
					System.out.println(e);
					e.printStackTrace();
					System.out.println(results.toString());
				}
				
				if (name != null){
					processedResult.getValues().add(name);
				}else{
					processedResult.getValues().add("");
				}
				if (desc != null){
					processedResult.getValues().add(desc);
				}else{
					processedResult.getValues().add("");
				}
				if (type != null){
					processedResult.getValues().add(type);
				}else{
					processedResult.getValues().add("");
				}

				Variable exampleVar = VariablesFactory.eINSTANCE.createVariable();
				exampleVar.setId("images");
				exampleVar.setName("Images");

				exampleVar.getTypes().add(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE));
				ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();

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
				if(!images.getElements().isEmpty())
				{
					exampleVar.getInitialValues().put(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE), images);
					processedResult.getValues().add(GeppettoSerializer.serializeToJSON(exampleVar));
				}
				else
				{
					processedResult.getValues().add("");
				}

				processedResults.getResults().add(processedResult);

				i++;
			}
			System.out.println("CreateImagesForQueryResultsQueryProcessor returning " + Integer.toString(i) + " rows");
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
