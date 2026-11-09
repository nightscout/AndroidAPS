# Food feature: requirements and history

This file keeps the requirements of the food feature, in the order they came, with the decisions
taken and why. It covers the proof of concept (POC) on the branch `poc/food-openfoodfacts`, and what
the final version must do.

- **POC** means: tried in the POC, to learn. It is not the final code. A POC never changes the Room
  database version, and keeps its own data in a separate JSON file (`food_poc_book.json`).
- **Final** means: to do in the real development, after the feature request has been discussed with
  the maintainers.
- Status words: *done in POC* (written and tested by unit tests, still to be checked by use),
  *to do*, *idea* (to study, nothing decided).

Nothing in this file is medical advice. The food values come from a public database filled by
volunteers and can be wrong: the user must always check them.

---

## 1. Food database and Open Food Facts (OFF) import

| # | Requirement | Status |
|---|---|---|
| 1.1 | Find a product on OFF by barcode (camera scan or typed) or by name. | done in POC |
| 1.2 | The import only fills the food editor. Nothing is saved until the user checks the values and presses Save. | done in POC |
| 1.3 | The editor offers the serving written on the package ("1 pot (125 g)") or 100 g. Values follow the portion, except a value the user typed. | done in POC |
| 1.4 | Only the barcode or the search words are sent to OFF. Nothing about the user or their therapy. | done in POC |
| 1.5 | Respect the OFF limits: 15 product reads and 10 searches per minute per user. No search while typing. A "too many requests" answer is never retried. | done in POC |
| 1.6 | User-Agent: OFF asks for `AppName/Version (contact)`. The contact must be a project address chosen by the maintainers, never the e-mail of a user. | to do (final) |
| 1.7 | Show the OFF attribution: data under ODbL, photos under CC BY-SA. | done in POC |
| 1.8 | Nutrition values are typed as written on the package (14.8, 1,7). Comma and dot are both accepted. | done in POC (rounded to whole numbers, see 1.9) |
| 1.9 | Keep decimal values. Today `FD`, the database and Nightscout keep whole numbers, so 0.4 g becomes 0. Decimals matter for small portions that are multiplied (a food counted per piece or per gram). The total of a meal sent to the wizard or the carbs dialog **stays a whole number**: it is rounded once, from the exact sum. With the precision of carbs counting, a total with decimals means nothing. | to do (final: values as `Double`, one database migration) |
| 1.10 | The category list shows every category, also when the field was filled by an import ("OFF"). | done in POC |
| 1.11 | A button at import time to correct the values or the portion when the OFF data is wrong. The corrected food is saved in AAPS; see 5.2 for sending the fix to OFF. | to do |
| 1.12 | Carbs with or without fiber. A European label gives carbs without fiber; a US label gives total carbs, fiber included. OFF keeps what the label says, so "carbs" does not mean the same thing for every product. Keep the fiber value (one optional column) so it can be shown later, or a warning given on a product with a US label. Nothing is computed again. See the sizes below. | idea, low priority |
| 1.13 | "As sold" or "prepared" values. For a powder (mashed potatoes, cocoa, soup) OFF can have values for the dry product and for the prepared one. The import must show which ones it uses. The POC always uses "as sold" and does not say it. | to do |
| 1.14 | OFF quality warnings (for example "energy does not match the other values"): shown at import time, not stored. | idea |
| 1.15 | Ingredients, allergens and traces: not stored. Shown on demand from the barcode (a "see on Open Food Facts" button, or read when the food is opened). Useful for families who also manage coeliac disease, which is common with type 1 diabetes. | idea |

**History**

- 2026-11-03: POC started from `dev`.
- 2026-11-08: bug found in use: values typed with a decimal ("14.8") were saved as 0. Fixed in the
  POC by reading the decimal and rounding. Same day: the category list was empty after an import,
  because the "OFF" value was read as a search. Fixed.
