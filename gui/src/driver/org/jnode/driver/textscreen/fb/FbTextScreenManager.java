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
 
package org.jnode.driver.textscreen.fb;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.FontMetrics;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;



import org.jnode.driver.DeviceException;
import org.jnode.driver.textscreen.TextScreen;
import org.jnode.driver.textscreen.TextScreenManager;
import org.jnode.driver.video.AlreadyOpenException;
import org.jnode.driver.video.FrameBufferAPI;
import org.jnode.driver.video.FrameBufferAPIOwner;
import org.jnode.driver.video.FrameBufferConfiguration;
import org.jnode.driver.video.Surface;
import org.jnode.driver.video.UnknownConfigurationException;
import org.jnode.awt.font.FontManager;
import org.jnode.naming.BootSplashControl;
import org.jnode.naming.InitialNaming;
import javax.naming.NamingException;

final class FbTextScreenManager implements TextScreenManager, FrameBufferAPIOwner, BootSplashControl {
    /**
     * The font to use for rendering characters in the console : 
     * it must be a mono spaced font (=a font with fixed width)
     */
    private static final Font FONT_SMALL = new Font("-Misc-Fixed-Medium-R-SemiCondensed--12-110-75-75-C-60-437-",
        Font.PLAIN, 12);
    private static final Font FONT_LARGE = new Font("-dosemu-VGA-Medium-R-Normal--19-190-75-75-C-100-IBM-",
        Font.PLAIN, 18);

    private final FbTextScreen systemScreen;
    private final Surface surface; 
    private FrameBufferConfiguration conf;
    
    /** Splash overlay thread - keeps painting splash over console until AWT takes over */
    private volatile boolean splashActive = false;
    private volatile boolean ownershipWasLost = false;
    private volatile long splashStartTime;
    private Thread splashThread;

    public FontManager getFontManager() {
        try {
            return InitialNaming.lookup(FontManager.NAME);
        } catch (NamingException ex) {
            return null;
        }
    }

