
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

package org.brunel.util;

import org.brunel.build.Builder;
import org.brunel.build.controls.ControlWriter;
import org.brunel.build.controls.Controls;
import org.brunel.build.d3.D3Builder;
import org.brunel.data.Data;
import org.brunel.model.VisItem;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * A utility class for creating small web applications fired off from Java
 */
public class WebDisplay {

    private static final String NAV_LOCATION = "/org/brunel/util/webdisplay-navigation.html";
    private static final String SINGLE_LOCATION = "/org/brunel/util/webdisplay-single.html";
    private static final String COPYRIGHT_COMMENTS = "<!--\n" +
            "\tD3 Copyright © 2012, Michael Bostock\n" +
            "\tjQuery Copyright © 2010 by The jQuery Project\n" +
            "\tsumoselect Copyright © 2014 Hemant Negi\n " +
            "-->\n";

    // Read the static HTML resources to add in
    private static final String NAV_BASE = new Scanner(WebDisplay.class.getResourceAsStream(NAV_LOCATION), "UTF-8").useDelimiter("\\A").next();
    private static final String BASE = new Scanner(WebDisplay.class.getResourceAsStream(SINGLE_LOCATION), "UTF-8").useDelimiter("\\A").next();

    public static String writeHtml(String css, String js, int width, int height, String baseDir, java.util.List<String> moreHeaders, Controls controls, String... titles) {
        String sumoDir = baseDir + "/sumoselect";
        String title = constructTitle(titles);
        String top = "<link rel=\"stylesheet\" type=\"text/css\" href=\"" + baseDir + "/BrunelBaseStyles.css\">\n" +
                "<link rel=\"stylesheet\" type=\"text/css\" href=\"http://code.jquery.com/ui/1.11.4/themes/smoothness/jquery-ui.css\">\n" +
                "<link rel=\"stylesheet\" type=\"text/css\" href=\"" + sumoDir + "/sumoselect.css\">\n";
        if (moreHeaders != null)
            for (String s : moreHeaders) top += s + "\n";
        String result = BASE
                .replace("$WIDTH$", Data.formatNumeric((double) width, true))
                .replace("$HEIGHT$", Data.formatNumeric((double) height, true))
                .replace("$CSS$", top)
                .replace("$TITLE$", title).replace("$STYLES$", css.trim()).replace("$SCRIPT$", js);
        if (controls.filters.isEmpty()) {
            // Controls are not needed, so we need fewer libraries
            result = result.replace("$IMPORTS$", COPYRIGHT_COMMENTS + D3Builder.imports(
                    "http://cdnjs.cloudflare.com/ajax/libs/d3/3.5.5/d3.min.js",
                    baseDir + "/BrunelData.js",
                    baseDir + "/BrunelD3.js"
            ));
        } else {
            // Controls are needed, so we need more libraries
            result = result.replace("$IMPORTS$", COPYRIGHT_COMMENTS + D3Builder.imports(
                    "http://cdnjs.cloudflare.com/ajax/libs/d3/3.5.5/d3.min.js",
                    "http://code.jquery.com/jquery-1.10.2.js",
                    "http://code.jquery.com/ui/1.11.4/jquery-ui.js",
                    baseDir + "/BrunelData.js",
                    baseDir + "/BrunelD3.js",
                    baseDir + "/BrunelEventHandlers.js",
                    baseDir + "/BrunelJQueryControlFactory.js",
                    sumoDir + "/jquery.sumoselect.min.js"
            ));
        }
        return result;
    }

    private static String constructTitle(String[] titles) {
        if (titles == null) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < titles.length; i++) {
            String a = titles[i];
            if (a.startsWith("<")) {
                builder.append(a);                // Pre-formatted -- do nothing
            } else if (i == 0) {
                builder.append("<h2>").append(a).append("</h2>");
            } else {
                builder.append("<p>").append(a).append("</p>");
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private final File displayBaseDir;
    private static File home;            // Base directory
    private static File out;             // Main code location

    private int count = 0;
    private String menuString = "<html><head><title>Charts</title></head><body>\n";
    private final ArrayList<String> headers = new ArrayList<String>();

    public WebDisplay(String dirName) {
        // Set 'home' and 'out' directories
        ensureBaseDirectoriesBuilt();

        // Copy source files and resources over
        ensureResourceExists("BrunelD3.js");
        ensureResourceExists("BrunelData.js");
        ensureResourceExists("BrunelBaseStyles.css");
        ensureResourceExists("BrunelEventHandlers.js");
        ensureResourceExists("BrunelJQueryControlFactory.js");
        ensureResourceExists("sumoselect/jquery.sumoselect.min.js");
        ensureResourceExists("sumoselect/sumoselect.css");

        displayBaseDir = makeDir(dirName);
    }

    public void addHeader(String s) {
        headers.add(s);
    }

    public File makeFile(String s) {
        return new File(displayBaseDir, s);
    }

    private void ensureBaseDirectoriesBuilt() {
        if (home != null) return;

        // Try special directory location, but if that is not defined, add to user's home directory
        String brunelDir = System.getProperty("brunel.home");
        if (brunelDir != null)
            home = new File(brunelDir);
        else
            home = new File(System.getProperty("user.home"), "brunel");
        home.mkdirs();
        if (!home.canWrite())
            throw new IllegalArgumentException("Cannot write to the Locations directory: " + home.getAbsolutePath());
        out = new File(home, "out");
        out.mkdir();
        if (!out.canWrite())
            throw new IllegalArgumentException("Cannot write to the Locations directory: " + out.getAbsolutePath());

        // 3rd party JS folders
        new File(out, "/sumoselect").mkdirs();
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
            ex.printStackTrace();
            buildError(file);
        }
    }

    public void buildSingle(VisItem target, int width, int height, String file, String... titles) {
        D3Builder builder = new D3Builder("visualization");
        builder.build(target, width, height);
        String css = builder.getStyleOverrides();
        String js = (String) builder.getVisualization();
        Controls controls = builder.getControls();
        js += new ControlWriter("controls", "BrunelJQueryControlFactory").write(controls);
        String html = writeHtml(css, js, width, height, "../out", headers, controls, titles);
        writeToFile(file, html);
    }

    public File makeDir(String name) {
        File f = new File(home, name);
        f.mkdirs();
        if (!f.canWrite())
            throw new IllegalArgumentException("Cannot write to the Locations directory: " + f.getAbsolutePath());
        return f;
    }

    public void showInBrowser() {
        try {
            Desktop desktop = Desktop.getDesktop();
            desktop.browse(new File(displayBaseDir, "index.html").toURI());
        } catch (Throwable ex) {
            // Silently fail
        }
    }

    private void buildError(String file) {
        try {
            writeToFile(file, BASE.replace("$SCRIPT$", "document.write('Error');"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void ensureResourceExists(String resourceName) {
        // Copy required static JS from Brunel project
        try {
            InputStream is = Builder.class.getResourceAsStream("/javascript/" + resourceName);
            // If the file is a translated file, we look for it in either a jar location or in the file system
            if (is == null)
                is = Builder.class.getResourceAsStream("/translated/" + resourceName);
            if (is == null) {
                File file = new File("data/build/translated/");
                if (!file.exists()) file = new File("../data/build/translated/");
                is = new FileInputStream(new File(file, resourceName));
            }

            File brunelD3 = new File(out, resourceName);
            Files.copy(is, brunelD3.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Could not copy required " + resourceName + " to output folder: " + out, e);
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
