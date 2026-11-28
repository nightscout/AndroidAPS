# Danish app and wiki glossary (AndroidAPS + AndroidAPSdocs, language da)

Built 2026-11-21 from the approved Danish app strings on Crowdin (<WORK>/app_all.json, 2634 strings)
and values-da-rDK. Their style is the standard. Many v4 UI words (Manage, Overview, Configuration,
Treatments, QuickWizard, scene, master/client) had no Danish string yet; those are choices made here,
with an eye on glossary-nb.md and glossary-sv-SE.md. Not yet reviewed by a native speaker.

## Style (from the approved app strings)
- Simple, clear Danish. Short sentences, common words. Write for a school-level reader.
- Address the reader with "du" (du, dig, din, dit, dine; ~170 strings). Never "De/Dem/Deres".
- "venligst" is used in the app (38 strings), but do not add it to new text; a plain imperative is
  enough: "Opdater appen", not "Opdater venligst appen".
- Buttons and actions: imperative ("Annuller", "Gem", "Luk", "Bekræft", "Tilføj", "Prøv igen",
  "Genoptag", "Opdater", "Rediger", "Slet"). Use the imperative without accent: "Aktiver", "Annuller",
  "Opdater", "Deaktiver" (the app has both "Aktivér" 21 and "Aktiver" 14; both are allowed Danish;
  new text: no accent).
- Status words: past participle ("Aktiveret", "Deaktiveret", "Annulleret", "Suspenderet").
- Nouns are not capitalized inside a label: "Midlertidigt mål", "Skift profil", not "Midlertidigt Mål".
- Compound nouns are written as one word, as Danish does: "basalrate", "profilskift", "pumpestatus",
  "BS-værdi" (hyphen after an abbreviation). Avoid English-style split words ("Pumpe status").
- Numbers: decimal comma (1,5 E/min; 0,05). Percent without space, as in the app (20%).
- Units: mmol/l and mg/dl in prose (the app has both cases; Danish standard is small l).
  Insulin amount = IE (app units_short, format_insulin_units, 33 strings); rate = E/t (app
  profile_ins_units_per_hour, 13 strings); g/E for IC. Hours "t" / "timer", minutes "min".
- Quotes: the app uses straight "..." (keep it in app strings). In the wiki use Danish »...«.
- "f.eks." for "e.g.".
- Keep "AAPS" (never "AndroidAPS" unless the source says so; the app still has 8 "AndroidAPS").
- English kept: AAPS, AAPSClient, Loop (as a name), SMB, IOB, COB, ISF, IC, DIA, UAM, TDD, TBR, LGS,
  Autosens, Autotune, DynISF, Nightscout, NSClient, Tidepool, xDrip+, BYODA, CGM, Wear OS, PIN, SMS,
  plugin (et plugin, pluginet, plugins), widget, token, Superbolus, OpenAPS SMB/AMA.
- UI labels in the wiki: the app's Danish string in bold, exactly as on screen.
- English-only tools (GitHub, Android Studio, AAPS Builder, pump displays, xDrip+): English label in
  bold, short Danish explanation in brackets is fine.
- SMS commands, JSON keys, anchors, code, image names: unchanged.

## Terms (EN = da). App label wins for UI words.

### Loop and modes
- loop = loop (et loop, loopet, "dit loop"); Loop (plugin/menu) = Loop
- closed loop = lukket loop (label: Lukket loop); open loop = åbent loop (label: Åbent loop)
- full closed loop = fuldt lukket loop; hybrid closed loop = hybrid lukket loop
- Low Glucose Suspend (LGS) = Stop ved lavt BS (LGS) (app "Lav glukose suspendering" - clumsy, see report)
- running mode = loop-tilstand (app "Kører tilstand" is wrong, see report); mode = tilstand (not "modus")
- suspend loop = Suspender loop; loop suspended = Loop suspenderet; resume loop = Genoptag loop
- disable loop = Deaktiver loop
- disconnect pump = Afbryd pumpe; reconnect pump = Genforbind pumpe; disconnected = Forbindelse afbrudt
- suspend (pump delivery) = Sæt på pause / Suspender; suspended (pump) = Sat på pause; resume = Genoptag

