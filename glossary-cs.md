# Czech wiki glossary (AndroidAPSdocs)

Style: simple Czech, short sentences, formal "vy" (lowercase "vy/váš", not "Vy/Váš").
Numbers: decimal comma (11,1 mmol/l), space before % (2 %), en dash in ranges (1–4 hodiny).
Quotes: „…“. Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Czech string (values-cs-rCZ) in bold, exactly as on screen.
Phone UI (Samsung/Android): Nastavení > O telefonu > Informace o softwaru > Číslo sestavení;
Možnosti vývojáře; Automatické aktualizace systému.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Google Play Protect, Autosens, SMB, HbA1c, CGM, DIA (with Czech label), fork,
workflow names (Export keystore), Android Studio menu items (Generate signed APK).

## Objectives (from app objectives.xml)
Objectives = Cíle; Objective N = Cíl N
1 Setting up visualization and monitoring = Nastavit vizualizaci a monitoring
2 Learn how to control AAPS = Naučte se, jak ovládat AAPS
3 Prove your knowledge = Prokažte své znalosti
4 Starting on an open loop = Začít s otevřenou smyčkou
5 Understanding your open loop = Porozumět otevřené smyčce
6 Starting to close the loop (Low Glucose Suspend) = Spustit uzavřenou smyčku (Ochrana před nízkou glykémií)
7 Tuning the closed loop = Ladění uzavřené smyčky
8 Adjust basals and ratios, enable Autosens = Upravit bazály a poměry, povolit Autosens
9 Enabling Super Micro Bolus (SMB) = Povolit Super Micro Bolus (SMB)
10 Automation = Automatizace

## Terms
loop / looping = smyčka; closed loop = uzavřená smyčka; open loop = otevřená smyčka
hybrid closed loop = hybridní uzavřená smyčka
BG / blood glucose = glykémie; glucose target = cílová glykémie; target = cíl
carbs = sacharidy; basal = bazál; basal rate = bazální dávka; background insulin = bazální inzulín
insulin = inzulín; pump = pumpa; insulin pump = inzulínová pumpa
hypo / hypoglycemia = hypoglykémie; ketoacidosis = ketoacidóza
type 1 diabetes / T1D = diabetes 1. typu; diabetes management = léčba diabetu
clinician = lékař; clinical team = lékařský tým; caregiver = pečovatel; follower = sledující
build (verb) = sestavit; build (noun) = sestavení; to build AAPS = sestavit AAPS
Building AAPS (page) = Sestavení AAPS
Browser build = Sestavení v prohlížeči; Android Studio build = Sestavení v Android Studiu
Command-line build = Sestavení z příkazové řádky; Browser build with a fork = Sestavení v prohlížeči s forkem
keystore = úložiště klíčů; keystore file = soubor úložiště klíčů (.jks)
settings export file = soubor s exportem nastavení
reporting server = reportovací server
release notes = poznámky k vydání
FAQ = Časté dotazy; Troubleshooting = Řešení problémů (app)
Google Drive = Disk Google; developer mode = režim vývojáře
SGLT-2 inhibitors = SGLT-2 inhibitory; gliflozins = glifloziny
virtual pump = virtuální pumpa (app)
Manage = Spravovat; Insulin settings = Nastavení inzulínu; Peak = Vrchol; Load peak from = Načíst vrchol z
DIA = Doba působnosti inzulínu (DIA)
Temp target = Dočasný cíl; Profile switch = Přepnutí profilu; Treatments = Ošetření
Extended bolus = Prodloužený bolus; Preferences = Předvolby; Configuration = Konfigurace
ISF = citlivost (ISF); I:C = inzulínosacharidový poměr (I:C)

