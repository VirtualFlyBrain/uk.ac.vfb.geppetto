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
import java.util.Map;

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
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.values.HTML;
import org.geppetto.model.values.Text;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;

/**
 * @author robertcourt
 *
 */
public class AddImportTypesSynonymQueryProcessor implements IQueryProcessor
{

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.geppetto.core.datasources.IQueryProcessor#process(org.geppetto.model.ProcessQuery, org.geppetto.model.variables.Variable, org.geppetto.model.QueryResults)
	 */
	@Override
	public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException
	{

		System.out.println(results);

		try
		{

			CompositeType type = (CompositeType) variable.getAnonymousTypes().get(0);

			Variable metaDataVar = VariablesFactory.eINSTANCE.createVariable();
			metaDataVar.setId("metaDataVar");
			CompositeType metaData = TypesFactory.eINSTANCE.createCompositeType();
			metaDataVar.getTypes().add(metaData);
			metaDataVar.setId(variable.getId() + "_metaDataVar");
			metaData.setId(variable.getId() + "_metadata");

			Type textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
			Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			String descriptionRef = "";

			// set meta id:
			Variable metaID = VariablesFactory.eINSTANCE.createVariable();
			metaID.setId("id");
			metaID.setName("ID");
			metaID.getTypes().add(metaData);
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
			label.getTypes().add(metaData);
			metaData.getVariables().add(label);
			HTML labelValue = ValuesFactory.eINSTANCE.createHTML();
			String labelLink = "<a href=\"#\" instancepath=\"" + (String) results.getValue("id", 0) + "\">" + (String) results.getValue("name", 0) + "</a>";
			labelValue.setHtml(labelLink);

			htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			label.getInitialValues().put(htmlType, labelValue);

			// set synonyms:
			if(results.getValue("synonyms", 0) != null)
			{
				Variable synonyms = VariablesFactory.eINSTANCE.createVariable();
				synonyms.setId("synonyms");
				synonyms.setName("Alternative names");
				synonyms.getTypes().add(metaData);
				metaData.getVariables().add(synonyms);
				HTML synonymsValue = ValuesFactory.eINSTANCE.createHTML();

				String synonymLinks = "";
				System.out.println(results.getValue("relationship", 0));
				if(results.getValue("relationship", 0) != null)
				{
					int i = 0;
					while(results.getValue("relationship", i) != null)
					{
						System.out.println(results.getValue("relationship", i));
						if((String) ((Map) results.getValue("relationship", i)).get("synonym") != null)
						{
							System.out.println((String) ((Map) results.getValue("relationship", i)).get("synonym"));
							synonymLinks += "<a href=\"#\" instancepath=\"" + (String) results.getValue("id", 0) + "\">" + (String) ((Map) results.getValue("relationship", i)).get("synonym") + "</a>";
							if(((Map) results.getValue("relationship", i)).get("scope") != null)
							{
								synonymLinks += " [synonym scope: \'" + (String) ((Map) results.getValue("relationship", i)).get("scope") + "\']";
							}
							if(results.getValue("relRef", i) != null)
							{
								synonymLinks += " (" + (String) results.getValue("relRef", i);
								if(results.getValue("relFBrf", i) != null)
								{
									synonymLinks += "; <a href=\"flybase.org/reports/" + (String) results.getValue("relFBrf", i) + "\" target=\"_blank\" >FlyBase: "
											+ (String) results.getValue("relFBrf", i) + "</a>";
								}
								if(results.getValue("relPMID", i) != null)
								{
									synonymLinks += "; <a href=\"http://www.ncbi.nlm.nih.gov/pubmed/?term=" + (String) results.getValue("relPMID", i) + "\" target=\"_blank\" >PMID: "
											+ (String) results.getValue("relPMID", i) + "</a>";
								}
								if(results.getValue("relDOI", i) != null)
								{
									synonymLinks += "; <a href=\" http://dx.doi.org/" + (String) results.getValue("relDOI", i) + "\" target=\"_blank\" >doi: "
											+ (String) results.getValue("relPMID", i) + "</a>";
								}
								synonymLinks += ")";
							}
							synonymLinks += "<br/>";
						}
						i++;
					}
				}
				synonymsValue.setHtml(synonymLinks);

				htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
				synonyms.getInitialValues().put(htmlType, synonymsValue);
			}

			// set description:
			if(results.getValue("description", 0) != null)
			{
				Variable description = VariablesFactory.eINSTANCE.createVariable();
				description.setId("description");
				description.setName("Description");
				description.getTypes().add(metaData);
				metaData.getVariables().add(description);
				Text descriptionValue = ValuesFactory.eINSTANCE.createText();
				descriptionValue.setText((String) ((List<String>) results.getValue("description", 0)).get(0));

				textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
				description.getInitialValues().put(textType, descriptionValue);
			}

			// set comment:
			if(results.getValue("comment", 0) != null)
			{
				Variable comment = VariablesFactory.eINSTANCE.createVariable();
				comment.setId("comment");
				comment.setName("Notes");
				comment.getTypes().add(metaData);
				metaData.getVariables().add(comment);
				Text commentValue = ValuesFactory.eINSTANCE.createText();
				commentValue.setText((String) ((List<String>) results.getValue("comment", 0)).get(0));

				textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
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
