# Paso 4 · La nueva interfaz: dónde está cada cosa

En la 4.0.0 desaparece la fila de pestañas. La pantalla principal es **Overview** y abajo hay una barra con tres hojas: **Treatments** (tratamientos), **Scenes** (escenas) y **Manage** (gestionar). Los nombres van en inglés porque así aparecen en la wiki; la traducción en la app puede variar.

## Equivalencias 3.x → 4.0.0

| Antes (3.x) | Ahora (4.0.0) |
| --- | --- |
| Fila de pestañas (Overview, Actions, Treatments…) | Overview como pantalla principal + barra inferior Treatments / Scenes / Manage |
| Config Builder | Se llama **Configuration** |
| Casilla del ojo para mostrar un plugin como pestaña | Eliminada |
| Pestaña y plugin **Actions** | Ya no existe; sus funciones están repartidas (ver abajo) |
| Plugin **Automation** | Ya no es plugin; está en **Manage** |
| Plugin **Food** | Ya no es plugin; está en **Manage** |
| Pestaña **Treatments** | Hoja **Treatments** de la barra inferior |
| Historial (History browser) y estadísticas (TDD) | Menú (☰) |

**Hoja Treatments:** asistente de bolo, insulina (bolo manual), carbohidratos, tratamiento (insulina y carbohidratos juntos), calibración, CGM (abre tu app de CGM si está configurada) y QuickWizards.

**Hoja Manage:** perfil, ajustes de insulina, objetivo temporal, presets de QuickWizard, escenas, automatización, comida, rotación de zonas, bomba, clientes autorizados y las acciones de bomba de la antigua pestaña Actions (bolo extendido, cancelar basal temporal, cebado/llenado, inserción de sensor, cambio de batería).

## Dónde están las funciones de la antigua pestaña Actions

| Función | 3.x | 4.0.0 |
| --- | --- | --- |
| Cambio de perfil y porcentaje | Actions, o pulsación larga en el nombre del perfil | Toca el nombre del perfil en la pantalla principal y luego el botón redondo ▶ (abajo a la derecha), o Manage → Profile |
| Objetivo temporal | Actions | Toca el objetivo en la pantalla principal, o Manage → Temp Target |
| Cebado/llenado, inserción de sensor, control de glucosa, otras entradas | Actions | Panel de estado desplegado, Manage o botones QuickLaunch |

## Cambios de comportamiento que conviene saber

- **El gesto de cambio de perfil cambia:** la pulsación larga ya no abre el diálogo. Ahora es tocar el nombre y después ▶.
- **La insulina activa no se edita:** la marcada como ACTIVE aparece en gris y no se puede editar ni borrar. Para cambiar DIA o pico, añade una insulina nueva con +, ajústala, guárdala y pulsa ▶ Activate.
- **La protección cubre más pantallas:** además de los ajustes, bloquea Profile, Insulin settings, Temp Target y la gestión de QuickWizard, que quedan en solo lectura. Para editar, toca el lápiz ✏️ e introduce la contraseña o usa la biometría.
- **Novedades que no existían:** Scenes (guardar y lanzar varias acciones juntas), búsqueda global, rotación de zonas de infusión, nuevos widgets y pulsación larga en la gráfica para ver valores.

En el reloj, la entrada de tratamientos está en el menú de AAPS del propio reloj (asistente, eCarbs, tratamiento…).

Siguiente: [Paso 5 · Ajustes posteriores](05-ajustes-posteriores.md)
