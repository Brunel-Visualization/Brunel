
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

import org.brunel.data.Field;
import org.brunel.library.Library;
import org.brunel.library.LibraryAction;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

@SuppressWarnings("serial")
 public class LibraryPanel extends JPanel {

    public final JList<LibraryAction> list = new JList<>();
    private final AppEventListener listener;
    private final Library library = Library.standard();


    public LibraryPanel(final AppEventListener listener) {
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
                listener.handleEvent("select action", LibraryPanel.this,list.getSelectedValue());
            }
        });

        list.setCellRenderer(new LibraryActionRenderer());
    }

	public void setTargetFields(Field[] fields) {
		LibraryAction[] choices = library.chooseAction(fields);
		list.setListData(choices);

	}

	protected void initializeList() {
    	list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setLayoutOrientation(JList.VERTICAL);
        list.setBackground(Common.BLUE8);
        list.setBorder(BorderFactory.createMatteBorder(5, 5, 5, 5, Common.BLUE8));
        list.setForeground(Common.GREEN2);
        JScrollPane scroller = new JScrollPane(list);
        add(scroller);
        scroller.setBorder(null);
        scroller.setBackground(Common.BLUE8);
        scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setMinimumSize(new Dimension(120, 20));
		scroller.setMaximumSize(new Dimension(120, 2000));
		scroller.setPreferredSize(new Dimension(120, 100));
    }

	private class LibraryActionRenderer extends DefaultListCellRenderer {
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
			LibraryAction a = (LibraryAction) value;
			JLabel c = (JLabel) super.getListCellRendererComponent(list, a.getName(), index, isSelected, cellHasFocus);
			c.setToolTipText(a.getDescription());
			if (a.getScore() > 0.75)
				c.setFont(Common.MEDIUM_BOLD);
			else if (a.getScore() < 0.5)
				c.setFont(Common.MEDIUM_ITALIC);
			else
				c.setFont(Common.MEDIUM);
			return c;
		}
	}
}
