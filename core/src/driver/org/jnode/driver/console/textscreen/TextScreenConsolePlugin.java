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
 
package org.jnode.driver.console.textscreen;

import java.awt.event.KeyEvent;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.naming.NamingException;

import org.jnode.driver.console.ConsoleException;
import org.jnode.driver.console.ConsoleManager;
import org.jnode.driver.console.TextConsole;
import org.jnode.naming.BootSplashControl;
import org.jnode.naming.InitialNaming;
import org.jnode.plugin.Plugin;
import org.jnode.plugin.PluginDescriptor;
import org.jnode.plugin.PluginException;
import org.jnode.util.WriterOutputStream;
import org.jnode.vm.VmSystem;

/**
 * @author Ewout Prangsma (epr@users.sourceforge.net)
 */
public class TextScreenConsolePlugin extends Plugin {

    private TextScreenConsoleManager mgr;

    /**
     * @param descriptor
     */
    public TextScreenConsolePlugin(PluginDescriptor descriptor) {
        super(descriptor);
    }

    /**
     * @see org.jnode.plugin.Plugin#startPlugin()
     */
    protected void startPlugin() throws PluginException {
        try {
            mgr = new TextScreenConsoleManager();
            InitialNaming.bind(ConsoleManager.NAME, mgr);

            // Create the first console
            final TextConsole first = (TextConsole) mgr.createConsole(
                null,
                (ConsoleManager.CreateOptions.TEXT |
                    ConsoleManager.CreateOptions.SCROLLABLE));
            first.setAcceleratorKeyCode(KeyEvent.VK_F1);
            mgr.focus(first);

            // Check if splash mode is active.  When it is, suppress ALL
            // console output so the user never sees boot text -- only the
            // graphical splash.  Output is restored by FbTextScreenManager
            // or CommandShell when AWT takes over.
            boolean splashActive = false;
            try {
                InitialNaming.lookup(BootSplashControl.class);
                splashActive = true;
            } catch (javax.naming.NamingException noBind) {
                // no splash
            }

            if (splashActive) {
                // Silent streams -- nothing reaches the VGA text screen
                PrintStream silent = new PrintStream(new OutputStream() {
                    public void write(int b) { }
                    public void write(byte[] b, int off, int len) { }
                });
                System.setOut(silent);
                System.setErr(silent);
            } else {
                System.setOut(new PrintStream(new WriterOutputStream(first.getOut(), false), true));
                System.setErr(new PrintStream(new WriterOutputStream(first.getErr(), false), true));
                System.out.println(VmSystem.getBootLog());
            }
        } catch (ConsoleException ex) {
            throw new PluginException(ex);
        } catch (NamingException ex) {
            throw new PluginException(ex);
        }
    }

    /**
     * @see org.jnode.plugin.Plugin#stopPlugin()
     */
    protected void stopPlugin() throws PluginException {
        if (mgr != null) {
            mgr.closeAll();
            InitialNaming.unbind(ConsoleManager.NAME);
            mgr = null;
        }
    }
}
