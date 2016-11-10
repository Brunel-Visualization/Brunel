
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

package org.brunel.util;

import org.brunel.build.d3.D3Builder;
import org.brunel.build.util.BuilderOptions;
import org.brunel.model.VisItem;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Scanner;

/**
 * A utility class for creating small web applications fired off from Java
 */
public class WebDisplay {

	// Read the static HTML resources to add in
	private static final String NAV_LOCATION = "/org/brunel/util/webdisplay-navigation.html";
	private static final String NAV_BASE = new Scanner(WebDisplay.class.getResourceAsStream(NAV_LOCATION), "UTF-8").useDelimiter("\\A").next();

	public static String writeHtml(D3Builder builder, int width, int height, String brunel, String... titles) {
		StringWriter writer = new StringWriter();
		PageOutput output = new PageOutput(builder, writer);
		output.addTitles(titles);
		if (brunel != null) output.addFooters("<small>" + PageOutput.escapeHTML(brunel) + "</small>");
		output.write();
		return writer.toString();
	}

	private final File displayBaseDir;
	private final BuilderOptions options;

	private int count;
	private String menuString = "<html><head><title>Charts</title></head><body>\n";

	public WebDisplay(BuilderOptions options, String dirName) {
		this.options = options;
		if (isLocalReference(options.locJavaScript)) LocalOutputFiles.install();
		displayBaseDir = LocalOutputFiles.makeDirectory(dirName);
	}

	private boolean isLocalReference(String location) {
		return location.startsWith("file:");
	}

	public File makeFile(String s) {
		return new File(displayBaseDir, s);
	}

	public void buildOneOfMultiple(VisItem target, String groupName, String name, Dimension size) {
		if (count == 0) writeToFile("index.html", NAV_BASE.replace("$TITLE$", displayBaseDir.getName()));
		String file = count + ".html";

		String text = count + ": " + groupName;
		menuString += "<a href=\"" + file + " \" target=\"content\">" + text + "</a><br>\n";
		writeToFile("menu.html", menuString + "</body></html>");

		try {
			count++;
			buildSingle(target, size.width, size.height, file, name);
		} catch (Exception ex) {
			buildError(file, ex);
		}
	}

	public void buildSingle(VisItem target, int width, int height, String file, String... titles) {
		D3Builder builder = D3Builder.make(options);
		builder.build(target, width, height);
		String html = writeHtml(builder, width, height, null, titles);
		writeToFile(file, html);
	}

	public void showInBrowser() {
		try {
			Desktop desktop = Desktop.getDesktop();
			desktop.browse(new File(displayBaseDir, "index.html").toURI());
		} catch (Throwable ex) {
			// Silently fail
		}
	}

	private void buildError(String file, Exception ex) {
		try {
			StringBuilder b = new StringBuilder();
			b.append("<html><head><title>Brunel Error</title></head><body><h2>Error Building Brunel</h2>\n");
			b.append("<p>").append(PageOutput.escapeHTML(ex.getMessage())).append("</p>\n");
			b.append("<ul>\n");
			for (StackTraceElement e : ex.getStackTrace())
				b.append("<li>").append(PageOutput.escapeHTML(e.toString())).append("</li>\n");
			b.append("</ul>\n</body></html>");
			writeToFile(file, b.toString());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void writeToFile(String fileName, String html) {
		try {
			File file = new File(displayBaseDir, fileName);
			PrintWriter item = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
			item.println(html);
			item.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
