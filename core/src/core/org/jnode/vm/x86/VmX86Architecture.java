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

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import org.jnode.annotation.Internal;
import org.jnode.annotation.MagicPermission;
import org.jnode.assembler.x86.X86Constants;
import org.jnode.bootlog.BootLogInstance;
import org.jnode.system.resource.ResourceManager;
import org.jnode.system.resource.ResourceNotFreeException;
import org.jnode.system.resource.ResourceOwner;
import org.jnode.vm.BaseVmArchitecture;
import org.jnode.vm.Unsafe;
import org.jnode.vm.VmMagic;
import org.jnode.vm.VmMultiMediaSupport;
import org.jnode.vm.VmSystem;
import org.jnode.vm.classmgr.VmIsolatedStatics;
import org.jnode.vm.classmgr.VmSharedStatics;
import org.jnode.vm.compiler.NativeCodeCompiler;
import org.jnode.vm.facade.MemoryMapEntry;
import org.jnode.vm.facade.VmUtils;
import org.jnode.vm.scheduler.IRQManager;
import org.jnode.vm.scheduler.VmProcessor;
import org.jnode.vm.scheduler.VmScheduler;
import org.jnode.vm.x86.compiler.l1a.X86Level1ACompiler;
import org.jnode.vm.x86.compiler.l1b.X86Level1BCompiler;
import org.jnode.vm.x86.compiler.l2.X86Level2Compiler;
import org.jnode.vm.x86.compiler.stub.X86StubCompiler;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Architecture descriptor for the Intel X86 architecture.
 *
 * @author Ewout Prangsma (epr@users.sourceforge.net)
 */
@MagicPermission
public abstract class VmX86Architecture extends BaseVmArchitecture {

    private static final Address DEFAULT_LOCAL_APIC_ADDRESS = Address.fromIntZeroExtend(0xFEE00000);

    /**
     * Start address of the boot image (1Mb)
     */
    public static final int BOOT_IMAGE_START = 0x00100000;

    // Page entry flags
    protected static final int PF_PRESENT = 0x00000001;

    protected static final int PF_WRITE = 0x00000002;

    protected static final int PF_USER = 0x00000004;

    protected static final int PF_PWT = 0x00000008;

    protected static final int PF_PCD = 0x00000010;

    protected static final int PF_ACCESSED = 0x00000020;

    protected static final int PF_DIRTY = 0x00000040;

    protected static final int PF_PSE = 0x00000080;

    protected static final int MBMMAP_BASEADDR = 0; // 64-bit base address

    protected static final int MBMMAP_LENGTH = 8; // 64-bit length

    protected static final int MBMMAP_TYPE = 16; // 32-bit type

    protected static final int MBMMAP_ESIZE = 20;

    // Values for MBMMAP_TYPE field
    protected static final int MMAP_TYPE_MEMORY = 1; // Available memory

    protected static final int MMAP_TYPE_RESERVED = 2; // Reserved memory

    protected static final int MMAP_TYPE_ACPI = 3; // ACPI reclaim memory

    protected static final int MMAP_TYPE_NVS = 4; // ACPI NVS memory

    protected static final int MMAP_TYPE_UNUSABLE = 5; // Memory with errors

    // found in it

    /**
     * The compilers
     */
    private final NativeCodeCompiler[] compilers;

    /**
     * The compilers under test
     */
    private final NativeCodeCompiler[] testCompilers;

    /**
     * The local APIC accessor, if any
     */
    private LocalAPIC localAPIC;

    /**
     * The MP configuration table
     */
    private MPConfigTable mpConfigTable;

    /**
     * Programmable interrupt controller
     */
    private PIC8259A pic8259a;

    /**
     * The boot processor
     */
    private transient VmX86Processor bootProcessor;

    /**
     * Topology decoder derived from the boot processor CPUID.
     */
    private transient X86CpuTopology cpuTopology;

    /**
     * True when firmware already enumerates every processor, including SMT threads.
     */
    private transient boolean firmwareEnumeratesProcessors;

