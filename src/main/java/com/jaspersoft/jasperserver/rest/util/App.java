package com.jaspersoft.jasperserver.rest.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;

import com.jaspersoft.jasperserver.dto.authority.ClientTenant;
import com.jaspersoft.jasperserver.dto.resources.ClientAdhocDataView;
import com.jaspersoft.jasperserver.dto.resources.ClientFile;
import com.jaspersoft.jasperserver.dto.resources.ClientFile.FileType;
import com.jaspersoft.jasperserver.dto.resources.ClientReference;
import com.jaspersoft.jasperserver.dto.resources.ClientReferenceableDataSource;
import com.jaspersoft.jasperserver.dto.resources.ClientReportUnit;
import com.jaspersoft.jasperserver.dto.resources.ClientResource;
import com.jaspersoft.jasperserver.dto.resources.ClientResourceListWrapper;
import com.jaspersoft.jasperserver.dto.resources.ClientResourceLookup;
import com.jaspersoft.jasperserver.dto.resources.ClientSemanticLayerDataSource;
import com.jaspersoft.jasperserver.jaxrs.client.apiadapters.resources.BatchResourcesAdapter;
import com.jaspersoft.jasperserver.jaxrs.client.apiadapters.resources.ResourceSearchParameter;
import com.jaspersoft.jasperserver.jaxrs.client.apiadapters.resources.ResourceSearchResponseHeader;
import com.jaspersoft.jasperserver.jaxrs.client.core.JasperserverRestClient;
import com.jaspersoft.jasperserver.jaxrs.client.core.RestClientConfiguration;
import com.jaspersoft.jasperserver.jaxrs.client.core.Session;
import com.jaspersoft.jasperserver.jaxrs.client.core.exceptions.ResourceNotFoundException;
import com.jaspersoft.jasperserver.jaxrs.client.core.operationresult.OperationResult;

import net.sf.jasperreports.engine.JRExpression;
import net.sf.jasperreports.engine.JRPropertiesMap;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.xml.JRXmlWriter;

/**
 * Update the reference/URI :
 * 		- the data source of a topic or domain to the new DS
 * 		- domain/topic of an ad hoc view to the new domain/topic
 * 		- reports based on an adhoc view to the new ad hoc view
 * 
 * Args:
 * 
 * configFile				a "properties" file that contains all the parameters below as key=value per line
 *  
 * URL						defaults to http://localhost:8080/jasperserver-pro
 * connectionConfigFile		see https://github.com/Jaspersoft/jrs-rest-java-client Configuration
 * 							overrides URL
 * 
 * username					Authentication against repo. defaults to jasperadmin
 * password					". defaults to jasperadmin
 * organization				". does not default
 * 
 * mode						updateDataReferences
 * 			folderToSearch like		"/";
 * 			origRoot like			"/organizations/Base_org_to_move/adhoc";
 * 			newRoot like			"/public/adhoc";
 * 
 * mode						createOrgs
 * 			orgFileName
 * 				properties file containing: original org folder and destination org id (and alias), name, description
 * 				org id can be hierarchical with /s, in which case the parent orgs will be created as necessary
 * 				and the id and alias will be the id with underscores
 * 
 * 				ie. /public/ABC=ABC|ABC Corporation|ABC Corporation
 * 					/public/XYZ/LMN=XYZ/LMN|ABC Corporation|ABC Corporation
 * 			optional
 * 			origRoot like		"/organizations/Base_org_to_move/adhoc";
 * 			newRoot like	"/public/adhoc";
 */
