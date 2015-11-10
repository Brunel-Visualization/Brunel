
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

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.InputStream;

public class Common {

    /* Font */
    public static final Font MEDIUM = new Font("Inconsolata, Arial", Font.PLAIN, 12);

    /* Watson colors */
    public static final Color BLUE3 = new Color(0, 178, 239);
    public static final Color BLUE8 = new Color(0, 25, 52);
    public static final Color GREEN2 = new Color(23, 175, 75);

    /* Border sizes */
    public static final int BORDER = 10;
    private static final Font BIG = new Font("Inconsolata, Arial", Font.BOLD, 14);

    public static JMenuItem makeMenuItem(String s, ActionListener listener) {
        final JMenuItem item = new JMenuItem(s);
        item.setBackground(BLUE8);
        item.setForeground(BLUE3);
        item.setFont(MEDIUM);
        item.addActionListener(listener);
        return item;
    }

    public static JButton makeIconButton(Icon icon, String tooltip) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setBackground(BLUE8);
        button.setForeground(BLUE3);
        button.setBorder(BorderFactory.createMatteBorder(5, 5, 5, 5, new Color(0, 0, 0, 0)));
        button.setFont(BIG);
        return button;
    }

    public static Icon make(String title) {
        String name = "/org/brunel/app/ui-icons/" + title;
        InputStream is = Common.class.getResourceAsStream(name);
        if (is == null) is = Common.class.getResourceAsStream(name + ".png");
        if (is == null) is = Common.class.getResourceAsStream(name + ".gif");
        if (is == null) is = Common.class.getResourceAsStream(name + ".jpg");
        try {
            return new ImageIcon(ImageIO.read(is));
        } catch (Exception ex) {
            throw new RuntimeException("Error reading resource: " + name, ex);
        }
    }

    /**
     * Gets a version defined in the arguments
     */
    public static String getVersion(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("-v") || args[i].equals("-version"))
                return args[i + 1];
        }
        return null;
    }
}
