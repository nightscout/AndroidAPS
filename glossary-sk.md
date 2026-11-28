# Slovak wiki glossary (AndroidAPSdocs, language sk)

Style: simple Slovak (spisovná slovenčina, not Czech), short sentences, common words, formal "vy"
written lowercase (vy, váš, vám - not Vy/Váš). Watch for Czech words that slip in: doporučiť ->
odporučiť, zariadení -> zariadenie, potřeba -> potreba, už/ještě -> už/ešte, který -> ktorý.
Numbers: decimal comma (11,1 mmol/l), space before % (2 %), en dash in ranges (1–4 hodiny),
quotes „…“. Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Slovak string (values-sk-rSK) in bold, exactly as on screen. If the app
string is clearly wrong, use the correct Slovak and report it as APP bug.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Google Play Protect, Autosens, SMB, HbA1c, CGM, fork, workflow names, Android Studio
menu items, labels of English-only tools (GitHub, AAPS Builder setup page, xDrip+, Juggluco, pump
displays) - a short Slovak explanation in brackets is fine.

## Objectives (app objectives.xml, short wiki titles)
Objectives = Ciele; Objective N = Cieľ N (app "%1$d. cieľ")
1 Setting up visualization and monitoring = Nastaviť vizualizáciu a monitoring
2 Learn how to control AAPS = Naučte sa, ako ovládať AAPS
3 Prove your knowledge = Preukážte svoje znalosti
4 Starting on an open loop = Začať s otvoreným okruhom
5 Understanding your open loop = Porozumieť otvorenému okruhu
6 Starting to close the loop (Low Glucose Suspend) = Spustiť uzavretý okruh (Ochrana pred nízkou glykémiou)
7 Tuning the closed loop = Ladenie uzavretého okruhu
8 Adjust basals and ratios, enable Autosens = Upraviť bazály a pomery, povoliť Autosens
9 Enabling Super Micro Bolus (SMB) = Povoliť Super Micro Bolus (SMB)
10 Automation = Automatizácia

## Terms (app words win)
loop / looping = okruh (app: Uzavretý okruh / Otvorený okruh); closed loop = uzavretý okruh;
open loop = otvorený okruh; hybrid closed loop = hybridný uzavretý okruh; full closed loop = plne uzavretý okruh
looper = používateľ okruhu
BG / blood glucose = glykémia; glucose target = cieľová glykémia; target = cieľ
carbs = sacharidy; basal = bazál; basal rate = bazálna dávka; background insulin = bazálny inzulín
temp basal / TBR = dočasný bazál (TBR)
insulin = inzulín; pump = pumpa; insulin pump = inzulínová pumpa
hypo / hypoglycemia = hypoglykémia; ketoacidosis = ketoacidóza
type 1 diabetes / T1D = diabetes 1. typu; diabetes management = liečba diabetu
clinician = lekár; clinical team = lekársky tím; caregiver = opatrovateľ; follower = sledujúci
build (verb) = zostaviť; build (noun) = zostavenie; Building AAPS (page) = Zostavenie AAPS
Browser build = Zostavenie v prehliadači; Android Studio build = Zostavenie v Android Studiu
Command-line build = Zostavenie z príkazového riadku
keystore = úložisko kľúčov; keystore file = súbor úložiska kľúčov (.jks); signing key = podpisový kľúč
repository = repozitár; preparation file = prípravný súbor
reporting server = reportovací server; release notes = poznámky k vydaniu
FAQ = Časté otázky; Troubleshooting = Riešenie problémov (app)
Google Drive = Disk Google; developer mode = režim vývojára
SGLT-2 inhibitors = SGLT-2 inhibítory; gliflozins = gliflozíny
Temp target = Dočasný cieľ; Profile switch = Prepnutie profilu; Treatments = Ošetrenia
Careportal (app section) = Starostlivosť (user decision; app strings "portál starostlivosti"/"Ošetrenia" for Careportal get aligned); Treatments = Ošetrenia; Nightscout Careportal web page = Careportal
Extended bolus = Predĺžený bolus; Settings = Nastavenia; Configuration = Konfigurácia
Preferences (settings screen) = Nastavenia (app string search_category_preferences is wrong)
Manage = Spravovať; Insulin settings = Nastavenia inzulínu; Peak = Vrchol
DIA = Doba pôsobenia inzulínu (DIA)
virtual pump = virtuálna pumpa; Automation = Automatizácia; plugin = plugin (app "Pluginy")
Open plugin = Otvoriť plugin; Add = Pridať; Resume = Obnoviť; Food (Manage item) = Jedlo
ISF = citlivosť (ISF); I:C = inzulínovo-sacharidový pomer (I:C); CR = sacharidový pomer (CR)
Low Glucose Suspend = Ochrana pred nízkou glykémiou (LGS)
Running mode = Režim prevádzky (app; the app also has "Režim spustenia" - prefer Režim prevádzky)
Time in Range = Čas v rozsahu (app); Setup Wizard = Sprievodca nastavením (app)
Simple mode = Jednoduchý mód (app); Sensitivity detection = Detekcia citlivosti; Hint = Nápoveda
Full sync = Úplná synchronizácia; Data choices = Možnosti dát; Log files = Log súbory (app)
Silence Alerts (Omnipod) = Stíšiť alarmy (app)
smartwatch = inteligentné hodinky (approved pages use it; not "smart hodinky"); watchface = ciferník; complication = komplikácia
SMS commands, JSON keys, anchors, code: unchanged.

