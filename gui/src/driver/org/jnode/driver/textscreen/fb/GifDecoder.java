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

package org.jnode.driver.textscreen.fb;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal GIF87a/GIF89a decoder.  Reads all image frames from an
 * animated GIF and returns them as BufferedImage[].
 */
final class GifDecoder {

    // LZW constants
    private static final int MAX_STACK_SIZE = 4096;

    private int width;
    private int height;
    private int[] gct;          // global colour table
    private int bgIndex;        // background colour index
    private int gctSize;
    private boolean gctFlag;

    // per-frame disposal / transparency
    private int dispose;
    private int transIndex;
    private boolean transparency;
    private int delay;

    // current data stream
    private byte[] rawData;
    private int pos;

    /**
     * Read all frames from a GIF input stream.
     */
    BufferedImage[] readFrames(InputStream is) throws IOException {
        if (is == null) return new BufferedImage[0];

        // Slurp entire stream into byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        is.close();
        rawData = baos.toByteArray();
        pos = 0;

        // Header: GIF87a or GIF89a
        String sig = new String(rawData, 0, 6, "US-ASCII");
        if (!sig.startsWith("GIF")) return new BufferedImage[0];
        pos = 6;

        // Logical Screen Descriptor
        width  = readShort();
        height = readShort();
        int packed = read();
        gctFlag = (packed & 0x80) != 0;
        gctSize = 2 << (packed & 7);
        bgIndex = read();
        read(); // pixel aspect ratio

        if (gctFlag) {
            gct = readColourTable(gctSize);
        }

        List frames = new ArrayList();
        int[] prev = null;      // previous frame pixels for disposal
        int[] canvas = new int[width * height]; // compositing canvas

        // Initialise canvas to transparent (alpha=0) so GIF-transparent
        // areas remain truly transparent in the emitted BufferedImages.
        for (int i = 0; i < canvas.length; i++) canvas[i] = 0;

        // Parse blocks
        boolean done = false;
        while (!done && pos < rawData.length) {
            int code = read();
            switch (code) {
                case 0x2C: // Image Descriptor
                    prev = readImage(canvas, prev, frames);
                    break;
                case 0x21: // Extension
                    readExtension();
                    break;
                case 0x3B: // Trailer
                    done = true;
                    break;
                case 0x00: // filler
                    break;
                default:
                    done = true;
                    break;
            }
        }

        return (BufferedImage[]) frames.toArray(new BufferedImage[frames.size()]);
    }

    private int[] readImage(int[] canvas, int[] prev, List frames) {
        int ix = readShort();
        int iy = readShort();
        int iw = readShort();
        int ih = readShort();
        int packed = read();
        boolean lctFlag =   (packed & 0x80) != 0;
        boolean interlace = (packed & 0x40) != 0;
        int lctSize = 2 << (packed & 7);

        int[] act; // active colour table
        if (lctFlag) {
            act = readColourTable(lctSize);
        } else {
            act = gct;
        }
        if (act == null) act = new int[256];

        // Save canvas state before drawing (for disposal)
        int[] save = new int[canvas.length];
        System.arraycopy(canvas, 0, save, 0, canvas.length);

        // Decode LZW pixel data
        int minCodeSize = read();
        int[] pixels = decodeLZW(minCodeSize, iw * ih);

        // Render to canvas
        int pi = 0;
        int pass = 0;
        int inc  = interlace ? 8 : 1;
        int line = interlace ? 0 : 0;

        for (int row = 0; row < ih; row++) {
            int dy;
            if (interlace) {
                dy = line;
                line += inc;
                if (line >= ih) {
                    pass++;
                    switch (pass) {
                        case 1: line = 4; inc = 8; break;
                        case 2: line = 2; inc = 4; break;
                        case 3: line = 1; inc = 2; break;
                    }
                }
            } else {
                dy = row;
            }

            for (int col = 0; col < iw; col++) {
                int idx = (pi < pixels.length) ? pixels[pi] : 0;
                pi++;
                if (transparency && idx == transIndex) continue;
                int cx = ix + col;
                int cy = iy + dy;
                if (cx >= 0 && cx < width && cy >= 0 && cy < height) {
                    canvas[cy * width + cx] = act[idx & 0xFF] | 0xFF000000;
                }
            }
        }

        // Emit frame
        BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        frame.setRGB(0, 0, width, height, canvas, 0, width);
        frames.add(frame);

        // Handle disposal
        switch (dispose) {
            case 2: // restore to background
                for (int py = iy; py < iy + ih && py < height; py++) {
                    for (int px = ix; px < ix + iw && px < width; px++) {
                        canvas[py * width + px] = 0;
                    }
                }
                break;
            case 3: // restore to previous
                if (prev != null) {
                    System.arraycopy(prev, 0, canvas, 0, canvas.length);
                }
                break;
            default: // 0, 1 = leave in place
                break;
        }

        // Reset per-frame flags
        transparency = false;
        dispose = 0;

        return save;
    }

