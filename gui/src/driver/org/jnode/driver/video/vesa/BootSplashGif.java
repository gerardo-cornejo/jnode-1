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
 */

package org.jnode.driver.video.vesa;

/**
 * Simple boot splash - shows loading indicator during JNode boot
 * Does NOT use threads or ImageIO to avoid framebuffer ownership conflicts
 */
public class BootSplashGif {
    
    private VESACore surface;
    private int screenWidth;
    private int screenHeight;
    
    private static final int BACKGROUND_COLOR = 0x001a1a3e; // Dark blue
    private static final int BORDER_COLOR = 0x00FF6600;      // Orange
    private static final int SPINNER_COLOR = 0x00FFFFFF;     // White
    
    /**
     * Creates simple boot splash (no GIF - just pattern)
     */
    public BootSplashGif(VESACore surface, int width, int height) {
        this.surface = surface;
        this.screenWidth = width;
        this.screenHeight = height;
    }
    
    /**
     * Quick splash display - drawspattern and returns immediately
     * Does NOT block or use threads
     */
    public void render() {
        try {
            System.out.println("[BootSplash] Rendering splash screen");
            
            // Fill background
            surface.fillRect(0, 0, screenWidth, screenHeight, BACKGROUND_COLOR, org.jnode.driver.video.Surface.PAINT_MODE);
            
            // Draw decorative border box
            drawBorderBox();
            
            // Draw simple spinner pattern
            drawSimpleSpinner();
            
            System.out.println("[BootSplash] Splash complete");
            
        } catch (Exception e) {
            System.out.println("[BootSplash] Error rendering splash: " + e.getMessage());
        }
    }
    
    /**
     * Draws decorative border box
     */
    private void drawBorderBox() {
        try {
            int boxWidth = 400;
            int boxHeight = 150;
            int boxX = (screenWidth - boxWidth) / 2;
            int boxY = (screenHeight - boxHeight) / 2;
            
            // Top line
            surface.drawLine(boxX, boxY, boxX + boxWidth, boxY, BORDER_COLOR, org.jnode.driver.video.Surface.PAINT_MODE);
            // Right line  
            surface.drawLine(boxX + boxWidth, boxY, boxX + boxWidth, boxY + boxHeight, BORDER_COLOR, org.jnode.driver.video.Surface.PAINT_MODE);
            // Bottom line
            surface.drawLine(boxX + boxWidth, boxY + boxHeight, boxX, boxY + boxHeight, BORDER_COLOR, org.jnode.driver.video.Surface.PAINT_MODE);
            // Left line
            surface.drawLine(boxX, boxY + boxHeight, boxX, boxY, BORDER_COLOR, org.jnode.driver.video.Surface.PAINT_MODE);
            
        } catch (Exception e) {
            System.out.println("[BootSplash] Error drawing border: " + e.getMessage());
        }
    }
    
    /**
     * Draws a simple spinner/loading pattern
     */
    private void drawSimpleSpinner() {
        try {
            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;
            int radius = 40;
            
            // Draw rotating bars pattern
            for (int angle = 0; angle < 360; angle += 45) {
                double rad = Math.toRadians(angle);
                int x1 = centerX;
                int y1 = centerY;
                int x2 = centerX + (int)(Math.cos(rad) * radius);
                int y2 = centerY + (int)(Math.sin(rad) * radius);
                
                surface.drawLine(x1, y1, x2, y2, SPINNER_COLOR, org.jnode.driver.video.Surface.PAINT_MODE);
            }
            
            // Draw text indicator
            surface.fillRect(centerX - 60, centerY + 60, 120, 10, SPINNER_COLOR, org.jnode.driver.video.Surface.PAINT_MODE);
            
        } catch (Exception e) {
            System.out.println("[BootSplash] Error drawing spinner: " + e.getMessage());
        }
    }
}