## Links to other wiki pages
Link text that names another page must use that page's Slovak title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate the title and add
`Folder/Page.md = title` to your <name>.terms.txt.

## Decided after the pilot
Low Glucose Suspend / LGS = Ochrana pred nízkou glykémiou (LGS) (app has two variants; app gets aligned to this)
Google Drive = Disk Google (app says "Google Disk"; app gets aligned)
Load peak from = Načítať vrchol z (app "Nahraj vrchol z" uses informal ty-form; app gets fixed)
FAQ page link text = the FAQ page's own title from titles.txt
full looping = plné používanie okruhu; Medical safety = Zdravotná bezpečnosť

## Pilot terms
full looping = plné používanie okruhu
Medical safety = Zdravotná bezpečnosť
Medical considerations = Zdravotné hľadiská
Technical (heading) = Technické hľadiská
Section For Clinicians = Sekcia pre lekárov
Android developer verification = overovanie vývojárov pre Android
Samsung developer mode path = Nastavenia > Informácie o telefóne > Informácie o softvéri > Číslo zostavy; Možnosti vývojára; Automatické aktualizácie systému
self-built app = vlastnoručne zostavená aplikácia

## Added after batch 1 (agent choices; app labels win)
additional communication device = ďalšie komunikačné zariadenie
Tips for basic usage (Combo) = Tipy na základné používanie
OS-AID = OS-AID (automatizované podávanie inzulínu s otvoreným zdrojovým kódom); OS-AID loop = okruh OS-AID
open-source = s otvoreným zdrojovým kódom (open-source also used in older approved strings)
open pumping / open-loop pumping = liečba pumpou bez okruhu
followers = sledujúci (not "sledovatelia")
remote monitoring = vzdialené sledovanie; remote control = vzdialené ovládanie (app)
precedent = precedens
controller (APS part) = riadiaca jednotka
dual-hormone = dvojhormonálny
functional cure = funkčné vyliečenie
development branch = vývojová vetva; master version = verzia master / hlavná verzia
smartwatch = smart hodinky
smartwatch = inteligentné hodinky (used across the whole approved Watches page and its title; glossary says "smart hodinky" - coordinator to decide)
companion app = sprievodná aplikácia
External Companion Apps (app label) = Externé sprievodné (Companion) aplikácie
insulin on board / IOB = aktívny inzulín (IOB)
full Android watch = plnohodnotné hodinky s Androidom
standalone (watch) = samostatné (hodinky)
broadcast (data) = vysielanie
follower / following only = iba sledovanie
Wear OS watch face changes (anchor wearos-watchface-changes) = Zmeny ciferníkov vo Wear OS
WearOS/WearOsSmartwatch.md link "Set up AAPS on your Wear OS watch" = Nastavenie AAPS na hodinkách s Wear OS
preparation page = prípravná stránka; preparation file = prípravný súbor
secret / secrets (GitHub) = secret / secrets (first use: „secrets (tajné hodnoty)“)
tap = ťuknite; click = kliknite
repository = repozitár (never „úložisko“, which is reserved for keystore = úložisko kľúčov)
Step N = krok N; Option N = možnosť N
fork (noun) = fork; create a fork = vytvoriť fork
cherry-pick a commit = Cherry-pick commitu
build log = log zostavenia
GitHub / prep page / Simple Web Server labels kept English: Settings, Secrets and variables, Actions, New repository secret, Name, Secret, Add secret, Run workflow, Use workflow from, Select AAPS Version, Create Fork, Repository secrets, Google Drive Auth, Start Auth, Copy, Get Started, New Server, Folder Path, Create Server
setup page (AAPS Builder) = stránka nastavenia
build workflow = workflow zostavenia (workflow undeclined; plural workflowy)
GitHub secret = secret (secretu, secrety)
build page (GitHub) = stránka zostavenia
Tap = Ťuknite na; Click = Kliknite na
green check = zelená fajka; red cross = červený krížik
Google refresh token expired = Platnosť obnovovacieho tokenu Google vypršala
Option 1 – Computer = Možnosť 1 – Počítač
Chrome menu: Downloads = Stiahnuté súbory; Install = Inštalovať; Open in new tab = Otvoriť na novej karte
Android: Settings → Apps → Open by default → Open supported links = Nastavenia → Aplikácie → Otvárať predvolene → Otvárať podporované odkazy (English in brackets)
settings file = súbor s nastaveniami
max bolus = maximálny bolus
average meal bolus + 3x max daily basal = priemerný bolus k jedlu + 3x maximálny denný bazál
zero-temping = nulový dočasný bazál (zero-temping)
Hint (task link) = Nápoveda
Reset finished state = Vynulovať stav dokončenia
Reset started state = Vynulovať stav spustenia
Where did the Actions tab go? = Kam zmizla karta Akcie?
loop icon = ikona okruhu
hardened mode = režim zvýšeného zabezpečenia (app)
client control = ovládanie z klienta (app)
Nightscout pills = rámčeky s údajmi (v Nightscoute)
Security considerations = Bezpečnostné hľadiská
Tidepool uploader = nástroj Tidepool uploader

