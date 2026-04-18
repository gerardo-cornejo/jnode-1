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

import java.awt.EventQueue;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.Charset;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.jnode.driver.input.VMWareBackdoor;

/**
 * Best-effort VMware guest integration for auto-resize and clipboard sync.
 */
final class VMWareIntegrationService implements Runnable {

    private static final Logger log = LogManager.getLogger(VMWareIntegrationService.class);
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final JNodeToolkit toolkit;
    private volatile boolean running;
    private Thread worker;
    private int tcloChannel = -1;
    private boolean sendCapabilities = true;
    private String lastGuestClipboard;
    private String lastHostClipboard;

    VMWareIntegrationService(JNodeToolkit toolkit) {
        this.toolkit = toolkit;
    }

    boolean start() {
        if (!VMWareBackdoor.isAvailable()) {
            return false;
        }
        tcloChannel = VMWareBackdoor.openMessageChannel(VMWareBackdoor.PROTOCOL_TCLO);
        if (tcloChannel < 0) {
            return false;
        }
        running = true;
        worker = new Thread(this, "VMWare-Integration");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    void stop() {
        running = false;
        final Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
        if (tcloChannel >= 0) {
            VMWareBackdoor.closeMessageChannel(tcloChannel);
            tcloChannel = -1;
        }
    }

    @Override
    public void run() {
        final byte[] tcloBuffer = new byte[512];
        while (running) {
            try {
                processClipboard();
                processTclo(tcloBuffer);
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ex) {
                log.debug("VMware integration loop error", ex);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processTclo(byte[] buffer) {
        if (tcloChannel < 0) {
            return;
        }

        if (!VMWareBackdoor.sendMessage(tcloChannel, new byte[0], 0)) {
            return;
        }

        final int size = VMWareBackdoor.receiveMessage(tcloChannel, buffer);
        if (size <= 0) {
            if (sendCapabilities) {
                sendCapabilities();
            }
            return;
        }

        final String message = new String(buffer, 0, size, UTF_8).trim();
        if (message.startsWith("reset")) {
            VMWareBackdoor.sendMessage(tcloChannel, "OK ATR toolbox".getBytes(UTF_8),
                "OK ATR toolbox".length());
            sendCapabilities = true;
        } else if (message.startsWith("ping")) {
            replyOk();
        } else if (message.startsWith("Capabilities_Register")) {
            replyOk();
            sendCapabilities = true;
        } else if (message.startsWith("Resolution_Set")) {
            handleResolutionSet(message);
            replyOk();
        } else {
            replyOk();
        }
    }

    private void handleResolutionSet(String message) {
        final String[] parts = message.split("\\s+");
        if (parts.length < 3) {
            return;
        }
        try {
            final int width = Integer.parseInt(parts[1]);
            final int height = Integer.parseInt(parts[2]);
            if (width > 0 && height > 0) {
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        toolkit.changeScreenSize(width, height);
                    }
                });
            }
        } catch (NumberFormatException ex) {
            log.debug("Ignoring malformed Resolution_Set: " + message, ex);
        }
    }

    private void sendCapabilities() {
        sendCapabilities = false;
        VMWareBackdoor.sendRpcMessage("tools.capability.resolution_set 1");
        VMWareBackdoor.sendRpcMessage("tools.capability.resolution_server toolbox 1");
        VMWareBackdoor.sendRpcMessage("tools.capability.display_topology_set 1");
        VMWareBackdoor.sendRpcMessage("tools.capability.color_depth_set 1");
        VMWareBackdoor.sendRpcMessage("tools.capability.resolution_min 0 0");
        VMWareBackdoor.sendRpcMessage("tools.capability.unity 1");
    }

    private void replyOk() {
        final byte[] ok = "OK ".getBytes(UTF_8);
        VMWareBackdoor.sendMessage(tcloChannel, ok, ok.length);
    }

    private void processClipboard() {
        final Clipboard clipboard = toolkit.getSystemClipboard();
        final String guestText = readClipboardText(clipboard);
        if (guestText != null && !guestText.equals(lastGuestClipboard) && !guestText.equals(lastHostClipboard)) {
            if (VMWareBackdoor.setClipboardText(guestText)) {
                lastGuestClipboard = guestText;
                lastHostClipboard = guestText;
            }
        }

        final String hostText = VMWareBackdoor.getClipboardText();
        if (hostText != null && !hostText.equals(lastHostClipboard)) {
            clipboard.setContents(new StringSelection(hostText), null);
            lastHostClipboard = hostText;
            lastGuestClipboard = hostText;
        }
    }

    void pushClipboardText(String text) {
        if (text != null && VMWareBackdoor.setClipboardText(text)) {
            lastGuestClipboard = text;
            lastHostClipboard = text;
        }
    }

    private String readClipboardText(Clipboard clipboard) {
        try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                final Object data = clipboard.getData(DataFlavor.stringFlavor);
                return (data instanceof String) ? (String) data : null;
            }
        } catch (Exception ex) {
            log.debug("Unable to read guest clipboard", ex);
        }
        return null;
    }
}
