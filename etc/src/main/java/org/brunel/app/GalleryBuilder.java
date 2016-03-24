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

package org.brunel.app;

import org.brunel.build.util.BuilderOptions;

import java.io.UnsupportedEncodingException;
import java.util.Map;

public class GalleryBuilder extends DocBuilder {

    private static final String ITEM_FORMAT = "<td width=\"33%%\">\n" +
            "  <a href=\"%s\"><img title=\"%s\" src=\"https://raw.github.com/Brunel-Visualization/Brunel/master/etc/src/main/resources/gallery/%s\"/></a>\n" +
            "  <p>%s</p>\n" +
            "</td>\n";

    /*
        Parameters are
        %s - target URL         ... http://brunel.jupyter.ninja:9080/brunel-service/docs/
        %s - description        ... this is a chart that ....
        %s - thumbnail image    ... bggtreemap.png
        %s - title              ... Title for the first one
     */

    public static final String GALLERY = "/org/brunel/app/gallery.txt";
    private int column;

    public static void main(String[] args) throws Exception {
        BuilderOptions options = BuilderOptions.make(args);
        new GalleryBuilder(options).run(GALLERY, ITEM_FORMAT);
    }

    public GalleryBuilder(BuilderOptions options) {
        super(options);
    }

    protected void run(String fileLoc, String itemFormat) throws Exception {

        out.append("<table>\n");
        super.run(fileLoc, itemFormat);
        if (column != 0) out.append("</row>\n");
        out.append("</table>\n");
        System.out.println(out);
        display.showInBrowser();

    }

    protected void show(Map<String, String> tags, String itemFormat) throws UnsupportedEncodingException {
        if (tags.isEmpty()) return;               // Nothing defined yet

        if (column == 0) {
            out.append("<tr>\n");
        }
    	super.show(tags, itemFormat);

        // Move to next item
        column++;
        if (column == 3) {
            column = 0;
            out.append("</tr>\n");
        }

    }



	@Override
	protected String format(String itemFormat, String target,
			String description, String image, String title, String brunel) {
		return String.format(itemFormat, target, description, image, title);
	}




}