## Added after batch 2
Android Studio labels kept English: Projects, Clone Repository, Get from Version Control, Trust project, File > Settings, Preferences (Mac), Appearance & Behavior, New UI, Enable new UI, Version Control, Path to Git executable, Test, Build, Generate Signed App Bundle / APK, Module, Next, Create, locate, Automatically (Windows Defender), Android Virtual Device
Android Studio new UI = nové používateľské rozhranie
image alt texts with underscores (Git_installed, Choose_components ...) = copied unchanged
WearOS/WearOsSmartwatch.md (link text "Wear OS smartwatch") = Inteligentné hodinky s Wear OS
Key AAPS Features (OpenAPS features page, link text) = Kľúčové funkcie AAPS
Config Builder (v3 name) = Config Builder (kept English, now "Konfigurácia")
Manage sheet = panel Spravovať
synced from the master / master = synchronizované z hlavného zariadenia; master = hlavné zariadenie (master)
mobile icon = ikona mobilu
insulin editor = editor inzulínu
preset (Load peak from) = predvoľba
custom peak = vlastný vrchol
fingerstick entry = meranie z prsta
slope / offset (calibration) = sklon / posun
sensor session = relácia senzora; warm-up = zahrievanie
pump driver = ovládač pumpy
engineering (developer) build = vývojárske (engineering) zostavenie
high-temp (verb) = nastaviť vysoký dočasný bazál
super bolus = superbolus
expandable sections = rozbaľovacie časti
read-only = iba na čítanie
search bar = vyhľadávací riadok
read-only = iba na čítanie
management screens = obrazovky správy
QuickLaunch buttons = tlačidlá Rýchleho spustenia
preset (temp target / quick wizard) = predvoľba
grace period = lehota
uploader (Nightscout) = nástroj na nahrávanie (dát)
Admin token (Nightscout) = token s rolou Admin
Simple mode = Jednoduchý mód (approved strings on this page that said "jednoduchý režim" were changed)
Super Micro Bolus = Super Micro Bolus (SMB) (kept English, approved "super mikro bolus" changed)
Settings > X (image captions) = Nastavenia > X
WearOS/WearOsSmartwatch.md (link text "Wear OS smartwatch") = Inteligentné hodinky s Wear OS
UsefulLinks/DedicatedGoogleAccountForAaps.md (link text "dedicated Google account") = samostatný účet Google
AapsScreens loop-status link "AAPS screens > The main screen > Loop status" = Obrazovky AAPS > Hlavná obrazovka > Stav okruhu
Permissions Required (panel) = Požadované povolenia
Grant (button) = Povoliť
NEXT / Next (wizard button) = ĎALEJ / Ďalej
FINISH (wizard button) = DOKONČIŤ
tick (permission granted) = fajka
Profile editor tabs IC / ISF / BAS / TARG = I:C / ISF / BAZ / CIEĽ (app)
insulin editor = editor inzulínu; nickname = prezývka
save icon = ikona uloženia
Android system labels (Apps, See all apps, Show system, Battery, App battery usage, Unrestricted, Allow background usage) = Aplikácie, Zobraziť všetky aplikácie, Zobraziť systémové, Batéria, Využitie batérie aplikáciou, Bez obmedzení, Povoliť používanie na pozadí (English in brackets)
USE THIS FOLDER / ALLOW (Android file picker) = POUŽIŤ TENTO PRIEČINOK / POVOLIŤ (English in brackets)
master (phone, client control) = master
Export & import settings (link text) = Export a import nastavení
web installer = webový inštalátor
free installation methods = bezplatné spôsoby inštalácie
advanced flow (Android) = rozšírený postup (advanced flow)
limited distribution account = účet s obmedzenou distribúciou (limited distribution account)
Apps from unverified developers = Aplikácie od neoverených vývojárov (Apps from unverified developers)
Allow apps from unverified developers = Povoliť aplikácie od neoverených vývojárov
Android system labels: About phone = Informácie o telefóne; Software information = Informácie o softvéri; Build number = Číslo zostavy; Developer options = Možnosti vývojára; USB debugging = Ladenie cez USB; Allow USB debugging? = Povoliť ladenie cez USB?; wireless debugging = bezdrôtové ladenie; Allow from this source = Povoliť z tohto zdroja; Install = Inštalovať; Open = Otvoriť; Done = Hotovo
Play Protect: More details = Ďalšie podrobnosti (More details); Install anyway = Napriek tomu inštalovať (Install anyway)
Google Drive web: New = Nový (New); File upload = Nahrať súbor (File upload)
package name = názov balíka
sideloading = „sideloading“ (inštalácia zo súboru APK)

