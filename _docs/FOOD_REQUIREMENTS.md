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
| 1.9 | Keep decimal values. Today `FD`, the database and Nightscout keep whole numbers, so 0.4 g becomes 0. | to do (final: values as `Double`, one database migration) |
| 1.10 | The category list shows every category, also when the field was filled by an import ("OFF"). | done in POC |
| 1.11 | A button at import time to correct the values or the portion when the OFF data is wrong. The corrected food is saved in AAPS; see 5.2 for sending the fix to OFF. | to do |

**History**

- 2026-11-03: POC started from `dev`.
- 2026-11-08: bug found in use: values typed with a decimal ("14.8") were saved as 0. Fixed in the
  POC by reading the decimal and rounding. Same day: the category list was empty after an import,
  because the "OFF" value was read as a search. Fixed.
- 2026-11-08: the OFF team pointed to their two reference pages:
  <https://world.openfoodfacts.org/data> and
  <https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/>.

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
| 6.2 | Final: keep decimals end to end (see 1.9), and check what older AAPS versions do with a decimal before uploading one. | to do (final) |

## 7. Final version: open points for the maintainers

- Database: values as `Double`, a `barcode` column on the food, tables for dishes, meals and the
  meal history. One migration, designed once from what the POC taught.
- Proposed as small pull requests: (A) values and barcode, (B) OFF import, (C) dishes, meals,
  history and export.
- Extended carbs from fat and protein: last, opt-in, and a decision for the maintainers and for
  clinicians, not only a technical one.
