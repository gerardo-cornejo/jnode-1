# JNode - Java Operating System

JNode is a free operating system written completely in Java. It runs on bare x86 hardware and provides a full Java runtime environment.

**Current Status**: ✅ Maven-based build system (Hybrid Phase)

---

## Quick Start

### Prerequisites

- **Java 8+** (Corretto, OpenJDK, or Oracle JDK)
- **Maven 3.6+**
- **Git**

### Build the Complete System

```powershell
# Windows (PowerShell)
.\build-complete.ps1

# Linux/macOS/Git Bash
./build-complete.sh
```

**Result**: 
- Maven compilation: ~70 seconds
- Ant ISO generation: ~7 seconds  
- **Output**: `all/build/cdroms/jnode-x86-lite.iso` (~103 MB)

---

## Build Architecture

### Hybrid Maven + Ant System

JNode now uses a **hybrid build approach**:

```
┌─────────────────────────────────┐
│  build-complete.ps1/sh          │
└──────────────┬──────────────────┘
               │
        ┌──────┴──────┐
        │             │
        ▼             ▼
   Maven (70s)    Ant (7s)
   Compile all    Build ISO
   13 modules     from binaries
        │             │
        └──────┬──────┘
               ▼
        jnode-x86-lite.iso
```

**Why Hybrid?**
- ✅ Maven handles compilation (automatic dependency management, IDE integration)
- ✅ Ant handles ISO generation (proven, reliable, fast when using pre-compiled binaries)
- ✅ No redundant compilation (Ant detects existing binaries and skips recompilation)

---

## Project Structure

### Maven Modules (13 total)

| Module | Purpose | Key Dependencies |
|--------|---------|------------------|
| `builder` | x86 assembler, Ant tasks, JavaCC parsers | - |
| `build-tools` | AsmConstBuilder, BootImageBuilder | builder, core |
| `core` | JVM, memory management, core classes | build-tools |
| `shell` | Command-line interface | core, nanoxml, bsh, rhino |
| `net` | Networking (TCP/IP, DNS, SSH, FTP) | core, shell, dnsjava, jsch, oncrpc |
| `textui` | Terminal UI framework | core, shell |
| `gui` | AWT, desktop, graphics, fonts | core, shell |
| `fs` | File systems (FAT, ext2, CIFS, NFS, FTP) | core, shell, net, edtftpj, jcifs |
| `cli` | Command-line tools and utilities | core, shell, net, fs |
| `distr` | Distribution apps, installation tools | all modules |
| `all` | Distribution build coordinator | all modules |
| `plugins` | Maven plugins (ISO generation) | core |
| `jnode-iso-maven-plugin` | Custom Maven plugin for bootimage | core |

### Directory Layout

```
jnode/
├── pom.xml                          # Root Maven POM
├── build-complete.ps1               # Automated build script (PowerShell)
├── build.bat / build.sh             # Original Ant build scripts
│
├── builder/                         # Ant task definitions, JavaCC parsers
│   ├── pom.xml
│   ├── src/builder/                 # Ant task source code
│   └── lib/                         # JavaCC, BCEL, etc.
│
├── build-tools/                     # NEW: Build tool classes
│   ├── pom.xml
│   └── (compiles AsmConstBuilder, BootImageBuilder)
│
├── core/                            # JVM core (13+ source roots)
│   ├── pom.xml
│   ├── src/core/                    # Core JVM classes
│   ├── src/classpath/vm/            # VMSpec implementation
│   ├── src/mmtk-vm/                 # Memory management
│   ├── src/native/                  # x86 assembly source (*.asm)
│   └── ... (8 more source directories)
│
├── shell/                           # Command shell
├── net/                             # Networking
├── gui/                             # Graphics/AWT
├── fs/                              # File systems
├── cli/                             # CLI commands
├── distr/                           # Distribution
├── textui/                          # Text UI
│
├── all/                             # Distribution assembly
│   ├── pom.xml
│   ├── build.xml                    # Original Ant build (ISO generation)
│   ├── conf/                        # Configuration (class lists, plugin list)
│   └── build/                       # Build output (generated after compile)
│       ├── x86/                     # 32-bit build artifacts
│       ├── cdroms/                  # ISO output directory
│       └── plugins/                 # Compiled plugins
│
└── plugins/                         # Maven plugins
    └── jnode-iso-maven-plugin/      # Custom plugin for bootimage generation
```