## Added after batch 3
school care plan = školský plán starostlivosti
treatment plan = liečebný plán
SettingUpAaps/YourAapsProfile.md (link text "Your AAPS Profile") = Váš profil AAPS
Maintenance/Reviewing.md (link text "Reviewing your data") = Kontrola údajov
Clinician's guide = Príručka pre lekárov
Main screen = Hlavná obrazovka
Section A/B/... = Sekcia A/B/...
Top bar = Horná lišta; status block = blok stavu; status row = riadok stavu; bottom navigation = dolná navigácia
menu (☰) = ponuka
navigation drawer = navigačný panel
chip (graph/IOB chip) = rámček / údaj (IOB)
folded / expanded row = zbalený / rozbalený riadok; chevron = šípka
Loop dialog = dialóg okruhu
QuickLaunch toolbar = lišta Rýchleho spustenia
Treatments sheet = panel Ošetrenia
notification bubble = bublina notifikácií
prediction lines = predikčné čiary
eventual BG = konečná glykémia
press and hold = stlačte a podržte
badge = odznak
careportal entries = záznamy ošetrení
Eating soon (TT) = Pred jedlom (app); Start Eating soon TT = Spustiť dočasný cieľ Pred jedlom
Set alarm (wizard) = Nastaviť upozornenie
Bolus will be recorded only = Bolus bude iba zaznamenaný (nie pumpou vydaný) (app)
teal = modrozelený; cyan = azúrová
trigger = spúšťač; rule = pravidlo; condition = podmienka; action = akcia
Recurring time = Opakovaný čas (app, new label)
Low Glucose Temp Target (example heading) = Dočasný cieľ pri nízkej glykémii
Automation Triggers / Actions = Spúšťače automatizácie / Akcie automatizácie
Automation Logs = Log automatizácie
location service = služba určovania polohy
unattended export = automatický export
(removed: Careportal = Starostlivosť, see Terms)
Loop & insulin (action group) = Uzavretý okruh &amp; inzulín; Alerts group = Výstrahy
Autotune profile (action) = Profil Autotune
Days M,T,W,T,F = Po, Ut, St, Št, Pi
Dynamic ISF = Dynamická ISF (feminine, as the app; DynISF also feminine)
Static ISF = Statická ISF
Future ISF = budúca ISF
Adjustment Factor (DynISF) = korekčný faktor (app: Korekčný faktor pre DynISF)
insulin divisor = inzulínový deliteľ
predicted BG / minimum predicted BG = predpovedaná glykémia / minimálna predpovedaná glykémia
eventual BG = výsledná glykémia
Profile Percentage = Percento profilu
fallback (value) = náhradná hodnota
VSENS graph = VSENS (premenlivá citlivosť)
eCarbs = eSacharidy (app); extended carbs = predĺžené sacharidy
carbs type Bread / Cake / Pizza = Chlieb / Koláč / Pizza (app)
Carb time = Čas sacharidov (app)
Carb Sensitivity Factor (CSF) = faktor citlivosti na sacharidy
absorption (carbs) = vstrebávanie
Treatments history = História ošetrení (app); Carbs and bolus tab = Sacharidy a bolusy
selection mode = režim výberu; long-press = dlho podržte
insulin stacking = kumulovanie inzulínu (insulin stacking)
multiwave bolus = viacvlnový (multiwave) bolus
chips (UI) = štítky (chips)
bottom sheet = spodný panel
Loop mode = Režim okruhu
hard-coded limits = pevne nastavené limity
sensitivity ratio = pomer citlivosti
Teenager (patient age) = Dospievajúci (app); Pregnant = Tehotenstvo (app); Insulin resistant adult = Dospelý s nízkou citlivosťou (app)
start bolus = úvodný bolus
low temps / zero-temps = nízke dočasné bazály („low temps“) / dočasné bazály 0 U/h („zero-temps“)
Bolus snooze DIA divisor = Deliteľ bolus snooze DIA (app)
Current basal safety multiplier = Bezpečnostný násobiteľ aktuálneho bazálu (app)
Max daily safety multiplier = Maximálny bezpečnostný násobiteľ najvyššieho denného bazálu (app)
Engineering Mode = Engineering Mode (vývojársky režim)
dev build = vývojárske zostavenie (dev)
mg/dL = mg/dl
profile carousel = karusel profilov
Reuse (button) = Znovu použiť (app)
read-only (screen) = iba na čítanie
page indicator = ukazovateľ strán
Positive / Negative time shift = kladný / záporný posun času
snapshot (of the profile) = snímka
UI (insulin unit in tables) = U
scene = scéna; follow-up scene = nadväzujúca scéna; chain into = nadviazať na; end action = akcia po skončení
Care-portal note = poznámka v Ošetreniach; Loop mode = režim okruhu
tile (Wear OS) = dlaždica; tile carousel = karusel dlaždíc; slot (tile) = pozícia
bottom navigation = spodná navigačná lišta; sheet = panel
Sensitivity algorithm = Algoritmus citlivosti
Sensitivity AAPS / Oref1 / WeightedAverage (plugins) = Citlivosť AAPS / Citlivosť Oref1 / Citlivosť vážený priemer (app; in running text WeightedAverage kept English like the EN)
site change = výmena miesta vpichu
un-announced meals (UAM) = neohlásené jedlá (UAM)
TT (temp target abbreviation) = DC (as in the app: DC Aktivita, DC Hypo)
Hypo / Activity / Eating soon Temp-Target (headings) = Dočasný cieľ Hypoglykémia / Aktivita / Pred jedlom (app preset names)
target chip (main screen) = rámček cieľa
preset carousel = karusel predvolieb
action bar = panel akcií
top toolbar = horný panel nástrojov
built-in / custom preset = vstavaná / vlastná predvoľba
factory defaults = výrobné (predvolené) hodnoty
TT toggles in the Carbs dialog = prepínače DC v dialógu Sacharidy
Wear OS watch = hodinky s Wear OS
pre bolus = bolus vopred (pre-bolus)
QuickLaunch = Rýchle spustenie; QuickLaunch toolbar = panel Rýchleho spustenia
quick-action toolbar = panel rýchlych akcií; quick-action button = tlačidlo rýchlej akcie
QuickWizard (feature/button) = Rýchly sprievodca; QuickWizards (plural) = predvoľby Rýchleho sprievodcu
QuickLaunch categories (app) = Ošetrenie, Portál starostlivosti, Spravovať, Rýchly sprievodca, Scény, Automatizácia, Predvoľby dočasného cieľa, Profil, Pluginy
Prime/Fill = Plnenie/doplňovanie (app)
Pump site change / Insulin cartridge change (Fill dialog checkboxes) = Zaznamenať výmenu setu / Zaznamenať výmenu inzulínu (app)
set change = výmena setu; patch pump = náplasťová pumpa; tube pump = pumpa s hadičkou
Site Rotation = Striedanie miest (app); View mode / Edit mode = režim zobrazenia / režim úprav
Search categories (app) = Pluginy, Dialógy, Kategórie nastavení, Nastavenia, Dokumentácia
global search = globálne vyhľadávanie
widget picker = ponuka widgetov; Android "Widgets" = Miniaplikácie (Widgets); Add = Pridať (Add)
Widget names (app) = AAPS widget, Kruhové zobrazenie glykémie AAPS, AAPS Malé, AAPS GLY + IOB + COB (kompaktné), Graf glykémie AAPS
DST / daylight saving = letný čas (DST); timezone = časové pásmo; Time shift = Posun času (app)
Android "Set automatically" / "Time zone" = Nastaviť automaticky (Set automatically) / Časové pásmo (Time zone)
Master ↔ Client control (link text) = Ovládanie Master ↔ Client
USB debugging = ladenie cez USB (Android: Ladenie cez USB)
Allow USB debugging? / Always allow from this computer = Povoliť ladenie cez USB? / Vždy povoliť z tohto počítača
About phone / Build number / Developer options = Informácie o telefóne / Číslo zostavy / Možnosti vývojára
data-capable USB cable = kábel USB, ktorý prenáša dáta (dátový kábel)
Android developer verification = overovanie vývojárov pre Android
BG (bold abbreviation in text) = glykémia (declined: glykémie, glykémiu, glykémii)
ISF = ISF (masculine: príliš silný / slabý ISF); insulin sensitivity factor = faktor citlivosti na inzulín
ICR / IC = ICR / IC (masculine); insulin-to-carb ratio = inzulínovo-sacharidový pomer
stronger / weaker (ISF, ICR) = silnejší / slabší
TDD (formula) = CDD (TDD) (app: "TDD Total" = "CDD celkom")
Profile helper = Pomocník s profilom (app)
Profile type = Typ profilu; Default profile = Predvolený profil; DPV Default profile = Predvolený DPV profil; Available profile = Dostupný profil (app)
Comparation (tab) = Porovnanie (app)
profile editor tabs IC / ISF / BAS / TARG = I:C / ISF / BAZ / CIEĽ (app)
schedule (profile) = časový plán; time block = časový blok
eating soon (temp target) = Pred jedlom (app)
pre-bolus = predbolus
bread unit = chlebová jednotka (BU kept)
Receive profile store from NS = Prijímať úložisko profilov z NS (app)
Nightscout data acceptance = Prijímanie dát z Nightscoutu (app)
Managing and activating profiles (anchor ProfileSwitch-manage-v4) = Správa a aktivácia profilov
search bar = vyhľadávacie pole
zero temp = nulový dočasný bazál


