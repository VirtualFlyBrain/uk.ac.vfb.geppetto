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
import org.geppetto.model.DataSourceLibraryConfiguration;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.ProcessQuery;
import org.geppetto.model.QueryResults;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.ImportType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.types.VisualType;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.util.ModelUtility;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;

/**
 * @author robertcourt
 *
 */
public class AddImportTypesImageQueryProcessor implements IQueryProcessor
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
			
			// retrieving the composite type for new importType variables 
			CompositeType type = (CompositeType) variable.getAnonymousTypes().get(0);

			Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
			
			System.out.println("Processing Images...");
			
			// set VFB individual images:
			if (variable.getId().startsWith("VFB_")){
				// set individual thumbnail:
				String tempFile = "http://www.virtualflybrain.org/data/VFB/i/" + variable.getId().substring(4, 8) + "/" + variable.getId().substring(8) + "/thumbnail.png";
				if (checkURL(tempFile)){
					System.out.println("Adding Thumbnail...");
					Variable thumbnailVar = VariablesFactory.eINSTANCE.createVariable();
					thumbnailVar.setId("thumbnail");
					thumbnailVar.setName("Thumbnail");
					thumbnailVar.getTypes().add(imageType);
					geppettoModelAccess.addVariableToType(thumbnailVar, metadataType);
					Image thumbnailValue = ValuesFactory.eINSTANCE.createImage();
					thumbnailValue.setName(variable.getName());
					thumbnailValue.setData(tempFile);
					thumbnailValue.setReference(variable.getId());
					thumbnailValue.setFormat(ImageFormat.PNG);
					thumbnailVar.getInitialValues().put(imageType, thumbnailValue);
				}
				// set image types:
				tempFile = remoteForID(variable.getId()) + "volume.obj";
				if (checkURL(tempFile)){
					System.out.println("Adding OBJ...");
					tempFile = localForID(variable.getId()) + "volume.obj";
					Variable objVar = VariablesFactory.eINSTANCE.createVariable();
					ImportType objImportType=TypesFactory.eINSTANCE.createImportType();
					objImportType.setUrl(tempFile);
					objImportType.setId(variable.getId()+"_obj");
					objImportType.setModelInterpreterId("objModelInterpreterService");
					objVar.getTypes().add(objImportType);
					geppettoModelAccess.addTypeToLibrary(objImportType, getLibraryFor(dataSource,"obj"));
					objVar.setId(variable.getId()+"_obj");
					objVar.setName("3D Volume");
					type.getVariables().add(objVar);
				}
				tempFile = remoteForID(variable.getId()) + "volume.swc";
				if (checkURL(tempFile)){
					System.out.println("Adding SWC...");
					tempFile = localForID(variable.getId()) + "volume.swc";
					Variable swcVar = VariablesFactory.eINSTANCE.createVariable();
					ImportType swcImportType=TypesFactory.eINSTANCE.createImportType();
					swcImportType.setUrl(tempFile);
					swcImportType.setId(variable.getId()+"_swc");
					swcImportType.setModelInterpreterId("swcModelInterpreterService");
					swcVar.getTypes().add(swcImportType);
					geppettoModelAccess.addTypeToLibrary(swcImportType, getLibraryFor(dataSource,"swc"));
					swcVar.setName("3D Skeleton");
					swcVar.setId(variable.getId()+"_swc");
					type.getVariables().add(swcVar);
				}
				tempFile = remoteForID(variable.getId()) + "volume.nrrd";
				if (checkURL(tempFile)){
					System.out.println("Adding NRRD...");
					// TODO add NRRD download
				}
				tempFile = remoteForID(variable.getId()) + "volume.wlz";
				if (checkURL(tempFile)){
					System.out.println("Adding Woolz...");
					tempFile = localForID(variable.getId()).replace("SERVER_ROOT/vfb/", "/disk/data/VFB/IMAGE_DATA/") + "volume.wlz";
					// TODO add 2D/woolz
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
	 * @param dataSource
	 * @param format
	 * @return
	 */
	private GeppettoLibrary getLibraryFor(DataSource dataSource, String format)
	{
		for(DataSourceLibraryConfiguration lc: dataSource.getLibraryConfigurations()){
			if(lc.getFormat().equals(format))
			{
				return lc.getLibrary();
			}
		}
		return null;
	}
	/**
	 * @param id
	 */
	private String remoteForID(String id){
		return "http://www.virtualflybrain.org/data/VFB/i/" + id.substring(4, 8) + "/" + id.substring(8) + "/";
	}
	/**
	 * @param id
	 */
	private String localForID(String id){
		return "SERVER_ROOT/vfb/VFB/i/" + id.substring(4, 8) + "/" + id.substring(8) + "/";
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
