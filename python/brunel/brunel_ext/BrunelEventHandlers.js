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

var BrunelEventHandlers = (function(){

	/**
	 * A simple filter handler
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
	
	var brunel;						//The Brunel to operate on 
	var filterHandler;				//Handles filter requests
	
	
	function FilterHandler(defaultFilter)   {


		//All filter values for the given visualization are by field id and whether the field's type.
        var filterState = defaultFilter;
        addDataProcess(makeFilterStatement());

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

		return function (filterField, filterValue) {
             if (filterField != null && filterValue != null) {
				 filterState[filterField] = filterValue;
				 buildVisualization(makeFilterStatement());
             }
        }
	}

	function createFilterHandlerAndSubscribe(defaultFilter) {
		
		if (!defaultFilter) defaultFilter = {};
    	filterHandler = new FilterHandler(defaultFilter);
		$.subscribe('filter.' + brunel.visId, function (_, a, b) {
			filterHandler(a, b);
		});
		filterHandler.filterState = defaultFilter;
     }
	
	//All data pre & post processing needed should be coordinated here.
	function addDataProcess(filterStatement) {
		brunel.dataPreProcess(function (data) {
			return data.filter(filterStatement)
		});
	}
	
	//Rebuild a visualization and include data processing.
	function buildVisualization(filterStatement) {		
		addDataProcess(filterStatement);
    	brunel.build();
	}


	return {
		
		//The Brunel to operate on must be set first
		set_brunel: function(b) {
			brunel = b;
		},
		
		//Create a filter handler with a default (can be null or empty).
		make_filter_handler: function (defaultFilter) {
			createFilterHandlerAndSubscribe(defaultFilter);
		}
     }

})();

