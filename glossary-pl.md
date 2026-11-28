# Polish wiki glossary (AndroidAPSdocs, language pl)

Style: simple, clear Polish, short sentences, common words. Address the reader informally ("Ty"
form: "ustaw", "Twój telefon"), like the Polish app does. Write Twój/Twoja/Ciebie with a capital
letter only at sentence start (normal lowercase "twój" in running text is fine too, but stay with one
form per page; prefer lowercase). Always use Polish letters (ą ć ę ł ń ó ś ź ż) - "petle" for "pętlę"
is an error.
Numbers: decimal comma (5,5 mmol/l), no space before % (2%), en dash in ranges (1–4 godz.), quotes „…”.
Units: mg/dl, mmol/l, U (or j.) for insulin units - keep "U" as in the app; godz. / min in text.
Keep "AAPS" (never "AndroidAPS" unless the source says so). AAPS is grammatically masculine
("AAPS obliczył", "AAPS mógł"), never feminine.
UI labels: use the app's Polish string (values-pl-rPL) in bold, exactly as on screen. The app
translation is approved by a native proofreader - follow it; if a label looks clearly wrong
(typo, missing Polish letter, wrong meaning), use correct Polish and report it as APP bug.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Google Play Protect, Autosens, SMB, IOB, COB, ISF, IC, HbA1c, CGM, fork, workflow names,
Android Studio menu items, labels of English-only tools (GitHub, AAPS Builder setup page, xDrip+,
Juggluco, pump displays) - a short Polish explanation in brackets is fine.

## Objectives (app objectives.xml) = Zadania; Objective N = Zadanie N
1 Setting up visualization and monitoring = Konfiguracja wizualizacji i monitorowania
2 Learn how to control AAPS = Dowiedz się, jak kontrolować AAPS
3 Prove your knowledge = Sprawdź swoją wiedzę
4 Starting on an open loop = Rozpoczęcie w trybie otwartej pętli
5 Understanding your open loop = Zrozumienie działania otwartej pętli
6 Starting to close the loop (Low Glucose Suspend) = Rozpoczęcie zamknięcia pętli z zawieszeniem przy niskiej glikemii
7 Tuning the closed loop = Dostrajanie zamkniętej pętli
8 Adjust basals and ratios, enable Autosens = Dostosowanie bazy i współczynników, włączenie Autosens
9 Enabling SMB = Włączenie SMB (Super Mikro Bolusy)
10 Automation = Włączanie automatyzacji
(Short wiki titles may shorten these; the bold app text must be exact when quoting the screen.)

## Terms (app words win)
loop = pętla; closed loop = zamknięta pętla (app: Zamknięta pętla); open loop = otwarta pętla
hybrid closed loop = hybrydowa zamknięta pętla; full closed loop = w pełni zamknięta pętla
BG / blood glucose = glikemia / poziom glukozy (BG label stays "BG" as in the app); target = cel
glucose = glukoza; carbs = węglowodany (app short: Węgl.); basal = baza (app); basal rate = dawka bazowa
temp basal / TBR = baza tymczasowa (TBR); insulin = insulina; pump = pompa; insulin pump = pompa insulinowa
hypo = hipoglikemia; ketoacidosis = kwasica ketonowa
clinician = lekarz; clinical team = zespół diabetologiczny; caregiver = opiekun / rodzic; follower = obserwujący
build (verb) = zbudować; Building AAPS = Budowanie AAPS; Browser build = Budowanie w przeglądarce
keystore = magazyn kluczy (keystore); signing key = klucz podpisu; repository = repozytorium
release notes = informacje o wersji; reporting server = serwer raportów
Temporary target = Cel tymczasowy (TT); Profile switch = Zmiana profilu; Treatments = Leczenie (app)
Careportal = Portal opieki (app); Extended bolus = Bolus Przedłużony (app)
Settings / Configuration = Ustawienia (app); Running mode = Tryb pracy (app); Automation = Automatyzacja
plugin = wtyczka; Open plugin = Otwórz wtyczkę; Maintenance = Konserwacja; Statistics = Statystyki
Low Glucose Suspend (LGS) = zawieszenie przy niskiej glikemii (LGS)
Pair with master = Sparuj z telefonem pacjenta (app)
DIA = czas działania insuliny (DIA); ISF = współczynnik wrażliwości na insulinę (ISF); IC = współczynnik insulina/węglowodany (IC)
smartwatch = smartwatch; watchface = tarcza zegarka; complication = komplikacja (complication)
Troubleshooting = Rozwiązywanie problemów; virtual pump = wirtualna pompa
MUST / MUST NOT = MUSISZ / NIE WOLNO; grams = g
SMS commands, JSON keys, anchors, code: unchanged.

