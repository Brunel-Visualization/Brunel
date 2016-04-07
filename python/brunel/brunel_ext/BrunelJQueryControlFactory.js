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

// A closure for JQuery UI controls for Brunel interactivity.  Intent is other UI tookits can be used so long as they
//adhere to the public API from this closure.  Styles should have good defaults but can be replaced by the client.

//Styles listed in Brunel.css


var BrunelJQueryControlFactory = (function () {

	/**
	 * visid:  HTML tag containing the visualization
	 * fieldid:  The id for the field within the data object
	 * fieldLabel:  The display label for the field
	 * min:   The data minimum of the field
	 * max:  The data maximum of the field
	 */
	function makeRangeSlider(visid, fieldid, fieldLabel, min, max ) {

		var valueStyle = "legend control-label";
		var valueLeftStyle = valueStyle + " control-left-label";
		var sliderStyle = "horizontal-box";

		var rangeSlider = $('<div />').addClass(sliderStyle);
	    var label = makeControlFieldLabel(fieldLabel)
	        .appendTo(rangeSlider);

	    var lowValue = $('<div />')
	        .appendTo(rangeSlider)
	        .addClass(valueLeftStyle);
	    lowValue.html(min);
		var highValue = $('<div />')
        	.addClass(valueStyle);
		highValue.html(max);
	    var slider = $('<div />')
	        .slider({
	            range: true,
	            min: min,
	            max: max,
	            values:[min,max],
	            slide: function( event, ui ) {
	                lowValue.html(ui.values[0]);
	                highValue.html(ui.values[1]);
	            },
	            change: function(event, ui) {
	            	//Publishes a filter event with min/max values from slider
	            	var filter = {
	            		"filter" : {"min":ui.values[0], "max":ui.values[1]},
						"filter_type": "range"
					};
	                $.publish('filter.' + visid, [fieldid, filter]);
	            }

			});
		// Put the slider in a container
	    var slider_container = $('<div />')
	        .append(slider);
	    rangeSlider.append(slider_container);
 	    highValue.appendTo(rangeSlider);

	    return rangeSlider;
	}

	/**Create a categorical filter.  Currently using SumoSelect.
	 * visid:  The id for the visualization
	 * fieldid:  The field id in the data source
	 * fieldLabel:  The display value for the field
	 * categories:  The set of unique categories for the field
	 */
	function makeCategoryFilter(visid, fieldid, fieldLabel, categories) {

		var select = $('<select />', { multiple:"multiple"}).addClass("select-filter");
		var id = select.uniqueId().attr('id');
		var categoryFilter = $('<div />').addClass("horizontal-box");
	    makeControlFieldLabel(fieldLabel).addClass("control-title")
        	.appendTo(categoryFilter);

		for (var x in categories) {
			$("<option />", {value: x, text: categories[x], selected: "selected"}).appendTo(select);
		}

		select.appendTo(categoryFilter);

		$('body').on('change', '#'+id, function() {
			var selected= [];
			 $( '#'+id + " option:selected" ).each(function() {
				 selected.push($( this ).text() );
			 });
         	 var filter = {
            		"filter" : selected,
				 "filter_type": "category"
			 };
             $.publish('filter.' + visid, [fieldid, filter]);
		});
		addSumoStyle(id);

		return categoryFilter;
	}

	//Ensure the control has been added to the DOM prior to styling.
	function addSumoStyle(id) {

		if (!jQuery.contains(document,$('#'+id)[0])) {
			setTimeout( function() {
				addSumoStyle(id);
			},100)
		}
		else {
			$('#'+id).SumoSelect();
		}

	}


	//Make a label containing a field name
	function makeControlFieldLabel (fieldLabel) {

	    return $('<text  />')
        	.addClass("title")
        	.html(fieldLabel);

	}


	// Expose these methods
    return {
        make_range_slider : makeRangeSlider,
        make_category_filter : makeCategoryFilter
	}

})();