## Added after batch 1 (app wins over earlier choices)
temp basal / TBR / temporary basal rate = dočasný bazál (TBR)   (app; NOT "dočasná bazální dávka")
plugin = plugin (app "Plugin", "Pluginy"; NOT "modul")
time in range = čas v cíli (app "Time in Range")
Overview (screen) = Přehled
carb ratio / CR = sacharidový poměr (CR); I:C = inzulínosacharidový poměr (I:C)
Simple mode = Zjednodušený režim; Sensitivity detection = Detekce citlivosti
Running mode = Režim smyčky; Hint = Nápověda
Open mode minimum change = Minimální změna v otevřené smyčce
full closed loop = plně uzavřená smyčka; looper = uživatel smyčky
controller (APS part) = řídicí jednotka; pre-bolus = předstihový bolus; dual-hormone = dvouhormonální
remote monitoring = vzdálený dohled; remote control = vzdálené ovládání
smartwatch = chytré hodinky; watchface = ciferník; complication = komplikace
companion app = doprovodná aplikace; standalone watch = samostatné hodinky
broadcast (inter-app) = vysílání; trusted BG source = důvěryhodný zdroj glykémie; patched app = upravená aplikace
additional communication device = další komunikační zařízení; native Bluetooth = vestavěný Bluetooth
repository = repozitář; private repository = soukromý repozitář; GitHub secret = secret (secretu, secrety)
build workflow = sestavovací workflow; signing key = podpisový klíč; signing keystore = podpisové úložiště klíčů
build variant = varianta sestavení; preparation file = přípravný soubor; local server = místní server
settings file = soubor s nastavením; prediction lines = predikční křivky; additional graph = doplňkový graf
max daily basal = maximální denní bazální dávka; average meal bolus = průměrný bolus na jídlo
Option 1 / Option 2 (build) = Možnost 1 / Možnost 2
English-only tools: labels of GitHub, Simple Web Server, xDrip+, Mac App Store
stay English (short Czech explanation in brackets is fine).
Phone/Chrome UI labels: Czech, but say "e.g." style is not needed; they can differ by phone maker.

## Links to other wiki pages
Link text that names another page must use that page's Czech title from <WORK>/titles.txt.
If the page has no Czech title yet, translate the title and add `Page.md = Czech title` to your
<name>.terms.txt.

## Added after batch 2
Open plugin = Otevřít plugin (app fixed on Crowdin 2026-10-30; was "Otevřít modul")
Add (button) = Přidat (app fixed; was "Výměna")
Setup Wizard = Průvodce nastavením (app)
master (client control) = hlavní zařízení; client = klient; paired client = spárovaný klient
hardened mode = zabezpečený režim (app)
fingerstick entry = hodnota z glukometru; zero-temping = nulový dočasný bazál
menu (☰) = nabídka (☰); Manage sheet = panel Spravovat; bottom navigation = spodní navigace
profile editor = editor profilu; insulin editor = editor inzulínu; time block = časový blok
profile editor tabs: IC = I:C; ISF = ISF; BAS = BAS; TARG = CÍL
ISF = citlivost na inzulín (ISF); ICR = inzulínosacharidový poměr (ICR); BU = výměnná jednotka (BU)
Low target / High target = Dolní cíl / Horní cíl; eating soon TT = dočasný cíl „Před jídlem“ (app)
Full sync = Plná synchronizace (app); Profile helper = Pomocník s profilem (app); TDD = CDD (app)
Data choices = Výběr dat; Log files = Soubory záznamů; Prime/Fill = Plnění/doplňování (all app)
search bar = vyhledávací pole; main screen = hlavní obrazovka; threshold = mez
skin (AAPS 3.x) = motiv vzhledu (skin)
gradle sync = synchronizace gradle; Java KeyStore (JKS) = úložiště klíčů Java (JKS); preparation page = přípravná stránka
Android developer verification = ověřování vývojářů Androidu; web installer = webový instalátor
USB debugging = Ladění USB; wireless debugging = bezdrátové ladění; Play Store = Obchod Play
Android UI: Apps = Aplikace; See all apps = Zobrazit všechny aplikace; App battery usage = Využití baterie aplikací;
Unrestricted = Neomezeno; Allow background usage = Povolit využití na pozadí

