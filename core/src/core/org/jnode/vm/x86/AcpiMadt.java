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
import org.jnode.annotation.MagicPermission;
import org.jnode.bootlog.BootLogInstance;
import org.jnode.system.resource.MemoryResource;
import org.jnode.system.resource.MemoryScanner;
import org.jnode.system.resource.ResourceManager;
import org.jnode.system.resource.ResourceNotFreeException;
import org.jnode.system.resource.ResourceOwner;
import org.jnode.util.NumberUtils;
import org.vmmagic.unboxed.Address;

/**
 * Minimal ACPI MADT parser used during early x86 processor bring-up.
 */
@MagicPermission
final class AcpiMadt {

    private static final byte[] RSDP_MAGIC = {'R', 'S', 'D', ' ', 'P', 'T', 'R', ' '};
    private static final int SDT_HEADER_SIZE = 36;
    private static final int MADT_HEADER_SIZE = 44;

    private final String rootTableSignature;
    private final Address localApicAddress;
    private final List<Integer> processorApicIds;
    private final List<IoApicInfo> ioApics;

    private AcpiMadt(String rootTableSignature, Address localApicAddress,
                     List<Integer> processorApicIds, List<IoApicInfo> ioApics) {
        this.rootTableSignature = rootTableSignature;
        this.localApicAddress = localApicAddress;
        this.processorApicIds = Collections.unmodifiableList(processorApicIds);
        this.ioApics = Collections.unmodifiableList(ioApics);
    }

    static AcpiMadt find(ResourceManager rm, ResourceOwner owner) {
        final RsdpInfo rsdp = findRsdp(rm, owner);
        if (rsdp == null) {
            return null;
        }

        AcpiMadt madt = null;
        if (!rsdp.xsdtAddress.isZero()) {
            madt = parseRootTable(rm, owner, rsdp.xsdtAddress, true);
        }
        if ((madt == null) && !rsdp.rsdtAddress.isZero()) {
            madt = parseRootTable(rm, owner, rsdp.rsdtAddress, false);
        }
        if (madt != null) {
            BootLogInstance.get().info("Found " + madt);
        }
        return madt;
    }

    Address getLocalApicAddress() {
        return localApicAddress;
    }

    List<Integer> getProcessorApicIds() {
        return processorApicIds;
    }

    List<IoApicInfo> getIoApics() {
        return ioApics;
    }

    String getRootTableSignature() {
        return rootTableSignature;
    }

    public String toString() {
        return "ACPI MADT via " + rootTableSignature + ", local APIC 0x"
            + NumberUtils.hex(localApicAddress.toLong()) + ", processors "
            + processorApicIds.size() + ", I/O APICs " + ioApics.size();
    }

    private static RsdpInfo findRsdp(ResourceManager rm, ResourceOwner owner) {
        final MemoryScanner scanner = rm.getMemoryScanner();
        Address rsdp = findRsdpInEbda(rm, owner, scanner);
        if (rsdp == null) {
            rsdp = scanner.findInt8Array(Address.fromIntZeroExtend(0xE0000), 0x20000,
                RSDP_MAGIC, 0, RSDP_MAGIC.length, 16);
        }
        if (rsdp == null) {
            return null;
        }
        return loadRsdp(rm, owner, rsdp);
    }

