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

package org.brunel.gallery;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.brunel.app.CookBookBuilder;
import org.brunel.app.DocBuilder;
import org.brunel.app.GalleryBuilder;
import org.brunel.build.util.BuilderOptions;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;

public class ExampleBuilder extends DocBuilder {
	
	 public static final JSONObject GALLERY = new JSONObject();
	 public static final JSONObject COOKBOOK = new JSONObject();
	 private static final String IMAGE_BASEURL ="https://raw.github.com/Brunel-Visualization/Brunel/master/etc/src/main/resources/gallery/";
	 
	 private JSONArray current;
	 
	 static {
		 try {
			make();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	 }
	 
	 
	 public ExampleBuilder(JSONArray jarray) {
		 super(new BuilderOptions());
		 this.current = jarray;
	 }
	 
	 private static void make() throws Exception {
		 make(GALLERY, "gallery", GalleryBuilder.GALLERY);
		 make(COOKBOOK, "basic", CookBookBuilder.BASIC);
		 make(COOKBOOK, "info_vis", CookBookBuilder.INFO_VIS);
		 make(COOKBOOK, "stats", CookBookBuilder.STATS);
		
	 }
	 
	 private static void make(JSONObject jobj, String name, String fileLoc) throws Exception {
		 JSONArray jarray = new JSONArray();
		 jobj.put(name, jarray);
		 new ExampleBuilder(jarray).run(fileLoc, null);
	 }
	
	 protected void show(Map<String, String> tags, String itemFormat) throws UnsupportedEncodingException {
		 
	        if (tags.isEmpty()) return;  
	        String brunel = tags.get("#brunel");
	        String id = tags.get("#id");
	        String title = tags.get("#title");
	        String description = tags.get("#description");
	        String ext = tags.get("#ext");
	        String width = tags.get("#width");
	        String height = tags.get("#height");
	        String controlHeight = tags.get("#control_height");
	        String image = id + (ext == null ? ".png" : "." + ext);
	        
	        JSONObject jobj= new JSONObject();
	        jobj.put("brunel", brunel);
	        jobj.put("id", id);
	        jobj.put("title", title);
	        jobj.put("description", description);
	        jobj.put("ext", ext);
	        jobj.put("width", width);
	        jobj.put("height", height);
	        jobj.put("control_height", controlHeight);
	        jobj.put("image", IMAGE_BASEURL + image);
	        
	        current.add(jobj);
	 }

	@Override
	protected String format(String itemFormat, String target,
			String description, String image, String title, String brunel) {
		return null;
	}

}