public class App 
{
	static Logger log = Logger.getLogger(App.class);
	//static String folderToSearch;
	//static String origAdhocRoot;
	//static String newBaseAdhocRoot;
	public static String DEFAULT_JRS_URL = "http://localhost:8080/jasperserver-pro";
	public static String DEFAULT_JRS_USERNAME = "jasperadmin";
	public static String DEFAULT_JRS_PASSWORD = "jasperadmin";

	
    public static void main( String[] args )
    {
    	Properties argMap;
    	try {
			argMap = getArgs(args);
		} catch (Exception e1) {
			log.error("getArgs fail", e1);
			return;
		}
    	
    	if (argMap.containsKey("configFile")) {
    		argMap = getProperties(argMap.getProperty("configFile"));
    		if (argMap.isEmpty()) {
    			log.error("empty configFile");
    			return;
    		}
    	}

    	JasperserverRestClient client;
    	RestClientConfiguration configuration = null;
    	
    	if (argMap.containsKey("connectionConfigFile")) {
   			configuration = RestClientConfiguration.loadConfiguration(argMap.getProperty("connectionConfigFile"));
   			log.info("using server connection config file: " + argMap.getProperty("connectionConfigFile"));
    	} else if (argMap.containsKey("URL") ){
    		configuration = new RestClientConfiguration(argMap.getProperty("URL"));
    		log.info("using server URL in args: " + argMap.getProperty("URL"));
    	} else {
    		configuration = new RestClientConfiguration(DEFAULT_JRS_URL);
    		log.info("using default URL: " + DEFAULT_JRS_URL);
    	}
    	
    	client = new JasperserverRestClient(configuration);
    	Session session = null;
    	
    	try {
    	
    		String username = argMap.containsKey("username") ? argMap.getProperty("username") : "jasperadmin";
    		if (argMap.containsKey("organization")) {
    			username += "|" + argMap.getProperty("organization");
    		}
    		
    		log.info("Accessing " + configuration.getJasperReportsServerUrl() + " with user: " + username);
    		
    		session = client.authenticate(username, argMap.containsKey("password") ? argMap.getProperty("password") : "jasperadmin");
    
    		if (argMap.containsKey("mode")) {
    			String mode = argMap.getProperty("mode");
    			if (mode.equalsIgnoreCase("updateDataReferences")) {
    				updateDataReferencesSearchAll(session, argMap);
    			} else if (mode.equalsIgnoreCase("createOrgs")) {
    				createOrgs(session, argMap);
    			}
    		} else {
    			// assume updateDataReferences
				updateDataReferencesSearchAll(session, argMap);
    		}
    	log.info("done");
    	} catch (Exception e) {
    		log.error("top level error", e);
    	} finally {
    		if (session != null) session.logout();
    	}
    }

    /*
     *  arg orgFileName
	 * 				properties file containing: original org folder and destination org id (and alias), name, description
	 * 				org id can be hierarchical with /s, in which case the parent orgs will be created as necessary
	 * 				and the id and alias will be the id with underscores
	 * 
	 * 				ie. /public/ABC=ABC|ABC Corporation|ABC Corporation
	 * 					/public/XYZ/LMN=XYZ/LMN|ABC Corporation|ABC Corporation

     */
    private static void createOrgs(Session session, Properties argMap) throws Exception {
		if (!argMap.containsKey("organizationFileName")) {
			log.info("no organizationFileName given: exiting");
			return;
		}
		
		Properties orgProperties = getProperties(argMap.getProperty("organizationFileName"));
		if (orgProperties.isEmpty()) {
			return;
		}
		
		for (Entry<Object, Object> entry: orgProperties.entrySet()) {
			String fromFolder = entry.getKey().toString();
			String toOrg = entry.getValue().toString();
			processOrg(session, argMap, fromFolder, toOrg);
		}
	}
 
	private static void processOrg(Session session, Properties argMap, String fromFolder, String toOrg) throws Exception {
		/*
		 * 	org id can be hierarchical with /s, in which case the parent orgs will be created as necessary
		 *  and the id and alias will be the id with underscores
		 *	ie. /public/ABC=ABC|ABC Corporation|ABC Corporation
		 * 		/public/XYZ/LMN=XYZ/LMN|ABC Corporation|ABC Corporation
		 */
		
		// parse org info
		String[] orgParts = toOrg.split("\\|");
		if (orgParts.length != 3) {
			log.info(toOrg + " does not have 3 sections. " + orgParts.length);
			return;
		}
		String idAlias = orgParts[0];
		String name = orgParts[1];
		String description = orgParts[2];
		
		String[] orgHierarchy = idAlias.split("/");
		
		// create/update org if needed
		log.info("Processing org: " + idAlias + " to " + fromFolder);
		
		ClientTenant org = findOrCreateOrg(session, orgHierarchy, name, description);
		
		// copy resources from fromFolder to new org
		
		moveResources(session, fromFolder, orgHierarchy);
		
		/*
		 * if we have these properties, update references
		 * 
		 * origAdhocRoot=/organizations/Base_org_to_move/adhoc
		 * newBaseAdhocRoot=/public/adhoc
		 */
		log.info(org);
		argMap.put("folderToSearch", org.getTenantFolderUri());
		updateDataReferences(session, argMap);
		
	}

