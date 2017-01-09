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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.datasources.AQueryResult;
import org.geppetto.model.datasources.DataSource;
import org.geppetto.model.datasources.DatasourcesFactory;
import org.geppetto.model.datasources.ProcessQuery;
import org.geppetto.model.datasources.QueryResult;
import org.geppetto.model.datasources.QueryResults;
import org.geppetto.model.datasources.SerializableQueryResult;
import org.geppetto.model.variables.Variable;

/**
 * @author matteocantarelli
 *
 */
public class VFBAberOWLQueryProcessor extends AQueryProcessor
{

	private Map<String, Object> processingOutputMap = new HashMap<String, Object>();

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.geppetto.core.datasources.IQueryProcessor#process(org.geppetto.model.ProcessQuery, org.geppetto.model.variables.Variable, org.geppetto.model.QueryResults)
	 */
	@Override
	public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException
	{
		if(results == null)
		{
			throw new GeppettoDataSourceException("Results input to " + query.getName() + "is null");
		}
		QueryResults processedResults = DatasourcesFactory.eINSTANCE.createQueryResults();
		int idIndex = results.getHeader().indexOf("remainder");
		int descirptionIndex = results.getHeader().indexOf("definition");
		int nameIndex = results.getHeader().indexOf("label");

		processedResults.getHeader().add("ID");
		processedResults.getHeader().add("Name");
		processedResults.getHeader().add("Definition");

		List<String> ids = new ArrayList<String>();
		for(AQueryResult result : results.getResults())
		{
			try{
				SerializableQueryResult processedResult = DatasourcesFactory.eINSTANCE.createSerializableQueryResult();
				String id = ((QueryResult) result).getValues().get(idIndex).toString();
				processedResult.getValues().add(id);

				processedResult.getValues().add(((List<String>) ((QueryResult) result).getValues().get(nameIndex)).get(0));
				ids.add("'" + id + "'");
				Object value = ((QueryResult) result).getValues().get(descirptionIndex);
				processedResult.getValues().add(value == null ? "" : value.toString());
				processedResults.getResults().add(processedResult);
			} catch (ClassCastException e) {
				System.out.println("Error handling aberOwl result: " + result.getEString());
				System.out.println("ClassCastException: " + e.getMessage());
			}
		}

		processingOutputMap.put("ARRAY_ID_RESULTS", ids);

		return processedResults;
	}

	@Override
	public Map<String, Object> getProcessingOutputMap()
	{
		return processingOutputMap;
	}

}
