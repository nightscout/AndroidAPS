# Ukrainian wiki glossary (AndroidAPSdocs, language uk)

Style: simple, clear, modern standard Ukrainian, short sentences, common words. Address the reader
politely with "ви" (lowercase: "налаштуйте", "ваш телефон"), as Ukrainian manuals and medical texts
do. The app mixes "ти" and "ви"; text quoted from the app keeps the app's own wording.
Never use Russian words or Russian spelling. Product names stay in Latin letters.
Numbers: decimal comma (5,5 ммоль/л), 2 %, en dash in ranges (1–4 години), quotes «…».
Units: мг/дл, ммоль/л; insulin units as in the app (check `format_insulin_units` in app_all.json);
год / хв in text.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Ukrainian string in bold, exactly as on screen. Current Ukrainian app
strings from Crowdin are in <WORK>/app_all.json ({key,en,tr}); they are newer than the repo. About
a third of the app was translated before; the rest is being translated now, so re-read app_all.json
or <WORK>/app_missing_part*.out.json for labels. If a label looks clearly wrong, use correct
Ukrainian and report it as APP bug.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Autosens, SMB, IOB, COB, ISF, IC, HbA1c, CGM, fork, workflow names, labels of
English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays): English in bold, a
short Ukrainian explanation in brackets is fine.

## Terms (app words win)
loop = петля (app: Петля); closed loop = закрита петля; open loop = відкрита петля
Suspend loop = Призупинити петлю; Resume loop = Відновити петлю
Objectives = Завдання (app); Objective N = Завдання N
BG / blood glucose = глюкоза крові (ГК); BG Source = Джерело ГК (app); target = ціль
carbs = вуглеводи; basal = базал (app); temp basal = тимчасовий базал (TBR)
Extended bolus = Подовжений болюс (app); bolus = болюс; insulin = інсулін; pump = помпа
profile = профіль; Settings = Налаштування (app); running mode = Робочий режим (app)
temp target = тимчасова ціль; calibration = калібрування; reservoir = резервуар; cannula = канюля
Automation = Автоматизація; Maintenance = Обслуговування; History browser = Перегляд історії
Export settings = Експорт налаштувань
hypo = гіпо(глікемія); clinician = лікар; caregiver = доглядач / батьки
build (verb) = зібрати (збірка); keystore = keystore (сховище ключів); plugin = плагін
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Links to other wiki pages
Link text that names another page must use that page's Ukrainian title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## App labels fixed on Crowdin (local values-uk-rUA files are older; these win)
(see app_all.json). Chosen while translating the missing app strings (2026-10-31):
insulin unit = Од (Од/год); grams = г; app = застосунок; master = основний телефон (master device = основний пристрій; the app already had «основний смартфон»); client = клієнт
pair / unpair = спарувати / розпарувати; hardened mode = захищений режим; simple mode = простий режим
QuickWizard = Швидкий майстер; QuickLaunch = швидкий запуск; preset = шаблон; scene = сцена
IOB = Активний інсулін (IOB); COB = Активні вуглеводи (COB); Overview = Огляд; Treatments = Терапія; Manage = Керувати (app label, key manage)
remote control = віддалене керування (app; not дистанційне); remote monitoring = віддалений моніторинг
watchface = циферблат; complication = віджет (as the app; Wear OS says ускладнення - unify in review); wizard = калькулятор; upload = вивантажити; smoothing = згладжування; trigger = тригер; master password = Головний пароль

## User decisions
- Wiki: upload and approve. App: translate missing strings + fix clear errors.
- 2026-10-31: "ви" everywhere, wiki and app. New app strings use ви; existing ти app strings are changed to ви later (as was done for Spanish tú).

## Round 2 (2026-11-21): app completion + wiki
Configuration (v4 menu, was Config Builder = Конфігуратор) = Конфігурація; Communication = Звʼязок; Manage sheet = панель Керувати
Insulin settings = Налаштування інсуліну; Load peak from = Завантажити пік з; Treatments history = Історія терапії
Linear calibration = Лінійне калібрування; No calibration = Без калібрування; slope / offset = нахил / зсув
mobile icon = іконка телефону; synced from the master = синхронізовано з основного телефону; per device = окремо на кожному пристрої
fingerstick = вимірювання з пальця; zero-temping = нульовий тимчасовий базал; pump screen = екран помпи; pump log = журнал помпи
AAPS counts (the bolus) = AAPS враховує; Recent searches = Нещодавні пошуки; insulin change (concentration) = заміна інсуліну
Patient age = Вік пацієнта; mmol/mol = ммоль/моль; Cloud sync = Синхронізація з хмарою
trusted BG data source = довірене джерело даних ГК (H1 of the target section on CompatiblesCgms; not надійне)
trusted sensor = довірений сенсор
follower / follower mode = послідовник / режим послідовника (wiki uses it ~60 times; not спостерігач)
uploader (CGM uploader) = вивантажувач (upload = вивантажити; not завантажувач)
Config Builder (old name) = Конфігуратор; v4 label Configuration = Конфігурація (only one old-name mention, on ConfigBuilder.md, is correct)
Notification Reader = Читач сповіщень (app)
AAPS Builder setup page labels = quote the Ukrainian labels of the translated setup page (SettingUpAaps/BrowserBuildAapsBuilderSetup.md), not English. The setup page linked from the wiki is a docs page and is translated ("part of this documentation, in your language", also said on DocumentationUpdate). Replaces the glossary rule "AAPS Builder page labels stay English" for uk.
I already have a keystore (setup page tab) = У мене вже є keystore
Create a new key (setup page tab) = Створити новий ключ
Your repository: = Ваш репозиторій:
Your GitHub username = Ваше ім'я користувача GitHub
Name of your repository = Назва вашого репозиторію
Keystore file = Файл keystore; Keystore password = Пароль keystore; Key alias = Псевдонім ключа (alias)
Key password is the same as the keystore password = Пароль ключа такий самий, як пароль keystore
Check and continue = Перевірити і продовжити; Create key = Створити ключ; Key created = Ключ створено
Download backup (aaps-keystore.jks) = Завантажити резервну копію (aaps-keystore.jks)
Create my private repository = Створити мій приватний репозиторій
Open the secrets page = Відкрити сторінку секретів; Open the build page = Відкрити сторінку збірки; Open my builds = Відкрити мої збірки
Copy name = Копіювати назву; Copy secret = Копіювати секрет
Connect Google Drive = Підключити Google Drive; Paste the copied address here = Вставте сюди скопійовану адресу; Get GDRIVE_OAUTH2 = Отримати GDRIVE_OAUTH2
Open the newest workflow = Відкрити найновіший робочий процес; Open my workflow file = Відкрити мій файл робочого процесу
Patch pump (app label patch_pump) = Патч-помпа
