# Migración de AAPS 3.x a 4.0.0 / Migrating AAPS 3.x to 4.0.0

**Español** · [English below](#english)

Guía paso a paso para actualizar AndroidAPS desde una versión 3.x (3.3 o 3.4) a la 4.0.0 conservando la configuración, y para orientarse en la nueva interfaz. Resume la documentación oficial y la contrasta con el código de la etiqueta `4.0.0` de este repositorio.

Esta guía no sustituye a la documentación oficial ni a tu equipo médico. Si algo de aquí contradice la wiki oficial, manda la wiki.

| Paso | Documento |
| --- | --- |
| 0 | [Antes de empezar](es/00-antes-de-empezar.md) |
| 1 | [Exportar los ajustes en 3.x](es/01-exportar-ajustes.md) |
| 2 | [Compilar e instalar la 4.0.0](es/02-compilar-e-instalar.md) |
| 3 | [Comprobar la versión e importar si hace falta](es/03-verificar-e-importar.md) |
| 4 | [La nueva interfaz: dónde está cada cosa](es/04-nueva-interfaz.md) |
| 5 | [Ajustes obligatorios después de actualizar](es/05-ajustes-posteriores.md) |
| 6 | [Comprobación final y problemas frecuentes](es/06-comprobacion-final.md) |
| Anexo | [Cómo importa la 4.0.0 un archivo de la 3.x (código)](es/anexo-importacion-tecnica.md) |

Fuentes oficiales (consultadas el 22-11-2026):

- Notas de la versión: https://androidaps.readthedocs.io/en/latest/Maintenance/ReleaseNotes.html
- Manual de actualización: https://androidaps.readthedocs.io/en/latest/Maintenance/UpdateToNewVersion.html
- Qué cambió en AAPS v4: https://androidaps.readthedocs.io/en/latest/Maintenance/V4Changes.html
- Exportar e importar ajustes: https://androidaps.readthedocs.io/en/latest/Maintenance/ExportImportSettings.html
- Código: `_docs/IMPORT.md` y `implementation/src/commonMain/kotlin/app/aaps/implementation/maintenance/migration/`

---

## English

Step-by-step guide to update AndroidAPS from a 3.x release (3.3 or 3.4) to 4.0.0 while keeping your configuration, and to find your way around the new interface. It summarises the official documentation and checks it against the code at tag `4.0.0` of this repository.

This guide does not replace the official documentation or your medical team. If anything here contradicts the official wiki, the wiki wins.

| Step | Document |
| --- | --- |
| 0 | [Before you start](en/00-before-you-start.md) |
| 1 | [Export your settings on 3.x](en/01-export-settings.md) |
| 2 | [Build and install 4.0.0](en/02-build-and-install.md) |
| 3 | [Check the version and import if needed](en/03-verify-and-import.md) |
| 4 | [The new interface: where everything is](en/04-new-interface.md) |
| 5 | [Required settings after updating](en/05-post-update-settings.md) |
| 6 | [Final check and common problems](en/06-final-checklist.md) |
| Annex | [How 4.0.0 imports a 3.x file (code)](en/annex-import-internals.md) |

Official sources (checked 2026-11-22): see the links above.