- 2026-11-08: the OFF team pointed to their two reference pages:
  <https://world.openfoodfacts.org/data> and
  <https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/>.
- 2026-11-09: one full OFF product was read to compare its fields with the food (376 fields, most
  of them for OFF itself). The comparison is below; it led to 1.12 to 1.15 and to the proposal
  "What to keep from OFF" in section 7.
- 2026-11-09: a line typed by hand in a meal (3.5) and a comment field (3.6) asked after use.

**Comparison: OFF product and AAPS food (2026-11-09)**

| OFF data | Food today (`FD`) | POC | Final |
|---|---|---|---|
| Name, brand | `name` | Brand added to the name | As today. A `brand` column only if we want to filter by brand. |
| Barcode | no | In the POC file | Column |
| Has a photo | no | Flag in the POC file | Column `hasImage` |
| Carbs | `Int` | Read as `Double`, rounded | `Double` |
| Sugars, fiber | no | Read and shown at import, not saved | To decide (see 1.12) |
| Polyols, starch, added sugars | no | no | Polyols to decide, not the others |
| Fat, protein, energy | `Int` | Read as `Double` | `Double` |
| Saturated fat, salt, sodium | no | no | No: not used for insulin |
| Serving on the package, total quantity | `portion` + `unit` | Used to offer the portion | Covered |
| As sold or prepared | no | no | See 1.13 |
| Ingredients, allergens, traces | no | no | Not stored, see 1.15 |
| Nutri-Score, NOVA, Eco-Score, labels, OFF categories | no | no | No: not about the therapy, and they change |
| Glycemic index | `gi` | no | Not in OFF. In Nightscout `gi` is a group 1/2/3 (low, medium, high), not an index: it stays `Int`. |

**Fiber: how big is the difference (1.12, 2026-11-09)**

Approximate values per 100 g, from the Ciqual (ANSES) and USDA tables. They change from one
product to another and are only sizes, not reference values.

| Food | Carbs without fiber (EU label) | Fiber | More with a US label |
|---|---|---|---|
| Sugar, soda, juice, sweets | 10–100 g | ~0 | 0 % |
| White rice, cooked | ~28 g | ~0.4 g | ~1 % |
| White bread, cooked pasta | 30–50 g | 2–3 g | ~5 % |
| Banana, potato | 17–20 g | 2–2.6 g | ~12 % |
| Oat flakes, wholemeal bread | 40–60 g | 7–10 g | ~17 % |
| Apple | ~11 g | ~2.4 g | ~20 % |
| Dark chocolate 70 % | ~33 g | ~11 g | ~33 % |
| Chickpeas cooked, raw carrot | 7–19 g | 3–8 g | ~40 % |
| Lentils cooked, bran cereals | 13–46 g | 8–27 g | ~60 % |
| Broccoli | ~3 g | ~2.6 g | 80–100 % |

- A usual Western day has 15 to 25 g of fiber for 200 to 250 g of carbs: about **5 to 10 %**.
- A user who always counts the same way has an insulin-to-carb ratio set for that way: a steady
  difference is taken in by the ratio.
- The big percentages are on foods with few carbs (150 g of carrot: 10.5 g or 14.7 g). The case
  that counts in grams is a large portion of pulses or bran cereals (200 g of cooked lentils: about
  26 g or 42 g, so 16 g apart).
- The real risk is mixing labels (a product with a US label scanned in France, or US products with
  foods typed from a European table): the difference is then not steady, and the ratio does not
  take it in. Some US users also count "net carbs" (total minus some or all of the fiber).
- Decision: no change to the computation. Keep the fiber value only.

## 2. Product photos

**Why:** when two products are close (same brand, other flavour), the package is often the only way
to pick the right one.

