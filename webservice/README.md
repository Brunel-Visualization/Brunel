# Web Service Module

This module provides a web service that will generate Brunel charts from input source

## Bluemix Support

The service is deployed on bluemix:

 * Application Name (Route): brunelvis
 * URL: brunelvis.mybluemix.net
    
 Useful CloudFoundry commands
 
    cf api https://api.ng.bluemix.net/          - set the API
    cf login -sso                               - login using single sign on
    cf push brunelvis -p webservices-??.war     - push the app

## Notes

 * The servlets are given the url-pattern `/build/*`, so their class doesn't need to
   scope the call using another `@Path()` annotation -- it just defines the path as `/`
 * The web services are called with 
   [https://brunelvis.mybluemix.net/build/*](https://brunelvis.mybluemix.net/build/*)
 * Static content and a small entry page are available at  the default 
   [https://brunelvis.mybluemix.net/](https://brunelvis.mybluemix.net/)
 * Sample data is available at 
  [https://brunelvis.mybluemix.net/sample](https://brunelvis.mybluemix.net/build/sample)
