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
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.types.CompositeType;
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
public class AddImportTypesQueryProcessor extends AQueryProcessor
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

			String tempId;
			String tempThumb;
			String tempName;
			String tempSpace;

			int i;
			int j;

			Variable exampleVar = VariablesFactory.eINSTANCE.createVariable();
			exampleVar.setId("examples");
			exampleVar.setName("Examples");
			exampleVar.getTypes().add(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE));
			geppettoModelAccess.addVariableToType(exampleVar, metadataType);
			ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();

			if(results.getValue("exId", 0) != null)
			{

				i = 0;
				j = 0;

				if (variable.getId() == results.getValue("exId", i))
				{
					exampleVar.setName("Painted Anatomy");
				}

				while(results.getValue("exId", i) != null)
				{
					tempSpace = (String) results.getValue("exTemp", i);
					System.out.println("Example for template " + tempSpace + (String) results.getValue("exId", i) + (String) results.getValue("exThumb", i) + (String) results.getValue("exName", i));

					tempId = (String) results.getValue("exId", i);
					tempThumb = (String) results.getValue("exThumb", i);
					tempThumb = "http://www.virtualflybrain.org/data/" + tempThumb;
					tempName = (String) results.getValue("exName", i);
					System.out.println("Adding Example Image: " + tempId + " " + tempName + " " + tempThumb);
					if (checkURL(tempThumb)) {
						addImage(tempThumb, tempName, tempId, images, j);
						j++;
					}
					i++;
				}
				exampleVar.getInitialValues().put(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE), images);
			}
		}
		catch(GeppettoVisitingException e)
		{
			System.out.println(e);
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
			return false;
		}
	}

}