## Added after batch 3 (agent choices; app labels still win)
remote treatment = vzdálená léčba
QuickLaunch toolbar = panel nástrojů QuickLaunch
Treatments sheet = panel Ošetření
careportal entries = záznamy péče
prediction line = predikční křivka
eventual BG = konečná glykémie
status row = stavový řádek
additional graph = doplňkový graf
Deviations (DEV) = Odchylky (ODCH) (app short labels: AKT, PRED, ODCH, ABS, TF, SENZ%, VSEN, -BGI)
folded row = sbalený řádek
chevron = šipka
notification bubble = bublina oznámení
BG warning sign = varovná značka glykémie
Bolus will be recorded only = Bolus nebude pumpou vydán, pouze zaznamenán (app)
Clinician's guide = Průvodce pro lékaře
Automation = automatizace (app "Automatizace"); Automation rule = pravidlo automatizace
trigger = spouštěč (app); condition = podmínka; action = akce
location service = služba určování polohy
unattended export = automatický export
Pump Site Change = Výměna setu (app)
Loop & insulin (action group) = Smyčka a inzulín (app); Alerts (action group) = Výstrahy (app)
On / Off = ZAP / VYP (app)
mg/dL, mmol/L = mg/dl, mmol/l
carb sensitivity factor (CSF) = faktor citlivosti na sacharidy (CSF)
carb absorption = vstřebávání sacharidů; absorbed = vstřebaný; unabsorbed = nevstřebaný
deviation (BG) = odchylka glykémie
cut-off (Oref1 carbs) = odříznutí
fallback = záložní/náhradní výpočet („fallback“)
Treatments history = Historie ošetření (app); Carbs and bolus (tab) = Sacharidy a bolusy (app)
drawer menu = postranní nabídka; selection mode = režim výběru; long-press = dlouze stisknout
Dynamic ISF = Dynamická ISF (app); DynISF = DynISF
Adjustment Factor (DynISF) = faktor úpravy
Profile Percentage = procento profilu; Profile Switch = přepnutí profilu
Future ISF = budoucí ISF
minimum predicted BG = minimální predikovaná glykémie; eventual BG = konečná glykémie; current BG = aktuální glykémie
Insulin Divisor = dělitel inzulínu
Static ISF = statická ISF; Profile ISF = ISF z profilu
low glucose suspend = pozastavení při nízké glykémii
VSENS (graph) = VSEN (app)
bolus wizard = bolusová kalkulačka (app)
eCarbs = eSacharidy (app); extended carbs = rozložené sacharidy
extended bolus = prodloužený bolus (app; NOT "rozložený bolus"); multiwave bolus = vícevlnný bolus
carbs type (wizard) = typ sacharidů; Bread / Cake / Pizza = Chléb / Koláč / Pizza (app)
Carb time = Čas sacharidů (app); Duration = Trvání (app)
pump driver = ovladač pumpy; TBR emulation = emulace TBR
Food (Manage item) = Potravina (app food_management); food database = databáze potravin
chip (filter) = štítek; bottom sheet = spodní panel; tile (Wear) = dlaždice
download-only = jen stahování; signed control channel = podepsaný ovládací kanál
SMB (Super Micro Bolus) = SMB; super micro boluses = super mikro bolusy
UAM / un-announced meals = UAM / neoznámená jídla
zero-temp / zero-temping = nulový dočasný bazál („zero-temping“)
low temps = nízké dočasné bazály
Insulin on Board = aktivní inzulín (IOB)
hard limit = pevný limit (app)
hard-coded limits = pevně naprogramované limity
Patient age types (app) = Dítě / Dospívající / Dospělý / Dospělý s nízkou citlivostí / Těhotná
Max U/h a temp basal can be set to (max-basal) = Maximální bazální dávka (app)
Max daily safety multiplier = Maximální denní bezpečnostní násobitel (app)
Current basal safety multiplier = Aktuální bezpečnostní násobitel bazálu (app)
Bolus snooze DIA divisor = Dělitel bolus snooze DIA (app)
Loop mode = režim smyčky
Low Glucose Suspend (LGS) = Ochrana před nízkou glykémií (LGS) (app)
Perform (button) = Provést (app)
sensitivity ratio = poměr citlivosti
dev build = vývojové sestavení; release build = běžné sestavení (release)
Engineering Mode = inženýrský režim (app)
noisy data = zašuměná data
AapsScreens.md section "AAPS screens > The main screen > Loop status" = Obrazovky AAPS > Hlavní obrazovka > Stav smyčky
Profile Percentage = procento profilu
time shift (profile) = posun času (app timeshift_label "Posun času")
Reuse (profile switch button) = Znovu použít (app)
Activate screen = obrazovka Aktivovat
schedule (IC/ISF/basal/target in profile) = rozpis
Your AAPS Profile (section/page) = Váš profil AAPS
QuickLaunch = Rychlé spuštění (app "Konfigurace Rychlého spuštění"); QuickLaunch toolbar = panel Rychlé spuštění
QuickWizard = Rychlý bolus (app); QuickWizards / Quick Wizard category = Rychlé bolusy (app)
quick-action button = tlačítko rychlé akce; overflow menu = rozbalovací nabídka; tooltip = popisek; drag handle = úchyt
shortcut = zkratka; catalog = katalog; Care Portal = Péče (app)
Treatments sheet = panel Ošetření; Food (Manage) = Potravina (app)
pump site change = výměna setu (app "Výměna setu"); insulin cartridge change = Výměna inzulínu (app)
prime (verb) = naplnit; priming = plnění
DST / daylight saving time = letní čas
widget picker = nabídka widgetů; home screen = domovská obrazovka
scene = scéna (app); scene template = šablona; follow-up scene = navazující scéna (app); chain into = navázat na
end action = koncová akce; Care-portal note = poznámka v Péči
tile (Wear OS) = dlaždice; tile slot = pozice
Sensitivity AAPS / Sensitivity Oref1 / Sensitivity WeightedAverage = Sensitivita AAPS / Sensitivita Oref1 / Sensitivita vážený průměr (app plugin names)
carb absorption / decay = vstřebávání sacharidů
TT (abbreviation of temp target) = DC (app tt_label)
preset (TT/profile) = předvolba
built-in preset = vestavěná předvolba; custom preset = vlastní předvolba
target chip (main screen) = štítek cíle
preset carousel = karusel předvoleb; action bar = panel akcí; top toolbar = horní lišta
Carbs dialog = dialog Sacharidy
Temp-Target Activity / Eating soon / Hypo = dočasný cíl Aktivita / Před jídlem / Hypoglykémie

