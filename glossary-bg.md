# Bulgarian wiki glossary (AndroidAPSdocs, language bg)

Style: simple, clear Bulgarian, short sentences, common words, formal "Вие" form for the reader
(вие/ви/вашия written lowercase in the middle of a sentence). Cyrillic only: never mix Latin and
Cyrillic letters inside one word (a Latin "c", "o", "a", "e", "p" that looks Cyrillic is an error).
Numbers: decimal comma (5,5 mmol/l), space before % (2 %), en dash in ranges (1–4 часа), quotes „…“.
Units: mg/dl, mmol/l, U for insulin units (as in the English source), ч / мин for hours/minutes in text.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Bulgarian string (values-bg-rBG) in bold, exactly as on screen. The app
translation is approved by a native proofreader - follow it; if a label looks clearly wrong
(typo, mixed letters), use correct Bulgarian and report it as APP bug.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Google Play Protect, Autosens, SMB, IOB, COB, ISF, IC, HbA1c, CGM, fork, workflow names,
Android Studio menu items, labels of English-only tools (GitHub, AAPS Builder setup page, xDrip+,
Juggluco, pump displays) - a short Bulgarian explanation in brackets is fine.

## Objectives (app objectives.xml; short wiki titles)
Objectives = Цели; Objective N = Цел N
1 Setting up visualization and monitoring = Настройка на визуализацията и наблюдението
2 Learn how to control AAPS = Научете се как да управлявате AAPS
3 Prove your knowledge = Докажете знанията си
4 Starting on an open loop = Стартиране в режим Отворен кръг
5 Understanding your open loop = Разбиране на режима Отворен кръг
6 Starting to close the loop (Low Glucose Suspend) = Стартиране на Затворен кръг (спиране на базала при ниска КЗ)
7 Tuning the closed loop = Настройване на Затворения кръг
8 Adjust basals and ratios, enable Autosens = Настройване на базала и коефициентите, активиране на Autosens
9 Enabling Super Micro Bolus (SMB) = Разрешаване на Super Micro Bolus (SMB)
10 Automation = Автоматизация

## Terms (app words win)
loop = кръг (app: Затворен кръг / Отворен кръг); closed loop = затворен кръг; open loop = отворен кръг
hybrid closed loop = хибриден затворен кръг; full closed loop = напълно затворен кръг
BG / blood glucose = кръвна захар (КЗ) (app abbreviation КЗ); glucose target = целева КЗ; target = цел
carbs = въглехидрати; basal = базал; basal rate = базална доза; temp basal / TBR = временен базал (TBR)
insulin = инсулин; pump = помпа; insulin pump = инсулинова помпа
hypo / hypoglycemia = хипогликемия; ketoacidosis = кетоацидоза
type 1 diabetes = диабет тип 1; diabetes management = лечение на диабета
clinician = лекар; clinical team = медицински екип; caregiver = болногледач / родител; follower = последовател
build (verb) = компилирам / изграждам (use "компилиране на AAPS" for Building AAPS); Building AAPS = Компилиране на AAPS
Browser build = Компилиране в браузъра; Android Studio build = Компилиране с Android Studio
keystore = хранилище за ключове (keystore); signing key = ключ за подписване; repository = хранилище (repository)
reporting server = сървър за отчети; release notes = бележки към версията
Temp target = Временна цел; Profile switch = Смяна на профил; Treatments = Лечения
Careportal = Портал за лечения (app); Extended bolus = Удължен болус
Preferences = Опции (app); Settings = Настройки; Configuration = Конфигурация; Manage = Управление
DIA = време на действие на инсулина (DIA); ISF = инсулинова чувствителност (ISF); IC = коефициент инсулин/въглехидрати (IC)
Low Glucose Suspend (LGS) = спиране на базала при ниска КЗ (LGS)
Running mode = Текущ режим (app); Automation = Автоматизация; plugin = плъгин; Open plugin = Отвори плъгин
Resume = Възстанови; Suspend = Спиране; Food = Храна; Setup Wizard = Съветник за настройване
Troubleshooting = Отстраняване на неизправности; virtual pump = виртуална помпа
smartwatch = смарт часовник; watchface = циферблат; complication = усложнение (complication)
SMS commands, JSON keys, anchors, code: unchanged.

## Links to other wiki pages
Link text that names another page must use that page's Bulgarian title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate the title and add
`Folder/Page.md = title` to your <name>.terms.txt.


