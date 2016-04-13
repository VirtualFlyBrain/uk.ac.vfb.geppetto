/*******************************************************************************
 * The MIT License (MIT)
 * 
 * Copyright (c) 2011 - 2015 OpenWorm.
 * http://openworm.org
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the MIT License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/MIT
 *
 * Contributors:
 *     	OpenWorm - http://openworm.org/people.html
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights 
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell 
 * copies of the Software, and to permit persons to whom the Software is 
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 * 
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

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.datasources.IQueryProcessor;
import org.geppetto.core.features.IFeature;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.services.GeppettoFeature;
import org.geppetto.core.services.registry.ServicesRegistry;
import org.geppetto.model.DataSource;
import org.geppetto.model.ProcessQuery;
import org.geppetto.model.QueryResults;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.util.ModelUtility;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;

/**
 * @author matteocantarelli
 *
 */
public class AddImportTypesQueryProcessor implements IQueryProcessor
{

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

			System.out.println("Processing Examples...");
			
			if (results.getValue("exId", 0) != null) {
			
				String tempId = "";
				String tempThumb = "";
				String tempName = "";
	
				int i = 0;
				Variable exampleVar = VariablesFactory.eINSTANCE.createVariable();
				exampleVar.setId("examples");
				exampleVar.setName("Examples");
				exampleVar.getTypes().add(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE));
				geppettoModelAccess.addVariableToType(exampleVar, metadataType);
				ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();
				while(results.getValue("exId", i) != null)
				{
					tempId = (String) results.getValue("exId", i);
					tempThumb = "http://www.virtualflybrain.org/data/VFB/i/" + tempId.substring(4, 8) + "/" + tempId.substring(8) + "/thumbnail.png";
					tempName = (String) results.getValue("exName", i);
					System.out.println("Adding Example Image: " + tempId + " " + tempName + " " + tempThumb);
					if (checkURL(tempThumb)){
						addImage(tempThumb, tempName, tempId, images, i);
						i++;
					}
				}
				exampleVar.getInitialValues().put(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE), images);
			}	
		}
		catch(GeppettoVisitingException e)
		{
			throw new GeppettoDataSourceException(e);
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
	private boolean checkURL(String urlString){
		try{
			URL url = new URL(urlString);
			HttpURLConnection huc =  (HttpURLConnection)  url.openConnection();
			huc.setRequestMethod("HEAD");
			huc.setInstanceFollowRedirects(false);
			return (huc.getResponseCode() == HttpURLConnection.HTTP_OK);
		}catch(Exception e){
			System.out.println("Error checking url (" + urlString + ") " + e.toString());
			return false;
		}
	}
	
	@Override
	public void registerGeppettoService() throws Exception
	{
		ServicesRegistry.registerQueryProcessorService(this);
	}

	@Override
	public boolean isSupported(GeppettoFeature feature)
	{
		return false;
	}

	@Override
	public IFeature getFeature(GeppettoFeature feature)
	{
		return null;
	}

	@Override
	public void addFeature(IFeature feature)
	{

	}

}
