package org.jnode.maven.iso;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mojo for creating bootable JNode ISO 9660 images.
 * Requires a pre-built bootimage.bin from BuildBootImageMojo.
 *
 * Supports:
 * - mkisofs command-line tool (external, if available)
 * - ISOTask from de.tu_darmstadt.iso9660 (native Java, via reflection)
 */
@Mojo(name = "build-iso", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class BuildISOImageMojo extends AbstractMojo {

    @Component
    private MavenProject project;

    @Parameter(property = "jnode.bits", defaultValue = "32", required = true)
    private String bits;

    @Parameter(property = "jnode.variant", defaultValue = "lite", required = true)
    private String variant;

    @Parameter(property = "jnode.buildDir", defaultValue = "${project.basedir}/build", required = true)
    private File buildDir;

    @Parameter(property = "jnode.cdroms.dir", defaultValue = "${project.basedir}/build/cdroms", required = true)
    private File cdromsDir;

    @Parameter(property = "jnode.enableMkisofs", defaultValue = "false")
    private boolean enableMkisofs;

    public void execute() throws MojoExecutionException, MojoFailureException {
        ISOBuildExecutor executor = null;
        try {
            getLog().info("========================================");
            getLog().info("JNode ISO Maven Plugin - ISO Image Build");
            getLog().info("========================================");
            getLog().info("Architecture: " + bits + "-bit x86");
            getLog().info("Variant: " + variant);
            getLog().info("Output: " + cdromsDir.getAbsolutePath());

            // Create CDROM directories
            cdromsDir.mkdirs();

            // Validate that bootimage exists
            File bootimageDir = new File(buildDir, bits + "bits/bootimage");
            File bootimageFile = new File(bootimageDir, "bootimage.bin");

            if (!bootimageFile.exists()) {
                throw new MojoFailureException(
                        "Bootimage not found at: " + bootimageFile.getAbsolutePath() + "\n" +
                        "Run 'mvn jnode-iso:build-bootimage' first to create bootimage"
                );
            }
            getLog().info("Bootimage found: " + bootimageFile.getAbsolutePath());

            // Step 1: Prepare CDROM directory structure
            getLog().info("");
            getLog().info("Step 1: Preparing CDROM directory structure");
            File cdromDir = new File(buildDir, "cdrom-" + variant);
            File bootGrubDir = new File(cdromDir, "boot/grub");
            bootGrubDir.mkdirs();
            File licenseDir = new File(cdromDir, "licenses");
            licenseDir.mkdirs();
            getLog().info("  Created: " + cdromDir.getAbsolutePath());

            // Copy bootimage
            File destBootimage = new File(cdromDir, "jnode" + bits + ".gz");
            File srcBootimage = new File(buildDir, "jnode" + bits + ".gz");
            if (srcBootimage.exists()) {
                copyFile(srcBootimage, destBootimage);
                getLog().info("  Copied kernel: " + destBootimage.getAbsolutePath());
            }

            // Step 2: Create ISO 9660 bootable image
            getLog().info("");
            getLog().info("Step 2: Creating ISO 9660 bootable image");
            String isoName = "jnode-x86" + (bits.equals("64") ? "_64" : "");
            if (!variant.isEmpty() && !variant.equals("full")) {
                isoName += "-" + variant;
            }
            isoName += ".iso";
            File isoFile = new File(cdromsDir, isoName);
            getLog().info("  Output: " + isoFile.getAbsolutePath());
            getLog().info("  Method: " + (enableMkisofs ? "mkisofs (external)" : "ISOTask (Java)"));

            try {
                if (enableMkisofs) {
                    createISOWithMkisofs(cdromDir, isoFile);
                } else {
                    // Prepare executor
                    List<File> taskJars = collectTaskJars();
                    executor = new ISOBuildExecutor(taskJars);
                    executor.createISO(cdromDir, isoFile);
                }
                getLog().info("  Status: SUCCESS");
                getLog().info("  Size: " + formatSize(isoFile.length()));
            } catch (Exception e) {
                getLog().error("  Status: FAILED - " + e.getMessage());
                getLog().debug("  Details: ", e);
                throw new MojoFailureException("Failed to create ISO image: " + e.getMessage(), e);
            }

            getLog().info("");
            getLog().info("========================================");
            getLog().info("ISO image build completed successfully");
            getLog().info("Output: " + isoFile.getAbsolutePath());
            getLog().info("========================================");

        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build ISO image: " + e.getMessage(), e);
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
     * Create ISO using mkisofs command-line tool.
     */
    private void createISOWithMkisofs(File sourceDir, File outputISO) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "mkisofs",
                "-o", outputISO.getAbsolutePath(),
                "-R",
                "-b", "boot/grub/eltorito.s2",
                "-no-emul-boot",
                "-boot-load-size", "4",
                "-boot-info-table",
                sourceDir.getAbsolutePath()
        );
        Process process = pb.start();
        // Consume streams to prevent hanging
        consumeStream(process.getInputStream());
        consumeStream(process.getErrorStream());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("mkisofs failed with exit code: " + exitCode);
        }
    }

    private void consumeStream(final java.io.InputStream stream) {
        new Thread() {
            public void run() {
                try {
                    byte[] buffer = new byte[1024];
                    while (stream.read(buffer) >= 0) {
                    }
                } catch (java.io.IOException e) {
                    // Ignore
                }
            }
        }.start();
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
     * Copy a file.
     */
    private void copyFile(File src, File dst) throws Exception {
        java.io.InputStream in = new java.io.FileInputStream(src);
        java.io.OutputStream out = new java.io.FileOutputStream(dst);
        try {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } finally {
            in.close();
            out.close();
        }
    }

    /**
     * Format file size as human-readable string.
     */
    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
