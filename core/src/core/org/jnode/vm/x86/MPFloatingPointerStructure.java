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
 
package org.jnode.vm.x86;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jnode.system.resource.MemoryResource;
import org.jnode.system.resource.MemoryScanner;
import org.jnode.system.resource.ResourceManager;
import org.jnode.system.resource.ResourceNotFreeException;
import org.jnode.system.resource.ResourceOwner;
import org.jnode.util.NumberUtils;
import org.jnode.annotation.MagicPermission;
import org.jnode.bootlog.BootLogInstance;
import org.vmmagic.unboxed.Address;

/**
 * @author Ewout Prangsma (epr@users.sourceforge.net)
 */
@MagicPermission
final class MPFloatingPointerStructure {

    private static final Address DEFAULT_LOCAL_APIC_ADDRESS = Address.fromIntZeroExtend(0xFEE00000);
    private static final Address DEFAULT_IO_APIC_ADDRESS = Address.fromIntZeroExtend(0xFEC00000);

    private MemoryResource mem;

    private static final int MAGIC = 0x5F504D5F; // _MP_

    private MPConfigTable configTable;

    /**
     * Find the MP floating pointer structure.
     *
     * @return The found structure, or null if not found.
     */
    public static MPFloatingPointerStructure find(ResourceManager rm,
                                                  ResourceOwner owner) {
        MPFloatingPointerStructure mp;
        mp = findInEbda(rm, owner);
        if (mp == null) {
            mp = findInLastKilobyteOfBaseMemory(rm, owner);
        }
        if (mp == null) {
            mp = find(rm, owner, 0xF0000, 0x100000);
        }
        if (mp == null) {
            return null;
        }
        return mp;
    }

    /**
     * Release the resources hold by this structure.
     */
    public void release() {
        mem.release();
    }

    /**
     * Gets the length of this structure in bytes.
     *
     * @return the length
     */
    final int getLength() {
        return (16 * (mem.getByte(0x08) & 0xFF));
    }

    /**
     * Gets the specification revision level
     */
    final int getSpecRevision() {
        return mem.getByte(0x09);
    }

    /**
     * Gets the MP system configuration type. When non-zero, a default
     * configuration is present, when zero an MP configuration table must be
     * present.
     *
     * @return
     */
    final int getSystemConfigurationType() {
        return mem.getByte(0x0B);
    }

    /**
     * Is the IMCR register present. This flag can be used to determine whether
     * PIC Mode or Virtual Wire mode is implemented by the system.
     *
     * @return
     */
    final boolean isIMCRPresent() {
        return ((mem.getByte(0x0C) & 0x80) != 0);
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return "MP 1." + getSpecRevision() + ", config-type 0x"
            + NumberUtils.hex(getSystemConfigurationType(), 2) + ", IMCR "
            + (isIMCRPresent() ? "present" : "not present")
            + ", ConfigTableAt 0x" + NumberUtils.hex(mem.getInt(0x04));
    }

    /**
     * Gets the physical address of the MP configuration table.
     *
     * @return the address
     */
    final Address getMPConfigTablePtr() {
        return Address.fromIntZeroExtend(mem.getInt(0x04));
    }

    /**
     * Gets the MPConfig table.
     */
    final MPConfigTable getMPConfigTable() {
        return configTable;
    }

    final boolean hasDefaultConfiguration() {
        return (getSystemConfigurationType() != 0);
    }

    final Address getDefaultLocalApicAddress() {
        return DEFAULT_LOCAL_APIC_ADDRESS;
    }

    final Address getDefaultIoApicAddress() {
        return DEFAULT_IO_APIC_ADDRESS;
    }

    final List<Integer> getDefaultProcessorApicIds() {
        if (!hasDefaultConfiguration()) {
            return Collections.emptyList();
        }
        final ArrayList<Integer> processorApicIds = new ArrayList<Integer>(2);
        processorApicIds.add(Integer.valueOf(0));
        processorApicIds.add(Integer.valueOf(1));
        return processorApicIds;
    }

    /**
     * Initialize this instance.
     *
     * @param mem
     */
    private MPFloatingPointerStructure(MemoryResource mem) {
        this.mem = mem;
    }

    /**
     * Is this a valid MPFP structure?
     */
    private final boolean isValid() {
        // Length should be 16
        if (getLength() != 16) {
            return false;
        }
        // Check checksum
        int sum = 0;
        for (int i = 0; i < 16; i++) {
            sum += mem.getByte(i) & 0xFF;
            sum &= 0xFF;
        }
        if (sum != 0) {
            return false;
        }

        return true;
    }

