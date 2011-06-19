openengsb-connector-maven-1.2.3 2011-06-19
---------------------------------------------------------------------

This release contains an upgrade to the versions attributes in the UI and an classloader issue.

** Bug
    * [OPENENGSB-1666] - The maven connector's version attribute has no name
    * [OPENENGSB-1667] - The maven connector cannot find the filter list

** Task
    * [OPENENGSB-1642] - Release openengsb-connector-maven-1.2.3


openengsb-connector-maven-1.2.2 2011-05-23
---------------------------------------------------------------------

This release contains an upgrade of all domains (build, deploy, test) and the openengsb-framework. In addition the 
ServiceInstance Factory does inject properties correctly now.


** Bug
    * [OPENENGSB-1598] - ServiceInstance factory does not inject properties

** Library Upgrade
    * [OPENENGSB-1492] - Upgrade openengsb-domain-build to 1.2.1
    * [OPENENGSB-1494] - Upgrade openengsb-domain-deploy to 1.2.1
    * [OPENENGSB-1499] - Upgrade openengsb-domain-test to 1.2.1
    * [OPENENGSB-1609] - Upgrade openengsb-framework to 1.3.0.M2

** Task
    * [OPENENGSB-1576] - Release openengsb-connector-maven-1.2.2


openengsb-connector-maven-1.2.1 2011-05-16
---------------------------------------------------------------------

Include automatic maven installation and bundle info 

** Bug
    * [OPENENGSB-1573] - bundle.info uses wrong resource-filtering

** Improvement
    * [OPENENGSB-669] - maven-connector should manage maven-insallation
    * [OPENENGSB-1189] - maven-connector should interpret path as subdirectories of ${karaf.data}

** Library Upgrade
    * [OPENENGSB-1508] - Push connectors and domains to latest openengsb-framework-1.3.0.M1

** New Feature
    * [OPENENGSB-948] - Add OSGI-INF/bundle.info as used in Karaf to the openengsb bundles

** Task
    * [OPENENGSB-1452] - Release openengsb-connector-maven-1.2.1


openengsb-connector-maven-1.2.0 2011-04-27
---------------------------------------------------------------------

Initial release of the OpenEngSB Maven Connector as standalone package

** Bug
    * [OPENENGSB-1401] - Domains in connctors are referenced by the wrong version
    * [OPENENGSB-1409] - Range missformed

** Library Upgrade
    * [OPENENGSB-1394] - Upgrade to openengsb-1.2.0.RC1
    * [OPENENGSB-1453] - Upgrade to openengsb-domain-build-1.2.0
    * [OPENENGSB-1454] - Upgrade to openengsb-domain-test-1.2.0
    * [OPENENGSB-1455] - Upgrade to openengsb-domain-deploy-1.2.0

** Task
    * [OPENENGSB-1275] - Use slf4j instead of commons-logging in maven connector
    * [OPENENGSB-1319] - Adjust all connectors to new ServiceManager-API
    * [OPENENGSB-1382] - Release openengsb-connector-maven-1.2.0
    * [OPENENGSB-1396] - Add infrastructure for notice file generation
    * [OPENENGSB-1397] - Add ASF2 license file

