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
import java.util.List;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.datasources.QueryChecker;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DataSourceLibraryConfiguration;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.Query;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.types.CompositeType;
import org.geppetto.model.types.ImageType;
import org.geppetto.model.types.ImportType;
import org.geppetto.model.types.Type;
import org.geppetto.model.types.TypesFactory;
import org.geppetto.model.types.TypesPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.util.ModelUtility;
import org.geppetto.model.values.HTML;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;

import com.google.gson.Gson;

/**
 * @author robertcourt
 *
 */
public class AddImportTypesThumbnailQueryProcessor extends AQueryProcessor
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
			Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			

			System.out.println("Processing Images...");

			// set VFB individual images:
			if(results.getValue("imageDir", 0) != null)
			{
				// set individual thumbnail:
				String tempFolder = (String) results.getValue("imageDir", 0);
				String tempFile = remoteFolder(tempFolder) + "thumbnail.png";
				if(checkURL(tempFile))
				{
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
				tempFile = remoteFolder(tempFolder) + "volume_man.obj"; // manually created obj rather than auto point cloud
				if(checkURL(tempFile))
				{
					System.out.println("Adding manual OBJ...");
					tempFile = localFolder(tempFolder) + "volume_man.obj";
					Variable objVar = VariablesFactory.eINSTANCE.createVariable();
					ImportType objImportType = TypesFactory.eINSTANCE.createImportType();
					objImportType.setUrl(tempFile);
					objImportType.setId(variable.getId() + "_obj");
					objImportType.setModelInterpreterId("objModelInterpreterService");
					objVar.getTypes().add(objImportType);
					geppettoModelAccess.addTypeToLibrary(objImportType, getLibraryFor(dataSource, "obj"));
					objVar.setId(variable.getId() + "_obj");
					objVar.setName("3D Volume");
					type.getVariables().add(objVar);
				}
				else
				{
					tempFile = remoteFolder(tempFolder) + "volume.obj";
					if(checkURL(tempFile))
					{
						System.out.println("Adding OBJ...");
						tempFile = localFolder(tempFolder) + "volume.obj";
						Variable objVar = VariablesFactory.eINSTANCE.createVariable();
						ImportType objImportType = TypesFactory.eINSTANCE.createImportType();
						objImportType.setUrl(tempFile);
						objImportType.setId(variable.getId() + "_obj");
						objImportType.setModelInterpreterId("objModelInterpreterService");
						objVar.getTypes().add(objImportType);
						geppettoModelAccess.addTypeToLibrary(objImportType, getLibraryFor(dataSource, "obj"));
						objVar.setId(variable.getId() + "_obj");
						objVar.setName("3D Volume");
						type.getVariables().add(objVar);
					}
				}
				tempFile = remoteFolder(tempFolder) + "volume.swc";
				if(checkURL(tempFile))
				{
					System.out.println("Adding SWC...");
					tempFile = localFolder(tempFolder) + "volume.swc";
					Variable swcVar = VariablesFactory.eINSTANCE.createVariable();
					ImportType swcImportType = TypesFactory.eINSTANCE.createImportType();
					swcImportType.setUrl(tempFile);
					swcImportType.setId(variable.getId() + "_swc");
					swcImportType.setModelInterpreterId("swcModelInterpreter");
					swcVar.getTypes().add(swcImportType);
					geppettoModelAccess.addTypeToLibrary(swcImportType, getLibraryFor(dataSource, "swc"));
					swcVar.setName("3D Skeleton");
					swcVar.setId(variable.getId() + "_swc");
					type.getVariables().add(swcVar);
				}
				tempFile = remoteFolder(tempFolder) + "volume.nrrd";
				if(checkURL(tempFile))
				{
					System.out.println("Adding NRRD...");
					Variable downloads = VariablesFactory.eINSTANCE.createVariable();
					downloads.setId("downloads");
					downloads.setName("Downloads");
					downloads.getTypes().add(htmlType);
					geppettoModelAccess.addVariableToType(downloads, metadataType);

					HTML downloadValue = ValuesFactory.eINSTANCE.createHTML();
					String downloadLink = "Aligned Image: ​<a download=\"" + (String) variable.getId() + ".nrrd\" href=\"" + tempFile + "\">" + (String) variable.getId() + ".nrrd</a><br/>​​​​​​​​​​​​​​​​​​​​​​​​​​​";
					downloadLink += "Note: see licensing section for reuse and attribution info."; // TODO: pull licensing from neo4j

					downloadValue.setHtml(downloadLink);
					downloads.getInitialValues().put(htmlType, downloadValue);
					// TODO: add NRRD download
				}
				tempFile = remoteFolder(tempFolder) + "volume.wlz";
				if(checkURL(tempFile))
				{
					System.out.println("Adding Woolz...");
					tempFile = localFolder(tempFolder).replace("SERVER_ROOT/vfb/", "/disk/data/VFB/IMAGE_DATA/") + "volume.wlz";
					Variable slicesVar = VariablesFactory.eINSTANCE.createVariable();
					ImageType slicesType = (ImageType) geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);
					Image slicesValue = ValuesFactory.eINSTANCE.createImage();
					slicesValue.setData(new Gson().toJson(new IIPJSON(0,"http://vfbdev.inf.ed.ac.uk/fcgi/wlziipsrv.fcgi",tempFile)));
					slicesValue.setFormat(ImageFormat.IIP);
					slicesValue.setReference(variable.getId());
					slicesVar.setId(variable.getId() + "_slices");
					slicesVar.setName(variable.getName());
					slicesVar.getTypes().add(slicesType);
					slicesVar.getInitialValues().put(slicesType, slicesValue);
					type.getVariables().add(slicesVar);
					// TODO: add 2D/woolz
				}
				if (results.getValue("tempId", 0) != null)
				{
					System.out.println("Adding Template Space...");
					Variable tempVar = VariablesFactory.eINSTANCE.createVariable();
					tempVar.setId("template");
					tempVar.setName("Template Space");
					tempVar.getTypes().add(htmlType);
					geppettoModelAccess.addVariableToType(tempVar, metadataType);

					HTML tempValue = ValuesFactory.eINSTANCE.createHTML();
					String tempLink = "<a href=\"#\" instancepath=\"" + (String) results.getValue("tempId", 0) + "\">" + (String) results.getValue("tempName", 0) + "</a>";

					tempValue.setHtml(tempLink);
					tempVar.getInitialValues().put(htmlType, tempValue);

					// Add template ID as supertype:

					List<GeppettoLibrary> dependenciesLibrary = dataSource.getDependenciesLibrary();
					String supertype = (String) results.getValue("tempId", 0);
					type.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
					System.out.println("Adding to SuperType: " + supertype);
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

	private class IIPJSON{
		int indexNumber;
		String serverUrl;
		String fileLocation;
		public IIPJSON(int indexNumber, String serverUrl, String fileLocation)
		{
			this.indexNumber=indexNumber;
			this.fileLocation=fileLocation;
			this.serverUrl=serverUrl;
		}
	}
	

	/**
	 * @param dataSource
	 * @param format
	 * @return
	 */
	private GeppettoLibrary getLibraryFor(DataSource dataSource, String format)
	{
		for(DataSourceLibraryConfiguration lc : dataSource.getLibraryConfigurations())
		{
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
	private String remoteForID(String id)
	{
		return "http://www.virtualflybrain.org/data/VFB/i/" + id.substring(4, 8) + "/" + id.substring(8) + "/";
	}

	/**
	 * @param id
	 */
	private String localForID(String id)
	{
		return "SERVER_ROOT/vfb/VFB/i/" + id.substring(4, 8) + "/" + id.substring(8) + "/";
	}

	/**
	 * @param id
	 */
	private String remoteFolder(String folder)
	{
		return "http://www.virtualflybrain.org/data/" + folder;
	}

	/**
	 * @param id
	 */
	private String localFolder(String folder)
	{
		return "SERVER_ROOT/vfb/" + folder;
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
