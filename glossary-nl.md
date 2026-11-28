# Dutch glossary (AAPS wiki + app, language nl)

Priority: this glossary > current Crowdin app string > local values-nl-rNL. If the app still shows an
older label, use the glossary term and report it as APP bug.

Style: simple, clear Dutch (standard Netherlands Dutch, readable for Flemish readers), short
sentences, common words.
Wiki: address the reader with "je" ("stel je telefoon in"), as most Dutch technical guides do; use
"jouw" only for emphasis. The wiki uses "je" everywhere, except text quoted from the app, which keeps
the app's own wording.
App: never "je/jij/jouw" or "u/uw"; use the imperative or an impersonal
phrasing instead, and never "a.u.b.", "alstublieft" or "alsjeblieft" ("Koppel de pomp aan de
telefoon", not "A.u.b. de pomp koppelen met je telefoon").
"please": leave it out, in the wiki too ("Lees de release notes", not "Lees alsjeblieft …").
Values never change; their notation follows the Dutch rules below in prose, and stays exactly as in
the source in code, placeholders (%1$s, %d, {0}, etc.), examples and app values.
Numbers: decimal comma (5,5 mmol/l), 2%.
Quotes: keep the quote style of the source ("…" stays "…", ‘…’ stays ‘…’).
Ranges in prose use an en dash without spaces (1–4 uur, 70–180 mg/dl, 3,9–10 mmol/l), also when
the source uses a hyphen.
Times: 24-hour clock without "uur" (2pm = 14:00, 9 pm - 7am = 21:00–07:00).
Units: mg/dl, mmol/l (lowercase l, also when the source writes mg/dL or mmol/L), E for insulin units
(app `format_insulin_units`), E/u for insulin rates (U/h becomes E/u; not "E/uur" or "E/h"), IE only
for international units in the insulin concentration text, uur / min in text.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Dutch string (Crowdin; local values-nl-rNL is older) in bold, exactly as on
screen, unless this glossary gives another term (see Priority). If a label looks clearly wrong, use
correct Dutch and report it as APP bug.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Autosens, SMB, IOB, COB, ISF, IC, HbA1c, CGM, TDD, fork, workflow names, labels of
English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays such as Insight and
Combo, also in menu paths): English in bold, a short Dutch explanation in brackets is fine.

