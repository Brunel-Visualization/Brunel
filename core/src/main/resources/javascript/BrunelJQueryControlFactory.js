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

    //Note, this includes support for animation multiple fields, but the syntax does not support this yet.
    var animations = {};            //Currently running animations.  Only 1 per vis.  {visid: {interval id, index, buttonid}}

    /**
     * visid:  HTML tag containing the visualization
     * datasetIndex the index number of the data set that contains the field in the visualization instance
     * fieldid:  The id for the field within the data object
     * fieldLabel:  The display label for the field
     * low:   The default low value for the slider
     * high:  The default high value for the slider
     * field:  The field to filter/animate over
     * animate:  Whether to animate
     * animateFrames:  The desired number of animation frames
     * animateSpeed:  The desired animation speed
     */
    function makeRangeSlider(visid, datasetIndex, fieldid, fieldLabel, keepMissing, low, high, field, animate, animateFrames, animateSpeed) {

        if (animateSpeed == null) animateSpeed = 500;
        if (animateFrames == null) animateFrames = 4;
        var domain = BrunelData.auto_NumericExtentDetail.makeForField(field),
            scale = BrunelData.auto_Auto.makeNumericScale(domain, true, [0, 0], 0, animateFrames + 1, true).divisions;
        var sliderId = "slider" + visid + fieldid;
        var buttonId = "animateButton" + visid + fieldid;

        var min = scale[0];
        var max = scale[scale.length - 1];

        var valueStyle = "legend control-label";
        var valueLeftStyle = valueStyle + " control-left-label";
        var sliderStyle = "horizontal-box";

        var rangeSlider = $('<div />').addClass(sliderStyle);
        var label = makeControlFieldLabel(fieldLabel)
            .appendTo(rangeSlider);

        //Adds play button for animations
        if (animate) {
            var playButton = $('<div />', {
                id: buttonId
            }).button({
                text: false,
                icons: {primary: "ui-icon-play"}
            }).click(function () {
                var wasThisAnimating = isButtonAnimating(visid, buttonId);
                if (isAnimating(visid)) {
                    //Shut off any currently running animations
                    stopAnimation(visid);
                    //Start this animation if it wasn't the one running
                    if (!wasThisAnimating) startAnimation(visid, sliderId, scale, buttonId, true, animateSpeed);
                }
                else {
                    //Start the animation
                    startAnimation(visid, sliderId, scale, buttonId, false, animateSpeed);
                }
            }).appendTo(rangeSlider);
        }


        if (!low) low = min;
        if (!high) high = max;

        var lowValue = $('<div />')
            .appendTo(rangeSlider)
            .addClass(valueLeftStyle);
        lowValue.html(field.format(low));
        var highValue = $('<div />')
            .addClass(valueStyle);
        highValue.html(field.format(high));

        //The default step is 1 which means there are no steps for a data range of 1.0 or less
        //In this case, we choose a step based on the scale Brunel creates for the field
        //We are not doing this always because it would make the filter too coarse by default.
        //A better solution is needed.
        var step = ( max - min > 2.0) ? 1 : (max - min) / scale.length;

        var slider = $('<div />', {
            id: sliderId
        })
            .slider({
                range: true,
                min: min,
                max: max,
                step: step,
                values: [low, high],
                slide: function (event, ui) {
                    lowValue.html(field.format(ui.values[0]));
                    highValue.html(field.format(ui.values[1]));
                },
                change: function (event, ui) {
                    //Publishes a filter event with min/max values from slider
                    var filter = {
                        "filter": {"min": ui.values[0], "max": ui.values[1]},
                        "filter_type": "range",
                        "datasetIndex": datasetIndex,
                        "keepMissing" : keepMissing
                    };
                    $.publish('filter.' + visid, [fieldid, filter], 0.9 * animateSpeed);
                }

            });
        // Put the slider in a container
        var slider_container = $('<div />')
            .append(slider);
        rangeSlider.append(slider_container);
        highValue.appendTo(rangeSlider);

        return rangeSlider;
    }

    //Whether any animations happening
    function isAnimating(visid) {
        return animations[visid] != null && animations[visid].id != null;
    }

    //Whether the animation for the given play button is running
    function isButtonAnimating(visid, buttonId) {
        return isAnimating(visid) && animations[visid].buttonId === buttonId;
    }

    //Stops all animations for the visid
    function stopAnimation(visid) {
        clearInterval(animations[visid].id);
        $("#" + animations[visid].buttonId).button("option", "icons", {primary: "ui-icon-play"});
        delete animations[visid].id;
    }

    //Main animation loop.
    function runAnimation(f, visid, speed) {
        if (isAnimating(visid)) stopAnimation(visid);
        animations[visid].id = setInterval(function () {
            f();
        }, speed);
    }

    //Stops any running animation and starts a new one
    function startAnimation(visid, sliderId, scale, buttonId, fromStart, speed) {

        $("#" + buttonId).button("option", "icons", {primary: "ui-icon-pause"});

        if (!animations[visid]) {
            animations[visid] = {
                index: 0
            };
        }
        animations[visid].buttonId = buttonId;
        if (fromStart) animations[visid].index = 0;

        runAnimation(function () {
            if (animations[visid].index < scale.length - 1) {
                $("#" + sliderId).slider({
                    values: [scale[animations[visid].index], scale[animations[visid].index + 1]]
                })
                animations[visid].index++;
            }
            else {
                animations[visid].index = 0;
            }
        }, visid, speed);
    }

    /**Create a categorical filter.  Currently using SumoSelect.
     * visid:  The id for the visualization
     * datasetIndex: the index number of the data set that contains the field in the visualization instance
     * fieldid:  The field id in the data source
     * fieldLabel:  The display value for the field
     * categories:  The set of unique categories for the field
     */
    function makeCategoryFilter(visid, datasetIndex, fieldid, fieldLabel, keepMissing, categories, selectedCategories) {

        var select = $('<select />', {multiple: "multiple"}).addClass("select-filter");
        var id = select.uniqueId().attr('id');
        var categoryFilter = $('<div />').addClass("horizontal-box");
        makeControlFieldLabel(fieldLabel).addClass("control-title")
            .appendTo(categoryFilter);

        for (var x in categories) {

            var sel = (selectedCategories && selectedCategories.indexOf(categories[x]) >= 0) ? true : false;
            if (!selectedCategories) sel = "selected";

            $("<option />", {value: x, text: categories[x], selected: sel}).appendTo(select);
        }

        select.appendTo(categoryFilter);

        $('body').on('change', '#' + id, function () {
            var selected = [];
            $('#' + id + " option:selected").each(function () {
                selected.push($(this).text());
            });
            var filter = {
                "filter": selected,
                "filter_type": "category",
                "datasetIndex": datasetIndex,
                "keepMissing" : keepMissing
            };
            $.publish('filter.' + visid, [fieldid, filter]);
        });
        addSumoStyle(id);

        return categoryFilter;
    }

    //Ensure the control has been added to the DOM prior to styling.
    function addSumoStyle(id) {

        if (!jQuery.contains(document, $('#' + id)[0])) {
            setTimeout(function () {
                addSumoStyle(id);
            }, 100)
        }
        else {
            $('#' + id).SumoSelect();
        }

    }


    //Make a label containing a field name
    function makeControlFieldLabel(fieldLabel) {

        return $('<text  />')
            .addClass("title")
            .html(fieldLabel);
    }

    // Expose these methods
    return {
        make_range_slider: makeRangeSlider,
        make_category_filter: makeCategoryFilter
    }

})();
