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
import org.geppetto.core.datasources.IQueryProcessor;
import org.geppetto.core.features.IFeature;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.services.GeppettoFeature;
import org.geppetto.core.services.registry.ServicesRegistry;
import org.geppetto.model.DataSource;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.GeppettoPackage;
import org.geppetto.model.ProcessQuery;
import org.geppetto.model.QueryResults;
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
public class AddTypesQueryProcessor implements IQueryProcessor
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

		List<String> supertypes = (List<String>) results.getValue("supertypes", 0);

		List<GeppettoLibrary> dependenciesLibrary = dataSource.getDependenciesLibrary();

		for(String supertype : supertypes)
		{
			if(!supertype.startsWith("_"))
			{ // ignore supertypes starting with _
				type.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
			}
		}
		
//		Extract initial metadata
		
//		Check if Variable already exists
//		TODO check if existing and get or ...
		
//		Create new Variable
		Variable metaDataVar = VariablesFactory.eINSTANCE.createVariable();
		metaDataVar.setId("metaDataVar");
		CompositeType metaData = TypesFactory.eINSTANCE.createCompositeType();
		metaDataVar.getTypes().add(metaData);
		metaDataVar.setId(variable.getId() + "_meta");
		metaData.setId(variable.getId() + "_metadata");
		metaData.setName("Info");
		metaDataVar.setName("Info");
		
		try {
			Type textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
			Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			
			// set meta id:
			Variable metaID = VariablesFactory.eINSTANCE.createVariable();
			metaID.setId("id");
			metaID.setName("ID");
			metaID.getTypes().add(htmlType);
			metaData.getVariables().add(metaID);
			HTML metaIdValue = ValuesFactory.eINSTANCE.createHTML();
			String idLink = "<a href=\"#\" instancepath=\"" + (String) results.getValue("id", 0) + "\">" + (String) results.getValue("id", 0) + "</a>";
			metaIdValue.setHtml(idLink);

			htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			metaID.getInitialValues().put(htmlType, metaIdValue);

			// set meta label/name:
			Variable label = VariablesFactory.eINSTANCE.createVariable();
			label.setId("label");
			label.setName("Name");
			label.getTypes().add(htmlType);
			metaData.getVariables().add(label);
			HTML labelValue = ValuesFactory.eINSTANCE.createHTML();
			String labelLink = "<a href=\"#\" instancepath=\"" + (String) results.getValue("id", 0) + "\">" + (String) results.getValue("name", 0) + "</a>";
			labelValue.setHtml(labelLink);

			htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			label.getInitialValues().put(htmlType, labelValue);
			
			// set description:
			if(results.getValue("description", 0) != null)
			{
				Variable description = VariablesFactory.eINSTANCE.createVariable();
				description.setId("description");
				description.setName("Description");
				description.getTypes().add(textType);
				metaData.getVariables().add(description);
				Text descriptionValue = ValuesFactory.eINSTANCE.createText();
				descriptionValue.setText((String) ((List<String>) results.getValue("description", 0)).get(0));
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
				commentValue.setText((String) ((List<String>) results.getValue("comment", 0)).get(0));
				comment.getInitialValues().put(textType, commentValue);
			}
			
			
//			External Links:
			Variable external = VariablesFactory.eINSTANCE.createVariable();
			external.setId("external");
			external.setName("External Links");
			external.getTypes().add(htmlType);
			metaData.getVariables().add(external);
			
			HTML externalValue = ValuesFactory.eINSTANCE.createHTML();
			String extLink = "";
			
			switch (((String) results.getValue("id", 0)).substring(0, 4)) {
				case "FBal":
					extLink = "<a href=\"http://flybase.org/reports/" + (String) results.getValue("id", 0) + "\" target=\"_blank\" title=\"FlyBase\" ><img src=\"http://flybase.org/favicon\" height=20 alt=\"FlyBase\" style=\"border: orange;border-style: solid;\" /></a>";
					break;
				case "FBbt":
					extLink = "<a href=\"http://flybase.org/cgi-bin/cvreport.html?rel=is_a&id=" + ((String) results.getValue("id", 0)).replace("_", ":") + "\" target=\"_blank\" title=\"FlyBase\" ><img src=\"http://flybase.org/favicon\" height=20 alt=\"FlyBase\" style=\"border: orange;border-style: solid;\" /></a>";
					break;
				case "VFB_":
					extLink = "<a href=\"http://neurolex.org/wiki/" + ((String) results.getValue("id", 0)).replace("_", ":") + "\" target=\"_blank\" title=\"NeuroLex\" ><img src=\"http://neurolex.org/favicon.ico\" height=20 alt=\"NeuroLex\" style=\"border: orange;border-style: solid;\" /></a>";
					break;
				default:
					break;
			}
			
			externalValue.setHtml(extLink);

			htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			external.getInitialValues().put(htmlType, externalValue);
			
			type.getVariables().add(metaDataVar);
			geppettoModelAccess.addTypeToLibrary(metaData, dataSource.getTargetLibrary());
			
		} catch (GeppettoVisitingException e) {
			throw new GeppettoDataSourceException(e);
		}
		
		return results;
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
