# Referencia Completa: Clases y Métodos de Inicialización de Video

## 📚 ÍNDICE DE CLASES

### MODO DEFAULT (Text Screen)
1. **PcTextScreenPlugin** - `core/src/driver/org/jnode/driver/textscreen/x86/`
2. **PcTextScreenManager** - `core/src/driver/org/jnode/driver/textscreen/x86/`

### MODO FRAMEBUFFER (Graphics)
3. **FbTextScreenPlugin** - `gui/src/driver/org/jnode/driver/textscreen/fb/`
4. **FBConsole** - `gui/src/driver/org/jnode/driver/textscreen/fb/`
5. **FbTextScreenManager** - `gui/src/driver/org/jnode/driver/textscreen/fb/`
6. **BootSplashGif** - `gui/src/driver/org/jnode/driver/video/vesa/`

### CONSOLE MANAGER (UI Principal)
7. **TextScreenConsolePlugin** - `core/src/driver/org/jnode/driver/console/textscreen/`
8. **TextScreenConsoleManager** - `core/src/driver/org/jnode/driver/console/textscreen/`

### VIDEO DRIVER (Hardware)
9. **VESADriver** - `gui/src/driver/org/jnode/driver/video/vesa/`

---

## 1️⃣ PcTextScreenPlugin

**Archivo:** `core/src/driver/org/jnode/driver/textscreen/x86/PcTextScreenPlugin.java`

### Métodos Públicos
```java
public class PcTextScreenPlugin extends Plugin {
    // Constructor
    public PcTextScreenPlugin(PluginDescriptor descriptor)
    
    // Plugin lifecycle
    protected void startPlugin() throws PluginException
    protected void stopPlugin() throws PluginException
}
```

### Flujo de Ejecución
1. **Instantiation:** Constructor crea `new PcTextScreenManager()`
2. **Plugin Start:** `startPlugin()` bindea manager en JNDI
3. **Manager Available:** Otros plugins pueden usar PcTextScreenManager.NAME

### Datos Miembro
```java
private final PcTextScreenManager mgr;
```

### Punto de Inyección
```java
protected void startPlugin() throws PluginException {
    try {
        // ➜ AQUÍ (antes de bind)
        InitialNaming.bind(PcTextScreenManager.NAME, mgr);
    }
}
```

---

## 2️⃣ PcTextScreenManager

**Archivo:** `core/src/driver/org/jnode/driver/textscreen/x86/PcTextScreenManager.java`

### Métodos Públicos
```java
public class PcTextScreenManager implements TextScreenManager {
    // Interface TextScreenManager
    public TextScreen getSystemScreen()
}
```

### Características
- Acceso a pantalla VGA 80x25
- Sin dependencias gráficas
- Siempre disponible

### JNDI Binding
```java
InitialNaming.bind(PcTextScreenManager.NAME, mgr);
// NAME = "PCTextScreenManager" (probablemente)
```

---

## 3️⃣ FbTextScreenPlugin

**Archivo:** `gui/src/driver/org/jnode/driver/textscreen/fb/FbTextScreenPlugin.java`

### Métodos Públicos
```java
public final class FbTextScreenPlugin extends Plugin {
    public FbTextScreenPlugin(PluginDescriptor descriptor)
    protected void startPlugin() throws PluginException
    protected void stopPlugin() throws PluginException
}
```

### Flujo de Ejecución
1. **Check GRUB:** `VmSystem.getCmdLine().indexOf(" fb") < 0`
2. **Start Thread:** Si " fb" presente, inicia thread para FBConsole.start()
3. **Async Initialization:** No bloquea plugin loading

### Condición de Ejecución
```java
protected void startPlugin() throws PluginException {
    if (VmSystem.getCmdLine().indexOf(" fb") < 0)
        return;  // ← NO se ejecuta si no hay " fb"
    
    Thread t = new Thread() {
        public void run() {
            FBConsole.start();  // ← Inicia console gráfica
        }
    };
    t.start();
}
```

