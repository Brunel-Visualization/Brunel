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

from IPython.html import widgets
from IPython.utils.traitlets import Unicode
from IPython.utils.traitlets import CFloat, List

# Uses Brunel 'controls' to create IPython Widgets.
# Widgets are the same regardless of visualization rendering technique.


def build_widgets(controls, visid):
    try:
        results ={'widgets':[], 'wire_code':"\n\n"}
        # add filters
        add_filter_widgets(controls, visid, results)
        # soon..  add others..

        if len(results['widgets'])==0:
            # No widgets
            return

        results['wire_code'] = results['wire_code']
        widget_box = widgets.Box(children=[x for x in results['widgets'] if x is not None])
        return {'widget_box': widget_box, 'wire_code': results['wire_code']}

    except KeyError:
        # no interactivity or filters.  Do Nothing
        return
    return


def add_filter_widgets(controls, visid, results):

    try:
        filters = controls['filters']
        results['widgets'] =  results['widgets'] + [build_filter_widget(filter, visid) for filter in filters]
        results['wire_code'] = results['wire_code'] + "BrunelEventHandlers.make_filter_handler(v);\n"
    except KeyError:
        return


def build_filter_widget(filter, visid):

    try:
        categories = filter['categories']
        #  Category select widget if we have categories
        return CategoryFilter(field_label = filter['label'], field_id=filter['id'], visid = visid, categories=categories)
    except KeyError:
        # not categories, use range slider
        return RangeSlider(field_label = filter['label'], field_id=filter['id'], visid = visid, data_min = filter['min'],
                           data_max = filter['max'])


class RangeSlider(widgets.DOMWidget):
    _view_name = Unicode('RangeSliderView', sync=True)
    _view_module = Unicode('nbextensions/brunel_ext/BrunelWidgets', sync=True)
    field_label = Unicode('field_label', sync=True)
    visid = Unicode('visid', sync=True)
    field_id = Unicode('field_id', sync=True)
    data_min = CFloat(0, sync=True)
    data_max = CFloat(10, sync=True)

class CategoryFilter(widgets.DOMWidget):
    _view_name = Unicode('CategoryFilterView', sync=True)
    _view_module = Unicode('nbextensions/brunel_ext/BrunelWidgets', sync=True)
    field_label = Unicode('field_label', sync=True)
    visid = Unicode('visid', sync=True)
    field_id = Unicode('field_id', sync=True)
    categories = List([], sync=True)
