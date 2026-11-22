# Paso 5 · Ajustes obligatorios después de actualizar

Estos son los pasos que la página oficial "What changed in AAPS v4" marca para después de la actualización. Varios afectan directamente a la seguridad del lazo.

## 1. Nightscout (NSClientV3)

NSClient v1 ya no existe. Ve a **Configuration → Communication → NSClientV3**:

- Escribe la URL de tu Nightscout y un **token de acceso**. El API secret antiguo ya no sirve.
- Confirma que tu Nightscout es la versión 15 o posterior, con API v3.
- **Websockets** debe estar configurado igual en el teléfono principal y en los seguidores. Si usas control remoto con AAPSClient, es obligatorio en ambos.

## 2. Insulina

Los registros antiguos que tenían insulina pero no DIA reciben un DIA por defecto (por ejemplo, 9,0 horas para Lyumjev), y los ya migrados no se vuelven a tocar. Si ves el aviso de migración sobre un DIA por defecto, revisa **Manage → Insulin settings**. Para cambiar DIA o pico, crea una insulina nueva y actívala (la activa no se edita).

## 3. AAPSClient (teléfonos de cuidadores)

En la 4.0.0, los clientes envían órdenes firmadas, pueden administrar un bolo y cada uno tiene un rol (solo lectura, comidas, cuidador, administrador). Empareja cada cliente con el teléfono principal: con un token de administrador en el principal, un código QR configura y empareja el cliente en un solo escaneo. Con el control de clientes activado (modo reforzado), los tratamientos escritos directamente en la web de Nightscout **ya no se aplican**; hay que introducirlos desde un cliente emparejado.

## 4. Lecturas de glucosa duplicadas

Antes, con lecturas duplicadas el lazo seguía en lazo cerrado sin corregir. Ahora pasa a **Low Glucose Suspend (LGS)** y el diálogo del lazo muestra el motivo. Elimina los duplicados (ver el aviso de calidad de glucosa) y vuelve a lazo cerrado a mano: mantén pulsado el icono del lazo y elige **Closed Loop**.

## 5. Tidepool

Normalmente no hay que hacer nada: los datos nuevos aparecen bajo el dispositivo **AAPS** y empieza un conjunto de datos nuevo. **No ejecutes "Full synchronization"** mientras sigan los datos antiguos en Tidepool, o se duplicarán. Si quieres todo bajo el nombre nuevo, primero **Purge all data** en la pantalla de Tidepool y después "Full synchronization"; solo se puede volver a subir lo que aún esté en el teléfono.

## 6. Protección

Si quieres editar perfiles, insulina, objetivos temporales o QuickWizards con la protección activa, toca el lápiz ✏️. Para quitar el bloqueo del todo, **Settings → Protection**.

## Además, según tu equipo

- **Bomba:** si al importar el archivo nombraba una bomba que esta versión no tiene, AAPS cae en la Virtual Pump. Vuelve a elegir tu bomba en **Configuration** y revisa sus ajustes.
- **Omnipod DASH:** la compensación de deriva está siempre activa en la 4.0.0.
- **Reloj:** en Wear OS 6 o posterior, la esfera se instala sola (Watch Face Push).

Siguiente: [Paso 6 · Comprobación final](06-comprobacion-final.md)
