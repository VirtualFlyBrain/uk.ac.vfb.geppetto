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
import org.geppetto.datasources.aberowl.AberOWLDataSourceService;
import org.geppetto.datasources.neo4j.Neo4jDataSourceService;
import org.geppetto.datasources.opencpu.OpenCPUDataSourceService;
import org.geppetto.datasources.owlery.OWLeryDataSourceService;
import org.geppetto.model.GeppettoModel;
import org.geppetto.model.GeppettoPackage;
import org.geppetto.model.util.GeppettoVisitingException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.context.support.GenericWebApplicationContext;

import uk.ac.vfb.geppetto.AddImportTypesThumbnailQueryProcessor;
import uk.ac.vfb.geppetto.AddImportTypesExtLinkQueryProcessor;
import uk.ac.vfb.geppetto.AddImportTypesRefsQueryProcessor;
import uk.ac.vfb.geppetto.AddImportTypesQueryProcessor;
import uk.ac.vfb.geppetto.AddImportTypesSynonymQueryProcessor;
import uk.ac.vfb.geppetto.AddTypesQueryProcessor;
import uk.ac.vfb.geppetto.CreateResultListForIndividualsForQueryResultsQueryProcessor;
import uk.ac.vfb.geppetto.VFBProcessTermInfo;
import uk.ac.vfb.geppetto.OWLeryQueryProcessor;
import uk.ac.vfb.geppetto.NBLASTQueryProcessor;

/**
 * @author matteocantarelli
 *
 */
public class VFBQueryTest
{

	/**
	 * @throws java.lang.Exception
	 */
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

