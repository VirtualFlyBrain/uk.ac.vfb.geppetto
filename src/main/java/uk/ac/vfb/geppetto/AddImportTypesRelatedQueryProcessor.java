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
import java.util.Map;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.util.ModelUtility;
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.HTML;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;

/**
 * @author robertcourt
 *
 */
public class AddImportTypesRelatedQueryProcessor extends AQueryProcessor
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

			Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);

			System.out.println("Processing Items...");

			// running through returned items - references.
			if(results.getValue("relationship", 0) != null)
			{

				String typeLink = "";
				String relat = "";
				String temp = "";

				int i = 0;
				while(results.getValue("relationship", i) != null)
				{
					if("type".equals((String) ((Map) results.getValue("relationship", i)).get("label")) || "is a".equals((String) ((Map) results.getValue("relationship", i)).get("label")))
					{ // parent type:
						typeLink += "<a href=\"#\" instancepath=\"" + (String) results.getValue("relId", i) + "\">";
						typeLink += (String) results.getValue("relName", i) + "</a>";
						typeLink += "<br/>";
					}
					else
					{
						// relationships:
						if(((Map) results.getValue("relationship", i)).get("label") != null)
						{
							temp = ((String) ((Map) results.getValue("relationship", i)).get("label")).replace("_", " ");
							relat += temp.substring(0, 1).toUpperCase() + temp.substring(1) + " ";
						}
						if(results.getValue("relName", i) != null)
						{
							if(results.getValue("relId", i) != null)
							{
								relat += "<a href=\"#\" instancepath=\"" + (String) results.getValue("relId", i) + "\">" + (String) results.getValue("relName", i) + "</a> ";
							}
							else
							{
								relat += (String) results.getValue("relName", i) + " ";
							}
						}
						if(results.getValue("relFBrf", i) != null)
						{
							relat += "[<a href=\"http://flybase.org/reports/" + (String) results.getValue("relFBrf", i) + "\" target=\"_blank\" >FlyBase:" + (String) results.getValue("relFBrf", i)
									+ "</a>]";
						}
						relat += "<br/>";
					}
					i++;
				}

				// set parent Type:
				if(!"".equals(typeLink))
				{
					System.out.println("Type:\n" + typeLink);
					Variable type = VariablesFactory.eINSTANCE.createVariable();
					type.setId("type");
					type.setName("Type");
					type.getTypes().add(htmlType);
					geppettoModelAccess.addVariableToType(type, metadataType);
					HTML typeValue = ValuesFactory.eINSTANCE.createHTML();
					typeValue.setHtml(typeLink);
					type.getInitialValues().put(htmlType, typeValue);
				}

				// set Relationships with any related references:
				if(!"".equals(relat))
				{
					System.out.println("Relationships:\n" + relat);
					Variable relationships = VariablesFactory.eINSTANCE.createVariable();
					relationships.setId("relationships");
					relationships.setName("Relationships");
					relationships.getTypes().add(htmlType);
					geppettoModelAccess.addVariableToType(relationships, metadataType);
					HTML relationshipsValue = ValuesFactory.eINSTANCE.createHTML();
					relationshipsValue.setHtml(relat);
					relationships.getInitialValues().put(htmlType, relationshipsValue);
				}

				if("<a href=\"#\" instancepath=\"VFB_10000005\">cluster</a><br/>".equals(typeLink))
				{
					i = 0;
					String tempId = "";
					String tempThumb = "";
					String tempName = "";

					int j = 0;
					Variable exampleVar = VariablesFactory.eINSTANCE.createVariable();
					exampleVar.setId("examples");
					exampleVar.setName("Members");
					exampleVar.getTypes().add(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE));
					geppettoModelAccess.addVariableToType(exampleVar, metadataType);
					ArrayValue images = ValuesFactory.eINSTANCE.createArrayValue();
					while(results.getValue("relationship", i) != null)
					{
						if("has_member".equals((String) ((Map) results.getValue("relationship", i)).get("label")))
						{
							if(results.getValue("relName", i) != null)
							{
								tempId = (String) results.getValue("relId", i);
								tempThumb = "http://www.virtualflybrain.org/data/VFB/i/" + tempId.substring(4, 8) + "/" + tempId.substring(8) + "/thumbnail.png";
								tempName = (String) results.getValue("relName", i);
								System.out.println("Adding Cluster Image: " + tempId + " " + tempName + " " + tempThumb);
								if(checkURL(tempThumb))
								{
									addImage(tempThumb, tempName, tempId, images, j);
									j++;
								}
							}
						}
						i++;
					}
					exampleVar.getInitialValues().put(geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE), images);
				}
			}

		}
		catch(GeppettoVisitingException e)
		{
			System.out.println(e);
			throw new GeppettoDataSourceException(e);
		}
		catch(Exception e)
		{
			System.out.println(e);
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