## Added after batch 4 (agent choices; app labels still win)
Nearby devices (Android permission) = Zařízení v okolí
revoke permission = odebrat oprávnění
Permission manager (Android) = Správce oprávnění
Security and privacy (Android) = Zabezpečení a ochrana soukromí
Privacy controls (Android) = Ovládací prvky ochrany soukromí
Don't allow / Don't allow anyway (Android) = Nepovolovat / Přesto nepovolit
Show system (Android) = Zobrazit systémové
system app = systémová aplikace
brick a pod = zničit pod
Bluetooth Battery Optimisation = Optimalizace baterie pro Bluetooth
Force stop = vynuceně ukončit
refresh token (Google OAuth2) = obnovovací token (refresh token)
build log = log sestavení
run (GitHub Actions) = běh; job = úloha
Google account labels: Security = Zabezpečení; Your connections to third-party apps & services = Vaše propojení s aplikacemi a službami třetích stran; Delete all = Smazat vše; See details = Zobrazit podrobnosti; Remove all access = Odebrat veškerý přístup (English kept in brackets)
positive IOB / negative IOB = kladné IOB / záporné IOB
too strong / not strong enough (ISF, basal, profile) = příliš silný / není dost silný
Start IOB / End IOB = počáteční IOB / konečné IOB
Start insulin / End insulin = počáteční inzulín / konečný inzulín
time frame = časový úsek
site absorption = vstřebávání v místě vpichu
infusion set = infuzní set
Rollercoaster (BG) = horská dráha
Tubeless pumps (section) = Pumpy bez hadičky
master password = hlavní heslo (app)
Export Options / User Settings / Local Storage / Cloud Storage (AAPS 3.4) = Možnosti exportu / Uživatelská nastavení / Lokální úložiště / Cloudové úložiště (app master)
Local chip (Maintenance, AAPS 4) = štítek Lokálně (app chip_local)
Use this folder / Allow (Android folder picker) = Použít tuto složku / Povolit
issue (GitHub) = issue
month headings (changelog) = Czech month name, capitalized: Říjen 2026
patched app = upravená aplikace
language switch = přepínač jazyka
end of life / end date (AAPS version) = konec podpory / datum konce podpory
API level = úroveň API
back-up = záloha; to back up = zálohovat
tubeless pump = pumpa bez hadičky
pod (Omnipod) = pod; pod session = relace podu
chip Local / Cloud (Maintenance) = Lokálně / Cloud (app chip_local / chip_cloud)
Decrypt & Review = Dešifrovat a zkontrolovat (app)
Also replace pump settings = Nahradit také nastavení pumpy (app)
master password = hlavní heslo (app)
file explorer = správce souborů
Release date = Datum vydání (format DD. MM. YYYY, as in approved strings)
Version X = Verze X
SMB after carbs = SMB po sacharidech (app pref_title_aps_use_smb_after_carbs)
Receive Running mode events (NSClient setting) = Přijímat provozní režim z NS (app)
unattended settings export = automatický export nastavení
bonding (BLE) = bonding
race condition = souběh (race condition)
expiration (version) = vypršení platnosti
contributors = přispěvatelé
History browser = Prohlížeč historie (app)
Statistics (menu/screen) = Statistiky (app)
Time In Tight Range (TITR) = čas v úzkém cíli (TITR)
LOW mark / HIGH mark = Dolní značka / Horní značka (app)
Ambulatory Glucose Profile (AGP) = ambulantní profil glukózy (AGP) (app)
Glucose Pentagon (CGP) = Glukózový pentagon (CGP) (app)
TDD Cycle Pattern = Vzory CDD podle periody (app)
Total Daily Dose = celková denní dávka (CDD)
Range for visualization = Rozsah pro zobrazení (app)
Gradle Sync = synchronizace Gradle (Gradle Sync)
Coming from the previous browser build (section) = Přechod z předchozího sestavení v prohlížeči
Updating AAPS with AAPS Builder (section) = Aktualizace AAPS s AAPS Builder
AAPS Builder troubleshooting (section) = Řešení problémů s AAPS Builder
access token (Nightscout) = přístupový token (app nsclient_token_title)
sheet (bottom navigation) = panel
read-only = jen pro čtení
pencil (edit unlock) = tužka
BG check = Kontrola glykémie (app)
cancel temp basal = Zrušit dočasný bazál (app)
Site Rotation = Rotace míst aplikace (app)
Authorized clients = Autorizovaní klienti (app)
Local Profile (v3 tab) = Lokální profil
status panel = stavový panel

