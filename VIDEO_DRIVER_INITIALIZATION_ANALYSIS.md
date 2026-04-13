# Análisis Completo: Inicialización de Drivers de Video en JNode

## Resumen Ejecutivo

No existe splash en modo DEFAULT. Hay 3 modos de inicialización de video:
1. **MODO DEFAULT** - TextScreen en VGA 80x25 (SEGURO para splash ASCII)
2. **MODO FB** - Framebuffer gráfico (disponible si grub contiene " fb")
3. **MODO VESA** - Video VESA (extends AbstractFrameBufferDriver)

---

## 1. INICIALIZACIÓN EN MODO DEFAULT

### Clase: PcTextScreenPlugin
**Ubicación:** `core/src/driver/org/jnode/driver/textscreen/x86/PcTextScreenPlugin.java`

```java
public class PcTextScreenPlugin extends Plugin {
    private final PcTextScreenManager mgr;

    public PcTextScreenPlugin(PluginDescriptor descriptor) {
        super(descriptor);
        this.mgr = new PcTextScreenManager();  // ✓ Inicializar aquí
    }

    protected void startPlugin() throws PluginException {
        try {
            InitialNaming.bind(PcTextScreenManager.NAME, mgr);
        } catch (NamingException ex) {
            throw new PluginException(ex);
        }
    }
}
```

**Métodos de Inicialización:**
- Constructor: `PcTextScreenPlugin(PluginDescriptor)`
- `startPlugin()` - Registra manager en JNDI

**PUNTO DE INYECCIÓN #1 (MÁS SEGURO):**
```
En PcTextScreenPlugin.startPlugin():
  - Antes de InitialNaming.bind()
  - Insertar método para dibujar ASCII splash
  - Ejecuta en modo texto puro sin dependencias gráficas
```

---

### Clase: PcTextScreenManager
**Ubicación:** `core/src/driver/org/jnode/driver/textscreen/x86/PcTextScreenManager.java`

```java
public class PcTextScreenManager implements TextScreenManager {
    public TextScreen getSystemScreen();
    // Crea TextScreen en VGA 80x25
}
```

**Características:**
- Modo texto puro (80x25 caracteres)
- Sin dependencias gráficas
- Disponible desde el boot inicial

---

## 2. INICIALIZACIÓN EN MODO FRAMEBUFFER GRÁFICO

### Clase: FbTextScreenPlugin  
**Ubicación:** `gui/src/driver/org/jnode/driver/textscreen/fb/FbTextScreenPlugin.java`

```java
public final class FbTextScreenPlugin extends Plugin {
    protected void startPlugin() throws PluginException {
        //this plugin start up if the "fb" parameter was specified in the GRUB command line
        if (VmSystem.getCmdLine().indexOf(" fb") < 0)
            return;  // ✓ Solo se ejecuta si hay " fb" en GRUB

        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    FBConsole.start();  // ✓ Inicia console gráfica
                } catch (Throwable e) {
                    Unsafe.debugStackTrace(e);
                }
            }
        };
        t.start();
    }
}
```

**PUNTO DE INYECCIÓN #2 (GRÁFICO):**
```
En FBConsole.startFBConsole():
  - Después de: surface = api.open(conf)
  - Antes de: InitialNaming.bind(TextScreenManager.NAME, fbTsMgr)
  - Llamar: new BootSplashGif(surface, conf.getScreenWidth(), conf.getScreenHeight()).render()
```

---

### Clase: FBConsole
**Ubicación:** `gui/src/driver/org/jnode/driver/textscreen/fb/FBConsole.java`

```java
class FBConsole {
    public static void start() throws Exception {
        final Collection<Device> devs = DeviceUtils.getDevicesByAPI(FrameBufferAPI.class);
        if (dev_count > 0) {
            startFBConsole(devs.iterator().next());
        } else {
            // Wait for device registration
            DeviceUtils.getDeviceManager().addListener(new DeviceManagerListener() {
                public void deviceRegistered(Device device) {
                    if (device.implementsAPI(FrameBufferAPI.class)) {
                        startFBConsole(device);
                    }
                }
            });
        }
    }

    private static void startFBConsole(Device dev) {
        try {
            final FrameBufferAPI api = dev.getAPI(FrameBufferAPI.class);
            final FrameBufferConfiguration conf = api.getConfigurations()[0];

            FbTextScreenManager fbTsMgr = new FbTextScreenManager(api, conf);
            // ✓ AQUÍ SE PUEDE INYECTAR SPLASH (después de init)
            
            InitialNaming.unbind(TextScreenManager.NAME);
            InitialNaming.bind(TextScreenManager.NAME, fbTsMgr);
        } catch (Throwable ex) {
            log.error("Error in FBConsole", ex);
            if (fbTsMgr != null) {
                fbTsMgr.ownershipLost();
            }
        }
    }
}
```