---

## Building

### Step 1: Maven Compilation

```bash
mvn clean install -DskipTests
```

This compiles all 13 modules with:
- ✅ Automatic dependency resolution from Maven Central + local repository
- ✅ 13 source roots in `core` module (custom JVM classes)
- ✅ Bootclasspath configuration (uses `classlib.jar` for core, `core/target/classes` for others)
- ✅ JavaCC parser generation (builder module)
- ✅ Template-based code generation (VmSystemSettings.java)
- ✅ All compiled classes available in `*/target/classes/`

**Time**: ~70 seconds

### Step 2: Ant ISO Generation

```bash
# Automatic (part of build-complete.ps1/sh)
./build.sh cd-x86-lite

# Or manually:
# Windows
build.bat cd-x86-lite

# Linux/macOS
./build.sh cd-x86-lite
```

This packages Maven's output into a bootable ISO:
- ✅ Detects pre-compiled binaries (no recompilation)
- ✅ Generates bootable x86 kernel image
- ✅ Creates ISO 9660 filesystem with Rock Ridge extensions
- ✅ Outputs: `all/build/cdroms/jnode-x86-lite.iso`

**Time**: ~7 seconds

**Total Time**: ~80 seconds (comparable to original Ant-only build)

---

## Build Configuration

### Java Version

All modules compile with **Java 1.8** (any variant: Corretto, OpenJDK, Oracle)

Set via:
```bash
# Windows
set JAVA_HOME=C:\Users\[user]\.jdks\corretto-1.8.0_482

# Linux/macOS
export JAVA_HOME=/path/to/jdk1.8.0
```

### Bootclasspath

To ensure compatibility with JNode's custom JVM:

- **core module**: Uses `classlib.jar` (GNU Classpath 0.99-jnode)
- **other modules**: Use `core/target/classes` (custom JVM classes)

This is configured in each module's `pom.xml`:
```xml
<compilerArgs>
    <arg>-bootclasspath</arg>
    <arg>${jnode.bootclasspath}</arg>
</compilerArgs>
```

---

## Compilation Details

### Multiple Source Roots

The `core` module has 13 source directories (typical):
- `src/core/` — JVM core classes
- `src/classpath/vm/` — VMSpec implementation
- `src/classpath/ext/` — Classpath extensions
- `src/openjdk/vm/` — OpenJDK integration
- `src/mmtk-vm/` — Memory management
- `src/driver/` — Device drivers
- `src/emu/` — Emulation classes
- `src/endorsed/nanoxml/` — NanoXML parser
- `src/vmmagic/` — VM magic classes
- `src/native/` — x86 assembly and native code
- `src/testframework/` — Test framework
- `src/classlib/` — Custom class library
- And more...

Maven automatically compiles all of them via `build-helper-maven-plugin`.

### Circular Dependency Resolution

**Problem**: `builder` depends on `core`, but `core` depends on builder's Ant tasks.

**Solution**: New `build-tools` module:
- Compiles only: AsmConstBuilder, BootImageBuilder, BuildObjectResolver, X86DualAssemblerFactory
- Located between builder and core in dependency chain
- `core` depends on `build-tools` (not `builder`)
- Breaks the circular dependency cleanly

---

## IDE Integration

The Maven structure provides full IDE support:

### IntelliJ IDEA
```
File → Open → [jnode root]
IDE automatically detects Maven structure and configures modules
```

### Eclipse
```
File → Import → Existing Maven Projects → [jnode root]
```

### VS Code
```
Install "Extension Pack for Java"
Open workspace root with Maven projects support enabled
```

---

## Dependencies

### External JARs (Local Repository)

Some JARs are custom or not in Maven Central. They're installed in your local repository (~/.m2/repository/):