### Punto de Inyección
```
En FBConsole.start() → Línea 44-50
En FBConsole.startFBConsole() → Línea 67 (después de crear FbTextScreenManager)
```

---

## 4️⃣ FBConsole

**Archivo:** `gui/src/driver/org/jnode/driver/textscreen/fb/FBConsole.java`

### Métodos Públicos/Estáticos
```java
class FBConsole {  // package-private
    public static void start() throws Exception
    private static void startFBConsole(Device dev)
}
```

### start() - Flujo
```java
public static void start() throws Exception {
    final Collection<Device> devs = 
        DeviceUtils.getDevicesByAPI(FrameBufferAPI.class);
    
    if (dev_count > 0) {
        startFBConsole(devs.iterator().next());  // Ya hay device
    } else {
        // Espera a que se registre device
        DeviceUtils.getDeviceManager().addListener(
            new DeviceManagerListener() {
                public void deviceRegistered(Device device) {
                    if (device.implementsAPI(FrameBufferAPI.class)) {
                        startFBConsole(device);  // ← Ejecuta cuando device disponible
                    }
                }
            }
        );
    }
}
```

### startFBConsole(Device dev) - Flujo
```java
private static void startFBConsole(Device dev) {
    FbTextScreenManager fbTsMgr = null;
    try {
        final FrameBufferAPI api = dev.getAPI(FrameBufferAPI.class);
        final FrameBufferConfiguration conf = api.getConfigurations()[0];
        
        fbTsMgr = new FbTextScreenManager(api, conf);  // ← Inicializa gráficos
        
        // ➜ PUNTO DE INYECCIÓN AQUÍ (después de crear manager)
        
        InitialNaming.unbind(TextScreenManager.NAME);
        InitialNaming.bind(TextScreenManager.NAME, fbTsMgr);  // ← Manager disponible
    } catch (Throwable ex) {
        log.error("Error in FBConsole", ex);
        if (fbTsMgr != null) {
            fbTsMgr.ownershipLost();  // Cleanup si falla
        }
    }
}
```

### Punto de Inyección
```
Línea 61-71: startFBConsole()
├─ Línea 67: Después de new FbTextScreenManager(api, conf)
│  ├─ Surface ya está abierta
│  └─ Ownership establecido
└─ Antes de InitialNaming.bind()
   └─ Antes de que otros plugins usen manager
```

---

## 5️⃣ FbTextScreenManager

**Archivo:** `gui/src/driver/org/jnode/driver/textscreen/fb/FbTextScreenManager.java`

### Métodos Públicos
```java
final class FbTextScreenManager 
    implements TextScreenManager, FrameBufferAPIOwner {
    
    // Constructor
    FbTextScreenManager(FrameBufferAPI api, FrameBufferConfiguration conf)
    
    // Interface TextScreenManager
    public TextScreen getSystemScreen()
    
    // Interface FrameBufferAPIOwner  
    public void ownershipLost()
    public void ownershipGained()
}
```

### Constructor - Flujo Detallado
```java
FbTextScreenManager(FrameBufferAPI api, FrameBufferConfiguration conf) 
    throws UnknownConfigurationException, AlreadyOpenException, 
           DeviceException {
    
    final Font font = conf.getScreenWidth() > 800 ? FONT_LARGE : FONT_SMALL;
    final FontMetrics fm = getFontManager().getFontMetrics(font);
    final int w = fm.getMaxAdvance();
    final int h = fm.getHeight();

    final int nbColumns = 80;      // ← Text screen es siempre 80 cols
    final int nbRows = 25;         // ← Y 25 rows

    // Centrar consola en pantalla
    final int consoleWidth = w * nbColumns;
    final int consoleHeight = h * nbRows;
    final int xOffset = (conf.getScreenWidth() - consoleWidth) / 2;
    final int yOffset = (conf.getScreenHeight() - consoleHeight) / 2;
    
    BufferedImage bufferedImage = new BufferedImage(
        consoleWidth, consoleHeight, BufferedImage.TYPE_INT_ARGB);
    Graphics graphics = bufferedImage.getGraphics();
    
    api.requestOwnership(this);      // ← Pide ownership
    surface = api.open(conf);        // ← SURFACE DISPONIBLE AQUÍ
    this.conf = conf;

    clearScreen();                   // ← Limpia con negro

    // ➜ PUNTO DE INYECCIÓN: Entre api.open() y clearScreen()
    
    systemScreen = new FbTextScreen(surface, bufferedImage, graphics, 
                                    font, nbColumns, nbRows, 
                                    xOffset, yOffset);
}
```

