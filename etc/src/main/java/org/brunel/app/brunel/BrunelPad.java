
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

import org.brunel.build.util.BuilderOptions;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.model.VisException;
import org.brunel.model.VisItem;
import org.brunel.util.Library;
import org.brunel.util.WebDisplay;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("serial")
public class BrunelPad extends JFrame implements AppEventListener, SourceTransfer.Droppable {

    /* use '-v version' to use a minified online library version */
    public static void main(String[] args) {
        BuilderOptions options = BuilderOptions.make(args);
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (final Exception ignored) {
            // I guess we won't have anything nice
        }

        new BrunelPad(options).start();
    }

    private final Settings settings;
    private final ActionEditorPane actionEditor;
    private final SourcePanel sourcePanel;
    private final List<String> history = new ArrayList<String>();
    private final BuilderOptions options;
    private Dataset base;
    private org.brunel.action.Action action;
    private org.brunel.action.Action transitory;

    private BrunelPad(BuilderOptions options) {
        super("BrunelPad");
        this.options = options;
        settings = new Settings("BrunelPad");
        settings.persistWindowLocation(this, "main", 50, 50, 800, 800);
        setTitle(null);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        actionEditor = new ActionEditorPane(this);
        sourcePanel = new SourcePanel(this);
        try {
            buildGUI();
        } catch (final Throwable t) {
            t.printStackTrace();
            System.exit(-1);
        }

        String historyStored = settings.getString("history");
        if (historyStored != null) history.addAll(Arrays.asList(historyStored.split("\\|\\+\\|")));

    }

    public void handleEvent(String tag, Object source, Object item) {
        if (tag.equals("select fields"))
            showFields((Field[]) item);
        else if (tag.equals("select source"))
            useSource((Dataset) item);
        else if (tag.equals("error"))
            error((Throwable) item);
        else if (tag.equals("define action"))
            setAction((String) item);
    }

    public boolean handleFile(File file) {
        try {
            byte[] text = Files.readAllBytes(file.toPath());
            return handleText(new String(text));
        } catch (Exception ex) {
            error(ex);
            return false;
        }
    }

    public boolean handleText(String text) {
        try {
            actionEditor.setText(action.toString());
            setAction(actionEditor.getText());

            return true;
        } catch (Exception ex) {
            error(ex);
            return false;
        }

    }

    private void initialize() {
        final String s = settings.getString("last-source");
        if (s != null) {
            URI u = URI.create(s);
            sourcePanel.handleFile(new File(u));
        }
    }

    public void setTitle(String title) {
        if (title == null)
            super.setTitle(settings.name);
        else
            super.setTitle(settings.name + ": " + title);
    }

    private void error(Throwable e) {
        ExceptionDialog.showError(e, this);
    }

    private void start() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setVisible(true);
                initialize();
            }
        });
    }

    private void addToHistory(String descr) {
        history.remove(descr);
        history.add(0, descr);
        String value = historyAsString();

        while (history.size() > 15 || value.length() > 8000) {
            history.remove(history.size() - 1);
            value = historyAsString();
        }

        settings.putString("history", value);
    }

    private String historyAsString() {
        StringBuilder b = new StringBuilder();
        for (String s : history) {
            if (b.length() > 0) b.append("|+|");
            b.append(s);
        }
        return b.toString();
    }

    private void buildDescription() {
        actionEditor.getActionMap().put(actionEditor.getInputMap().get(KeyStroke.getKeyStroke("ENTER")), new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                String text = actionEditor.getText();
                setAction(text);
                history.add(text);
            }
        });
    }

    private void buildGUI() {

        GridBagConstraints cons = new GridBagConstraints();
        cons.weightx = 1.0;
        cons.weighty = 1.0;
        cons.fill = GridBagConstraints.BOTH;
        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setMinimumSize(new Dimension(200, 50));
        bottom.setPreferredSize(new Dimension(2000, 50));
        bottom.add(actionEditor, cons);
        cons.weightx = 0.0;
        JComponent historyButton = Common.makeIconButton(Common.make("copytext"), "History");
        historyButton.addMouseListener(new MouseAdapter() {

            public void mouseClicked(MouseEvent e) {
                JPopupMenu p = new JPopupMenu("History");
                p.setBackground(Common.BLUE8);
                for (final String s : history)
                    p.add(Common.makeMenuItem(s, new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            setAction(s);
                        }
                    }));
                p.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        bottom.add(historyButton, cons);

        buildDescription();
        bottom.setOpaque(false);

        JSplitPane content = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        content.setDividerLocation(0.6666);
        content.setResizeWeight(0.6666);
        setContentPane(content);

        content.setBorder(BorderFactory.createMatteBorder(Common.BORDER, Common.BORDER,
                Common.BORDER, Common.BORDER, Color.black));
        content.setBackground(Color.black);
        content.add(sourcePanel);
        content.add(bottom);
    }

    private void setAction(String text) {
        try {
            action = org.brunel.action.Action.parse(text);
            transitory = null;
            updateVis();
        } catch (Throwable ex) {
            error(ex);
        }

    }

    private void setAction(org.brunel.action.Action a) {
        try {
            action = a;
            transitory = null;
            updateVis();
        } catch (Throwable ex) {
            error(ex);
        }

    }

    private void showFields(Field[] fields) {
        org.brunel.action.Action a = Library.choose(fields);
        setAction(a);

    }

    private void updateVis() {
        if (base == null || action == null) return;
        try {
            org.brunel.action.Action a = action;
            if (transitory != null) a = a.append(transitory);
            VisItem item = a.apply(base);

            String error = item.validate();
            if (error != null) {
                throw VisException.makeApplying(new IllegalStateException(error), action.toString());
            }

            String descr = a.simplify().toString();
            actionEditor.setText(descr);
            int width = getWidth() - 30;
            Dimension size = new Dimension(width, (int) (width / 1.618));

            WebDisplay display = new WebDisplay("BrunelPad");
            display.buildSingle(item, size.width, size.height, "index.html", options, "<h2 style='text-align:center'>" + a.toString() + "</h2>");
            display.showInBrowser();

            if (transitory == null) addToHistory(descr);
        } catch (Throwable e) {
            error(e);
        }
    }

    private void useSource(Dataset source) {
        base = source;
        sourcePanel.setSource(source);
        if (source.stringProperty("uri") != null) settings.putString("last-source", source.stringProperty("uri"));
        setTitle(source.name());
        transitory = null;
        updateVis();
    }

}