    private void readExtension() {
        int label = read();
        switch (label) {
            case 0xF9: // Graphic Control Extension
                read(); // block size (always 4)
                int packed = read();
                dispose = (packed & 0x1C) >> 2;
                transparency = (packed & 1) != 0;
                delay = readShort();
                transIndex = read();
                read(); // block terminator
                break;
            default:
                skipBlocks();
                break;
        }
    }

    private void skipBlocks() {
        int blockSize;
        while ((blockSize = read()) > 0) {
            pos += blockSize;
            if (pos >= rawData.length) break;
        }
    }

    private int[] readColourTable(int ncolors) {
        int[] tab = new int[ncolors];
        for (int i = 0; i < ncolors; i++) {
            int r = read();
            int g = read();
            int b = read();
            tab[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return tab;
    }

    // ---- LZW decoder ----

    private int[] decodeLZW(int minCodeSize, int pixelCount) {
        int clearCode = 1 << minCodeSize;
        int eoiCode   = clearCode + 1;
        int codeSize  = minCodeSize + 1;
        int codeMask  = (1 << codeSize) - 1;
        int available = clearCode + 2;
        int oldCode   = -1;

        // LZW tables
        short[] prefix = new short[MAX_STACK_SIZE];
        byte[]  suffix = new byte[MAX_STACK_SIZE];
        byte[]  stack  = new byte[MAX_STACK_SIZE];
        int stackTop = 0;

        // Initialise table
        for (int i = 0; i < clearCode; i++) {
            prefix[i] = 0;
            suffix[i] = (byte) i;
        }

        // Read sub-blocks into a contiguous buffer
        byte[] block = readSubBlocks();
        int bits = 0;
        int datum = 0;
        int blockPos = 0;

        int[] pixels = new int[pixelCount];
        int pi = 0;

        int first = 0;

        while (pi < pixelCount) {
            // Need more bits?
            while (bits < codeSize) {
                if (blockPos >= block.length) break;
                datum |= (block[blockPos++] & 0xFF) << bits;
                bits += 8;
            }
            if (bits < codeSize) break;

            int code = datum & codeMask;
            datum >>= codeSize;
            bits -= codeSize;

            if (code == clearCode) {
                codeSize = minCodeSize + 1;
                codeMask = (1 << codeSize) - 1;
                available = clearCode + 2;
                oldCode = -1;
                continue;
            }
            if (code == eoiCode) break;

            if (oldCode == -1) {
                pixels[pi++] = code & 0xFF;
                oldCode = code;
                first = code & 0xFF;
                continue;
            }

            int inCode = code;
            stackTop = 0;
            if (code >= available) {
                stack[stackTop++] = (byte) first;
                code = oldCode;
            }
            while (code >= clearCode && stackTop < MAX_STACK_SIZE - 1) {
                stack[stackTop++] = suffix[code];
                code = prefix[code];
            }
            first = suffix[code] & 0xFF;
            stack[stackTop++] = (byte) first;

            // Add to table
            if (available < MAX_STACK_SIZE) {
                prefix[available] = (short) oldCode;
                suffix[available] = (byte) first;
                available++;
                if ((available & codeMask) == 0 && available < MAX_STACK_SIZE) {
                    codeSize++;
                    codeMask = (1 << codeSize) - 1;
                }
            }
            oldCode = inCode;

            // Push pixels from stack
            while (stackTop > 0 && pi < pixelCount) {
                pixels[pi++] = stack[--stackTop] & 0xFF;
            }
        }

        return pixels;
    }

    /**
     * Read consecutive GIF sub-blocks into one contiguous byte array.
     */
    private byte[] readSubBlocks() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        int blockSize;
        while ((blockSize = read()) > 0) {
            if (pos + blockSize > rawData.length) {
                blockSize = rawData.length - pos;
            }
            out.write(rawData, pos, blockSize);
            pos += blockSize;
        }
        return out.toByteArray();
    }

    // ---- Low-level reads ----

    private int read() {
        if (pos >= rawData.length) return 0;
        return rawData[pos++] & 0xFF;
    }

    private int readShort() {
        int lo = read();
        int hi = read();
        return lo | (hi << 8);
    }
}
