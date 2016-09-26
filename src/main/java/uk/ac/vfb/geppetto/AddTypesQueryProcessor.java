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

import java.util.List;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.GeppettoPackage;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.values.HTML;
import org.geppetto.model.values.Text;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;

/**
 * @author matteocantarelli
 *
 */
public class AddTypesQueryProcessor extends AQueryProcessor
{

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.geppetto.core.datasources.IQueryProcessor#process(org.geppetto.model.ProcessQuery, org.geppetto.model.variables.Variable, org.geppetto.model.QueryResults)
	 */
	@Override
	public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException
	{

		System.out.println("Creating Metadata for " + variable.getId() + "...");

		geppettoModelAccess.setObjectAttribute(variable, GeppettoPackage.Literals.NODE__NAME, results.getValue("name", 0));
		CompositeType type = TypesFactory.eINSTANCE.createCompositeType();
		type.setId(variable.getId());
		variable.getAnonymousTypes().add(type);

		// add supertypes
		boolean template = false;
		List<GeppettoLibrary> dependenciesLibrary = dataSource.getDependenciesLibrary();
		if(results.getValue("supertypes", 0) != null)
		{
			List<String> supertypes = (List<String>) results.getValue("supertypes", 0);

			for(String supertype : supertypes)
			{
				if(!supertype.startsWith("_"))
				{ // ignore supertypes starting with _
					type.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
					System.out.println("Adding to SuperType: " + supertype);
				}
				if(supertype.equals("Template"))
				{
					template = true;
				}
			}
		}
		else
		{
			type.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType("Orphan", dependenciesLibrary));
		}

		// Extract initial metadata

		// Check if Variable already exists
		// TODO check if existing and get or ...

		// Create new Variable
		Variable metaDataVar = VariablesFactory.eINSTANCE.createVariable();
		metaDataVar.setId("metaDataVar");
		CompositeType metaData = TypesFactory.eINSTANCE.createCompositeType();
		metaDataVar.getTypes().add(metaData);
		metaDataVar.setId(variable.getId() + "_meta");
		metaData.setId(variable.getId() + "_metadata");
		metaData.setName("Info");
		metaDataVar.setName(variable.getName());

		try
		{
			Type textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
			Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);

			// set meta label/name:
			Variable label = VariablesFactory.eINSTANCE.createVariable();
			label.setId("label");
			label.setName("Name");
			label.getTypes().add(htmlType);
			metaData.getVariables().add(label);
			HTML labelValue = ValuesFactory.eINSTANCE.createHTML();
			String labelLink = "";
			if(results.getValue("name", 0) != null)
			{
				labelLink = "<a href=\"#\" instancepath=\"" + (String) variable.getId() + "\">" + (String) results.getValue("name", 0) + "</a>";
			}
			else
			{
				labelLink = "<a href=\"#\" instancepath=\"" + (String) variable.getId() + "\">" + (String) variable.getName() + "</a>";
			}
			labelLink = "<h2>" + labelLink + "</h2>";
			labelValue.setHtml(labelLink);

			htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			label.getInitialValues().put(htmlType, labelValue);

			// set meta id:
			Variable metaID = VariablesFactory.eINSTANCE.createVariable();
			metaID.setId("id");
			metaID.setName("ID");
			metaID.getTypes().add(htmlType);
			metaData.getVariables().add(metaID);
			HTML metaIdValue = ValuesFactory.eINSTANCE.createHTML();
			String idLink = "<a href=\"#\" instancepath=\"" + (String) variable.getId() + "\">" + (String) variable.getId() + "</a>";
			metaIdValue.setHtml(idLink);

			htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			metaID.getInitialValues().put(htmlType, metaIdValue);

			// set description:
			if(results.getValue("description", 0) != null)
			{
				Variable description = VariablesFactory.eINSTANCE.createVariable();
				description.setId("description");
				description.setName("Description");
				description.getTypes().add(textType);
				metaData.getVariables().add(description);
				Text descriptionValue = ValuesFactory.eINSTANCE.createText();
				String desc = ((List<String>) results.getValue("description", 0)).get(0);
				desc = highlightLinks(desc);
				descriptionValue.setText(desc);
				description.getInitialValues().put(textType, descriptionValue);
			}

			// set comment:
			if(results.getValue("comment", 0) != null)
			{
				Variable comment = VariablesFactory.eINSTANCE.createVariable();
				comment.setId("comment");
				comment.setName("Notes");
				comment.getTypes().add(textType);
				metaData.getVariables().add(comment);
				Text commentValue = ValuesFactory.eINSTANCE.createText();
				commentValue.setText(highlightLinks(((List<String>) results.getValue("comment", 0)).get(0)));
				comment.getInitialValues().put(textType, commentValue);
			}

			type.getVariables().add(metaDataVar);
			geppettoModelAccess.addTypeToLibrary(metaData, dataSource.getTargetLibrary());

		}
		catch(GeppettoVisitingException e)
		{
			throw new GeppettoDataSourceException(e);
		}

		return results;
	}

	/**
	 * @param text
	 */
	private String highlightLinks(String text)
	{
		try
		{
			text = text.replaceAll("([F,V,G].*)[:,_]([0-9]*)","<a href=\"#\" instancepath=\"$1_$2\">$1_$2</a>");
			return text;
		}
		catch(Exception e)
		{
			System.out.println("Error highlighting links in (" + text + ") " + e.toString());
			return text;
		}
	}

}
