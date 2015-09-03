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

brunel <- function(brunel, data, width=800, height=600) {
	visid <-  paste("vis", uuid::UUIDgenerate(), sep='')
	controlsid <- paste("controls", uuid::UUIDgenerate(), sep='')
	query_params = list(src=brunel, width=width, height=height, visid=visid)
	csv <- to_csv(data)
	response <- brunel_service_call(query_params, csv)
	display_d3_output(response, visid, controlsid, width, height)	
}

to_csv <- function(data) {
	tc <- textConnection("out", "w") 
	write.csv(data, tc, row.names =FALSE)	
	close(tc)
	return(out)
}

brunel_service_call <- function (query_params, csv) {
	url = paste(brunel_server, "/brunel/interpret/d3", sep='')	
	response <- httr::POST(url, query = query_params, httr::content_type("text/plain"), httr::accept_json(), body=csv) 
}

display_d3_output <- function(response, visid, controlsid, width, height) {

    con <- httr::content(response)

	if (response$status_code != 200)  {
		IRdisplay::display_html(con)
	}
	else {
		d3js <- con$js
      	d3css <- con$css
      	controls <- con$controls

		html_values <- list(d3css=d3css, visId=visid, width=width, height=height, d3js=d3js, controlsid=controlsid, controls=build_brunel_widget_js(controls, controlsid))
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