---

### Clase: FbTextScreenManager
**Ubicación:** `gui/src/driver/org/jnode/driver/textscreen/fb/FbTextScreenManager.java`

```java
final class FbTextScreenManager implements TextScreenManager, FrameBufferAPIOwner {
    FbTextScreenManager(FrameBufferAPI api, FrameBufferConfiguration conf) 
        throws UnknownConfigurationException, AlreadyOpenException, DeviceException {
        
        api.requestOwnership(this);
        surface = api.open(conf);        // ✓ Surface disponible AQUÍ
        this.conf = conf;
        
        clearScreen();                   // ✓ ANTES de esto se puede dibujar splash
        systemScreen = new FbTextScreen(...);
    }
}
```

**Métodos Importantes:**
- Constructor - Inicializa surface gráfica
- `getSystemScreen()` - Retorna TextScreen
- `ownershipLost()` - Limpia cuando pierde control
- `ownershipGained()` - Recupera control

---

## 3. MANAGER DE CONSOLAS

### Clase: TextScreenConsolePlugin
**Ubicación:** `core/src/driver/org/jnode/driver/console/textscreen/TextScreenConsolePlugin.java`

```java
public class TextScreenConsolePlugin extends Plugin {
    protected void startPlugin() throws PluginException {
        try {
            mgr = new TextScreenConsoleManager();
            InitialNaming.bind(ConsoleManager.NAME, mgr);

            // Create the first console
            final TextConsole first = (TextConsole) mgr.createConsole(
                null,
                (ConsoleManager.CreateOptions.TEXT |
                    ConsoleManager.CreateOptions.SCROLLABLE));
            first.setAcceleratorKeyCode(KeyEvent.VK_F1);
            mgr.focus(first);
            System.setOut(new PrintStream(...));
            System.setErr(new PrintStream(...));
            
            System.out.println(VmSystem.getBootLog());  // ✓ Boot log aquí
        } catch (ConsoleException ex) {
            throw new PluginException(ex);
        }
    }
}
```

**PUNTO DE INYECCIÓN #3 (ASCII ART):**
```
Antes de System.out.println(VmSystem.getBootLog()):
  - Insertar ASCII art del splash
  - Funciona en TODOS los modos (DEFAULT, FB, VESA)
  - Garantizado que se va a ver
```

---

## 4. VESA VIDEO DRIVER

### Clase: VESADriver
**Ubicación:** `gui/src/driver/org/jnode/driver/video/vesa/VESADriver.java`

```java
public class VESADriver extends AbstractFrameBufferDriver {
    protected void startDevice() throws DriverException {
        try {
            Address vbeControlInfo = UnsafeX86.getVbeControlInfos();  // De GRUB
            VbeInfoBlock vbeInfoBlock = new VbeInfoBlock(vbeControlInfo);
            
            Address vbeModeInfo = UnsafeX86.getVbeModeInfos();
            ModeInfoBlock modeInfoBlock = new ModeInfoBlock(vbeModeInfo);

            kernel = new VESACore(this, vbeInfoBlock, modeInfoBlock, (PCIDevice) getDevice());
            configs = kernel.getConfigs();
        } catch (ResourceNotFreeException ex) {
            throw new DriverException(ex);
        }
        
        final Device dev = getDevice();
        super.startDevice();
        dev.registerAPI(HardwareCursorAPI.class, kernel);
    }
}
```

**Información Clave:**
- Gets VBE info from GRUB bootloader (UnsafeX86 methods)
- Creates VESACore for handling graphics
- Registers with CursorAPI
- **NO se llama explícitamente para splash - es solo driver de video**

---

## 5. CLASE EXISTENTE: BootSplashGif

**Ubicación:** `gui/src/driver/org/jnode/driver/video/vesa/BootSplashGif.java`

```java
public class BootSplashGif {
    private VESACore surface;
    
    public BootSplashGif(VESACore surface, int width, int height);
    
    public void render() {
        surface.fillRect(0, 0, screenWidth, screenHeight, BACKGROUND_COLOR, PAINT_MODE);
        drawBorderBox();
        drawSimpleSpinner();
    }
    
    private void drawBorderBox() { ... }
    private void drawSimpleSpinner() { ... }  // Líneas rotativas
}
```