    /**
     * Human readable source used to enumerate processors.
     */
    private transient String processorEnumerationSource;

    /**
     * The centralized irq manager
     */
    private transient X86IRQManager irqManager;

    /**
     * Initialize this instance using the default compiler.
     */
    public VmX86Architecture(int referenceSize) {
        this(referenceSize, "L1A");
    }

    /**
     * Initialize this instance.
     *
     * @param compiler the name of the compiler to use as standard.  If
     *                 the supplied name is {@code null} or doesn't match (case insensitively)
     *                 one of the known names, the default compiler will be used.
     */
    public VmX86Architecture(int referenceSize, String compiler) {
        super(referenceSize, new VmX86StackReader(referenceSize));
        this.compilers = new NativeCodeCompiler[2];
        this.compilers[0] = new X86StubCompiler();
        // Compare insensitively, producing a warning if the user selects
        // an unknown compiler, and using a default where appropriate.
        if (compiler != null && compiler.length() > 0 &&
            !compiler.equalsIgnoreCase("default")) {
            if ("L1B".equalsIgnoreCase(compiler)) {
                this.compilers[1] = new X86Level1BCompiler();
            } else if ("L1A".equalsIgnoreCase(compiler)) {
                this.compilers[1] = new X86Level1ACompiler();
            } else {
                BootLogInstance.get().warn("JNode native compiler '" + compiler + "' is unknown.");
            }
        }
        if (this.compilers[1] == null) {
            BootLogInstance.get().warn("JNode native compiler defaulting to 'L1A'");
            this.compilers[1] = new X86Level1ACompiler();
        }
        this.testCompilers = new NativeCodeCompiler[1];
        this.testCompilers[0] = new X86Level2Compiler();
    }

    /**
     * Gets the name of this architecture.
     *
     * @return name
     */
    public final String getName() {
        return "x86";
    }

    /**
     * Gets the full name of this architecture, including operating mode.
     *
     * @return Name
     */
    public String getFullName() {
        if (getReferenceSize() == 4) {
            return getName() + "-32";
        } else {
            return getName() + "-64";
        }
    }

    /**
     * Gets the byte ordering of this architecture.
     *
     * @return ByteOrder
     */
    public final ByteOrder getByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    /**
     * Gets the current operating mode; i.e. 32 or 64 bit mode.
     *
     * @return mode
     */
    public final X86Constants.Mode getMode() {
        if (getReferenceSize() == 4) {
            return X86Constants.Mode.CODE32;
        } else {
            return X86Constants.Mode.CODE64;
        }
    }

    /**
     * Gets all compilers for this architecture.
     *
     * @return The compilers, sorted by optimization level, from least
     *         optimizations to most optimizations.
     */
    public final NativeCodeCompiler[] getCompilers() {
        return compilers;
    }

    /**
     * Gets all test compilers for this architecture.
     *
     * @return The compilers, sorted by optimization level, from least
     *         optimizations to most optimizations.
     */
    public final NativeCodeCompiler[] getTestCompilers() {
        return testCompilers;
    }

    /**
     * @see org.jnode.vm.BaseVmArchitecture#initializeProcessors(ResourceManager)
     */
    protected final void initializeProcessors(ResourceManager rm) {
        // Mark current cpu as bootprocessor
        final VmX86Processor bootCpu = (VmX86Processor) VmMagic.currentProcessor();
        this.bootProcessor = bootCpu;
        bootCpu.setBootProcessor(true);
        firmwareEnumeratesProcessors = false;
        processorEnumerationSource = "bootstrap";

        // Initialize HyperV
        initializeHyperV();

        final String cmdLine = VmSystem.getCmdLine();
        if (cmdLine.contains("mp=no")) {
            return;
        }
        final ResourceOwner owner = ResourceOwner.SYSTEM;

        if (initializeProcessorsViaAcpi(rm, owner, bootCpu)) {
            BootLogInstance.get().info("Activating timeslice interrupts");
            bootCpu.activateTimeSliceInterrupts();
            return;
        }
        if (initializeProcessorsViaMp(rm, owner, bootCpu)) {
            return;
        }
        initializeProcessorsViaCpuTopologyFallback(rm, owner, bootCpu);
    }

