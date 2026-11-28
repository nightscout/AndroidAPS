# Russian glossary (AndroidAPS app + AndroidAPSdocs wiki, language ru)

Style: simple, clear, modern standard Russian, short sentences, common words. No slang, no
needless anglicisms where the app already has a Russian word.
Address the user politely with "вы", lowercase ("введите", "ваш телефон"), as the app already does
(67 app strings with вы, 0 with ты; the wiki also uses only вы).
Buttons and menu items: infinitive, as in the approved app strings (Сохранить, Отменить, Удалить,
Начать, Закрыть, Возобновить). Instructions and messages: imperative вы-form (Введите название
задачи., Подтвердите терапию, Повторите попытку.). No "Пожалуйста" (the app drops "Please").
Numbers: decimal comma in prose (34,9 ед.; 5,5 ммоль/л); keep the source format in code, setting
values and formats with placeholders. Percent without a space, as in the app: 10%.
En dash in ranges (1–4 часа). Quotes «…» in prose; keep "…" where the source string already uses
them around a UI label in the app.
Units: insulin ед (app format_insulin_units "%1$.2f ед"; label "Ед" when it stands alone as in
insulin_unit_shortname), ед/ч, г (app shortgram "г"), мг/дл, ммоль/л, мин, ч.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Russian string in bold, exactly as on screen. Current app strings from
Crowdin are in <WORK>/app_all.json ({key,en,tr}); they are newer than the repo values-ru-rRU files.
About 55% of the app was translated by people before and many strings are approved: existing
approved app strings win over this glossary. If a label looks clearly wrong, use correct Russian
and report it as APP bug.
Names kept in English (Latin letters): Nightscout, Tidepool, xDrip+, BYODA, GitHub, GitHub Actions,
Android Studio, AAPS Builder, Autosens, Autotune, SMB, IOB, COB, ISF, IC, DIA, TDD, UAM, HbA1c,
CGM, Pod, OpenAPS, fork, workflow names, labels of English-only tools (GitHub, Android Studio, pump
displays): English in bold, a short Russian explanation in brackets is fine.
SMS commands, JSON keys, anchors, code, image names, placeholders: unchanged.
Rule: @121563 "PUMP" stays "PUMP" (pump display / SMS word, not translated).

## Terms (app words win; "(app)" = the word the approved app strings use)
loop = цикл; Loop (plugin/label) = Замкнутый цикл (app); closed loop = замкнутый цикл (ЗЦ) (app)
open loop = открытый цикл (app); full closed loop = автономный замкнутый цикл (АЗЦ) (wiki)
hybrid closed loop = гибридный замкнутый цикл
Suspend loop = Приостановить цикл; Resume loop = Возобновить цикл (app)
Running mode = Режим работы (app Compose label; also "Рабочий режим", "Текущий режим" - see report)
Low Glucose Suspend (LGS) = Приостановка на низкой ГК (LGS) (app "Приостановка помпы на низкой ГК")
Objectives = Цели (app); Objective N = Цель N (app); exam = тест (app "Открыть тест")

BG / blood glucose = ГК (глюкоза крови) (app; wiki 160× ГК); not "СК"/"сахар"
BG Source = Источник ГК (app still says "Источник СК" - APP bug)
BG check = Проверка ГК (app); fingerstick = измерение глюкометром
target = цель; BG target = целевое значение ГК / Целевая ГК (app)
temp target (TT) = временная цель (ВЦ) (app; short label "Врем. цель")
Eating soon TT = ВЦ «Ожидаемый прием пищи» (app); Activity = Нагрузка (app); Hypo = Гипо (app)
hypo / hypoglycemia = гипогликемия (short "гипо"); hyper = гипергликемия
carbs = углеводы (app; short "Угли" only where the app uses it); eCarbs = растянутые углеводы (eCarbs) (app)
carb absorption = усвоение углеводов (app); COB = COB (активные углеводы) (app)
insulin = инсулин; IOB = IOB (активный инсулин) (app)
basal = базал (app); basal rate = базальная скорость (app); basal profile = базальный профиль
temp basal / TBR = временный базал (app; short ВБС / "Врем. базал" only as in app labels)
bolus = болюс; extended bolus = пролонгированный болюс (app); super bolus = суперболюс (app)
SMB = SMB (супер микроболюс) (app); UAM = UAM (непредвиденный прием пищи) (app)
ISF = ISF (чувствительность к инсулину); long: Фактор чувствительности к инсулину (ISF) (app)
IC / I:C / carb ratio = IC (углеводный коэффициент) (app)
DIA = DIA (время действия инсулина) (app); peak = пик (app); Load peak from = Загрузить пик из (app)
TDD = TDD (суммарная суточная доза) (app)
Autosens = Autosens (app); Sensitivity detection = Модули чувствительности (app); sensitivity = чувствительность
Dynamic ISF = Динамический ISF (app); Autotune = Autotune (plugin name label "Автонастройка" in app)
profile = профиль; Local profile = Локальный профиль (app); Profile helper = Помощник профиля (app)
profile switch = переключение профиля (app majority; careportal/status labels say "Смена профиля" - quote as on screen)

