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

package org.brunel.app;

import org.brunel.action.Action;
import org.brunel.model.VisItem;
import org.brunel.util.WebDisplay;

import java.awt.*;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public abstract class DocBuilder {

    protected final WebDisplay display = new WebDisplay("Full Tests");
    protected final StringBuilder out = new StringBuilder();


    protected void run(String fileLoc, String itemFormat) throws Exception {


        /* Read the file in */
        String[] lines = new Scanner(WebDisplay.class.getResourceAsStream(fileLoc), "UTF-8").useDelimiter("\\A").next().split("\n");

        Map<String, String> tags = new HashMap<String, String>();
        String currentTag = null;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                show(tags, itemFormat);
                tags.clear();
                currentTag = null;
            } else if (line.startsWith("#")) {
                // Definition of a tag
                int p = line.indexOf(" ");
                currentTag = line.substring(0, p).trim();
                tags.put(currentTag, line.substring(p).trim());
            } else {
                // Continuation of a tag
                if (currentTag == null)
                    throw new IllegalStateException("Line does not define a tag or continue one: " + line);
                tags.put(currentTag, tags.get(currentTag) + " " + line);
            }
        }

        show(tags, itemFormat);


    }

    protected void show(Map<String, String> tags, String itemFormat) throws UnsupportedEncodingException {
        if (tags.isEmpty()) return;                                                     // Nothing defined yet

        String brunel = tags.get("#brunel");
        String id = tags.get("#id");
        String title = tags.get("#title");
        String description = tags.get("#description");
        String ext = tags.get("#ext");
        String widthS = tags.get("#width");
        String heightS = tags.get("#height");
        String controlHeights = tags.get("#control_height");
        String image = id + (ext == null ? ".png" : "." + ext);

        int WIDTH = widthS == null ? 1000 : Integer.parseInt(widthS);
        int HEIGHT =  heightS == null ? 800 : Integer.parseInt(heightS);
        int CONTROL_HEIGHT =  controlHeights == null ? 0 : Integer.parseInt(controlHeights);

        String target = String.format("http://brunel.mybluemix.net/gallery_app/renderer?" +
                "title=%s&brunel_src=%s&description=%s&width=%d&height=%d&control_height=%d",
                encode(title), encode(brunel), encode(description), WIDTH, HEIGHT, CONTROL_HEIGHT);

        try {
            Action a = Action.parse(brunel);
            VisItem item = a.apply();                       // data description should be included
            display.buildOneOfMultiple(item, "all", id, new Dimension(WIDTH, HEIGHT), true);
        } catch (Exception e) {
            System.err.println("Error running gallery item: " + tags);
            e.printStackTrace();
        }

        String itemText = format(itemFormat, target, description, image, title, brunel);
        out.append(itemText);


    }

    private String encode(String title) throws UnsupportedEncodingException {
        return URLEncoder.encode(title, "utf-8");
    }

    abstract protected String format(String itemFormat, String target, String description, String image, String title, String brunel);


}
