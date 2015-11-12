
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

package org.brunel.app.brunel;

import org.brunel.action.Parser;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.ActionEvent;

class ActionEditorPane extends JTextPane implements DocumentListener, Runnable {

    private final Style defaultStyle;
    private final Parser parser = new Parser();

    public ActionEditorPane(final AppEventListener appEventListener) {
        setBackground(Common.BLUE8);
        setBorder(BorderFactory.createMatteBorder(5, 5, 5, 5, Common.BLUE8));
        setForeground(Color.lightGray);
        setCaretColor(Common.GREEN2);
        setFont(new Font("Monospaced", Font.PLAIN, 14));

        DefaultStyledDocument sc = (DefaultStyledDocument) getDocument();

        defaultStyle = sc.getStyle(StyleContext.DEFAULT_STYLE);

        // Styles are: error, name, syntax, field, option, number, string

        Style errorStyle = sc.addStyle("?", defaultStyle);
        StyleConstants.setUnderline(errorStyle, true);
        StyleConstants.setForeground(errorStyle, Color.red);

        Style commandStyle = sc.addStyle("name", defaultStyle);
        StyleConstants.setForeground(commandStyle, Color.white);
        StyleConstants.setBold(commandStyle, true);

        Style syntaxStyle = sc.addStyle("syntax", defaultStyle);
        StyleConstants.setForeground(syntaxStyle, Color.gray);

        Style fieldStyle = sc.addStyle("field", defaultStyle);
        StyleConstants.setForeground(fieldStyle, Color.yellow);

        Style specialFieldStyle = sc.addStyle("option", defaultStyle);
        StyleConstants.setForeground(specialFieldStyle, Common.GREEN2);
        StyleConstants.setItalic(specialFieldStyle, true);
        StyleConstants.setBold(specialFieldStyle, true);
        sc.addStyle("list", specialFieldStyle);


        Style numberStyle = sc.addStyle("number", defaultStyle);
        StyleConstants.setForeground(numberStyle, Common.GREEN2);
        StyleConstants.setBold(numberStyle, true);

        Style stringStyle = sc.addStyle("string", defaultStyle);
        StyleConstants.setForeground(stringStyle, Common.GREEN2);
        StyleConstants.setItalic(stringStyle, true);

        getDocument().addDocumentListener(this);

        getActionMap().put(getInputMap().get(KeyStroke.getKeyStroke("ENTER")), new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                appEventListener.handleEvent("define action", ActionEditorPane.this, getText());
            }
        });
    }

    public void insertUpdate(DocumentEvent e) {
        SwingUtilities.invokeLater(this);
    }

    public void removeUpdate(DocumentEvent e) {
        SwingUtilities.invokeLater(this);
    }

    public void changedUpdate(DocumentEvent e) {
        // Do not care
    }

    public void run() {

        String text = getText();
        if (text.length() < 1) return;

        // Tokenize the text, then parse to assign actions and types
        java.util.List<Parser.BrunelToken> tokens = parser.tokenize(text);
        try {
            setToolTipText(null);
            parser.makeActionFromTokens(tokens, text);
        } catch (Exception e) {
            setToolTipText(e.getMessage());
        }

        // Colorize
        DefaultStyledDocument d = (DefaultStyledDocument) getDocument();
        d.setCharacterAttributes(0, text.length(), defaultStyle, true);
        for (Parser.BrunelToken token : tokens) {
            d.setCharacterAttributes(token.start, token.end - token.start, getStyle(token.parsedType), false);
        }

    }

}
