# Croatian glossary (AndroidAPS app + AndroidAPSdocs wiki, language hr)

Built 2026-11-21 from the hr app strings on Crowdin (<WORK>/app_all.json; state per string in
<WORK>/app_states.json / app_states.tsv: 2058 translated, only 687 approved (A), about 1370 not
approved (U)). Approved strings win over unapproved ones. Unapproved strings were used where the
approved ones say nothing, but many of them are rough (machine-like, wrong meaning): never copy a U
string without checking it against its own English.

Style: simple, clear standard Croatian (ijekavian: "primijeniti", "vrijeme", "uvijek"), short
sentences, common words. Never Serbian or Bosnian forms: "točno" not "tačno", "tisuća" not
"hiljada", "tjedan" not "sedmica", "razina" not "nivo" (the app has "nivo" in some U strings),
"sat" not "čas". "Greška" is fine in short labels, "pogreška" in sentences.
Address the reader politely with "vi" in running text, lowercase ("Pokušajte ponovno", "vaš
telefon"), as the approved app strings do ("Molimo ažurirajte", "Pokrenite", "Usporedite"). Never "ti"
("Stvarno želiš", "Hoćeš li" in the app are errors).
Buttons, menu items and switches: short 2nd person singular imperative, as Croatian Android and the
approved app strings do: "Spremi", "Otkaži", "Potvrdi", "Zaustavi", "Osvježi", "Prikaži …",
"Aktiviraj profil", "Pokreni uparivanje" (75 approved vs 6 with -ite). Summaries and messages: "vi".
Numbers: decimal comma (5,5 mmol/L, 1,5 sat), 2 %, en dash in ranges (1–4 sata), quotes „…“.
Units: mg/dL, mmol/L (as the app), g for carbs, h and min for time ("%1$.2f h", "%1$d min").
Insulin unit: J (jedinica; approved units_short = J; J/h) - see "Inconsistencies" below.
Keep "AAPS" (never "AndroidAPS" unless the source says so); decline with hyphen: AAPS-a, AAPS-u.
Same for abbreviations: GUK-a, SMB-a, ISF-a, NS-a, Pod-a (as the app).
UI labels: use the app's Croatian string in bold, exactly as on screen; current app strings are in
<WORK>/app_all.json ({key,en,tr}); they are newer than the repo (values-hr-rHR has approved strings
only). If a label looks clearly wrong (Serbian form, "ti", wrong meaning), use correct Croatian and
report it as APP bug.
Names kept in English: Nightscout, Tidepool, xDrip+, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Autosens, Autotune, SMB, UAM, ISF, IC, DIA, TDD, HbA1c, CGM, Oref0/Oref1, fork,
Careportal (app), Watchface (app), Pod, RileyLink/OrangeLink, workflow names, labels of English-only
tools (GitHub, AAPS Builder page, Android Studio, pump displays): English in bold, a short Croatian
explanation in brackets is fine.

## Objectives (app: Objectives = Ciljevi)
Objectives = Ciljevi (app); Objective N = Cilj N; Start / Verify = Započni / Provjeri
2 Learn how to control AAPS = Naučite kako kontrolirati AAPS (app)
Note: "cilj" is also "target". In prose write "Cilj 3" (capital, number) for an objective and
"ciljna vrijednost GUK-a" / "cilj" for a glucose target, so the reader can tell them apart.

## Loop and algorithm
loop = petlja (app, 14 A); closed loop = zatvorena petlja; open loop = otvorena petlja
hybrid closed loop = hibridna zatvorena petlja; looping = korištenje petlje
Loop status = Status petlje; Suspend loop = Suspendiraj petlju; Resume loop = Nastavi petlju
suspended = suspendirano (app "Suspendiran", "Petlja suspendirana"); paused = pauzirano (app)
Low Glucose Suspend (LGS) = Zaštita od niske razine glukoze (LGS) (app U "Zaštita od niske razine glukoze u krvi")
running mode = način rada (app U "Način rada s ponavljanjem" is wrong - report)
simple mode = jednostavni način rada (app U "Jednostavni mod")
hardened mode = zaštićeni način rada (no app string yet)
SMB = SMB; Enable SMB = Omogući SMB (app); UAM = UAM; Enable UAM = Omogući UAM (app)
Autosens = Autosens; Use Autosens = Koristi Autosens
Dynamic ISF / DynISF = dinamički ISF (app "dinamička osjetljivost" for "dynamic sensitivity")
sensitivity = osjetljivost (app); Sensitivity Oref1 / AAPS / WeightedAverage = Osjetljivost Oref1 / AAPS / ponderirani prosjek
deviation = odstupanje (app); delta = delta (app "Delta", "Prikaži Deltu"); avg delta = prosječna delta
prediction lines = linije predviđanja (app); eventual BG = konačni GUK
constraint = ograničenje (app); limit = granica / ograničenje; hard limit = čvrsta granica
max basal = maksimalni bazal (app); max IOB = maksimalni aktivni inzulin (max IOB)
Autotune = Autotune (app); plugin = dodatak (app "Dodaci za osjetljivost", "DODATAK OMOGUĆEN")
algorithm = algoritam; request (loop) = zahtjev (app)

## Glucose, carbs, insulin
BG / blood glucose = GUK (glukoza u krvi) (app bg_label "GUK"; long: "glukoza u krvi")
BG value = vrijednost GUK-a; BG source = izvor GUK-a; BG graph = GUK graf (app)
glucose = glukoza (app); glycemia = glikemija; target = cilj (app); Blood Glucose Target = Ciljana razina glukoze u krvi (app)
high / low = visoko / nisko (app); in range = u rasponu (app); time in range = vrijeme u rasponu
hypo / hypoglycemia = hipo / hipoglikemija; hyper = hiperglikemija
carbs = ugljikohidrati; short label = UH (app carbs "UH")
COB = Aktivni UGH (app label, approved) - in prose: aktivni ugljikohidrati (COB)
IOB = Aktivni inzulin (app); Basal IOB = Bazalni aktivni inzulin (app)
eCarbs / extended carbs = eUH / e-ugljikohidrati (app "e-ugljikohidrati", "eUH")
meal = obrok (app, approved); Meal type = vrsta obroka
absorption = apsorpcija (app); carb absorption = apsorpcija ugljikohidrata
insulin = inzulin; ISF = ISF (Insulin Sensitivity Factor = Faktor osjetljivosti na inzulin, app)
IC / I:C = IC (Insulin to Carb ratio = Omjer inzulina i ugljikohidrata, app)
DIA = DIA (Duration of Insulin Action = Trajanje djelovanja inzulina, app)
peak (insulin curve) = vrh djelovanja (app U "Špica" - use "vrh"); Peak Time = vrijeme vrha djelovanja
TDD = TDD (app); Total Daily Dose = ukupna dnevna doza (app)
insulin concentration = koncentracija inzulina; Insulin change = promjena inzulina (app)
units (insulin) = jedinice (app "Jedinice"); short: J; U/h = J/h
calibration = kalibracija (app); fingerstick = mjerenje iz prsta; smoothing = zaglađivanje (app)
sensor = senzor (app); CGM = CGM; sensor change / insert = promjena senzora / umetanje senzora (app)
missed readings = propuštena očitanja (app)

## Bolus, basal, targets, profile
bolus = bolus; basal = bazal (app); basal rate = bazalna stopa (app)
temp basal / TBR = privremeni bazal (TBR) (app); zero-temp = nulti privremeni bazal
extended bolus = produženi bolus (app); superbolus = Superbolus (app)
meal bolus = bolus za obrok (app); correction bolus = korekcijski bolus
SMB delivered = isporučen SMB; deliver = isporučiti (app); delivery = isporuka (app)
temp target / TT = privremeni cilj (app); TT short = TT
Eating soon / Activity / Hypo (TT presets) = Prije obroka / Aktivnost / Hipo
preset = predložak (app U "Unaprijed postavljeno" is too long)
profile = profil (app); profile switch = zamjena profila (app); Activate profile = Aktiviraj profil (app)
percentage = postotak (app); time shift = vremenski pomak (app)
local profile = lokalni profil; Profile helper = Pomoćnik profila (app)
wizard (bolus calculator) = Kalkulator (app menu_wizard); bolus wizard = bolus kalkulator (app)
QuickWizard = Brzi kalkulator (app U "Brzi Izbornik" / "brzi čarobnjak" - report)
setup wizard = čarobnjak za postavljanje

## Pump and devices
pump = pumpa (app); pump driver = upravljački program pumpe (app); virtual pump = virtualna pumpa
pod (Omnipod) = Pod (app; Pod-a, Pod-u); patch pump = pumpa bez katetera (app U)
reservoir = spremnik (app U 26; approved "rezervoar" twice in Combo errors - report)
cannula = kanila (app); infusion set = infuzijski set (app); tube / tubing = cijev (app)
prime = punjenje katetera (app "Punjenje katetera"); to prime = napuniti kateter
fill (reservoir) = napuniti / punjenje (app); Prime/Fill = Punjenje (app U "Prime/Punjenje")
site change = promjena mjesta primjene (app U); cartridge change = zamjena spremnika
battery = baterija (app); battery change = zamjena baterije (app)
pair / pairing = upariti / uparivanje (app, 13 A); unpair = raspariti (app)
connect / disconnect = spojiti / odspojiti (app); connected = povezano (app); connection = veza
Disconnect pump = Odspoji pumpu; Reconnect pump = Ponovno spoji pumpu
Bluetooth = Bluetooth (app); scan = skeniranje (app)
firmware = firmware (app); serial number = serijski broj (app)
history (pump) = povijest (app); event = događaj (app)
alarm = alarm (app); alert = upozorenje (app); warning = upozorenje (app); urgent alarm = hitni alarm
notification = obavijest (app); snooze = odgoda (app "AAPS odgoda upozorenja"; button "Snooze" kept)
mute / silence = isključi zvuk / utišaj (app)
suspend delivery = obustavi isporuku; resume delivery = nastavi isporuku (app)

## Screens, menus, actions
Overview = Pregled (no app string yet); Treatments = Tretmani (app "Tretman")
Careportal = Careportal (app); note = bilješka (app)
Settings / Preferences = Postavke (app); Configuration = Konfiguracija (app)
Maintenance = Održavanje (app); History browser = Preglednik povijesti (app)
Statistics = Statistika (app); Menu = Izbornik (app "Glavni izbornik")
Automation = Automatizacija (app); trigger = okidač; condition = uvjet
action = radnja (app U "Radnja"); scene = scena (app)
Export / Import settings = Izvezi / Uvezi postavke (app); export = izvoz; import = uvoz
Reset databases = Resetiraj baze podataka (app "Resetiraj bazu")
user entries = korisnički unosi (app); log / logs = dnevnik / dnevnici (app)
master password = Glavna lozinka (app); password = lozinka
Cancel = Otkaži (app); OK = U redu (app); Save = Spremi (app); Close = Zatvori (app)
Next = Sljedeći (app); Confirm = Potvrdi (app); Refresh = Osvježi (app); Retry = Pokušaj ponovno (app)
Stop = Zaustavi (app); Exit = Izlaz (app); Default = Zadano (app); None = Nijedan (app)
Enable / Disable = Omogući / Onemogući (app); enabled = omogućeno

## Sync, remote, watch
Nightscout = Nightscout; NS = NS; NSClient = NSClient; API secret = API tajna (app "Tajni API Nightscouta")
sync = sinkronizacija (app); upload = prijenos / prenijeti (app "Podaci preneseni"; button "Pošalji sada")
download = preuzeti (app); cloud = oblak (app)
master (device) = glavni uređaj (app "glavni uređaj", "glavni pametni telefon")
client = klijent (app); AAPSClient = AAPSClient; follower = pratitelj (app)
remote control = daljinsko upravljanje (app); remote command = daljinska naredba (app)
SMS commands = SMS naredbe (app); SMS command words stay English
token = token (app "pristupni token")
phone = telefon (app); smartwatch / watch = sat (app); WearOS = Wear OS
watchface = Watchface (app keeps English); complication = komplikacija (app); tile = pločica (app)
heart rate = otkucaji srca (app); steps = koraci (app)

## Links to other wiki pages
Link text that names another page must use that page's Croatian title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

SMS commands, JSON keys, anchors, code, image names: unchanged.

## Inconsistencies in the app (2026-11-21) and the choice made
- Insulin unit: approved units_short = "J", but approved format strings keep "U" ("%1$.2f U",
  "U/h", "0,00U"); unapproved strings use "J" and "J/satu". Choice: J and J/h everywhere; the
  approved "U" strings get fixed later.
- Carbs: "UH" (carbs, 3 A) vs "UGH" (COB label "Aktivni UGH", 2 A). Choice: UH for carbs; COB label
  stays "Aktivni UGH" until the app is unified (proposal: "Aktivni UH").
- BG: "GUK" (bg_label, A) vs "BG" in some approved cwf comments ("BG vrijednost"). Choice: GUK.
- Loop: "petlja" (14 A) vs untranslated "Loop/loop" (4 A: "Loop status", "status Loopa"). Choice: petlja.
- Profile switch: "Zamjena profila" (2 A) vs "Prekidači profila" (A, wrong: hardware switches) vs
  "promjena profila" (U). Choice: zamjena profila.
- Temp basal: "privremeni bazal" (A) vs "Temp bazal" (A). Choice: privremeni bazal.
- Temp target: "privremeni cilj" (A) vs "Temp Target" left English (A). Choice: privremeni cilj.
- Basal rate: "bazalna stopa" (A) vs "bazalna doza" (2 A). Choice: bazalna stopa.
- Plugin: "dodatak" vs "PLUGIN" (A). Choice: dodatak.
- Action: "Aktivnost" (approved ue_action) clashes with Activity TT "Aktivnost"; U uses "Radnja".
  Choice: radnja.
- Suspend: "suspendirano" (6 A) vs "obustaviti" (1 A, 15 U). Choice: suspendirati for the loop,
  obustaviti isporuku for pump delivery.
- Reservoir: "rezervoar" (2 A, Combo) vs "spremnik" (26 U). Choice: spremnik (standard Croatian).
- Wizard: "Kalkulator" (A menu) vs "čarobnjak za bolus" (A) vs QuickWizard "Brzi Izbornik" (U, wrong).
  Choice: Kalkulator / bolus kalkulator / Brzi kalkulator.
- Error: "pogreška" (14 A) and "greška" (11 A) both used. Choice: "Greška" in short labels,
  "pogreška" in sentences.
- Address: "vi" in approved sentences; a few "ti" ("Stvarno želiš resetirati bazu?", "Hoćeš li…",
  "Tvoj stil"). Choice: vi.
- Disconnect: "odspojiti" (A) vs "Otkači pumpu" (A). Choice: odspojiti.
- Timeshift: "vremenski odmak" (3 U) vs "vremenski pomak" (2 U, main label timeshift_label). Choice: pomak.
- Eating soon TT: approved "Započnite uskoro počnite jesti TT" is broken. Choice: "Prije obroka".

## User decisions
(none yet)

## Round 3 decisions (2026-11-21, consistency pass)
# hr consistency pass r2, 2026-11-21. Lines: english = word (app wins; else glossary; else the more common one)
Allow client control = Dopusti upravljanje s klijenta (app pref_title_ns_allow_client_control + authorized_clients_comm_switch_title, both singular)
client control = upravljanje s klijenta
Manage sheet = ploča Upravljanje (not "list")
Treatments sheet = ploča Tretmani (not "list Tretmani")
sheet / bottom sheet = ploča
Mute for 5 minutes = Isključi zvuk na 5 minuta (app mute5min)
un-announced meals (UAM) = nenajavljeni obroci (app pref_summary_aps_use_uam; not "neprijavljeni")
grace period (pod/patch) = dodatno razdoblje (app patch_expiration_summary; not "produženo razdoblje")
Caregiver (client role) = Njegovatelj (app client_role_caregiver); caregiver in prose = skrbnik
patch pump / tubeless pump = pumpa bez katetera (app patch_pump)
Bluetooth bonding (Dash) = Trajno Bluetooth uparivanje (bonding) (app omnipod_dash_use_bonding)
Pair RileyLink = Upari RileyLink (app rileylink_pair, now fixed)
Unattended Settings Export = Izvoz postavki bez nadzora (app)
QuickLaunch toolbar = alatna traka Brzo pokretanje
status row = redak statusa
status lights = statusna svjetla (app statuslights)
clinician = zdravstveni djelatnik
Google Play Protect = Google Play zaštita
pen (insulin pen) = pen (app U strings); "inzulinska olovka" also OK in prose once
trusted BG source = pouzdan izvor GUK-a
roller coaster (BG) = tobogan
hamburger menu = izbornik s tri crtice (Hamburger izbornik)
Google Drive = Google Drive (product name; app storage_google_drive "Google disk" is a chip label only)
watchface = OPEN (user decision): app keeps "Watchface"; WearOsSmartwatch prose + 2 titles use "brojčanik"
Sensitivity detection = OPEN: app "Detekcija osjetljivosti"; page title "Otkrivanje osjetljivosti"
Tubeless pumps (section/link) = OPEN: see report

## Round 4 decisions (2026-11-21, decided by Claude at user request, can change)
- watchface = Watchface (never "brojčanik")
- caregiver = njegovatelj (app role Njegovatelj); "skrbnik" only for legal guardian
- Sensitivity detection = Detekcija osjetljivosti (app label), also the page title
- tubeless pumps = pumpe bez katetera
- Google Play Protect = Google Play zaštita
- EOPatch device in the app = Patch (Patch-a ...), not "pumpa"
