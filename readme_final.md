# JNode: lectura tecnica profunda del repositorio

## Proposito de este documento

Este archivo concentra el conocimiento mas importante que se puede extraer del repositorio actual de `jnode/` sin depender de memoria externa. No es solo un README de bienvenida: es una lectura tecnica del proyecto, de su arquitectura, de su build, de sus subsistemas y del estado observable del arbol de trabajo.

La idea central es esta:

- JNode no es una aplicacion Java grande.
- JNode es un sistema operativo experimental construido alrededor de una VM Java propia.
- El repositorio contiene tanto el kernel y la VM como la libreria de clases, drivers, shell, GUI, stack de red, sistemas de archivos y el toolchain que empaqueta todo en una imagen arrancable x86.

En otras palabras: aqui conviven un compilador/assembler de arranque, un runtime Java de bajo nivel, un sistema de plugins, un gestor de drivers, una VFS, una pila de red, una shell y herramientas de distribucion.

## Resumen ejecutivo

### Que es JNode

JNode es un sistema operativo escrito predominantemente en Java, pensado para correr directamente sobre hardware x86 mediante su propia infraestructura de arranque y su propia maquina virtual, en vez de ejecutarse sobre una JVM hospedada por otro sistema operativo.

### Como esta organizado

El repositorio esta dividido en subproyectos relativamente limpios:

- `core/`: VM, boot, kernel runtime, servicios base, plugin framework, naming, seguridad, drivers base.
- `builder/`: toolchain de build, empaquetado, tareas Ant, JNAsm, constructor de boot images.
- `fs/`: VFS, servicios de archivos, drivers de bloque, sistemas de archivos, particiones.
- `net/`: red, IPv4, NFS, protocolos y drivers de red.
- `shell/`: shell principal, interpretes, sintaxis, aliases, help, harness de pruebas.
- `cli/`: comandos de usuario agrupados por categoria.
- `gui/`: AWT parcial, toolkit grafico, framebuffer, video, input, desktop.
- `textui/`: toolkit estilo texto con `charva` y `charvax`.
- `distr/`: aplicaciones, instalacion, utilidades de distribucion.
- `all/`: ensamblado global, plugins finales, `initjars`, imagenes x86, ISO y tareas de calidad.

### Cual es la idea arquitectonica dominante

JNode esta fuertemente orientado a plugins. Incluso los servicios centrales del sistema se empaquetan y arrancan como plugins. El boot image lanza el runtime, el runtime carga un `initjar`, ese `initjar` contiene plugins, y los plugins levantan servicios como consola, VFS, red, GUI, shell y mas.

### Que hace especial a este repositorio

No se limita a codigo Java normal. Tambien contiene:

- codigo ensamblador x86 en `core/src/native/x86/`
- integracion con GNU Classpath, OpenJDK e IcedTea
- soporte de `vmmagic` y MMTk
- tareas Ant personalizadas para compilar, descriptorizar, empaquetar plugins y construir boot images
- imagenes de arranque x86, GRUB, ISO, netboot y rastros de targets legacy como JOP y NT boot loader

## Radiografia del arbol actual

### Modulos y volumen aproximado de codigo Java

Conteo observado en el repositorio actual:

| Modulo | Java files |
| --- | ---: |
| `core` | 1428 |
| `fs` | 551 |
| `gui` | 318 |
| `shell` | 255 |
| `net` | 245 |
| `distr` | 161 |
| `textui` | 129 |
| `builder` | 111 |
| `cli` | 101 |
| **Total** | **3299** |

### Estructura interna relevante de `core/src`

Conteo observado por subarbol:

| Subarbol | Java files | Lectura practica |
| --- | ---: | --- |
| `core/src/core` | 581 | boot, VM, naming, plugins, runtime, system services |
| `core/src/driver` | 263 | buses, consola, input, sistema, video, serial |
| `core/src/test` | 174 | pruebas del runtime base |
| `core/src/openjdk` | 169 | adaptacion e integracion con OpenJDK |
| `core/src/classpath` | 155 | GNU Classpath y extensiones |
| `core/src/mmtk-vm` | 33 | integracion con memory manager toolkit |
| `core/src/vmmagic` | 21 | soporte `org.vmmagic.*` |
| `core/src/classlib` | 18 | piezas de libreria de clases |

### Descriptores y artefactos de plugins

Observaciones del arbol:

- `core/descriptors`: 88 archivos descriptor
- `fs/descriptors`: 48
- `gui/descriptors`: 28
- `net/descriptors`: 25
- `distr/descriptors`: 17
- `shell/descriptors`: 14
- `cli/descriptors`: 9
- `textui/descriptors`: 1

Despues del build actual ya existen artefactos generados:

