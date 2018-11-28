package uk.ac.vfb.geppetto;

import org.geppetto.core.datasources.GeppettoDataSourceException;
import org.geppetto.core.datasources.QueryChecker;
import org.geppetto.core.model.GeppettoModelAccess;
import org.geppetto.core.model.GeppettoSerializer;
import org.geppetto.datasources.AQueryProcessor;
import org.geppetto.model.GeppettoLibrary;
import org.geppetto.model.GeppettoPackage;
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
import org.geppetto.model.values.ArrayElement;
import org.geppetto.model.values.ArrayValue;
import org.geppetto.model.values.HTML;
import org.geppetto.model.values.Image;
import org.geppetto.model.values.ImageFormat;
import org.geppetto.model.values.Text;
import org.geppetto.model.values.ValuesFactory;
import org.geppetto.model.variables.Variable;
import org.geppetto.model.variables.VariablesFactory;
import org.geppetto.core.model.GeppettoSerializer;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import org.apache.commons.lang.StringUtils;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * @author RobertCourt
 */
public class VFBProcessTermInfoCore extends AQueryProcessor {

	/*
	 * (non-Javadoc)
	 *
	 * @see org.geppetto.core.datasources.IQueryProcessor#process(org.geppetto.model.ProcessQuery, org.geppetto.model.variables.Variable, org.geppetto.model.QueryResults)
	 */
	@Override
	public QueryResults process(ProcessQuery query, DataSource dataSource, Variable variable, QueryResults results, GeppettoModelAccess geppettoModelAccess) throws GeppettoDataSourceException {

		//	Populating passed variable with Core Term Info 
		//      ID: short_form
		String tempId = "xxxxx";
		//	Label: label
		String tempName = "not found";

		System.out.println("Creating Variable for: " + String.valueOf(variable.getId()));

		try {
			Type textType = geppettoModelAccess.getType(TypesPackage.Literals.TEXT_TYPE);
			Type htmlType = geppettoModelAccess.getType(TypesPackage.Literals.HTML_TYPE);
			Type imageType = geppettoModelAccess.getType(TypesPackage.Literals.IMAGE_TYPE);

			// term
			if (results.getValue("term", 0) != null) {
				Map<String, Object> term = (Map<String, Object>) results.getValue("term", 0);
				//core
				if (term.getValue("core", 0) != null) {
					Map<String, Object> core = (Map<String, Object>) term.getValue("core", 0);
					//ID/short_form
					if (core.get("short_form") != null) {
						if (String.valueOf(variable.getId()).equals((String) core.get("short_form"))) {
							tempId = (String) core.get("short_form");
						} else {
							System.out.println("ERROR: Called ID: " + String.valueOf(variable.getId()) + " does not match returned ID: " + (String) core.get("short_form"));
							tempId = (String) core.get("short_form");
						}
					}
					//label
					if (core.get("label") != null) {
						tempName = (String) core.get("label");
					}
					// add label to variable
					geppettoModelAccess.setObjectAttribute(variable, GeppettoPackage.Literals.NODE__NAME, tempName);
					// add parent composite type
					CompositeType parentType = TypesFactory.eINSTANCE.createCompositeType();
					// set ID of parent 
					parentType.setId(tempId);
					// add to variable
					variable.getAnonymousTypes().add(parentType);
					
					// Create new child composite variable & type for term info data to be stored in
					Variable metaDataVar = VariablesFactory.eINSTANCE.createVariable();
					CompositeType metaDataType = TypesFactory.eINSTANCE.createCompositeType();
					metaDataVar.getTypes().add(metaDataType);
					metaDataVar.setId(tempId + "_meta");
					metaDataType.setId(tempId + "_metadata");
					metaDataType.setName("Info");
					metaDataVar.setName(tempName);
					geppettoModelAccess.addVariableToType(metaDataVar, parentType);
					
					// Label: {label} ({short_form}) (all on one line)
					String labelLink = "<b>" + tempName + "</b> (" + tempId + ")";
					// set meta label/name:
					Variable label = VariablesFactory.eINSTANCE.createVariable();
					label.setId("label");
					label.setName("Label");
					label.getTypes().add(htmlType);
					HTML labelValue = ValuesFactory.eINSTANCE.createHTML();
					label.getInitialValues().put(htmlType, labelValue);
					labelValue.setHtml(labelLink);
					geppettoModelAccess.addVariableToType(label, metaDataType);
					
					geppettoModelAccess.addTypeToLibrary(metaDataType, dataSource.getTargetLibrary());
					
					if (core.getValue("types", 0) != null) {
						List<String> supertypes = (List<String>) core.getValue("types", 0);

						for (String supertype : supertypes) {
							if (!supertype.startsWith("_")) { // ignore supertypes starting with _
								parentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType(supertype, dependenciesLibrary));
								superTypes += supertype + ", ";
							}
						}
					} else {
						parentType.getSuperType().add(geppettoModelAccess.getOrCreateSimpleType("Orphan", dependenciesLibrary));
					}
				} else {
					System.out.println("Error core not returned for: " + String.valueOf(variable.getId()));
				}
			} else {
				System.out.println("Error term not returned for: " + String.valueOf(variable.getId()));
			}

		} catch (GeppettoVisitingException e) {
			System.out.println("Error creating metadata: " + e.toString());
			e.printStackTrace();
			throw new GeppettoDataSourceException(e);
		}

		return results;
	}

}
