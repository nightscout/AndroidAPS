# Paso 1 · Exportar los ajustes en la versión 3.x

La wiki pide exportar los ajustes antes de actualizar. Si la instalación sale bien no la necesitarás, porque los ajustes siguen en el teléfono, pero es tu única copia de seguridad: los ajustes se guardan cifrados y ni la copia de Android ni el traspaso entre teléfonos los incluyen.

## Qué hacer, todavía en la 3.x

1. Comprueba que tienes una contraseña maestra en **Ajustes → Protección** y que la recuerdas. El archivo se cifra con ella.
2. Abre **Mantenimiento** (en la 3.x, desde el menú de la esquina superior) y pulsa **Exportar ajustes**. Introduce la contraseña maestra.
3. El archivo queda en `Almacenamiento interno/AAPS/preferences/`, con un nombre del tipo `AAAA-MM-DD_hora_nombreapp.json`.
4. Copia ese archivo fuera del teléfono: a un ordenador y a un almacenamiento en la nube.

## Qué guarda la exportación

Según la wiki: los ajustes de configuración, el estado de los objetivos, la conexión con Nightscout y Tidepool, las automatizaciones, los perfiles y, en bombas sin tubo (Omnipod, Medtrum), los datos de conexión del pod activo. En Dana RS guarda la conexión de la bomba, que solo se aplica si al importar marcas que quieres reemplazar los ajustes de la bomba.

## Qué no guarda

- El historial de tratamientos ni la base de datos de AAPS. La base de datos se conserva al actualizar porque instalas encima con el mismo keystore.
- El APK ni el keystore. Haz copia de ambos por separado (en especial del archivo keystore y sus contraseñas).

## Avisos

- **Objetivos:** sin una exportación, perder los ajustes significa repetir todos los objetivos para volver a tener lazo cerrado.
- **Pods:** exporta justo después de cada activación de pod. Importar un archivo de un pod anterior puede dejar a AAPS intentando hablar con un pod viejo e impedirte activar uno nuevo.

Siguiente: [Paso 2 · Compilar e instalar](02-compilar-e-instalar.md)
