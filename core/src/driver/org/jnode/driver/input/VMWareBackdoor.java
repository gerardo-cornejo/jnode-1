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

import java.nio.charset.Charset;
import org.jnode.vm.scheduler.VmProcessor;
import org.jnode.vm.x86.UnsafeX86;
import org.jnode.vm.x86.X86CpuID;

/**
 * Small helper around VMware's low-bandwidth backdoor.
 */
public final class VMWareBackdoor {

    static final int VMWARE_MAGIC = 0x564D5868;
    static final int VMWARE_PORT = 0x5658;
    private static final int VMWARE_PORT_HB = 0x5659;

    static final int CMD_GET_VERSION = 10;
    private static final int CMD_GET_SELECTION_LENGTH = 6;
    private static final int CMD_GET_NEXT_PIECE = 7;
    private static final int CMD_SET_SELECTION_LENGTH = 8;
    private static final int CMD_SET_NEXT_PIECE = 9;
    private static final int CMD_MESSAGE = 30;
    static final int CMD_ABSPOINTER_DATA = 39;
    static final int CMD_ABSPOINTER_STATUS = 40;
    static final int CMD_ABSPOINTER_COMMAND = 41;

    static final int ABSPOINTER_ENABLE = 0x45414552;
    static final int ABSPOINTER_RELATIVE = 0x000000F5;
    static final int ABSPOINTER_ABSOLUTE = 0x53424152;
    static final int ABSPOINTER_ERROR = 0xFFFF0000;

    public static final int BUTTON_LEFT = 0x20;
    public static final int BUTTON_RIGHT = 0x10;
    public static final int BUTTON_MIDDLE = 0x08;
    public static final int BUTTON_LEFT_ALT = 0x01;
    public static final int BUTTON_RIGHT_ALT = 0x02;
    public static final int BUTTON_MIDDLE_ALT = 0x04;

    private static final int MESSAGE_OPEN = 0x00000000;
    private static final int MESSAGE_SEND = 0x00010000;
    private static final int MESSAGE_RECV = 0x00030000;
    private static final int MESSAGE_ACK = 0x00050000;
    private static final int MESSAGE_CLOSE = 0x00060000;

    public static final int PROTOCOL_RPCI = 0x49435052;
    public static final int PROTOCOL_TCLO = 0x4F4C4354;

    private static final Charset TEXT_CHARSET = Charset.forName("UTF-8");
    private static final int CLIPBOARD_CHUNK_SIZE = 4;
    private static final String VMWARE_HYPERVISOR = "VMwareVMware";

    private VMWareBackdoor() {
    }

    public static boolean isAvailable() {
        try {
            final X86CpuID cpuId = (X86CpuID) VmProcessor.current().getCPUID();
            if (cpuId == null || !cpuId.hasHYPERVISOR()) {
                return false;
            }
            if (!VMWARE_HYPERVISOR.equals(cpuId.getHypervisorVendor())) {
                return false;
            }
            final int[] regs = command(CMD_GET_VERSION, ~VMWARE_MAGIC, 0, 0, 0);
            return regs != null && regs[1] == VMWARE_MAGIC && regs[0] != 0xFFFFFFFF;
        } catch (Throwable ex) {
            return false;
        }
    }

    public static boolean setAbsolutePointer(boolean absolute) {
        if (!isAvailable()) {
            return false;
        }
        if (!commandOnly(CMD_ABSPOINTER_COMMAND, ABSPOINTER_ENABLE)) {
            return false;
        }
        command(CMD_ABSPOINTER_STATUS, 0, 0, 0, 0);
        command(CMD_ABSPOINTER_DATA, 1, 0, 0, 0);
        return commandOnly(CMD_ABSPOINTER_COMMAND,
            absolute ? ABSPOINTER_ABSOLUTE : ABSPOINTER_RELATIVE);
    }

    public static int[] readAbsolutePointer() {
        final int[] status = command(CMD_ABSPOINTER_STATUS, 0, 0, 0, 0);
        if (status == null || status[0] == ABSPOINTER_ERROR) {
            return null;
        }
        if ((status[0] & 0xFFFF) < 4) {
            return null;
        }
        return command(CMD_ABSPOINTER_DATA, 4, 0, 0, 0);
    }

    public static int openMessageChannel(int protocol) {
        final int[] regs = new int[6];
        regs[0] = VMWARE_MAGIC;
        regs[1] = protocol;
        regs[2] = CMD_MESSAGE | MESSAGE_OPEN;
        regs[3] = VMWARE_PORT;
        if (UnsafeX86.vmwareBackdoor(regs) == 0) {
            return -1;
        }
        return ((regs[0] & 0x10000) != 0) ? (regs[3] >>> 16) : -1;
    }

    public static boolean closeMessageChannel(int channel) {
        return command(CMD_MESSAGE | MESSAGE_CLOSE, 0, channel, 0, 0) != null;
    }

