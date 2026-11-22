# Anexo · Cómo importa la 4.0.0 un archivo de la 3.x

Resumen, para usuarios, de lo que hace el código en la etiqueta `4.0.0`. La explicación completa para desarrolladores está en [`_docs/IMPORT.md`](../../IMPORT.md); las migraciones, en `implementation/src/commonMain/kotlin/app/aaps/implementation/maintenance/migration/PreferenceMigrations.kt`.

## Las mismas migraciones al arrancar y al importar

La 3.3 exportaba todos los ajustes sin filtrar y con nombres antiguos, como `LocalProfile_0_isf` o `ConfigBuilder_PUMP_DanaRSPlugin_Enabled`. La 4.0.0 ya no reconoce esos nombres, así que pasa el contenido por una cadena de migraciones antes de usarlo. Esa misma cadena se ejecuta sobre los ajustes del teléfono al arrancar tras actualizar y sobre el archivo cuando importas, de modo que ambos caminos dan el mismo resultado. El orden actual es:

`overviewMarks → dynamicIsfRetired → dynIsfAdjust → smsOtpPassword → widgetUseBlack → objectives → configBuilder → localProfile → insulinPlugins → tidepool → apsMode → tempTargetPresets → activityMonitorRetired`

Lo que importa para ti:

- **Perfiles, plugins seleccionados, objetivos y modo del lazo** se convierten a los nombres nuevos. Sin este paso se perderían.
- **El modo del lazo** de una copia de la 3.3 (`aps_mode`) se traduce a un registro de *running mode*. Si no se hiciera, el lazo volvería apagado.
- **La insulina** dejó de ser un plugin: sus filas antiguas de Config Builder se eliminan y la insulina con la que se administraron los registros viejos se deduce aparte.
- **Los presets de objetivo temporal** con valor cero (algo que la 3.3 podía dejar) se descartan para que valga el valor por defecto. Al importar no se inventan presets por encima de los tuyos.
- Un valor que no se puede convertir se registra y se deja como está, sin adivinar.

## Lo que la importación no aplica nunca

- **Ajustes ligados al dispositivo** (39 claves marcadas como no exportables): identidad de la bomba, punteros de sincronización de Nightscout, xDrip, Tidepool y OpenHumans, plazos de alarmas locales y la escena en curso.
- **Ajustes de bomba**, si no marcas *Also replace pump settings*. Se cuentan igualmente para que la pantalla de confirmación diga cuántos cambiarían.
- **Nombres que esta versión no reconoce**, por ejemplo de una versión más nueva. Se dejan y se registran, no se borran.
- **Valores idénticos** a los que ya tiene el teléfono, para que el cambio sea pequeño y se pueda revisar.
- En un teléfono **cliente**, los ajustes sincronizados, porque pertenecen al teléfono principal.

## Un detalle de la pantalla de confirmación

Los recuentos de la vista previa se calculan con los nombres originales del archivo, antes de migrarlo (las migraciones solo se ejecutan al aplicar). Con un archivo de la 3.3, la vista previa puede mostrar **menos cambios de los que realmente se aplicarán**. No es un error.
