# Greek wiki glossary (AndroidAPSdocs, language el)

Style: simple, clear Greek (modern standard, monotonic accents), short sentences, common words.
Address the reader in the polite plural (εσείς: "ρυθμίστε", "το τηλέφωνό σας"), as Greek technical
documents do. Greek letters only inside Greek words; product names stay in Latin letters.
Numbers: decimal comma (5,5 mmol/l), space before % is not used (2%), en dash in ranges (1–4 ώρες),
quotes «…». Units: mg/dl, mmol/l, U for insulin units (as in the English source), ώρ./λεπ. in text.
Keep "AAPS" (never "AndroidAPS" unless the source says so). AAPS is neuter (το AAPS).
UI labels: use the app's Greek string (values-el-rGR) in bold, exactly as on screen. The Greek app
was only half translated; missing labels are translated on Crowdin first, so the section
"App labels fixed on Crowdin" below and the page term list win over the local repo files.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Autosens, SMB, IOB, COB, ISF, IC, HbA1c, CGM, fork, workflow names, labels of
English-only tools (GitHub, AAPS Builder page, xDrip+, Juggluco, pump displays): English in bold,
a short Greek explanation in brackets is fine.

## Terms (app words win)
loop = κύκλωμα (app: Κύκλωμα); closed loop = κλειστό κύκλωμα; open loop = ανοιχτό κύκλωμα
Objectives = Βήματα (app); Objective N = Βήμα N
BG / blood glucose = γλυκόζη αίματος (BG); glucose = γλυκόζη; target = στόχος
carbs = υδατάνθρακες; basal = βασικός ρυθμός (app); temp basal = προσωρινός βασικός ρυθμός (TBR)
insulin = ινσουλίνη; pump = αντλία; profile = προφίλ; treatment = θεραπεία
Extended bolus = Εκτεταμένο bolus (app); bolus = bolus (as in the app)
Suspend loop = Αναστολή κυκλώματος (app); Settings = Ρυθμίσεις (app)
hypo = υπογλυκαιμία; clinician = γιατρός; caregiver = φροντιστής / γονέας
build (verb) = δημιουργώ / κάνω build; Building AAPS = Δημιουργία του AAPS
plugin = πρόσθετο (plugin); keystore = keystore (αποθήκη κλειδιών)
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Links to other wiki pages
Link text that names another page must use that page's Greek title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## App labels fixed on Crowdin (local values-el-rGR files are older; these win)
The whole Greek app was translated/reviewed on 2026-10-31 (5226/5226 approved). Main labels:
Configuration = Διαμόρφωση; Overview = Επισκόπηση; Manage = Διαχείριση; Objectives = Βήματα (N. Βήμα)
running mode = τρόπος λειτουργίας; calibration = βαθμονόμηση (never Καλιμπράρισμα); reservoir = δεξαμενή
cannula = καθετήρας; QuickWizard = Γρήγορος Οδηγός; scene = σενάριο; temp target = προσωρινός στόχος
Condition (automation) = Συνθήκη; Profile percentage = Ποσοστό προφίλ; Time shift = Χρονική μετατόπιση
watchface = watchface (Latin, as in the app: Προσαρμοσμένο Watchface); never πρόσοψη ρολογιού
tile = πλακίδιο; complication = Επιπλοκή (Greek Wear OS term); transmitter = πομπός; patched app = τροποποιημένη εφαρμογή
Insulin resistant adult = Ενήλικας με αντίσταση στην ινσουλίνη; activity graph = Δράση ινσουλίνης

## Decisions from the consistency pass (2026-10-31)
upload (Open Humans, Nightscout) = ανέβασμα / ανεβάζω (app: Ανέβασμα τώρα), not αποστολή
tubing = σωληνάκι (gen. σωληνακιού); tubeless = χωρίς σωληνάκι; reservoir change = αλλαγή δεξαμενής
watchface plural = τα watchface (no -s); master = κύρια συσκευή; client = πελάτης; paired client = συζευγμένος πελάτης
Nightscout token = διακριτικό (admin token may stay with «(διακριτικό διαχειριστή)»)
Resume (running mode dialog) = Συνέχιση; History browser = Εξερεύνηση ιστορικού
Page titles are nouns: Ενημέρωση σε νέα έκδοση ή κλάδο, Απομακρυσμένη παρακολούθηση, Ρυθμίσεις (Preferences.md),
Βήμα N – Δημιουργία/Εξουσιοδότηση ..., Επιλογή N – Δημιουργία νέου keystore / Ανέβασμα του υπάρχοντος keystore σας
Index toctree entries = the page H1.

## User decisions
- Wiki: upload and approve. App: translate missing strings + fix clear errors.