- `all/build/descriptors/`: 234 archivos
- `all/build/plugins/`: 230 JARs de plugins
- `all/build/initjars/`: 4 bundles (`default.jgz`, `full.jgz`, `shell.jgz`, `tests.jgz`)
- `all/build/cdroms/jnode-x86.iso`: imagen ISO construida
- `all/build/x86/jnode32.gz`: kernel/image comprimida x86

Esto sugiere que el workspace no solo contiene fuente: tambien conserva una corrida de build ya realizada.

## Mapa del repositorio

### Directorios de primer nivel

| Ruta | Rol principal |
| --- | --- |
| `all/` | build orquestador, empaquetado global, plugin lists, ISO, x86 |
| `builder/` | tareas Ant propias, generacion de boot image, JNAsm, configure |
| `cli/` | comandos de usuario agrupados por categoria |
| `core/` | VM, boot, runtime, plugin framework, servicios base, drivers nucleares |
| `distr/` | aplicaciones distribuidas, instalacion, tooling de sistema |
| `docs/` | documentacion historica, presentaciones, specs y diagramas |
| `fs/` | VFS, sistemas de archivos, dispositivos de bloque, particiones |
| `gui/` | AWT, framebuffer, video, desktop, fuentes, input grafico |
| `licenses/` | licencias del proyecto |
| `local/` | overrides locales, en este caso plugin-lists personalizados |
| `net/` | red, IPv4, NFS, protocolos, drivers de NIC |
| `shell/` | shell principal, interpretes, sintaxis, ayuda, pruebas black-box |
| `sound/` | placeholder; hoy no tiene `src/` util |
| `textui/` | toolkit estilo texto con `charva` y `charvax` |
| `netbeans/`, `.idea/`, `.project`, `.classpath` | huellas de soporte multi-IDE y legado |

### Dependencias practicas entre modulos

Leyendo los `build.xml`, la dependencia conceptual queda asi:

- `core` es la base de todo.
- `shell` depende de `core`.
- `net` depende de `core` y `shell`.
- `fs` depende de `core`, `shell` y `net`.
- `gui` depende de `core` y `shell`.
- `textui` depende de `core` y `shell`.
- `builder` depende de `core` y `fs`.
- `distr` depende de `core`, `gui`, `textui`, `shell` y `fs`.
- `cli` depende de `core`, `fs`, `shell` y `net`.
- `all` orquesta y empaqueta todos los anteriores.

Eso revela algo importante: la shell no es un accesorio tardio. Es una pieza de primer nivel en la composicion del sistema.

## Como arranca el sistema

### Vista general del boot

El camino de arranque, simplificado, es este:

1. Se compila el codigo nativo x86.
2. Se construye una boot image que mezcla kernel nativo, clases Java y metadata.
3. Se empaquetan los plugins en `initjars`.
4. El cargador arranca el kernel/boot image.
5. La primera entrada Java es `org.jnode.boot.Main.vmMain()`.
6. `VmSystem.initialize()` levanta naming, logging, heap, procesadores, classloader y runtime basico.
7. Se intenta mostrar el boot splash temprano.
8. `InitJarProcessor` abre el `initjar`, encuentra plugins y los carga.
9. `DefaultPluginManager` arranca plugins de sistema y plugins auto-start.
10. El `Main-Class` del manifest del plugin list apunta a `org.jnode.shell.CommandShell`.
11. La shell se vuelve la interfaz principal de usuario.

### Punto de entrada Java

Archivo clave:

- `core/src/core/org/jnode/boot/Main.java`

Lo importante de esa clase:

- `vmMain()` es la primera entrada Java despues del arranque assembler.
- llama a `VmSystem.initialize()`.
- arranca el splash temprano con `BootSplashRunner.start()`.
- carga plugins desde el `initjar` mediante `InitJarProcessor`.
- crea `DefaultPluginManager`.
- arranca plugins de sistema.
- lee el `Main-Class` del manifest del plugin-list.
- invoca `main(String[])` sobre esa clase.

Ese `Main-Class` se define en:

- `all/conf/default-plugin-list.xml`
- `all/conf/full-plugin-list.xml`
- y tambien puede redefinirse mediante listas custom en `local/plugin-lists/`

En la configuracion observada, el `Main-Class` es:

- `org.jnode.shell.CommandShell`

con argumento:

- `boot`

### Inicializacion del runtime base

Archivo clave:

- `core/src/core/org/jnode/vm/VmSystem.java`

La inicializacion hace, entre otras cosas:

- crea el espacio de nombres global con `InitialNaming`
- inicializa el boot log
- levanta el `ResourceManager`
- inicializa el `VmSystemClassLoader`
- levanta `VmThread`
- arranca el heap manager
- inicializa procesadores
- calibra timing y procesador
- arranca `LoadCompileService`
- carga el `initJar` desde memoria nativa
- levanta `log4j`
- aplica adaptaciones OpenJDK especificas

Esto deja claro que JNode no arranca desde Java normal sino desde una fase de sistema donde Java ya esta embebido dentro del propio OS.

### El `initjar`

