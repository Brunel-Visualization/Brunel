/*
 * Copyright (c) 2016 IBM Corporation and others.
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

import org.brunel.build.VisualizationBuilder;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/**
 * Ensures JS files needed for local operations are in the right places
 */
public class LocalOutputFiles {

    private static final List<String> RESOURCES = Arrays.asList(
            "BrunelD3.js", "BrunelData.js", "Brunel.css",
            "BrunelEventHandlers.js", "BrunelJQueryControlFactory.js",
            "sumoselect/jquery.sumoselect.min.js", "sumoselect/sumoselect.css");

    private final File home, out;           // Home and output resources directory

    public static void install() {
        for (String s : RESOURCES) INSTANCE.ensureResourceExists(s);
    }

    public static File makeDirectory(String dirName) {
        return INSTANCE.ensureWritable(new File(INSTANCE.home, dirName));
    }

    private static final LocalOutputFiles INSTANCE = new LocalOutputFiles();

    private LocalOutputFiles() {
        home = getHomeDirectory();                      // Top level brunel output directory
        out = ensureWritable(new File(home, "out"));    // for the standard parts
        new File(out, "/sumoselect").mkdirs();          // For the 3rd part JS items
    }

    public static Writer makeFileWriter(String fileName) {
        File f = new File(INSTANCE.home, fileName);
        try {
            f.getParentFile().mkdirs();
            return new OutputStreamWriter(new FileOutputStream(f), "utf-8");
        } catch (Exception e) {
            throw new RuntimeException("Error creating file to write to: " + f.getAbsolutePath());
        }
    }

    public static void showInBrowser(String location) {
        File file = new File(INSTANCE.home, location);
        try {
            Desktop.getDesktop().browse(file.toURI());
        } catch (Throwable ex) {
            throw new RuntimeException("Failed to show file in browser: " + file,  ex);
        }
    }

    private File ensureWritable(File f) {
        f.mkdirs();
        if (!f.canWrite())
            throw new IllegalArgumentException("Cannot write to the directory: " + f.getAbsolutePath());
        return f;
    }

    private File getHomeDirectory() {
        // Try special directory location, but if that is not defined, add to user's home directory
        String brunelDir = System.getProperty("brunel.home");
        File home = brunelDir != null ? new File(brunelDir) :
                new File(System.getProperty("user.home"), "brunel");
        return ensureWritable(home);
    }

    private void ensureResourceExists(String resourceName) {
        try {
			// Either we are running form the IDE, in which case we find the file in the file system,
			// Or we are in a jar, in which case it should be in the indicated directory
			InputStream is = VisualizationBuilder.class.getResourceAsStream("/readable/" + resourceName);
            if (is == null) {
                File file = new File("out/javascript/readable");
                if (!file.exists()) file = new File("../out/javascript/readable");
                is = new FileInputStream(new File(file, resourceName));
            }
            Files.copy(is, new File(out, resourceName).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Could not copy required " + resourceName + " to output folder: " + out, e);
        }
    }

}