    /**
     * Create a processor instance for this architecture.
     *
     * @return The processor
     */
    public abstract VmProcessor createProcessor(int id,
                                                VmSharedStatics sharedStatics, VmIsolatedStatics isolatedStatics,
                                                VmScheduler scheduler);

    @Override
    @Internal
    public final IRQManager createIRQManager(VmProcessor processor) {
        synchronized (this) {
            // Create PIC if not available
            if (pic8259a == null) {
                pic8259a = new PIC8259A();
            }
            if (irqManager == null) {
                irqManager = new X86IRQManager(bootProcessor, pic8259a);
            }
        }
        return irqManager;
    }

    /**
     * Initialize a processor wrt. APIC and add it to the list of processors.
     *
     * @param cpu
     */
    final void initX86Processor(VmX86Processor cpu) {
        cpu.setApic(localAPIC);
        cpu.setTopology(cpuTopology);
        super.addProcessor(cpu);
    }

    final boolean hasFirmwareEnumeratedProcessors() {
        return firmwareEnumeratesProcessors;
    }

    private boolean initializeProcessorsViaAcpi(ResourceManager rm, ResourceOwner owner,
                                                VmX86Processor bootCpu) {
        final AcpiMadt madt = AcpiMadt.find(rm, owner);
        if (madt == null) {
            return false;
        }
        if (!initializeBootstrapApic(rm, owner, bootCpu, madt.getLocalApicAddress())) {
            return false;
        }

        firmwareEnumeratesProcessors = true;
        processorEnumerationSource = "ACPI MADT via " + madt.getRootTableSignature();
        for (AcpiMadt.IoApicInfo ioApicInfo : madt.getIoApics()) {
            try {
                final IOAPIC ioApic = new IOAPIC(rm, owner, ioApicInfo.getAddress());
                ioApic.dump(System.out);
            } catch (ResourceNotFreeException ex) {
                BootLogInstance.get().error("Cannot claim I/O APIC region " + ioApicInfo, ex);
            }
        }
        startApplicationProcessors(rm, bootCpu, madt.getProcessorApicIds());
        return true;
    }