| # | Requirement | Status |
|---|---|---|
| 2.1 | The database does not grow: only the barcode is kept with the food. The photo is a small file (OFF 200 px version, 10 to 30 kB) in the private files of the app, named after the barcode. | done in POC (the barcode is in the POC JSON file, not in the database) |
| 2.2 | A flag says whether OFF has a photo for the product. When it has none, no download is ever tried. | done in POC |
| 2.3 | When a food is opened in the editor and its photo file is missing (new phone, restored backup, cleared data), the photo is downloaded again, without a button. | done in POC |
| 2.4 | OFF changes the photo address when a photo is replaced. When the old address is gone, the product is read again to get the new address. If OFF has no photo any more, the flag is set to false. If OFF does not answer, nothing changes and it is tried again next time. | done in POC |
| 2.5 | Photos are shown in the import results, on the food cards and at the top of the editor. | done in POC (Android only; desktop and iOS show foods without photos) |
| 2.6 | Only https addresses on `openfoodfacts.org` are downloaded, and only small files. | done in POC |
| 2.7 | Photos are not in the backup: the barcode and the flag are enough to download them again. | done in POC |
| 2.8 | Delete the photo file when no food uses its barcode any more. | to do (final). The POC keeps the files, so that "undo delete" keeps working. |
| 2.9 | Foods imported before 2.1 have no barcode, so they get no photo. They must be imported again. | known limit of the POC |
| 2.10 | A photo taken by the user, for a home-made dish without a barcode. Same file mechanism, named after the food id. | idea |

## 3. Dishes, standard meals and meal history

| # | Requirement | Status |
|---|---|---|
| 3.1 | A dish is foods put together under one name. A standard meal is foods and dishes. Values are always computed from the foods, never stored. | done in POC |
| 3.2 | A meal is sent to the bolus wizard or to the carbs dialog. The history keeps a copy of what was eaten at that time. | done in POC |
| 3.3 | A meal is kept only when its treatment is really saved. It follows the treatment: when the treatment is deleted, the meal is removed too. A record that is only "not found" never removes a meal. | done in POC |
| 3.4 | Final: pass a meal id to the wizard and carbs dialogs, instead of finding the treatment again by note and time. | to do (final) |
| 3.5 | A line typed by hand in a meal: a name and the carbs the user estimated, for something that is not in the list (a home-made quiche next to a grated carrot and a dessert that are). It counts in the total sent to the wizard or the carbs dialog. It is marked "estimated" in the meal, the dishes, the history and the CSV. Its fat, protein and energy are not known, so the meal shows them as not known. It can be part of a saved dish or meal, with a fixed value. Something eaten often is simply added to the food list, with the estimate as its carbs: no "save as a food" button is needed. The food screen only makes sense when at least one food exists (otherwise the user types the carbs in the carbs dialog or the wizard), so the line typed by hand is added from the meal sheet, after a food. Foods are listed, not eaten in that order. | done in POC |
| 3.6 | A free text comment on each food, dish and standard meal. For the final version: a `note` column on the food (same migration as 1.9), and a field in the new tables. To decide: is the comment of a standard meal copied into the meal history? | to do (final) |

UI rules agreed on 2026-11-05: a tap on a card adds it to the meal, the pencil changes it,
orange/green buttons send to Carbs/Wizard (theme colours). Delete only with a long press and the
delete button of the top bar, as in the treatments. Deleting a food that is still used elsewhere
shows a warning but is not blocked.

## 4. Export, backup and restore

| # | Requirement | Status |
|---|---|---|
| 4.1 | CSV export for a doctor's appointment: one file for the meal history, one for the catalogue. Period: 7, 14, 30 days or all. | done in POC |
| 4.2 | In the CSV, the insulin column is the insulin **really given**, not the calculated one: the user can change or stop a bolus, and the doctor compares meals with glucose results. | done in POC |
| 4.3 | JSON backup and restore of the POC data, to try the POC on a real phone and to move the data later into the final storage. A restore only adds what is missing. | done in POC (personal use) |
| 4.4 | Final: check first how backup and sync can go through Nightscout (today AAPS only downloads foods from Nightscout), then the settings export as a second way. | to do (final) |

