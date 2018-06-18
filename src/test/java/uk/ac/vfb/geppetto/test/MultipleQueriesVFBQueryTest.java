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

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.geppetto.core.common.GeppettoInitializationException;
import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.manager.SharedLibraryManager;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.GeppettoModelReader;
import org.geppetto.core.model.GeppettoSerializer;
import org.geppetto.core.services.registry.ApplicationListenerBean;
import org.geppetto.datasources.aberowl.AberOWLDataSourceService;
import org.geppetto.datasources.neo4j.Neo4jDataSourceService;
import org.geppetto.datasources.owlery.OWLeryDataSourceService;
import org.geppetto.model.GeppettoModel;
import org.geppetto.model.datasources.Query;
import org.geppetto.model.datasources.BooleanOperator;
import org.geppetto.model.datasources.DatasourcesFactory;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.datasources.RunnableQuery;
import org.geppetto.model.util.GeppettoModelException;
import org.geppetto.model.util.GeppettoVisitingException;
import org.geppetto.model.variables.Variable;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.context.support.GenericWebApplicationContext;
import uk.ac.vfb.geppetto.*;

import org.geppetto.model.values.Pointer;
import org.geppetto.model.values.PointerElement;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author matteocantarelli
 *
 */
public class MultipleQueriesVFBQueryTest
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
		
		BeanDefinition queryProcessTermInfoBeanDefinition = new RootBeanDefinition(VFBProcessTermInfo.class);
		context.registerBeanDefinition("vfbProcessTermInfo", queryProcessTermInfoBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbProcessTermInfo", queryProcessTermInfoBeanDefinition);

		BeanDefinition queryProcessorImportTypesBeanDefinition = new RootBeanDefinition(AddImportTypesQueryProcessor.class);
		context.registerBeanDefinition("vfbImportTypesQueryProcessor", queryProcessorImportTypesBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbImportTypesQueryProcessor", queryProcessorImportTypesBeanDefinition);

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

		BeanDefinition aberOWLQueryProcessorBeanDefinition = new RootBeanDefinition(VFBAberOWLQueryProcessor.class);
		context.registerBeanDefinition("vfbAberOWLQueryProcessor", aberOWLQueryProcessorBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbAberOWLQueryProcessor", aberOWLQueryProcessorBeanDefinition);

		BeanDefinition createImagesForQueryBeanDefinition = new RootBeanDefinition(CreateImagesForQueryResultsQueryProcessor.class);
		context.registerBeanDefinition("vfbCreateImagesForQueryResultsQueryProcessor", createImagesForQueryBeanDefinition);
		context.registerBeanDefinition("scopedTarget.vfbCreateImagesForQueryResultsQueryProcessor", createImagesForQueryBeanDefinition);

		BeanDefinition neo4jDataSourceBeanDefinition = new RootBeanDefinition(Neo4jDataSourceService.class);
		context.registerBeanDefinition("neo4jDataSource", neo4jDataSourceBeanDefinition);
		context.registerBeanDefinition("scopedTarget.neo4jDataSource", neo4jDataSourceBeanDefinition);

		BeanDefinition aberOWLDataSourceBeanDefinition = new RootBeanDefinition(AberOWLDataSourceService.class);
		context.registerBeanDefinition("aberOWLDataSource", aberOWLDataSourceBeanDefinition);
		context.registerBeanDefinition("scopedTarget.aberOWLDataSource", aberOWLDataSourceBeanDefinition);
		
		BeanDefinition owleryDataSourceBeanDefinition = new RootBeanDefinition(OWLeryDataSourceService.class);
		context.registerBeanDefinition("owleryDataSource", owleryDataSourceBeanDefinition);
		context.registerBeanDefinition("scopedTarget.owleryDataSource", owleryDataSourceBeanDefinition);

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
		retrievedContext = ApplicationListenerBean.getApplicationContext("vfbAberOWLQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbAberOWLQueryProcessor"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("vfbCreateImagesForQueryResultsQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbCreateImagesForQueryResultsQueryProcessor"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("vfbProcessTermInfo");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.vfbProcessTermInfo"));
		retrievedContext = ApplicationListenerBean.getApplicationContext("owleryIdOnlyQueryProcessor");
		Assert.assertNotNull(retrievedContext.getBean("scopedTarget.owleryIdOnlyQueryProcessor"));

	}

	@Test
	public void testANDedQueries() throws GeppettoInitializationException, GeppettoVisitingException, GeppettoDataSourceException, GeppettoModelException, IOException
	{

		GeppettoModel model = GeppettoModelReader.readGeppettoModel(VFBQueryTest.class.getClassLoader().getResource("VFBModel/GeppettoModelVFB.xmi"));
		model.getLibraries().add(SharedLibraryManager.getSharedCommonLibrary());

		GeppettoModelAccess geppettoModelAccess = new GeppettoModelAccess(model);

		Neo4jDataSourceService neo4JDataSource = new Neo4jDataSourceService();
		neo4JDataSource.initialize(model.getDataSources().get(0), geppettoModelAccess);

		AberOWLDataSourceService aberDataSource = new AberOWLDataSourceService();
		aberDataSource.initialize(model.getDataSources().get(1), geppettoModelAccess);

		OWLeryDataSourceService owleryDataSource = new OWLeryDataSourceService();
		owleryDataSource.initialize(model.getDataSources().get(2), geppettoModelAccess);
		
		//Build list of available query indexs against ids:
		Map<String,Integer> avQ = new HashMap();
		Integer i = 0;
		for (Query query : model.getQueries()) {
			String q = query.getId();
			if (avQ.containsKey(q)) {
				System.out.println("Duplicate query id: " + q);
			} else {
				avQ.put(q, i);
			}
			i++;
		}
		
		System.out.println(GeppettoSerializer.serializeToJSON(model, true));

		neo4JDataSource.fetchVariable("FBbt_00003748");
		neo4JDataSource.fetchVariable("FBbt_00003852");
		

		Variable variable1 = geppettoModelAccess.getPointer("FBbt_00003748").getElements().get(0).getVariable();
		Variable variable2 = geppettoModelAccess.getPointer("FBbt_00003852").getElements().get(0).getVariable();

		EList<RunnableQuery> runnableQueriesEMF = new BasicEList<RunnableQuery>();

		RunnableQuery rqEMF1 = DatasourcesFactory.eINSTANCE.createRunnableQuery();
		rqEMF1.setQueryPath(model.getQueries().get(avQ.get("neuronssynaptic")).getPath());
		rqEMF1.setTargetVariablePath(variable1.getPath());
		runnableQueriesEMF.add(rqEMF1);
		
		RunnableQuery rqEMF2 = DatasourcesFactory.eINSTANCE.createRunnableQuery();
		rqEMF2.setQueryPath(model.getQueries().get(avQ.get("neuronssynaptic")).getPath());
		rqEMF2.setTargetVariablePath(variable2.getPath());
		runnableQueriesEMF.add(rqEMF2);

		
		int count = owleryDataSource.getNumberOfResults(runnableQueriesEMF);
		try{
			Assert.assertTrue(87<count);
		}catch (AssertionError e) {
			System.out.println("Fail: only " + count + " results returned, there should be 88+ results");
			throw new AssertionError(e);
		}

		QueryResults results = owleryDataSource.execute(runnableQueriesEMF);

		Assert.assertEquals("ID", results.getHeader().get(0));
		Assert.assertEquals("Name", results.getHeader().get(1));
		Assert.assertEquals("Definition", results.getHeader().get(2));
		Assert.assertEquals("Type", results.getHeader().get(3));
		Assert.assertEquals("Images", results.getHeader().get(4));
		try{
			Assert.assertTrue(87<results.getResults().size());
		}catch (AssertionError e) {
			System.out.println("Fail: only " + results.getResults().size() + " results returned, there should be 88+ results");
			throw new AssertionError(e);
		}
	
		System.out.println(GeppettoSerializer.serializeToJSON(results, true));

	}

	@Test
	public void testNANDedQueries() throws GeppettoInitializationException, GeppettoVisitingException, GeppettoDataSourceException, GeppettoModelException, IOException
	{

		GeppettoModel model = GeppettoModelReader.readGeppettoModel(VFBQueryTest.class.getClassLoader().getResource("VFBModel/GeppettoModelVFB.xmi"));
		model.getLibraries().add(SharedLibraryManager.getSharedCommonLibrary());

		GeppettoModelAccess geppettoModelAccess = new GeppettoModelAccess(model);

		Neo4jDataSourceService neo4JDataSource = new Neo4jDataSourceService();
		neo4JDataSource.initialize(model.getDataSources().get(0), geppettoModelAccess);

		AberOWLDataSourceService aberDataSource = new AberOWLDataSourceService();
		aberDataSource.initialize(model.getDataSources().get(1), geppettoModelAccess);

		OWLeryDataSourceService owleryDataSource = new OWLeryDataSourceService();
		owleryDataSource.initialize(model.getDataSources().get(2), geppettoModelAccess);
		
        //Build list of available query indexs against ids:
        Map<String,Integer> avQ = new HashMap();
        Integer i = 0;
        for (Query query : model.getQueries()) {
            String q = query.getId();
            System.out.println("Query #" + Integer.toString(i) + ", id:" + q);
            if (avQ.containsKey(q)) {
                System.out.println("Duplicate query id: " + q);
            } else {
                avQ.put(q, i);
            }
            i++;
        }
        
        System.out.println(GeppettoSerializer.serializeToJSON(model, false));

		neo4JDataSource.fetchVariable("FBbt_00003748");
		neo4JDataSource.fetchVariable("FBbt_00045048");
		
		System.out.println(GeppettoSerializer.serializeToJSON(model, false));
			
   		Variable variable1 = geppettoModelAccess.getPointer("FBbt_00003748").getElements().get(0).getVariable();
		Variable variable2 = geppettoModelAccess.getPointer("FBbt_00045048").getElements().get(0).getVariable();

		EList<RunnableQuery> runnableQueriesEMF = new BasicEList<RunnableQuery>();

		RunnableQuery rqEMF1 = DatasourcesFactory.eINSTANCE.createRunnableQuery();
		rqEMF1.setQueryPath(model.getQueries().get(avQ.get("partsof")).getPath());
		rqEMF1.setTargetVariablePath(variable1.getPath());
		runnableQueriesEMF.add(rqEMF1);
		
		RunnableQuery rqEMF2 = DatasourcesFactory.eINSTANCE.createRunnableQuery();
		rqEMF2.setQueryPath(model.getQueries().get(avQ.get("neuronsparthere")).getPath());
		rqEMF2.setTargetVariablePath(variable2.getPath());
		rqEMF2.setBooleanOperator(BooleanOperator.NAND);
		runnableQueriesEMF.add(rqEMF2);
		
		int count = owleryDataSource.getNumberOfResults(runnableQueriesEMF);
		try{
			Assert.assertTrue(83<count);
		}catch (AssertionError e) {
			System.out.println("Fail: only " + count + " results returned, there should be 84+ results");
			throw new AssertionError(e);
		}
			
		QueryResults results = owleryDataSource.execute(runnableQueriesEMF);

		Assert.assertEquals("ID", results.getHeader().get(0));
		Assert.assertEquals("Name", results.getHeader().get(1));
		Assert.assertEquals("Definition", results.getHeader().get(2));
		Assert.assertEquals("Type", results.getHeader().get(3));
		Assert.assertEquals("Images", results.getHeader().get(4));
		Assert.assertTrue(results.getResults().size() > 80);

		System.out.println(GeppettoSerializer.serializeToJSON(results, true));

	}


}
