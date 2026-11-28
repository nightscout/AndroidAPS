# Catalan glossary (AndroidAPS app + AndroidAPSdocs wiki, language ca)

Style: simple, clear Catalan (central standard, IEC spelling), short sentences, common words.
Address the reader with the 2nd person plural, as the app does: "Comproveu", "Premeu", "el vostre
telèfon", "Si esteu segurs". Do not use "tu" and do not use "vostè".
Buttons, menu items and short actions: infinitive, as in the app ("Aturar bomba", "Exportar
configuració", "Desar", "Cancel·lar"). Instructions in running text: imperative plural ("Seleccioneu",
"Activeu"). Status messages: short, impersonal ("Llaç desactivat", "Bolus no lliurat").
Numbers: decimal comma in new text (5,5 mmol/l, 0,1 U), a space before % is not used (20%, as in the
app). Ranges with an en dash (1–4 hores). Numbers that come from a placeholder (%1$.2f) are formatted
by the phone, do not touch them. Some old app strings copy the English dot ("0.7", "1.5 hores"):
keep them only if you do not edit that string.
Quotes: «…» in wiki prose; inside app strings keep the "…" that the app already uses.
Apostrophe: straight ' (the app mixes ' and ’; use ').
Units: mg/dl, mmol/l, U (insulin units, as in the app), U/h, g, min, h. Write "U" also in text
("2 U"), not "UI".
Keep "AAPS" (never "AndroidAPS" unless the source says so). AAPS is masculine ("l'AAPS", "AAPS
envia...", app writes it without article: "AAPS no n'és conscient").
UI labels: use the app's Catalan string in bold, exactly as on screen. Current app strings from
Crowdin are in <WORK>/app_all.json ({file,key,en,tr}); they are newer than the repo (values-ca-rES);
search there first. If a label is clearly wrong (wrong meaning, typo, Spanish word), use correct
Catalan and report it as APP bug.
Names kept in English: Nightscout, NSClient, Tidepool, xDrip+, BYODA, Open Humans, GitHub, GitHub
Actions, Android Studio, AAPS Builder, Autosens, Autotune, SMB, UAM, TBR, TDD, IOB, COB, ISF, I:C, DIA,
HbA1c, CGM, OpenAPS, Oref, RileyLink, Wear OS, fork, workflow names, SMS command words.
In a first mention you can add a short explanation in brackets: "IOB (insulina a bord)".

## Terms (app words win; "app" = the word is used in approved app strings)

### Loop and modes
loop = llaç (app "Llaç"); Loop plugin = Llaç; loops (config group) = Llaços (app)
closed loop = llaç tancat (app); open loop = llaç obert (app)
LGS / low glucose suspend = mode llaç LGS (app uel_lgs_loop_mode); in prose: suspensió per glucèmia baixa (LGS)
running mode / loop mode = mode de funcionament (app operating_mode "Mode de funcionament")
Suspend = Suspendre (app pump history, Omnipod button); Suspend loop = Suspendre el llaç (app has "Aturar el llaç", see inconsistencies)
Stop = Aturar (app "Aturar bomba"); Resume = Reprendre (app "Reprendre llaç")
Disconnect pump = Desconnectar bomba (app); Reconnect = Reconnectar (app "Reconnectar bomba")
Loop enabled / disabled = Llaç activat / desactivat (app); enable / disable = activar / desactivar
APS = APS; algorithm = algoritme

### Glucose
BG / blood glucose = glucèmia (app "Glucèmia"); glucose = glucèmia (app); BG check = Control glucèmia (app)
CGM = CGM (monitor continu de glucosa) (app); sensor = sensor; sensor change = canvi de sensor
BG source = Origen glucèmia (app configbuilder_bgsource); in prose also "font de glucèmia" (app)
calibration = calibració (app); manual BG / fingerstick = glucèmia manual (app careportal_mbg)
delta = delta (f.: "la delta"); avg. delta = delta mitjana (app)
target = objectiu (app "Objectiu"); in prose say "objectiu de glucèmia" when it can mix with Objectives
temp target / TT = objectiu temporal (app), short OT (app "Iniciar OT: Hipo")
Eating soon = Menjar aviat (app); Activity = Activitat (esport) (app); Hypo (TT) = Hipo (app)
hypo / hypoglycemia = hipoglucèmia (short "hipo", app); hyper = hiperglucèmia
noisy (CGM data) = sorollós / dades sorolloses (app)
mg/dl, mmol/l = mg/dl, mmol/l; Glucose units = Unitats glucèmia (app)

### Insulin and treatments
insulin = insulina (app); insulin type / insulin plugin = Insulina (app configbuilder_insulin)
bolus = bolus (pl. "els bolus") (app); deliver = lliurar (app "Lliurar ara"); delivery = lliurament (app)
meal bolus = bolus d'àpat (app); correction bolus = bolus de correcció (app); snack bolus = bolus de snack (app)
extended bolus = bolus estès (app); superbolus = Superbolus (app); combo bolus = bolus combo (app)
SMB = SMB (super micro bolus) (app)
bolus wizard / Calculator = Calculadora de bolus / Calculadora (app boluswizard, menu_wizard)
QuickWizard = Assistent ràpid (no app string yet - choice)
Setup Wizard = Assistent de configuració (app); wizard (generic) = assistent (app)
basal = basal (f.: "la basal") (app); basal rate = taxa basal (choice; app has "Ràtio de basal", see below)
temp basal / TBR = basal temporal (TBR) (app); Temp basal start / end = Inici / Fi basal temporal (app)
max basal = basal màxima (app "Màx. basal")
IOB / insulin on board = IOB (insulina a bord) (app iob_label); Bolus IOB / Basal IOB = IOB Bolus / IOB Basal (app)
COB / carbs on board = COB (carbohidrats a bord) (no app expansion yet - choice, same pattern as IOB)
ISF = ISF (factor de sensibilitat a la insulina) (app)
IC / I:C ratio = ràtio I:C (ràtio insulina:carbohidrats) (app; "ràtio" is feminine)
DIA = DIA (durada de l'acció de la insulina) (app; feminine "la DIA")
peak (insulin curve) = pic (app "Temps del pic"); Free-Peak Oref = Oref sense pic (app)
TDD = TDD (dosi diària total) (app keeps "TDD")
UAM = UAM (àpats no anunciats) (app keeps "UAM")
autosens = Autosens (app); sensitivity detection = Detecció de sensibilitat (app)
Sensitivity plugins = Plugins de sensibilitat (app); weighted average = mitjana ponderada
Dynamic ISF = ISF dinàmic (choice); Autotune = Autotune
treatment = tractament (app); Treatments = Tractaments (app)
user entry = entrada d'usuari (app); User action = Acció de l'usuari (app)
Insulin change / reservoir change = canvi d'insulina / canvi de reservori (app)
units (insulin) = unitats, U (app)

### Carbs and food
carbs / carbohydrates = carbohidrats (app, most strings); short labels and buttons = Carbs (app menu_carb, action_carbs)
extended carbs / eCarbs = carbohidrats estesos / e-carbs (app "CARBS ESTESOS", "e-carbs")
carb correction = correcció de carbohidrats (app)
meal = àpat (app); food = àpat (app uel_food "ÀPAT"); food database = base de dades d'aliments (choice)
carb absorption = absorció de carbohidrats (app)

### Profile
profile = perfil (app); profile switch = canvi de perfil (app); Do Profile Switch = Canviar de perfil (app)
local profile = perfil local (choice); profile percentage = percentatge del perfil; time shift = desplaçament horari
Exercise = Esport (app careportal_exercise); Exercise and Profiles = Esport i perfils (app)

### Pump and devices
pump = bomba (app); insulin pump = bomba d'insulina (app); virtual pump = bomba virtual
pod = pod (masc., lower case "el pod", app); patch = patch (bomba patch) (choice)
reservoir = reservori (app); cannula = cànula (app); insert cannula = Inserir cànula (app)
site change = canvi de cànula (app "Canvi de cànula", "CANVI CÀNULA")
infusion site = punt d'infusió (app); needle cap = tap de l'agulla (app); fill syringe = xeringa (app)
prime = encebar (app "Encebar"); priming = encebament (app "Encebaments"); Prime/Fill (menu) = Encebar (app)
fill (pod, reservoir) = omplir (app "Omplir pod"; also "emplenar" in one text - use omplir)
battery (phone, watch, rechargeable) = bateria (app); pump battery (replaceable AA/AAA) = pila de la bomba (app pb_label)
low battery = Bateria baixa (app) / pila baixa (pump)
pair = emparellar; pairing = emparellament (choice; app wrongly uses "sincronitzar", see below)
Bluetooth = Bluetooth (app); Bluetooth address = Adreça Bluetooth (app)
driver (pump) = controlador (app "driver/controlador")
pump status = Estat bomba (app); pump history = Historial de la bomba (app)
Pod activation / deactivation = activació / desactivació del pod; Discard Pod = Descartar pod (app)
Pod fault = Error del pod (app)
beeps = bips (app)

### Screens, menus, settings
Overview (main screen) = Resum (choice; no app string yet)
Actions = Accions (app); Careportal = Portal de cures (app)
Settings / Preferences = Configuració (app uses it for both)
Configuration (v4 menu, was Config Builder) = Configurador (choice, so it does not mix with Settings = Configuració)
Plugin preferences = Configuració plugin (app); plugin = plugin (app)
Objectives = Objectius (app); Objective N = Objectiu N (app "%1$d. Objectiu")
Start objective = Iniciar objectiu (app); Prove your knowledge = Demostreu els vostres coneixements (app)
Maintenance = Manteniment (app); Statistics = Estadístiques (app)
History browser = Historial (app nav_history_browser)
Export settings / Import settings = Exportar configuració / Importar configuració (app)
master password = clau mestra (app); password = contrasenya (app); PIN = PIN
log / logs = registre (app "Registre de pols"; app also keeps "logs" - prefer registre in new text)
Delete logs = Eliminar logs (app)
status = estat (app); notification = avís (app "Avís", "Avisos")
Android system notifications (phone settings) = notificacions (Android Catalan wording)
alarm = alarma (app); alert = alerta (app); warning (pump code W..) = avís (app "Avís W31")
snooze = postposar (app "Postposar")
Automation = Automatització (app); trigger = activador (app); action = acció (app); condition = condició (choice)
scene = escena (choice); task / rule (automation) = tasca (app automation_missing_task_name)
SMS Communicator = Comunicador SMS (app); authenticator = autenticador (app "Autenticador")

### Sync, master and client
Nightscout = Nightscout; NS = NS; NSClient = NSClient (app)
sync / synchronization = sincronització (app "Sincronització completa"); resync = resincronitzar
upload = enviar (app "Enviar estat a NS"); download = descarregar (app)
master (phone) = principal (app "mòbil principal"); master device = dispositiu principal
client (AAPSClient phone) = client; follower = seguidor
Tidepool, Open Humans = unchanged

### Wear and phone
watch / smartwatch = rellotge / rellotge intel·ligent (app)
watchface = watchface (app keeps it in labels); in prose "esfera (watchface)" is fine
complication = complicació (app); tile (Wear OS) = mosaic (Android Catalan)
phone = telèfon / mòbil (app uses both; prefer telèfon)

### Common buttons (app)
OK = OK; Cancel = Cancel·lar; Save = Desar; Delete / Remove = Eliminar; Clear = Esborrar
Add = Afegir; Edit = Editar; Next = Següent; Back = Enrere; Yes / No = Sí / No
Refresh = Actualitzar; Reset = Restablir; Close = Tancar; Start = Iniciar; Stop = Aturar

### Build (wiki)
build (verb) = compilar (app "compilar l'apk"); build variant = variant de compilació (app)
keystore = magatzem de claus (keystore); signing key = clau de signatura
repository = repositori; branch = branca; release notes = notes de la versió

## Links to other wiki pages
Link text that names another page must use that page's Catalan title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no Catalan title yet, translate the title and add
`Folder/Page.md = title` to your <name>.terms.txt.

## Known app inconsistencies (2026-11-21, decision in brackets)
- pair / pairing = "sincronitzar / Sincronització" in most strings, "emparellar" once; this mixes with
  sync. (use emparellar / emparellament; fix the pairing strings)
- Suspend = "Suspendre" (pump history, Omnipod button) and "Aturar" (suspendloop, uel_suspend, Omnipod
  cmd "Aturar lliuraments"). (Suspendre; Aturar only for Stop)
- carbs = "Carbohidrats" (most), "Carbs" (menu, short labels), "Hidrats de carboni" (carbs_g),
  "hidrats" (carbs_req_threshold_summary). (carbohidrats; Carbs only for short labels)
- basal rate = "Ràtio de basal" (basal_long_label). (taxa basal)
- I:C = "ràtio I:C" and "relació I:C". (ràtio)
- pump battery = "Pila de la bomba" and "Bateria baixa / Canvieu bateria" for the same pump alarm. (pila for the pump)
- fill = "Omplir pod" and "Empleneu un nou pod". (omplir)
- Calculator = "Calculadora de bolus" and "Calculador de bolus" (uel_bolus_calculator). (Calculadora)
- Refresh = "ACTUALITZAR" and "REFRESCAR" (uel_tt_ns_refresh). (Actualitzar)
- log = "registre" and "log/logs". (registre in new text)

## Clear app errors found (report / fix on Crowdin)
- omnipod_common_preferences_smb_beeps_enabled: "Bips d'SMS activats" -> "Bips d'SMB activats"
- add_short: "AFEGR" -> "AFEGIR"
- menu_resync: "Re-sincronizar" (Spanish spelling) -> "Resincronitzar"
- sensitivity_weighted_average: "mitja ponderada" -> "mitjana ponderada"
- smbalwaysdisabled: abbreviation "pq" -> "perquè"
- virtualpump_uploadstatus_title: "Enviar estat a NS" drops "or Tidepool"
- uel_bolus_calculator: not in upper case like the other uel_ strings ("Calculador de bolus")

## Round 3 decisions (2026-11-21, consistency pass)
# ca consistency pass r2, 2026-11-21 (app word wins; else glossary; else more common in dump)
Time shift (screen label) = Decalatge horari (app timeshift_label); time shift (prose) = desplaçament horari (glossary; ~25 wiki strings use it, ProfileSwitch page explains the label as "Decalatge horari (desplaçament horari)"). Not changed in this pass; see open items.
Allow client control (label) = Permetre el control des dels clients (app); prose "control des del client" (about 15 strings) is left as it is
AAPS directory (prose) = directori d'AAPS (app pref_title_aaps_directory); bold labels as on that screen: setup wizard "Directori AAPS" (permission_directory_title), Maintenance "Carpeta d'AAPS" (aaps_directory)
sheet (bottom sheet) = full (app clientcontrol_orphan_notification "full Gestionar"); not "panell"
Manage sheet = full Gestionar (app)
External Companion Apps = Apps complementàries externes (app tizen); not "Aplicacions companyes externes"
companion app = aplicació complementària
engineering mode = mode enginyeria (app engineering_mode_enabled); not "mode d'enginyeria"
clinician = professional sanitari (not "metge")
infusion set = set d'infusió (app equil_new_infusion_set_*); not "catèter"
fill syringe = xeringa (app); glossary "jeringa" is Spanish, wrong
custom watchface = watchface personalitzat (masculine, app complication_cwf_*); titles use "watchfaces personalitzats"
Weights (AutoISF heading) = Pesos
Bluetooth related issues (page title) = Problemes relacionats amb el Bluetooth (the H1 was already this; link texts changed)
Wear OS watch face changes (section) = Canvis dels watchfaces a Wear OS (the heading on WearOsSmartwatch)
Weight (AutoISF) = Pes (app weight_label, changed from "Ponderació")
pod status Suspended = Suspès (app omnipod_common_pod_status_suspended); Combo status Suspended = Suspesa (app combov2_suspended)
Suspend delivery = Suspendre lliurament (app); Suspend loop = Suspendre el llaç (app); Loop suspended = Llaç suspès; Pump suspended = Bomba suspesa
basal rate = taxa basal (app basal_long_label now "Taxa basal"); base basal rate = taxa basal base
Pairing (button) = Emparellament (app pairing); Not paired = No emparellat (app)
Show AvgDelta = Mostrar delta mitjana (app pref_show_avgdelta); Show Ago = Mostrar temps des de l'última lectura (app pref_show_ago)
Set alarm = Establir alarma (app wizard_set_alarm)
Set Basal Profile (Medtronic) = Establir perfil basal (app)
Re-Sync = Resincronitzar (app menu_resync)
Updating AAPS (section title) = Actualitzar AAPS
Android system labels: localized Android wording first, English in brackets only when unsure (decided 2026-11-21)
