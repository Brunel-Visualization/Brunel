/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//Provides Brunel event handling and communication facilities that are relatively agnostic to a specific GUI toolkit
//used to create the UI controls.

//Using Tiny Pub/Sub for now.  May need to replace..
(function($) {

  var o = $({});

  $.subscribe = function() {
    o.on.apply(o, arguments);
  };

  $.unsubscribe = function() {
    o.off.apply(o, arguments);
  };

  $.publish = function() {
    o.trigger.apply(o, arguments);
  };

}(jQuery));

function BrunelEventHandlers (brunel) {

	var brunel = brunel;			//The Brunel visualization to operate on 
	var filterHandler;				//Handles all filter requests (multiple filters)
	
	/**
	 * A simple filter handler.  
	 *
	 * Example Filter event:
	 *
	 *  {
    		"aFieldId" : {
    			"filter" : {"min":0, "max":10}  //or array of selected categories
    			"filter_type" : 'range',        //or 'category'
    		}
        }

	 *
	 */	
	function FilterHandler(defaultFilter)   {


		//All filter values for the given visualization are by field id and whether the field's type.
        var filterState = defaultFilter;

        function makeFilterStatement () {

        	var filterStatement = "";
        	for (var field in filterState) {
        		if (filterState[field].filter_type === 'range') {
        			filterStatement += makeRangeFilter(field, filterState[field].filter);
        		}
        		else if (filterState[field].filter_type === 'category') {
        			filterStatement += makeCategoryFilter(field, filterState[field].filter);
        		}
        		filterStatement += ";";

			}
        	return filterStatement.substring(0,filterStatement.length-1);
        }

        function makeRangeFilter(field, filter) {
        	return field + " in " + filter.min + ", " + filter.max;
        }

        function makeCategoryFilter(field, filter) {
        	var filterCommand = field + " is ";
        	for (var filterVal in filter) {
        		filterCommand += filter[filterVal] + ", ";
			}
        	return filterCommand.substring(0,filterCommand.length-1);
		}

		return {
			
			//Executes a request to filter
			filter: function (filterField, filterValue) {
	             if (filterField != null && filterValue != null) {
					 filterState[filterField] = filterValue;
					 buildVisualization();
	             }
			},
			
			//Generates a single filter statment for all filter controls
			makeFilterStatement:  makeFilterStatement
		}
	}

	function createFilterHandlerAndSubscribe(defaultFilter) {
		
		if (!defaultFilter) defaultFilter = {};
    	filterHandler = FilterHandler(defaultFilter);
        addDataProcess();

		$.subscribe('filter.' + brunel.visId, function (_, a, b) {
			filterHandler.filter(a, b);
		});
     }
	
	//All data pre & post processing needed should be coordinated here.
	//Currently this is just processing the filtering
	function addDataProcess() {
		
		brunel.dataPreProcess(function (data) {
			return data.filter(filterHandler.makeFilterStatement())
		});
	}
	
	//Rebuild a visualization including any data processing.
	function buildVisualization() {		
		addDataProcess();
    	brunel.build();
	}


	return {
		//Create a filter handler with a default (can be null or empty).
		make_filter_handler: function (defaultFilter) {
			createFilterHandlerAndSubscribe(defaultFilter);
		}
     }

};