Archivo clave:

- `core/src/core/org/jnode/boot/InitJarProcessor.java`

Funcion:

- recibe un `MemoryResource` con el `initjar`
- lo abre como jar en memoria
- recorre entradas `.jar`
- para cada una intenta cargar un plugin descriptor dentro del `PluginRegistry`
- expone tambien el `Main-Class` y los argumentos definidos en el manifest

En otras palabras: el `initjar` no es un zip casual. Es el bundle de bootstrap funcional del sistema.

### Splash de arranque

Se observa una implementacion de splash temprano y de overlay framebuffer:

- `core/src/core/org/jnode/boot/BootSplashRunner.java`
- `core/src/core/org/jnode/naming/BootSplashControl.java`
- `gui/src/driver/org/jnode/driver/textscreen/fb/FbTextScreenManager.java`
- `core/src/driver/org/jnode/driver/console/textscreen/TextScreenConsolePlugin.java`
- `gui/src/driver/org/jnode/driver/video/vesa/BootSplashGif.java`

Lectura tecnica:

- el splash temprano puede limpiar el framebuffer VBE y ocultar el texto VGA
- `BootSplashControl` se publica en `InitialNaming`
- `TextScreenConsolePlugin` detecta si el splash esta activo y puede silenciar `System.out` y `System.err`
- `FbTextScreenManager` mantiene una animacion overlay hasta que AWT toma el control o vence un timeout

Esto parece ser parte de una evolucion reciente del arbol, porque cruza consola de texto, framebuffer y desktop.

## Sistema de plugins

### Idea central

JNode usa plugins como mecanismo de composicion del sistema. El plugin framework no esta encima del sistema operativo: forma parte del sistema operativo.

Archivos importantes:

- `core/src/core/org/jnode/plugin/PluginManager.java`
- `core/src/core/org/jnode/plugin/model/PluginRegistryModel.java`
- `core/src/core/org/jnode/plugin/model/PluginDescriptorModel.java`
- `core/src/core/org/jnode/plugin/model/PluginJar.java`

### Que papel juegan los descriptores

Los descriptores XML de cada modulo definen:

- identidad del plugin
- version
- dependencias
- paquetes exportados
- extension points
- extensiones
- clases plugin
- mappers de servicios o memory manager cuando aplica

El build copia y filtra estos descriptores a `all/build/descriptors/`, y luego usa `PluginTask` para empaquetar JARs finales en `all/build/plugins/`.

### System plugins versus default plugins

`all/conf/system-plugin-list.xml` define plugins que siempre van al `initjar`, entre ellos:

- `org.jnode.plugin`
- `org.jnode.plugin.impl`
- `org.jnode.runtime`
- `org.jnode.vm`
- `org.jnode.vm.core`
- `org.jnode.util`
- `org.jnode.work`
- `rt`
- `rt.vm`

`all/conf/default-plugin-list.xml` agrega el resto del mundo de usuario y sistema:

- consola
- AWT
- shell
- drivers
- red
- VFS
- fs concretos
- comandos
- desktop
- debug

`all/conf/full-plugin-list.xml` incluye el `default` y suma componentes mas pesados, por ejemplo:

- `com.sun.tools.javac`
- `com.sun.tools.jdi`
- `org.apache.tools.ant`
- `charva`
- `org.jnode.install`
- `org.jnode.apps.httpd`
- `jetty`
- `org.jnode.apps.jpartition`

### Override local importante

El workspace actual tiene:

- `jnode.properties` con `custom.plugin-list.dir = ${root.dir}/local/plugin-lists/`
- `local/plugin-lists/default-plugin-list.xml`

Ese plugin-list local deshabilita explicitamente:

- `org.jnode.partitions`
- `org.jnode.partitions.ibm`
- `org.jnode.partitions.command`

Motivo indicado en el propio archivo:

- evitar deadlocks

Esto es un detalle muy importante del estado local. Cualquier comportamiento observado en builds o booteos de este workspace puede estar condicionado por ese override.

## La VM y el kernel Java

### Donde esta el corazon tecnico

Si alguien quiere entender el proyecto en profundidad, el trio minimo es:

- `core/src/core/org/jnode/boot/Main.java`
- `core/src/core/org/jnode/vm/VmSystem.java`
- `builder/src/builder/org/jnode/build/x86/BootImageBuilder.java`

### Componentes nucleares visibles

Dentro de `core/src/core/org/jnode/` destacan:

| Paquete | Aproximacion |
| --- | --- |
| `vm` | ejecucion, arquitectura, clases VM, scheduler, memoria, x86 |
| `plugin` | framework de plugins |
| `naming` | registro global de servicios |
| `system` | recursos y servicios basicos |
| `boot` | secuencia de arranque Java |
| `bootlog` | logging temprano |
| `work` | cola y trabajos asincronos |
| `permission`, `security` | permisos y seguridad |

