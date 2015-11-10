
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

import org.brunel.model.VisException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;

class ExceptionDialog extends JDialog implements ActionListener {

    private static ExceptionDialog dialog;

    public static void showError(Throwable e, JFrame owner) {
        if (dialog == null) dialog = new ExceptionDialog(owner);
        dialog.setDetails(e);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        dialog.ok.requestFocusInWindow();
        dialog.ok.requestFocus();
    }

    private final String packageStart;          // Start name of this class's package

    private void setDetails(Throwable e) {

        if (e instanceof VisException) {
            VisException b = (VisException) e;
            type.setText("Error " + b.getType());
            description.setText(b.getShortMessage());
            details.setText("Processing: " + b.getSource() + "\n\n" + getDetails(e));
        } else {
            type.setText(getErrorType(e));
            description.setText(e.getMessage());
            details.setText(getDetails(e));
        }
        e.printStackTrace();
    }

    private String getErrorType(Throwable e) {
        if (e instanceof IOException) return "I/O Error";
        if (e instanceof OutOfMemoryError) return "Insufficient Memory";
        return "Internal Error (" + e.getClass().getSimpleName() + ")";
    }

    private String getDetails(Throwable e) {
       if (e instanceof NullPointerException) {
            return "A Programming error caused a null exception" + getWhere(e.getStackTrace());
        } else if (e instanceof IOException) {
            return "An exception was raised during an i/o call" + getWhere(e.getStackTrace());
        } else if (e instanceof RuntimeException) {
            return "A runtime error (" + e.getClass().getSimpleName() + ") was raised" + getWhere(e.getStackTrace());
        } else {
            return "An error (" + e.getClass().getSimpleName() + ") was raised" + getWhere(e.getStackTrace());
        }
    }

    private String getWhere(StackTraceElement[] trace) {
        // Search first for something in our package, then for any non-java, then just use the first
        for (StackTraceElement e : trace)
            if (e.getClassName().startsWith(packageStart)) return getWhere(e);
        for (StackTraceElement e : trace)
            if (!e.getClassName().startsWith("java")) return getWhere(e);
        return getWhere(trace[0]);

    }

    private String getWhere(StackTraceElement e) {
        String main = "\n\nMethod:\t" + e.getClassName() + "." + e.getMethodName();
        if (e.getFileName() != null)
            return main + "\nFile:\t" + e.getFileName() + "\nLine:\t" + e.getLineNumber();
        else
            return main;
    }

    private final JLabel type;
    private final JLabel description;
    private final JTextPane details;
    private final JButton ok;

    private ExceptionDialog(JFrame parent) {
        super(parent, "Error", true);
        setResizable(false);
        String s = getClass().getName();
        int p = s.indexOf('.');
        p = s.indexOf('.', p + 1);
        this.packageStart = s.substring(0, p);

        JPanel main = new JPanel(new GridBagLayout());
        setContentPane(main);

        GridBagConstraints cons = new GridBagConstraints();
        cons.gridx = 0;

        type = new JLabel("<Type Goes Here>");
        description = new JLabel("<Description Goes Here>");
        details = new JTextPane();
        cons.fill = WIDTH;
        main.add(type, cons);
        main.add(description, cons);
        main.add(details, cons);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        buttons.setOpaque(false);
        ok = new JButton("OK");
        ok.addActionListener(this);
        buttons.add(ok);

        main.add(buttons, cons);

        details.setEditable(false);
        ok.setMnemonic(KeyEvent.VK_ENTER);
        getRootPane().setDefaultButton(ok);

        setLookAndFeel(main, Font.PLAIN, 12);
        setLookAndFeel(type, Font.ITALIC, 14);
        setLookAndFeel(description, Font.BOLD, 14);
        setLookAndFeel(details, Font.PLAIN, 12);
        setLookAndFeel(ok, Font.PLAIN, 12);
    }

    private void setLookAndFeel(JComponent c, int fontStyle, int fontSize) {
        c.setFont(new Font("Helvetica Neue", fontStyle, fontSize));
        if (!(c instanceof JButton)) {
            c.setBackground(Color.DARK_GRAY);
            c.setForeground(Color.WHITE);
            c.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        }
    }

    public void actionPerformed(ActionEvent e) {
        setVisible(false);
    }

}