    private final boolean initConfigTable(ResourceManager rm,
                                          ResourceOwner owner) {
        final Address tablePtr = getMPConfigTablePtr();
        if (tablePtr.isZero()) {
            return false;
        }
        int size = 0x2C; // Base table length
        try {
            MemoryResource mem = rm.claimMemoryResource(owner, tablePtr, size,
                ResourceManager.MEMMODE_NORMAL);
            // Read the table length
            int baseTableLen = mem.getChar(4);
            mem.release();
            // Claim the full table.
            // BootLogInstance.get().info("baseTableLength " + baseTableLen);
            size = baseTableLen;
            mem = rm.claimMemoryResource(owner, tablePtr, size,
                ResourceManager.MEMMODE_NORMAL);
            this.configTable = new MPConfigTable(mem);
            if (configTable.isValid()) {
                return true;
            } else {
                configTable.release();
                configTable = null;
                return false;
            }
        } catch (ResourceNotFreeException ex) {
            BootLogInstance.get().warn("Cannot claim MP config table region");
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Find the structure between to pointers.
     *
     * @param rm
     * @param owner
     * @param startPtr
     * @param endPtr
     * @return The structure found, or null if not found
     */
    private static MPFloatingPointerStructure find(ResourceManager rm,
                                                   ResourceOwner owner, int startPtr, int endPtr) {
        final MemoryScanner ms = rm.getMemoryScanner();
        Address ptr = Address.fromIntZeroExtend(startPtr);
        int size = endPtr - startPtr;
        final int stepSize = 16;
        while (size > 0) {
            Address res = ms.findInt32(ptr, size, MAGIC, stepSize);
            if (res != null) {
                try {
                    final MemoryResource mem;
                    mem = rm.claimMemoryResource(owner, res, 16,
                        ResourceManager.MEMMODE_NORMAL);
                    final MPFloatingPointerStructure mp = new MPFloatingPointerStructure(
                        mem);
                    if (mp.isValid()) {
                        if (mp.hasDefaultConfiguration() || mp.initConfigTable(rm, owner)) {
                            return mp;
                        }
                    }
                    mp.release();
                } catch (ResourceNotFreeException ex) {
                    BootLogInstance.get().warn("Cannot claim MP region");
                }
            }
            ptr = ptr.add(stepSize);
            size -= stepSize;
        }
        return null;
    }

    private static MPFloatingPointerStructure findInEbda(ResourceManager rm, ResourceOwner owner) {
        MemoryResource bda = null;
        try {
            bda = rm.claimMemoryResource(owner, Address.fromIntZeroExtend(0x40E), 2,
                ResourceManager.MEMMODE_NORMAL);
            final int ebdaSegment = bda.getChar(0) & 0xFFFF;
            if (ebdaSegment == 0) {
                return null;
            }
            final int ebdaAddress = ebdaSegment << 4;
            if ((ebdaAddress < 0x80000) || (ebdaAddress >= 0xA0000)) {
                return null;
            }
            return find(rm, owner, ebdaAddress, ebdaAddress + 1024);
        } catch (ResourceNotFreeException ex) {
            BootLogInstance.get().warn("Cannot claim BIOS data area while searching MP table");
            return null;
        } finally {
            if (bda != null) {
                bda.release();
            }
        }
    }

    private static MPFloatingPointerStructure findInLastKilobyteOfBaseMemory(ResourceManager rm,
                                                                              ResourceOwner owner) {
        MemoryResource bda = null;
        try {
            bda = rm.claimMemoryResource(owner, Address.fromIntZeroExtend(0x413), 2,
                ResourceManager.MEMMODE_NORMAL);
            final int baseMemoryKb = bda.getChar(0) & 0xFFFF;
            if (baseMemoryKb < 2) {
                return find(rm, owner, 639 * 1024, 640 * 1024);
            }
            final int end = baseMemoryKb * 1024;
            return find(rm, owner, end - 1024, end);
        } catch (ResourceNotFreeException ex) {
            BootLogInstance.get().warn("Cannot claim BIOS data area while searching base memory for MP table");
            return find(rm, owner, 639 * 1024, 640 * 1024);
        } finally {
            if (bda != null) {
                bda.release();
            }
        }
    }
}
