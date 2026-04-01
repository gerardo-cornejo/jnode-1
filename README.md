# JNode - Sistema Operativo en Java

[![Build Status](https://travis-ci.org/jnode/jnode.svg?branch=master)](https://travis-ci.org/jnode/jnode)

JNode es un proyecto único y ambicioso: un sistema operativo completo implementado predominantemente en Java, diseñado para ejecutarse directamente sobre hardware x86 sin necesidad de una máquina virtual Java convencional.

## Tabla de Contenidos

1. [Historia del Proyecto](#historia-del-proyecto)
2. [Arquitectura General](#arquitectura-general)
3. [Estructura de Directorios](#estructura-de-directorios)
4. [Sub-Proyectos](#sub-proyectos)
5. [Componentes del Kernel](#componentes-del-kernel)
6. [Sistema de Construcción](#sistema-de-construcción)
7. [Configuración](#configuración)
8. [Construcción y Ejecución](#construcción-y-ejecución)
9. [Tecnologías y Dependencias](#tecnologías-y-dependencias)

---

## Historia del Proyecto

JNode (Java Operating System - Node) fue iniciado como un proyecto de código abierto con el objetivo de crear un sistema operativo donde Java fuera el lenguaje principal de desarrollo. A diferencia de otros proyectos que usan Java solo para aplicaciones de usuario, JNode lleva Java al núcleo del sistema operativo, ejecutándose directamente sobre el hardware x86 mediante una máquina virtual personalizada.

El proyecto ha sido desarrollado durante muchos años por un equipo de voluntarios, demostrando la viabilidad de implementar un sistema operativo completo utilizando un lenguaje de alto nivel como Java.

---

## Arquitectura General

JNode sigue una **arquitectura modular** dividida en varios sub-proyectos, cada uno encapsulando una funcionalidad específica del sistema operativo. Esta separación facilita el mantenimiento, las pruebas y el desarrollo independiente de cada componente.

### Principios de Diseño

- **Máquina Virtual Personalizada**: JNode implementa su propia JVM que ejecuta código Java directamente sobre hardware x86
- **Sistema de Plugins**: El sistema utiliza un arquitectura basada en plugins que permite cargar componentes dinámicamente
- **Sistema de Archivos Virtual (VFS)**: Abstracción unificada sobre múltiples sistemas de archivos físicos
- **Gestión de Memoria Avanzada**: Soporte para múltiples algoritmos de gestión de memoria incluyendo MMTk

---

## Estructura de Directorios

```
jnode/
├── all/                 # Proyecto raíz que integra todos los sub-proyectos
├── builder/             # Herramientas de construcción del sistema
├── cli/                 # Comandos de línea de comandos adicionales
├── core/                # Núcleo: VM, kernel, framework de controladores
├── distr/               # Herramientas y apps para distribución
├── docs/                # Documentación del proyecto
├── fs/                  # Sistemas de archivos y controladores de bloque
├── gui/                 # Interfaz gráfica (AWT parcial + Thinlet)
├── net/                 # Pila de red y controladores de red
├── shell/               # Shell interactivo y comandos del sistema
├── sound/               # Sistema de sonido (trabajo futuro)
├── textui/              # Interfaz de texto AWT
├── builder/            # Herramientas de construcción
├── licenses/           # Licencias del proyecto
├── .travis/             # Configuración de Travis CI
├── build.sh/build.bat  # Scripts de construcción
├── jnode.properties.dist # Plantilla de propiedades
└── README.md           # Este archivo
```

Cada sub-proyecto sigue la misma estructura interna:

```
<subproyecto>/
├── build/           # Resultados de la compilación
├── descriptors/     # Descriptores de plugins XML
├── lib/            # Librerías específicas del sub-proyecto
├── src/            # Código fuente
├── .classpath      # Configuración de classpath para Eclipse
├── .project        # Archivo de proyecto Eclipse
├── build.xml       # Archivo de construcción Ant
└── build-tests.xml # Pruebas del sub-proyecto
```

---

## Sub-Proyectos

### 1. JNode-Core (`core/`)

El corazón del sistema operativo. Contiene:

- **Máquina Virtual de Java**: Implementación personalizada de JVM para x86
- **Kernel del OS**: Funcionalidades centrales del sistema operativo
- **Framework de Controladores**: Sistema modular para drivers de dispositivos
- **Gestor de Memoria**: Soporte para múltiples algoritmos (MMTk, NoGC, GenRC)
- **Planificador de Procesos**: Algoritmos de planificación deCPU

**Paquetes principales:**
- `org.jnode.vm` - Máquina virtual
- `org.jnode.vm.x86` - Código específico de arquitectura x86
- `org.jnode.vm.compiler` - Compilador JIT
- `org.jnode.vm.memmgr` - Gestor de memoria
- `org.jnode.vm.scheduler` - Planificador de procesos
- `org.jnode.driver` - Framework de controladores
- `org.jnode.boot` - Código de arranque
- `org.jnode.system` - Servicios del sistema

### 2. JNode-FS (`fs/`)

Sistemas de archivos y controladores de dispositivos de bloque:

**Sistemas de archivos soportados:**
- **FAT**: FAT12, FAT16, FAT32
- **ext2/ext3/ext4**: Sistema de archivos de Linux
- **NFS**: Network File System para montajes remotos

**Controladores de dispositivos:**
- Discos IDE
- Discos SCSI
- Dispositivos de partición
- Tablas de partición MBR (IBM PC estándar)

**Paquetes principales:**
- `org.jnode.fs` - Interfaces y clases base
- `org.jnode.fs.fat` - Implementación FAT
- `org.jnode.fs.ext2` - Implementación ext2/ext3
- `org.jnode.partitions.ibm` - Particiones MBR

### 3. JNode-Net (`net/`)

Pila de red completa con soporte para:

**Protocolos de bajo nivel:**
- Ethernet
- ARP (Address Resolution Protocol)
- IPv4

**Protocolos de transporte:**
- TCP (Transmission Control Protocol)
- UDP (User Datagram Protocol)
- ICMP (Internet Control Message Protocol)

**Protocolos de aplicación:**
- DHCP
- FTP
- TFTP
- NFS (cliente y servidor)

**Paquetes principales:**
- `org.jnode.net.ethernet` - Capa de enlace
- `org.jnode.net.arp` - Protocolo ARP
- `org.jnode.net.ipv4` - Protocolo IPv4
- `org.jnode.net.tcp` - TCP
- `org.jnode.net.udp` - UDP
- `org.jnode.net.nfs` - NFS

### 4. JNode-Shell (`shell/`)

Interfaz de línea de comandos:

- Intérprete de comandos interactivo
- Arquitectura basada en plugins para comandos
- Compatible con comandos Unix estándar

### 5. JNode-GUI (`gui/`)

Interfaz gráfica de usuario:

- Implementación parcial de AWT (Abstract Window Toolkit)
- Toolkit Thinlet (biblioteca ligera de UI en Java puro)
- Controladores de dispositivos de video
- Controladores de dispositivos de entrada (teclado, ratón)

**Paquetes principales:**
- `org.jnode.awt` - Implementación AWT
- `thinlet` - Toolkit Thinlet
- `org.jnode.driver` - Controladores de dispositivos gráficos
- `org.jnode.font` - Gestión de fuentes

### 6. JNode-CLI (`cli/`)

Comandos adicionales de línea de comandos:

- Comandos utilitarios para scripts
- Integración con el shell
- Arquitectura de plugins

### 7. JNode-Builder (`builder/`)

Herramientas de construcción:

- Constructor de imágenes del sistema
- Configurador del sistema
- Generación de imágenes ISObootables
- Configuraciones para arranque PXE

### 8. JNode-Distr (`distr/`)

Distribución del sistema:

- Aplicaciones del sistema
- Herramientas de instalación
- Componentes de emulación
- Bibliotecas adicionales

### 9. JNode-All (`all/`)

Proyecto raíz que integra todos los sub-proyectos:

- Archivos de configuración de construcción
- Listas de plugins del sistema
- Configuraciones de arranque (GRUB)
- Resultados finales de la construcción (imágenes ISO)

---

## Componentes del Kernel

### 1. Máquina Virtual de Java (JVM)

La JVM de JNode es una implementación personalizada que ejecuta código Java directamente sobre hardware x86:

- **Intérprete de Bytecode**: Ejecuta instrucciones Java
- **Compilador JIT** (opcional): Compilación Just-In-Time para mejor rendimiento
- **Gestor de Memoria**: Gestión de objetos Java y memoria nativa

Ubicación: `core/src/core/org/jnode/vm/`

### 2. Gestor de Memoria

Sistema avanzado de gestión de memoria con múltiples estrategias:

- **Default (def)**: Gestor de memoria predeterminado
- **MMTk**: Memory Management Toolkit con múltiples algoritmos
- **NoGC**: Sin recolección de basura
- **GenRC**: Generacional con conteo de referencias

Ubicación: `core/src/core/org/jnode/vm/memmgr/`

### 3. Planificador de Procesos

Determina la distribución del tiempo de CPU entre hilos de ejecución:

- Considera prioridades de procesos
- Quantum de tiempo configurable
- Distribución justa de recursos

Ubicación: `core/src/core/org/jnode/vm/scheduler/`

### 4. Controladores de Dispositivos

Framework modular para drivers de hardware:

- **Disco**: IDE, SCSI
- **Red**: Tarjetas Ethernet
- **Vídeo**: Controladores gráficos
- **Entrada**: Teclado, ratón

Ubicación: `core/src/driver/`

### 5. Sistema de Archivos Virtual (VFS)

Capa de abstracción unificada:

- Acceso uniforme a diferentes sistemas de archivos
- Soporte para FAT, ext2, NFS, etc.
- Interfaz `FileSystem` como punto central

Ubicación: `fs/src/fs/org/jnode/fs/`

### 6. Pila de Red

Implementación de protocolos de Internet:

- IPv4, TCP, UDP, ICMP, ARP
- Diseño ligero para sistemas embebidos
- Soporte para DHCP, FTP, TFTP, NFS

Ubicación: `net/src/net/org/jnode/net/`

### 7. Sistema de Plugins

Arquitectura extensible:

- Carga dinámica de componentes
- Descriptores XML en `descriptors/`
- Listas de plugins en `all/conf/`

### 8. Sistema de Arranque

Proceso de inicialización:

- Inicialización de hardware de bajo nivel
- Carga del kernel Java
- Inicialización de servicios fundamentales
- Soporte para múltiples medios de arranque

---

## Sistema de Construcción

JNode utiliza **Apache Ant** como sistema de construcción.

### Estructura de Construcción

```
all/build.xml          # Punto de entrada principal
core/build.xml         # Construcción del núcleo
fs/build.xml           # Sistema de archivos
net/build.xml          # Red
shell/build.xml        # Shell
gui/build.xml          # Interfaz gráfica
```

### Objetivos de Construcción Principales

| Objetivo | Descripción |
|----------|-------------|
| `cd-x86-lite` | Construye imagen CD-ROM básica |
| `help` | Muestra ayuda sobre objetivos disponibles |
| `clean` | Limpia archivos generados |
| `test` | Ejecuta pruebas unitarias |

### Proceso de Construcción

1. **Compilación**: Compila código Java de cada módulo
2. **Pruebas**: Ejecuta pruebas unitarias con JUnit
3. **Empaquetado**: Genera archivos JAR con descriptores de plugins
4. **Initjars**: Construye imágenes iniciales del sistema
5. **Imágenes ISO**: Crea imágenesbootables con GRUB

---

## Configuración

### jnode.properties

El archivo `jnode.properties` (copiado de `jnode.properties.dist`) controla:

- **Gestor de memoria**: Selección de algoritmo (MMTk, NoGC, GenRC)
- **Configuración de compilación**: Versión de Java destino
- **Opciones del compilador**: Parámetros de compilación
- **Ajustes de VM**: Configuración para pruebas
- **Rutas de herramientas**: mkisofs, grub, etc.

### Listas de Plugins

En `all/conf/`:

| Archivo | Descripción |
|---------|-------------|
| `default-plugin-list.xml` | Configuración predeterminada |
| `full-plugin-list.xml` | Todos los plugins |
| `shell-plugin-list.xml` | Plugins del shell |
| `system-plugin-list.xml` | Plugins del sistema |
| `tests-plugin-list.xml` | Plugins de pruebas |

---

## Construcción y Ejecución

### Requisitos

- Java JDK 8 o superior
- Apache Ant
- mkisofs (para imágenes ISO)
- GRUB (para bootloader)

### Construcción

**Windows:**
```batch
build.bat cd-x86-lite
```

**Linux:**
```bash
./build.sh cd-x86-lite
```

**Eclipse:**
Ejecutar el objetivo "cd-x86-lite" de `all/build.xml`

### Resultados de la Construcción

- `all/build/cdroms/jnode-x86-lite.iso` - Imagen CD-ROMarrancable
- `all/build/cdroms/jnode-x86-lite.iso.vmx` - Configuración VMware

### Ejecución

**VMware:**
Abrir `all/build/cdroms/jnode-x86-lite.iso.vmx` y hacer clic en Start

**QEMU (Linux):**
```bash
./qemu.sh
```

---

## Tecnologías y Dependencias

### Lenguajes

- **Java**: Lenguaje principal del proyecto
- **Ensamblador (x86)**: Código de bajo nivel para inicialización

### Bibliotecas Principales

- **Ant**: Sistema de construcción
- **JUnit**: Framework de pruebas
- **MMTk**: Memory Management Toolkit
- **Mauve**: Suite de pruebas
- **Thinlet**: Toolkit de UI ligero
- **Log4j**: Sistema de logging

### Herramientas

- **Eclipse**: IDE de desarrollo principal
- **Travis CI**: Integración continua
- **SonarQube**: Análisis de código estático

---

## Estructura del Código Fuente

### Core (`core/src/`)

```
classlib/         # Bibliotecas de clases Java
classpath/        # Implementación de classpath
core/             # Núcleo del sistema (vm, system, boot, etc.)
driver/           # Framework de controladores
emu/              # Emulación
endorsed/         # Componentes endorsados
mmtk-vm/          # Memory Management Toolkit
native/           # Código nativo (ensamblador)
openjdk/          # Annotations de OpenJDK
template/         # Plantillas
test/             # Pruebas del núcleo
testframework/    # Framework de pruebas
vmmagic/          - Código mágico para la VM
```

### FS (`fs/src/`)

```
commands/         # Comandos de sistemas de archivos
driver/           # Controladores de dispositivos de bloque
fs/               # Implementación de sistemas de archivos
partitions/       # Tablas de partición
test/             # Pruebas
```

### Net (`net/src/`)

```
driver/           # Controladores de dispositivos de red
net/              # Pila de protocolos
test/             # Pruebas
```

---

## Contribuir al Proyecto

### Comunidad

- **Foros**: www.jnode.org
- **IRC**: #JNode.org@irc.oftc.net

### Desarrollo

1. Importar proyectos en Eclipse
2. Seguir las convenciones de código (checkstyle)
3. Ejecutar pruebas antes de enviar cambios
4. Usar el sistema de plugins para nuevas funcionalidades

---

## Limitaciones y Estado del Proyecto

JNode es un proyecto de investigación y desarrollo que demuestra la viabilidad de un sistema operativo implementado en Java. Aunque no ha alcanzado un estado de producción estable, proporciona:

- Prueba de concepto de OS en Java
- Base para investigación en sistemas operativos
- Referencia para implementación de JVMs
- Ejemplo de arquitectura de sistemas operativosmodulares

---

## Licencia

Ver directorio `licenses/` para detalles sobre las licencias de los diferentes componentes.

---

## Referencias

- Sitio web: www.jnode.org
- Repositorio: https://github.com/jnode/jnode
- IRC: #JNode.org@irc.oftc.net

---

*Este documento fue generado automáticamente a partir del análisis del código fuente del proyecto JNode.*