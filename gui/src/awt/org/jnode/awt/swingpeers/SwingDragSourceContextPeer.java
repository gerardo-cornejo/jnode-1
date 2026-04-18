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

package org.jnode.awt.swingpeers;

import java.awt.Cursor;
import java.awt.Image;
import java.awt.Point;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragSourceContext;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.dnd.peer.DragSourceContextPeer;

final class SwingDragSourceContextPeer implements DragSourceContextPeer {

    private final SwingToolkit toolkit;
    private final DragGestureEvent trigger;
    private Cursor cursor;

    SwingDragSourceContextPeer(SwingToolkit toolkit, DragGestureEvent trigger) {
        this.toolkit = toolkit;
        this.trigger = trigger;
    }

    public void startDrag(final DragSourceContext dsc, Cursor c, Image dragImage, Point imageOffset)
        throws InvalidDnDOperationException {
        this.cursor = c;
        final boolean exported = toolkit.exportTransferable(dsc.getTransferable());
        SwingToolkit.invokeNowOrLater(new Runnable() {
            public void run() {
                dsc.dragDropEnd(new DragSourceDropEvent(dsc, trigger.getDragAction(), exported));
            }
        });
    }

    public Cursor getCursor() {
        return cursor;
    }

    public void setCursor(Cursor c) throws InvalidDnDOperationException {
        this.cursor = c;
        toolkit.updateCursor(c == null ? Cursor.getDefaultCursor() : c);
    }

    public void transferablesFlavorsChanged() {
        // Nothing else to do. We always export the current transferable on start.
    }
}