	private static ClientTenant findOrCreateOrg(Session session, String[] orgHierarchy, String name, String description) throws Exception {
		
		String parentOrgId = null;
		// make sure the parent is there. Otherwise create it.
		// this gets around ordering problems when dealing with a Properties object
		if (orgHierarchy.length > 1) {
			String[] parentOrgHierarchy = Arrays.copyOfRange(orgHierarchy, 0, orgHierarchy.length - 1);
			
			ClientTenant parentOrg = findOrg( session, parentOrgHierarchy );
			
			if (parentOrg == null) {
				parentOrgId = orgId(parentOrgHierarchy);
				parentOrg = findOrCreateOrg(session, parentOrgHierarchy,
						parentOrgId,
						parentOrgId);
				
				// still null? no way!
				if (parentOrg == null) {
					throw new Exception("didn't create org: " + parentOrgId);
				}
				parentOrgId = parentOrg.getId();
			}
		}
		
		String orgId = orgId(orgHierarchy);
		ClientTenant org = findOrg(session, orgHierarchy);
		
		if (org == null) {
			log.info("Creating org: " + orgId);
			ClientTenant organization = new ClientTenant();
			organization.setId(orgId);
			organization.setAlias(orgId);
			if (parentOrgId != null)
				organization.setParentId(parentOrgId);
			organization.setTenantName(name);
			organization.setTenantDesc(description);

			OperationResult<ClientTenant> createdOrg = session
			        .organizationsService()
			        .organization(organization)
			        .create();
			if (Status.Family.familyOf(createdOrg.getResponseStatus()) == Status.Family.SUCCESSFUL) {
				// get the full org, to get the filled in values like Uri
				org = session
				        .organizationsService()
				        .organization(organization)
				        .get()
				        .getEntity();
			} else {
				org = null;
			}
		}
		return org;
		
	}
	
	private static ClientTenant findOrg(Session session, String[] orgHierarchy) {
		ClientTenant organization = new ClientTenant();
		
		organization.setId(orgId(orgHierarchy));
		OperationResult<ClientTenant> findOrg = null;
		try {
			findOrg = session
		        .organizationsService()
		        .organization(organization)
		        .get();
		} catch (ResourceNotFoundException e) {
			return null;
		}
		if (Status.Family.familyOf(findOrg.getResponseStatus()) == Status.Family.SUCCESSFUL) {
			log.info("Found org: " + orgId(orgHierarchy));
			return findOrg.getEntity();
		} else {
			return null;
		}
	}
	
	private static String orgId(String[] orgHierarchy) {
		// will give [par1, par2, org]
		String orgId = Arrays.toString(orgHierarchy);
		log.debug("Converting orgHierarchy: " + orgId);

		// strip off the []
		// convert ", " to "_
		// to get par1_par2_org
		return orgId.replace("[","").replace("]","").replace(", ","_");
		
	}

	private static void moveResources(Session session, String fromFolder, String[] orgHierarchy) throws Exception {
		ClientTenant org = findOrg(session, orgHierarchy);
		if (org == null) {
			return;
		}
		
		String destFolder = org.getTenantFolderUri();
		
		BatchResourcesAdapter searchRequest = getFolderContentsSearchRequest(session, fromFolder);
		
		/*
		 * OperationResult<ClientResource> result = session
        .resourcesService()
        .resource("/reports")
        .copyFrom("/datasources/testFolder");
        
        OperationResult<ClientResource> result = session
        .resourcesService()
        .resource("/datasources")
        .moveFrom("/reports/testFolder");
        
		 */
    	
    	OperationResult<ClientResourceListWrapper> searchResult = searchRequest.search();
    	
    	Response response = searchResult.getResponse();
    	Boolean done = Boolean.FALSE;
    	Integer lastOffset = 0;
    	Integer totalCount = 0;
    	Integer pageSize = 100; // the default
    	
    	while (!done) {
	    	if (Status.Family.familyOf(response.getStatus()) == Status.Family.SUCCESSFUL) {
	    		// only happens on first request
	    		if (response.getHeaderString(ResourceSearchResponseHeader.TOTAL_COUNT.getName()) != null) {
	    			totalCount = Integer.parseInt(response.getHeaderString(ResourceSearchResponseHeader.TOTAL_COUNT.getName()));
	    			log.debug("Approximate total result count: "  + totalCount);
	    			// if no results, stop
	    			if (totalCount == 0) {
	    				break;
	    			}
	    		}

		    	// get the next batch if something is there
	    		Integer startIndex = Integer.parseInt(response.getHeaderString(ResourceSearchResponseHeader.START_INDEX.getName())); 
	    	    Integer resultCount = Integer.parseInt(response.getHeaderString(ResourceSearchResponseHeader.RESULT_COUNT.getName()));
	    	    
	    	    log.debug("start index : " + startIndex);
	    	    log.debug("result count : " + resultCount);

	    	    //processResult(searchResult.getEntity(), session, folderToSearch, origAdhocRoot, newBaseAdhocRoot);

	    	    moveResources(searchResult.getEntity(), session, destFolder);
	    	    
	    	    Integer nextOffset = startIndex + resultCount;
				/*
				 * nextOffset in header only works for forceFullPage = true
				 * response.getHeaderString(ResourceSearchResponseHeader.NEXT_OFFSET.getName());
				 */
	    		
	    		if (resultCount == pageSize) {
	    			if (lastOffset < nextOffset) {
		    			log.debug("Next batch from offset: " + nextOffset);
		    			searchRequest = getFolderContentsSearchRequest(session, fromFolder)
		    	    	        .parameter(ResourceSearchParameter.OFFSET, nextOffset.toString());
		    			searchResult = searchRequest.search();
		    			response = searchResult.getResponse();
		    			lastOffset = nextOffset;
	    			} else {
	    				log.info("Error: next offset: " + nextOffset + " not beyond lastOffset: " + lastOffset);
		    			done = Boolean.TRUE;
	    			}
	    		} else {
	    			done = Boolean.TRUE;
	    		}
	    	} else {
	    		log.info("Search failed with response code: " + response.getStatus());
	    		done = Boolean.TRUE;
	    	}
    	}
		
	}