    public static boolean sendMessage(int channel, byte[] message, int length) {
        if (message == null || length < 0 || length > message.length) {
            return false;
        }
        final int[] sendRegs = new int[6];
        sendRegs[0] = VMWARE_MAGIC;
        sendRegs[1] = length;
        sendRegs[2] = CMD_MESSAGE | MESSAGE_SEND;
        sendRegs[3] = VMWARE_PORT | (channel << 16);
        if (UnsafeX86.vmwareBackdoor(sendRegs) == 0) {
            return false;
        }
        if (length == 0) {
            return true;
        }
        if (((sendRegs[2] >>> 16) & 0x0081) != 0x0081) {
            return false;
        }

        final int[] hbRegs = new int[6];
        hbRegs[0] = VMWARE_MAGIC;
        hbRegs[1] = 0x00010000;
        hbRegs[2] = length;
        hbRegs[3] = VMWARE_PORT_HB | (channel << 16);
        return UnsafeX86.vmwareBackdoorHighBandwidthOut(hbRegs, message, 0, length) != 0;
    }

    public static int receiveMessage(int channel, byte[] buffer) {
        if (buffer == null) {
            return -1;
        }
        final int[] recvRegs = new int[6];
        recvRegs[0] = VMWARE_MAGIC;
        recvRegs[2] = CMD_MESSAGE | MESSAGE_RECV;
        recvRegs[3] = VMWARE_PORT | (channel << 16);
        if (UnsafeX86.vmwareBackdoor(recvRegs) == 0) {
            return -1;
        }
        final int size = recvRegs[1];
        if (size == 0) {
            return 0;
        }
        if (size < 0 || size > buffer.length) {
            return -1;
        }
        if (((recvRegs[2] >>> 16) & 0x0083) != 0x0083) {
            return -1;
        }

        final int[] hbRegs = new int[6];
        hbRegs[0] = VMWARE_MAGIC;
        hbRegs[1] = 0x00010000;
        hbRegs[2] = size;
        hbRegs[3] = VMWARE_PORT_HB | (channel << 16);
        if (UnsafeX86.vmwareBackdoorHighBandwidthIn(hbRegs, buffer, 0, size) == 0) {
            return -1;
        }

        final int[] ackRegs = new int[6];
        ackRegs[0] = VMWARE_MAGIC;
        ackRegs[1] = 1;
        ackRegs[2] = CMD_MESSAGE | MESSAGE_ACK;
        ackRegs[3] = VMWARE_PORT | (channel << 16);
        if (UnsafeX86.vmwareBackdoor(ackRegs) == 0) {
            return -1;
        }
        return size;
    }

    public static boolean sendRpcMessage(String message) {
        final int channel = openMessageChannel(PROTOCOL_RPCI);
        if (channel < 0) {
            return false;
        }
        try {
            final byte[] request = appendTerminator(message == null ? "" : message);
            if (!sendMessage(channel, request, request.length)) {
                return false;
            }
            return receiveMessage(channel, new byte[64]) >= 0;
        } finally {
            closeMessageChannel(channel);
        }
    }

    public static String getClipboardText() {
        final int[] lengthRegs = command(CMD_GET_SELECTION_LENGTH, 0, 0, 0, 0);
        if (lengthRegs == null) {
            return null;
        }
        final int length = Math.max(0, lengthRegs[0]);
        if (length == 0) {
            return "";
        }
        final byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            final int request = Math.min(CLIPBOARD_CHUNK_SIZE, length - offset);
            final int[] pieceRegs = command(CMD_GET_NEXT_PIECE, request, 0, 0, 0);
            if (pieceRegs == null) {
                return null;
            }
            final int value = pieceRegs[0];
            for (int i = 0; i < request; i++) {
                data[offset + i] = (byte) ((value >>> (i * 8)) & 0xFF);
            }
            offset += request;
        }
        return trimTrailingNul(new String(data, TEXT_CHARSET));
    }

    public static boolean setClipboardText(String text) {
        final byte[] bytes = appendTerminator(text == null ? "" : text);
        if (command(CMD_SET_SELECTION_LENGTH, bytes.length, 0, 0, 0) == null) {
            return false;
        }
        int offset = 0;
        while (offset < bytes.length) {
            int value = 0;
            final int chunk = Math.min(CLIPBOARD_CHUNK_SIZE, bytes.length - offset);
            for (int i = 0; i < chunk; i++) {
                value |= (bytes[offset + i] & 0xFF) << (i * 8);
            }
            if (command(CMD_SET_NEXT_PIECE, value, 0, 0, 0) == null) {
                return false;
            }
            offset += chunk;
        }
        return true;
    }

    private static boolean commandOnly(int command, int bx) {
        return command(command, bx, 0, 0, 0) != null;
    }

    private static int[] command(int command, int bx, int dx, int si, int di) {
        final int[] regs = new int[6];
        regs[0] = VMWARE_MAGIC;
        regs[1] = bx;
        regs[2] = command;
        regs[3] = VMWARE_PORT | (dx << 16);
        regs[4] = si;
        regs[5] = di;
        return (UnsafeX86.vmwareBackdoor(regs) != 0) ? regs : null;
    }

    private static byte[] appendTerminator(String value) {
        final byte[] raw = value.getBytes(TEXT_CHARSET);
        final byte[] terminated = new byte[raw.length + 1];
        System.arraycopy(raw, 0, terminated, 0, raw.length);
        return terminated;
    }

    private static String trimTrailingNul(String value) {
        final int index = value.indexOf('\0');
        return (index >= 0) ? value.substring(0, index) : value;
    }
}
