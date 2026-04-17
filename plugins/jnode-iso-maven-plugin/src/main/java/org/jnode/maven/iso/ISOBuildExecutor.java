package org.jnode.maven.iso;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.apache.tools.ant.Project;

/**
 * Utility class to execute JNode build tasks via reflection.
 * Allows invoking Ant task classes without directly depending on Ant.
 *
 * Tasks executed:
 * - AsmConstBuilder: Generate ASM constants from Java class metadata
 * - Asm: Compile x86 native assembly code
 * - BootImageBuilder: Create bootable kernel image
 * - ISOTask: Generate ISO 9660 filesystem
 */
public class ISOBuildExecutor {

    private final URLClassLoader taskClassLoader;

    /**
     * Constructor that creates a classloader with necessary JARs.
     */
    public ISOBuildExecutor(List<File> jarFiles) throws MalformedURLException {
        URL[] urls = new URL[jarFiles.size()];
        for (int i = 0; i < jarFiles.size(); i++) {
            urls[i] = jarFiles.get(i).toURI().toURL();
        }
        this.taskClassLoader = URLClassLoader.newInstance(urls, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Generate ASM constants using AsmConstBuilder.
     */
    public void generateAsmConstants(File outputDir, String bits, List<File> classesURLs) throws Exception {
        try {
            Class<?> asmConstBuilderClass = taskClassLoader.loadClass("org.jnode.build.x86.AsmConstBuilder");
            Object builder = asmConstBuilderClass.newInstance();

            // Set destination file
            Method setDestFile = asmConstBuilderClass.getMethod("setDestFile", File.class);
            setDestFile.invoke(builder, new File(outputDir, "java.inc"));

            // Set bits
            Method setBits = asmConstBuilderClass.getMethod("setBits", int.class);
            setBits.invoke(builder, Integer.parseInt(bits));

            // Set class URLs (comma-separated, in jar: format for JARs)
            StringBuilder urlsBuilder = new StringBuilder();
            for (File f : classesURLs) {
                if (urlsBuilder.length() > 0) urlsBuilder.append(",");
                String path = f.getAbsolutePath().replace("\\", "/");
                String url;
                if (path.endsWith(".jar")) {
                    // Use jar: URL scheme for JAR files
                    url = "jar:file:///" + path + "!/";
                } else {
                    // Regular file URL for directories/other files
                    url = "file:///" + path;
                }
                urlsBuilder.append(url);
                System.out.println("DEBUG: Adding classpath URL: " + url);
            }
            String classesURLStr = urlsBuilder.toString();
            System.out.println("DEBUG: Final classesURL: " + classesURLStr);
            Method setClassesURL = asmConstBuilderClass.getMethod("setClassesURL", String.class);
            setClassesURL.invoke(builder, classesURLStr);

            // Execute
            Method execute = asmConstBuilderClass.getMethod("execute");
            execute.invoke(builder);

        } catch (ClassNotFoundException e) {
            throw new Exception("AsmConstBuilder not found in classpath. Check builder module compilation.", e);
        }
    }

    /**
     * Compile x86 native assembly using Asm task.
     */
    public void compileAssembly(File sourceDir, File outputDir, String bits, String version) throws Exception {
        try {
            Class<?> asmTaskClass = taskClassLoader.loadClass("org.jnode.ant.taskdefs.Asm");
            Object asmTask = asmTaskClass.newInstance();

            // Set the Ant Project on the Task
            Project project = new Project();
            Method setProject = asmTaskClass.getMethod("setProject", Project.class);
            setProject.invoke(asmTask, project);

            // Set source and output directories
            Method setSrcdir = asmTaskClass.getMethod("setSrcdir", File.class);
            setSrcdir.invoke(asmTask, sourceDir);

            Method setDestdir = asmTaskClass.getMethod("setDestdir", File.class);
            setDestdir.invoke(asmTask, outputDir);

            // Set output format
            Method setOutputFormat = asmTaskClass.getMethod("setOutputFormat", String.class);
            setOutputFormat.invoke(asmTask, "elf");

            // Set bits and extension
            Method setBits = asmTaskClass.getMethod("setBits", int.class);
            setBits.invoke(asmTask, Integer.parseInt(bits));

            Method setExtension = asmTaskClass.getMethod("setExtension", String.class);
            setExtension.invoke(asmTask, "o");

            // Set version
            Method setVersion = asmTaskClass.getMethod("setVersion", String.class);
            setVersion.invoke(asmTask, version);

            // Enable JNasm (pure Java assembler instead of external NASM)
            Method setEnableJNasm = asmTaskClass.getMethod("setEnableJNasm", boolean.class);
            setEnableJNasm.invoke(asmTask, true);

            // Execute
            Method execute = asmTaskClass.getMethod("execute");
            execute.invoke(asmTask);

        } catch (ClassNotFoundException e) {
            throw new Exception("Asm task not found in classpath. Check builder module compilation.", e);
        }
    }

    /**
     * Build bootable kernel image using BootImageBuilder.
     */
    public void buildBootImage(File bootimageDir, File nativeObjectFile, String bits,
                               File pluginListFile, File pluginDir, File classListFile,
                               File x86ClassListFile, String version, File buildDir, File sourceDir) throws Exception {
        try {
            // Reset VmUtils.VM_INSTANCE to allow HeapHelperImpl instantiation
            resetVmInstance();

            Class<?> bootImageBuilderClass = taskClassLoader.loadClass("org.jnode.build.x86.BootImageBuilder");
            Object builder = bootImageBuilderClass.newInstance();

            // Set the Ant Project on the Task
            Project project = new Project();
            Method setProject = bootImageBuilderClass.getMethod("setProject", Project.class);
            setProject.invoke(builder, project);

            // Set destination files
            Method setDestFile = bootImageBuilderClass.getMethod("setDestFile", File.class);
            setDestFile.invoke(builder, new File(bootimageDir, "bootimage.bin"));

            Method setListFile = bootImageBuilderClass.getMethod("setListFile", File.class);
            setListFile.invoke(builder, new File(bootimageDir, "bootimage.lst"));

            Method setDebugFile = bootImageBuilderClass.getMethod("setDebugFile", File.class);
            setDebugFile.invoke(builder, new File(bootimageDir, "bootimage.debug"));

            // Set kernel file
            Method setKernelFile = bootImageBuilderClass.getMethod("setKernelFile", File.class);
            setKernelFile.invoke(builder, nativeObjectFile);

            // Set configuration files
            Method setPluginList = bootImageBuilderClass.getMethod("setPluginList", File.class);
            setPluginList.invoke(builder, pluginListFile);

            Method setSystemPluginList = bootImageBuilderClass.getMethod("setSystemPluginList", File.class);
            setSystemPluginList.invoke(builder, pluginListFile);

            Method setPluginDir = bootImageBuilderClass.getMethod("setPluginDir", File.class);
            setPluginDir.invoke(builder, pluginDir);

            Method setCoreClassListFile = bootImageBuilderClass.getMethod("setCoreClassListFile", File.class);
            setCoreClassListFile.invoke(builder, classListFile);

            Method setArchClassListFile = bootImageBuilderClass.getMethod("setArchClassListFile", File.class);
            setArchClassListFile.invoke(builder, x86ClassListFile);

            // Set architecture
            Method setTargetArch = bootImageBuilderClass.getMethod("setTargetArch", String.class);
            setTargetArch.invoke(builder, "x86");

            Method setBits = bootImageBuilderClass.getMethod("setBits", int.class);
            setBits.invoke(builder, Integer.parseInt(bits));

            // Set required properties
            Method setVersion = bootImageBuilderClass.getMethod("setVersion", String.class);
            setVersion.invoke(builder, version);

            Method setMemMgrPluginId = bootImageBuilderClass.getMethod("setMemMgrPluginId", String.class);
            setMemMgrPluginId.invoke(builder, "org.jnode.vm.memmgr.def");

            // Enable JNasm (pure Java assembler instead of external NASM)
            Method setEnableJNasm = bootImageBuilderClass.getMethod("setEnableJNasm", boolean.class);
            setEnableJNasm.invoke(builder, true);

            // Configure AsmSourceInfo with kernel assembly source files
            Method createNanokernelsources = bootImageBuilderClass.getMethod("createNanokernelsources");
            Object asmSourceInfo = createNanokernelsources.invoke(builder);

            // Set the main source file (jnode.asm)
            // sourceDir passed from Mojo is ${project.basedir}/../core/src/native (absolute)
            File sourceDir_x86 = new File(sourceDir, "x86");
            File jnodeAsmFile = new File(sourceDir_x86, "jnode.asm");
            System.out.println("DEBUG: Looking for jnode.asm at: " + jnodeAsmFile.getAbsolutePath());
            System.out.println("DEBUG: jnode.asm exists: " + jnodeAsmFile.exists());
            Class<?> asmSourceInfoClass = taskClassLoader.loadClass("org.jnode.build.AsmSourceInfo");
            Method setSrcFile = asmSourceInfoClass.getMethod("setSrcFile", File.class);
            setSrcFile.invoke(asmSourceInfo, jnodeAsmFile);

            // Add include directories
            Method createIncludeDir = asmSourceInfoClass.getMethod("createIncludeDir");
            Object includeDirObj = createIncludeDir.invoke(asmSourceInfo);
            Class<?> includeDirClass = taskClassLoader.loadClass("org.jnode.build.AsmSourceInfo$IncludeDir");
            Method setDir = includeDirClass.getMethod("setDir", File.class);
            setDir.invoke(includeDirObj, sourceDir_x86);

            // Execute
            Method execute = bootImageBuilderClass.getMethod("execute");
            execute.invoke(builder);

        } catch (ClassNotFoundException e) {
            throw new Exception("BootImageBuilder not found in classpath. Check builder module compilation.", e);
        }
    }

    /**
     * Create ISO 9660 filesystem using ISOTask.
     */
    public void createISO(File sourceDir, File outputISO) throws Exception {
        try {
            Class<?> isoTaskClass = taskClassLoader.loadClass("de.tu_darmstadt.informatik.rbg.hatlak.iso9660.ISOTask");
            Object isoTask = isoTaskClass.newInstance();

            // Set base directory and output file
            Method setBaseDir = isoTaskClass.getMethod("setBaseDir", File.class);
            setBaseDir.invoke(isoTask, sourceDir);

            Method setDestFile = isoTaskClass.getMethod("setDestFile", File.class);
            setDestFile.invoke(isoTask, outputISO);

            // Set options
            Method setEnableRockRidge = isoTaskClass.getMethod("setEnableRockRidge", boolean.class);
            setEnableRockRidge.invoke(isoTask, true);

            Method setMkisofsCompatibility = isoTaskClass.getMethod("setMkisofsCompatibility", boolean.class);
            setMkisofsCompatibility.invoke(isoTask, true);

            // Boot image configuration
            File bootImageFile = new File(sourceDir, "boot/grub/eltorito.s2");
            if (bootImageFile.exists()) {
                Method setBootImage = isoTaskClass.getMethod("setBootImage", File.class);
                setBootImage.invoke(isoTask, bootImageFile);

                Method setBootImageSectorCount = isoTaskClass.getMethod("setBootImageSectorCount", int.class);
                setBootImageSectorCount.invoke(isoTask, 4);

                Method setGenBootInfoTable = isoTaskClass.getMethod("setGenBootInfoTable", boolean.class);
                setGenBootInfoTable.invoke(isoTask, true);
            }

            // Execute
            Method execute = isoTaskClass.getMethod("execute");
            execute.invoke(isoTask);

        } catch (ClassNotFoundException e) {
            throw new Exception("ISOTask not found in classpath. Check iso9660.jar dependency.", e);
        }
    }

    /**
     * Reset VmUtils.VM_INSTANCE to allow bootimage generation.
     * HeapHelperImpl throws SecurityException if VM is already initialized.
     */
    private void resetVmInstance() throws Exception {
        try {
            Class<?> vmUtilsClass = taskClassLoader.loadClass("org.jnode.vm.facade.VmUtils");
            java.lang.reflect.Field vmInstanceField = vmUtilsClass.getDeclaredField("VM_INSTANCE");
            vmInstanceField.setAccessible(true);
            vmInstanceField.set(null, null);
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            System.out.println("DEBUG: Could not reset VmUtils.VM_INSTANCE: " + e.getMessage());
        }
    }

    /**
     * Close the class loader resources.
     */
    public void close() throws Exception {
        if (taskClassLoader != null) {
            try {
                // close() method was added in Java 7, try to use it via reflection
                java.lang.reflect.Method closeMethod = taskClassLoader.getClass().getMethod("close");
                closeMethod.invoke(taskClassLoader);
            } catch (NoSuchMethodException e) {
                // Java 6 doesn't have close() - ignore
            }
        }
    }
}
