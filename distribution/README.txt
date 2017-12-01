
Process to convert Harris Data from single tenant to multi-tenant

Assumptions

In a multi-tenant Jaspersoft instance with single tenant repository structure
Login as superuser
/organizations/organization_1 has all the ad hoc config that the customers are using
and that the data source is under the adhoc folder.


Manual
	Copy the resources in /organization/organization_1/adhoc to /public/adhoc
	Create the new orgs: /organization/organization_ABC, ....

Run the tool mode = updateDataReferences to update the public area
       folderToSearch = "/public/adhoc";
       origRoot = "/organizations/organization_1/adhoc";
       newRoot = "/public/adhoc";

Run the tool mode = createOrgs for the orgs
            create the orgs.properties file containing entries:
                /public/XYZ/LMN=XYZ/LMN|ABC Corporation|ABC Corporation
       origRoot = "/organizations/organization_1/adhoc";
       newRoot = "/public/adhoc";

Delete the /public/<org> and /organization_1 folders


multiTenantUpdate.bat

Usage:

This tool has 2 modes:

1. update data source references in topics, ad hoc views, ad hoc reports and domains

example: multiTenantUpdate -configFile appUpdatePublicAdhoc.properties
 
2. create a set of organizations
   copy existing resources to the organizations
   and update data source references (as above)

example: multiTenantUpdate -configFile app.properties

Assumes that data sources are within a set of folders underneath the adhoc folder of /public

 * 
 * Args:
 * 
 * configFile				a "properties" file that contains all the parameters below as key=value per line
 * 
 * or 
 * 
 * connectionConfigFile		see https://github.com/Jaspersoft/jrs-rest-java-client Configuration
 * 							sets URL and connection properties
 *
 * URL						if no connectionConfigFile given. defaults to http://localhost:8080/jasperserver-pro
 * 
 * username					Authentication against repo. defaults to jasperadmin
 * password					"". defaults to jasperadmin
 * organization				"". does not default
 * 
 * mode						updateDataReferences
 * 			folderToSearch like		"/";
 * 			origRoot like			"/organizations/Base_org_to_move/adhoc";
 * 			newRoot like		"/public/adhoc";
 * 
 * OR
 * 
 * mode						createOrgs
 * 			orgFileName
 * 				properties file containing: original org folder and destination org id (and alias), name, description
 * 				org id can be hierarchical with /s, in which case the parent orgs will be created as necessary
 * 				and the id and alias will be the id with underscores
 * 
 * 				ie. /public/ABC=ABC|ABC Corporation|ABC Corporation
 * 					/public/XYZ/LMN=XYZ/LMN|ABC Corporation|ABC Corporation
 *
 *				example in distribution: orgs.properties
 *
 * 			optional - won't update moved resources if not provided
 * 			origRoot like	"/organizations/Base_org_to_move/adhoc";
 * 			newRoot like	"/public/adhoc";