### Scheduler, hilos y CPU

Clases relevantes localizadas:

- `org.jnode.vm.scheduler.VmScheduler`
- `org.jnode.vm.scheduler.VmThread`
- `org.jnode.vm.scheduler.VmProcessor`
- `org.jnode.vm.BaseVmArchitecture`
- `org.jnode.vm.x86.VmX86Architecture32`
- `org.jnode.vm.x86.VmX86Architecture64`
- `org.jnode.vm.x86.VmX86Processor32`
- `org.jnode.vm.x86.VmX86Processor64`

Lectura conceptual:

- hay una abstraccion de arquitectura VM
- hay representacion propia de procesadores e hilos VM
- hay scheduler explicito del sistema
- existe soporte para x86 de 32 y 64 bits

### Naming como service locator del sistema

`InitialNaming` aparece por todo el codigo:

- plugin manager
- console manager
- filesystem service
- network layer manager
- BootSplashControl
- font manager

Esto cumple el rol de un registro global de servicios del sistema. Es casi un bus de descubrimiento y lookup interno de JNode.

### Resource management

`VmSystem.initialize()` inicializa un `ResourceManager`. Esto es coherente con un OS y VM que necesitan administrar memoria y recursos fisicos de forma explicita.

### Libreria de clases: mezcla de fuentes

`core/build.xml` muestra que el build del core mezcla varias fuentes:

- `classpath/*`
- `openjdk/*`
- `icedtea/*`
- `classlib/*`
- `vmmagic/*`
- `mmtk-vm/*`
- `core/*`
- `driver/*`

Esto revela una realidad importante:

- JNode no depende de una libreria de clases ajena como caja negra.
- El repositorio incorpora y adapta piezas de Classpath, OpenJDK e IcedTea para su propio runtime.

### Soporte `vmmagic` y MMTk

Existen subarboles dedicados a:

- `core/src/vmmagic`
- `core/src/mmtk-vm`

y descriptores para planes de memoria:

- `org.jnode.vm.memmgr.def`
- `org.jnode.vm.memmgr.mmtk.ms`
- `org.jnode.vm.memmgr.mmtk.nogc`
- `org.jnode.vm.memmgr.mmtk.genrc`

El build deja ver que:

- el memory manager se elige con `jnode.memmgr.plugin.id`
- por defecto se usa `org.jnode.vm.memmgr.def`
- hay alternativas MMTk que se compilan como clases-plan especificas

### Soporte x86 y ensamblador nativo

Hay codigo nativo en:

- `core/src/native/x86/`

Archivos representativos:

- `kernel.asm`
- `jnode.asm`
- `vm-invoke.asm`
- `vm-jumptable.asm`
- `syscall.asm`
- `ints.asm`
- `mm32.asm`
- `mm64.asm`
- `ap-boot.asm`
- `unsafe.asm`

Y clases Java x86 como:

- `X86CpuID`
- `PIC8259A`
- `LocalAPIC`
- `IOAPIC`
- `VmX86Thread`
- `UnsafeX86`

Eso deja clara la frontera real del proyecto: Java domina, pero el arranque, la transicion a la VM y algunas primitivas de hardware son nativas.

## Toolchain de build

### No es Maven ni Gradle

Todo el proyecto pivota sobre Ant.

Puntos de entrada:

- `build.sh`
- `build.bat`
- `all/build.xml`

`build.sh` y `build.bat` lanzan `core/lib/ant-launcher.jar` apuntando a `all/build.xml`.

### Requisitos observables del build

Segun `all/build.xml`:

- Java runtime soportado: 1.6, 1.7 o 1.8
- `java.source=1.6`
- `java.target=1.6`
- `java.encoding=US-ASCII`

En Windows, el build falla si la ruta del proyecto tiene espacios. Esto esta explicitamente validado en `all/build.xml`.

### `all/lib/jnode.xml`: la macro biblioteca del build

Archivo clave:

- `all/lib/jnode.xml`

Define:

- `jnode.compile`
- `jnode.compile.test`
- `jnode.javadoc`
- `jnode.copy-descriptors`
- `jnode.clean`
- `jnode.antall`

`jnode.antall` compila en este orden:

1. `core`
2. `shell`
3. `net`
4. `fs`
5. `builder`
6. `gui`
7. `textui`
8. `distr`
9. `cli`

Ese orden es una radiografia directa de la composicion del sistema.

### Pipeline de `all/build.xml`

Secuencia clave observada:

1. `prepare`
2. `assemble-projects`
3. `assemble-plugins`
4. `assemble-default-initjars`
5. `assemble-custom-initjars` si hay overrides
6. `assemble`
7. targets de imagen `x86`, `cd-x86`, `cd-x86-lite`, etc.

Mas en detalle:

- `assemble-projects`: dispara `pre-compile` en `builder` y luego `assemble` de todos los subproyectos
- `assemble-plugins`: empaqueta plugins a partir de descriptores y alias de librerias
- `assemble-default-initjars`: construye bundles `.jgz` usando `system-plugin-list.xml` y los `*plugin-list.xml`
- `assemble-custom-initjars`: construye `initjars` desde `local/plugin-lists/` u otra ruta custom

### Build del `core`

`core/build.xml` hace cosas mas ricas que un compile comun:

- genera `VmSystemSettings.java` desde templates
- genera settings de seguridad
- expande tests primitivos
- compila clases test
- compila clases-plan para MMTk
- copia recursos no Java

Esto es evidencia de que `core` combina runtime, toolchain y generacion de codigo.

### Build del `builder`

`builder/build.xml`:

- compila `TemplateTask` primero
- usa JavaCC para JNAsm
- ejecuta `native2ascii` cuando hace falta
- construye `jnode-builder.jar`
- construye `jnode-configure.jar`
- contiene regression tests para ensamblador

### Generacion de la imagen x86

Archivo clave:

- `all/build-x86.xml`

Flujo resumido:

1. prepara directorios x86
2. descomprime o usa GRUB
3. genera constantes assembler desde clases Java con `genconst`
4. ensambla codigo nativo x86
5. corre `org.jnode.build.x86.BootImageBuilder`
6. produce `bootimage.bin`, `bootimage.lst`, `bootimage.debug`, `bootimage.txt`
7. comprime la imagen a `jnode32.gz` o variante equivalente
8. empaqueta bootdisk, netboot o CDROM cuando corresponde

Este punto es crucial: el build no solo hace jars. Hace una imagen arrancable donde clases Java, metadata VM y kernel nativo quedan integrados.

### `BootImageBuilder`

Archivo clave:

- `builder/src/builder/org/jnode/build/x86/BootImageBuilder.java`

Lo que se observa:

- soporta x86 de 32 y 64 bits
- crea una arquitectura VM concreta
- instancia `VmScheduler`
- prepara `VmProcessor`
- enlaza codigo ELF del kernel
- mapea salto a `Main.vmMain()`
- trabaja con layouts de objetos, estaticos compartidos y clases bootables

Este archivo es uno de los puentes entre el universo Java y la imagen booteable final.

### Artefactos finales del ensamblado

Carpetas relevantes:

- `all/build/descriptors/`
- `all/build/plugins/`
- `all/build/initjars/`
- `all/build/x86/`
- `all/build/cdroms/`

Artefactos notables observados:

- `all/build/initjars/default.jgz`
- `all/build/initjars/full.jgz`
- `all/build/initjars/shell.jgz`
- `all/build/initjars/tests.jgz`
- `all/build/cdroms/jnode-x86.iso`
- `all/build/x86/jnode32.gz`

### Targets utiles

Targets destacados de `all/build.xml`:

- `assemble`
- `x86`
- `x86_64`
- `cd-x86`
- `cd-x86-lite`
- `boot-files-winNT`
- `tests`
- `regression-tests`
- `check-plugins`
- `encoding-test`
- `encoding-fix`
- `pmd`
- `checkstyle`
- `checkstyle-new`
- `findbugs`
- `document-plugins`
- `hotswap`

### Configuracion del build

Archivos:

- `jnode.properties.dist`
- `jnode.properties`
- `.travis/jnode.properties`

Opciones visibles:

- plugin-lists custom
- menu GRUB custom
- desactivar bootdisk
- desactivar netboot
- elegir memory manager
- `jnode.debugger.host` y `jnode.debugger.port`
- activar seguridad
- controlar uso de classlib local
- activar o desactivar JNAsm

## Drivers y gestion de dispositivos

### El modelo de dispositivos

Archivo clave:

- `core/src/driver/org/jnode/driver/AbstractDeviceManager.java`

Observaciones:

- mantiene registro de dispositivos
- mantiene mappers `DeviceToDriverMapper`
- mantiene `DeviceFinder`
- expone listeners del manager y del dispositivo
- tiene un `SystemBus`
- al registrar un device intenta encontrar driver y arrancarlo
- puede bloquear el arranque si en cmdline aparece `no<deviceId>`

Esto es una arquitectura bastante clasica de OS, pero expresada en objetos Java.

### Familias de drivers observadas

En `core/src/driver/org/jnode/driver/` aparecen grupos como:

- `bus`
- `chipset`
- `console`
- `input`
- `serial`
- `system`
- `textscreen`
- `character`

Ademas, por descriptores, se ve soporte para:

- PCI
- SMBus
- USB y hubs UHCI
- Firewire
- IDE, ATAPI y SCSI
- PS2
- CMOS, firmware y PnP
- speaker
- multiples NICs
- multiples GPUs y framebuffers

## Sistema de archivos y almacenamiento

### VFS y servicio central

Archivo clave:

- `fs/src/fs/org/jnode/fs/service/def/FileSystemPlugin.java`

Que hace:

- implementa `FileSystemService`
- administra tipos de FS y sistemas montados
- crea un `VirtualFSDevice`
- expone una `VirtualFS`
- registra API de FS hacia la VM (`VMIOUtils`)
- levanta un `FileSystemMounter`
- se publica en `InitialNaming`

Este plugin es el corazon del subsistema de archivos.

### Lectura arquitectonica

La capa de FS tiene dos responsabilidades diferentes:

- proveer una VFS global para la semantica de rutas y montajes
- implementar backends concretos para distintos formatos y dispositivos

### Sistemas de archivos observados por descriptores

El repositorio muestra soporte para:

- FAT
- JFAT
- EXT2
- ISO9660
- NTFS
- HFS+
- exFAT
- RAMFS
- initrd
- JIFS
- FTPFS
- SMBFS
- NFS

Esto es mas amplio de lo que sugiere una lectura superficial del repo.

### Particiones y bloque

Soporte visible:

- bloque IDE
- ramdisk
- SCSI CDROM
- USB storage
- particiones IBM y MBR
- GPT
- APM

### FS y red se cruzan

En `fs/build.xml` se ve dependencia tanto de `shell` como de `net`, y por descriptores existen:

- `org.jnode.fs.ftpfs`
- `org.jnode.fs.smbfs`
- `org.jnode.fs.nfs`

O sea: parte del espacio de archivos se apoya en protocolos remotos, no solo en disco local.

## Networking

### Servicio de red base

Archivo clave:

- `net/src/net/org/jnode/net/service/NetPlugin.java`

Lo que se observa:

- crea `DefaultNetworkLayerManager`
- registra `NetworkLayerManager` en `InitialNaming`
- levanta un `QueueProcessorThread<SocketBuffer>`
- inyecta API de red para la VM (`VMNetUtils.setAPI`)

La red esta organizada como un manager de layers con procesamiento asincrono de paquetes.

### Configuracion IPv4

Archivo clave:

- `net/src/net/org/jnode/net/ipv4/config/impl/IPv4ConfigurationPlugin.java`

Lo importante:

- crea un `ConfigurationProcessor`
- carga config desde preferencias
- registra `IPv4ConfigurationService`
- observa `DeviceManager`
- reacciona a dispositivos de red y los configura via `WorkUtils`

Esto implica que la configuracion de red no es puramente estatica: tambien es reactiva a aparicion de dispositivos.

### Protocolos y componentes visibles

Por paquetes y descriptores:

- Ethernet
- ARP
- IPv4
- IPv4 config
- NFS
- FTP
- TFTP
- DNS (`org.xbill.dns`)
- ONC RPC
- JSch

### Drivers de NIC visibles por descriptor

Ejemplos:

- `3c90x`
- `bcm570x`
- `eepro100`
- `lance`
- `loopback`
- `ne2k-pci`
- `prism2`
- `rtl8139`
- `via_rhine`
- `wireless`
- `usb.bluetooth`

## Shell, CLI y experiencia de usuario

### Shell principal

Archivo clave:

- `shell/src/shell/org/jnode/shell/CommandShell.java`

Esta clase no es una shell minima. Soporta:

- historial de comandos
- historial de input de aplicaciones
- completion
- aliases
- sintaxis formalizada
- interpretes intercambiables
- invokers intercambiables
- integracion con consola JNode
- modo de boot shell

El `main(String[])` busca la consola enfocada desde `ConsoleManager`, construye una `CommandShell` y ejecuta `run()`.

### Interpretes e invokers

Valores visibles:

- invoker inicial: `proclet`
- invoker de emulacion: `thread`
- interpreter inicial: `redirecting`

Esto sugiere que la shell separa parsing e interpretacion de la estrategia de invocacion.

### Bjorne shell

Se observa plugin y pruebas para:

- `org.jnode.shell.bjorne`

Eso apunta a una shell compatible con estilo Bourne o Bjorne, al menos en parte.

### CLI por categorias

`cli/src/commands/org/jnode/command/` esta organizado en:

- `archive`
- `argument`
- `common`
- `dev`
- `file`
- `net`
- `system`
- `util`

Esto es buena pista para orientarse en los comandos del sistema.

### Text UI

`textui/src/textui/` contiene:

- `charva`
- `charvax`

La lectura practica es que JNode tiene una capa tipo toolkit para aplicaciones de texto, con widgets estilo AWT o Swing pero orientados a terminal y text mode.

## GUI, AWT y desktop

### Toolkit grafico

Archivo clave:

- `gui/src/awt/org/jnode/awt/JNodeToolkit.java`

Lo que muestra:

- integra `ClasspathToolkit`
- administra `EventQueue`
- gestiona clipboard
- trabaja con `FrameBufferAPI`
- mantiene `JNodeFrameBufferDevice`
- coordina keyboard y mouse handlers
- puede arrancar, parar y refrescar GUI