## Added after batch 5 (agent choices; app labels still win)
companion app mode (xDrip) = režim doprovodné aplikace
bridge (NFC to Bluetooth, MiaoMiao/Bubble) = můstek
vendor app = aplikace výrobce
backfill / backfilling = doplňování chybějících dat
insert/set a sensor = zavést senzor
Notification Reader (BG source) = Čtečka oznámení (app)
Hamburger Menu (xDrip) = Nabídka (☰)
NFC scan = NFC sken; to NFC scan = naskenovat přes NFC
Reader (Libre) = čtečka; FSL Reader = čtečka FSL
bridge (NFC to Bluetooth) = můstek
raw values / raw data = surové hodnoty / surová data
sensor warmup = zahřívání senzoru
private shared key = soukromý sdílený klíč
flight mode / airplane mode = režim Letadlo
insert a sensor = zavést senzor
xDrip Overview screen = obrazovka Overview v xDrip
CompatibleCgms/xDrip.md (link text "xDrip settings page") = stránka nastavení xDrip
pump screen = obrazovka pumpy
key lock = zámek kláves
remote-terminal mode = režim vzdáleného terminálu
command mode = příkazový režim
multiwave bolus = vícevlnný bolus
low battery (alarm) = slabá baterie
service pack (Accu-Chek) = servisní balení
Pumps no longer sold (section of Future-possible-Pump-Drivers) = Pumpy, které již nejsou v prodeji
Timezone traveling with pumps (section) = Cestování přes časová pásma s pumpami
phase out (product) = postupně končit
pairing wizard = průvodce párováním
patch (EOPatch/CareLevo device, in prose) = patch (patche, patchi, patchem); UI labels follow the app ("pumpa")
patch pump = patch pumpa
insulin delivery = výdej inzulínu
needle action lever = páka jehly
boot (patch) = nabootovat / spustit se
passkey (Bluetooth) = přístupový klíč
fill port = plnicí otvor
base plate (Equil) = základna
discard (patch) = zahodit; deactivate = deaktivovat
Getting-Started/BuildingAaps.md link text "Build APK" = Sestavení AAPS
pump base (Medtrum) = základna pumpy
reservoir patch (Medtrum) = patch se zásobníkem
patch session = relace patche
safety lock (Medtrum) = bezpečnostní pojistka
Allow all the time (Android location permission) = „Povolit vždy“ („Allow all the time“)
pump screen (DASH) = obrazovka pumpy
grace period (pod) = doba tolerance
pod fault = chyba podu; pod failure = selhání podu
discard a pod = vyřadit pod
uncertain (command) = nejistý (app field value "neznámé")
fill syringe = stříkačka
needle cap = kryt jehly
infusion site = místo vpichu
engineering mode build = sestavení s inženýrským režimem
basal correction = korekce bazálu (app)
Pod Management menu (old AAPS) = nabídka Pod Management (kept English)
pod communication device = komunikační zařízení pro pod
Omnipod pump screen = obrazovka pumpy Omnipod
Pod Management (menu, old) = Správa Podu (app)
uncertain (command/notification) = nejistý; (uncertain) status field = (neznámé) (app omnipod_common_uncertain)
screaming pod = „ječící“ pod
Suspend (Omnipod button) = Pozastavit (app has wrong "Vypnutí", reported)
Set time (Omnipod button) = Nastavení času (app)
Silence Alerts / ACK ALERTS = Ztišit alarmy (app)
Resume Delivery = Obnovit dodávání inzulínu (app)
RileyLink Stats = Statistika RileyLink (app)
Reset RileyLink = Resetovat RileyLink (app)
Pair RileyLink = Spárovat RileyLink (app)
RileyLink Status = Status RileyLink (app)
Pod Expires / Pod Hard End = Expirace Podu / Definitivní konec podu (app)
grace period = doba odkladu
helical antenna = šroubovicová anténa

