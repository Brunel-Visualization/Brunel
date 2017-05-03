
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

import org.brunel.app.brunel.SourceTransfer.Droppable;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.io.CSV;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.TooManyListenersException;

@SuppressWarnings("serial")
 public class SourcePanel extends JPanel implements Droppable {

    public final JList<Field> list = new JList<>();
    private final AppEventListener listener;

    public SourcePanel(final AppEventListener listener) {
        super(new BorderLayout());
        this.listener = listener;
        initializeList();

        // Annoying issue with mouse release outside component needs this kludge to avoid triggering fake select events
        list.addListSelectionListener(new ListSelectionListener() {
            public int[] selected = new int[0];
            boolean ignoreEvent;

            public void valueChanged(ListSelectionEvent e) {
                if (ignoreEvent || e.getValueIsAdjusting()) return;
                Point p = MouseInfo.getPointerInfo().getLocation();
                SwingUtilities.convertPointFromScreen(p, list);
                if (!list.contains(p)) {
                    // Should not modify the fields
                    ignoreEvent = true;
                    list.setSelectedIndices(selected);
                    ignoreEvent = false;
                    return;
                }
                listener.handleEvent("select fields", SourcePanel.this, getSelectedFields());
                this.selected = list.getSelectedIndices();
            }
        });


        SourceTransfer dropHandler = new SourceTransfer(this);
        setTransferHandler(dropHandler);
        try {
            getDropTarget().addDropTargetListener(dropHandler);
        } catch (TooManyListenersException ex) {
            //Silently fail, but not sure this can actually happen
        }
    }

    protected void initializeList() {
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setBackground(Common.BLUE8);
        list.setBorder(BorderFactory.createMatteBorder(5, 5, 5, 5, Common.BLUE8));
        list.setForeground(Common.BLUE3);
        list.setFont(Common.MEDIUM);
        list.setAutoscrolls(true);
        JScrollPane scroller = new JScrollPane(list);
        add(scroller);
        scroller.setBorder(null);
        scroller.setBackground(Common.BLUE8);
        scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    }

    private Field[] getSelectedFields() {
        List<Field> f = list.getSelectedValuesList();
        return f.toArray(new Field[f.size()]);
    }

    public boolean handleFile(final File f) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    String content = new String(Files.readAllBytes(f.toPath()), "utf-8");
                    Field[] fields = CSV.read(content);
                    Dataset source = Dataset.make(fields);
                    String name = f.getName();
                    if (name.contains(".")) name = name.substring(0, name.indexOf('.'));
                    name = asIdentifier(name);
                    source.set("name", name);
                    source.set("uri", f.toURI().toString());
                    setSource(source);
                    listener.handleEvent("select source", SourcePanel.this, source);
                } catch (Throwable e) {
                    listener.handleEvent("error", SourcePanel.this, e);
                }
            }
        });
        return true;
    }

    private static String asIdentifier(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '$')
                sb.append('_');
            else if (Character.isJavaIdentifierPart(c))
                sb.append(c);
            else
                sb.append('_');
        }
        String t = sb.toString().replaceAll("__+", "_");
        if (t.startsWith("_")) t = t.substring(1);
        if (t.endsWith("_")) t = t.substring(0, t.length() - 1);
        return t;
    }

    public boolean handleText(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    Field[] fields = CSV.read(text);
                    Dataset source = Dataset.make(fields);
                    setSource(source);
                    listener.handleEvent("select source", SourcePanel.this, source);
                } catch (Throwable e) {
                    listener.handleEvent("error", SourcePanel.this, e);
                }
            }

        });
        return true;
    }

    private Dataset getSource() {
        if (list.getModel() instanceof SourceListModel)
            return ((SourceListModel) list.getModel()).source;
        else
            return null;
    }

    public void setSource(Dataset source) {
        if (source == getSource()) return;
        list.setModel(new SourceListModel(source));
    }

}