		BeanDefinition queryProcessTermInfoBeanDefinition = new RootBeanDefinition(VFBProcessTermInfo.class);
		context.registerBeanDefinition("vfbProcessTermInfo", queryProcessTermInfoBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbProcessTermInfo", queryProcessTermInfoBeanDefinition);
		
		BeanDefinition queryProcessorImportTypesSynonymBeanDefinition = new RootBeanDefinition(AddImportTypesSynonymQueryProcessor.class);
		context.registerBeanDefinition("vfbImportTypesSynonymQueryProcessor", queryProcessorImportTypesSynonymBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbImportTypesSynonymQueryProcessor", queryProcessorImportTypesSynonymBeanDefinition);

		BeanDefinition queryProcessorImportTypesThumbnailBeanDefinition = new RootBeanDefinition(AddImportTypesThumbnailQueryProcessor.class);
		context.registerBeanDefinition("vfbImportTypesThumbnailQueryProcessor", queryProcessorImportTypesThumbnailBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbImportTypesThumbnailQueryProcessor", queryProcessorImportTypesThumbnailBeanDefinition);

		BeanDefinition queryProcessorImportTypesExtLinkBeanDefinition = new RootBeanDefinition(AddImportTypesExtLinkQueryProcessor.class);
		context.registerBeanDefinition("vfbImportTypesExtLinkQueryProcessor", queryProcessorImportTypesExtLinkBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbImportTypesExtLinkQueryProcessor", queryProcessorImportTypesExtLinkBeanDefinition);

		BeanDefinition queryProcessorImportTypesRefsBeanDefinition = new RootBeanDefinition(AddImportTypesRefsQueryProcessor.class);
		context.registerBeanDefinition("vfbImportTypesRefsQueryProcessor", queryProcessorImportTypesRefsBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbImportTypesRefsQueryProcessor", queryProcessorImportTypesRefsBeanDefinition);
		
		BeanDefinition createResultListForQueryBeanDefinition = new RootBeanDefinition(CreateResultListForIndividualsForQueryResultsQueryProcessor.class);
		context.registerBeanDefinition("vfbCreateResultListForIndividualsForQueryResultsQueryProcessor", createResultListForQueryBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbCreateResultListForIndividualsForQueryResultsQueryProcessor", createResultListForQueryBeanDefinition);
		
		BeanDefinition queryProcessorOwleryBeanDefinition = new RootBeanDefinition(OWLeryQueryProcessor.class);
		context.registerBeanDefinition("owleryIdOnlyQueryProcessor", queryProcessorOwleryBeanDefinition);
		context.registerBeanDefinition("scopedTarget.owleryIdOnlyQueryProcessor", queryProcessorOwleryBeanDefinition);
		
		BeanDefinition queryProcessorNBLASTBeanDefinition = new RootBeanDefinition(NBLASTQueryProcessor.class);
		context.registerBeanDefinition("nblastQueryProcessor", queryProcessorNBLASTBeanDefinition);
		context.registerBeanDefinition("scopedTarget.nblastQueryProcessor", queryProcessorNBLASTBeanDefinition);

		BeanDefinition neo4jDataSourceBeanDefinition = new RootBeanDefinition(Neo4jDataSourceService.class);
		context.registerBeanDefinition("neo4jDataSource", neo4jDataSourceBeanDefinition);
		context.registerBeanDefinition("scopedTarget.neo4jDataSource", neo4jDataSourceBeanDefinition);
		
		BeanDefinition aberOWLDataSourceBeanDefinition = new RootBeanDefinition(AberOWLDataSourceService.class);
		context.registerBeanDefinition("aberOWLDataSource", aberOWLDataSourceBeanDefinition);
		context.registerBeanDefinition("scopedTarget.aberOWLDataSource", aberOWLDataSourceBeanDefinition);
		
		BeanDefinition owleryDataSourceBeanDefinition = new RootBeanDefinition(OWLeryDataSourceService.class);
		context.registerBeanDefinition("owleryDataSource", owleryDataSourceBeanDefinition);
		context.registerBeanDefinition("scopedTarget.owleryDataSource", owleryDataSourceBeanDefinition);
		
		BeanDefinition nblastDataSourceBeanDefinition = new RootBeanDefinition(OpenCPUDataSourceService.class);
		context.registerBeanDefinition("opencpuDataSource", nblastDataSourceBeanDefinition);
		context.registerBeanDefinition("scopedTarget.opencpuDataSource", nblastDataSourceBeanDefinition);

		ContextRefreshedEvent event = new ContextRefreshedEvent(context);
		ApplicationListenerBean listener = new ApplicationListenerBean();
		listener.onApplicationEvent(event);
		ApplicationContext retrievedContext = ApplicationListenerBean.getApplicationContext("vfbTypesQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbTypesQueryProcessor"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("vfbImportTypesQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbImportTypesQueryProcessor"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("vfbImportTypesSynonymQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbImportTypesSynonymQueryProcessor"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("vfbImportTypesThumbnailQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbImportTypesThumbnailQueryProcessor"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("vfbImportTypesExtLinkQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbImportTypesExtLinkQueryProcessor"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("vfbImportTypesRefsQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbImportTypesRefsQueryProcessor"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("vfbProcessTermInfo");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbProcessTermInfo"));		
		retrievedContext = ApplicationListenerBean.getApplicationContext("owleryIdOnlyQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.owleryIdOnlyQueryProcessor"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("nblastQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.nblastQueryProcessor"));
	}

	/**
	 * Test method for {@link org.geppetto.datasources.neo4j.Neo4jDataSourceService#fetchVariable(java.lang.String)}.
	 * 
	 * @throws GeppettoDataSourceException
	 * @throws GeppettoInitializationException
	 * @throws GeppettoVisitingException
	 * @throws IOException
	 */
	@Test
	public void testFetchVariable() throws GeppettoDataSourceException, GeppettoInitializationException, GeppettoVisitingException, IOException
	{
		GeppettoModel model = GeppettoModelReader.readGeppettoModel(VFBQueryTest.class.getClassLoader().getResource("VFBModel/GeppettoModelVFB.xmi"));
		model.getLibraries().add(SharedLibraryManager.getSharedCommonLibrary());

		GeppettoModelAccess geppettoModelAccess = new GeppettoModelAccess(model);
		Neo4jDataSourceService dataSource = new Neo4jDataSourceService();
		dataSource.initialize(model.getDataSources().get(0), geppettoModelAccess);
		
		OpenCPUDataSourceService dataSource2 = new OpenCPUDataSourceService();
		dataSource2.initialize(model.getDataSources().get(4), geppettoModelAccess);


		dataSource.fetchVariable("FBbt_00100219");

		dataSource.fetchVariable("VFB_00000001");

		//dataSource2.fetchVariable("VFB_00014755");		

		// // Initialize the factory and the resource set
		GeppettoPackage.eINSTANCE.eClass();
		Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
		Map<String, Object> m = reg.getExtensionToFactoryMap();
		m.put("xmi", new XMIResourceFactoryImpl()); // sets the factory for the XMI type
		ResourceSet resSet = new ResourceSetImpl();

		Resource resource = resSet.createResource(URI.createURI("./src/test/resources/fetchedVariable.xmi"));
		resource.getContents().add(model);
		resource.save(null);

	}

	
}
