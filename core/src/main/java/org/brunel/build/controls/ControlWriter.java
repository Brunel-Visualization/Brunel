/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.build.controls;

import com.google.gson.Gson;
import org.brunel.build.util.ScriptWriter;

import java.util.List;

/**
 * Writes Javascript using the JSON object created by a Builder that contains information required to create the
 * user interface controls.
 *
 * @author drope
 */
public class ControlWriter {

    private final String controlId;              //The HTML id for the tag that will contain the UI controls
    private final String uiFactoryClass;         //The name of the Factory class that creates the UI instances
    private final ScriptWriter out;
    private final Gson gson = new Gson();

    public ControlWriter(String controlId, String uiFactoryClass) {
        this.controlId = controlId;
        this.uiFactoryClass = uiFactoryClass;
        out = new ScriptWriter();
    }

    public String write(Controls controls) {
        List<Filter> filters = controls.filters;
        if (filters.isEmpty()) return "";
        String visId = controls.visId;
        out.titleComment("Create and wire controls");
        out.add("$(function() {").ln().indentMore();
        createFilters(filters, visId);

        out.indentLess().ln();
        out.add("})").endStatement();

        out.add("BrunelEventHandlers.make_filter_handler(v)").endStatement();
        return out.content();
    }

    private void createFilters(List<Filter> filters, String visId) {

        for (Filter filter : filters) {
            String fieldId = filter.id;
            String label = filter.label;
            Object[] categories = filter.categories;
            Double min = filter.min;
            Double max = filter.max;

            //Range filter
            if (categories == null) {
                out.add("$(", out.quote("#" + controlId), ").append(", uiFactoryClass, ".make_range_slider(", out.quote(visId), ",",
                        out.quote(fieldId), ",", out.quote(label), ",", min, ",", max, "))").endStatement();
            }

            //Category filter
            else {
                out.add("$(", out.quote("#" + controlId), ").append(", uiFactoryClass, ".make_category_filter(", out.quote(visId), ",",
                        out.quote(fieldId), ",", out.quote(label), ",", gson.toJson(categories), "))").endStatement();
            }

        }
    }

}