## App labels fixed on Crowdin after batch 5 (these win over anything above)
Silence Alerts (Omnipod) = Ztišit výstrahy; Set time (Omnipod) = Nastavit čas; Suspend delivery = Pozastavit
Resume = Obnovit; Food (Manage) = Potraviny; Open plugin = Otevřít plugin; Add = Přidat

## Added after batch 6 (agent choices; app labels still win)
cherry-pick = cherry-pick (kept English)
commit hash = hash commitu
pull request / PR = pull request / PR
FCL / full closed loop = plně uzavřená smyčka (FCL)
HCL = hybridní uzavřená smyčka (HCL)
EatingSoonTT = dočasný cíl „Před jídlem“
iobTH / iob threshold = iobTH / práh IOB
boosted SMB = zesílené SMB; boost (ISF/insulin) = zesílení
EBG (Effect on Blood Glucose) = vliv na glykémii (EBG)
insulin tail = „ocas“ inzulínu
APS plugin screen = obrazovka pluginu APS
SettingUpAaps/TransferringAndInstallingAaps.md (short link text "Transferring and installing") = Přenos a instalace
Pair with master = Párovat s hlavním zařízením (app)
Set up from the master = Nastavit z hlavního zařízení (app)
Master device unreachable (banner) = Hlavní zařízení je nedostupné (app master_offline_banner)
client roles Read-only / Meals / Caregiver / Admin = Pouze pro čtení / Jídlo / Pečovatel / Admin (app)
status card = stavová karta
running mode chip = štítek režimu smyčky
uploader battery = baterie nahrávajícího telefonu
admin token (Nightscout) = token správce
pair / pairing = spárovat / párování (app "Párovat"); unpair = odpárovat (app)
pairing secret = tajný klíč pro párování
signed command = podepsaný příkaz; signed command channel = podepsaný kanál pro příkazy
admin token (Nightscout) = admin token
the client's own Nightscout token = vlastní token Nightscoutu pro klienta (app "Vlastní token Nightscoutu")
websockets = websockety
client roles = role klientů: Pouze pro čtení / Jídlo / Pečovatel / Admin (app)
Allow client control = Povolit ovládání z klienta (app)
master offline = hlavní zařízení offline
cap (an action against limits) = omezit
replay (command) = opakovaně poslaný příkaz
mobile icon (synced setting) = ikona mobilu
bidirectional preference = obousměrná předvolba
Admin tools (Nightscout) = Admin tools (nástroje správce)
developer settings (Android Auto) = nastavení pro vývojáře
Unknown sources (Android Auto) = „Neznámé zdroje“ („Unknown sources“)
All AAPS notifications (Android) = Všechna oznámení aplikace AAPS
follower phone = telefon sledujícího
admin token = admin token
Allow client control = Povolit ovládání z klienta (app)
Pair with master = Párovat s hlavním zařízením (app)
Receive (NSClient options) = Přijímat (app)
hamburger menu (Nightscout) = nabídka („hamburger“)
Complications watchface = Ciferník komplikací (app)
eat reminder = připomínka jídla
Time to eat (reminder) = Čas na jídlo (app)
Add tile (Wear OS) = Přidat dlaždici („Add tile“)
running mode picker = výběr režimu smyčky
slot / place (tile, complication) = pozice
Mute 5 min (watch alarm) = Ztlumit na 5 min (not yet in app)
Snooze Alert shortcut = zkratka pro potvrzení výstrahy (app "AAPS Potvrzení výstrahy")
check mark = značka zaškrtnutí
swipe away (notification) = odsunout
burn-in = vypálení obrazu
Watch Face Studio = Watch Face Studio
WearOS/WearOsSmartwatch.md section "Changing to an AAPS Watchface" = Přechod na ciferník AAPS
authenticator app = autentizační aplikace
Authenticator setup = Nastavení Authenticatoru (app)
Reset Authenticators = Resetovat Authenticatory (app)
caregiver phone = telefon pečovatele
AAPS phone = telefon s AAPS
authentication (SMS) = ověření; authentication code = ověřovací kód
SMS command words (BG, BOLUS 1.2, TARGET MEAL ...) = unchanged English, also in examples
Current running mode (SMS reply) = Aktuální režim běhu (app)
Wear apk = Wear apk (kept); watch app = aplikace pro hodinky; phone app = aplikace pro telefon
side-load (apk to watch) = ruční instalace („side-load“); to side-load = ručně nainstalovat
Complications watchface = Ciferník komplikací (app wear_pushed_watchface_wfs)
Custom watchface = Vlastní ciferník (app)
Watchface installed on watch = Ciferník nainstalován na hodinkách (app)
Wear control = Ovládání přes Wear (app)
complication slot = pozice pro komplikace
watch face editor = editor ciferníku
legacy watch face engine = starý engine ciferníků
Always-on display (watch) = Vždy zapnutý displej
Customize (watch face) = Přizpůsobit
Add Watch Face = Přidat ciferník
App shortcut (complication) = Zkratka aplikace
AAPS Snooze Alert = AAPS Potvrzení výstrahy (app snooze_alert)
Watch: About watch / Software information / Software version = O hodinkách / Informace o softwaru / Verze softwaru
ADB debugging / Wireless debugging / Pair new device = Ladění ADB / Bezdrátové ladění / Spárovat nové zařízení
client flavor (AAPSClient/2/3) = varianta klienta
WearOS/BuildingAapsWearOS.md (link text "Building the Wear app") = Sestavení aplikace Wear

