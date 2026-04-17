# JNode Maven Build Guide

## Overview

JNode has migrated from pure Ant to a **hybrid Maven + Ant build system**:

- **Maven** (100%): Handles compilation of all 13 modules with automatic dependency management
- **Ant** (temporary): Handles the final ISO image generation step (to be replaced with Maven plugin in future)

This hybrid approach provides:
- ✅ Clean Maven module structure with dependency management
- ✅ IDE integration (IntelliJ, Eclipse, VS Code)
- ✅ Reproducible builds with Maven's dependency resolution
- ✅ Proven Ant ISO generation that works reliably
- ⏳ Future: 100% Maven ISO generation once classloader/JNasm integration is completed

## Quick Start

### Option 1: Automated Build (Recommended)

```bash
# Linux/macOS/Git Bash on Windows
./build-complete.sh

# Windows Command Prompt
build-complete.bat
```

This runs both Maven compilation and Ant ISO generation in one command.

### Option 2: Step-by-Step

**Step 1: Maven Compilation**
```bash
mvn clean install -DskipTests
```

This compiles all 13 modules and installs them to the local Maven repository. Output: ~1 minute

**Step 2: Ant ISO Generation**
```bash
# Set JAVA_HOME if not already set (required for ./build.sh)
export JAVA_HOME="/path/to/jdk1.8.0"  # Linux/macOS
set JAVA_HOME=C:\Path\To\jdk1.8.0     # Windows

# Run Ant for ISO generation
./build.sh cd-x86-lite   # Linux/macOS/Git Bash
build.bat cd-x86-lite    # Windows Command Prompt
```

Output: `all/build/cdroms/jnode-x86-lite.iso` (~103 MB)

## Module Structure

All modules are now Maven POM projects:

| Module | Purpose | Dependencies |
|--------|---------|--------------|
| `builder` | x86 assembler, bootstrapping tools | core via separate build-tools |
| `build-tools` | AsmConstBuilder, BootImageBuilder | builder, core |
| `core` | JVM, memory management, core classes | builder (via build-tools) |
| `shell` | Command-line interface | core, nanoxml, bsh, rhino |
| `net` | Networking (TCP/IP, DNS, SSH) | core, shell, dnsjava, jsch, oncrpc |
| `textui` | Terminal UI framework | core, shell |
| `gui` | AWT, desktop, graphics | core, shell |
| `fs` | File systems, FTP, CIFS, NFS | core, shell, net, edtftpj, jcifs |
| `cli` | Command-line tools | core, shell, net, fs |
| `distr` | Distribution apps, installation | all modules + extra libraries |
| `all` | ISO image assembly | all modules + iso9660, sabre |
| `plugins` | Maven plugins for ISO building | core (via build-tools) |

## Architecture

```
Build Process:
1. Maven compiles: builder → core (depends on builder) → other modules
2. Maven installs: All modules to ~/.m2/repository/org/jnode/
3. Ant builds ISO: Packages compiled classes + native code into bootable ISO

Circular Dependency Resolution:
- builder.pom.xml compiles Ant task definitions
- build-tools.pom.xml (NEW) compiles AsmConstBuilder, BootImageBuilder
- core.pom.xml depends on build-tools (not builder directly)
- All modules depend on core
```

## Compilation Details

### Java Version

All modules compile with **Java 1.8** (Corretto, OpenJDK, or Oracle JDK 1.8)

### Bootclasspath Configuration

- `core` module: bootclasspath = `classlib.jar` (GNU Classpath 0.99-jnode)
- Other modules: bootclasspath = `core/target/classes` (custom JVM)

This ensures compiled code is compatible with JNode's custom JVM, not the host JVM.

### Source Roots

Each module has multiple source directories (e.g., core has 13+):
- `src/core/` — core JVM classes
- `src/classpath/vm/` — VMSpec implementation
- `src/mmtk-vm/` — Memory management
- `src/driver/` — Device drivers
- `src/emu/` — Emulation classes
- etc.

Maven automatically compiles all of them.

## Troubleshooting

### "Cannot find Maven"

Make sure Maven 3.6+ is installed and in your PATH:
```bash
mvn -v
```

### "Cannot find JAVA_HOME"

Set JAVA_HOME before running build scripts:
```bash
# Linux/macOS
export JAVA_HOME=/usr/libexec/java_home -v 1.8

# Windows
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_482
```

### "Compilation error: cannot find symbol java.lang.Object"

This means core module didn't compile first. Try:
```bash
mvn clean install -pl core -am
```

Then rebuild other modules:
```bash
mvn clean install -DskipTests
```

### "ISO generation failed: task or type if"

This happens when ant-contrib isn't available. The Ant build includes:
```xml
<taskdef resource="net/sf/antcontrib/antcontrib.properties" />
```

Ensure ant-contrib.jar is in your Ant classpath (usually automatic if using ./build.sh).

## Maven Profiles

Currently no active profiles. Future profiles planned:
- `iso-x86-lite` — 32-bit lightweight ISO
- `iso-x86_64` — 64-bit ISO
- `iso-full` — Full distribution with sources

## Future Work

### Phase 8: 100% Maven ISO Generation

Goal: Replace Ant ISO generation with a Maven plugin that:
1. Invokes AsmConstBuilder directly (currently working)
2. Invokes x86 assembly compiler via JNasm (currently working)
3. Invokes BootImageBuilder with proper classloader setup (needs work)
4. Invokes ISO task programmatically (needs classloader fix)

The challenge is classloader/reflection-based invocation of Ant tasks. The current Maven plugin `jnode-iso-maven-plugin` has Steps 1-2 working but struggles with Step 3's security restrictions (HeapHelperImpl throws SecurityException if VM is already initialized).

### Workaround for Full Maven

Until 100% Maven is ready, users can:
1. Run `mvn clean install` to compile all modules
2. Run `./build.sh cd-x86-lite` to generate ISO (Ant handles this well)
3. Both succeed and produce identical results to original Ant-only build

## Glossary

- **bootclasspath**: JVM classes used during bootstrap (must match target JVM)
- **classlib.jar**: GNU Classpath 0.99-jnode (provides java.lang.Object, etc.)
- **bootstrap**: Process of loading custom JVM into memory before user code
- **JNasm**: Pure Java x86 assembler (no external NASM/YASM needed)
- **Mojo**: Maven plugin goal class
- **POM**: Project Object Model (Maven configuration file)
- **URLClassLoader**: Java classloader that loads from JAR URLs (used in Maven plugin)

## See Also

- Original Ant build: `all/build.xml`, `build.bat`, `build.sh`
- Maven POM files: `pom.xml` (root), `*/pom.xml` (modules)
- Maven plugin: `plugins/jnode-iso-maven-plugin/`
- Project memory (context): `.claude/projects/*/memory/`
