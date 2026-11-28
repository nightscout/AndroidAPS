# Dutch wiki glossary (AndroidAPSdocs, language nl)

Style: simple, clear Dutch (standard Netherlands Dutch, readable for Flemish readers), short
sentences, common words. Address the reader with "je/jouw" ("stel je telefoon in"), as most Dutch
technical guides do. The app mixes "je" and "u"; the wiki uses "je" everywhere, except text quoted
from the app, which keeps the app's own wording.
Numbers: decimal comma (5,5 mmol/l), 2%, en dash in ranges (1–4 uur), quotes "…" or ‘…’.
Units: mg/dl, mmol/l, E for insulin units (app `format_insulin_units`), E/u for a basal rate
(app `units_insulin_rate`), IE only for international units in the insulin concentration text,
uur / min in text.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Dutch string (values-nl-rNL, newer text on Crowdin) in bold, exactly as on
screen. If a label looks clearly wrong, use correct Dutch and report it as APP bug.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Autosens, SMB, IOB, COB, ISF, IC, HbA1c, CGM, fork, workflow names, labels of
English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays): English in bold, a
short Dutch explanation in brackets is fine.

## Terms (app words win)
loop = loop (app: Loop); closed loop = gesloten loop; open loop = open loop
Objectives = Doelen (app); Objective N = Doel N
BG / blood glucose = bloedglucose (BG); target = streefwaarde / streefdoel (app: Tijdelijk streefdoel)
carbs = koolhydraten (app); basal = basaal; temp basal = tijdelijk basaal (TBR)
Extended bolus = Vertraagde bolus (app); bolus = bolus; insulin = insuline; pump = pomp
profile = profiel; Settings / Preferences = Instellingen (app); running mode = Bedrijfsmodus (app; "Actieve modus" is only the Equil label)
calibration = kalibratie; cannula = canule; temp target = tijdelijk streefdoel
hypo = hypo; clinician = arts / zorgverlener; caregiver = verzorger / ouder
build (verb) = bouwen; keystore = keystore; plugin = plugin
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Links to other wiki pages
Link text that names another page must use that page's Dutch title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## App labels fixed on Crowdin (local values-nl-rNL files are older; these win)
Read <WORK>/app_all.json (all current Dutch app strings from Crowdin, {key,en,tr}) for labels.
Configuration = Configuratie; Open plugin = Plug-in openen; Overview = Overzicht; Manage = Beheren; Communication = Communicatie
Automation = Automatisering; Scenes = Scènes; Maintenance = Onderhoud; Export settings = Exporteer instellingen
Hardened mode = Beveiligde modus; Simple mode = Eenvoudige modus; Pair with master = Koppel met master
Authorized clients = Geautoriseerde clients; client = client; master = master; Profile percentage = Profiel percentage
Careportal = Careportal; QuickWizard = QuickWizard

## Exceptions
- ClinicianGuideToAaps (for doctors) uses "u"; all other pages use "je".

## User decisions
- Wiki: upload and approve. App: translate missing strings + fix clear errors.

## Decisions in the consistency pass (2026-10-31)
- watchface: prose uses "watchface" (most used in the wiki, 275 vs 38). App labels are quoted
  exactly as the app shows them, also when they say "wijzerplaat" (Wijzerplaat complicaties,
  Aangepaste wijzerplaat, Wijzerplaat is geïnstalleerd op horloge, Installeer de wijzerplaat).
- Prime/Fill: the phone button is "Prime/Vul" (prime_fill). "Ontlucht/vul" is the watch menu
  (menu_prime_fill), "Ontlucht/Vul" and "Prime / Vullen" are Equil screens: use them only there.
- Insulin settings = "Insuline instellingen" (app label insulin_management, no hyphen).
- Tubeless pumps = "pompen zonder slang" (link text: "Pompen zonder slang"); not "slangloze pompen".
- Setup wizard = "Setup Wizard" (app nav_setupwizard); not "installatiewizard".
- Smoothing = "vloeiend maken" (app); page H1 "Vloeiend maken van bloedglucosewaarden"; not "filteren".
- closed loop: prose "gesloten loop"; the running mode label stays "Closed loop" (app closedloop).
  Objective titles keep the app wording.
- command-line = "opdrachtregel".
- main phone / primary phone = "AAPS-telefoon" (never "hoofdtelefoon": that means headphones).
- Basal rate unit = "E/u" (app units_insulin_rate); not "E/uur" or "E/h".
- catheter = "canule".
- Pump displays (Insight, Combo) stay English, also in menu paths.
- Link texts that name another page use that page's approved H1. Use the part after the colon for
  browser build steps ("Je GitHub-fork maken", "Google Drive autoriseren", "De APK bouwen").