### Datos Miembro
```java
private final FbTextScreen systemScreen;
private final Surface surface;          // ← Para dibujar splash
private FrameBufferConfiguration conf;  // ← Ancho/alto
```

### Métodos de Ownership
```java
public void ownershipLost() {
    if (systemScreen != null) {
        clearScreen();
        systemScreen.close();  // ← Pierde control de framebuffer
    }
}

public void ownershipGained() {
    if (systemScreen != null) {
        clearScreen();
        systemScreen.open();   // ← Recupera control
    }
}
```

### Punto de Inyección
```
En constructor, línea ~90:
├─ Después: api.requestOwnership(this) + surface = api.open(conf)
├─ Antes: clearScreen() + systemScreen = new FbTextScreen()
└─ Acceso a:
   ├─ Surface surface (para dibujar)
   ├─ FrameBufferConfiguration conf (dimensiones)
   └─ VESACore (si es VESA driver)
```

---

## 6️⃣ BootSplashGif

**Archivo:** `gui/src/driver/org/jnode/driver/video/vesa/BootSplashGif.java`

### Métodos Públicos
```java
public class BootSplashGif {
    // Constructor
    public BootSplashGif(VESACore surface, int width, int height)
    
    // Main method
    public void render()
    
    // Private drawing methods
    private void drawBorderBox()
    private void drawSimpleSpinner()
}
```

### Constructor
```java
public BootSplashGif(VESACore surface, int width, int height) {
    this.surface = surface;
    this.screenWidth = width;
    this.screenHeight = height;
}
```

### render() - Flujo
```java
public void render() {
    try {
        System.out.println("[BootSplash] Rendering splash screen");
        
        // Rellena fondo
        surface.fillRect(0, 0, screenWidth, screenHeight, 
                        BACKGROUND_COLOR, PAINT_MODE);
        
        drawBorderBox();      // Marco decorativo
        drawSimpleSpinner();  // Animación de carga
        
        System.out.println("[BootSplash] Splash complete");
    } catch (Exception e) {
        System.out.println("[BootSplash] Error: " + e.getMessage());
    }
}
```

### Métodos de Dibujo Disponibles
```java
surface.fillRect(x, y, width, height, color, mode)
surface.drawLine(x1, y1, x2, y2, color, mode)
// Potencialmente: drawImage(), fill(), etc.
```

### Colores Predefinidos
```java
private static final int BACKGROUND_COLOR = 0x001a1a3e;  // Dark blue
private static final int BORDER_COLOR = 0x00FF6600;      // Orange
private static final int SPINNER_COLOR = 0x00FFFFFF;     // White
```

### Nota Importante
```
⚠️ BootSplashGif YA EXISTE pero NO se instancia en ningún lugar
   → Es disponible para usar en FbTextScreenManager
   → Modificación mínima necesaria
```

---

## 7️⃣ TextScreenConsolePlugin ⭐ (RECOMENDADO)

**Archivo:** `core/src/driver/org/jnode/driver/console/textscreen/TextScreenConsolePlugin.java`

### Métodos Públicos
```java
public class TextScreenConsolePlugin extends Plugin {
    public TextScreenConsolePlugin(PluginDescriptor descriptor)
    protected void startPlugin() throws PluginException
    protected void stopPlugin() throws PluginException
}
```