## Added after batch 7
Tuned (profile) = Vyladěno (app autotune_tunedprofile_name)
Tune days (old label / automation action field) = Ladit dní (app)
Number of days of data = Počet dní s daty (app)
input profile / output profile = vstupní profil / výstupní profil
circadian variation = cirkadiánní variace
Autotune profile (automation action) = Profil Autotune (app)
International Units (IU) = mezinárodní jednotky (IU); Real IU = skutečné IU
Concentrated Units (CU) = koncentrované jednotky (CU)
modified profile = upravený profil
dilution / diluted insulin = ředění / zředěný inzulín; diluent = ředicí roztok
underdose / overdose = poddávkování / předávkování
external bolus = externí bolus
tube pump = pumpa s hadičkou; Patch/Pod pump = patch/pod pumpa
Reservoir Change = výměna zásobníku
mental model = způsob myšlení
Insulin concentration support = Podpora koncentrace inzulinu (app, spelled "inzulinu")
Will be activated / Currently active = Bude aktivován / Aktuálně aktivní (app)
Insulin nickname = Přezdívka inzulínu (app)
recent apps (list) = seznam posledních aplikací
EMUI labels: App launch = Spouštění aplikací; Manage automatically / manually = Spravovat automaticky / ručně; Auto-launch = Automatické spouštění; Secondary launch = Sekundární spuštění; Run in background = Spustit na pozadí; Special access = Zvláštní přístup; Power saving mode = Režim úspory energie; Ultra power saving = Ultra úspora energie
view (watchface layout element, in prose) = prvek (ImageView/TextView/ChartView names kept)
twin view = dvojitý prvek
delta / avg delta (prose) = rozdíl / průměrný rozdíl (app "Rozdíl")
hardcoded resource files = pevně dané soubory zdrojů
key / key value (JSON) = klíč / hodnota klíče
raw value = surová hodnota
dark (preference) = tmavé pozadí; match divider = dělicí čára
proofreader (Crowdin) = korektor; proofreading = korektura
branch (git) = větev
Markdown = Markdown
named reference (MyST anchor) = pojmenovaný odkaz
admonition / collapsing note = rámeček s poznámkou / sbalitelná poznámka
style guide = průvodce stylem
tutorial (Diátaxis) = výukový průvodce; how-to guide = návod; explanation = vysvětlení
active / passive verb = činný / trpný rod
battery savings / battery optimization = úspora baterie / optimalizace baterie
Pump unreachable (alert) = „Pumpa nedostupná“ (app)
App info / Force stop / Storage / Clear cache (Android) = Informace o aplikaci / Vynutit ukončení / Úložiště / Vymazat mezipaměť
red warning badge = červený varovný odznak
duplicate BGs = zdvojené glykémie
scene templates Exercise / Sleep / Alcohol / Bathing / Post-exercise Night = Cvičení / Spánek / Alkohol / Koupání / Noc po cvičení (app)
Exit (menu) = Konec (app)
Advanced settings (NSClient) = Rozšířená nastavení (app)
Enable SMB with high temp targets = Povolit SMB při vysokém dočasném cíli (app)
COB / Carbs On Board = zbývající sacharidy (app "Zbývající sacharidy")
status light (BAT/CAN/RES/SEN) = kontrolka
sensor noise = šum senzoru
AID / Automated Insulin Delivery = automatizované podávání inzulínu (AID)
Artificial Pancreas System = systém umělé slinivky
glossary entry style = English entry name in bold + Czech in brackets, e.g. <strong>Closed Loop</strong> (uzavřená smyčka)
toctree captions (index.md) = Používání této dokumentace; 1) Začínáme; 2) Sestavení aplikace; 3) Nastavení reportovacího serveru; 4) Konfigurace AAPS; 5) Každodenní život s AAPS; 6) Vzdálené funkce AAPS; 7) Chytré hodinky s Wear OS; 8) Údržba AAPS; 9) Pomoc a řešení problémů; 10) Pokročilé možnosti AAPS; 11) Jak podpořit AAPS; 12) Zdroje
documentation index (start page) = obsah dokumentace
dedicated (Google account) = samostatný
email forwarding = přeposílání e-mailů
recovery email = obnovovací e-mail
2-step verification (Google) = dvoufázové ověření