## 5. Sending data to Open Food Facts (upload)

Asked by the OFF team on 2026-11-08. **Not a priority** for a first version: the authentication
must be studied first. Kept here so it is not lost.

| # | Idea | Notes |
|---|---|---|
| 5.1 | A scanned barcode is not in OFF: offer to add it, with a photo of the package and a photo of the nutrition table. | Photos are uploaded with `/api/v3.3/product/{barcode}/images`. |
| 5.2 | The OFF values are wrong: offer to send the corrected values (see 1.11). | Product write: v2 and v3 APIs. |

**What the OFF documentation says (read on 2026-11-08):**

- Every write needs an OFF account. There is no OAuth yet ("will be supported in the future").
- Today: a session cookie from the login API (preferred; same IP address; about 10 sessions per
  user), or the user name and password sent with each write request.
- An app should send `app_name`, `app_version` and `app_uuid` (a random id per user, so OFF can
  block one bad user without blocking the app).
- Production and the test server ("staging") have separate accounts.
- No limit on write requests.

**Questions to answer before any work:**

- Can AAPS store an OFF password safely, or should it wait for OAuth?
- The user must clearly choose to send data. Nothing is sent without an action of the user.
- A photo can show more than the package (a hand, a room): the user must see it before sending.
- Licence: what the user sends is published under ODbL (data) and CC BY-SA (photos).

## 6. Nightscout

| # | Requirement | Status |
|---|---|---|
| 6.1 | A food in Nightscout with a decimal value ("carbs": 10.5) must not make the whole food list fail. | done: separate fix branch `Fix/ns-food-decimal-values` (values rounded when read, upload format unchanged). Not yet proposed. |
| 6.2 | Final: keep decimals end to end (see 1.9). | to do (final) |
| 6.3 | Older AAPS versions. A Nightscout site belongs to one person, and when that person moves to AAPS 4 their followers (AAPSClient) move too. So there is no long mix of versions. There is a short time during the update: a follower still on 3.x loses its whole food list when the main phone uploads a decimal (the bug of 6.1). Accepted, or decimals are only sent when the value has some. | decision: no long-term support of whole numbers only |
| 6.4 | The Nightscout food editor (web page, `lib/food/food.js`) saves with `parseInt`: a food created by AAPS with 14.8 g becomes **14** when someone saves it in the web page (cut, not rounded). Today this already happens to `portion` (1.5 becomes 1). One line to change (`parseFloat`): propose a pull request to cgm-remote-monitor with the AAPS change. | to do |
| 6.5 | Other programs that use the Nightscout food list (checked on 2026-11-09): the Nightscout server stores the values as sent and its bolus calculator works with decimals. Nocturne (the .NET rewrite of Nightscout) already has portion, carbs, fat, protein and energy as `double` and `gi` as `int`, and has connectors that write foods. A community tool imports a Brazilian carbs table into Nightscout (writes only). No program was found that reads foods and needs whole numbers. The search was a web search and a read of these projects, not a code search of all GitHub: private tools and forks are not covered. | checked |

## 7. Final version: open points for the maintainers

- Database: values as `Double`, a `barcode` column on the food, tables for dishes, meals and the
  meal history. One migration, designed once from what the POC taught.
- What to keep from OFF (proposal, see the comparison in section 1): on the food, `barcode`,
  `hasImage` and `note`, maybe `fiber` and `sugars` (1.12). The photo address stays a local cache:
  it changes when OFF replaces a photo, and the product is read again to find it. Ingredients,
  allergens and scores are read from OFF when needed, never stored.
- Proposed as small pull requests: (A) values and barcode, (B) OFF import, (C) dishes, meals,
  history and export.
- Extended carbs from fat and protein: last, opt-in, and a decision for the maintainers and for
  clinicians, not only a technical one.
