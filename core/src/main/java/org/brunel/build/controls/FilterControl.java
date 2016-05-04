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

import java.util.List;

import org.brunel.action.Param;
import org.brunel.action.Param.Type;
import org.brunel.data.Dataset;
import org.brunel.data.Field;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Java object containing the state of the filters
 *
 * @author drope, gwills
 */
public class FilterControl {

    /**
     * Given a field, make the information for a valid filter for it
     *
     * @param data    base data to filter
     * @param fieldID identifier of the field to filter using
     * @param modifier the modifier Param for the filter field which is expected to contain default values.
     * @return built Filter description
     */
    public static FilterControl makeForField(Dataset data, String fieldID, Param modifier) {
        Field field = data.field(fieldID);
        if (field.preferCategorical()) {
        	String[] selectedCategories = null;

        	if (modifier != null) {
	        	if (modifier.type() == Type.option) {
	        		selectedCategories= new String[] {modifier.asString()};
	        	}
	        	else if (modifier.type() == Type.list) {
	        		List<Param> params = modifier.asList();
	        		selectedCategories = new String[params.size()];
	        		for (int i=0;i < selectedCategories.length; i++) {
	        			selectedCategories[i] = params.get(i).asString();
	        		}
	        		
	        	}
        	}
            return new FilterControl(data.name(), fieldID, field.label, field.categories(), null, null, null, null, selectedCategories);
        }
        else {
        	Double low = null;
        	Double high =null;
        	
        	if (modifier != null) {
	        	if (modifier.type() == Type.number) {
	        		low = modifier.asDouble();
	        	}
	        	else if (modifier.type() == Type.list) {
	        		List<Param> params = modifier.asList();
	        		if (params.size() == 2) {
	        			low = params.get(0).asDouble();
	        			high = params.get(1).asDouble();
	        		}
	        	}
        	}
        	
            return new FilterControl(data.name(), fieldID, field.label, null, field.min(), field.max(), low, high, null);

        }

    }
    
    public static JsonObject buildFilterDefaults(List<FilterControl> filterControls) {
    	JsonObject jobj = new JsonObject();
    	
    	for (FilterControl f : filterControls) {
    		if (f.selectedCategories != null) {
        		JsonObject aFilter = new JsonObject();
    			aFilter.addProperty("filter_type", "category");
    			JsonArray jarray = new JsonArray();
    			for (Object o:f.selectedCategories) {
    				jarray.add(new JsonPrimitive(o.toString()));
    			}
    			aFilter.add("filter", jarray);
        		jobj.add(f.id, aFilter);

    		}
    		else if (f.lowValue != null) {
        		JsonObject aFilter = new JsonObject();
    			aFilter.addProperty("filter_type", "range");
    			JsonObject range = new JsonObject();
    			range.addProperty("min", f.lowValue);
    			Double high = f.highValue == null ? f.max : f.highValue;
    			range.addProperty("max", high);
    			aFilter.add("filter", range);
        		jobj.add(f.id, aFilter);

    		}
    	}
    	
    	return jobj;
    }
    


    public final String data;
    public final String id;
    public final String label;
    public final Object[] categories;
    public final Double min;
    public final Double max;
    public final Double lowValue;							//default provided by syntax
    public final Double highValue;							//default provided by syntax
    public final Object[] selectedCategories;				//default provided by syntax

    private FilterControl(String data, String id, String label, Object[] categories, Double min, Double max,
    		Double lowValue, Double highValue, Object[] selectedCategories) {
        this.data = data;
        this.id = id;
        this.label = label;
        this.categories = categories;
        this.min = min;
        this.max = max;
        this.lowValue = lowValue == null ? null : Math.max(min,lowValue);
        this.highValue = highValue == null ? null : Math.min(max, highValue);
        this.selectedCategories = selectedCategories;
        
        if (lowValue != null && highValue != null && lowValue > highValue) {
        	throw new IllegalArgumentException("Low filter value (" + lowValue +") cannot be greater than high filter value ("+highValue+") for: " + label);
        }
        
        substituteExactCase();
    }
    
    //Params passed by syntax are converted to lower case, this will find the exact value in categories for each selectedCategories
    private void substituteExactCase() {
    	if (selectedCategories != null) {
    		for (int i =0;i < selectedCategories.length; i++) {
    			for (int j=0; j< categories.length; j++) {
    				if (selectedCategories[i] instanceof String && categories[j] instanceof String){
	    				String selected = (String)selectedCategories[i];
	    				String main = (String)categories[j];
	    				if (selected.equalsIgnoreCase(main)) selectedCategories[i] = main;
    				}
    			}
    				
    		}
    	}
    	
    }
}