**Métodos de VESACore utilizados:**
- `fillRect(x, y, w, h, color, mode)` - Rellena rectángulo
- `drawLine(x1, y1, x2, y2, color, mode)` - Dibuja línea

**Nota:** Esta clase YA EXISTE pero NO se instancia en ningún lugar. 
Es un buen punto de partida para splash gráfico.

---

## 6. ORDEN DE INICIALIZACIÓN DE PLUGINS

Basado en configuración en `conf/`:

1. **Core Plugins (ejecutan primero)**
   - PcTextScreenPlugin (core - SIEMPRE)
   - DeviceFinderPlugin
   - DriverPlugin
   - InputPlugin
   
2. **UI Plugins (si están habilitados)**
   - FbTextScreenPlugin (gui - solo si " fb" en GRUB)
   - AWTPlugin
   - FontPlugin
   
3. **Console Managers**
   - TextScreenConsolePlugin (ÚLTIMO antes de shell)
   - Log4jConfigurePlugin
   
4. **Shell & Desktop**
   - Shell, Desktop, etc.

---

## RECOMENDACIONES Y PUNTOS DE INYECCIÓN

### Opción A: ASCII ART en Boot Log (RECOMENDADO - MÁS SEGURO)

**Ventajas:**
✓ Funciona en TODOS los modos (DEFAULT, FB, VESA)
✓ Garantizado que se ejecuta
✓ Sin dependencias de framebuffer
✓ Se ve claramente en modo texto

**Ubicación:** `TextScreenConsolePlugin.startPlugin()`
```java
protected void startPlugin() throws PluginException {
    try {
        // ... crear manager ...
        
        // AGREGAR AQUÍ: ASCII art splash
        System.out.println(generateAsciiSplash());
        
        System.out.println(VmSystem.getBootLog());
    }
}
```

---

### Opción B: Splash Gráfico en Modo FB

**Ventajas:**
✓ Visual atractivo cuando hay framebuffer
✓ Utiliza clase BootSplashGif existente
✓ Se ejecuta en modo gráfico

**Ubicación:** `FBConsole.startFBConsole()` o `FbTextScreenManager.__init__()`
```java
private static void startFBConsole(Device dev) {
    FbTextScreenManager fbTsMgr = new FbTextScreenManager(api, conf);
    
    // AGREGAR AQUÍ: Mostrar splash gráfico
    // Pero CUIDADO: no interferir con ownership del framebuffer
    try {
        VESADriver vesaDriver = ...;
        new BootSplashGif(vesaDriver.kernel, width, height).render();
    } catch (Exception e) {
        // Silenciosamente ignorar si falla
    }
}
```

---

### Opción C: Splash en Modo DEFAULT (Menos usado)

**Ubicación:** `PcTextScreenPlugin.startPlugin()`
```java
protected void startPlugin() throws PluginException {
    try {
        // Dibujar ASCII art en mgr.getSystemScreen()
        TextScreen screen = mgr.getSystemScreen();
        drawSplashOnScreen(screen);
        
        InitialNaming.bind(PcTextScreenManager.NAME, mgr);
    }
}
```

---

## RESUMEN DE CLASES Y MÉTODOS CLAVE

| Clase | Ubicación | Método de Init | Punto Inyección |
|-------|-----------|----------------|-----------------|
| PcTextScreenPlugin | core/.../x86/ | startPlugin() | Antes de bind |
| FbTextScreenPlugin | gui/.../fb/ | startPlugin() | En FBConsole.start() |
| FbTextScreenManager | gui/.../fb/ | constructor | Después api.open() |
| TextScreenConsolePlugin | core/.../textscreen/ | startPlugin() | Antes boot log |
| VESADriver | gui/.../vesa/ | startDevice() | Driver (no splash directo) |
| BootSplashGif | gui/.../vesa/ | render() | En FbTextScreenManager |

---

## CONCLUSIÓN

**El punto SEGURO más efectivo para inyectar splash es:**

1. **TextScreenConsolePlugin.startPlugin()** - Antes de System.out.println(VmSystem.getBootLog())
   - ASCII art que se vea en todos los modos
   - Ejecuta exactamente cuando la consola está lista
   - No interfiere con ningún driver

2. **Como alternativa gráfica:** FbTextScreenManager constructor - Si hay framebuffer
   - Después de api.open(conf)
   - Llamar BootSplashGif.render()