	private static void moveResources(ClientResourceListWrapper resourceListWrapper, Session session, String destFolder)
			throws Exception {
    	
    	if (resourceListWrapper.getResourceLookups().isEmpty()) {
    		log.info("No results to move");
    		return;
    	} else {
    		log.info("Moving " + resourceListWrapper.getResourceLookups().size() + " resources");
    	}
    	
    	// /public/adhoc/topics
    	// /organizations/organization_1/reports/Orders_Ad_Hoc_View
    	
    	Iterator<ClientResourceLookup> resourceListIterator = resourceListWrapper.getResourceLookups().iterator();
    	
    	while (resourceListIterator.hasNext()) {
    		ClientResourceLookup resourceLookup = resourceListIterator.next();
    		try {
	    	    OperationResult<ClientResource> moveResult = session
	    	            .resourcesService()
	    	            .resource(destFolder)
	    	            .moveFrom(resourceLookup.getUri());
	    	    if (Status.Family.familyOf(moveResult.getResponse().getStatus()) != Status.Family.SUCCESSFUL) {
	    	    	throw new Exception("unsuccessful move of: " + resourceLookup.getUri()  + " to " + destFolder);
	    	    }
    		} catch (Exception e) {
    			log.info("Resource move exception", e);
    			throw e;
    		}
    	}

	}

	/*
	private static void updateResources(Session session, String origAdhocRoot, String newBaseAdhocRoot) {
		*
		 * origAdhocRoot=/organizations/Base_org_to_move/adhoc
		 * newBaseAdhocRoot=/public/adhoc
		 *
		if (origAdhocRoot == null || newBaseAdhocRoot == null) {
			return;
		}
	}
	*/
	
	public static BatchResourcesAdapter getDataDependenciesSearchRequest(Session session, String folder) {
    	return session
    	        .resourcesService()
    	        .resources()
    	        .parameter(ResourceSearchParameter.FOLDER_URI, folder)
    	        .parameter(ResourceSearchParameter.SHOW_HIDDEN_ITEMS, "true")
    	        .parameter(ResourceSearchParameter.TYPE, "reportUnit")
    			.parameter(ResourceSearchParameter.TYPE, "adhocDataView")
    			.parameter(ResourceSearchParameter.TYPE, "semanticLayerDataSource")
    			.parameter(ResourceSearchParameter.TYPE, "file")
    			;

    }

	public static BatchResourcesAdapter getFolderContentsSearchRequest(Session session, String folder) {
    	return session
    	        .resourcesService()
    	        .resources()
    	        .parameter(ResourceSearchParameter.FOLDER_URI, folder)
    	        .parameter(ResourceSearchParameter.RECURSIVE, "false");

    }

