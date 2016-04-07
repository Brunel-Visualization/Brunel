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

require.config({
    paths: {
        'BrunelEventHandlers': '/nbextensions/brunel_ext/BrunelEventHandlers',
        'BrunelJQueryControlFactory': '/nbextensions/brunel_ext/BrunelJQueryControlFactory',
        'SumoSelect': '/nbextensions/brunel_ext/sumoselect/jquery.sumoselect.min'
    },
    shim: {
        'BrunelEventHandlers': {
            exports: 'BrunelEventHandlers'
        },
        'BrunelJQueryControlFactory': {
            exports: 'BrunelJQueryControlFactory'
        }
    }
});


define(function (require) {

    var ipywidget = require("widgets/js/widget");
    var BrunelEventHandlers = require("BrunelEventHandlers");
    var BrunelJQueryControlFactory = require("BrunelJQueryControlFactory");
    require("SumoSelect");

    //View object for our python slider.  Uses Brunel factory to create the JQuery object
    var RangeSliderView = ipywidget.DOMWidgetView.extend({
        render: function () {
            var fieldId = this.model.get('field_id');
            var visId = this.model.get('visid');
            var fieldLabel = this.model.get('field_label');
            var min = this.model.get('data_min');
            var max = this.model.get('data_max');

            this.$el.addClass("brunel");
            this.$el.append(BrunelJQueryControlFactory.make_range_slider(visId, fieldId, fieldLabel, min, max));
        }
    });

    //View object for our python slider.  Uses Brunel factory to create the JQuery object
    var CategoryFilterView = ipywidget.DOMWidgetView.extend({
        render: function () {
            var fieldId = this.model.get('field_id');
            var visId = this.model.get('visid');
            var fieldLabel = this.model.get('field_label');
            var categories = this.model.get('categories');

            this.$el.addClass("brunel");
            this.$el.append(BrunelJQueryControlFactory.make_category_filter(visId, fieldId, fieldLabel, categories));
        }
    });

    return {
        RangeSliderView: RangeSliderView,
        CategoryFilterView: CategoryFilterView
    }
});
