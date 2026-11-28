# Norwegian Bokmål wiki glossary - DRAFT (AndroidAPSdocs + AndroidAPS, language nb)

Built 2026-11-08 from the approved nb wiki text (<WORK>/all.json, about 16 600 strings, 85% approved
by other translators) and the nb app strings (values-nb-rNO, <WORK>/app_terms.json). Their style is the
standard. Not yet reviewed by a native speaker.

## Style seen in the approved text
- Address the reader with "du" (trykk, velg, telefonen din). No "De" form.
- Possessive mostly after the noun: "profilen din", "telefonen din", "innstillingene dine".
- Imperative for steps: "Trykk på ...", "Velg ...", "Åpne ...". "Trykk på" (489) is used for tap; "klikk"
  only for a computer/browser.
- Quotes: Norwegian angle quotes «...» (976 strings). Inner quotes are rare.
- Dash: en dash with spaces " – " where English has " — " or " - " in lists.
- UI labels: the app's nb string in bold, exactly as on screen (**Konfigurasjon**, **Administrer**,
  **Innstillinger**). Paths: **Administrer → Insulininnstillinger**, `&#062;` / `>` kept as in English.
- Labels of English-only tools (GitHub, xDrip+, Android Studio, AAPS Builder, Dexcom app, pump
  displays) stay English in bold, often with a short Norwegian explanation in brackets:
  **Troubleshooting** (feilsøking).
- Numbers: decimal comma (5,5 mmol/L; 0,55). Percent with space: "100 %".
- Units: mmol/L and mg/dL (capital L/dL; 57 vs 0 for mmol/l). Insulin: E (enheter), E/t for U/h,
  E/ml for IU/mL. Hours "t" (9 t), minutes "min".
- "f.eks." (66) and "for eksempel" (299) are both used; prefer "for eksempel" in running text.
- Links to English-only pages: add "(engelsk)" after the link.
- English terms kept: AAPS (never AndroidAPS unless the source says so), SMB (plural "SMB-er"), IOB, COB,
  ISF, DIA, UAM, Autosens, AutoISF, DynISF, CGM (plural "CGM-er"), Nightscout, NSClient(V3),
  AAPSClient, xDrip(+), Juggluco, BYODA, OpenAPS SMB/AMA, websockets, apk, plugin (en plugin, pluginen,
  plugins), backfill (in brackets after "utfylling av hull").
- Local abbreviations from the app: BS (for BG: "BS-kilde", "BS-verdi"), IK (for IC).

## Terms (EN = nb). App label wins for UI words.
### Core therapy
- BG = BS (UI) / blodsukker (prose); glucose = glukose
- BG source = BS-kilde
- loop = loop (loopen, looping); open loop = Åpen loop; closed loop = Lukket loop; full closed loop = full lukket loop
- Low Glucose Suspend (LGS) = Stopp ved lavt BS
- running mode = kjøremodus
- bolus = bolus (bolusen, boluser)
- bolus wizard / calculator = boluskalkulator / Kalkulator (UI)
- QuickWizard = Hurtigknapp
- QuickLaunch = Hurtigstart
- extended bolus = forlenget bolus
- basal rate = basaldose (prose, 203 hits); "Basal" in graph labels
- temp basal / temporary basal = midlertidig basal
- zero-temp = null-basal (zero-temping)
- temp target = Midlertidig mål
- target = målverdi / Mål (UI)
- carbs = Karbo (UI label) / karbohydrater (prose)
- carbs on board = Karbo ombord (UI), COB
- insulin on board = Aktivt insulin (IOB)
- IC (insulin to carb ratio) = IK
- ISF = ISF (Insulinsensitivitetsfaktor (ISF))
- DIA = DIA (insulinets virkningstid)
- peak = Topp (UI) / topp
- Load peak from = Last topp fra
- Insulin settings = Insulininnstillinger; Insulin nickname = Kallenavn for insulin
- concentration = konsentrasjon; insulin concentration = insulinkonsentrasjon
- profile = profil; profile switch = profilbytte; Local Profile = lokal profil
- sensitivity = sensitivitet; sensitivity detection = Sensitivitetsdeteksjon; Sensitivity change = Sensitivitetsendring
- deviations = Avvik; Blood Glucose Impact = BS-påvirkning (BGI)
- absorption = absorpsjon; Absorption settings = Absorpsjonsinnstillinger (approved wiki; app has "Absorberingsinnstillinger")
- Absorption cutoff = Absorpsjonsgrense
- hypo = hypo
- Pregnant (patient type) = Graviditet (app string "pregnant")

