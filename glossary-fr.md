# French wiki glossary (AndroidAPSdocs, language fr)

Style: simple, clear French, short sentences, common words. Address the reader as "vous", like the
app does. Typography: non-breaking space before : ; ? ! and inside « … »; decimal comma
(5,5 mmol/l); 2 %; en dash in ranges (1–4 heures). Units: mg/dl, mmol/l, U for insulin units (as in
the English source), h / min in text.
Keep "AAPS" (never "AndroidAPS" unless the source says so). AAPS is masculine (AAPS est prêt).
UI labels: use the app's French string (values-fr-rFR) in bold, exactly as on screen. The app
translation is approved by a native proofreader - follow it; if a label looks clearly wrong, use
correct French and report it as APP bug.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Autosens, SMB, IOB, COB, ISF, IC, HbA1c, CGM, fork, workflow names, labels of
English-only tools (GitHub, AAPS Builder page, xDrip+, Juggluco, pump displays): English in bold,
a short French explanation in brackets is fine.

## Terms (app words win)
loop = boucle (app: Boucle); closed loop = boucle fermée; open loop = boucle ouverte
Objectives = Objectifs (app); Objective N = Objectif N
BG / blood glucose = glycémie (BG); target = cible; carbs = glucides (app)
basal = basal / débit basal; temp basal = débit basal temporaire (DBT/TBR as in the app)
insulin = insuline; pump = pompe; profile = profil; Treatments = Traitements (app)
Extended bolus = Bolus étendu (app); Suspend loop = Suspendre la boucle (app)
Settings = Paramètres (app); Configuration: use the app string of the Configuration screen
Pair with master = Associer au maître (app); Open plugin = Ouvrir le plugin; plugin = plugin
hypo = hypoglycémie; clinician = médecin; caregiver = aidant / parent
build (verb) = compiler; Building AAPS = Compiler AAPS; keystore = keystore (magasin de clés)
CGM = MGC (app); Configuration screen = Configuration; Settings = Paramètres (two screens)
Manage sheet = panneau Gestion (app Gestion); Insulin settings = Paramètres Insuline (app)
Sensitivity detection = Calcul de Sensibilité (app); Wear plugin = Montre (app); fingerstick = mesure au doigt
zero-temping = basal temporaire à zéro; hardened mode = mode renforcé; Master ↔ Client control = Contrôle maître ↔ client (H1 + all link texts, lower case)
cannula = canule (app; "cathéter" only where the English says catheter); Troubleshooting = Dépannage
loopers = boucleurs (no quotes); Release notes = Notes de version (in a sentence: les notes de version)
maxIOB / max-IOB = kept as the OpenAPS name; IOB/COB in prose = IA/GA (l'IA f., les GA m.)
Objective titles: noun style as in PreparingForAaps ("Objectif 6 : Démarrage de la boucle fermée avec l'Arrêt Glycémie Basse (AGB)")
upload = envoyer / envoi (never "télécharger"); reporting server = serveur de rapports
simple mode = mode simple; temp target = cible temporaire (CT); Dynamic ISF = SI dynamique (app)
IOB = IA (Insuline Active), COB = GA (Glucides Actifs), UAM = RNS, LGS = AGB, DIA = DAI, TDD = DTQ,
temp target = CT (cible temporaire), IC = G/I, ISF = SI - all as in the French app; aCOB stays English
Upload now = Envoyer maintenant (app, fixed); Full sync = Synchronisation complète (app)
BG Source = Source GLY (app); Status lights = Voyants d'état (app); Carb time = Heure des glucides (app, fixed)
safety = sécurité (app: Sécurité; not "sûreté"); follower = suiveur; caregiver = aidant
UAM = RNS (repas non signalés, app); OS-AID = OS-AID (explanation in brackets at first use)
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Links to other wiki pages
Link text that names another page must use that page's French title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## App labels fixed on Crowdin (local values-fr-rFR files are older; these win)
Upload now = Envoyer maintenant; Carb Time = Heure des glucides; Condition = Condition (was État);
Decrypt & Review = Déchiffrer et vérifier; Also replace pump settings = Remplacer aussi les paramètres de pompe;
Currently active (insulin) = Active actuellement; AAPS Snooze Alert = AAPS Mettre en veille l'alerte;
Open Humans strings use envoi/envoyer; CareLevo strings translated (Suspendre/Reprendre l'injection, Jeter le patch,
Batterie faible, Insuline faible, Délai de pose du patch dépassé ...).
Still "Télécharger ... vers NS" in the app for "Upload ... to NS" (not fixed; wiki quotes the app label).

## User decisions
- Wiki: upload and approve. App: missing strings + clear errors only.

## Added 2026-11-08 (Dexcom Notification Reader, alarm settings)
Notification Reader = Lecteur de notifications (app)
Permissions Required = Autorisations requises (app)
Notification Access = Accès aux notifications (app); Grant = Autoriser (app)
Allow notification access (Android) = Autoriser l'accès aux notifications
Allow restricted settings (Android) = Autoriser les paramètres restreints; App info = Infos sur l'appli
Notification read, reply & control (Android) = Lecture, réponse et contrôle des notifications
trusted BG data source = source de glycémie fiable; trusted data source = source de données fiable
backfill = complément des données manquantes (backfill)
uploader (CGM/Nightscout) = outil d'envoi
Mute for 5 minutes = Muet pendant 5 minutes (app); Silence alarm = Arrêter l'alarme (app); Silence and close = Arrêter et fermer (app)
Gradually increase notification volume = Augmenter progressivement le volume des notifications (app)
AAPS alarms (notification channel group) = Alarmes AAPS (app)
alarm volume / notification volume (phone) = volume des alarmes / volume des notifications
