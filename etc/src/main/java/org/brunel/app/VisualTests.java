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

import org.brunel.action.Action;
import org.brunel.build.util.BuilderOptions;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.io.CSV;
import org.brunel.data.io.Serialize;
import org.brunel.data.Fields;
import org.brunel.util.Library;
import org.brunel.util.WebDisplay;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;

class VisualTests {

    private static final String TESTS_LOCATION = "/org/brunel/app/tests.txt";
    private static final String INDEX_LOCATION = "/org/brunel/app/test-html/test-index.html";

    private final Dataset baseball, cpi, whiskey, simple;             // Data sets to use
    private final Library library;                          // Library we build
    private final WebDisplay display;

    private VisualTests(BuilderOptions options) {
        baseball = serializeCheck(Dataset.make(readResourceAsCSV("baseball2004.csv")));
        cpi = serializeCheck(Dataset.make(readResourceAsCSV("UK_CPI.csv")));
        whiskey = serializeCheck(Dataset.make(readResourceAsCSV("whiskey.csv")));
        simple = serializeCheck(Dataset.make(readResourceAsCSV("simple.csv")));
        library = Library.custom();
        display = new WebDisplay(options, "Full Tests");
    }

    private Dataset serializeCheck(Dataset dataset) {
        return (Dataset) Serialize.deserialize(Serialize.serializeDataset(dataset));
    }

    private void run() throws Exception {
        // Read the file and break it up
        String text = new Scanner(WebDisplay.class.getResourceAsStream(TESTS_LOCATION), "UTF-8").useDelimiter("\\A").next();
        int libStart = text.indexOf("####");
        libStart = text.indexOf("\n", libStart + 1) + 1;
        int libEnd = text.indexOf("####", libStart);
        int testsStart = text.indexOf("\n", libEnd + 1);
        int testsEnd = text.lastIndexOf("####");
        String libraryItems = text.substring(libStart, libEnd).trim();
        String[] testItems = text.substring(testsStart, testsEnd).split("\n");

        library.addItems(new ByteArrayInputStream(libraryItems.getBytes("UTF-8")));

        int n = 0;
        for (String s : testItems)
            if (s.contains("::")) runTest(s, ++n);

        // Grab the javascript and write into it the number of tests
        String html = new Scanner(WebDisplay.class.getResourceAsStream(INDEX_LOCATION), "UTF-8").useDelimiter("\\A").next();
        html = html.replace("$MAX$", "" + n);
        File f = display.makeFile("index.html");
        OutputStream os = new FileOutputStream(f);
        os.write(html.getBytes("UTF-8"));
        os.close();

        display.showInBrowser();
    }

    private void runTest(String test, int index) {
        /*
                A full test has this format:

                    action dataset field,field,... [/ additional commands] [:: result [:: notes]]

                The action references the action to be called, the dataset and fields provide the required parameters.
                If additional commands are specified, they are applied after the action has been performed.
                'result' should be 'pass', 'fail' or  else which is treated as 'pass, but could be better'
                Notes are plain text.
         */

        // Split into the the three main pieces, separated by the "::" block
        String[] pieces = test.split("::");
        String main = pieces[0].trim();                                        // The main command
        String result = pieces.length > 1 ? pieces[1].trim() : "?";            // Pass, Fail or color note
        String comments = pieces.length > 2 ? pieces[2].trim() : "";           // Comments on the test

        // Handle the main action command, and any additional actions to be appended
        String actionCommand, additionalCommands;
        if (main.contains("/")) {
            int p = main.indexOf('/');
            actionCommand = main.substring(0, p).trim();
            additionalCommands = main.substring(p + 1).trim();
        } else {
            actionCommand = main;
            additionalCommands = null;
        }

        String[] command = actionCommand.split("\\s+");

        String actionName = command[0];
        Dataset base = getBase(command[1]);
        Field[] fields = getFields(command, base);

        Action a;
        if (actionName.equals("-")) {
            // No library item for this test
            a = Action.parse(additionalCommands);
        } else {
            a = library.make(actionName, fields);
            if (additionalCommands != null) a = a.append(Action.parse(additionalCommands));
        }

        result = result.substring(0, 1).toUpperCase() + result.substring(1);

        String id = String.format("test%04d", index);
        String name = String.format("%04d", index);

        int HEIGHT = 350;
        int WIDTH = 500;
        try {
            display.buildSingle(a.apply(base), WIDTH, HEIGHT, id + ".html", makeTitle(name, a, result), makeComments(comments, result));
        } catch (Exception e) {
            System.err.println("Error running test: " + test);
            e.printStackTrace();
        }
    }

    private String makeComments(String notes, String result) {
        return "<p style='" + resultColor(result) + ";margin:2px;margin-bottom:6px'>" + result + ": " + notes + "</p>";
    }

    private String makeTitle(String id, Action a, String result) {
        String s = a.toString();
        if (s.length() > 100) s = s.substring(0, 100) + "...";
        return "<h2 style='text-align:center;margin:2px;" + resultColor(result) + "'>" +
                "<span style='font-style:italic;color:#aaaaaa'>" + id + "</span>&nbsp;&nbsp;&nbsp;&nbsp;" + s + "</h2>";
    }

    private String resultColor(String result) {
        if (result.equalsIgnoreCase("pass")) return "color:green";
        if (result.equalsIgnoreCase("fail")) return "color:red";
        return "color:blue";
    }

    private Field[] getFields(String[] command, Dataset base) {
        Field[] fields = new Field[command.length - 2];
        for (int i = 0; i < fields.length; i++) {
            String name = command[i + 2];
            Field f = base.field(name, true);
            if (f == null) {
                char c = name.charAt(0);
                if (Data.isQuoted(name))
                    f = Fields.makeConstantField(name, Data.deQuote(name), Data.deQuote(name), base.rowCount());
                else if (Character.isJavaIdentifierStart(c))
                    throw new IllegalStateException("field name: " + name + " not found in " + command[1]);
                else
                    f = Fields.makeConstantField(name, name, Data.asNumeric(name), base.rowCount());
            }
            fields[i] = f;
        }
        return fields;
    }

    private Dataset getBase(String s) {
        if (s.equalsIgnoreCase("baseball") || s.equalsIgnoreCase("bb")) return baseball;
        else if (s.equalsIgnoreCase("whiskey") || s.equalsIgnoreCase("why")) return whiskey;
        else if (s.equalsIgnoreCase("cpi") || s.equalsIgnoreCase("cp")) return cpi;
        else if (s.equalsIgnoreCase("simple") || s.equalsIgnoreCase("si")) return simple;
        else throw new IllegalStateException("Bad data set: " + s);
    }

    public static void main(String[] args) throws Exception {
        VisualTests tests = new VisualTests(BuilderOptions.make(args));
        tests.run();
    }

    private Field[] readResourceAsCSV(String resourceName) {
        InputStream stream = VisualTests.class.getResourceAsStream("/org/brunel/app/data-csv/" + resourceName);
        Scanner scanner = new Scanner(stream).useDelimiter("\\A");
        String s = scanner.hasNext() ? scanner.next() : "";
        return CSV.read(s);
    }

}
