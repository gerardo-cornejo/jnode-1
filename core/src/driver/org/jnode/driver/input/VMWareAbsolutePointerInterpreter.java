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

package org.jnode.driver.input;

import java.io.PrintWriter;

/**
 * VMware absolute pointer support over the classic backdoor.
 */
final class VMWareAbsolutePointerInterpreter implements PointerInterpreter {

    private boolean enabled;
    private int lastButtons = Integer.MIN_VALUE;
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;

    public boolean probe(AbstractPointerDriver d) {
        enabled = VMWareBackdoor.setAbsolutePointer(true);
        reset();
        return enabled;
    }

    public String getName() {
        return "VMWare Absolute Pointer";
    }

    public PointerEvent handleScancode(int scancode) {
        if (!enabled) {
            return null;
        }
        final int[] regs = VMWareBackdoor.readAbsolutePointer();
        if (regs == null) {
            return null;
        }

        final int buttons = convertButtons(regs[0] & 0xFFFF);
        final int x = regs[1] & 0xFFFF;
        final int y = regs[2] & 0xFFFF;
        final int z = (byte) (regs[3] & 0xFF);
        if (buttons == lastButtons && x == lastX && y == lastY && z == lastZ) {
            return null;
        }

        lastButtons = buttons;
        lastX = x;
        lastY = y;
        lastZ = z;
        return new PointerEvent(buttons, x, y, z, PointerEvent.ABSOLUTE);
    }

    public void reset() {
        lastButtons = Integer.MIN_VALUE;
        lastX = Integer.MIN_VALUE;
        lastY = Integer.MIN_VALUE;
        lastZ = Integer.MIN_VALUE;
    }

    public void showInfo(PrintWriter out) {
        out.println("Name          : " + getName());
        out.println("Enabled       : " + enabled);
        out.println("Coordinates   : normalized absolute 0..65535");
    }

    private int convertButtons(int vmwareButtons) {
        int buttons = 0;
        if ((vmwareButtons & (VMWareBackdoor.BUTTON_LEFT | VMWareBackdoor.BUTTON_LEFT_ALT)) != 0) {
            buttons |= PointerEvent.BUTTON_LEFT;
        }
        if ((vmwareButtons & (VMWareBackdoor.BUTTON_RIGHT | VMWareBackdoor.BUTTON_RIGHT_ALT)) != 0) {
            buttons |= PointerEvent.BUTTON_RIGHT;
        }
        if ((vmwareButtons & (VMWareBackdoor.BUTTON_MIDDLE | VMWareBackdoor.BUTTON_MIDDLE_ALT)) != 0) {
            buttons |= PointerEvent.BUTTON_MIDDLE;
        }
        return buttons;
    }
}
