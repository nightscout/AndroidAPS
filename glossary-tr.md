# Turkish wiki and app glossary (AndroidAPSdocs + AndroidAPS, language tr)

Style (user decision 2026-10-31): simple, clear, modern standard Turkish, short sentences, common words.
Address the reader formally with "siz" (seçin, dokunun, telefonunuz). Buttons: noun or short imperative as Turkish Android apps do.
Text quoted from the app keeps the app's own wording.
Product names, plugin names and abbreviations stay in Latin letters as in English (AAPS, Nightscout,
xDrip, Tidepool, GitHub, Android Studio, AAPS Builder, Autosens, SMB, CGM) unless the app uses a
local abbreviation (check app_all.json, e.g. for IOB / COB / ISF / IC / TDD / DIA): app words win.
Numbers: decimal comma (5,5 mmol/L), units as the app shows them.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Turkish string in bold, exactly as on screen. Current Turkish app strings from
Crowdin are in <WORK>/app_all.json ({key,en,tr}); missing ones are being translated now
(<WORK>/app_missing_part*.out.json). If a label looks clearly wrong, use correct Turkish and report it
as APP bug.
Labels of English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays) stay English
in bold; a short Turkish explanation in brackets is fine.
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Terms (app words win; add decided terms here)
Take loop, bolus, basal, temp basal, extended bolus, temp target, profile, profile switch, carbs,
BG, sensor, pairing, master, client, scene, running mode, QuickWizard from the existing app
(app_all.json). Where the app uses two words for one English term, use the one in more approved
strings and report the other as APP bug.

## Links to other wiki pages
Link text that names another page must use that page's Turkish title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## Added 2026-11-07 (ConfigBuilder rewrite, AAPSClient iOS)
Config Builder (v3 name) = Config Builder (stays English, as in the earlier Turkish text)
category (Configuration screen) = kategori; search bar = arama çubuğu; Manage sheet = Yönet paneli
mobile (phone) icon / synced from the master = mobil (telefon) simgesi / ana birimden senkronize edildi
per device = her cihazda ayrı; Custom peak = Özel tepe; "Load peak from" presets = "Tepe değerini şuradan yükle" ön ayarları
Open plugin = Eklentiyi aç (app key open_plugin; "Eklentiyi açın" is another key)
APS category = APS (YPS) (app label)
Dev communication = Geliştirici iletişimi (not "Geliştirici sürümü")

## Added 2026-11-08 (Dexcom Notification Reader, alarm settings)
Notification Reader = Bildirim okuyucu; Permissions required = Gerekli İzinler; Notification Access = Bildirim erişimi; Allow = İzin ver (app)
Mute for 5 minutes / Silence alarm / Silence and close = 5 dakika sessize al / Alarmı sessize al / Sessize al ve kapat (app)
Override Do Not Disturb = Rahatsız Etmeyin modunu geçersiz kıl; AAPS alarms = AAPS alarmları (app)
trend arrow = eğilim oku; Volume = Ses düzeyi; Sound = Ses; Vibration = Titreşim
Android path Apps > AAPS > Notifications = Uygulamalar > AAPS > Bildirimler
backfill = eksik verileri doldurma (backfill); xDrip follower mode names stay English
