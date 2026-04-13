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

package org.jnode.naming;

/**
 * Control interface for the boot splash screen.
 * Bound via InitialNaming so any plugin can stop the splash.
 */
public interface BootSplashControl {

    /**
     * Stops the splash animation and waits for the splash thread to finish.
     */
    void stopSplash();
}