- H1 fixes: AapsScreens = "AAPS-schermen"; SensitivityDetectionAndCob = "Gevoeligheidsdetectie
  (Autosens)"; WearOsSmartwatch = "AAPS instellen op je Wear OS-horloge" (titles.js misses it).

## Added 2026-11-08 (batch round: ConfigBuilder rewrite, alarm settings)
Override Do Not Disturb mode = Niet storen-stand negeren (app pref_title_alert_override_dnd)
Gradually increase notification volume = Geleidelijk volume voor meldingen verhogen (app pref_title_alert_increase_volume)
Mute for 5 minutes = 5 minuten dempen (app mute5min)
Silence alarm = Alarmen dempen (app alarm_silence, see bug)
Silence and close = Dempen en sluiten (app alarm_silence_and_close)
AAPS alarms (notification channel group) = AAPS alarmen (app alarm_channel_group)
Do Not Disturb (prose) = Niet storen
Comparison (profile tab) = Vergelijken
Force stop (Android) = Gedwongen stoppen
App info (Android) = App-info
Custom peak = Eigen piek
calibration pair = paar
slope / offset (calibration) = helling / verschuiving (app cal_slope_offset)
fingerstick value = vingerprikwaarde
mobile (phone) icon (synced from master) = mobiel (telefoon) icoon
SettingUpAaps/ChangeAapsConfiguration.md = Wijzigingen maken in de configuratie van AAPS
SupportingAaps/StateOfTranslations.md = Status van de vertaling van de app en de documentatie

## Added 2026-11-08 (nl batch3: AapsClient, browser build)
private copy (of AAPS Builder) = privé-kopie
template (GitHub repository template) = template
Connect Google Drive (setup page button) = Google Drive verbinden
Get GDRIVE_OAUTH2 (setup page button) = GDRIVE_OAUTH2 ophalen
Open the newest workflow (setup page button) = De nieuwste workflow openen
Paste the copied address here (setup page label) = Plak hier het gekopieerde adres
plain-text version = versie als platte tekst
copy icon = kopieerpictogram
Tab switcher = tabbladwisselaar
package manager = pakketbeheerder
Apple developer account = Apple-ontwikkelaarsaccount
notarized (by Apple) = genotariseerd
follower device = volgapparaat
ready-made (download/file) = kant-en-klaar
Current running mode: %s (SMS reply) = Huidige bedrijfsmodus: %s

## Added 2026-11-08 (nl batch2)
AAPS settings cannot be read = AAPS instellingen kunnen niet gelezen worden (app settings_unreadable_title)
Retry (settings unreadable screen) = Opnieuw proberen (app core/ui retry)
Start fresh = Opnieuw beginnen (app settings_unreadable_start_fresh)
key store (of the phone) = sleutelopslag (key store)
encrypted store = versleutelde opslag
settings export = export van de instellingen
trusted (BG source) = vertrouwd; trusted data source = vertrouwde gegevensbron
follower mode = volgmodus (plural volgmodi)
Remote control (AAPSClient) = bediening op afstand
websocket connection = websocket-verbinding
Failed build = Mislukte build; "the build failed (red cross)" = de build is mislukt (rood kruis)
List of workflows = Lijst met workflows
pump settings (replaced on import) = pompinstellingen

## Added 2026-11-08 (nl batch1)
Notification Reader = Notificatielezer (app notification_reader)
Permissions Required = Vereiste machtigingen (app permission_sheet_title)
Notification Access = Toegang tot notificaties (app permission_notification_listener_title)
Grant = Toestaan (app permission_grant)
Allow notification access (Android) = Toegang tot meldingen toestaan
Notification read, reply & control (Android list name) = Meldingen lezen, beantwoorden en beheren
transmitter = zender
backfill = backfill
trend arrow = trendpijl
clock change (graph marker) = klokwijziging / wijzigingen van de klok
Time to eat (eat reminder) = Tijd om te eten (app eat_reminder_title)
The time is up = De tijd is om (app eat_reminder_rung)
Silence alarm = Alarmen dempen; Silence and close = Dempen en sluiten; Dismiss = Negeren
Alarms and reminders (Android) = Alarmen en herinneringen
bell (eat reminder) = bel
Bolus will be recorded only (not delivered by pump) = Bolus wordt alleen geregistreerd (niet toegediend door pomp) (app bolus_recorded_only)
Change smoothing = Vloeiend maken wijzigen (app change_smoothing)
Average smoothing = Gemiddeld vloeiend maken (app avg_smoothing_name)
Sensor age = Sensor leeftijd (app triggerSensorAgeLabel)
Comparison operators = Vergelijkingsoperatoren
Bolus protection = Bolus beveiliging; Settings protection = Instellingen beveiliging (app)
ACTIVE (profile card) = ACTIEF (app active_profile_indicator)
Absorption max time = Maximale absorptietijd koolhydraten (app pref_title_absorption_maxtime)