    public static void processResult(ClientResourceListWrapper resourceListWrapper, Session session, String folder,
    		String origRoot, String newRoot)
    		throws Exception {
    	
    	if (resourceListWrapper.getResourceLookups().isEmpty()) {
    		log.info("No results");
    		return;
    	} else {
    		log.info("Processing " + resourceListWrapper.getResourceLookups().size() + " resources");
    	}
    	
    	// /public/adhoc/topics
    	// /organizations/organization_1/reports/Orders_Ad_Hoc_View
    	
    	Iterator<ClientResourceLookup> resourceListIterator = resourceListWrapper.getResourceLookups().iterator();
    	
    	while (resourceListIterator.hasNext()) {
    		ClientResourceLookup resourceLookup = resourceListIterator.next();
    		log.info("Processing " + resourceLookup.getUri());
	    		    	
	    	/*
	    	 * Will see jndiJdbcDataSource and folder
	    	 * Types of interest are:
	    	 * reportUnit
	    	 * 		data source in topics will be a data source
	    	 * 		data source in ad hoc reports will be an ad hoc view?  
	    	 * adhocDataView
	    	 * 		data source in topics will be a data source
	    	 * 		data source in ad hoc reports will be a topic?  
	    	 * domain
	    	 * 		data source in domains will be a data source
	    	 */
    		
    		try {
    			if (resourceLookup.getUri().startsWith(folder)) {
			    	if (resourceLookup.getResourceType().equalsIgnoreCase("reportUnit")) {
				    	updateReportUnit(resourceLookup, session, origRoot, newRoot);
				    } else if (resourceLookup.getResourceType().equalsIgnoreCase("adhocDataView")) {
				    	updateAdhocDataView(resourceLookup, session, origRoot, newRoot);
			    	} else if (resourceLookup.getResourceType().equalsIgnoreCase("semanticLayerDataSource")) {
			    		updateSemanticLayerDataSource(resourceLookup, session, origRoot, newRoot);
			    	} else if (resourceLookup.getResourceType().equalsIgnoreCase("file")) {
			    		updateJRXMLFile(resourceLookup, session, origRoot, newRoot);
			    	} else {
		    			log.info("Ignoring " + resourceLookup.getUri() + " because " + resourceLookup.getResourceType() + " is not a focus");
			    	}
	    		} else {
	    			log.info("Ignoring " + resourceLookup.getUri() + " because it is not in the target folder");
	    		}
    		} catch (Exception e) {
    			log.info("Resource update exception", e);
    			throw e;
    		}
    	}

    }
    
    public static void updateDataReferences(Session session, Properties argMap) throws Exception {
    	/*
    	 * Update Public ad hoc resources that were in an /organization folder
    	 * Assume that the resources have been moved to /public/adhoc and we are updating them there
    	 */
    	
    	// Need to parameterize these from command line
    	
    	String folderToSearch = null;
    	String origRoot = null;
    	String newRoot = null;
    	
    	if (argMap.containsKey("folderToSearch"))
    		folderToSearch = argMap.getProperty("folderToSearch");
    	
    	if (argMap.containsKey("origRoot"))
    		origRoot = argMap.getProperty("origRoot");
    	
    	if (argMap.containsKey("newRoot"))
    		newRoot = argMap.getProperty("newRoot");
    	
		if (folderToSearch == null || origRoot == null || newRoot == null) {
			log.info("Stopping updateDataReferences. Missing values: folderToSearch: " + folderToSearch + 
					", origRoot: " + origRoot + 
					", newRoot: " + newRoot);
			return;
		}

    	// get everything in the new adhoc area
    	
    	BatchResourcesAdapter searchRequest = getDataDependenciesSearchRequest(session, folderToSearch);
    	
    	log.info("Searching for report units and ad hoc views in: " + folderToSearch);
    	log.info("Converting references from: " + origRoot + " to " + newRoot);
    	
    	OperationResult<ClientResourceListWrapper> searchResult = searchRequest.search();
    	
    	Response response = searchResult.getResponse();
    	Boolean done = Boolean.FALSE;
    	Integer lastOffset = 0;
    	Integer totalCount = 0;
    	Integer pageSize = 100; // the default
    	
    	while (!done) {
	    	if (Status.Family.familyOf(response.getStatus()) == Status.Family.SUCCESSFUL) {
	    		// only happens on first request
	    		if (response.getHeaderString(ResourceSearchResponseHeader.TOTAL_COUNT.getName()) != null) {
	    			totalCount = Integer.parseInt(response.getHeaderString(ResourceSearchResponseHeader.TOTAL_COUNT.getName()));
	    			log.debug("Approximate total result count: "  + totalCount);
	    			// if no results, stop
	    			if (totalCount == 0) {
	    				break;
	    			}
	    		}

		    	// get the next batch if something is there
	    		Integer startIndex = Integer.parseInt(response.getHeaderString(ResourceSearchResponseHeader.START_INDEX.getName())); 
	    	    Integer resultCount = Integer.parseInt(response.getHeaderString(ResourceSearchResponseHeader.RESULT_COUNT.getName()));
	    	    
	    	    log.debug("start index : " + startIndex);
	    	    log.debug("result count : " + resultCount);

	    	    processResult(searchResult.getEntity(),  session, folderToSearch, origRoot, newRoot);

	    	    Integer nextOffset = startIndex + resultCount;
				/*
				 * nextOffset in header only works for forceFullPage = true
				 * response.getHeaderString(ResourceSearchResponseHeader.NEXT_OFFSET.getName());
				 */
	    		
	    		if (resultCount == pageSize) {
	    			if (lastOffset < nextOffset) {
		    			log.debug("Next batch from offset: " + nextOffset);
		    			searchRequest = getDataDependenciesSearchRequest(session, folderToSearch)
		    	    	        .parameter(ResourceSearchParameter.OFFSET, nextOffset.toString());
		    			searchResult = searchRequest.search();
		    			response = searchResult.getResponse();
		    			lastOffset = nextOffset;
	    			} else {
	    				log.info("Error: next offset: " + nextOffset + " not beyond lastOffset: " + lastOffset);
		    			done = Boolean.TRUE;
	    			}
	    		} else {
	    			done = Boolean.TRUE;
	    		}
	    	} else {
	    		log.info("Search failed with response code: " + response.getStatus());
	    		done = Boolean.TRUE;
	    	}
    	}

    }
    
