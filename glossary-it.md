# Italian wiki and app glossary (AndroidAPSdocs + AndroidAPS, language it)

Style (user decision 2026-10-31): simple, clear, modern standard Italian, short sentences, common words.
Address the reader informally with "tu" (premi, seleziona, il tuo telefono), as modern Italian apps do. Buttons: infinitive or noun as Italian Android apps do.
Text quoted from the app keeps the app's own wording.
Product names, plugin names and abbreviations stay in Latin letters as in English (AAPS, Nightscout,
xDrip, Tidepool, GitHub, Android Studio, AAPS Builder, Autosens, SMB, CGM) unless the app uses a
local abbreviation (check app_all.json, e.g. for IOB / COB / ISF / IC / TDD / DIA): app words win.
Numbers: decimal comma (5,5 mmol/l), units as the app shows them.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Italian string in bold, exactly as on screen. Current Italian app strings from
Crowdin are in <WORK>/app_all.json ({key,en,tr}); missing ones are being translated now
(<WORK>/app_missing_part*.out.json). If a label looks clearly wrong, use correct Italian and report it
as APP bug.
Labels of English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays) stay English
in bold; a short Italian explanation in brackets is fine.
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Terms (app words win; add decided terms here)
Take loop, bolus, basal, temp basal, extended bolus, temp target, profile, profile switch, carbs,
BG, sensor, pairing, master, client, scene, running mode, QuickWizard from the existing app
(app_all.json). Where the app uses two words for one English term, use the one in more approved
strings and report the other as APP bug.

## Links to other wiki pages
Link text that names another page must use that page's Italian title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## Added 2026-11-07 (ConfigBuilder rewrite, AAPSClient iOS)
sync (phone) icon = icona del telefono; fingerstick = capillare; calibration pair = coppia; slope / offset = pendenza / scostamento
Open my workflow file = Apri il mio file workflow (setup page buttons in Italian, as existing U strings)
How can I donate? = Come posso fare una donazione? ("Come posso partecipare?" is "How do I participate?")
Sensitivity change (graph) = Variazione di sensibilità (app); websocket
zero-temping: "basale temporanea zero" vs plain "zero-temping" on other pages - proofreader to choose
GitHub and macOS labels stay English (Italian in brackets)

## Added 2026-11-08 (Dexcom Notification Reader, alarm settings)
Notification Reader = Lettore Notifiche (app); AAPS alarms channel = **Allarmi AAPS** (app)
trusted data source = origine dati affidabile (CompatiblesCgms keeps "sorgenti di dati affidabili" from its heading)
alarm volume = volume della sveglia; Sound / Volume / Vibration = Suono / Volume / Vibrazione
Android path Apps > Notifications = App > Notifiche
Android labels: DexcomG6/G7 keep them English, but SMSCommands @224891 uses "Consenti impostazioni con restrizioni" - not consistent yet
