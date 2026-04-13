package org.jnode.driver.console.textscreen;

import java.io.PrintStream;

/**
 * ASCII boot splash for default (text) mode
 * Displays a simple animated splash without interfering with framebuffer
 * 
 * @author JNode Community
 */
public class BootSplashDefault {

    public static void printSplash(PrintStream out) {
        try {
            // Clear screen with escape codes
            out.print("\u001b[2J");  // Clear screen
            out.print("\u001b[H");   // Move cursor to home (0,0)
            
            // Print splash border
            out.println("+==========================================================================+");
            out.println("|                                                                          |");
            out.println("|                    +------------------------------+                      |");
            out.println("|                    |                              |                      |");
            out.println("|                    |       JNode OS Boot          |                      |");
            out.println("|                    |    Java Operating System     |                      |");
            out.println("|                    |                              |                      |");
            out.println("|                    +------------------------------+                      |");
            out.println("|                                                                          |");
            out.println("|                 Initializing system components...                       |");
            out.println("|                                                                          |");
            
            // Simple spinner animation
            String[] spinner = { "|", "/", "-", "\\" };
            for (int i = 0; i < 8; i++) {
                out.print("|                              ");
                out.print(spinner[i % 4]);
                out.print("  ");
                for (int j = 0; j < i; j++) {
                    out.print("#");
                }
                out.println("                                 |");
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // ignore
                }
                // Move cursor up one line
                out.print("\u001b[A");
            }
            
            out.println("|                              [SUCCESS]                                  |");
            out.println("|                                                                          |");
            out.println("+==========================================================================+");
            out.println();
            
        } catch (Exception e) {
            // If something fails, just continue - don't break boot
            System.err.println("Error displaying splash: " + e.getMessage());
        }
    }
}