    public static void updateDataReferencesSearchAll(Session session, Properties argMap) throws Exception {
    	/*
    	 * Update Public ad hoc resources that were in an /organization folder
    	 * Assume that the resources have been moved to /public/adhoc and we are updating them there
    	 */
    	
    	// Need to parameterize these from command line
    	
    	String folderToSearch = null;
    	String origRoot = null;
    	String newRoot = null;
    	Boolean excludePublicFolder = false;
    	
    	if (argMap.containsKey("folderToSearch"))
    		folderToSearch = argMap.getProperty("folderToSearch");
    	
    	if (argMap.containsKey("origRoot"))
    		origRoot = argMap.getProperty("origRoot");
    	
    	if (argMap.containsKey("newRoot"))
    		newRoot = argMap.getProperty("newRoot");
    	
    	if (argMap.containsKey("excludePublicFolder"))
    		excludePublicFolder = Boolean.valueOf(argMap.getProperty("excludePublicFolder"));
    	
		if (folderToSearch == null || origRoot == null || newRoot == null) {
			log.info("Stopping updateDataReferences. Missing values: folderToSearch: " + folderToSearch + 
					", origRoot: " + origRoot + 
					", newRoot: " + newRoot);
			return;
		}
		
		OperationResult<ClientResourceListWrapper> result = session
	    	        .resourcesService()
	    	        .resources()
	    	        .parameter(ResourceSearchParameter.FOLDER_URI, folderToSearch)
	    	        .parameter(ResourceSearchParameter.SHOW_HIDDEN_ITEMS, "true")
	    	        .parameter(ResourceSearchParameter.TYPE, "reportUnit")
	    			.parameter(ResourceSearchParameter.TYPE, "adhocDataView")
	    			.parameter(ResourceSearchParameter.TYPE, "semanticLayerDataSource")
	    			.parameter(ResourceSearchParameter.TYPE, "file")
	    			.searchAll();
    	
    	if (result.getEntity().getResourceLookups().isEmpty()) {
    		log.info("No results");
    		return;
    	} else {
    		log.info("Processing " + result.getEntity().getResourceLookups().size() + " resources");
    	}

    	Integer count = 0;
		for (ClientResourceLookup resourceLookup : result.getEntity().getResourceLookups()) {
			
			if (excludePublicFolder && resourceLookup.getUri().startsWith("/public")) {
    			log.debug("Ignoring " + resourceLookup.getUri() + " because it is in /public");
				continue;
			}
			count++;
    		log.debug("Processing " + resourceLookup.getUri());
	    	
	    	/*
	    	 * Will see jndiJdbcDataSource and folder
	    	 * Types of interest are:
	    	 * reportUnit
	    	 * 		data source in topics will be a data source
	    	 * 		data source in ad hoc reports will be an ad hoc view?  
	    	 * adhocDataView
	    	 * 		data source in topics will be a data source
	    	 * 		data source in ad hoc reports will be a topic?  
	    	 * domain
	    	 * 		data source in domains will be a data source
	    	 */
    		
    		try {
    			if (resourceLookup.getUri().startsWith(folderToSearch)) {
			    	if (resourceLookup.getResourceType().equalsIgnoreCase("reportUnit")) {
				    	updateReportUnit(resourceLookup, session, origRoot, newRoot);
				    } else if (resourceLookup.getResourceType().equalsIgnoreCase("adhocDataView")) {
				    	updateAdhocDataView(resourceLookup, session, origRoot, newRoot);
			    	} else if (resourceLookup.getResourceType().equalsIgnoreCase("semanticLayerDataSource")) {
			    		updateSemanticLayerDataSource(resourceLookup, session, origRoot, newRoot);
			    	} else if (resourceLookup.getResourceType().equalsIgnoreCase("file")) {
			    		updateJRXMLFile(resourceLookup, session, origRoot, newRoot);
			    	} else {
		    			log.debug("Ignoring " + resourceLookup.getUri() + " because " + resourceLookup.getResourceType() + " is not a focus");
			    	}
	    		} else {
	    			log.debug("Ignoring " + resourceLookup.getUri() + " because it is not in the target folder");
	    		}
    		} catch (Exception e) {
    			log.error("Resource update exception. resource # in list: " + count, e);
    			throw e;
    		}
			
		}
		log.info("Processed " + result.getEntity().getResourceLookups().size() + " resources");
    }
    
