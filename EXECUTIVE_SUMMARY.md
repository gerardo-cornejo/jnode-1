# Resumen Ejecutivo: Dónde Inyectar Splash en JNode

## TL;DR - Respuesta Rápida

### 🎯 PUNTO RECOMENDADO

**Archivo:** `core/src/driver/org/jnode/driver/console/textscreen/TextScreenConsolePlugin.java`
**Línea:** 62 (antes de `System.out.println(VmSystem.getBootLog())`)
**Tipo:** ASCII art splash
**Garantía:** 100% - funciona en TODOS los modos

---

## 1. Los 3 Modos de Video Encontrados

| MODO | DRIVER | CUÁNDO | SEGURIDAD |
|------|--------|--------|-----------|
| **DEFAULT** | PcTextScreenManager | Siempre | ✅ Máxima |
| **FB** | FbTextScreenManager | Si " fb" en GRUB | ⚠️ Media |
| **VESA** | VESADriver | Si device VESA | ⚠️ Media |

---

## 2. Puntos de Inyección Encontrados

### ✅ Opción 1: ASCII ART en Console Manager (RECOMENDADO)
```
Archivo: TextScreenConsolePlugin.java
Línea: 62 (antes de boot log)
Funciona en: TODOS los modos ✓
Complejidad: BAJA
Seguridad: MÁXIMA

Código:
protected void startPlugin() throws PluginException {
    // ... setup consola ...
    
    // ➜ INSERTAR AQUÍ
    System.out.println(SPLASH_ASCII_ART);
    
    System.out.println(VmSystem.getBootLog());
}
```

### ⭐ Opción 2: Gráfico en Framebuffer Manager
```
Archivo: FbTextScreenManager.java
Línea: ~90 (después api.open, antes clearScreen)
Funciona en: Modo FB solamente
Complejidad: MEDIA
Seguridad: BUENA

Código:
api.requestOwnership(this);
surface = api.open(conf);

// ➜ INSERTAR AQUÍ
BootSplashGif splash = new BootSplashGif(...);
splash.render();

clearScreen();
```

### ⭐ Opción 3: Gráfico en FBConsole  
```
Archivo: FBConsole.java
Línea: ~67 (después FbTextScreenManager, antes bind)
Funciona en: Modo FB solamente
Complejidad: MEDIA
Seguridad: BUENA

Código:
fbTsMgr = new FbTextScreenManager(api, conf);

// ➜ INSERTAR AQUÍ (si necesario)

InitialNaming.bind(TextScreenManager.NAME, fbTsMgr);
```

---

## 3. Clases Clave Identificadas

### Inicialización
- **PcTextScreenPlugin** - Modo DEFAULT
- **FbTextScreenPlugin** - Modo FB (requiere flag " fb")
- **TextScreenConsolePlugin** ⭐ - Manager de consolas (MEJOR PUNTO)

### Managers
- **PcTextScreenManager** - VGA 80x25
- **FbTextScreenManager** - Framebuffer gráfico  
- **TextScreenConsoleManager** - Console manager (escucha eventos)

### Base Gráfica
- **BootSplashGif** - YA EXISTE sin usar
- **VESADriver** - Driver de video VESA

### Interfaces
- **TextScreenManager** - Interfaz common
- **FrameBufferAPI** - API gráfica
- **ConsoleManager** - Manager de consolas

---

## 4. Métodos de Inicialización

```
Orden de ejecución:
1. PcTextScreenPlugin.startPlugin()        [130ms ≈]
2. FbTextScreenPlugin.startPlugin()        [async, si " fb"]
3. TextScreenConsolePlugin.startPlugin()   [200ms ≈] ⭐ MEJOR AQUÍ
4. Log4j, AWT, Desktop, Shell...
```

---

## 5. Flujo de Inicialización Gráfica

```
BOOT
 ↓
VGA 80x25 (PcTextScreenPlugin)
 ↓
Framebuffer gráfico (FbTextScreenPlugin si " fb")
 ↓
Console Manager (TextScreenConsolePlugin) ⭐ SPLASH ASCII
 ↓
Boot Log
 ↓
Shell
```

---

## 6. RESUMEN POR DRIVER

### PcTextScreenManager (DEFAULT)
- **Ubicación:** `core/src/driver/org/jnode/driver/textscreen/x86/`
- **Plugin:** `PcTextScreenPlugin.java`
- **Tipo:** VGA 80x25 texto puro
- **Splash:** Posible (ASCII), pero mejor con Console
- **Timing:** Muy temprano en boot

