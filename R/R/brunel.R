# Copyright (c) 2015 IBM Corporation and others.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# You may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#JS locations passed to HTML template are read from env variable if present.  Defaults otherwise.
D3_LOC <- "//cdnjs.cloudflare.com/ajax/libs/d3/4.2.1/d3.min"
TOPO_JSON_LOC <- "//cdnjs.cloudflare.com/ajax/libs/topojson/1.6.20/topojson.min"
JS_LOC <- "/nbextensions/brunel_ext"
BRUNEL_CONFIG <- trimws(Sys.getenv("BRUNEL_CONFIG"))
OPTS <- strsplit(BRUNEL_CONFIG,";")[[1]]

get_config_key <- function (in_key) {
	return (tolower(trimws(in_key)))
}

for (opt in OPTS) {
	keyval <- strsplit(trimws(opt),"=")
	if (length(keyval) >=1) {
		if (identical(get_config_key(keyval[[1]][1]), "locd3")) 
			D3_LOC <- keyval[[1]][2]
		if (identical(get_config_key(keyval[[1]][1]), "locjavascript"))
			JS_LOC <- keyval[[1]][2]	
		if (identical(get_config_key(keyval[[1]][1]), "loctopojson")) 
			TOPO_JSON_LOC <- keyval[[1]][2]	
	}
}


#The docs say that .jpackage() is the preferred way to load the VM
#However, this failed under Win64 w/IBM JDK
load_java <- function() {
	rJava::.jinit()
	core_jar <- brunel_jar_name("core")
	data_jar <- brunel_jar_name("data")
	rJava::.jaddClassPath(system.file("java", core_jar, package = "brunel"))
	rJava::.jaddClassPath(system.file("java", data_jar, package = "brunel"))
	rJava::.jaddClassPath(system.file("java", "gson-2.3.1.jar", package = "brunel"))
}

#Dynamically add a version to the .jar file name.
brunel_jar_name <- function (module) {
	version <- packageVersion("brunel")
	return ( paste("brunel-", module, "-", version, ".jar", sep="") )
}

#Main HTML template containing results
d3_template_html <-  readLines(system.file("templates", "D3_Template.html", package = "brunel"))

#' Produce a Brunel visualization
#'
#' Given a brunel script and a supplied data frame, renders the visualization.
#' The BRUNEL_SERVER environment variable must be set to the location of the
#' Brunel web service.
#' 
#' @param brunel the brunel script
#' @param data the data frame containing the data
#' width the width in pixels for the resulting visualization
#' height the heigh in pixels for the resulting visualization

brunel <- function(brunel, data=NULL, width=800, height=600, online_js=FALSE) {

 	ONLINE_JS <<- FALSE
	if (online_js==TRUE) ONLINE_JS <<- TRUE
	load_java()
    cache_data(brunel)
	visid <-  paste("vis", uuid::UUIDgenerate(), sep='')
	controlsid <- paste("controls", uuid::UUIDgenerate(), sep='')
	csv <- to_csv(data)
	brunel_json <- brunel_d3_json(csv, brunel, width, height,visid, controlsid)
	display_d3_output(brunel_json, visid, controlsid, width, height)	
}

#CSV conversion of a dataframe
to_csv <- function(data) {
    if (is.null(data)) return(NULL)
	tc <- textConnection("out", "w") 
	write.csv(data, tc, row.names =FALSE, na="", quote=TRUE)	
	#collapse the CSV to a single string so it aligns with the Java method parameter
	str <- paste(out, collapse='\n')
	close(tc)
	return (str)
}

#Get the brunel JSON containing the display information.  
brunel_d3_json <- function(csv, brunel_src, width, height, visId, controlsId) {
	response <- rJava::J("org.brunel.util.D3Integration")$createBrunelJSON(csv, brunel_src, as.integer(width), as.integer(height), visId, controlsId)
	
	#Parse the String into a JSON object
	jsonlite::fromJSON(response)
}

#Find all names of data objects in the brunel
get_dataset_names <- function (brunel) {
    result <- rJava::J("org.brunel.util.D3Integration")$getDatasetNames(brunel)
}

#Add data to Brunel's data cache
cache_data <- function (brunel) {
    data_set_names <- get_dataset_names(brunel)
    for (name in data_set_names) {
        obj <- get(name)
        if (class(obj) == "data.frame") {
            csv <- to_csv(obj)
            rJava::J("org.brunel.util.D3Integration", "cacheData", name, csv)           
        }
    }    
}

display_d3_output <- function(brunel_json, visid, controlsid, width, height) {

	d3js <- brunel_json$js
  	d3css <- brunel_json$css
	version <- paste(packageVersion("brunel"), sep="")
  	
	#If requested, force using online JS files
    jsloc <- JS_LOC
    if (ONLINE_JS == TRUE) jsloc <- "https://brunelvis.org/js"
    
    #render the HTML
	html_values <- list(d3css=d3css, visId=visid, width=width, height=height, d3js=d3js, controlsid=controlsid, 
		d3loc=D3_LOC, topoJsonloc=TOPO_JSON_LOC, jsloc=jsloc, version=version )
	html <- template_render(d3_template_html, html_values)
	IRdisplay::display_html(html)
}

template_render <- function(template, values) {
	tokens <- names(values)
	
	for (token in tokens) {
            next_token = paste('{{', token, '}}', sep='')
		template <- sub(next_token, values[token], template, fixed=TRUE)
      }
	return(paste(template, collapse=' '))
}