### Glucose and targets
- BG / blood glucose = BS (label) / blodsukker (prose); glucose = glukose
- BG source = BS-kilde; BG check = BS-kontrol; BG value = BS-værdi; fingerstick = fingerprik
- target = mål / målværdi (label "Målværdi"); target range = målområde
- low / high target = nedre / øvre målværdi
- temp target / TT = midlertidigt mål (neuter: "et midlertidigt mål"; app also has "Midlertidig mål", wrong)
- Eating Soon = Spiser snart; Activity = Aktivitet; Hypo (TT) = Hypo
- hypo / hypoglycemia = hypo / hypoglykæmi; hyper = hyperglykæmi
- time in range = tid i målområde; delta = delta; avg delta = gennemsnitlig delta
- prediction (lines) = forudsigelse (prognoselinjer); deviation = afvigelse
- in range / low mark / high mark = i område / nedre grænse / øvre grænse

### Insulin and dosing
- insulin = insulin; bolus = bolus (boluser); basal = basal (basaler)
- basal rate = basalrate; temp basal / TBR = midlertidig basal (TBR)
- cancel temp basal = Annuller midlertidig basal; zero temp = nul-basal
- extended bolus = forlænget bolus (25 strings; app also "udvidet bolus" 6)
- meal bolus = måltidsbolus; correction bolus = korrektionsbolus
- SMB / Super Micro Bolus = SMB; UAM / unannounced meals = UAM / uanmeldte måltider
- IOB / insulin on board = IOB / insulin om bord; COB / carbs on board = COB / kulhydrater om bord
- ISF = ISF (insulinfølsomhedsfaktor); IC / I:C = IC (insulin til kulhydrat-forhold)
- DIA = DIA (varighed af insulinvirkning); peak / peak time = spids / spidstidspunkt
- TDD / total daily dose = TDD / samlet daglig dosis
- max IOB = maks. IOB; max basal = maks. basal; max bolus = maks. bolus
- Superbolus = Superbolus; insulin units = IE (enheder); Units = Enheder

### Carbs and food
- carbs = kulhydrater (label "Kulhydrater"; short "KH")
- eCarbs / extended carbs = e-kulhydrater (forlængede kulhydrater)
- carb absorption = kulhydratabsorption; meal = måltid; food = mad (Food (Manage) = Mad)

### Profile and sensitivity
- profile = profil; local profile = lokal profil; profile name = profilnavn
- profile switch = profilskift; do profile switch = Skift profil
- profile percentage = profilprocent; time shift = tidsforskydning
- sensitivity = følsomhed; sensitivity detection = Detektering af følsomhed
- Sensitivity AAPS / Oref1 / WeightedAverage = Følsomhed AAPS / Følsomhed Oref1 / Følsomhed Vægtet Gennemsnit (app)
- Autosens / Autotune = Autosens / Autotune; Dynamic ISF = Dynamisk ISF

### Devices
- pump = pumpe (pumpen); insulin pump = insulinpumpe; pump driver = pumpedriver
- pod = pod (poden); patch = patch (patchen); patch pump = patchpumpe
- reservoir = reservoir; cartridge = ampul; cannula = kanyle; infusion set = infusionssæt
- prime = klargør (Prime/Fill = Klargør/Fyld); fill = fyld
- pump site change = skift af infusionssæt (careportal label: Skift indstikssted)
- reservoir change = reservoirskift; insulin change = insulinskift
- battery = batteri; battery change = batteriskift; low battery = lavt batteri
- sensor = sensor; sensor change = sensorskift; warm-up = opvarmning; calibration = kalibrering
- transmitter = sender; CGM = CGM
- pairing / pair = parring / par (Pair = Par); unpair = ophæv parring
- Bluetooth = Bluetooth; device = enhed; phone = telefon
- smartwatch / watch = smartur / ur (Wear = Ur); watchface = urskive; tile = felt (Wear OS-felt)
- widget = widget