## Added after batch 4 (agent choices; User decisions section still wins)
Nearby devices (Android permission) = Zariadenia v okolí
Android Security and privacy / Privacy controls / Permission manager = Zabezpečenie a ochrana súkromia / Ovládanie ochrany súkromia / Správca povolení (English in brackets)
Don't allow / Don't allow anyway / Show system (Android) = Nepovoliť / Aj tak nepovoliť / Zobraziť systémové
revoke permission = odobrať povolenie
Bluetooth bonding (Dash setting) = Bluetooth párovanie (app)
Bluetooth Battery Optimisation (heading) = Optimalizácia batérie pre Bluetooth
three-dot menu = ponuka s tromi bodkami
Where to get help for dash = Kde získať pomoc pre Dash
GitHub copy of AndroidAPS = kópia AndroidAPS na GitHube
run (workflow run) = beh; job = úloha; Annotations / Failure / Re-run jobs / Re-run failed jobs / Update secret = kept English
grey tick = sivá fajka; grey circle = sivý krúžok
Google account labels kept English with Slovak in brackets: Security (Zabezpečenie), Your connections to third-party apps & services, Delete all (Odstrániť všetko), See details (Zobraziť podrobnosti), Remove all access (Odstrániť všetok prístup), Confirm (Potvrdiť)
local server = lokálny server
escape character = únikový (escape) znak; backslash = spätná lomka
CompatiblePhones/Huawei.md (link text "Huawei Bluetooth & battery optimization") = Huawei: Bluetooth a optimalizácia batérie
Stuck High / Stuck Low (ProfileTuning) = Glykémia zostáva vysoko / Glykémia zostáva nízko
Rollercoaster = Horská dráha
strong / weak Profile = silný / slabý profil
basal stock = zásoba bazálu
Start insulin / End insulin = počiatočný inzulín / konečný inzulín
wizard (bolus) = kalkulačka (app)
Not optimized (Android, older) = „Neoptimalizované“ (Not optimized)
reconnection = opätovné pripojenie
NSClient status connected = „Pripojené“ (app "connected")
free tier (MongoDB) = bezplatná verzia (free tier)
Maintenance/ExportImportSettings.md (link text "Creating back-ups") = Vytváranie záloh (full title proposal: Vytváranie a obnova záloh)
Tubeless pumps (ExportImportSettings anchor) = Pumpy bez hadičky
Settings Export (section) = Export nastavení
AAPS 3.4 labels (master branch app): Export Options = Nastavenia exportu; User Settings = Užívateľské nastavenia; Local Storage = Lokálne úložisko; Cloud Storage = Cloudové úložisko; Export settings (Local) = Exportovať nastavenia (lokálne)
Local (chip) = Lokálne; master password = hlavné heslo
Uninstall (Android) = Odinštalovať (Uninstall)
Use this folder / Allow (Android picker, mixed case) = Použiť tento priečinok (Use this folder) / Povoliť (Allow)
Full sync finished (NSClient log) = kept English
keep looping = pokračovať v používaní okruhu
Gradle Sync = Gradle Sync / synchronizácia Gradle; Gradle Resync = opätovná synchronizácia Gradle
uncommitted changes = necommitnuté zmeny
Commit tab = karta Commit
Lost keystore = Stratené úložisko kľúčov
Month headings = Január, Február, Marec, Apríl, Máj, Jún, Júl, August, September, Október, November, December + year (e.g. Október 2024)
UsefulLinks/Glossary.md (link text) = Slovník
Getting-Started/Introduction.md (link text "Introduction") = Úvod
GettingHelp/ProfileTuning.md (link text "Profile tuning") = Ladenie profilu
Updating AAPS (aaps-builder-updates section) = Aktualizácia AAPS
Computer Build (old name) = Zostavenie v počítači
patched app = upravená aplikácia
transmitter = vysielač
Time zone traveling (link text) = Cestovanie medzi časovými pásmami
language switch = prepínač jazyka
back-up / backup = záloha; to back up = zálohovať
master password = hlavné heslo
AAPS directory = AAPS priečinok (app)
Local / Cloud chips = štítky Lokálne / Cloud (app)
Cloud Directory = Cloudový adresár (app)
Export settings / Import settings = Exportovať nastavenia / Importovať nastavenia (app)
Also replace pump settings = Nahradiť aj nastavenia pumpy (new app label)
Decrypt & Review = Dešifrovať a skontrolovať (new app label)
Settings / Preferences file = súbor Nastavenia / súbor s nastaveniami
tubeless pumps = pumpy bez hadičky
pod session = relácia podu
Access to the AAPS directory was lost (notification) = Prístup do priečinka AAPS bol stratený
end date (version) = dátum ukončenia (podpory); End of life = Koniec podpory
API level = úroveň API
update guide = návod na aktualizáciu
Building Instructions (link text) = návod na zostavenie
Incompatible Gradle JVM (section name) = kept English + (nekompatibilná JVM pre Gradle)
About (menu) = O aplikácii (app)
Maintenance/UpdateToNewVersion.md (link text "Update to a new version") = Aktualizácia na novú verziu
Release date: DD-MM-YYYY = Dátum vydania: DD.MM.YYYY
Version X = Verzia X
update manual = návod na aktualizáciu
Unattended settings export = Automatický export nastavení (app)
AAPS directory = AAPS priečinok (app)
Pod activation (trigger) = Aktivácia Podu (app)
master password = hlavné heslo (app)
Authenticator setup = nastavenie aplikácie Authenticator
DynISF Adjustment Factor = korekčný faktor DynISF
websockets = websockety
uploader (NS) = nástroj na nahrávanie
Client roles = Roly klientov; Read-only / Meals / Caregiver / Admin = Iba na čítanie / Jedlá / Opatrovateľ / Správca (app)
Phone alarms on watch: Silence / Mute 5 min = Stíšiť / Stlmiť na 5 min (wear strings not yet translated in sk)
time to eat (watch reminder) = čas jesť
pump emulator = emulátor pumpy
screen reader = čítačka obrazovky
grace period = lehota
History browser = Prehliadač histórie (app)
Statistics (screen/menu item) = Štatistika (app)
Statistics tabs Insulin / Time in Range / Glucose / Cycle = Inzulín / Čas v rozsahu / Glykémia / Cyklus (app)
date card = karta s dátumom
TDD = CDD (app); Total Daily Dose = Celková denná dávka (app)
TBB (total base basal) = celková základná bazálna dávka (TBB)
Time In Tight Range (TITR) = čas v úzkom rozsahu (TITR)
Ambulatory Glucose Profile = Ambulantný glukózový profil (app)
LOW mark / HIGH mark = Dolná značka / Horná značka (app)
Range for visualization = Rozsah pre zobrazenie (app)
SD = smerodajná odchýlka; CV = variačný koeficient
Glucose Pentagon (CGP) = Glucose Pentagon (CGP) (kept English as in app); pentagon = päťuholník
Bolus Wizard = Bolusová kalkulačka (app)
Calc (text on bolus line) = „Kalk“ (app)
User entry = Záznam používateľa (new app label)
Running mode (tab) = Režim prevádzky
trash can icon = ikona koša; eye icon = ikona oka
rule of thumb = orientačné pravidlo
endo / endocrinologist = diabetológ
Coming from the previous browser build (anchor aaps-builder-from-fork) = Prechod z predchádzajúceho zostavenia v prehliadači
Updating AAPS with AAPS Builder (anchor aaps-builder-updates) = Aktualizácia AAPS pomocou AAPS Builder
AAPS Builder troubleshooting = riešenie problémov s AAPS Builder
GitHub labels kept English: Repositories, Sync Fork, Update Branch, All Workflows, Run Workflow, AAPS Version, Workflow run was successfully requested.
Overview (v3 tab / main screen) = Prehľad
Local Profile (v3 tab) = Lokálny profil
Settings protection = Ochrana nastavení (app)
pencil (top bar) = ceruzka
BG check = Kontrola glykémie (app)
Cancel temp basal = Zrušiť dočasný bazál (app)
status panel = panel stavu
access token = prístupový token
websockets = websockety
Release Notes (link text) = poznámky k vydaniu; update manual = návod na aktualizáciu
Change the Profile Percentage (anchor link) = Zmena percenta profilu
Changing the insulin in use (anchor link) = Zmena používaného inzulínu
BG warning sign (anchor link) = Varovný znak glykémie
Tidepool screen = obrazovka Tidepool
roles (client control) = rola
main phone = hlavný telefón