    private static Address findRsdpInEbda(ResourceManager rm, ResourceOwner owner,
                                          MemoryScanner scanner) {
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
            return scanner.findInt8Array(Address.fromIntZeroExtend(ebdaAddress), 1024,
                RSDP_MAGIC, 0, RSDP_MAGIC.length, 16);
        } catch (ResourceNotFreeException ex) {
            BootLogInstance.get().warn("Cannot claim BIOS data area while searching RSDP");
            return null;
        } finally {
            if (bda != null) {
                bda.release();
            }
        }
    }

    private static RsdpInfo loadRsdp(ResourceManager rm, ResourceOwner owner, Address rsdpAddress) {
        MemoryResource header = null;
        MemoryResource full = null;
        try {
            header = rm.claimMemoryResource(owner, rsdpAddress, 36, ResourceManager.MEMMODE_NORMAL);
            final int revision = header.getByte(15) & 0xFF;
            final Address rsdtAddress = Address.fromIntZeroExtend(header.getInt(16));
            int length = 20;
            Address xsdtAddress = Address.zero();
            if (revision >= 2) {
                length = header.getInt(20);
                if (length < 36) {
                    length = 36;
                }
                xsdtAddress = Address.fromLong(header.getLong(24));
            }
            if (length > 36) {
                header.release();
                header = null;
                full = rm.claimMemoryResource(owner, rsdpAddress, length, ResourceManager.MEMMODE_NORMAL);
            } else {
                full = header;
                header = null;
            }
            if (!hasChecksum(full, 20)) {
                return null;
            }
            if ((revision >= 2) && !hasChecksum(full, length)) {
                return null;
            }
            return new RsdpInfo(revision, rsdtAddress, xsdtAddress);
        } catch (ResourceNotFreeException ex) {
            BootLogInstance.get().warn("Cannot claim RSDP while initializing ACPI MADT");
            return null;
        } finally {
            if (header != null) {
                header.release();
            }
            if (full != null) {
                full.release();
            }
        }
    }

    private static AcpiMadt parseRootTable(ResourceManager rm, ResourceOwner owner,
                                           Address rootAddress, boolean xsdt) {
        final SdtInfo root = loadSystemDescriptionTable(rm, owner, rootAddress);
        if (root == null) {
            return null;
        }
        try {
            if (root.length < SDT_HEADER_SIZE) {
                return null;
            }
            if (xsdt) {
                if (!"XSDT".equals(root.signature)) {
                    return null;
                }
            } else if (!"RSDT".equals(root.signature)) {
                return null;
            }
            final int entrySize = xsdt ? 8 : 4;
            final int tableCount = (root.length - SDT_HEADER_SIZE) / entrySize;
            for (int i = 0; i < tableCount; i++) {
                final int offset = SDT_HEADER_SIZE + (i * entrySize);
                final Address tableAddress = xsdt ? Address.fromLong(root.resource.getLong(offset))
                    : Address.fromIntZeroExtend(root.resource.getInt(offset));
                if (tableAddress.isZero()) {
                    continue;
                }
                final AcpiMadt madt = parseMadt(rm, owner, tableAddress, root.signature);
                if (madt != null) {
                    return madt;
                }
            }
            return null;
        } finally {
            root.resource.release();
        }
    }

    private static AcpiMadt parseMadt(ResourceManager rm, ResourceOwner owner,
                                      Address tableAddress, String rootSignature) {
        final SdtInfo table = loadSystemDescriptionTable(rm, owner, tableAddress);
        if (table == null) {
            return null;
        }
        try {
            if (!"APIC".equals(table.signature) || (table.length < MADT_HEADER_SIZE)) {
                return null;
            }

            Address localApicAddress = Address.fromIntZeroExtend(table.resource.getInt(36));
            final ArrayList<Integer> processorApicIds = new ArrayList<Integer>();
            final ArrayList<IoApicInfo> ioApics = new ArrayList<IoApicInfo>();

            int offset = MADT_HEADER_SIZE;
            while ((offset + 2) <= table.length) {
                final int type = table.resource.getByte(offset) & 0xFF;
                final int length = table.resource.getByte(offset + 1) & 0xFF;
                if ((length < 2) || ((offset + length) > table.length)) {
                    break;
                }

                switch (type) {
                    case 0:
                        if (length >= 8) {
                            final int apicId = table.resource.getByte(offset + 3) & 0xFF;
                            final int flags = table.resource.getInt(offset + 4);
                            if ((flags & 0x03) != 0) {
                                addProcessorApicId(processorApicIds, apicId);
                            }
                        }
                        break;
                    case 1:
                        if (length >= 12) {
                            final int ioApicId = table.resource.getByte(offset + 2) & 0xFF;
                            final Address ioApicAddress = Address.fromIntZeroExtend(table.resource.getInt(offset + 4));
                            final int gsiBase = table.resource.getInt(offset + 8);
                            ioApics.add(new IoApicInfo(ioApicId, ioApicAddress, gsiBase));
                        }
                        break;
                    case 5:
                        if (length >= 12) {
                            localApicAddress = Address.fromLong(table.resource.getLong(offset + 4));
                        }
                        break;
                    case 9:
                        if (length >= 16) {
                            final int x2ApicId = table.resource.getInt(offset + 4);
                            final int flags = table.resource.getInt(offset + 8);
                            if ((flags & 0x03) != 0) {
                                if ((x2ApicId >= 0) && (x2ApicId <= 0xFF)) {
                                    addProcessorApicId(processorApicIds, x2ApicId);
                                } else {
                                    BootLogInstance.get().warn("Ignoring x2APIC processor 0x"
                                        + NumberUtils.hex(x2ApicId) + " because x2APIC startup is not implemented yet");
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }

                offset += length;
            }

            if (processorApicIds.isEmpty() || localApicAddress.isZero()) {
                return null;
            }
            return new AcpiMadt(rootSignature, localApicAddress, processorApicIds, ioApics);
        } finally {
            table.resource.release();
        }
    }

    private static void addProcessorApicId(ArrayList<Integer> processorApicIds, int apicId) {
        final Integer value = Integer.valueOf(apicId);
        if (!processorApicIds.contains(value)) {
            processorApicIds.add(value);
        }
    }

    private static SdtInfo loadSystemDescriptionTable(ResourceManager rm, ResourceOwner owner,
                                                      Address tableAddress) {
        MemoryResource header = null;
        MemoryResource table = null;
        try {
            header = rm.claimMemoryResource(owner, tableAddress, 8, ResourceManager.MEMMODE_NORMAL);
            final String signature = getSignature(header);
            final int length = header.getInt(4);
            if (length < SDT_HEADER_SIZE) {
                return null;
            }
            header.release();
            header = null;

            table = rm.claimMemoryResource(owner, tableAddress, length, ResourceManager.MEMMODE_NORMAL);
            if (!hasChecksum(table, length)) {
                table.release();
                return null;
            }
            return new SdtInfo(signature, length, table);
        } catch (ResourceNotFreeException ex) {
            return null;
        } finally {
            if (header != null) {
                header.release();
            }
        }
    }

    private static boolean hasChecksum(MemoryResource mem, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += mem.getByte(i) & 0xFF;
            sum &= 0xFF;
        }
        return sum == 0;
    }

    private static String getSignature(MemoryResource mem) {
        final char[] signature = new char[4];
        for (int i = 0; i < 4; i++) {
            signature[i] = (char) (mem.getByte(i) & 0xFF);
        }
        return String.valueOf(signature);
    }

    static final class IoApicInfo {
        private final int id;
        private final Address address;
        private final int gsiBase;

        private IoApicInfo(int id, Address address, int gsiBase) {
            this.id = id;
            this.address = address;
            this.gsiBase = gsiBase;
        }

        int getId() {
            return id;
        }

        Address getAddress() {
            return address;
        }

        public String toString() {
            return "I/O APIC " + id + " @0x" + NumberUtils.hex(address.toLong()) + ", GSI base " + gsiBase;
        }
    }

    private static final class RsdpInfo {
        private final int revision;
        private final Address rsdtAddress;
        private final Address xsdtAddress;

        private RsdpInfo(int revision, Address rsdtAddress, Address xsdtAddress) {
            this.revision = revision;
            this.rsdtAddress = rsdtAddress;
            this.xsdtAddress = xsdtAddress;
        }
    }

    private static final class SdtInfo {
        private final String signature;
        private final int length;
        private final MemoryResource resource;

        private SdtInfo(String signature, int length, MemoryResource resource) {
            this.signature = signature;
            this.length = length;
            this.resource = resource;
        }
    }
}