    public static void updateReportUnit(ClientResourceLookup rl, Session session,
    		String origRoot, String newRoot) throws Exception {

    	OperationResult<ClientResource> resourceResult = session
	    		.resourcesService()
		        .resource(rl.getUri())
		        .details();
    	
    	ClientReportUnit ru = (ClientReportUnit) resourceResult.getEntity();
	    // update the resource
		ClientReferenceableDataSource ds = ru.getDataSource();

		log.debug(ru.toString());

		if (ds != null  && ds.getUri().startsWith(origRoot)) {
			log.info(rl.getUri() + " : " + rl.getResourceType());
			String newDSURI = newRoot + ds.getUri().substring(origRoot.length());
			log.info("\tOrig RU DS URI " + ds.getUri() + ", New RU DS URI " + newDSURI);

			ClientReference newDS = new ClientReference(newDSURI);
			ru.setDataSource(newDS);

			log.debug(ru.toString());
			OperationResult<ClientResource> resourceUpdate = session
		    		.resourcesService()
			        .resource(ru.getUri())
			        .createOrUpdate(ru);

			log.info("\tUpdated to " + ru.getDataSource().getUri());
		}
    }
    
    public static void updateAdhocDataView(ClientResourceLookup rl, Session session,
    		String origRoot, String newRoot) throws Exception {
    	
    	OperationResult<ClientResource> resourceResult = session
	    		.resourcesService()
		        .resource(rl.getUri())
		        .details();
    	
    	ClientAdhocDataView adv = (ClientAdhocDataView) resourceResult.getEntity();

		ClientReferenceableDataSource ds = adv.getDataSource();
		
		if (ds.getUri().startsWith(origRoot)) {		
	    	log.info(rl.getUri() + " : " + rl.getResourceType());
			
			String newDSURI = newRoot + ds.getUri().substring(origRoot.length());
			log.info("\tOrig Adhoc Data View DS URI " + ds.getUri() + ", new ad hoc Data View DS URI " + newDSURI);

			ClientReference newDS = new ClientReference(newDSURI);
			adv.setDataSource(newDS);

			OperationResult<ClientResource> resourceUpdate = session
		    		.resourcesService()
			        .resource(adv.getUri())
			        .createOrUpdate(adv);
			
			log.info("\tUpdated to " + adv.getDataSource().getUri());
		}
    }
    
