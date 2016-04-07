
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

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

@SuppressWarnings("serial")
 public class SourceTransfer extends TransferHandler implements DropTargetListener {


    private final int BORDER = 5;
    private final Border BORDER_ACTIVE = BorderFactory.createLineBorder(new Color(0.0f, 0.5f, 1.0f, 0.5f), BORDER);
    private final Droppable target;
    private Border BORDER_INACTIVE;
    private boolean borderSet;

    public SourceTransfer(Droppable target) {
        this.target = target;
    }

    public void dragEnter(DropTargetDragEvent dtde) {
        if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor) || dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        else
            dtde.rejectDrag();
        setState(dtde, true);
    }

    public void dragOver(DropTargetDragEvent dtde) {
        setState(dtde, true);
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    public void dragExit(DropTargetEvent dte) {
        setState(dte, false);
    }

    public void drop(DropTargetDropEvent dtde) {

        boolean success = false;
        if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor) || dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            try {
                success = doTransfer(dtde.getTransferable(), dtde.getLocation());
            } catch (Exception e) {
                success = false;
                e.printStackTrace();
            }
            if (success)
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
            else
                dtde.rejectDrop();
        }
        setState(dtde, false);
        dtde.dropComplete(success);
    }

    private boolean doTransfer(Transferable transferable, Point p)
            throws UnsupportedFlavorException, IOException {
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            File f = (File) ((List) transferable.getTransferData(DataFlavor.javaFileListFlavor)).get(0);
            return target.handleFile(f);
        } else if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            String value = (String) transferable.getTransferData(DataFlavor.stringFlavor);
            return target.handleText(value);
        } else
            return false;
    }

    private void setState(DropTargetEvent dtde, boolean active) {
        JComponent c = (JComponent) dtde.getDropTargetContext().getComponent();
        if (!borderSet) BORDER_INACTIVE = c.getBorder();
        borderSet = true;
        c.setBorder(active ? BORDER_ACTIVE : BORDER_INACTIVE);
    }

    public boolean importData(TransferSupport support) {
        Transferable t = support.getTransferable();
        Point point = support.getDropLocation().getDropPoint();
        try {
            return doTransfer(t, point);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    public interface Droppable {

       boolean handleFile(File file);

       boolean handleText(String text);
   }
}
