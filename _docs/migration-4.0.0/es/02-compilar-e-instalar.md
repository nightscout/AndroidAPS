# Paso 2 · Compilar e instalar la 4.0.0

Compila la 4.0.0 con el mismo método que usaste la primera vez. La wiki distingue cuatro:

| Método original | Qué hacer para la 4.0.0 |
| --- | --- |
| Navegador, **AAPS Builder** | En tu repositorio privado (normalmente `my-aaps`), abre la página *Build AAPS*, elige la versión 4.0.0 y descarga la app desde GitHub. No hay fork que sincronizar. |
| Navegador, **fork + archivo de preparación** | En tu fork (`AndroidAPS` en tu cuenta) sincroniza con el original y ejecuta el workflow **AAPS-CI**. El APK aparece en tu Google Drive. |
| **Android Studio** | Sigue las instrucciones de actualización con Android Studio: trae los cambios del repositorio, cambia a la versión 4.0.0 y genera el APK firmado. |
| **Línea de comandos** | Igual que tu primera compilación por CLI, pero trayendo los cambios nuevos con `git` (por ejemplo `git fetch --tags && git checkout 4.0.0`). |

En el código, la etiqueta `4.0.0` apunta al commit `f77abd9b82` (merge de la PR #5448) y declara `appVersion = "4.0.0"`. La compilación usa Java 21 (`JavaVersion.VERSION_21`) y Gradle 9.8.2; si compilas en local y ves un error de JVM de Gradle, revisa que Android Studio use un JDK 21.

## Firma: el punto que más problemas da

Firma la 4.0.0 con **el mismo keystore y el mismo alias** que la versión instalada. Android solo deja instalar encima si la firma coincide, y es la única forma de conservar la base de datos y los ajustes. Si la firma no coincide, Android te obligará a desinstalar, y eso borra todo lo que hay en la app.

## Instalación

- Instala el APK nuevo **encima** de la versión actual. **No desinstales** primero. La propia wiki lo resume así: para no perder el historial, actualiza siempre, nunca desinstales y reinstales.
- Si usas reloj, actualiza también la app de Wear con el APK de la misma compilación. La app del reloj se ha reescrito; consulta la página "AAPS for Wear OS" de la wiki si algo no conecta.

Siguiente: [Paso 3 · Comprobar e importar](03-verificar-e-importar.md)