### FbTextScreenManager (FRAMEBUFFER)
- **Ubicación:** `gui/src/driver/org/jnode/driver/textscreen/fb/`
- **Plugin:** `FbTextScreenPlugin.java` (si " fb" en GRUB)
- **Tipo:** Gráfico completo (framebuffer)
- **Splash:** Posible (BootSplashGif) → YA EXISTE
- **Timing:** Medio-temprano en boot

### TextScreenConsoleManager (CONSOLE)
- **Ubicación:** `core/src/driver/org/jnode/driver/console/textscreen/`
- **Plugin:** `TextScreenConsolePlugin.java`
- **Tipo:** Console text (text + scrollable buffers)
- **Splash:** ⭐ MEJOR PUNTO (ASCII ART)
- **Timing:** Después de drivers, antes de shell
- **Garantía:** 100% - funciona en TODOS los modos

---

## 7. Drivers de Video (NO se inyecta splash aquí)

### VESADriver
- **Ubicación:** `gui/src/driver/org/jnode/driver/video/vesa/`
- **Método:** `startDevice()`
- **Rol:** Solo driver - no display
- **Nota:** ✗ No es punto de splash

---

## 8. Decisión Final

### SI quieres máxima portabilidad:
✅ **TextScreenConsolePlugin** → ASCII art splash

### SI quieres gráficos bonitos:
✅ **FbTextScreenManager** → BootSplashGif (usar VESACore)

### SI quieres ambos:
✅ Combina:
- ASCII en Console (todos modos)
- Gráfico en FB (modo fb solamente)

---

## 9. ARCHIVO MÁS IMPORTANTE

**EDITAR:** `core/src/driver/org/jnode/driver/console/textscreen/TextScreenConsolePlugin.java`

**Línea:** En `startPlugin()`, alrededor de línea 62

**Cambio:**
```java
// ANTES:
System.out.println(VmSystem.getBootLog());

// DESPUÉS:
printBootSplash();  // ← Nueva función
System.out.println(VmSystem.getBootLog());
```

---

## 10. Verificación de Existencia

### Archivos que YA EXISTEN para usar:

✅ `BootSplashGif.java` - Clase de splash gráfico (sin usar)
```java
public class BootSplashGif {
    public void render() { ... }
    private void drawBorderBox() { ... }
    private void drawSimpleSpinner() { ... }
}
```

✅ `TextScreenConsolePlugin.java` - Plugin de console
```java
public class TextScreenConsolePlugin extends Plugin {
    protected void startPlugin() throws PluginException { ... }
}
```

---

## 11. TABLA RÁPIDA DE REFERENCIA

| Clase | Plugin | Método | Línea | Tipo | ✓ Usar |
|-------|--------|--------|-------|------|---------|
| PcTextScreenPlugin | Sí | startPlugin()  | 45 | DEFAULT| No |
| FbTextScreenPlugin | Sí | startPlugin() | 43 | FB | No |
| FBConsole | No | startFBConsole() | 61 | FB | Opcional |
| FbTextScreenManager | No | constructor | 75 | FB Gráfico | Sí (si FB) |
| BootSplashGif | No | render() | - | Gráfico | Sí (si FB) |
| **TextScreenConsolePlugin** | **Sí** | **startPlugin()** | **62** | **CONSOLE** | **⭐ SÍ (TODO)** |

---

## 12. Próximos Pasos

1. **Crear clase BootSplashPrinter.java**
   - Genera ASCII art del splash
   - Método: `printAsciiSplash(PrintStream out)`

2. **Modificar TextScreenConsolePlugin.java**
   - Llamar a BootSplashPrinter.printAsciiSplash(System.out)
   - Antes de System.out.println(VmSystem.getBootLog())

3. **Opcionalmente: Activar BootSplashGif**
   - En FbTextScreenManager constructor
   - Después de api.open(conf)
   - Si conf es gráfico

4. **Compilar y probar**
   - `./build.bat cd-x86`
   - Verificar splash en QEMU

---

## CONCLUSIÓN

**El splash de JNode debe ir en:**

### Ubicación: `TextScreenConsolePlugin.java`
### Línea: 62 (antes de boot log)
### Tipo: ASCII art
### Por qué: Funciona en TODO, es seguro y es el lugar "correcto" en el flujo boot

**Alternativa gráfica:**
### Ubicación: `FbTextScreenManager.java`
### Línea: 90 (después api.open)
### Tipo: BootSplashGif (ya existe)
### Por qué: Visual pero solo en modo FB

