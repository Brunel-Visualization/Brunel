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

d3_template_html <-  readLines(system.file("templates", "D3_Template.html", package = "brunel"))
brunel_server <- Sys.getenv("BRUNEL_SERVER")

#sessionid is needed since the R implementation currently uses Brunel web service which caches the data.
#This should avoid problems with same named dataframes in different notebooks.
sessionid <- paste("",uuid::UUIDgenerate(),sep='')

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

brunel <- function(brunel, data=NULL, width=800, height=600) {
    cache_data(brunel)
	visid <-  paste("vis", uuid::UUIDgenerate(), sep='')
	controlsid <- paste("controls", uuid::UUIDgenerate(), sep='')
	query_params = list(src=brunel, width=width, height=height, visid=visid, controlsid=controlsid, data_prefix=sessionid)
	csv <- to_csv(data)
	response <- brunel_service_call(query_params, csv)
	display_d3_output(response, visid, controlsid, width, height)	
}

to_csv <- function(data) {
    if (is.null(data)) return(NULL)
	tc <- textConnection("out", "w") 
	write.csv(data, tc, row.names =FALSE)	
	close(tc)
	return(out)
}

brunel_service_call <- function (query_params, csv) {
	url = paste(brunel_server, "/brunel/interpret/d3", sep='')	
	response <- httr::POST(url, query = query_params, httr::content_type("text/plain"), httr::accept_json(), body=csv) 
}

get_dataset_names <- function (brunel) {
    query_params <- list(brunel_src=brunel)
    url = paste(brunel_server, "/brunel/interpret/data_names", sep='')
    response <- httr::GET(url, query = query_params,  httr::accept_json()) 
    con <- httr::content(response)
}

cache_data <- function (brunel) {
    data_set_names <- get_dataset_names(brunel)
    for (name in data_set_names) {
        obj <- get(name)
        if (class(obj) == "data.frame") {
            query_params <- list(data_key=name, prefix=sessionid)
            csv <- to_csv(obj)
            url <- paste(brunel_server, "/brunel/interpret/cache", sep='')
            response <- httr::POST(url, query = query_params, httr::content_type("text/plain"), body=csv) 
        }
    }
    
}

display_d3_output <- function(response, visid, controlsid, width, height) {

    con <- httr::content(response)

	if (response$status_code != 200)  {
		IRdisplay::display_html(con)
	}
	else {
		d3js <- con$js
      	d3css <- con$css

		html_values <- list(d3css=d3css, visId=visid, width=width, height=height, d3js=d3js, controlsid=controlsid)
		html <- template_render(d3_template_html, html_values)
		IRdisplay::display_html(html)
  	}
}

template_render <- function(template, values) {
	tokens <- names(values)
	for (token in tokens) {
            next_token = paste('{{', token, '}}', sep='')
		template <- sub(next_token, values[token], template, fixed=TRUE)
      }
	return(paste(template, collapse=' '))
}