## From the translated pages
OS-AID = OS-AID (short Polish explanation in brackets at first use)
branch = gałąź; master branch = gałąź „master”; development branch = gałąź rozwojowa (development)
controller (APS) = sterownik; sensor = sensor (not czujnik); dual-hormone system = system dwuhormonalny
SGLT-2 inhibitors (gliflozins) = inhibitory SGLT-2 (flozyny)
objectives = zadania (never "cele"); task inside an objective = krok
master (client control) = urządzenie pacjenta (app; "telefon pacjenta" in Pair with master); client = urządzenie opiekuna
Manage sheet = panel Zarządzaj; Treatments sheet = panel Leczenie; main screen = ekran główny
bottom navigation = dolny pasek nawigacji; Loop dialog = okno pętli; chip = element; chevron = strzałka
QuickLaunch toolbar = pasek QuickLaunch; notification bubble = dymek powiadomień
simple mode = Tryb uproszczony (app); hardened mode = tryb silniejszych zabezpieczeń (app)
Hint = Podpowiedź; Save (icon) = Zachowaj (app ZACHOWAJ)
peak (insulin) = szczyt; fingerstick = pomiar z palca; zero-temping = zerowa baza tymczasowa
prediction line = linia prognozy; deviations = odchylenia; bread units = wymienniki węglowodanowe (WW)
build variant = wariant budowania; new UI (Android Studio) = nowy interfejs
uploader = uploader (program wysyłający dane); status lights = wskaźniki stanu (app)
Super Micro Bolus = Super Mikro Bolus; UAM = nieogłoszone posiłki (UAM); pre-bolus = bolus przed posiłkiem
hard limit = twarde ograniczenie; sensitivity ratio (Autosens) = współczynnik wrażliwości (always add
"(ISF)" to the ISF term so the two do not mix); Loop mode = Tryb pętli; Engineering Mode = Tryb Inżynierski
dev build = wersja rozwojowa (dev); Insulin on Board = aktywna insulina (IOB)
CSF = współczynnik wrażliwości na węglowodany (CSF); Dynamic ISF = Dynamiczny ISF (app); Insulin Divisor = dzielnik insuliny
eventual BG = końcowa BG (eventual BG); trigger = wyzwalacz; action = akcja; rule = reguła
Temp targets in text: cel tymczasowy Ćwiczenia / Wkrótce posiłek / Hipo; on the TT screen use app labels
(Aktywność / Wkrótce posiłek / Hipo). Scene = scena; follow-up scene = następna scena; tile = kafelek
Low/High target = Dolny/Górny cel; time blocks = bloki czasowe; Profile helper = Pomocnik profilu (app)
transmitter = nadajnik (not transmiter); receiver = odbiornik; patched app = zmodyfikowana aplikacja (patched)
bridge = mostek (bridge); backfill = uzupełnianie brakujących danych; warm-up = rozgrzewanie
release notes = Informacje o wersji (page title); IU = jednostki międzynarodowe (IU)
Authorized clients = Autoryzowani opiekunowie (Manage label; screen title differs in app)

## Links to other wiki pages
Link text that names another page must use that page's Polish title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate the title and add
`Folder/Page.md = title` to your <name>.terms.txt.

## App labels fixed on Crowdin (local values-pl-rPL files are older; these win)
Configuration (screen, nav_configuration) = Konfiguracja (was "Ustawienia", same as Settings)
Settings = Ustawienia; Preferences = Ustawienia
Prime/Fill = Napełnij/Wypełnij (was "Rozpocznij/Wypełnij")
Suspend loop = Wstrzymaj pętlę; Disable loop = Wyłącz pętlę (was "pętle")
QuickWizard = Szybki kalkulator everywhere (management item was "Szybki bolus")
Config Builder page title = Konfiguracja (Config Builder)

## Final decisions (consistency pass)
Config Builder (alone) = Config Builder (never "Kreator konfiguracji" = Setup Wizard)
custom watchface = własna tarcza (app labels with "niestandardowa" are quoted as they are)
client control in text = sterowanie z urządzenia opiekuna; treatment entries = wpisy leczenia (not "zabiegi")
Google Drive = Dysk Google; branch = gałąź; release notes = Informacje o wersji
Pod (Omnipod) = Pod with capital letter (Poda, Podem, w Podzie); Deactivate Pod button = Odłącz Poda (app)
ExportImportSettings title = Tworzenie i przywracanie kopii zapasowych

## User decisions
- Wiki: upload and approve. App: missing strings + clear errors only (native proofreader's work).

## Added 2026-11-07 (ConfigBuilder rewrite, AAPSClient iOS)
Configuration (v4 menu) = Konfiguracja (not "Ustawienia")
websockets = websockety (older approved strings on AapsClient/RemoteControl still say "websockets"; align later)
mandatory = obowiązkowe; Tab switcher = Przełącznik kart
macOS labels: Gotowe / Ustawienia systemowe → Prywatność i ochrona / Otwórz mimo to (English kept in brackets)
GitHub/Google button labels stay English

## Added 2026-11-08 (Dexcom Notification Reader, alarm settings)
Notification Reader = Odczyt powiadomień (app; in text "źródło BG Odczyt powiadomień", not declined)
Permissions Required = panel Wymagane uprawnienia; Notification Access = Dostęp do powiadomień; Grant = Przyznaj (app)
trend arrow = strzałka trendu; Vibration = Wibracje
Android labels: Polish text + English in brackets, e.g. (Allow notification access), as on G7
