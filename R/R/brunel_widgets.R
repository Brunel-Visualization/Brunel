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

range_filter_template <- "\t$('#{{controlsid}}').append(BrunelJQueryControlFactory.make_range_slider( '{{visid}}', '{{field_id}}', '{{label}}', {{min}}, {{max}} ));\n"
cat_filter_template <- "\t$('#{{controlsid}}').append(BrunelJQueryControlFactory.make_category_filter(  '{{visid}}', '{{field_id}}', '{{label}}', {{categories}} ));\n"

build_brunel_widget_js <- function (controls, controlsid) {

	script <- "$(function() {\n"
    script <- build_filters(controls$filters, controls$visId, controlsid, script)
	script <- paste(script, "});\n")

	if (length(controls$filter) >0) {
		script <- paste(script, "BrunelEventHandlers.make_filter_handler(v);\n")
	}

	return(script)
}

build_filters <- function (filters, visid, controlsid, script) {
	

	for (filter in filters) {
		if (is.null(filter$categories)) {
			script <- paste(script, build_range_filter(filter, visid, controlsid))
		}
		else {
			script <- paste(script, build_cat_filter(filter, visid, controlsid))
		}
	}
	return(script)
}

build_range_filter <- function (range_state, visid, controlsid) {
	values <- list(visid=visid, controlsid=controlsid, field_id=range_state$id, label=range_state$label, min=range_state$min, max=range_state$max)
	return (template_render(range_filter_template, values))
}

build_cat_filter <- function (cat_filter_state, visid, controlsid) {
	values <- list(visid=visid, controlsid=controlsid, field_id=cat_filter_state$id, label=cat_filter_state$label, categories = jsonlite::toJSON(unlist(cat_filter_state$categories)) )
	return (template_render(cat_filter_template, values))
}