### Screens, menus, buttons
- Overview / main screen = Oversigt / hovedskærm
- Manage (sheet) = Administrer
- Configuration / Config Builder = Konfiguration
- Preferences / Settings = Indstillinger (app has "Præferencer" 14 times; use Indstillinger)
- Plugin preferences = Plugin-indstillinger; Open plugin = Åbn plugin
- Treatments = Behandlinger; treatment = behandling; Treatments history = Behandlingshistorik
- Actions = Handlinger; action = handling
- Careportal = Careportal (kept, as the app)
- Calculator / bolus wizard = Beregner / bolusberegner (not "guide", not "lommeregner")
- QuickWizard = Hurtigbolus (app "Hurtig Guide" in one string; see report)
- QuickLaunch = Hurtigstart
- Setup Wizard = Opsætningsguide (app has "Installationsguide" and "opsætningsguiden")
- Objectives = Mål (app menu label); objective N = Mål N / læringsmål N in prose
- History browser = Historikbrowser; Statistics = Statistik; Maintenance = Vedligeholdelse
- Export / Import settings = Eksporter / Importer indstillinger; log files = logfiler
- master password = hovedadgangskode; password = adgangskode (not "kodeord")
- Cancel = Annuller; Save = Gem; Close = Luk; OK = OK; Add = Tilføj; Edit = Rediger; Delete = Slet
- Retry = Prøv igen; Confirm = Bekræft; Refresh = Opdater; Clone = Klon; Yes / No = Ja / Nej
- Enabled / Disabled = Aktiveret / Deaktiveret; On / Off = Til / Fra

### Automation
- Automation = Automatisering; automation rule = automatiseringsregel
- trigger = udløser; condition = betingelse; precondition = forudsætning; action = handling
- scene = scene; scene template = sceneskabelon
- Run automations = Kør automatiseringer

### Remote, sync and alerts
- master (phone) = hovedenhed (NOT "hovedtelefon" = headphones; changed 2026-11-21); client = klient
- AAPSClient = AAPSClient; NSClient = NSClient; follower = følger
- remote control = fjernstyring; remote monitoring = fjernovervågning; caregiver = omsorgsperson
- sync / synchronization = synkronisering; Full synchronization = Fuld synkronisering
- upload = upload (uploader); download = download; uploader = uploader
- reporting server = rapporteringsserver; cloud = sky
- SMS Communicator = SMS-kommunikator; authenticator app = godkendelsesapp
- alarm = alarm; alert = advarsel; urgent alarm = akut alarm; warning = advarsel
- notification = notifikation; reminder = påmindelse
- note (careportal) = notat; announcement = meddelelse; question = spørgsmål; exercise = motion

### Build and technical (wiki)
- build (verb / noun) = bygge / build; browser build = build i browseren
- repository = repository; workflow = workflow; keystore = keystore (nøglelager)
- backup = sikkerhedskopi; to back up = sikkerhedskopiere
- read-only = skrivebeskyttet; developer options = Udviklerindstillinger (Android)

## Links to other wiki pages
Link text that names another page must use that page's Danish title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no Danish title yet, translate the title and add
`Folder/Page.md = Danish title` to your <name>.terms.txt.

## Decisions and inconsistencies (2026-11-21, from the app review)
Where the app uses two words for one thing, the first word is the one to use; fix the other in the
app when the string is touched.
- BG: "BS" (22) = "BG" (22). Use BS; "Vis BS" and "Vis BG" exist for the same key pref_show_bg.
- insulin units: IE (33) for amounts vs E (some strings) and U (some untranslated). Rates stay E/t (app).
  Open question for a native reviewer: IE/t would be more consistent.
- Preferences: Indstillinger (67) over Præferencer (14).
- password: adgangskode (14) over kodeord (10).
- temp target: "midlertidigt mål" (correct neuter) over "midlertidig mål" (19, wrong gender).
- open loop: "Åbent loop" (correct neuter, like "dit loop") over "Åben Loop" (7, incl. the openloop label).
- extended bolus: forlænget bolus (25) over udvidet bolus (6).
- resume: Genoptag (15) over Fortsæt (10).
- suspend: Suspender (loop) / Sat på pause (pump status); both exist, keep this split.
- delivery: levering (35) / tilførsel (27) / afgivelse (10) - use "levering" for bolus, "insulintilførsel"
  for "insulin delivery".
- sensitivity: følsomhed (12) over sensitivitet (6).
- bolus wizard: bolusberegner (Beregner) over Bolusguide / Lommeregner.
- Objectives: "Mål" (menu), "Målsætninger" and "Læringsmål" also used; "Mål" clashes with target.
  Keep "Mål" as menu label, "læringsmål" in prose when it could be read as target.
