/*
 * $Id$
 *
 * Copyright (C) 2003-2015 JNode.org
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; If not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.jnode.awt;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;

final class VMWareTransferable implements Transferable {

    private static final DataFlavor[] FLAVORS = {
        DataFlavor.javaFileListFlavor,
        DataFlavor.stringFlavor
    };

    private final List<?> files;
    private final String uriList;

    VMWareTransferable(List<?> files, String uriList) {
        this.files = files;
        this.uriList = uriList;
    }

    public DataFlavor[] getTransferDataFlavors() {
        return FLAVORS.clone();
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.javaFileListFlavor.equals(flavor) || DataFlavor.stringFlavor.equals(flavor);
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (DataFlavor.javaFileListFlavor.equals(flavor)) {
            return files;
        }
        if (DataFlavor.stringFlavor.equals(flavor)) {
            return uriList;
        }
        throw new UnsupportedFlavorException(flavor);
    }
}