### startPlugin() - Flujo Completo
```java
protected void startPlugin() throws PluginException {
    try {
        // 1. Crear manager
        mgr = new TextScreenConsoleManager();
        
        // 2. Registrar en JNDI
        InitialNaming.bind(ConsoleManager.NAME, mgr);

        // 3. Crear primera consola
        final TextConsole first = (TextConsole) mgr.createConsole(
            null,
            (ConsoleManager.CreateOptions.TEXT |
             ConsoleManager.CreateOptions.SCROLLABLE));
        
        // 4. Configurar tecla de acceso
        first.setAcceleratorKeyCode(KeyEvent.VK_F1);
        mgr.focus(first);
        
        // 5. Setup System.out/err
        System.setOut(new PrintStream(
            new WriterOutputStream(first.getOut(), false), true));
        System.setErr(new PrintStream(
            new WriterOutputStream(first.getErr(), false), true));
        
        // ➜ PUNTO DE INYECCIÓN AQUÍ (antes de println)
        // System.out.println(SPLASH_ASCII_ART);
        
        // 6. Imprimir boot log
        System.out.println(VmSystem.getBootLog());
        
    } catch (ConsoleException ex) {
        throw new PluginException(ex);
    } catch (NamingException ex) {
        throw new PluginException(ex);
    }
}
```

### Punto de Inyección - RECOMENDADO
```
Línea 62-66 (antes de System.out.println(VmSystem.getBootLog())):

VENTAJAS:
✓ Garantizado que se ejecuta (TODOS los modos)
✓ Consola ya está lista
✓ Antes del boot log
✓ Sin dependencias de drivers gráficos
✓ Timing perfecto (última cosa visible antes de shell)
```

---

## 8️⃣ TextScreenConsoleManager

**Archivo:** `core/src/driver/org/jnode/driver/console/textscreen/TextScreenConsoleManager.java`

### Métodos Públicos
```java
public class TextScreenConsoleManager extends AbstractConsoleManager {
    public TextScreenConsoleManager() throws ConsoleException
    public TextScreenConsole createConsole(String name, int options)
    protected TextScreenManager getTextScreenManager()
}
```

### Constructor - Flujo
```java
public TextScreenConsoleManager() throws ConsoleException {
    // Escucha cambios en TextScreenManager
    InitialNaming.addNameSpaceListener(
        TextScreenManager.NAME, 
        new NameSpaceListener<TextScreenManager>() {
            public void serviceBound(TextScreenManager service) {
                final TextScreen systemScreen = service.getSystemScreen();
                
                // Notificar a todas las consolas
                for (Console c : getConsoles()) {
                    TextScreenConsole console = (TextScreenConsole) c;
                    final TextScreen screen;
                    
                    if ((console.getOptions() & CreateOptions.SCROLLABLE) != 0) {
                        screen = systemScreen.createCompatibleScrollableBufferScreen(
                            SCROLLABLE_HEIGHT);
                    } else {
                        screen = systemScreen.createCompatibleBufferScreen();
                    }
                    console.systemScreenChanged(screen);
                }
            }

            public void serviceUnbound(TextScreenManager service) {
                // nothing
            }
        }
    );
}
```

### Características
- Escucha registro de TextScreenManager
- Crea consolas compatible con TextScreen
- Crea consolas scrollables (500 líneas default)

---

## 9️⃣ VESADriver

**Archivo:** `gui/src/driver/org/jnode/driver/video/vesa/VESADriver.java`

### Métodos Públicos
```java
public class VESADriver extends AbstractFrameBufferDriver {
    public VESADriver()
    
    public FrameBufferConfiguration[] getConfigurations()
    public FrameBufferConfiguration getCurrentConfiguration()
    public synchronized Surface open(FrameBufferConfiguration config)
    public synchronized Surface getCurrentSurface()
    public final synchronized boolean isOpen()
    
    protected void startDevice() throws DriverException
    protected void stopDevice() throws DriverException
}
```