    private boolean initializeProcessorsViaMp(ResourceManager rm, ResourceOwner owner,
                                              VmX86Processor bootCpu) {
        final MPFloatingPointerStructure mp = MPFloatingPointerStructure.find(rm, owner);
        if (mp == null) {
            BootLogInstance.get().info("No MP table found");
            return false;
        }
        int defaultConfigurationType = 0;
        try {
            BootLogInstance.get().info("Found " + mp);
            this.mpConfigTable = mp.getMPConfigTable();
            defaultConfigurationType = mp.getSystemConfigurationType();
        } finally {
            mp.release();
        }

        if (mpConfigTable == null) {
            if (defaultConfigurationType != 0) {
                initializeProcessorsViaDefaultMpConfig(rm, owner, bootCpu, mp,
                    defaultConfigurationType);
                return true;
            }
            return false;
        }

        processorEnumerationSource = "Intel MP table";

        mpConfigTable.dump(System.out);
        if (!initializeBootstrapApic(rm, owner, bootCpu, mpConfigTable.getLocalApicAddress())) {
            return false;
        }

        for (MPEntry entry : mpConfigTable.entries()) {
            if (entry instanceof MPIOAPICEntry) {
                final MPIOAPICEntry apicEntry = (MPIOAPICEntry) entry;
                if (apicEntry.getFlags() != 0) {
                    try {
                        final IOAPIC ioAPIC = new IOAPIC(rm, owner, apicEntry.getAddress());
                        ioAPIC.dump(System.out);
                        break;
                    } catch (ResourceNotFreeException ex) {
                        BootLogInstance.get().error("Cannot claim I/O APIC region ", ex);
                    }
                }
            }
        }

        try {
            VmX86Processor.detectAndstartLogicalProcessors(rm);
        } catch (ResourceNotFreeException ex) {
            BootLogInstance.get().error("Cannot claim region for logical processor startup", ex);
        }

        final X86CpuID cpuId = (X86CpuID) bootCpu.getCPUID();
        final HashMap<Integer, MPProcessorEntry> physCpus = new HashMap<Integer, MPProcessorEntry>();
        for (MPEntry e : mpConfigTable.entries()) {
            if (e.getEntryType() == 0) {
                final MPProcessorEntry cpuEntry = (MPProcessorEntry) e;
                if (cpuEntry.isEnabled() && !cpuEntry.isBootstrap()) {
                    final int apicId = cpuEntry.getApicID();
                    final int physId = cpuId.getPhysicalPackageId(apicId);
                    if (!physCpus.containsKey(physId)) {
                        physCpus.put(physId, cpuEntry);
                    }
                }
            }
        }

        for (MPProcessorEntry cpuEntry : physCpus.values()) {
            final int apicId = cpuEntry.getApicID();
            final VmX86Processor newCpu = (VmX86Processor) createProcessor(apicId,
                VmUtils.getVm().getSharedStatics(), bootCpu.getIsolatedStatics(),
                bootCpu.getScheduler());
            initX86Processor(newCpu);
            try {
                newCpu.startup(rm);
            } catch (ResourceNotFreeException ex) {
                BootLogInstance.get().error("Cannot claim region for processor startup", ex);
            }
        }

        BootLogInstance.get().info("Activating timeslice interrupts");
        bootCpu.activateTimeSliceInterrupts();
        return true;
    }

    private void initializeProcessorsViaDefaultMpConfig(ResourceManager rm, ResourceOwner owner,
                                                        VmX86Processor bootCpu,
                                                        MPFloatingPointerStructure mp,
                                                        int defaultConfigurationType) {
        processorEnumerationSource = "Intel MP default config type "
            + defaultConfigurationType;

        if (!initializeBootstrapApic(rm, owner, bootCpu, mp.getDefaultLocalApicAddress())) {
            return;
        }

        try {
            final IOAPIC ioApic = new IOAPIC(rm, owner, mp.getDefaultIoApicAddress());
            ioApic.dump(System.out);
        } catch (ResourceNotFreeException ex) {
            BootLogInstance.get().warn("Cannot claim default I/O APIC region", ex);
        }

        final int processorCountBefore = VmUtils.getVm().availableProcessors();
        try {
            VmX86Processor.detectAndstartLogicalProcessors(rm);
        } catch (ResourceNotFreeException ex) {
            BootLogInstance.get().error("Cannot claim region for logical processor startup", ex);
        }

        if (VmUtils.getVm().availableProcessors() == processorCountBefore) {
            startApplicationProcessors(rm, bootCpu, createDefaultMpProcessorApicIds());
        }

        BootLogInstance.get().info("Activating timeslice interrupts");
        bootCpu.activateTimeSliceInterrupts();
    }

