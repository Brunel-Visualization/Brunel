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
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

class GalleryBuilder {

    private static final String ITEM_FORMAT = "<td>\n" +
            "  <a href=\"%s\"><img title=\"%s\" src=\"https://raw.github.com/Brunel-Visualization/Brunel/master/etc/src/main/resources/gallery/%s\"/></a>\n" +
            "  <p>%s</p>\n" +
            "</td>\n";

    /*
        Parameters are
        %d - top
        %d - left
        %s - description        ... this is a chart that ....
        %s - target URL         ... http://brunel.jupyter.ninja:9080/brunel-service/docs/
        %s - thumbnail image    ... bggtreemap.png
        %s - title              ... Title for the first one
     */

    // Photoshop setting: circle selection 200px wide with 10px feathering

    private static final String GALLERY = "/org/brunel/app/gallery.txt";

    public static void main(String[] args) throws Exception {
        new GalleryBuilder().run();
    }

    private final WebDisplay display = new WebDisplay("Full Tests");
    private final StringBuilder out = new StringBuilder();

    private int row = 0;
    private int column = 0;

    private void run() throws Exception {

        out.append("<table>\n");

        /* Read the file in */
        String[] lines = new Scanner(WebDisplay.class.getResourceAsStream(GALLERY), "UTF-8").useDelimiter("\\A").next().split("\n");

        Map<String, String> tags = new HashMap<String, String>();
        String currentTag = null;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                show(tags);
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

        show(tags);

        if (column != 0) out.append("</row>\n");
        out.append("</table>\n");

        display.showInBrowser();

        System.out.println(out.toString());
    }

    private void show(Map<String, String> tags) {
        if (tags.isEmpty()) return;                                                     // Nothing defined yet

        String brunel = tags.get("#brunel");
        String id = tags.get("#id");
        String title = tags.get("#title");
        String description = tags.get("#description");
        String image = id + ".png";
        String target = "http://brunel.jupyter.ninja:9080/brunel-service/docs/";        // Service later

        int HEIGHT = 800;
        int WIDTH = 1000;
        try {
            Action a = Action.parse(brunel);
            VisItem item = a.apply();                       // data description should be included
            display.buildOneOfMultiple(item, "all", id, new Dimension(WIDTH, HEIGHT));
        } catch (Exception e) {
            System.err.println("Error running gallery item: " + tags);
            e.printStackTrace();
        }

        if (column == 0) {
            out.append("<tr>\n");
        }

        String itemText = String.format(ITEM_FORMAT, target, description, image, title);
        out.append(itemText);

        // Move to next item
        column++;
        if (column == 3 && row % 2 == 0 || column == 2 && row % 2 == 1) {
            column = 0;
            row++;
            out.append("</tr>\n");
        }

    }

}