No es un wrapper superficial: es la base grafica del sistema en terminos de AWT.

### Desktop

Archivos:

- `gui/src/desktop/org/jnode/desktop/DesktopPlugin.java`
- `gui/src/desktop/org/jnode/desktop/Desktop.java`
- `gui/src/desktop/org/jnode/desktop/classic/Desktop.java`

`DesktopPlugin` actualmente fija:

- `jnode.desktop = org.jnode.desktop.classic.Desktop`

O sea, el desktop clasico parece ser la opcion activa por defecto.

### Video y framebuffer

Descriptores relevantes en `gui/descriptors/`:

- `org.jnode.driver.video.vga`
- `org.jnode.driver.video.vgahw`
- `org.jnode.driver.video.vesa`
- `org.jnode.driver.video.vmware`
- `org.jnode.driver.video.cirrus`
- `org.jnode.driver.video.nvidia`
- `org.jnode.driver.video.ati.mach64`
- `org.jnode.driver.video.ati.radeon`

Esto muestra que la GUI depende de una capa de video relativamente rica para un proyecto de este tipo.

### Entrada y consola grafica

Tambien se ven descriptores para:

- `org.jnode.driver.ps2`
- `org.jnode.driver.input.usb`
- `org.jnode.driver.console.swing`
- `org.jnode.driver.textscreen.fb`
- `org.jnode.driver.textscreen.swing`

### Fuentes

Descriptores y paquetes muestran soporte de fuentes:

- `org.jnode.awt.font`
- `org.jnode.awt.font.truetype`
- `org.jnode.font.bdf`
- `org.jnode.awt.font.bdf`

## Aplicaciones de distribucion

`distr/src/apps/org/jnode/apps/` contiene paquetes como:

- `charvabsh`
- `commander`
- `console`
- `debug`
- `derby`
- `edit`
- `editor`
- `httpd`
- `jetty`
- `jpartition`
- `telnetd`
- `vmware`

Lectura practica:

- `distr/` no es solo instalador
- es el espacio donde viven apps de usuario y utilidades del sistema empacadas para la distribucion final

`jpartition` destaca por volumen propio, lo que sugiere una aplicacion relativamente seria dentro del ecosistema JNode.

## Testing, CI y calidad

### Suites de prueba observadas

Hay `build-tests.xml` al menos en:

- `cli/`
- `core/`
- `fs/`
- `net/`
- `shell/`

Conteo aproximado de clases de test observadas:

| Modulo | Test classes |
| --- | ---: |
| `core` | 78 |
| `fs` | 35 |
| `gui` | 31 |
| `shell` | 27 |
| `net` | 11 |
| `distr` | 4 |
| `builder` | 2 |
| `cli` | 0 |
| `textui` | 0 o sin `src/test` |

### Particularidad de `shell`

`shell/build-tests.xml` corre:

- JUnit
- black-box tests via `org.jnode.test.shell.harness.TestHarness`
- pruebas especificas de Bjorne shell

Eso indica un enfoque bastante maduro para la shell: no solo unit tests, tambien pruebas de comportamiento.

### Regression tests de `builder`

`all/build.xml` tiene target `regression-tests` que delega al `builder`, donde se ven pruebas ligadas a JNAsm y ensamblador.

### Calidad estatica

`all/build.xml` expone targets para:

- `pmd`
- `checkstyle`
- `checkstyle-new`
- `findbugs`
- `check-plugins`
- `encoding-test`
- `encoding-fix`

Tambien hay:

- `sonar-project.properties`

que apunta especialmente a `fs` y `net`.

### Integracion continua historica

`.travis.yml` y `.travis/travis.sh` muestran una pipeline historica que:

1. instala `nasm` y `qemu`
2. corre `./build.sh clean -Dbuild.properties.file=../.travis/jnode.properties cd-x86-lite regression-tests`
3. arranca QEMU headless con la ISO lite
4. redirige salida serie a archivo
5. espera la cadena `System has finished`

Eso es muy valioso porque revela el criterio minimo de "el sistema bootea y llega a un final observable".

## Scripts de uso y operacion

### Build

- `build.sh`
- `build.bat`

Ambos lanzan Ant via `core/lib/ant-launcher.jar`.

### Ejecucion en QEMU

Scripts visibles:

- `qemu.sh`
- `qemu.bat`

Observaciones:

- `qemu.bat` usa `qemu-system-i386`, `-m 2048`, `-smp 2`, y la ISO `jnode-x86.iso`
- `qemu.sh` usa `qemu-system-x86_64`, ISO `jnode-x86-lite.iso`, `-usb` y `-vga vmware`

Esto sugiere que los flujos de prueba no son identicos entre Windows y Unix-like.

### Pruebas por script

- `test.sh <project> [targets...]`

Permite lanzar `build-tests.xml` por modulo o para todos.

## Documentacion historica y artefactos anexos

