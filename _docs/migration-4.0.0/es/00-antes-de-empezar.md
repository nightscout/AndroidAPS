# Paso 0 · Antes de empezar

La 4.0.0 se publicó el 31-10-2026. Es la versión con más cambios en años: la app del teléfono y la del reloj se han reescrito en Jetpack Compose, la navegación por pestañas desaparece y varias funciones cambian de sitio o de nombre. Merece la pena prepararlo con calma.

## Requisitos

- **Android 12 o superior** en el teléfono. Es el mínimo desde AAPS 3.4 y el código de la 4.0.0 lo mantiene (`minSdk = 31` en `buildSrc/src/main/kotlin/Versions.kt`).
- **Wear OS 3.0 o superior** (API 30) si usas reloj. Wear OS 2 no está soportado.
- **Nightscout 15 o superior con API v3** y un **token de acceso** (no el API secret antiguo). El cliente NSClient v1 se ha eliminado y solo queda NSClientV3.
- **El mismo almacén de claves (keystore)** con el que firmaste la versión que tienes instalada. Sin él no podrás instalar encima y perderás la base de datos.
- La **contraseña maestra** de AAPS (Ajustes → Protección). Sin ella no se puede importar una exportación.

## Lecturas obligatorias según la wiki

1. Las notas de la versión 4.0.0.
2. La página "What changed in AAPS v4", que lista lo que se movió, se renombró o se eliminó.

Si te queda alguna duda, la wiki recomienda resolverla con la comunidad (Discord o Facebook) antes de actualizar.

## Recomendaciones prácticas (no son pasos oficiales)

- Elige un momento tranquilo, con tiempo para revisar ajustes, no justo antes de dormir ni de salir.
- Ten a mano material de repuesto y la forma de administrar insulina manualmente por si algo falla.
- Si usas Omnipod u otra bomba sin tubo, hacerlo justo después de un cambio de pod simplifica las cosas: la exportación del paso 1 contendrá los datos del pod activo.
- Anota cómo tienes ahora el modo del lazo, el perfil activo y los objetivos completados. Te servirá para comprobar el resultado.

Siguiente: [Paso 1 · Exportar los ajustes](01-exportar-ajustes.md)