- site change: "Skift indstikssted" / "Skift infusionssæt" / "Skift af kanyle" - use skift af infusionssæt.
- Setup Wizard: "Installationsguide" vs "opsætningsguiden" - use Opsætningsguide.

Clear app errors seen (fix on Crowdin):
- hypott_label "Hypo Temp-Target" = "Hypo midlertidig basal" (should be Hypo midlertidigt mål)
- exercise_label "Exercise and Temporary Targets" = "Motion og midlertidige basaler" (should be ... mål)
- limitingbasalratio "Limiting max basal rate" = "Begrænser max IOB" (should be maks. basalrate)
- wear_action_running_mode_title "Running mode" = "Kører tilstand" (should be Loop-tilstand)
- automation_category_targets "Targets" = "Mål," (stray comma); automation_category_profile = "Profilen"
- string_a019 "Pach" typo; uel_calibration_removed "KALIBRRATION" typo
- invalid_basal_rate uses decimal point (0.05) instead of comma
- quick_wizard_not_available: "Valgt guide" - should be the QuickWizard word

## Round 3 decisions (2026-11-21, consistency pass)
# da consistency pass r2, 2026-11-21. Lines: english = word (app word wins, then glossary, then the more common one)
master (phone) = hovedenhed (never hovedtelefon; app changed today)
main phone (prose) = primære telefon / hovedenhed (never hovedtelefon)
Pair with master = Par med hovedenhed
secret (GitHub) = secret (secret'en, secrets) - not hemmelighed; AAPS Builder setup page says "Åbn siden med secrets"
client control = klientstyring (app setupwizard: Klientstyring); the app label "Tillad styring fra klienter" stays as it is when quoted
status row (main screen) = statuslinje
status light / status lights = statuslys (app statuslights = Statuslys), not statuslampe
prediction line = prognoselinje (glossary), not forudsigelseslinje
trusted BG source = pålidelig BS-kilde
backfilling = udfyldning af manglende data
tube pump = pumpe med slange; tubeless pumps = slangeløse pumper
site rotation = rotation af indstikssteder (app site_rotation = Rotation af indstikssteder)
smoothing = udjævning (app Udjævning), not udglatning
CompatibleCgms/SmoothingBloodGlucoseData.md = Udjævning af blodsukkerdata
Profile helper = Profilhjælper (app now; was "Profil hjælper")
Load Watchface = Indlæs urskive (app now; was "Indlæs Urskive")
Complications watchface = Urskive med komplikationer (app wear_pushed_watchface_wfs)
Settings protection = Beskyttelse af indstillinger (app now)
Pod Management (menu) = Pod-administration (app: menuen til Pod-administration)
RileyLink Stats = RileyLink-statistik (app)
History browser = Historikbrowser (app now)
Statistics = Statistik (app now)
SMS Communicator = SMS-kommunikator (app now)
Setup Wizard = Opsætningsguide (app now)
Prime/Fill = Klargør/Fyld (app now)
time shift = tidsforskydning (app now)
temp target = midlertidigt mål (app now)
Low Glucose Suspend = Stop ved lavt BS (app now)
imperative labels = without accent (Aktiver, Deaktiver, Opdater, Suspender, Annuller, Ignorer, Vibrer); only exception Kassér
trusted BG source = pålidelig BS-kilde; trusted BG data source = pålidelig BS-datakilde (never betroet)
pre-bolus = bolus før måltidet (not præbolus / forhåndsbolus)
QuickLaunch toolbar = Hurtigstart-værktøjslinjen (not værktøjslinjen Hurtigstart)
Android developer verification = Android-udviklerverificering (H1 on TransferringAndInstallingAaps)
tubeless pumps (link to ExportImportSettings section) = Slangeløse pumper
Start Activity TT / Start Hypo TT (carbs dialog) = Start "Aktivitet" mål / Start "Hypo" mål (app now)
Omnipod overview: Pod-status, Pod-historik, RileyLink-status, Standardbasalrate, Pumpens serienummer, Pumpefrekvens (app now)
GettingHelp/BluetoothTroubleshooting.md = Bluetooth-relaterede problemer (H1 as translated; titles_proposed had "Problemer med Bluetooth")