pump = помпа (app); insulin pump = инсулиновая помпа; virtual pump = виртуальная помпа (app)
patch pump = патч-помпа (app; one string has "Разовая помпа" - APP bug); patch = патч (app)
Pod = Pod (app, Latin, e.g. "Ошибка Pod", "Pod активирован"); pod session = сессия Pod
reservoir = резервуар (app); cannula = катетер (app; "канюля" only where the app has it)
prime / fill = заполнить; Prime/Fill = Прайм/заполнение (app)
site change (pump) = смена места катетера (app "Смена места катетера помпы")
sensor change = замена сенсора; insulin change = замена инсулина; battery change = замена батареи (app)
battery = батарея (app); low battery = низкий заряд батареи (app)
disconnect pump = Отключить помпу (app); reconnect pump = подключить помпу снова / "Возобновить соединение с помпой" (app)
suspend (pump) = приостановить (Omnipod button label "Останов" in app); resume = Возобновить (app)
pair / pairing = выполнить сопряжение / сопряжение (app); unpair = разорвать сопряжение (app combov2)
Bluetooth = Bluetooth
sensor = сенсор (app, wiki 82×; not "датчик" in new text); CGM = CGM (мониторинг глюкозы)
calibration = калибровка (app); smoothing = сглаживание (app)

Settings / Preferences = Настройки (app); plugin preferences = настройки модуля
plugin = модуль (app majority "Модуль включен", "Модули чувствительности"; wiki 17× модуль)
Configuration (v4 menu) = Конфигурация (app); Config Builder (old name) = Конфигуратор (wiki)
Overview (main screen) = главный экран; Actions = Действия (app)
Treatments = Терапия (app); Treatments history = История терапии (app)
Careportal = Портал терапии (app); note = Примечание (app); announcement = Оповещение (app); exercise = Нагрузка (app)
Bolus wizard = Мастер болюса (app); QuickWizard = Мастер быстрых настроек (app + wiki)
Setup Wizard = Мастер установки (app)
Automation = Автоматизация (app); trigger = триггер (app); condition = условие (app); action = действие (app)
scene = сценарий (app label_scene_tile); preset = предустановка (app)
History browser = Просмотр журнала (app; one other "История"); Statistics = Статистика (app)
Maintenance = Обслуживание (app); log files = файлы журналов (app)
Export settings = Экспорт настроек (app); Import settings = Импортировать настройки (app)
Unattended settings export = Автоматический экспорт настроек (app)
master password = Главный пароль (app; one "мастер-пароль" - APP inconsistency)
master (Master/Client control) = основной телефон / основное устройство; client = клиент
remote control = удаленное управление (app majority "Удаленная команда", "Удаленный болюс")
remote monitoring = удаленный мониторинг; follower = фолловер (app; wiki spells фоллоуэр/фолоуер - unify to фолловер)
caregiver = опекун / родители; clinician = врач
Nightscout client / NSClient = клиент NS (app); sync = синхронизация (app); full sync = полная синхронизация (app)
upload (to NS) = загрузить (app majority; uploader = загрузчик (app)); download (a file) = скачать
alarm = сигнал тревоги; alert = оповещение (app); notification = уведомление (app)
snooze = отложить (app "Отложить оповещения AAPS")
watch / smartwatch = смарт-часы (app "Смарт-часы Wear"); watchface = циферблат (app); complication = дополнение (app)
Engineering mode = Режим отладки (app); Simple mode = Простой режим (app)
build (verb) = собрать; build (noun) = сборка; keystore = хранилище ключей (wiki); keystore file = файл хранилища ключей (.jks)
OK = OK; Cancel = Отменить (app); Save = Сохранить; Delete / Remove = Удалить; Edit = Редактировать (app)

## Links to other wiki pages
Link text that names another page must use that page's Russian title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## App labels fixed on Crowdin (local values-ru-rRU files are older; these win)
(see app_all.json)

## User decisions
- Wiki: upload only (CROWDIN_NO_APPROVE=1); the user approves. App: translate missing strings + fix clear errors (app_fix.js approves).
- Where the app and the wiki disagree, the app wins.