## Added 2026-11-08 (el batch4: ConfigBuilder rewrite, alarm settings)
Do Not Disturb (Android mode) = Μην ενοχλείτε
Vibration = Δόνηση
alarm volume (phone) = ένταση ξυπνητηριού
notification volume = ένταση ειδοποιήσεων
Force stop (Android) = Αναγκαστική διακοπή
App info (Android) = Πληροφορίες εφαρμογής
Apps (Android settings) = Εφαρμογές
Customize (watchface) = Προσαρμογή (Customize)
pair (calibration: fingerstick + sensor value) = ζεύγος (τιμών)
slope / offset (calibration) = κλίση / μετατόπιση
warm-up (sensor) = προθέρμανση
fingerstick = μέτρηση από το δάχτυλο
bin icon = εικονίδιο του κάδου
donate = κάνω δωρεά
medical device (certification) = ιατροτεχνολογικό προϊόν
search bar = γραμμή αναζήτησης
bottom navigation = κάτω γραμμή πλοήγησης
Manage sheet = φύλλο Διαχείριση

## Added 2026-11-08 (el batch3: AapsClient, browser build)
websockets = websockets (τα websockets, neuter plural)
mandatory = υποχρεωτικός/-ή/-ό
Updating your copy (AAPS Builder setup dropdown title, not translatable in Crowdin) = **Updating your copy** (ενημέρωση του αντιγράφου σας)
Connect Google Drive (setup page button) = Connect Google Drive (kept English like the other setup page buttons; Greek in brackets on first mention)
plain-text version = έκδοση απλού κειμένου
Select all / Copy / Paste (phone menu) = **Select all** (Επιλογή όλων) / **Copy** (Αντιγραφή) / **Paste** (Επικόλληση)
notarized (Apple) = επικυρωμένη (notarized)
package manager = διαχειριστής πακέτων
Apple developer account = λογαριασμός προγραμματιστή Apple (Apple developer)
iPhone simulator = προσομοιωτής iPhone (simulator)
Tab switcher = Εναλλαγή καρτελών
virtualization support = υποστήριξη εικονικοποίησης

## Added 2026-11-08 (el batch2)
Notification Reader (BG source, link text) = Ανάγνωση ειδοποιήσεων
Dexcom app (Notification Reader) = Εφαρμογή Dexcom (Ανάγνωση ειδοποιήσεων)
trusted (BG source) = αξιόπιστη (πηγή BG)
follower mode = λειτουργία follower
uploader (CGM uploader app) = εφαρμογή που ανεβάζει τα δεδομένα
setup page (AAPS Builder) = σελίδα ρύθμισης
repository = αποθετήριο
secret (GitHub) = secret
build log = log του build
run the build again = τρέξτε ξανά το build
Updating AAPS (link to aaps-builder-updates) = Ενημέρωση AAPS
Updating AAPS with AAPS Builder = Ενημέρωση του AAPS με το AAPS Builder
Retry (settings unreadable screen) = Επανάληψη (app)
Start fresh = Νέα αρχή (app)
AAPS settings cannot be read = Δεν είναι δυνατή η ανάγνωση των ρυθμίσεων του AAPS (app)
settings export = εξαγωγή ρυθμίσεων
key store (Android) = αποθήκη κλειδιών (key store)
Nightscout host = διακομιστής (host) του Nightscout
Remote control needs websockets = Ο απομακρυσμένος έλεγχος χρειάζεται websockets
After the update = Μετά την ενημέρωση
Fork and preparation file = Fork και αρχείο προετοιμασίας
Ease of Access Centre = «Ease of Access Centre» (Κέντρο Διευκόλυνσης Πρόσβασης)

## Added 2026-11-08 (el batch1)
Notification Reader (BG source) = Ανάγνωση ειδοποιήσεων (app)
trusted BG data source = αξιόπιστη πηγή δεδομένων BG
backfill (CGM) = συμπλήρωση κενών (backfill)
Clock changes (graph marker) = Αλλαγές ώρας
Time to eat (eat reminder) = Ώρα για φαγητό (app); +5 min = +5 λεπ. (app)
Alarms and reminders (Android permission) = Ξυπνητήρια και υπενθυμίσεις (app)
Bolus will be recorded only (not delivered by pump) = Το Bolus μόνο θα καταγραφεί (δε θα χορηγηθεί από την αντλία) (app)
Change smoothing (automation action) = Αλλαγή εξομάλυνσης (app)
Send SMS to all numbers = Αποστολή SMS σε όλους τους αριθμούς (app)
Run Autotune = Εκτέλεση Autotune (app)
bolus protection = προστασία bolus; settings protection = προστασία ρυθμίσεων
Comparison operators = Τελεστές σύγκρισης
Expanded status panel = ανοιγμένη περιοχή κατάστασης
