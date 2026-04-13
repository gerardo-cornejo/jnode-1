# Guía Rápida: Dónde Inyectar Splash de Boot en JNode

## 📍 UBICACIONES DE CÓDIGO ESPECÍFICAS

### 1️⃣ OPCIÓN RECOMENDADA - ASCII Splash en TextScreenConsolePlugin

**Archivo:** `core/src/driver/org/jnode/driver/console/textscreen/TextScreenConsolePlugin.java`

**Líneas de Inyección:**
- Línea 53: Método `startPlugin()`
- Línea 62-66: ANTES de `System.out.println(VmSystem.getBootLog())`

**Código Actual:**
```java
protected void startPlugin() throws PluginException {
    try {
        mgr = new TextScreenConsoleManager();
        InitialNaming.bind(ConsoleManager.NAME, mgr);

        final TextConsole first = (TextConsole) mgr.createConsole(
            null,
            (ConsoleManager.CreateOptions.TEXT |
                ConsoleManager.CreateOptions.SCROLLABLE));
        first.setAcceleratorKeyCode(KeyEvent.VK_F1);
        mgr.focus(first);
        System.setOut(new PrintStream(new WriterOutputStream(first.getOut(), false), true));
        System.setErr(new PrintStream(new WriterOutputStream(first.getErr(), false), true));
        
        // ➜ INSERTAR AQUÍ: Splash ASCII art
        // System.out.println(SPLASH_ASCII_ART);
        
        System.out.println(VmSystem.getBootLog());
    } catch (ConsoleException ex) {
        throw new PluginException(ex);
    }
}
```

**Ventajas:**
✅ Funciona en TODOS los modos (DEFAULT, FB, VESA)
✅ Se ve ANTES del boot log
✅ 100% seguro - no interfiere con drivers
✅ Simple de implementar

---

### 2️⃣ ALTERNATIVA - Splash Gráfico en Modo Framebuffer

**Archivo:** `gui/src/driver/org/jnode/driver/textscreen/fb/FBConsole.java`

**Líneas de Inyección:**
- Línea 61-71: Método `startFBConsole(Device dev)`
- **OPCIÓN A:** Línea 67 - Después de crear `fbTsMgr`
- **OPCIÓN B:** Dentro de `FbTextScreenManager.__init__()` - Línea 90

**Código Actual:**
```java
private static void startFBConsole(Device dev) {
    FbTextScreenManager fbTsMgr = null;
    try {
        final FrameBufferAPI api = dev.getAPI(FrameBufferAPI.class);
        final FrameBufferConfiguration conf = api.getConfigurations()[0];

        fbTsMgr = new FbTextScreenManager(api, conf);  // ← Aquí se abre Surface
        
        // ➜ INSERTAR AQUÍ (opción):
        // if (fbTsMgr.getCurrentSurface() != null) {
        //     new BootSplashGif(fbTsMgr, conf.getScreenWidth(), 
        //                       conf.getScreenHeight()).render();
        // }
        
        InitialNaming.unbind(TextScreenManager.NAME);
        InitialNaming.bind(TextScreenManager.NAME, fbTsMgr);
    } catch (Throwable ex) {
        log.error("Error in FBConsole", ex);
    }
}
```

**Alternativa - En FbTextScreenManager:**

**Archivo:** `gui/src/driver/org/jnode/driver/textscreen/fb/FbTextScreenManager.java`

```java
FbTextScreenManager(FrameBufferAPI api, FrameBufferConfiguration conf) 
    throws UnknownConfigurationException, AlreadyOpenException, DeviceException {
    
    // ... código de inicialización ...
    
    api.requestOwnership(this);
    surface = api.open(conf);  // ← Surface disponible AQUÍ
    this.conf = conf;

    // ➜ INSERTAR AQUÍ:
    // renderBootSplash();
    
    clearScreen();
    systemScreen = new FbTextScreen(...);
}

private void renderBootSplash() {
    try {
        // Acceso a surface y conf para dibujar splash
        BootSplashGif splash = new BootSplashGif((VESACore)surface, 
                                                  conf.getScreenWidth(), 
                                                  conf.getScreenHeight());
        splash.render();
    } catch (Exception e) {
        // Log silencioso - no bloquear boot
    }
}
```

**Ventajas:**
✅ Visual - muestra splash gráfico elegante
✅ Utiliza código existente (BootSplashGif)
✅ Solo en modo FB (cuando hay gráficos)

---

### 3️⃣ ALTERNATIVA - En Inicialización de PcTextScreen (DEFAULT mode)

**Archivo:** `core/src/driver/org/jnode/driver/textscreen/x86/PcTextScreenPlugin.java`

**Líneas de Inyección:**
- Línea 45-50: Método `startPlugin()`

**Código Actual:**
```java
protected void startPlugin() throws PluginException {
    try {
        // ➜ INSERTAR ANTES DE BIND:
        // drawAsciiSplashToScreen(mgr.getSystemScreen());
        
        InitialNaming.bind(PcTextScreenManager.NAME, mgr);
    } catch (NamingException ex) {
        throw new PluginException(ex);
    }
}
```

---

## 🎯 RESUMEN DE MÉTODOS CLAVE POR UBICACIÓN

### Método 1: TextScreenConsolePlugin.startPlugin()
**Archivo:** `core/src/driver/org/jnode/driver/console/textscreen/TextScreenConsolePlugin.java:55`