- `classlib.jar` (GNU Classpath 0.99-jnode)
- `mmtk.jar` (Memory management toolkit)
- `iso9660.jar` (ISO image generation)
- `sabre.jar` (ISO/UDF utilities)
- `javacc.jar` (Parser generator)
- `nanoxml.jar`, `bsh.jar`, `rhino.jar` (scripting)
- And others...

**Note**: These are already installed in the standard Maven repository locations.

---

## Output Artifacts

After `build-complete.ps1`:

### Compiled Classes (Maven)
```
*/target/classes/              # Compiled Java classes per module
*/target/jnode-*.jar           # Compiled JAR files
```

### Bootable ISO (Ant)
```
all/build/cdroms/jnode-x86-lite.iso    # Main bootable ISO (~103 MB)
all/build/x86/bootimage.bin             # Raw kernel image
all/build/x86/jnode32.gz                # Compressed kernel
```

---

## Troubleshooting

### "Cannot find Maven"
```
mvn -v
```
If not found, add Maven to PATH or install from https://maven.apache.org/

### "Cannot find Java"
```
java -version
```
If not found, set JAVA_HOME:
```bash
# Windows
set JAVA_HOME=C:\Users\[user]\.jdks\corretto-1.8.0_482

# Linux/macOS
export JAVA_HOME=/Library/Java/JavaVirtualMachines/*/Contents/Home
```

### "Build timeout or out of memory"
Maven needs more heap:
```bash
set MAVEN_OPTS=-Xmx2g -Xms512m  # Windows
export MAVEN_OPTS=-Xmx2g -Xms512m  # Linux/macOS
```

### "ISO not generated"
Check that `build.sh` or `build.bat` exists in project root:
```bash
ls -la build.sh
```

### "Compilation errors: cannot find symbol java.lang.Object"
The `core` module didn't compile first. Try:
```bash
mvn clean install -pl core -am -DskipTests
mvn clean install -DskipTests
```

---

## Hybrid Build Rationale

### Why Not 100% Maven?

Full Maven-based ISO generation requires:
1. Solving complex classloader integration (URLClassLoader with reflection)
2. Handling JNasm assembly compilation in-process
3. Managing HeapHelperImpl security restrictions
4. Full Maven plugin development

These are non-trivial and deferred for Phase 8.

### Why Hybrid is Better Than Pure Ant

| Aspect | Pure Ant | Hybrid Maven+Ant |
|--------|----------|-----------------|
| Dependency Management | Manual | Automatic ✅ |
| IDE Integration | Minimal | Full ✅ |
| Reproducible Builds | Moderate | High ✅ |
| CI/CD Friendly | No | Yes ✅ |
| Performance | ~80s | ~80s |

---

## Future Improvements

### Phase 8: 100% Maven ISO Generation

When conditions allow, transition to:
```bash
mvn clean install -P iso-x86-lite
```

This requires:
- Completing Maven plugin classloader integration
- Solving JNasm in-process assembly
- Custom URLClassLoader for Ant task invocation

Current status: Steps 1-2 working, Step 3 deferred.

---

## Contributing

1. **Compile**: `mvn clean install -DskipTests`
2. **Test**: Modify code and recompile
3. **Build ISO**: `./build-complete.ps1` (or manual steps)
4. **Boot**: Use QEMU or physical hardware

---

## Resources

- **Build Documentation**: [MAVEN_BUILD.md](MAVEN_BUILD.md)
- **Migration Notes**: [MAVEN_MIGRATION.md](MAVEN_MIGRATION.md)
- **Original README**: [README.md](README.md)
- **Official Site**: http://www.jnode.org/
- **Project History**: See `README.md` for original documentation

---

## License & Credits

JNode is an open-source project. See LICENSE file for details.

**Recent Changes**:
- ✅ Complete Maven migration (13 modules)
- ✅ Hybrid Maven + Ant build system
- ✅ Automated build scripts (PowerShell/Bash)
- ✅ Cleaned up IDE configuration files
- ✅ Removed obsolete Ant/Eclipse/Travis artifacts
- ✅ Full documentation updates

---

**Status**: Ready for production builds. ISO generation confirmed working with ~103 MB output.

Last Updated: 2026-04-17
