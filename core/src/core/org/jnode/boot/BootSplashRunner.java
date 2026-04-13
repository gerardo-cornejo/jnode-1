/*
 * $Id$
 *
 * Copyright (C) 2003-2015 JNode.org
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 */

package org.jnode.boot;

import javax.naming.NamingException;

import org.jnode.naming.BootSplashControl;
import org.jnode.naming.InitialNaming;
import org.jnode.vm.Unsafe;
import org.jnode.vm.VmSystem;
import org.jnode.vm.x86.UnsafeX86;
import org.vmmagic.unboxed.Address;

/**
 * Core-level early boot splash.  Fills the VBE framebuffer black before
 * any plugins load (hides VGA text), and registers a {@link BootSplashControl}
 * binding so that FbTextScreenManager knows not to show the text console.
 * <p>
 * The actual spinner animation is handled by the FbTextScreenManager overlay
 * which uses the Surface API (proven to work through BitmapGraphics/VESACore).
 */
public final class BootSplashRunner implements BootSplashControl {

    // VBE ModeInfoBlock field offsets
    private static final int OFF_BYTES_PER_SCANLINE = 16;
    private static final int OFF_X_RESOLUTION = 18;
    private static final int OFF_Y_RESOLUTION = 20;
    private static final int OFF_BITS_PER_PIXEL = 25;
    private static final int OFF_RAM_BASE = 40;

    private volatile boolean stopped;

    /**
     * Attempt to start the boot splash.  Returns silently if VBE is
     * not available or the kernel command line does not contain "fb".
     */
    public static void start() {
        try {
            doStart();
        } catch (Throwable t) {
            Unsafe.debug("[BootSplash] EXCEPTION: ");
            Unsafe.debug(t.getClass().getName());
            Unsafe.debug("\n");
        }
    }

    private static void doStart() {
        String cmdLine = VmSystem.getCmdLine();
        if (cmdLine == null || cmdLine.indexOf(" fb") < 0) {
            Unsafe.debug("[BootSplash] no fb -- skip\n");
            return;
        }

        Address modeInfo = UnsafeX86.getVbeModeInfos();
        if (modeInfo.isZero()) {
            Unsafe.debug("[BootSplash] no VBE -- skip\n");
            return;
        }

        int xRes = modeInfo.add(OFF_X_RESOLUTION).loadShort() & 0xFFFF;
        int yRes = modeInfo.add(OFF_Y_RESOLUTION).loadShort() & 0xFFFF;
        int bpp  = modeInfo.add(OFF_BITS_PER_PIXEL).loadByte() & 0xFF;
        int ramBase = modeInfo.add(OFF_RAM_BASE).loadInt();
        int bpsl = modeInfo.add(OFF_BYTES_PER_SCANLINE).loadShort() & 0xFFFF;

        if (xRes == 0 || yRes == 0 || ramBase == 0 || bpp != 32) {
            Unsafe.debug("[BootSplash] bad VBE params -- skip\n");
            // Even without VBE framebuffer, clear VGA text memory so the
            // user does not see boot text before the FB splash takes over.
            Address vga = Address.fromIntZeroExtend(0xB8000);
            for (int i = 0; i < 80 * 25; i++) {
                vga.add(i * 2).store((short) 0x0700);  // blank with grey-on-black attr
            }
            return;
        }

        int stride = (bpsl > 0) ? bpsl : xRes * 4;

        // Fill entire screen BLACK to hide VGA text output during plugin loading.
        Unsafe.debug("[BootSplash] filling black ");
        Unsafe.debug(Integer.toString(xRes));
        Unsafe.debug("x");
        Unsafe.debug(Integer.toString(yRes));
        Unsafe.debug("\n");

        Address fb = Address.fromIntZeroExtend(ramBase);
        for (int row = 0; row < yRes; row++) {
            int rowOff = row * stride;
            for (int col = 0; col < xRes; col++) {
                fb.add(rowOff + col * 4).store(0);
            }
        }

        // Register as BootSplashControl -- signals other components that
        // splash mode is active (suppresses text console).
        BootSplashRunner runner = new BootSplashRunner();
        try {
            InitialNaming.bind(BootSplashControl.class, runner);
            Unsafe.debug("[BootSplash] bound OK\n");
        } catch (NamingException e) {
            Unsafe.debug("[BootSplash] bind failed\n");
        }
    }

    public void stopSplash() {
        stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }
}
