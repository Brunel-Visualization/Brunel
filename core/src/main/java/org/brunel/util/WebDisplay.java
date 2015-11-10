
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

import org.brunel.build.Builder;
import org.brunel.build.d3.D3Builder;
import org.brunel.build.util.BuilderOptions;
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

    // Read the static HTML resources to add in
    private static final String NAV_BASE = new Scanner(WebDisplay.class.getResourceAsStream(NAV_LOCATION), "UTF-8").useDelimiter("\\A").next();
    private static final String BASE = new Scanner(WebDisplay.class.getResourceAsStream(SINGLE_LOCATION), "UTF-8").useDelimiter("\\A").next();

    public static String writeHtml(D3Builder builder, int width, int height, java.util.List<String> moreHeaders, String brunel, String... titles) {
        String css = builder.getStyleOverrides();
        String js = (String) builder.getVisualization();
        String imports = builder.makeImports();
        if (width < 5) width = 800;
        if (height < 5) height = 600;

        String title = constructTitle(titles);
        String brunelTitle = constructBrunelTitle(brunel);

        String stylesheets = builder.makeStyleSheets();

        // This is a bit of a hack, throwing them on here
        if (moreHeaders != null)
            for (String s : moreHeaders) stylesheets += s + "\n";
        String result = BASE
                .replace("$WIDTH$", Data.formatNumeric((double) width, true))
                .replace("$HEIGHT$", Data.formatNumeric((double) height, true))
                .replace("$CSS$", stylesheets)
                .replace("$TITLE$", title).replace("$BRUNEL$", brunelTitle).replace("$STYLES$", css.trim()).replace("$SCRIPT$", js);

        result = result.replace("$IMPORTS$", imports);
        return result;
    }

    private static String constructBrunelTitle(String brunel) {
    	if (brunel == null) return "";
    	StringBuilder builder = new StringBuilder();
    	builder.append("<small>").append(brunel).append("</small>");
    	return builder.toString();
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

    public void buildOneOfMultiple(VisItem target, String groupName, String name, Dimension size, String version) {
        if (count == 0) writeToFile("index.html", NAV_BASE.replace("$TITLE$", displayBaseDir.getName()));
        String file = count + ".html";

        String text = count + ": " + groupName;
        menuString += "<a href=\"" + file + " \" target=\"content\">" + text + "</a><br>\n";
        writeToFile("menu.html", menuString + "</body></html>");

        try {
            count++;
            buildSingle(target, size.width, size.height, file, version, name);
        } catch (Exception ex) {
            ex.printStackTrace();
            buildError(file);
        }
    }

    public void buildSingle(VisItem target, int width, int height, String file, String remoteVersion, String... titles) {
        BuilderOptions options = new BuilderOptions();
        if (remoteVersion == null) {
            options.localResources = "../out";
        } else {
            options.version = remoteVersion;
        }
        D3Builder builder = D3Builder.make(options);
        builder.build(target, width, height);
        String html = writeHtml(builder, width, height, headers,null, titles);
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