    public static void updateSemanticLayerDataSource(ClientResourceLookup rl, Session session,
    		String origRoot, String newRoot) throws Exception {

    	OperationResult<ClientResource> resourceResult = session
	    		.resourcesService()
		        .resource(rl.getUri())
		        .details();
    	
    	ClientSemanticLayerDataSource domain = (ClientSemanticLayerDataSource) resourceResult.getEntity();
	    // update the resource
		ClientReferenceableDataSource ds = domain.getDataSource();
		if (ds != null  && ds.getUri().startsWith(origRoot)) {
			log.info(rl.getUri() + " :" + rl.getResourceType());
			String newDSURI = newRoot + ds.getUri().substring(origRoot.length());
			log.info("\tOrig Domain DS URI " + ds.getUri() + ", New Domain DS URI " + newDSURI);

			ClientReference newDS = new ClientReference(newDSURI);
			domain.setDataSource(newDS);

			OperationResult<ClientResource> resourceUpdate = session
		    		.resourcesService()
			        .resource(domain.getUri())
			        .createOrUpdate(domain);
			
			log.info("\tUpdated to " + domain.getDataSource().getUri());
		}
    }
    
    
    public static void updateJRXMLFile(ClientResourceLookup rl, Session session,
    		String origRoot, String newRoot) throws Exception {
    	
    	// Get the details
    	OperationResult<ClientResource> resourceResult = session
	    		.resourcesService()
		        .resource(rl.getUri())
		        .details();
    	
    	ClientFile file = (ClientFile) resourceResult.getEntity();
      	
    	// proceed if it is a JRXML
    	
    	if (file.getType() != FileType.jrxml) {
    		return;
    	}

    	log.info("Processing JRXML: " + rl.getUri());
    	
    	OperationResult<InputStream> resourceStream = session
	    		.resourcesService()
		        .resource(rl.getUri())
		        .downloadBinary();
    	
    	if (newRoot == null || newRoot.trim().length() == 0) {
    		newRoot = "";
    	}
    	
    	try {
	    	JasperDesign jasperDesign = JRXmlLoader.load(resourceStream.getEntity());
	    	Boolean updated = false;
	    	
	    	for (JRExpression expr : jasperDesign.getExpressions()) {
				JRDesignExpression dExpr = (JRDesignExpression) expr;
				//log.debug(expr.getText());
				String exprText = dExpr.getText();
				// expressions with "repo:
				if (exprText != null && exprText.startsWith("\"repo:" + origRoot)) {
					String newURI = "\"repo:" + newRoot + exprText.substring(origRoot.length() + 6);
					log.info("Updated " + exprText + " to " + newURI);
					dExpr.setText(newURI);
					updated = true;
				}
	    	}
	    	
	    	JRPropertiesMap map = jasperDesign.getPropertiesMap();
	    	for (String propertyName : map.getPropertyNames()) {
	    		String propertyValue = map.getProperty(propertyName);
				if (propertyValue != null && propertyValue.startsWith(origRoot)) {
					String newURI = newRoot + propertyValue.substring(origRoot.length());
					log.info("Updated property: " + propertyName + "  from " + propertyValue + " to " + newURI);
					map.setProperty(propertyName, newURI);
					updated = true;
				}
				if (propertyValue != null && propertyValue.startsWith("repo:" + origRoot)) {
					String newURI = "repo:" + newRoot + propertyValue.substring(origRoot.length() + 5);
					log.info("Updated " + propertyValue + " to " + newURI);
					map.setProperty(propertyName, newURI);
					updated = true;
				}
	    	}
	    	
	    	if (updated) {
		    	File tempFile = File.createTempFile("AppUpdate", ".jrxml");
		    	
		    	JRXmlWriter.writeReport(jasperDesign, tempFile.getCanonicalPath(), "UTF-8");
		    	
		    	OperationResult result = session
			        .resourcesService()
			        .resource(rl.getUri())
			        .updateContentFile(tempFile, FileType.jrxml, file.getLabel(), file.getDescription());
		    	
		    	tempFile.delete();
		   			
				log.info("\tUpdated expressions in " + rl.getUri());
	    	}
    	} catch (Exception e) {
    		log.error("failed JRXML load and update", e);
    	}
	}

    private static Properties getArgs(String[] args) throws Exception {
    	final Properties params = new Properties();

    	//List<String> options = null;
    	String propName = null;
    	for (int i = 0; i < args.length; i++) {
    	    final String a = args[i];

    	    if (a.charAt(0) == '-') {
    	        if (a.length() < 2) {
    	            throw new Exception("Error at argument " + a);
    	        }

    	        //options = new ArrayList<String>();
    	        //params.put(a.substring(1), options);
    	        propName = a.substring(1);
    	    }
    	    else if (propName != null) {
    	        // options.add(a);
    	    	params.setProperty(propName, a);
    	    	propName = null;
    	    }
    	    else {
    	    	throw new Exception("Illegal parameter usage");
    	    }
    	}
    	return params;
    }

    private static Properties getProperties(String filename) {
    	Properties argMap = new Properties();
		Boolean loaded = false;
		try {
			argMap.load(new FileInputStream(filename));
			loaded = true;
		} catch (FileNotFoundException e) {
			log.info("properties load error - FileNotFound: " + filename);
		} catch (IOException e) {
			log.info("properties load error - IOException: " + filename);
		}
		if (!loaded) {
			InputStream is = App.class.getResourceAsStream(filename);
			try {
				argMap.load(is);
    			log.info("properties loaded as InputStream: " + filename);
			} catch (IOException e) {
    			log.info("properties load error as InputStream - IOException: " + filename, e);
			}
		}
		return argMap;

    }
}
