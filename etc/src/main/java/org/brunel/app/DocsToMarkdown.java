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

import org.brunel.data.Data;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import static java.nio.file.Files.readAllBytes;

/**
 * Take our online docs and build a single markdown document from them
 */
public class DocsToMarkdown {

    private static final int LINE_WIDTH = 100;

    public static void main(String[] args) throws Exception {
        String dirName = args.length == 0 ? "service/src/main/webapp/docs" : args[0];
        File dir = new File(dirName);
        if (!dir.isDirectory()) throw new IllegalArgumentException("Bad src directory: " + dir.getAbsolutePath());

        DocsToMarkdown dtm = new DocsToMarkdown(dir);
        System.out.println("Using: " + Data.join(dtm.files()));

        dtm.start();
        for (String s : dtm.files()) dtm.process(s);
        dtm.finish();
        dtm.show();
    }

    private final File outFile;
    private final List<String> files = new ArrayList<String>();     // Constituent files
    private final PrintWriter out;                                  // Write information here
    private int column;                                             // Index of the column we are on
    private boolean lastWasBlankLine;                               // true if we have added a blank line
    private boolean blankOK;                                        // True if we want a leading blank

    private DocsToMarkdown(File dir) throws Exception {
        outFile = new File(dir, "Brunel Documentation.md");
        this.out = new PrintWriter(outFile);
        File index = new File(dir, "index.html");
        String text = new String(readAllBytes(index.toPath()));
        int p = text.indexOf("<nav>");
        int q = text.indexOf("</nav>");
        addToFiles(text.substring(p, q));
    }

    private void addToFiles(String text) {
        String[] parts = text.split("load\\('");
        for (String p : parts) {
            int q = p.indexOf("'");
            if (q > 0) files.add(p.substring(0, q));
        }
    }

    private void ensureBlankLine() {
        if (lastWasBlankLine) return;
        if (column > 0) out.println();
        out.println();
        column = 0;
        lastWasBlankLine = true;
    }

    private void ensureNewLine() {
        if (column > 0) {
            out.println();
            column = 0;
        }
    }

    private void ensureSpace(String s) {
        if (s.startsWith(",") || s.startsWith(";") || s.startsWith(")") || s.startsWith(".")) return;
        if (column > 0 && blankOK) {
            output(" ");
            column++;
        }
    }

    private List<String> files() {
        return files;
    }

    private void finish() {
        out.close();
    }

    private void output(String s) {
        out.print(s);
    }

    private void process(String fName) throws IOException {
        File base = new File(outFile.getParent(), fName + ".html");
        String text = new String(readAllBytes(base.toPath()));
        String[] sections = text.split("<[hH]");
        for (String s : sections) processSection(s);
    }

    private void processBlock(String block) {
        boolean blockQuote = false;
        lastWasBlankLine = true;
        column = 0;
        blankOK = false;
        boolean inComments = false;
        boolean inCode = false;
        boolean inExamples = false;
        boolean inPre = false;

        String str = block.replaceAll(">", "> ").replaceAll("<", " <").replaceAll("<a[^>]*>", "<a> ");
        StringTokenizer tok = new StringTokenizer(str, " \t\n\r\f", true);
        while (tok.hasMoreTokens()) {
            String s = tok.nextToken();
            String t = s.toLowerCase();
            if (inPre) {
                if (t.equals("</pre>")) {
                    inPre = false;
                    continue;
                } else {
                    if (s.equals("\n")) output("\n    ");
                    else output(s);
                }
                continue;
            }
            if (s.length() == 1 && " \t\n\r\f".contains(s)) continue;
            if (inComments) {
                // Do nothing until end tag found
                if (s.equals("-->")) inComments = false;
            } else if (t.equals("<code>")) {
                ensureSpace(s);
                output("`");
                column++;
                inCode = true;
                blankOK = false;
            } else if (t.equals("</code>")) {
                output("`");
                column++;
                inCode = false;
                blankOK = true;
            } else if (t.equals("<b>") || t.equals("<strong>")) {
                ensureSpace(s);
                output("**");
                column++;
                blankOK = false;
            } else if (t.equals("</b>") || t.equals("</strong>")) {
                output("**");
                column++;
                blankOK = true;
            } else if (t.equals("<i>") || t.equals("<em>")) {
                ensureSpace(s);
                output("_");
                column++;
                blankOK = false;
            } else if (t.equals("</i>") || t.equals("</em>")) {
                output("_");
                column++;
                blankOK = true;
            } else if (t.equals("<pre>")) {
                ensureBlankLine();
                inExamples = false;
                inPre = true;
            } else if (t.startsWith("<br")) {
                output("  ");
                column += 2;
                ensureNewLine();
            } else if (t.startsWith("<p")) {
                if (t.equals("<p>")) inExamples = false;
                ensureBlankLine();
            } else if (t.equals("</p>") || t.equals("</pre>")) {
                ensureBlankLine();
                blockQuote = false;
            } else if (t.equals("<ul>") || t.equals("</ul>") || t.equals("<ol>") || t.equals("</ol")) {
                // Ensure we are on a new line
                ensureBlankLine();
            } else if (t.startsWith("<li>")) {
                ensureNewLine();
                output(" * ");
            } else if (t.equals("<a>") || t.equals("</a>") || t.equals("</li>")) {
                // Just ignored
            } else if (t.equals("<!--")) {
                // In a block comment
                inComments = true;
            } else if (t.startsWith("<")) {
                ensureNewLine();
                throw new IllegalStateException("UNHANDLED: " + s);
            } else if (t.equals("class=\"examples\">")) {
                ensureNewLine();
                if (!inExamples) output("<!-- examples -->\n\n");
                blockQuote = true;
                inExamples = true;
            } else {
                if ((!inCode || inExamples) && column + 1 + s.length() > LINE_WIDTH)    // No wrapping inside `` quotes
                    ensureNewLine();
                else
                    ensureSpace(s);
                if (column == 0 && blockQuote) output("    ");
                s = s.replaceAll("&gt;", ">").replaceAll("&lt;", "<");
                output(s);
                column += s.length();
                lastWasBlankLine = false;
                blankOK = true;
            }

        }
    }

    private void processSection(String s) {
        if (s.isEmpty()) return;
        char h = s.charAt(0);                                       // header level number "1", "2", "3", etc.
        int p = s.toLowerCase().indexOf("</h");
        if (p >= 0) {
            String title = s.substring(2, p).trim();
            if (h == '1') {
                // Underlined title
                out.println(title);
                for (int i = 0; i < title.length(); i++) output("-");
                out.println();
            } else {
                ensureBlankLine();
                output("### ");
                processBlock(title);
                out.println();
            }

            s = s.substring(p + 5);
        }
        processBlock(s);

        out.println();

    }

    private void show() throws IOException {
        Desktop.getDesktop().open(outFile);
    }

    private void start() {
        out.println("Brunel Documentation");
        out.println("====================");
        out.println();
    }
}
