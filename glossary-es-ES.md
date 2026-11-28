# Spanish wiki glossary (AndroidAPSdocs, language es-ES)

Style: simple, clear Spanish for all Spanish speakers (neutral, no regional slang), short sentences,
common words. Address the reader as "tú" ("configura", "tu teléfono"). Gender-neutral where easy.
Numbers: decimal comma (5,5 mmol/l), 2 %, en dash in ranges (1–4 horas), quotes «…».
Units: mg/dl, mmol/l, U for insulin units (as in the English source), h / min in text.
Keep "AAPS" (never "AndroidAPS" unless the source says so). AAPS is masculine (el AAPS / AAPS).
UI labels: use the app's Spanish string (values-es-rES) in bold, exactly as on screen. The app
translation is approved by a native proofreader - follow it; if a label looks clearly wrong, use
correct Spanish and report it as APP bug.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Autosens, SMB, IOB, COB, ISF, IC, HbA1c, fork, workflow names, labels of
English-only tools (GitHub, AAPS Builder page, xDrip+, Juggluco, pump displays): English in bold,
a short Spanish explanation in brackets is fine.

## Terms (app words win)
loop = lazo (app: Lazo); closed loop = lazo cerrado; open loop = lazo abierto
Objectives = Objetivos (app); Objective N = Objetivo N
BG / blood glucose = glucosa (BG); target = objetivo
carbs = carbohidratos (app); basal = basal (app: Dosis Basal); temp basal = basal temporal (TBR)
insulin = insulina; pump = bomba; profile = perfil; Treatments = Tratamientos (app)
Extended bolus = Bolo extendido (app); bolus = bolo
Configuration = Configuración (app); Settings: check the app string for each screen (the app uses
both Ajustes and Configuración - report it if Configuration and Settings show the same word)
Pair with master = Emparejar con el máster (app); Open plugin = Abrir complemento; plugin = complemento
hypo = hipoglucemia; clinician = médico; caregiver = cuidador / padre o madre
build (verb) = compilar; Building AAPS = Compilar AAPS; keystore = almacén de claves (keystore)
BG Source = Fuente de glucosa (app); CGM = MCG (app: "Recibir datos de MCG desde NS"); master (branch) = master
Configuration = Configuración; Settings = Ajustes (app, two different screens); Config Builder (old v3) = Tabla de configuraciones
Manage sheet = panel Administrar; master/client = máster/cliente; fingerstick = medición capilar
zero-temping = basal temporal a cero; companion app = app complementaria; Overview (old) = Inicio
QuickLaunch = Lanzamiento rápido (app); main screen = pantalla principal; Treatments sheet = panel Tratamientos
status row = fila de estado; running mode = modo de funcionamiento; eventual BG = glucosa final
IOB = insulina activa (IOB); COB = carbohidratos activos (COB); Resume = Reanudar (app, fixed)
simple mode = Modo sencillo; hardened mode = Modo reforzado; client control = Control de clientes (app)
MDI = múltiples inyecciones diarias (MDI); reporting server = servidor de informes
remote monitoring = seguimiento remoto; smartwatch = reloj inteligente; virtual pump = bomba virtual
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Links to other wiki pages
Link text that names another page must use that page's Spanish title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## App labels fixed on Crowdin (local values-es-rES files are older; these win)
(none yet)

## Exceptions
- ClinicianGuideToAaps (for doctors) uses "usted"; all other pages use "tú".
- Quoted app messages keep the app's own wording. Since 2026-10-31 the app uses "tú" everywhere too
  (user decision), so old "usted" quotes in the wiki are out of date.
- Patched app = app parcheada; watch "Carb time" = Hora HC; "loopers" stays.

## User decisions
- Wiki: upload and approve. App: missing strings + clear errors only.
- 2026-10-31: app made consistent: tú everywhere, máster for the master phone, Carbohidratos (not Hidratos de carbono).