## Pilot decisions
MUST NOT = НЕ БИВА (not "НЕ ТРЯБВА"); grams = г; Samsung/Android menu labels: Bulgarian in bold + (English label)
looping / full looping = затворен кръг / пълноценно използване на затворения кръг
Command-line build = Компилиране от командния ред
Browser build with a fork = Компилиране в браузъра с fork
fork method = методът с fork
Google Drive = Google Drive (kept English)
GitHub account = профил в GitHub
export of settings = експорт на настройките
release notes = бележки към версията
clinical team = медицински екип
followers = последователи (NSFollowers kept in brackets)
SGLT-2 inhibitors = SGLT-2 инхибитори; gliflozins = глифлозини
retinopathy = ретинопатия; treatment-induced neuropathy = предизвикана от лечението невропатия
percentage points = процентни пункти
MUST NOT = НЕ БИВА (clearer than "НЕ ТРЯБВА")
g (grams) in text = г
Objectives (as link text) = Цели / Целите
Developer mode = режим за разработчици; Developer options = Опции за разработчици (Developer options)
Samsung/Android menu labels = Bulgarian label in bold + English label in brackets


## Added after batch 1
keystore = хранилище за ключове (always in full); repository = хранилище (repository) - first use with English in brackets; cloud storage = облачно пространство
additional communication device = допълнително комуникационно устройство
Artificial Pancreas System (APS) = система за изкуствен панкреас (APS)
OS-AID = OS-AID (автоматизирано подаване на инсулин с отворен код)
open-source = с отворен код
automated insulin delivery (AID) = автоматизирано подаване на инсулин (AID)
learning algorithm = самообучаващ се алгоритъм
MDI (multiple daily injections) = многократни дневни инжекции (MDI)
time in range (TIR) = време в целевия диапазон (TIR)
rig = Rig (малък компютър)
supermicro bolus (SMB) = супер микро болус (SMB)
unannounced meals (UAM) = необявени хранения (UAM)
master branch = клон master; development branch = клон за разработка (development)
blind users = незрящи потребители
screen reader = екранен четец
healthcare professionals = медицински специалисти
consumable supplies = консумативи
type 1 diabetic = човек с диабет тип 1
tested hardware setups = тествани хардуерни конфигурации
phone wallpaper = тапет за телефона
External Companion Apps = Външни приложения за проследяване (app plugin name)
companion app = придружаващо приложение
broadcast (noun, data sent to other apps) = излъчване
standalone (watch) = самостоятелен
full Android watch = пълноценен часовник с Android
following only = само следене
insulin on board = активен инсулин (IOB) (app: Активен Инсулин (IOB))
COB = активни въглехидрати (COB) (app: Активни въглехидрати)
loop status = статус на кръга
time in range (TIR) = време в целта (TIR) (app: Време в целта)
BG Source = Източник на КЗ (app)
trusted BG data source = надежден източник на данни за КЗ
transmitter = трансмитер
patched app = модифицирано приложение
CR (carb ratio) = CR (коефициент за въглехидрати)
BR (basal rates) = BR (базални дози)
supplies = консумативи; infusion set = инфузионен сет; inserter = апликатор (инсертер)
reporting server = сървър за отчети
build workflow = работен процес за компилиране (build workflow); workflow = работен процес
setup page (AAPS Builder) = страница за настройка
GitHub secret = секрет (secret)
alias (keystore) = псевдоним (alias)
private repository = частно хранилище
a build (noun) = компилация; build page = страницата на компилацията; build log = лог на компилацията
artifacts = артефакти (GitHub label Artifacts kept English)
option (Option 1/2 of browser build) = Вариант 1 / Вариант 2
preparation file = подготвителен файл
Google refresh token expired = Изтекъл токен за опресняване на Google
Android developer verification = проверка на разработчиците на Android
tap = докоснете; click = натиснете; long press = продължително натискане
cloud storage = облачно пространство (not "хранилище", to avoid a clash with repository/keystore)
Chrome: Downloads = Изтегляния; Install = Инсталиране; Open in new tab = Отваряне в нов раздел (bold + English in brackets)
Android: Settings → Apps → Open by default → Open supported links = Настройки → Приложения → Отваряне по подразбиране → Отваряне на поддържаните връзки (+ English in brackets)
reporting servers = сървъри за отчети
Simple mode = Опростен режим (app)
Sensitivity Detection = Определяне на чувствителност (app)
Overview / main screen = главния екран
settings file = файл с настройки
max daily basal = максимален дневен базал
average meal bolus = среден болус за хранене
zero-temping = нулев временен базал (zero-temping)
Hint = Подсказка (app)
Not completed yet = Все още не е завършено (app string is wrong, see bugs)
Reset finished state = Изчисти завършените (app); Reset started state = Изчисти стартираните (app)
Open Loop / Closed Loop / Low Glucose Suspend running mode = Отворен кръг / Затворен кръг / Спиране на базал при ниска КЗ (app)
Configuration → Loop = Конфигурация → Кръг
How long will it take? (anchor preparing-how-long-will-it-take) = Колко време ще отнеме?
master (phone) = главен телефон (master); client = клиент
Master ↔ Client control = Управление главен телефон ↔ клиент
Overview (old plugin / screen) = Общ преглед
Config Builder (v3 name) = Config Builder (kept English); Configuration = Конфигурация
Insulin (Config Builder heading) = Инсулин (not the app's "Инсулин (ед.):")
Loop (Config Builder heading) = Кръг
Wear (plugin heading) = Часовник (Wear)
Manage sheet = листът „Управление“
bottom navigation = долната навигация
search bar = лентата за търсене
fingerstick entry = измерване от пръст
slope / offset (calibration) = наклон / отместване
zero-temping = нулев временен базал
companion app = придружаващо приложение
bolus wizard = болус калкулатор (app)
engineering (developer) build = инженерна компилация (за разработчици)
Key AAPS Features = Основни функции на AAPS
Synchronization with the reporting server = Синхронизация със сървъра за отчети
client control = контрол от клиентско приложение (app: Контрол от клиентско приложение)
hardened mode = режим на засилена защита (app)
RemoteFeatures/ClientMasterControl.md (link text "Master ↔ Client control") = Контрол Master ↔ Client
Nightscout pills = „хапчета“ (pills) в Nightscout
security = сигурност
master (phone) = главен телефон; client = клиент; paired = свързан; role = роля
hardened mode = режим на засилена защита (app hardened_mode_title)
Simple Mode = Опростен режим (app pref_title_simple_mode)
NEXT (setup wizard button) = Следващ (app next_button)
FINISH (setup wizard button) = КРАЙ (app setupwizard_finish)
Grant (Permissions Required panel) = Позволи (app permission_grant); Grant (wizard Permissions screen) = Разрешаване (app grant)
Permissions Required = Трябват права за достъп за няколко неща (app permission_sheet_title)
Profile editor tabs IC / ISF / BAS / TARG = И / Въгл / Чувств / БАЗА / ЦЕЛ (app ic_short, isf_short, basal_short, target_short)
OK button = ОК (Cyrillic, app); Dismiss = Откажи (app)
Android Allow = Разрешаване (Allow); USE THIS FOLDER = ИЗПОЛЗВАНЕ НА ТАЗИ ПАПКА (USE THIS FOLDER)
Duration of insulin action (DIA) and peak = Време на действие на инсулина (DIA) и пик
Bluetooth permission = Разрешение за Bluetooth
nickname (insulin) = кратко име
Tidepool account = профил в Tidepool
Login / Logout (icons) = Вход / Изход (app)
Connected (Tidepool status) = Свързана (app interfaces "connected")
log (on plugin screen) = дневник (log)
USB debugging = отстраняване на грешки през USB (USB debugging)
About phone = Всичко за телефона (About phone); Build number = Номер на компилацията (Build number)
data-capable USB cable = USB кабел, който може да пренася данни
Android developer verification = проверка на разработчиците за Android

## User decisions (these win over everything above)
Uploads are approved. The app translation (approved by a native proofreader) is the reference for UI words. loop = кръг everywhere (app aligned today).
Omnipod pod in running text = пач (plural пачове), as ~190 approved strings; bold UI labels keep the app text ("Активирай Pod"). alert = аларма; basal rate = базална доза; Option = Вариант
Final consistency decisions: GitHub secret = секрет; pairing secret = таен ключ за свързване (app); click = натиснете (right-click = щракнете с десния бутон); tap = докоснете; alert (noun) = аларма (CareLevo contrast "alert vs critical alarm" = предупреждение); QuickWizard in prose = бързи действия; Suspend loop = Спри кръга; Loop suspended = Кръг спрян; Discard (patch) = Изхвърли; Treatment button = Лечение; Add = Добави

## Added 2026-11-07 (ConfigBuilder rewrite, AAPSClient iOS)
Set up your configuration (CGM, pump, …) and enable features = Настройте конфигурацията си (CGM, помпа, …) и активирайте функции (app)
calibration pairs = двойки; Auto ISF = Автом ISF (app); SENS% = ЧУВСТВ% (app); notarized = заверено (notarized)
macOS labels: Готово (Done), Системни настройки → Поверителност и сигурност, Отвори въпреки това (English in brackets)
EndOfLife script: visible words in template strings translated ("and" = и, "to" = до); code unchanged

## Added 2026-11-08 (Dexcom Notification Reader, alarm settings)
Notification Reader = Четец на Нотификации; Notification Access = Достъп до Нотификации; Permissions required = Нужни са разрешения (app)
Mute for 5 minutes / Silence alarm / Silence and close = Изключи за 5 минути / Заглуши алармата / Заглуши и затвори (app)
Override Do Not Disturb = Прескочи DND режима; AAPS alarms = AAPS аларми (app)
backfill = попълване на пропуските (backfill)
Vibration (Android setting) = Вибриране (Vibration); Allow (Android dialog) = Разрешаване (Allow)
alarm volume / notification volume = сила на звука за аларми / за известия