## Terms
loop = loop (app: Loop); to loop (verb) = loopen (wiki)
closed loop = gesloten loopmodus (prose); open loop = open loopmodus (prose); the running mode label
and the loop status stay "Closed loop" / "Open loop" (app closedloop, openloop)
Objectives = Leerdoelen; Objective N = Leerdoel N; objective titles follow this glossary (Leerdoel),
also where the app still says "Doel"
BG / blood glucose = bloedglucose (BG); target = streefdoel; temp target = tijdelijk streefdoel
target range = streefbereik; Time in range (TIR) = Tijd binnen bereik (TIR)
hypoglycemia = hypoglykemie / hypo (plural hypo's)
hyperglycemia = hyperglykemie / hyper (plural hypers)
carbs = koolhydraten (app); basal = basaal; basal rate = basaalstand (never "basaalsnelheid" or
"basaal snelheid"); basal insulin = basale insuline; basal profile = basaalprofiel
temp basal = tijdelijke basaalstand (TBR) (never "tijdelijk basaal" or "tijdelijke basaal");
zero temp = tijdelijke basaalstand van 0 E/u
Extended bolus = Verlengde bolus (app); eCarbs / extended carbs = verlengde koolhydraten (eCarbs)
bolus = bolus; insulin = insuline; pump = pomp
Insulin on board (IOB) = Actieve insuline (IOB), not "insuline aan boord"; Bolus IOB = Bolus-IOB
(app label, short); Basal IOB = Basale IOB (app label, short); prose may explain them as "actieve
bolusinsuline" / "actieve basale insuline"
Carbs on board (COB) = Actieve koolhydraten (COB), not "koolhydraten aan boord"
Insulin sensitivity factor (ISF) = Insulinegevoeligheidsfactor (ISF); Insulin-to-carb ratio (IC) =
koolhydraatratio (IC) (app ic_label; long label ic_long_label = Insuline-koolhydraatverhouding)
DIA = insulinewerkingsduur (DIA); TDD = totale dagelijkse dosis (TDD) (app tdd); T1D = diabetes type 1 (never "T1D")
profile = profiel; profile switch = profielwissel; profile switches = profielwisselingen
Pod = Pod (Omnipod, always capital); patch = patch (lowercase as a generic noun; capital only in a product name)
site = plaatsingslocatie; site change = wissel van plaatsingslocatie (app); site rotation =
afwisselen van plaatsingslocaties (app site_rotation); infusion set = infusieset
master password = hoofdwachtwoord (not "masterwachtwoord"); PIN = pincode (one word)
command = opdracht (not "commando"); SMS Communicator = SMS-opdrachten (app); pump driver = pompdriver; scene = scène (with accent)
Verbs for actions: tap = tik; press = druk op; select = selecteer; choose = kies; click = klik
enable / turn on = zet aan (prose), "Schakel … in" (labels); disable / turn off = zet uit (prose),
"Schakel … uit" (labels); activate = activeer only for a Pod, patch or sensor
Settings / Preferences = Instellingen (app); running mode = Loopmodus (app; "Actieve modus" is only the Equil label)
calibration = kalibratie; cannula / catheter = canule
notification = melding (not "notificatie"); warning = waarschuwing; alarm = alarm
smartwatch / watch = horloge ("smartwatch" only for the device type, e.g. "Wear OS-smartwatch")
follower = volger (app labels keep the app wording)
artificial pancreas = kunstmatige alvleesklier; artificial pancreas system =
kunstmatige-alvleeskliersysteem; never "pancreassysteem"
pancreas = alvleesklier
clinician = arts / zorgverlener; caregiver = verzorger / ouder (never "zorgverlener": that is the
clinician); the client role label for caregiver = "Verzorger" (app client_role_caregiver)
diabetes clinic / endocrinologist / diabetes team / clinical team / care team = behandelteam (not
"endocrinoloog" or "diabeteskliniek"; this localisation is an allowed exception to Source-faithful)
build (verb) = bouwen; plugin = plug-in
keystore: the Android Studio / AAPS Builder file stays "keystore" (English tool, `keystore.zip`);
the phone's or computer's system key store = sleutelarchief (app settings_unreadable_keystore_explanation)
Unchanged: SMS command syntax (e.g. `BOLUS 1.2`), JSON keys, anchors, code, image names.

## Links to other wiki pages
Link text that names another page must use that page's Dutch title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## App labels fixed on Crowdin (local values-nl-rNL files are older; these win)
Read <WORK>/app_all.json (all current Dutch app strings from Crowdin, {key,en,tr}) for labels.
Configuration = Configuratie; Open plugin = Plug-in openen; Overview = Overzicht; Manage = Beheren; Communication = Communicatie
Automation = Automatisering; Scenes = Scènes; Maintenance = Onderhoud; Export settings = Exporteer instellingen
Settings export (the noun, e.g. a heading or "the settings export") = Export van instellingen; not "Export instellingen" or "Instellingen export". "Export settings" as a button/menu label keeps the app wording (Exporteer instellingen).
Hardened mode = Beveiligde modus; Simple mode = Eenvoudige modus; Pair with master = Koppel met master
Authorized clients = Geautoriseerde clients; client = client; master = master; Profile percentage = Profielpercentage
Profile switch = Profielwissel; Site rotation = Afwisselen van plaatsingslocaties
Client control = bediening door clients (the master is operated remotely by paired clients); Allow client
control = Sta bediening door clients toe; "while client control is on" = "zolang bediening door clients
aan staat"; Master ↔ Client control = Master ↔ client: bediening op afstand; never "Client besturing",
"clientbesturing" or "Client controle"
AAPS directory = AAPS-map
Careportal = Careportal; QuickWizard = QuickWizard

## Source-faithful wording
Translate what the source says, with the same word class and meaning. Do not paraphrase and do not
rewrite a word into a description of it. Splitting a long sentence is fine; changing meaning or word
class is not.
- "It starts with an optional step" = "Het begint met een optionele stap"; not "Het begint met een
  stap die je mag overslaan".
- Same for other adjectives and fixed terms: "optional" = optioneel, "required" = verplicht,
  "recommended" = aanbevolen.
- Keep negations and numbers: "do not remove" stays a prohibition and a number never changes.
- Lengths and similar everyday units may be converted to the metric system ("a few inches" =
  "enkele centimeters", "2 inches" = "5 cm"). Never convert a glucose unit: a setting, label,
  heading or value in mg/dl is not converted to mmol/l, or the other way round. Do not add precision
  that the source does not have.
- Translate colors and fixed words literally: "yellow" = "geel".

## Style rules for labels and sentences
- Actions and buttons use the imperative ("Toon…", "Registreer…", "Sta … toe", "Schakel … in").
  Headings and statuses stay a noun or a participle ("Pomp onderbroken", not "Pomp onderbreken").
- Dutch compounds are one word: profielwissel, wachtrijgrootte, insulinepomptherapie, loopstatus,
  batterijniveau; never "Profiel wissel" or "Batterij niveau".
  If an app label splits a compound, use correct Dutch in prose and report the label as APP bug.
- Compounds with an abbreviation or product name take a hyphen: BG-waarde, AAPS-map,
  xDrip+-statusregel, SMB-toedieningsratio, Wear OS-horloge, plug-in. Otherwise use a hyphen only
  for a vowel clash (insuline-instellingen), not as a separator ("wachtrij-grootte" is wrong).
- Headings use sentence case: only the first word and names take a capital ("Externe companion-apps",
  not "Externe Companion Apps").

## Exceptions
- ClinicianGuideToAaps (for doctors) uses "u"; all other pages use "je".

## User decisions
- Wiki: upload and approve. App: translate missing strings + fix clear errors.

## Decisions
- watchface = "wijzerplaat" (as the app: Installeer de wijzerplaat, Meer wijzerplaatinstellingen,
  Aangepaste wijzerplaat); custom watchface = aangepaste wijzerplaat; watch face picker =
  wijzerplaatkiezer. Not "watchface", except in file names, code and key names.
- Prime/Fill: the phone button is "Prime/Vul" (prime_fill). "Ontlucht/vul" is the watch menu
  (menu_prime_fill), "Ontlucht/Vul" and "Prime / Vullen" are Equil screens: use them only there.
- Insulin settings = "Insuline-instellingen" (app label insulin_management).
- Tubeless pumps = "pompen zonder slang" (link text: "Pompen zonder slang"); not "slangloze pompen".
- Setup wizard = "Installatiewizard" (app nav_setupwizard); not "Setup Wizard".
- Smoothing = "vloeiend maken" (app); page H1 "Vloeiend maken van bloedglucosewaarden"; not "filteren".
- command-line = "opdrachtregel".
- main phone / primary phone = "mastertelefoon" (the AAPS master; never "hoofdtelefoon": that means
  headphones).
- Link texts that name another page use that page's approved H1. Use the part after the colon for
  browser build steps ("Je GitHub-fork maken", "Google Drive autoriseren", "De APK bouwen").
- H1 fixes: AapsScreens = "AAPS-schermen"; SensitivityDetectionAndCob = "Gevoeligheidsdetectie
  (Autosens)"; WearOsSmartwatch = "AAPS instellen op je Wear OS-horloge" (titles.js misses it).