## Added 2026-11-08 (es-ES batch4: ConfigBuilder rewrite, alarm settings)
SupportingAaps/StateOfTranslations.md = Estado de la traducción de la app y de la documentación
Override Do Not Disturb mode = Anular el modo "No molestar" (app label, bold)
Do Not Disturb = «No molestar»
AAPS alarms (notification channel list) = Alarmas de AAPS
Vibration (Android notification setting) = Vibración
Apps > AAPS > Notifications (Android settings path) = Aplicaciones > AAPS > Notificaciones
Force stop = Forzar detención; App info = Información de la aplicación
Linear calibration pair = par; slope = pendiente; offset = desplazamiento
Slope 0.85, mid-range correction -0.4 = Pendiente 0,85, corrección de rango medio -0,4 (app cal_slope_offset)
fingerstick value = valor capilar / medición capilar
Matching divider (watch setting) = Divisor de coincidencia (app wear pref_matching_divider)
BGI = impacto en la glucosa (BGI)
Links (RileyLink/OrangeLink/EmaLink) = dispositivos Link (never "Enlaces")
How can I donate? = ¿Cómo puedo donar?

## Added 2026-11-08 (es-ES batch3: AapsClient, browser build)
Max IOB for SMB = IOB Máximo para SMB (app label pref_title_smb_max_iob; replaces old wiki «IOB total máxima que OpenAPS no puede superar»)
Updating your copy (dropdown title on AAPS Builder setup page, not translatable) = **Updating your copy** (Actualizar tu copia)
Connect Google Drive / Paste the copied address here / Get GDRIVE_OAUTH2 / Open the newest workflow / Open my workflow file (AAPS Builder page buttons and labels) = kept in English, like the other button labels on that page
GDRIVE_OAUTH2 is ready (page message) = GDRIVE_OAUTH2 está listo
signing key = clave de firma
build steps = pasos de compilación
build workflow = workflow de compilación
offline (master) = sin conexión
File to download = Archivo para descargar
Following two people? = ¿Sigues a dos personas?
macOS: Done / System Settings → Privacy & Security / Open Anyway = Hecho (Done) / Ajustes del Sistema → Privacidad y seguridad / Abrir igualmente (Open Anyway)
Google: Advanced / Go to … = Advanced (Configuración avanzada) / Go to … (Ir a …)
Chrome: This site can't be reached / 127.0.0.1 refused to connect = English in bold + (No se puede acceder a este sitio web) / (127.0.0.1 ha rechazado la conexión)

## Added 2026-11-08 (es-ES batch2)
trusted (BG data source) = de confianza
Notification Reader (BG source) = Lector de notificaciones
Dexcom app = app de Dexcom
uploader (CGM uploader) = uploader
follower mode = modo follower
secret (GitHub secret) = secreto
build log = log de compilación
run the build again = volver a ejecutar la compilación
tag (git tag) = tag
Tubeless pumps = bombas sin tubo
Start fresh (app button) = Empezar de nuevo
Retry (app button) = Reintentar

## Added 2026-11-08 (es-ES batch1)
Notification Reader = Lector de notificaciones (app)
Notification Access = Acceso a notificaciones (app); Permissions Required = Permisos necesarios (app); Grant = Conceder (app)
Allow notification access = Permitir acceso a notificaciones; Allow restricted settings = Permitir ajustes restringidos
trusted BG data source = fuente de datos de glucosa de confianza
backfill = relleno de datos (backfill)
Time to eat (eat reminder) = Hora de comer (app); The time is up = Se acabó el tiempo (app)
Silence alarm = Silenciar alarma (app); Silence and close = Silenciar y cerrar (app); Dismiss = Descartar (app); Stop = Detener (app)
clock change marker = marca de cambio de hora; now line = línea de «ahora»
Change smoothing = Cambiar suavizado (app); Unscented Kalman Filter = Filtro de Kalman no lineal (app); Average smoothing = Suavizado promedio (app)
Send SMS to all numbers = Enviar SMS a todos los números (app); Alerts (automation group) = Alertas (app); Run Autotune = Ejecutar Autotune (app)
Bolus will be recorded only (not delivered by pump) = El bolo sólo se anotará (no será entregado por la bomba) (app)
Absorption max time = Tiempo máximo de absorción (app)
Engineering Mode = Modo de ingeniería