    /**
     * 
     * @param g
     * @param width in pixels
     * @param height in pixels
     * @throws DeviceException 
     * @throws AlreadyOpenException 
     * @throws UnknownConfigurationException 
     */
    FbTextScreenManager(FrameBufferAPI api, FrameBufferConfiguration conf) 
        throws UnknownConfigurationException, AlreadyOpenException, DeviceException {

        final Font font = conf.getScreenWidth() > 800 ? FONT_LARGE : FONT_SMALL;
        final FontMetrics fm = getFontManager().getFontMetrics(font);
        final int w = fm.getMaxAdvance();
        final int h = fm.getHeight();

        final int nbColumns = 80;
        final int nbRows = 25;

        // compute x and y offsets to center the console in the screen
        final int consoleWidth = w * nbColumns;
        final int consoleHeight = h * nbRows;
        final int xOffset = (conf.getScreenWidth() - consoleWidth) / 2;
        final int yOffset = (conf.getScreenHeight() - consoleHeight) / 2;
        
        BufferedImage bufferedImage = new BufferedImage(consoleWidth, consoleHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = bufferedImage.getGraphics();
        
        api.requestOwnership(this);
        surface = api.open(conf);
        this.conf = conf;

        systemScreen = new FbTextScreen(surface, bufferedImage, graphics, font, nbColumns, nbRows, xOffset, yOffset);

        // Always show the splash overlay during boot.  The overlay loads
        // spinner.gif and animates it until AWT calls ownershipLost(), or
        // it times out (60 s) and falls back to the text console.
        startSplashOverlay(conf.getScreenWidth(), conf.getScreenHeight());

        // Register ourselves as BootSplashControl so CommandShell can
        // stop the splash directly before launching startawt.
        try {
            InitialNaming.bind(BootSplashControl.class, this);
        } catch (NamingException e) {
            // If BootSplashRunner already bound, overwrite it
            try {
                InitialNaming.unbind(BootSplashControl.class);
                InitialNaming.bind(BootSplashControl.class, this);
            } catch (NamingException e2) {
                System.out.println("[FbTSM] Could not bind BootSplashControl: " + e2);
            }
        }
    }

    /**
     * Starts a daemon thread that loads spinner.gif and keeps painting it
     * over the console output until ownershipLost() is called (AWT takes over).
     */
    private void startSplashOverlay(final int screenW, final int screenH) {
        splashActive = true;
        splashStartTime = System.currentTimeMillis();
        splashThread = new Thread(new Runnable() {
            public void run() {
                try {
                    // Load GIF frames into memory
                    BufferedImage[] frames = loadGifFrames();
                    if (frames == null || frames.length == 0) {
                        System.out.println("[BootSplash] No frames loaded, falling back to console");
                        splashActive = false;
                        systemScreen.open();
                        return;
                    }

                    // Display at native GIF size
                    final int targetSize = 16;
                    BufferedImage[] scaled = new BufferedImage[frames.length];
                    for (int i = 0; i < frames.length; i++) {
                        scaled[i] = scaleImage(frames[i], targetSize, targetSize);
                    }
                    frames = scaled;

                    System.out.println("[BootSplash] Loaded " + frames.length + " GIF frames, starting animation");
                    final AffineTransform identity = new AffineTransform();

                    // Paint black background once
                    surface.fill(new Rectangle(0, 0, screenW, screenH),
                        null, identity, Color.BLACK, Surface.PAINT_MODE);

                    final int dstX = (screenW - targetSize) / 2;
                    final int dstY = (screenH - targetSize) / 2;

                    // Loop until AWT takes ownership, stopSplash is called,
                    // or 30-second safety timeout expires.
                    final long deadline = System.currentTimeMillis() + 30000;
                    int frameIndex = 0;
                    while (splashActive && System.currentTimeMillis() < deadline) {
                        BufferedImage frame = frames[frameIndex];
                        int fw = frame.getWidth();
                        int fh = frame.getHeight();

                        // Clear GIF area with black
                        surface.fill(new Rectangle(dstX, dstY, fw, fh),
                            null, identity, Color.BLACK, Surface.PAINT_MODE);

                        // Paint frame pixel by pixel - skip transparent pixels
                        int[] rgb = frame.getRGB(0, 0, fw, fh, null, 0, fw);
                        for (int py = 0; py < fh; py++) {
                            for (int px = 0; px < fw; px++) {
                                int argb = rgb[py * fw + px];
                                int alpha = (argb >>> 24) & 0xFF;
                                if (alpha > 0) {
                                    surface.setRGBPixel(dstX + px, dstY + py, argb);
                                }
                            }
                        }

                        frameIndex = (frameIndex + 1) % frames.length;
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    System.out.println("[BootSplash] Animation error: " + e.getMessage());
                }
                splashActive = false;
                // If AWT did not take over, open the console as fallback
                if (!ownershipWasLost) {
                    System.out.println("[BootSplash] Splash ended without AWT - opening console");
                    systemScreen.open();
                }
            }
        }, "BootSplashAnimator");
        splashThread.setDaemon(true);
        splashThread.start();
    }

    /**
     * Nearest-neighbor scale a BufferedImage to target dimensions.
     */
    private static BufferedImage scaleImage(BufferedImage src, int tw, int th) {
        BufferedImage dst = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        int sw = src.getWidth();
        int sh = src.getHeight();
        for (int y = 0; y < th; y++) {
            int sy = y * sh / th;
            for (int x = 0; x < tw; x++) {
                int sx = x * sw / tw;
                dst.setRGB(x, y, src.getRGB(sx, sy));
            }
        }
        return dst;
    }

    /**
     * Loads all frames from spinner.gif using custom GIF decoder (no ImageIO dependency).
     */
    private BufferedImage[] loadGifFrames() {
        try {
            InputStream is = getClass().getResourceAsStream("spinner.gif");
            if (is == null) {
                System.out.println("[BootSplash] spinner.gif not found via getResourceAsStream");
                is = getClass().getClassLoader().getResourceAsStream("spinner.gif");
            }
            if (is == null) {
                System.out.println("[BootSplash] spinner.gif not found at all");
                return null;
            }

            GifDecoder decoder = new GifDecoder();
            BufferedImage[] frames = decoder.readFrames(is);
            return frames;
        } catch (Exception e) {
            System.out.println("[BootSplash] Error loading GIF: " + e.getMessage());
            return null;
        }
    }

    private final void clearScreen() {
        // initial painting of all the screen area
        final Rectangle r = new Rectangle(0, 0, conf.getScreenWidth(), conf.getScreenHeight());
        surface.fill(r, null, new AffineTransform(), Color.BLACK, Surface.PAINT_MODE);
    }
    
    /**
     * @see org.jnode.driver.textscreen.TextScreenManager#getSystemScreen()
     */
    public TextScreen getSystemScreen() {
        return systemScreen;
    }

    /**
     * Called by CommandShell via BootSplashControl to stop the splash
     * before launching AWT.
     */
    public void stopSplash() {
        // Ensure the splash is visible for at least 2 seconds so the
        // user actually sees it before AWT takes over.
        long elapsed = System.currentTimeMillis() - splashStartTime;
        if (elapsed < 2000) {
            try {
                Thread.sleep(2000 - elapsed);
            } catch (InterruptedException e) {
                // proceed anyway
            }
        }
        // Mark as if AWT took over so the fallback code does not open console
        ownershipWasLost = true;
        splashActive = false;
        if (splashThread != null) {
            splashThread.interrupt();
            try {
                splashThread.join(2000);
            } catch (InterruptedException e) {
                // ignore
            }
            splashThread = null;
        }
    }

    @Override
    public void ownershipLost() {
        // Stop splash animation - AWT is taking over
        ownershipWasLost = true;
        stopSplash();
        
        // systemScreen might be null at construction time
        if (systemScreen != null) {
            systemScreen.close();
        }
    }
    
    @Override
    public void ownershipGained() {
        if (systemScreen != null) {
            clearScreen();
            systemScreen.open();
        }
    }
}
