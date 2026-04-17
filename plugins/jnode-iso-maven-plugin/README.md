# JNode ISO Maven Plugin

Professional Maven plugin for building bootable JNode ISO images. Replaces the Ant-based build-x86.xml with a Maven-first approach.

## Goals

### jnode-iso:build-bootimage
Builds the JNode bootable kernel image for x86 architecture.

**Tasks:**
1. Generate ASM constants from Java classes
2. Compile x86 native assembly code
3. Build bootable Java image with plugins

**Parameters:**
- `jnode.bits` - Architecture (32 or 64, default: 32)
- `jnode.buildDir` - Build output directory
- `jnode.sourceDir` - Native source directory (core/src/native)
- `jnode.pluginDir` - Plugin directory
- `jnode.classlistFile` - Classes to compile natively (core-classes.txt)
- `jnode.x86ClasslistFile` - x86-specific classes (x86-classes.txt)
- `jnode.pluginListFile` - Plugin list XML

### jnode-iso:build-iso
Creates bootable ISO 9660 image from the bootimage.

**Parameters:**
- `jnode.bits` - Architecture (32 or 64, default: 32)
- `jnode.variant` - ISO variant (lite, full, default: lite)
- `jnode.buildDir` - Build directory with bootimage
- `jnode.cdromsDir` - Output directory for ISO files
- `jnode.enableMkisofs` - Use external mkisofs tool (default: false)

## Usage

### Build 32-bit lite ISO
```bash
mvn -pl all jnode-iso:build-bootimage jnode-iso:build-iso \
  -Djnode.bits=32 \
  -Djnode.variant=lite
```

### Build 64-bit full ISO
```bash
mvn -pl all jnode-iso:build-bootimage jnode-iso:build-iso \
  -Djnode.bits=64 \
  -Djnode.variant=full
```

### From all/pom.xml
```xml
<plugin>
  <groupId>org.jnode</groupId>
  <artifactId>jnode-iso-maven-plugin</artifactId>
  <version>0.2.9-dev</version>
  <executions>
    <execution>
      <goals>
        <goal>build-bootimage</goal>
        <goal>build-iso</goal>
      </goals>
      <configuration>
        <bits>32</bits>
        <variant>lite</variant>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## Architecture

The plugin is structured in three layers:

### Layer 1: Mojo Classes
- **BuildBootImageMojo** - Orchestrates bootimage creation
- **BuildISOImageMojo** - Creates ISO from bootimage

### Layer 2: Task Integration
Tasks from jnode-builder module:
- `AsmConstBuilder` - Generates java.inc from Java class metadata
- `Asm` - Compiles x86 assembly to object files
- `BootImageBuilder` - Links objects and Java classes into bootable image
- `ISOTask` - Creates ISO 9660 filesystem

### Layer 3: Maven Integration
- Plugin POM declares all dependencies
- Parameters map to Maven properties
- Lifecycle phases: prepare-package → package → verify

## Implementation Status

### ✅ Completed
- [x] Plugin POM structure
- [x] BuildBootImageMojo skeleton
- [x] BuildISOImageMojo skeleton
- [x] Parameter handling
- [x] Directory creation
- [x] Error handling

### 🔄 In Progress
- [ ] AsmConstBuilder integration
- [ ] Asm task integration
- [ ] BootImageBuilder integration
- [ ] ISOTask integration
- [ ] mkisofs fallback

### 📋 TODO
- [ ] Unit tests
- [ ] Integration tests
- [ ] Documentation site
- [ ] Plugin configuration schema
- [ ] Mojo execution logging

## Integration with Ant Tasks

The plugin calls JNode's existing Ant task classes directly using their Java APIs:

```java
// Example (not yet implemented)
AsmConstBuilder builder = new AsmConstBuilder();
builder.setDestFile(outputFile);
builder.setBits(Integer.parseInt(bits));
builder.addClassURL(...);
builder.execute();
```

This requires understanding each task's Java API, which will be implemented in the next iteration.

## Dependencies

- Maven 3.8+
- Java 8
- JNode core, shell, net, fs, gui modules (compiled)
- GNU Classpath, MMTK, ISO9660, Log4j (local JAR dependencies)

## Testing

```bash
# Test plugin installation
mvn -pl plugins/jnode-iso-maven-plugin clean install

# Test from all module
cd all
mvn jnode-iso:build-bootimage -Djnode.bits=32
mvn jnode-iso:build-iso -Djnode.bits=32 -Djnode.variant=lite
```

## Known Limitations

1. **No Direct ASM Compilation Yet** - Requires C/C++ toolchain
2. **ISOTask Integration Pending** - mkisofs alternative needed
3. **GRUB Extraction Not Automated** - Manual or antrun task required
4. **Linux-Only** - x86 assembly compilation requires Linux/Unix

## Future Enhancements

1. Custom Mojo for each Ant task class
2. Artifact attachment (ISO files as artifacts)
3. Parallel builds (32-bit and 64-bit)
4. Docker integration for cross-platform builds
5. Binary distribution caching

## References

- [Maven Plugin Guide](https://maven.apache.org/plugins/maven-plugin-plugin/)
- [Mojo API Javadoc](https://maven.apache.org/plugin-developers/)
- JNode ISO Building: `all/build-x86.xml`
