# Lithuanian wiki and app glossary (AndroidAPSdocs + AndroidAPS, language lt)

Style (user decision 2026-10-31): simple, clear, modern standard Lithuanian, short sentences, common words.
Address the reader formally with "jūs" (spauskite, pasirinkite, jūsų telefonas). Buttons: infinitive or noun as Lithuanian Android apps do.
Text quoted from the app keeps the app's own wording.
Product names, plugin names and abbreviations stay in Latin letters as in English (AAPS, Nightscout,
xDrip, Tidepool, GitHub, Android Studio, AAPS Builder, Autosens, SMB, CGM) unless the app uses a
local abbreviation (check app_all.json, e.g. for IOB / COB / ISF / IC / TDD / DIA): app words win.
Numbers: decimal comma (5,5 mmol/l), units as the app shows them.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Lithuanian string in bold, exactly as on screen. Current Lithuanian app strings from
Crowdin are in <WORK>/app_all.json ({key,en,tr}); missing ones are being translated now
(<WORK>/app_missing_part*.out.json). If a label looks clearly wrong, use correct Lithuanian and report it
as APP bug.
Labels of English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays) stay English
in bold; a short Lithuanian explanation in brackets is fine.
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Terms (app words win; add decided terms here)
Take loop, bolus, basal, temp basal, extended bolus, temp target, profile, profile switch, carbs,
BG, sensor, pairing, master, client, scene, running mode, QuickWizard from the existing app
(app_all.json). Where the app uses two words for one English term, use the one in more approved
strings and report the other as APP bug.

## Links to other wiki pages
Link text that names another page must use that page's Lithuanian title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## Decisions (2026-10-31)
- sensor = jutiklis (app: 39 strings vs 11 "sensorius"); fix wiki "sensorius" in the consistency pass
- app abbreviations: AIO (IOB), AAO (COB), JIF (ISF), IA (IC), KG (BG), LT (TT), NDM (UAM), IVT (DIA), NGJ (CGM)
- smoothing = glodinimas; branch = atšaka; fork / commit stay English

## Added 2026-11-07 (ConfigBuilder rewrite, AAPSClient iOS)
WearOS/WearOsSmartwatch.md = Wear OS išmanusis laikrodis; YourAapsProfile = Jūsų AAPS profilis
Getting-Started/CompatiblePumps = Suderinamos pompos; KeyAapsFeatures = Pagrindinės AAPS funkcijos
Config Builder (old name) = Konfigūratorius; Manage sheet = meniu Valdymas; search bar = paieškos juosta
zero-temping = nulinė laikina bazė; Pregnant = Nėščioji (app); APS category = DKS (app); Basal (graph) = Valandinė bazė (app)
websockets: spelled "websockets" and "WebSocket" on approved strings - choose one later
macOS, GitHub and AAPS Builder labels stay English

## Added 2026-11-08 (Dexcom Notification Reader, alarm settings)
Notification Reader = Pranešimų skaitytuvas (app); AAPS alarms (Android notification group) = AAPS įspėjimai (app)
alarm in body text = signalas; trusted BG data source = patikimas glikemijos duomenų šaltinis
backfill = trūkstamų duomenų užpildymas (backfill); trend arrow = tendencijos rodyklė; alarm volume (Android) = žadintuvo garsumas
Android labels stay English with Lithuanian in brackets (Allow notification access, App info, Allow restricted settings)
Open: app alarm_silence = "Nutildyti" (same as Mute); better "Nutildyti signalą"