### startDevice() - Flujo (Principal)
```java
protected void startDevice() throws DriverException {
    ModeInfoBlock modeInfoBlock = null;
    try {
        // 1. Obtener info de VBE desde GRUB (bootloader)
        Address vbeControlInfo = UnsafeX86.getVbeControlInfos();
        VbeInfoBlock vbeInfoBlock = new VbeInfoBlock(vbeControlInfo);
        if (vbeInfoBlock.isEmpty()) {
            throw new DriverException(
                "can't start device (vbeInfoBlock is empty): " +
                "grub haven't switched to graphic mode");
        }

        // 2. Obtener modo info
        Address vbeModeInfo = UnsafeX86.getVbeModeInfos();
        modeInfoBlock = new ModeInfoBlock(vbeModeInfo);
        if (modeInfoBlock.isEmpty()) {
            throw new DriverException(
                "can't start device (modeInfoBlock is empty): " +
                "grub haven't switched to graphic mode");
        }

        // 3. Crear core de VESA
        kernel = new VESACore(this, vbeInfoBlock, modeInfoBlock, 
                             (PCIDevice) getDevice());
        configs = kernel.getConfigs();  // ← Configuraciones disponibles
        
    } catch (ResourceNotFreeException ex) {
        throw new DriverException(ex);
    } catch (Throwable t) {
        throw new DriverException(t);
    }
    
    // 4. Registrar driver
    final Device dev = getDevice();
    super.startDevice();
    dev.registerAPI(HardwareCursorAPI.class, kernel);
}
```

### Nota Importante
```
⚠️ VESADriver es SOLO un driver, NO tiene lógica de splash
   → BootSplashGif se usa ANTES que VESADriver.startDevice()
   → La interacción ocurre a través de TextScreen managers
```

---

## 🔄 DIAGRAMA DE INICIALIZACIÓN

```
BOOT
  ↓
┌─────────────────────────────────────┐
│ PcTextScreenPlugin.startPlugin()    │
│  └─ Crea PcTextScreenManager        │
│  └─ Bindea en JNDI                  │
└─────────────────────────────────────┘
  ↓
┌─────────────────────────────────────┐
│ FbTextScreenPlugin.startPlugin()    │ (si " fb" en GRUB)
│  └─ Inicia thread                   │
│  └─ Llama FBConsole.start()         │
│     └─ Espera FrameBufferAPI device │
│        └─ Crea FbTextScreenManager  │
│           └─ Abre Surface           │
│           └─ Bindea en JNDI         │
└─────────────────────────────────────┘
  ↓
┌─────────────────────────────────────────────────────┐
│ TextScreenConsolePlugin.startPlugin()               │
│  └─ Crea TextScreenConsoleManager                   │
│  └─ Bindea en JNDI (ConsoleManager.NAME)            │
│  └─ Crea primera consola TEXT|SCROLLABLE            │
│  ➜ MEJOR PUNTO PARA INYECTAR SPLASH AQUÍ            │
│  └─ Imprime VmSystem.getBootLog()                   │
└─────────────────────────────────────────────────────┘
  ↓
SHELL Y APLICACIONES
```

---

## 📋 TABLA RESUMEN

| Clase | Archivo | Método Key | Inyección | Tipo |
|-------|---------|-----------|-----------|------|
| PcTextScreenPlugin | core/.../x86/ | startPlugin() | Antes bind | DEFAULT |
| FbTextScreenPlugin | gui/.../fb/ | startPlugin() | En thread | FB (init) |
| FBConsole | gui/.../fb/ | startFBConsole() | Línea 67 | FB (init) |
| FbTextScreenManager | gui/.../fb/ | constructor | Línea 90 | FB (gráfico) |
| BootSplashGif | gui/.../vesa/ | render() | On-demand | Gráfico |
| TextScreenConsolePlugin | core/.../textscreen/ | **startPlugin()** | **Línea 62** | **⭐ RECOMENDADO** |
| TextScreenConsoleManager | core/.../textscreen/ | constructor | Escucha eventos | Console |
| VESADriver | gui/.../vesa/ | startDevice() | NO usar | Driver only |

