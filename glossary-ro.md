# Romanian wiki and app glossary (AndroidAPSdocs + AndroidAPS, language ro)

Style (user decision 2026-10-31): simple, clear, modern standard Romanian, short sentences, common words.
Address the reader formally with "dumneavoastră" verb forms (apăsați, selectați, telefonul dumneavoastră). Buttons: infinitive or noun. Use correct diacritics ș ț (comma below), ă â î.
Text quoted from the app keeps the app's own wording.
Product names, plugin names and abbreviations stay in Latin letters as in English (AAPS, Nightscout,
xDrip, Tidepool, GitHub, Android Studio, AAPS Builder, Autosens, SMB, CGM) unless the app uses a
local abbreviation (check app_all.json, e.g. for IOB / COB / ISF / IC / TDD / DIA): app words win.
Numbers: decimal comma (5,5 mmol/l), units as the app shows them.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Romanian string in bold, exactly as on screen. Current Romanian app strings from
Crowdin are in <WORK>/app_all.json ({key,en,tr}); missing ones are being translated now
(<WORK>/app_missing_part*.out.json). If a label looks clearly wrong, use correct Romanian and report it
as APP bug.
Labels of English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays) stay English
in bold; a short Romanian explanation in brackets is fine.
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Terms (app words win; add decided terms here)
Take loop, bolus, basal, temp basal, extended bolus, temp target, profile, profile switch, carbs,
BG, sensor, pairing, master, client, scene, running mode, QuickWizard from the existing app
(app_all.json). Where the app uses two words for one English term, use the one in more approved
strings and report the other as APP bug.

## Links to other wiki pages
Link text that names another page must use that page's Romanian title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## Added 2026-11-07 (ConfigBuilder rewrite, AAPSClient iOS)
SettingUpAaps/YourAapsProfile.md = Profilul dumneavoastră AAPS
Open my workflow file = Deschideți fișierul meu workflow; Tab switcher = Comutatorul de file
How can I donate? = Cum pot face o donație? ("Cum pot participa?" is "How do I participate?")
mobile (phone) icon = pictogramă telefon; Peak = Vârf (app); Configuration = Configurație (app)
graph labels (app): Insulină activă, Carbohidrați la bord, Modificarea sensibilității; BAS = BAZ
plugin = Module in the app; some unapproved wiki strings say "Pluginurile" - proofreader to decide

## Added 2026-11-08 (Dexcom Notification Reader, alarm settings)
Notification Reader = Cititor notificări; Permissions required = Permisiuni necesare; Notification Access = Acces la notificări; Allow = Acordați (app)
Mute for 5 minutes / Silence alarm / Silence and close = Amuțiți pentru 5 minute / Opriți sunetul / Opriți sunetul și închideți (app)
Override Do Not Disturb = Suprascrieți modul Nu Deranjați; AAPS alarms = Alarme AAPS (app)
Android: Allow notification access = Permiteți accesul la notificări; Allow restricted settings = Permiteți setările restricționate
backfill = completarea datelor lipsă (backfill); follower modes = moduri de urmăritor; uploader = aplicația care încarcă datele (uploader)
trusted BG data source = sursă de date de glicemie de încredere
