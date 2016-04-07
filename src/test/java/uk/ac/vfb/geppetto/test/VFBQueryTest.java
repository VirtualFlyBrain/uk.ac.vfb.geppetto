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
package uk.ac.vfb.geppetto.test;

import java.io.IOException;
import java.util.Map;

import junit.framework.Assert;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.geppetto.core.common.GeppettoInitializationException;
import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.manager.SharedLibraryManager;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.GeppettoModelReader;
import org.geppetto.core.model.GeppettoSerializer;
import org.geppetto.core.services.registry.ApplicationListenerBean;
import org.geppetto.datasources.Neo4jDataSourceService;
import org.geppetto.model.GeppettoModel;
import org.geppetto.model.GeppettoPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.context.support.GenericWebApplicationContext;

import uk.ac.vfb.geppetto.AddImportTypesQueryProcessor;
import uk.ac.vfb.geppetto.AddImportTypesSynonymQueryProcessor;
import uk.ac.vfb.geppetto.AddTypesQueryProcessor;

/**
 * @author matteocantarelli
 *
 */
public class VFBQueryTest
{

	/**
	 * @throws java.lang.Exception
	 */
	@SuppressWarnings("deprecation")
	@BeforeClass
	public static void setUp() throws Exception
	{
		GenericWebApplicationContext context = new GenericWebApplicationContext();
		
		BeanDefinition queryProcessorBeanDefinition = new RootBeanDefinition(AddTypesQueryProcessor.class);
		context.registerBeanDefinition("vfbTypesQueryProcessor", queryProcessorBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbTypesQueryProcessor", queryProcessorBeanDefinition);
		
		BeanDefinition queryProcessorImportTypesBeanDefinition = new RootBeanDefinition(AddImportTypesQueryProcessor.class);
		context.registerBeanDefinition("vfbImportTypesQueryProcessor", queryProcessorImportTypesBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbImportTypesQueryProcessor", queryProcessorImportTypesBeanDefinition);
		
		BeanDefinition queryProcessorImportTypesSynonymBeanDefinition = new RootBeanDefinition(AddImportTypesSynonymQueryProcessor.class);
		context.registerBeanDefinition("vfbImportTypesSynonymQueryProcessor", queryProcessorImportTypesSynonymBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbImportTypesSynonymQueryProcessor", queryProcessorImportTypesSynonymBeanDefinition);
		
		ContextRefreshedEvent event = new ContextRefreshedEvent(context);
		ApplicationListenerBean listener = new ApplicationListenerBean();
		listener.onApplicationEvent(event);
		ApplicationContext retrievedContext = ApplicationListenerBean.getApplicationContext("vfbTypesQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbTypesQueryProcessor"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("vfbImportTypesQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbImportTypesQueryProcessor"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("vfbImportTypesSynonymQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbImportTypesSynonymQueryProcessor"));
		
	}

	/**
	 * Test method for {@link org.geppetto.datasources.Neo4jDataSourceService#fetchVariable(java.lang.String)}.
	 * 
	 * @throws GeppettoDataSourceException
	 * @throws GeppettoInitializationException
	 * @throws GeppettoVisitingException 
	 * @throws IOException 
	 */
	@Test
	public void testFetchVariable() throws GeppettoDataSourceException, GeppettoInitializationException, GeppettoVisitingException, IOException
	{
		GeppettoModel model = GeppettoModelReader.readGeppettoModel(VFBQueryTest.class.getClassLoader().getResource("GeppettoModelM1.xmi"));
        model.getLibraries().add(SharedLibraryManager.getSharedCommonLibrary());
        
		GeppettoModelAccess geppettoModelAccess = new GeppettoModelAccess(model);
		Neo4jDataSourceService dataSource = new Neo4jDataSourceService();
		dataSource.initialize(model.getDataSources().get(0), geppettoModelAccess);
		
		System.out.println(GeppettoSerializer.serializeToJSON(model, true));
		
		dataSource.fetchVariable("FBbt_00100219");
		
//		// Initialize the factory and the resource set
		GeppettoPackage.eINSTANCE.eClass();
		Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
		Map<String, Object> m = reg.getExtensionToFactoryMap();
		m.put("xmi", new XMIResourceFactoryImpl()); // sets the factory for the XMI type
		ResourceSet resSet = new ResourceSetImpl();

		Resource resource = resSet.createResource(URI.createURI("./src/test/resources/fetchedVariable.xmi"));
		resource.getContents().add(model);
		resource.save(null);
		
		
		System.out.println(GeppettoSerializer.serializeToJSON(model, true));

	}

}