### Screens and navigation (v4)
- Config Builder / Configuration = Konfigurasjon (Config Builder stays English when the old name is meant)
- menu (☰) = menyen (☰)
- main screen / home screen = hovedskjermen
- Overview = Oversikt
- Manage (sheet) = Administrer(-arket)
- bottom navigation = navigasjonslinjen nederst
- search bar = søkefeltet
- Open plugin = Åpne plugin
- Settings / Preferences = Innstillinger (gear icon = tannhjulikonet)
- Treatments = Behandlinger; Treatments history = Behandlingshistorikk
- Actions = Handlinger
- Automation = Automasjon
- Food = Mat
- Maintenance = Vedlikehold; Export settings = Eksporter innstillinger; Import settings = Importer innstillinger
- master password = hovedpassord
- Objectives = Opplæringsmål ("opplæringsmål 8")
- Setup Wizard = Oppsettveiviser
- History browser = Historikkleser
- Statistics = Statistikk
- Careportal = Helseportal
- Status lights = Statuslys
- Show notes in dialogs = Vis merknadsfelt i dialoger
- Bolus percentage = Bolusprosent
- Exit = Avslutt
- Smoothing = Utjevning; Calibration = Kalibrering; No calibration = Ingen kalibrering; Linear calibration = Lineær kalibrering
- Communication (v4 category) = Kommunikasjon; Synchronization (v3) = Synkronisering
- General = Generelt; Constraints = Begrensninger; Safety = Sikkerhet
- Wear = Klokke; smartwatch = smartklokke
- SMS Communicator = SMS-kommunikasjon; SMS commands = SMS-kommandoer
- Notification Reader = Varslingsleser
- Permissions Required = Tillatelser kreves; Notification Access = Tilgang til varsler; Grant = Tillat
- Random BG = Vilkårlig BS
- Virtual pump = Virtuell pumpe
- ACTIVE = AKTIV

### Alarms
- Mute for 5 minutes = Demp i 5 minutter; Silence alarm = Demp alarm; Silence and close = Demp og lukk
- Override Do Not Disturb mode = Overstyr ikke forstyrr-modus (Android mode itself: Ikke forstyrr)
- Gradually increase notification volume = Øk varselvolumet gradvis
- AAPS alarms (notification channel group) = AAPS-alarmer
- Sound = Lyd; volume = volum; vibration = vibrasjon

### Devices and remote
- pump = pumpe; pump driver = pumpedriver; pod = pod (podden); reservoir = reservoar; cannula = kanyle
- Suspend (Omnipod) = Pause; Resume = Gjenoppta
- sensor = sensor; transmitter = sender; sensor change = sensorbytte (Log sensor change = Logg sensorbytte)
- warm-up = oppvarming; fingerstick = fingerstikk(måling)
- pairing = sammenkobling (Pairing = Sammenkobler in app)
- firmware = fastvare
- master (phone) = hovedenheten; client = klient
- remote control = fjernstyring; remote monitoring = fjernovervåking
- reporting server = rapporteringsserver; uploader = opplaster
- trusted BG data source = pålitelig BS-datakilde
- backfill = utfylling av hull (backfill)
- offline (master) = frakoblet
- websockets = websockets ("Bruk websockets")
- companion app = hjelpeapp
- broadcast = kringkasting

### Build and technical
- browser build = nettleserbygg; build (noun) = bygget; repository = repositoryet
- workflow = arbeidsflyt
- App info (Android) = Appinfo; Force stop = Tving avslutning; Allow restricted settings = Tillat begrensede innstillinger
- Allow notification access = Tillat tilgang til varsler
- read-only = skrivebeskyttet

## Page titles used in links
- SettingUpAaps/YourAapsProfile.md = Din AAPS-profil
- Getting-Started/CompatiblePumps.md = Kompatible pumper
- SettingUpAaps/CompletingTheObjectives.md = Opplæringsmålene
- CompatibleCgms/SmoothingBloodGlucoseData.md = Utjevning av blodsukkerdata
- GettingHelp/TroubleshootingNsClient.md = Feilsøking av NSClient
- What changed in AAPS v4 (anchor v4-changes) = Hva er endret i AAPS v4
- How to reset the master password? (FAQ) = Hvordan tilbakestiller jeg hovedpassordet?
- Master ↔ Client control = Hovedenhet ↔ klientstyring
- How can I donate? = Hvordan kan jeg gi penger?

## Inconsistencies noticed (not fixed)
- mg/dL (62) vs mg/dl (16) in approved text: prefer mg/dL.
- "master" appears untranslated in 91 approved strings next to "hovedenhet" (214); prefer hovedenhet.
- App has both "Absorberingsinnstillinger" (app) and "Absorpsjonsinnstillinger" (approved wiki).
- App "Log sensor change" has two forms: "Logg sensorskifte" and "Logg sensorbytte"; the wiki uses sensorbytte.