La carpeta `docs/` no parece una documentacion moderna estilo handbook. Mas bien funciona como archivo historico del proyecto. Contiene:

- presentaciones (`JNodeIntro`, `ReloadablePlugins`, `IsolatesAndGc`, etc.)
- diagramas de arquitectura
- specs de VM
- specs externas de hardware, por ejemplo ATA y ATAPI
- ayuda de Eclipse

Esto tiene dos implicaciones:

- hay conocimiento valioso fuera del codigo
- parte de ese conocimiento probablemente refleja estados historicos del proyecto y no necesariamente el estado exacto del arbol actual

## IDEs y legado de herramientas

El repo contiene huellas de varias epocas:

- Eclipse: `.project`, `.classpath`
- IntelliJ: `.idea/`, `.ipr`, `.iws`, `.iml`
- NetBeans: `netbeans/`

Eso habla de un proyecto viejo, vivido y mantenido durante mucho tiempo por distintos colaboradores y flujos de trabajo.

## Particularidades y caveats importantes

### 1. El proyecto es profundamente legacy-friendly

Muchos indicadores apuntan a un ecosistema historico:

- target y source Java 1.6
- Travis con Oracle JDK 7
- Ant como build central
- GRUB y targets de arranque tipo NTLDR y JOP
- mezcla de GNU Classpath y OpenJDK

No es una critica. Es parte esencial de como esta construido el proyecto.

### 2. `sound/` hoy no es un modulo fuerte

Existe `sound/build.xml`, pero no hay `sound/src/` util. Operativamente parece un placeholder o modulo incompleto.

### 3. Hay customizacion local que cambia el comportamiento

El override local de plugin list sin particiones puede alterar:

- booteo
- deteccion de discos y particiones
- montaje
- debugging de deadlocks

No hay que asumir que el comportamiento local coincide con el `default-plugin-list.xml` original.

### 4. El build cuida problemas de path y encoding

Detalles importantes:

- en Windows no se toleran espacios en la ruta del proyecto
- `java.encoding` esta fijado a `US-ASCII`
- existen tareas `encoding-test` y `encoding-fix`

Eso sugiere que encoding y tooling historicamente han sido una fuente real de problemas.

### 5. El proyecto tiene multiples capas de Java

Conviene no pensar en Java como una sola cosa dentro de JNode. Aqui hay:

- codigo de aplicacion
- codigo de shell
- servicios del sistema
- runtime de la VM
- clases de libreria de clases
- adaptaciones OpenJDK
- primitivas cercanas al hardware
- codigo nativo que hace posible todo lo anterior

## Recorrido recomendado para entender el proyecto

Si alguien nuevo quiere entender JNode rapido y con buen retorno, este orden tiene sentido:

1. `all/build.xml`
2. `all/lib/jnode.xml`
3. `all/build-x86.xml`
4. `builder/src/builder/org/jnode/build/x86/BootImageBuilder.java`
5. `core/src/core/org/jnode/boot/Main.java`
6. `core/src/core/org/jnode/vm/VmSystem.java`
7. `core/src/core/org/jnode/plugin/*`
8. `core/src/driver/org/jnode/driver/AbstractDeviceManager.java`
9. `fs/src/fs/org/jnode/fs/service/def/FileSystemPlugin.java`
10. `net/src/net/org/jnode/net/service/NetPlugin.java`
11. `shell/src/shell/org/jnode/shell/CommandShell.java`
12. `gui/src/awt/org/jnode/awt/JNodeToolkit.java`
13. `all/conf/default-plugin-list.xml`
14. `local/plugin-lists/default-plugin-list.xml`

Ese recorrido te lleva desde el build y el boot, hacia la composicion por plugins, y luego a los servicios visibles por el usuario.

## Lectura conceptual final

JNode es un experimento serio y de gran escala alrededor de una idea extrema pero coherente:

- usar Java no solo para aplicaciones, sino para construir el propio sistema operativo

El repositorio demuestra que esa idea no se quedo en una prueba de concepto minima. Hay evidencia concreta de:

- boot image real
- toolchain propio
- VM y scheduler
- drivers
- filesystem stack
- networking
- shell
- GUI, AWT y framebuffer
- aplicaciones distribuidas
- pruebas y CI historicos

Tambien deja ver los costos naturales de esa ambicion:

- complejidad elevada
- mezcla de capas de distinta epoca
- dependencia de tooling legacy
- necesidad de overrides locales para algunos problemas operativos

Pero justo por eso el proyecto es interesante. No es otra app Java. Es una tesis de arquitectura de sistemas hecha repositorio.

## En una frase

Si hubiera que resumir el proyecto con precision tecnica y sin marketing:

JNode es un monorepo de sistema operativo en Java para x86, construido alrededor de una VM propia, un framework de plugins, una infraestructura de build y boot image hecha a medida y un conjunto amplio de subsistemas de OS escritos mayoritariamente en Java.
