# Brunel Gallery

Bluemix service supporting the Brunel Gallery web application.  This service is expected to be deployed in IBM Bluemix  as a Liberty application with the Data Cache service bound to the application.  Due to the added dependencies, this code is not compiled in the main Brunel build.   This service uses the main Brunel service and adds dataset caching, an upload feature, and a service to generate HTML pages containing the live visualizations given initial Brunel.

## Dependencies

See:  [IBM WAS Downloads](https://developer.ibm.com/wasdev/downloads/) and install:

* IBM WAS Liberty  
* IBM WebSphere eXtreme Scale

It is simplest to install the provided plug-ins for Eclipse.

Also needed:

* Brunel .jar files for `core`, `data`, `etc`, and `service`
* Gson (Brunel execution dependency)

