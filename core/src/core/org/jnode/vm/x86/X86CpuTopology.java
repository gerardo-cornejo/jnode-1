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

/**
 * Decodes package/core/thread identifiers from an APIC ID.
 *
 * <p>This class intentionally stays lightweight. It gives the VM a consistent
 * view of modern CPU topology using the CPUID information that is already
 * available very early during bootstrap. ACPI MADT based enumeration still
 * needs to be implemented separately.</p>
 */
final class X86CpuTopology {

    private final String source;
    private final int logicalProcessorsPerPackage;
    private final int coresPerPackage;
    private final int threadsPerCore;
    private final int threadBits;
    private final int coreBits;
    private final int packageShift;

    private X86CpuTopology(String source, int logicalProcessorsPerPackage,
                           int coresPerPackage, int threadsPerCore,
                           int threadBits, int coreBits, int packageShift) {
        this.source = source;
        this.logicalProcessorsPerPackage = sanitizeCount(logicalProcessorsPerPackage);
        this.coresPerPackage = sanitizeCount(coresPerPackage);
        this.threadsPerCore = sanitizeCount(threadsPerCore);
        this.threadBits = Math.max(0, threadBits);
        this.coreBits = Math.max(0, coreBits);
        this.packageShift = Math.max(0, packageShift);
    }

    static X86CpuTopology detect(X86CpuID cpuId) {
        final X86CpuID.ExtendedTopologyInfo extendedTopology = cpuId.getExtendedTopologyInfo();
        if (extendedTopology != null) {
            return fromExtendedTopology(extendedTopology);
        }
        final X86CpuTopology topology = fromModernCounts(cpuId);
        if (topology != null) {
            return topology;
        }
        return fromLegacyCount(cpuId);
    }

    private static X86CpuTopology fromExtendedTopology(X86CpuID.ExtendedTopologyInfo topologyInfo) {
        final int logicalProcessors = sanitizeCount(topologyInfo.logicalProcessorsPerPackage);
        final int threadsPerCore = Math.min(logicalProcessors,
            sanitizeCount(topologyInfo.threadsPerCore));
        final int coreBits = Math.max(0, topologyInfo.packageShift - topologyInfo.threadBits);
        int coresPerPackage = logicalProcessors / threadsPerCore;
        if (coresPerPackage <= 0) {
            coresPerPackage = 1;
        }
        return new X86CpuTopology(topologyInfo.source, logicalProcessors,
            coresPerPackage, threadsPerCore, topologyInfo.threadBits,
            coreBits, topologyInfo.packageShift);
    }

    private static X86CpuTopology fromModernCounts(X86CpuID cpuId) {
        int logicalProcessors = sanitizeCount(cpuId.getLogicalProcessors());
        int coresPerPackage = cpuId.getIntelCoreCount();
        String source = "cpuid.1";
        if (coresPerPackage > 0) {
            source = "cpuid.4";
        } else {
            coresPerPackage = cpuId.getAmdCoreCount();
            if (coresPerPackage > 0) {
                source = "cpuid.80000008";
            }
        }
        if (coresPerPackage <= 0) {
            return null;
        }

        coresPerPackage = sanitizeCount(coresPerPackage);
        if (logicalProcessors < coresPerPackage) {
            // Some modern hypervisors expose multicore topology via deterministic
            // cache parameters, but still report a legacy logical processor count
            // of 1 in CPUID leaf 1. In that case, trust the core count as a
            // lower bound for the number of runnable logical CPUs.
            logicalProcessors = coresPerPackage;
            source = source + "+core-fallback";
        }

        coresPerPackage = Math.min(logicalProcessors, coresPerPackage);
        final int threadsPerCore;
        if (logicalProcessors > coresPerPackage && (logicalProcessors % coresPerPackage) == 0) {
            threadsPerCore = logicalProcessors / coresPerPackage;
        } else {
            if (logicalProcessors < coresPerPackage) {
                coresPerPackage = logicalProcessors;
            }
            threadsPerCore = 1;
        }

        final int threadBits = bitWidth(threadsPerCore);
        final int packageShift = bitWidth(logicalProcessors);
        final int coreBits = Math.max(0, packageShift - threadBits);
        return new X86CpuTopology(source, logicalProcessors, coresPerPackage,
            threadsPerCore, threadBits, coreBits, packageShift);
    }

    private static X86CpuTopology fromLegacyCount(X86CpuID cpuId) {
        final int logicalProcessors = sanitizeCount(cpuId.getLogicalProcessors());
        final int packageShift = bitWidth(logicalProcessors);
        final int threadBits = bitWidth(logicalProcessors);
        return new X86CpuTopology("cpuid.1-legacy", logicalProcessors, 1,
            logicalProcessors, threadBits, 0, packageShift);
    }

    int getLogicalProcessorsPerPackage() {
        return logicalProcessorsPerPackage;
    }

    int getCoresPerPackage() {
        return coresPerPackage;
    }

    int getThreadsPerCore() {
        return threadsPerCore;
    }

    String getSource() {
        return source;
    }

    int getPackageId(int apicId) {
        return apicId >>> packageShift;
    }

    int getCoreId(int apicId) {
        if (coreBits == 0) {
            return 0;
        }
        return (apicId >>> threadBits) & bitMask(coreBits);
    }

    int getThreadId(int apicId) {
        if (threadBits == 0) {
            return 0;
        }
        return apicId & bitMask(threadBits);
    }

    int getPackageBaseApicId(int apicId) {
        return apicId & ~bitMask(packageShift);
    }

    public String toString() {
        return "source=" + source + ", logical/package=" + logicalProcessorsPerPackage
            + ", cores/package=" + coresPerPackage + ", threads/core=" + threadsPerCore
            + ", apic-shift=" + packageShift;
    }

    private static int sanitizeCount(int value) {
        return (value <= 0) ? 1 : value;
    }

    private static int bitWidth(int count) {
        int value = sanitizeCount(count) - 1;
        int width = 0;
        while (value != 0) {
            width++;
            value >>>= 1;
        }
        return width;
    }

    private static int bitMask(int bits) {
        if (bits <= 0) {
            return 0;
        }
        if (bits >= 31) {
            return 0x7FFFFFFF;
        }
        return (1 << bits) - 1;
    }
}