## User decisions (these win over everything above)
Careportal (app section, quick launch category, care-portal notes) = Starostlivosť; Treatments = Ošetrenia
Temp target short form = DC (as the app); Running mode = Režim prevádzky; LGS = Ochrana pred nízkou glykémiou (LGS)
smartwatch = inteligentné hodinky; Google Drive = Disk Google; Preferences = Nastavenia
Browser build troubleshooting page title = Zostavenie v prehliadači: riešenie problémov
Statistics = Štatistiky; Pregnant (patient type) = Tehotná; Careportal entries = záznamy v Starostlivosti; Careportal note = poznámka v Starostlivosti
QuickLaunch category Careportal = Starostlivosť; Loop plugin label = Okruh; "see ..." = pozrite (formal), never "pozri"
temporary basal rate in prose = dočasný bazál ("Dočasná bazálna dávka" only where a pump screen shows it); endocrinologist = diabetológ

## Added 2026-11-02 (update after English fixes, PR #2657; the User decisions section above still wins)
private copy (AAPS Builder repository) = súkromná kópia; build steps = kroky zostavenia; workflow file = súbor workflow
template (GitHub repository) = šablóna; address bar = panel s adresou
AAPS Builder setup page buttons are TRANSLATED (2026-11-21, see <WORK>/setup_labels.txt: Vytvoriť kľúč, Kopírovať secret, Pripojiť Disk Google ...)
Linear calibration: pair = dvojica; line = priamka; drift (sensor) = posun (drift); fingerstick value = hodnota z merania z prsta
SupportingAaps/StateOfTranslations.md = Stav prekladu aplikácie a dokumentácie
stylus = dotykové pero