## Added 2026-11-02 (update after English fixes, PR #2657)
private copy (AAPS Builder repository) = soukromá kopie; build steps = kroky sestavení; workflow file = soubor workflow
template (GitHub repository) = šablona; secrets page = stránka secretů; debug mode (build variant) = ladicí režim (debug)
AAPS Builder setup page buttons are TRANSLATED (2026-11-21, see <WORK>/setup_labels.txt: Vytvořit klíč, Kopírovat secret, Připojit Disk Google ...); GitHub and Google button labels stay English
calibration: pair = dvojice; slope / offset = sklon / posun; calibration line = přímka
Silence alarm / Silence and close = Ztišit alarm / Ztišit a zavřít (app); Start (objective button) = Začátek (app)
Retry = Opakovat; Start fresh = Začít nově; AAPS settings cannot be read = Nastavení AAPS nelze načíst (all app)
SupportingAaps/StateOfTranslations.md = Stav překladu aplikace a dokumentace
SettingUpAaps/ChangeAapsConfiguration.md = Změna konfigurace AAPS
Effect on Blood Glucose (EBG) = vliv na glykémii (EBG); Time to eat bar = lišta Čas na jídlo; kitchen timer = kuchyňská minutka
saturation gradient filter = filtr s přechodem sytosti; version control = správa verzí; stylus = dotykové pero

## Added 2026-11-07 (ConfigBuilder rewrite, AAPSClient iOS)
Config Builder (old name) = Konfigurátor; Configuration (v4 menu) = Konfigurace (app)
mobile icon = ikona mobilu; synced from the master = synchronizováno z hlavního zařízení; per device = zvlášť v každém zařízení
Peak (insulin curve) = Vrchol (app), never "Účinnost"; Custom peak = vlastní vrchol; Load peak from = Načíst vrchol z (app)
Switch plugin confirmation = potvrzení přepnutí pluginu (app "Přepnout plugin?")
release assets = soubory vydání (label Assets English); notarized = notarizovaná; package manager = správce balíčků
Apple developer account = vývojářský účet Apple; iPhone simulator = simulátor iPhonu
email forwarding = přeposílání e-mailů; recovery email = obnovovací e-mail; 2-step verification = dvoufázové ověření
How can I donate? = Jak mohu přispět finančně? ("Jak mohu přispět?" is "How do I participate?")
WearOS/WearOsSmartwatch.md = Chytré hodinky s Wear OS

## Added 2026-11-08 (Dexcom Notification Reader, alarm settings)
Notification Reader = Čtečka oznámení (inflect: Čtečku oznámení, s Čtečkou oznámení)
phone's alarm volume = hlasitost budíku v telefonu
Mute for 5 minutes / Silence alarm / Silence and close = Ztlumit na 5 minut / Ztišit alarm / Ztišit a zavřít (app)
Override Do Not Disturb = Přebít režim Nerušit; AAPS alarms = AAPS alarmy (app)
xDrip follower mode names (Dexcom Share follower, Web follower, CareLink follower) stay English
