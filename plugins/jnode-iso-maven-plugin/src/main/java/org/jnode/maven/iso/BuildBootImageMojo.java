package org.jnode.maven.iso;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mojo for building JNode bootable kernel images.
 *
 * Orchestrates the complete x86 ISO building pipeline:
 * 1. Generate ASM constants from Java metadata (AsmConstBuilder)
 * 2. Compile x86 native assembly code (Asm task)
 * 3. Link bootloader and Java classes into bootable image (BootImageBuilder)
 *
 * The actual tasks are executed via reflection using ISOBuildExecutor.
 */
@Mojo(name = "build-bootimage", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class BuildBootImageMojo extends AbstractMojo {

    @Component
    private MavenProject project;

    @Parameter(property = "jnode.bits", defaultValue = "32", required = true)
    private String bits;

    @Parameter(property = "jnode.buildDir", defaultValue = "${project.basedir}/build", required = true)
    private File buildDir;

    @Parameter(property = "jnode.sourceDir", defaultValue = "${project.basedir}/../core/src/native", required = true)
    private File sourceDir;

    @Parameter(property = "jnode.pluginDir", defaultValue = "${project.basedir}/build/plugins", required = true)
    private File pluginDir;

    @Parameter(property = "jnode.classlistFile", defaultValue = "${project.basedir}/../all/conf/core-classes.txt", required = true)
    private File classListFile;

    @Parameter(property = "jnode.x86ClasslistFile", defaultValue = "${project.basedir}/../all/conf/x86-classes.txt", required = true)
    private File x86ClassListFile;

    @Parameter(property = "jnode.pluginListFile", defaultValue = "${project.basedir}/../all/conf/system-plugin-list.xml", required = true)
    private File pluginListFile;

    public void execute() throws MojoExecutionException, MojoFailureException {
        ISOBuildExecutor executor = null;
        try {
            getLog().info("========================================");
            getLog().info("JNode ISO Maven Plugin - Bootimage Build");
            getLog().info("========================================");
            getLog().info("Architecture: " + bits + "-bit x86");
            getLog().info("Build directory: " + buildDir.getAbsolutePath());

            // Create necessary directories
            File bitDir = new File(buildDir, bits + "bits");
            File nativeDir = new File(bitDir, "native");
            File bootimageDir = new File(bitDir, "bootimage");
            File nativeSrcDir = new File(nativeDir, "src");
            File nativeOutputDir = new File(nativeDir, "output");

            nativeSrcDir.mkdirs();
            nativeOutputDir.mkdirs();
            bootimageDir.mkdirs();

            // Prepare classpath with project artifacts
            List<File> taskJars = collectTaskJars();
            executor = new ISOBuildExecutor(taskJars);

            // Step 1: Generate ASM constants
            getLog().info("");
            getLog().info("Step 1: Generating ASM constants for " + bits + "-bit x86");
            getLog().info("  Input: Java class metadata");
            getLog().info("  Output: " + new File(nativeSrcDir, "java.inc").getAbsolutePath());

            try {
                List<File> classURLs = collectClassURLs();
                executor.generateAsmConstants(nativeSrcDir, bits, classURLs);
                getLog().info("  Status: SUCCESS");
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null && e.getCause() != null) {
                    msg = e.getCause().getMessage();
                }
                getLog().warn("  Status: FAILED - " + msg);
                getLog().debug("  Details: ", e);
            }

            // Step 2: Compile assembly
            getLog().info("");
            getLog().info("Step 2: Compiling x86 native assembly");
            getLog().info("  Input: " + sourceDir.getAbsolutePath());
            getLog().info("  Output: " + nativeOutputDir.getAbsolutePath());

            try {
                executor.compileAssembly(
                        new File(sourceDir, "x86"),
                        nativeOutputDir,
                        bits,
                        "JNode " + project.getVersion()
                );
                getLog().info("  Status: SUCCESS");
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null && e.getCause() != null) {
                    msg = e.getCause().getMessage();
                }
                getLog().warn("  Status: FAILED - " + msg);
                getLog().debug("  Details: ", e);
            }

            // Step 3: Build bootable kernel image
            getLog().info("");
            getLog().info("Step 3: Building bootable kernel image");
            getLog().info("  Output: " + new File(bootimageDir, "bootimage.bin").getAbsolutePath());

            try {
                File kernelFile = new File(nativeOutputDir, "jnode.o");
                executor.buildBootImage(
                        bootimageDir,
                        kernelFile,
                        bits,
                        pluginListFile,
                        pluginDir,
                        classListFile,
                        x86ClassListFile,
                        "JNode " + project.getVersion(),
                        buildDir,
                        sourceDir
                );
                getLog().info("  Status: SUCCESS");

                // Gzip the bootimage
                getLog().info("");
                getLog().info("Step 4: Compressing bootimage");
                File bootimageFile = new File(bootimageDir, "bootimage.bin");
                File kernelGz = new File(buildDir, "jnode" + bits + ".gz");
                if (bootimageFile.exists()) {
                    compressFile(bootimageFile, kernelGz);
                    getLog().info("  Output: " + kernelGz.getAbsolutePath());
                    getLog().info("  Status: SUCCESS");
                }
            } catch (Exception e) {
                getLog().warn("  Status: FAILED - " + e.getMessage());
                getLog().debug("  Details: ", e);
            }

            getLog().info("");
            getLog().info("========================================");
            getLog().info("Bootimage build completed");
            getLog().info("========================================");

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build bootimage: " + e.getMessage(), e);
        } finally {
            if (executor != null) {
                try {
                    executor.close();
                } catch (Exception e) {
                    getLog().warn("Failed to close executor: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Collect JARs needed for task execution.
     */
    private List<File> collectTaskJars() {
        List<File> jars = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Set<Artifact> artifacts = project.getArtifacts();
        for (Artifact artifact : artifacts) {
            if (artifact.getFile() != null && artifact.getFile().exists()) {
                jars.add(artifact.getFile());
            }
        }
        return jars;
    }

    /**
     * Collect class URLs for AsmConstBuilder.
     */
    private List<File> collectClassURLs() {
        List<File> urls = new ArrayList<>();
        File localRepo = new File(System.getProperty("user.home"), ".m2/repository");

        // Add classpath.jar (GNU Classpath) - required for java.lang.Object, etc.
        File classpathJar = new File(localRepo, "gnu/classpath/classpath/0.99-jnode/classpath-0.99-jnode.jar");
        if (classpathJar.exists()) {
            urls.add(classpathJar);
            getLog().info("Added classpath.jar: " + classpathJar.getAbsolutePath());
        } else {
            getLog().warn("Classpath.jar not found: " + classpathJar.getAbsolutePath());
        }

        // Add mmtk.jar
        File mmtkJar = new File(localRepo, "org/mmtk/mmtk/0.99-jnode/mmtk-0.99-jnode.jar");
        if (mmtkJar.exists()) {
            urls.add(mmtkJar);
            getLog().info("Added mmtk.jar: " + mmtkJar.getAbsolutePath());
        }

        // Use the same JARs that were collected for task execution
        // This includes jnode-core and all other compiled modules
        urls.addAll(collectTaskJars());
        return urls;
    }

    /**
     * Compress a file using GZIP.
     */
    private void compressFile(File input, File output) throws Exception {
        java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(
                new java.io.FileOutputStream(output));
        java.io.FileInputStream fis = new java.io.FileInputStream(input);
        byte[] buffer = new byte[1024];
        int len;
        try {
            while ((len = fis.read(buffer)) > 0) {
                gzos.write(buffer, 0, len);
            }
        } finally {
            gzos.close();
            fis.close();
        }
    }
}
