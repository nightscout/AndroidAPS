# Paso 6 · Comprobación final y problemas frecuentes

La wiki termina el proceso pidiendo que compruebes que los ajustes están bien y que revises las notas de la versión por si hay instrucciones específicas. Esta lista sirve para hacerlo con orden; compárala con lo que anotaste en el paso 0.

## Lista de comprobación

- [ ] **About** muestra 4.0.0.
- [ ] La bomba correcta está seleccionada en **Configuration** (no la Virtual Pump) y conecta.
- [ ] El perfil activo es el que tenías y sus valores (basales, ISF, ratio, objetivos) coinciden.
- [ ] La insulina activa y su DIA son los esperados (**Manage → Insulin settings**).
- [ ] Los objetivos completados siguen completados.
- [ ] El modo del lazo es el que tenías. Si está en LGS, mira el motivo en el diálogo del lazo (por ejemplo, glucosas duplicadas).
- [ ] Llegan lecturas de glucosa de tu fuente CGM.
- [ ] Nightscout sincroniza con NSClientV3 y token de acceso.
- [ ] Automatizaciones (**Manage → Automation**) y QuickWizards presentes y activos.
- [ ] Los clientes AAPSClient, si los usas, están emparejados.
- [ ] El reloj, si lo usas, conecta y muestra datos.
- [ ] Has hecho una **exportación nueva** desde la 4.0.0 (menú ☰ → Maintenance → Export settings). Si quieres, activa la exportación desatendida en **Settings → Maintenance → Unattended Settings Export** y crea una automatización semanal o tras cada cambio de pod.

## Problemas frecuentes

| Síntoma | Qué hacer |
| --- | --- |
| Android no deja instalar encima | La firma no coincide. Usa el keystore original. Si no lo tienes, consulta "Reinstalling AAPS (new keystore)" en la wiki: implica instalar desde cero e importar el archivo del paso 1. |
| Tras importar aparece la Virtual Pump | El archivo nombraba una bomba que esta versión no tiene. Elige tu bomba en **Configuration** y revisa sus ajustes. |
| No se puede activar un perfil tras importar | Cambia a Virtual Pump en **Configuration → Pump** y después vuelve a tu tipo de bomba. |
| AAPS avisa de que no puede guardar ajustes | Ha perdido el acceso al directorio de AAPS. Vuelve a seleccionarlo. |
| No puedo activar un pod nuevo tras importar | Probablemente importaste un archivo de un pod anterior. Puede que tengas que desactivar o descartar ese pod. Exporta siempre tras activar un pod. |
| No puedo editar el perfil o la insulina | Es la protección. Toca el lápiz ✏️. En la insulina activa, crea una nueva y actívala. |
| Tratamientos metidos en la web de Nightscout no llegan | Con control de clientes activo ya no se aplican. Usa un AAPSClient emparejado. |
| El lazo pasa solo a LGS | Revisa si hay glucosas duplicadas, elimínalas y vuelve a lazo cerrado manteniendo pulsado el icono del lazo. |

Para cualquier otra cosa, la página "Troubleshooting (start here)" de la wiki y la comunidad en Discord.

Volver al [índice](../README.md)
