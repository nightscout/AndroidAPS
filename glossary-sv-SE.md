# Swedish wiki and app glossary (AndroidAPSdocs + AndroidAPS, language sv-SE)

Style (user decision 2026-10-31): simple, clear, modern standard Swedish, short sentences, common words.
Address the reader with "du" (tryck, välj, din telefon), as Swedish software does. Buttons: imperative or noun as Swedish Android apps do.
Text quoted from the app keeps the app's own wording.
Product names, plugin names and abbreviations stay in Latin letters as in English (AAPS, Nightscout,
xDrip, Tidepool, GitHub, Android Studio, AAPS Builder, Autosens, SMB, CGM) unless the app uses a
local abbreviation (check app_all.json, e.g. for IOB / COB / ISF / IC / TDD / DIA): app words win.
Numbers: decimal comma (5,5 mmol/l), units as the app shows them.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Swedish string in bold, exactly as on screen. Current Swedish app strings from
Crowdin are in <WORK>/app_all.json ({key,en,tr}); missing ones are being translated now
(<WORK>/app_missing_part*.out.json). If a label looks clearly wrong, use correct Swedish and report it
as APP bug.
Labels of English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays) stay English
in bold; a short Swedish explanation in brackets is fine.
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Terms (app words win; add decided terms here)
Take loop, bolus, basal, temp basal, extended bolus, temp target, profile, profile switch, carbs,
BG, sensor, pairing, master, client, scene, running mode, QuickWizard from the existing app
(app_all.json). Where the app uses two words for one English term, use the one in more approved
strings and report the other as APP bug.

## Links to other wiki pages
Link text that names another page must use that page's Swedish title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## Added 2026-11-07 (ConfigBuilder rewrite, AAPSClient iOS)
mandatory = obligatoriska; copy icon = kopieringsikonen; address bar = adressfältet
WearOS/WearOsSmartwatch.md = Smartklocka med Wear OS; Custom peak = Egen topp; Pregnant = Gravid
Food (Manage entry) = Matdatabas (approved text; app has both Mat and Matdatabas)
How can I donate? = Hur kan jag skänka pengar? ("Hur kan jag vara med?" is "How do I participate?")
EndOfLife script: visible words translated ("and" = och, "to" = till); code unchanged

## Added 2026-11-08 (Dexcom Notification Reader, alarm settings)
Notification Reader = Läsare för aviseringar; Notification Access = Åtkomst till aviseringar; Permissions required = Behörigheter krävs; Allow = Bevilja (app)
Mute for 5 minutes / Silence alarm / Silence and close = Tysta i 5 minuter / Tysta larm / Tysta och stäng (app)
Override Do Not Disturb = Ignorera Stör ej-läge; AAPS alarms = AAPS larm (app string alarm_channel_group; standard Swedish would be "AAPS-larm")
trusted BG data source = pålitlig BG-källa; backfill = ifyllning av saknade data (backfill)
xDrip follower mode names stay English