## Added 2026-11-07 (ConfigBuilder rewrite, AAPSClient iOS)
Set up your configuration (CGM, pump, …) and enable features = Nastaviť konfiguráciu (CGM, pumpa, …) a zapnúť funkcie (app)
Switch plugin? = Prepnúť plugin? (app); Pregnant = Tehotná (app)
Tab switcher = Prepínač kariet; tags page = stránka tagov; package manager = správca balíkov; notarized = notarizovaná
File menu = Ponuka súboru; macOS and GitHub labels stay English (Slovak in brackets for macOS)
EndOfLife script: visible words in template strings are translated ("and" = a, "to" = až); code unchanged

## Added 2026-11-08 (Dexcom Notification Reader, alarm settings)
Notification Reader = Čítačka notifikácií (app); Notification Access (AAPS permission) = Prístup k notifikáciám (app)
Android screen: Allow notification access = Povoliť prístup k upozorneniam (as on approved G7 strings)
"notifikácia" for AAPS labels and Dexcom notifications, "upozornenie" for Android screens
trusted BG data source = dôveryhodný zdroj údajov glykémie
Mute for 5 minutes / Silence alarm / Silence and close = Stíšiť na 5 minút / Stíšiť alarm / Stíšiť a zavrieť (app)
AAPS alarms = AAPS alarmy; alarm volume = hlasitosť budíka; notification volume = hlasitosť upozornení