    private void initializeProcessorsViaCpuTopologyFallback(ResourceManager rm,
                                                            ResourceOwner owner,
                                                            VmX86Processor bootCpu) {
        final X86CpuID cpuId = (X86CpuID) bootCpu.getCPUID();
        final int logicalProcessors = cpuId.getLogicalProcessorsPerPackage();
        System.out.println("CPUID topology probe: apic=" + cpuId.hasAPIC()
            + ", htt=" + cpuId.hasHTT()
            + ", leaf1-logical=" + cpuId.getLeaf1LogicalProcessorField()
            + ", logical/package=" + logicalProcessors
            + ", topology=" + cpuId.getTopology().getSource());
        if (!cpuId.hasAPIC() || (logicalProcessors <= 1)) {
            return;
        }

        System.out.println("No firmware SMP tables found; trying CPUID/APIC fallback");
        processorEnumerationSource = "CPUID/APIC fallback";
        if (!initializeBootstrapApic(rm, owner, bootCpu, DEFAULT_LOCAL_APIC_ADDRESS)) {
            processorEnumerationSource = "bootstrap";
            return;
        }

        final int processorCountBefore = VmUtils.getVm().availableProcessors();
        try {
            VmX86Processor.detectAndstartLogicalProcessors(rm);
        } catch (ResourceNotFreeException ex) {
            BootLogInstance.get().error("Cannot claim region for CPUID/APIC fallback processor startup", ex);
        }

        final int processorCountAfter = VmUtils.getVm().availableProcessors();
        if (processorCountAfter > processorCountBefore) {
            System.out.println("CPUID/APIC fallback started "
                + (processorCountAfter - processorCountBefore) + " additional processor(s)");
            BootLogInstance.get().info("Activating timeslice interrupts");
            bootCpu.activateTimeSliceInterrupts();
        } else {
            System.out.println("CPUID/APIC fallback did not start additional processors");
        }
    }

    private Iterable<Integer> createDefaultMpProcessorApicIds() {
        final java.util.ArrayList<Integer> processorApicIds = new java.util.ArrayList<Integer>(2);
        processorApicIds.add(Integer.valueOf(0));
        processorApicIds.add(Integer.valueOf(1));
        return processorApicIds;
    }

    private boolean initializeBootstrapApic(ResourceManager rm, ResourceOwner owner,
                                            VmX86Processor bootCpu, Address localApicAddress) {
        try {
            localAPIC = new LocalAPIC(rm, owner, localApicAddress);
        } catch (ResourceNotFreeException ex) {
            BootLogInstance.get().error("Cannot claim APIC region", ex);
            return false;
        }

        bootCpu.setApic(localAPIC);
        bootCpu.loadAndSetApicID();
        final X86CpuID cpuId = (X86CpuID) bootCpu.getCPUID();
        cpuTopology = cpuId.getTopology();
        bootCpu.setTopology(cpuTopology);
        BootLogInstance.get().info("CPU topology: " + cpuTopology);
        return true;
    }

    @Override
    public String getProcessorSummary() {
        if (bootProcessor == null) {
            return null;
        }

        final HashSet<Integer> packages = new HashSet<Integer>();
        final HashSet<String> cores = new HashSet<String>();
        int logicalProcessors = 0;

        for (org.jnode.vm.facade.VmProcessor processor : VmUtils.getVm().getProcessors()) {
            if (processor instanceof VmX86Processor) {
                final VmX86Processor x86Processor = (VmX86Processor) processor;
                packages.add(Integer.valueOf(x86Processor.getPackageId()));
                cores.add(x86Processor.getPackageId() + ":" + x86Processor.getCoreId());
                logicalProcessors++;
            }
        }

        final X86CpuTopology topology = cpuTopology;
        final StringBuilder sb = new StringBuilder();
        sb.append("enumeration=");
        sb.append((processorEnumerationSource == null) ? "unknown" : processorEnumerationSource);
        if (topology != null) {
            sb.append(", decode=");
            sb.append(topology.getSource());
        }
        sb.append(", packages=");
        sb.append(packages.isEmpty() ? 1 : packages.size());
        sb.append(", cores=");
        sb.append(cores.isEmpty() ? 1 : cores.size());
        sb.append(", logical=");
        sb.append((logicalProcessors == 0) ? 1 : logicalProcessors);
        if (topology != null) {
            sb.append(", cores/package=");
            sb.append(topology.getCoresPerPackage());
            sb.append(", threads/core=");
            sb.append(topology.getThreadsPerCore());
        }
        return sb.toString();
    }

