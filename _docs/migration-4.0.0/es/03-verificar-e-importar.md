# Paso 3 · Comprobar la versión e importar si hace falta

## Comprobar la versión

Abre el menú (☰) arriba a la izquierda y entra en **About** (Acerca de). Debe indicar 4.0.0.

## ¿Hay que importar?

Si instalaste encima con el mismo keystore, los ajustes ya están en el teléfono: al arrancar, la 4.0.0 convierte por sí sola los nombres de ajustes de la 3.x a los nuevos. En ese caso **no importes** por costumbre; revisa los ajustes (pasos 5 y 6) y deja la exportación como copia de reserva.

Importa la exportación del paso 1 solo si:

- falta algo después de actualizar, o
- has tenido que instalar desde cero (otro teléfono, keystore nuevo, desinstalación).

Una importación es una **fusión**: escribe los valores del archivo y deja intactos los ajustes que no aparecen en él. No borra nada.

## Cómo importar en la 4.0.0

1. Menú (☰) → **Maintenance** (Mantenimiento). En **File management**, elige el origen con las fichas **Local** o **Cloud** y pulsa **Import settings**. El archivo más reciente sale arriba.
2. Elige el archivo, escribe la contraseña maestra y pulsa **Decrypt & Review**.
3. Decide la casilla **Also replace pump settings** (también reemplazar los ajustes de la bomba):
   - **Sin marcar (por defecto):** se conservan la bomba actual, su emparejamiento y sus ajustes en este teléfono. Es lo correcto si actualizas en el mismo teléfono, sobre todo con un pod en marcha.
   - **Marcada:** solo cuando un teléfono nuevo pasa a controlar la misma bomba.
4. Pulsa **Import**. AAPS pregunta **Apply imported settings?** y no escribe nada hasta que pulsas **OK**. **Cancel** deja el teléfono como estaba.
5. Los ajustes se aplican sin reiniciar la app; solo se reinician los plugins cuyos ajustes cambian. Si la bomba está ocupada, AAPS espera y ofrece reintentar al cabo de un minuto. Cualquier tratamiento que estuviera en cola durante la importación se descarta y AAPS lo informa como cancelado.

## Teléfono nuevo

Instala primero el APK y vuelve a elegir el directorio de AAPS. Si AAPS pierde el acceso al directorio (tras reinstalar o borrar el almacenamiento), muestra un aviso y deja de guardar ajustes hasta que lo vuelvas a seleccionar. En Dana RS tendrás que emparejar a mano el teléfono nuevo con la bomba.

Siguiente: [Paso 4 · La nueva interfaz](04-nueva-interfaz.md)
