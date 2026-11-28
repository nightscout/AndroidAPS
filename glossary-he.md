# Hebrew wiki and app glossary (AndroidAPSdocs + AndroidAPS, language he)

Style (user decision 2026-10-31): simple, clear modern Hebrew, short sentences, common words.
Gender-neutral: address the reader with infinitive or impersonal forms ("יש ללחוץ", "לבחור את…",
"ניתן להגדיר"); where an imperative is needed, use plural ("לחצו", "בחרו"). Never masculine-only or
feminine-only singular forms. Buttons and short labels: infinitive or noun ("שמירה", "ביטול", "להפעיל").
Text quoted from the app keeps the app's own wording.
Product names, plugin names and abbreviations stay in Latin letters (AAPS, Nightscout, xDrip,
Tidepool, GitHub, Android Studio, AAPS Builder, Autosens, SMB, IOB, COB, ISF, IC, CGM, TDD, DIA).
Numbers: decimal point as in Israeli usage (5.5), units mmol/L and mg/dL in Latin letters as on screen
(app format strings win), insulin units as the app shows them (check `format_insulin_units` in
app_all.json). Time: שעות / דקות in text. Quotes "…".
Keep "AAPS" (never "AndroidAPS" unless the source says so).
Right-to-left: write natural Hebrew; never reorder or reverse Latin words, numbers, tags, links,
placeholders or Markdown; they stay exactly as in the English.
UI labels: use the app's Hebrew string in bold, exactly as on screen. Current Hebrew app strings from
Crowdin are in <WORK>/app_all.json ({key,en,tr}). If a label looks clearly wrong, use correct
Hebrew and report it as APP bug.
Labels of English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays) stay English
in bold; a short Hebrew explanation in brackets is fine.

## Terms (app words win; add new ones here as they are decided)
loop = לולאה; closed loop = לולאה סגורה; open loop = לולאה פתוחה
bolus = בולוס; basal = בזאלי; temp basal = בזאלי זמני; insulin = אינסולין; pump = משאבה
carbs = פחמימות; profile = פרופיל; target = יעד; temp target = יעד זמני
BG / blood glucose = רמת סוכר בדם (BG); sensor = חיישן; Objectives = see app (`objectives`)
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Links to other wiki pages
Link text that names another page must use that page's Hebrew title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## Right-to-left decisions (2026-10-31)
- UI paths: write the arrow as ← (ניהול ← פרופיל); keep &#062; entities unchanged.
- "on the right of / left of" a control: write ליד (layout is mirrored); keep left/right only when describing a screenshot.
- Autotune plugin label = כוונון אוטומטי (app); Autotune in running text stays Latin.

## Added 2026-11-07 (ConfigBuilder rewrite, AAPSClient iOS)
How can I donate? = איך אפשר לתרום כסף? (the heading "How do I participate?" = איך משתתפים?)
Websockets are **mandatory** = Websockets הם **חובה**
tab switcher = מחליף הכרטיסיות; address bar = שורת הכתובת; copy icon = סמל ההעתקה
build variants = וריאנטים של בנייה
TransferringAndInstallingAaps.md link text = העברה והתקנה (page title not approved yet)
top-left = בפינה העליונה (corner not named, the layout is mirrored)

## Added 2026-11-08 (Dexcom Notification Reader, alarm settings)
Notification Reader = קורא התראות; Permissions required = נדרשות הרשאות; Notification Access = גישה להתראות; Allow = מתן הרשאה (app)
Mute for 5 minutes / Silence alarm / Silence and close = השתקה ל-5 דקות / השתקת ההתראה / השתקה וסגירה (app)
Override Do Not Disturb = עקיפת מצב 'נא לא להפריע'; AAPS alarms = התראות AAPS (app)
backfill = השלמת נתונים בדיעבד (backfill); trend arrow = חץ מגמה; uploader = המעלה (uploader)
Vibration = רטט; Volume = עוצמת קול; phone's alarm volume = עוצמת הקול של השעון המעורר בטלפון
trusted source link text = מקור מהימן לערכי סוכר