**Flujo:**
1. Crea TextScreenConsoleManager
2. Bindea en JNDI
3. Crea primer console
4. **← AQUÍ INSERTAR SPLASH ANTES DEL BOOTLOG**
5. Imprime boot log

---

### Método 2: FBConsole.startFBConsole()
**Archivo:** `gui/src/driver/org/jnode/driver/textscreen/fb/FBConsole.java:61`

**Flujo:**
1. Obtiene FrameBufferAPI
2. Crea FbTextScreenManager
3. **← AQUÍ INSERTAR SPLASH GRÁFICO**
4. Bindea manager

---

### Método 3: FbTextScreenManager.__init__()
**Archivo:** `gui/src/driver/org/jnode/driver/textscreen/fb/FbTextScreenManager.java:75`

**Flujo:**
1. Abre surface gráfica
2. **← AQUÍ INSERTAR SPLASH GRÁFICO**
3. Limpia pantalla
4. Crea system screen

---

### Método 4: PcTextScreenPlugin.startPlugin()
**Archivo:** `core/src/driver/org/jnode/driver/textscreen/x86/PcTextScreenPlugin.java:45`

**Flujo:**
1. Manager ya creado en constructor
2. **← AQUÍ INSERTAR SPLASH ANTES DE BINDEAR**
3. Bindea manager

---

## 📊 TABLA COMPARATIVA DE OPCIONES

| OPCIÓN | UBICACIÓN | TIPO | GARANTIZADO | COMPLEJIDAD | RECOMENDA |
|--------|-----------|------|-----------|------------|-----------|
| ASCII en Console | TextScreenConsolePlugin | Texto | SÍ - 100% | Baja | ⭐⭐⭐ |
| Gráfico en FB | FbTextScreenManager | Gráfico | Conditonal | Media | ⭐⭐ |
| ASCII en DEFAULT | PcTextScreenPlugin | Texto | SÍ - 100% | Media | ⭐ |
| Combo (ASCII+Gráfico) | Ambas | Híbrido | SÍ - 100% | Alta | ⭐⭐⭐ |

---

## 🔄 ORDEN DE EJECUCIÓN DE PLUGINS

```
1. PcTextScreenPlugin.startPlugin()          [core - SIEMPRE]
   └─ Bindea PcTextScreenManager
   
2. FbTextScreenPlugin.startPlugin()          [gui - SI " fb" EN GRUB]
   └─ Inicia FBConsole.start()
      └─ Crea FbTextScreenManager
      
3. TextScreenConsolePlugin.startPlugin()     [core - SIEMPRE]
   └─ Crea TextScreenConsoleManager
   └─ Crea primer console
   └─ Imprime VmSystem.getBootLog()           ← MEJOR PUNTO PARA ASCII
   
4. Log4jConfigurePlugin, AWTPlugin, etc.
```

---

## 🚀 PASOS PARA IMPLEMENTAR

### Paso 1: Crear clase SplashPrinter (nuevo archivo)

**Ubicación:** `core/src/driver/org/jnode/driver/console/textscreen/BootSplashPrinter.java`

```java
package org.jnode.driver.console.textscreen;

public class BootSplashPrinter {
    public static void printAsciiSplash(PrintStream out) {
        out.println("╔════════════════════════════════════════════════════╗");
        out.println("║         ╔════╗██████╗ ███████╗███████╗            ║");
        out.println("║         ║    ║██╔═══╝██╔════╝██╔════╝            ║");
        out.println("║         ║    ║██║    ██║     ███████╗            ║");
        out.println("║         ║    ║██║    ██║     ╚════██║            ║");
        out.println("║         ╚════╝╚██████╝╚██████╝███████║            ║");
        out.println("║            Pure Java Operating System             ║");
        out.println("╚════════════════════════════════════════════════════╝");
        out.println();
    }
}
```

### Paso 2: Modificar TextScreenConsolePlugin

```java
protected void startPlugin() throws PluginException {
    try {
        mgr = new TextScreenConsoleManager();
        InitialNaming.bind(ConsoleManager.NAME, mgr);

        final TextConsole first = (TextConsole) mgr.createConsole(
            // ... opciones ...
        );
        // ... configuración ...
        
        System.setOut(new PrintStream(...));
        System.setErr(new PrintStream(...));
        
        // ✓ INSERTAR AQUÍ:
        BootSplashPrinter.printAsciiSplash(System.out);
        
        System.out.println(VmSystem.getBootLog());
    }
}
```

---

## 📝 NOTAS IMPORTANTES

1. **BootLog es de VmSystem:** Se obtiene con `VmSystem.getBootLog()`
   - Contiene timestamp y build info
   - Se imprime ÚLTIMA en el proceso

2. **TextScreenConsolePlugin es el ÚLTIMO punto seguro:**
   - Se ejecuta cuando consola está lista
   - Antes de shell y aplicaciones
   - Garantizado funcionamiento

3. **No interferir con ownership de framebuffer:**
   - FrameBufferAPIOwner controla Surface
   - Dibujar splash DENTRO del ownership
   - No hacer operaciones gráficas fuera

4. **Alternativas experimentales:**
   - VESADriver.startDevice() - NO es punto de splash (solo driver)
   - SwingTextScreenManager - Para testing, no producción

---

## 🔍 VERIFICACIÓN

Para verificar que splash se muestra correctamente:

1. Compilar y hacer boot CD/imagen
2. Verificar en modo QEMU/VirtualBox
3. Línea de comando: `./qemu.sh` o `./qemu.bat`
4. Kernel debe mostrar splash ANTES de shell