    private void startApplicationProcessors(ResourceManager rm, VmX86Processor bootCpu,
                                            Iterable<Integer> apicIds) {
        final int bootApicId = bootCpu.getId();
        for (Integer apicIdValue : apicIds) {
            final int apicId = apicIdValue.intValue();
            if (apicId == bootApicId) {
                continue;
            }
            final VmX86Processor newCpu = (VmX86Processor) createProcessor(apicId,
                VmUtils.getVm().getSharedStatics(), bootCpu.getIsolatedStatics(),
                bootCpu.getScheduler());
            initX86Processor(newCpu);
            try {
                newCpu.startup(rm);
            } catch (ResourceNotFreeException ex) {
                BootLogInstance.get().error("Cannot claim region for processor startup", ex);
            }
        }
    }

    /**
     * Print the multiboot memory map to Unsafe.debug.
     */
    protected final void dumpMultibootMMap() {
        final int cnt = UnsafeX86.getMultibootMMapLength();
        Address mmap = UnsafeX86.getMultibootMMap();

        Unsafe.debug("Memory map\n");
        for (int i = 0; i < cnt; i++) {
            long base = mmap
                .loadLong(Offset.fromIntZeroExtend(MBMMAP_BASEADDR));
            long length = mmap
                .loadLong(Offset.fromIntZeroExtend(MBMMAP_LENGTH));
            int type = mmap.loadInt(Offset.fromIntZeroExtend(MBMMAP_TYPE));
            mmap = mmap.add(MBMMAP_ESIZE);

            Unsafe.debug(mmapTypeToString(type));
            Unsafe.debug(base);
            Unsafe.debug(" - ");
            Unsafe.debug(base + length - 1);
            Unsafe.debug('\n');
        }
    }

    /**
     * Convert an mmap type into a human readable string.
     *
     * @param type
     * @return
     */
    private final String mmapTypeToString(int type) {
        switch (type) {
            case MMAP_TYPE_MEMORY:
                return "Available    ";
            case MMAP_TYPE_RESERVED:
                return "Reserved     ";
            case MMAP_TYPE_ACPI:
                return "ACPI reclaim ";
            case MMAP_TYPE_NVS:
                return "ACPI NVS     ";
            case MMAP_TYPE_UNUSABLE:
                return "Unusable     ";
            default:
                return "Undefined    ";
        }
    }

    /**
     * @see org.jnode.vm.BaseVmArchitecture#createMemoryMap()
     */
    protected MemoryMapEntry[] createMemoryMap() {
        final int cnt = UnsafeX86.getMultibootMMapLength();
        final MemoryMapEntry[] map = new MemoryMapEntry[cnt];
        Address mmap = UnsafeX86.getMultibootMMap();

        for (int i = 0; i < cnt; i++) {
            long base = mmap
                .loadLong(Offset.fromIntZeroExtend(MBMMAP_BASEADDR));
            long length = mmap
                .loadLong(Offset.fromIntZeroExtend(MBMMAP_LENGTH));
            int type = mmap.loadInt(Offset.fromIntZeroExtend(MBMMAP_TYPE));
            mmap = mmap.add(MBMMAP_ESIZE);

            map[i] = new X86MemoryMapEntry(Address.fromLong(base), Extent
                .fromLong(length), type);
        }

        return map;
    }

    /**
     * @see org.jnode.vm.BaseVmArchitecture#createMultiMediaSupport()
     */
    protected VmMultiMediaSupport createMultiMediaSupport() {
        final X86CpuID id = (X86CpuID) VmProcessor.current().getCPUID();
        if (id.hasMMX()) {
            return new MMXMultiMediaSupport();
        } else {
            return super.createMultiMediaSupport();
        }
    }

    /**
     * Identify ourselves in HyperV (when that is detected)
     */
    void initializeHyperV() {
        final X86CpuID id = (X86CpuID) VmProcessor.current().getCPUID();
        if (!id.detectHyperV())
            return;
        Unsafe.debug("Initializing HyperV");
        long guestOsId = (0x29L << 48);
        UnsafeX86.writeMSR(Word.fromIntZeroExtend(HyperV.HV_X64_MSR_GUEST_OS_ID), guestOsId);
        Unsafe.debug("Initialized Hyper-V guest OS ID");
    }
}
