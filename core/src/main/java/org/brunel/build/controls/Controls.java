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

package org.brunel.build.controls;

import com.google.gson.Gson;
import org.brunel.action.Param;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;

import java.util.ArrayList;
import java.util.List;

/**
 * Java object that is serialized to JSON containing the state of the controls.
 *
 * @author drope, gwills
 */
public class Controls {

    public final List<FilterControl> filters;		//Filter state
    private final transient BuilderOptions options; //Options not serialized to JSON
    private final transient Gson gson = new Gson();


    public Controls(BuilderOptions options) {
        this.options = options;
        this.filters = new ArrayList<>();
    }

    public void buildControls(VisSingle vis, Dataset data) {
        for (Param f : vis.fFilter) {
            filters.add(FilterControl.makeForFilterField(data, f.asField(data), f.firstModifier()));
        }
        FilterControl animationControl = FilterControl.makeForAnimation(data, vis.fAnimate);
        if (animationControl != null) filters.add(animationControl);
    }

    public boolean isNeeded() {
        return !filters.isEmpty();
    }

    public void writeControls(ScriptWriter out, String visInstance) {
    	if (options.controlsIdentifier != null)
    		writeControls(options.controlsIdentifier, "BrunelJQueryControlFactory", out, visInstance);
    }

    public void writeControls(String controlId, String uiFactoryClass, ScriptWriter out, String visInstance) {
        if (!needsControls()) return;
        out.titleComment("Create and wire controls");
        out.add("$(function() {").ln().indentMore();
        createFilters(controlId, uiFactoryClass, out, visInstance);

        out.indentLess().ln();
        out.add("})").endStatement();


    }
    
    public void writeEventHandler(ScriptWriter out, String visInstance) {
        if (!needsControls()) return;
        String filterDefaults =  gson.toJson(FilterControl.buildFilterDefaults(filters));
        
        out.add("var eventHandler = BrunelEventHandlers(",visInstance,")").endStatement();
        out.add("eventHandler.make_filter_handler(",filterDefaults,")").endStatement();
    }
    
    private boolean needsControls() {
    	return !filters.isEmpty(); 
    }

    private void createFilters(String controlId, String uiFactoryClass, ScriptWriter out, String visInstance) {

        for (FilterControl filter : filters) {
            String fieldId = filter.id;
            String label = filter.label;
            Object[] categories = filter.categories;
            Double low = filter.lowValue;
            Double high = filter.highValue;
            Object[] selectedCategories = filter.selectedCategories;
            Double animateSpeed = filter.animateSpeed;
            Double animateFrames = filter.animateFrames;
            boolean animate = filter.animate;
            
            

            //Range filter
            if (categories == null) {
                out.add("$(", out.quote("#" + controlId), ").append(", uiFactoryClass, ".make_range_slider(", out.quote(options.visIdentifier), ",",
                        out.quote(fieldId), ",", out.quote(label), ",", low, ",", high, ",",
                        visInstance, ".data().field(", out.quote(fieldId), "),", animate, "," , animateFrames, ",", animateSpeed, "))").endStatement();
            }

            //Category filter
            else {
                out.add("$(", out.quote("#" + controlId), ").append(", uiFactoryClass, ".make_category_filter(", out.quote(options.visIdentifier), ",",
                        out.quote(fieldId), ",", out.quote(label), ",", gson.toJson(categories), ",", gson.toJson(selectedCategories), "))").endStatement();
            }

        }
    }
}
