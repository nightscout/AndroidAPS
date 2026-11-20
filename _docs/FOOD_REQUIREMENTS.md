# Food feature: requirements and history

This file keeps the requirements of the food feature, in the order they came, with the decisions
taken and why. It covers the proof of concept (POC) on the branch `poc/food-openfoodfacts`, and what
the final version must do.

- **POC** means: tried in the POC, to learn. It is not the final code. A POC never changes the Room
  database version, and keeps its own data in a separate JSON file (`food_poc_book.json`).
- **Final** means: to do in the real development, after the feature request has been discussed with
  the maintainers.
- Status words: *done in POC* (written and tested by unit tests, still to be checked by use),
  *to do*, *idea* (to study, nothing decided), *final: commit N* (in the final code, commit N of
  the branch `Food/off-import-room37`: tested by unit tests, not yet tried on a phone).
- The branch `Food/off-import-room37` has three commits: (1) Room 37, values with decimals and the
  new food columns; (2) Nightscout in decimals; (3) the OFF import, the photos and the two editor
  modes. They give the pull requests (A) = 1 + 2 and (B) = 3. (C) = dishes, meals, history and
  export, still to do.

Nothing in this file is medical advice. The food values come from a public database filled by
volunteers and can be wrong: the user must always check them.

---

## 1. Food database and Open Food Facts (OFF) import

| # | Requirement | Status |
|---|---|---|
| 1.1 | Find a product on OFF by barcode (camera scan or typed) or by name. | done in POC; final: commit 3 |
| 1.2 | The import only fills the food editor. Nothing is saved until the user checks the values and presses Save. | done in POC; final: commit 3 |
| 1.3 | The editor offers the serving written on the package ("1 pot (125 g)") or 100 g. Values follow the portion, except a value the user typed. | done in POC; final: commit 3 |
| 1.4 | Only the barcode or the search words are sent to OFF. Nothing about the user or their therapy. | done in POC; final: commit 3 |
| 1.5 | Respect the OFF limits: 15 product reads and 10 searches per minute per user. No search while typing. A "too many requests" answer is never retried. | done in POC; final: commit 3 |
| 1.6 | User-Agent: OFF asks for `AppName/Version (contact)`. The contact must be a project address chosen by the maintainers, never the e-mail of a user. | final, commit 3: `AndroidAPS/<version> (https://github.com/nightscout/AndroidAPS)` for now. The POC sends `AndroidAPS-FoodPOC/0.1` with the same link. The address is still to choose, see "User-Agent and accounts" below. |
| 1.7 | Show the OFF attribution: data under ODbL, photos under CC BY-SA. | done in POC; final: commit 3 |
| 1.8 | Nutrition values are typed as written on the package (14.8, 1,7). Comma and dot are both accepted. | done in POC (rounded to whole numbers, see 1.9); final: commit 3, with decimals |
| 1.9 | Keep decimal values. Today `FD`, the database and Nightscout keep whole numbers, so 0.4 g becomes 0. Decimals matter for small portions that are multiplied (a food counted per piece or per gram). The total of a meal sent to the wizard or the carbs dialog **stays a whole number**: it is rounded once, from the exact sum. With the precision of carbs counting, a total with decimals means nothing. | final: Room 37 (values as `Double`, commit 1) and Nightscout in decimals (commit 2, see 6.2) |
| 1.10 | The category list shows every category, also when the field was filled by an import ("OFF"). | done in POC; final: commit 3 |
| 1.11 | A way at import time to correct the values or the portion when the OFF data is wrong. The corrected food is saved in AAPS; see 5.2 for sending the fix to OFF. | done in POC: the "Change the values" mode of 1.16; final: commit 3 |
| 1.12 | Carbs with or without fiber. A European label gives carbs without fiber; a US label gives total carbs, fiber included. OFF keeps what the label says, so "carbs" does not mean the same thing for every product. Keep the fiber value (one optional column) so it can be shown later, or a warning given on a product with a US label. Nothing is computed again. See the sizes below. | final: the fiber of OFF is saved and follows the portion, but is not shown (column in commit 1, import in commit 3). Showing it, or a warning: idea, low priority |
| 1.13 | "As sold" or "prepared" values. For a powder (mashed potatoes, cocoa, soup) OFF can have values for the dry product and for the prepared one. The import must show which ones it uses. The POC always uses "as sold" and does not say it. | done in POC (2026-11-15), to try in use: when OFF also has the carbs of the prepared product, the editor says in red that its values are as sold, with the carbs of the prepared product per 100 g or ml. The user changes the values if they weigh it prepared. No switch between the two (idea: only carbs are read for the prepared product, and the serving on the package is for one of the two). Final (commit 3): still silent, to do |
| 1.14 | OFF quality warnings (for example "energy does not match the other values"): shown at import time, not stored. | idea |
| 1.15 | Ingredients, allergens and traces: not stored. Shown on demand from the barcode (a "see on Open Food Facts" button, or read when the food is opened). Useful for families who also manage coeliac disease, which is common with type 1 diabetes. | idea |
| 1.16 | Two modes in the food editor. **Change the portion**: only the portion is typed, and carbs, fat, protein and energy follow it in proportion; the unit cannot be changed. **Change the values**: every field is typed, as on the package, for example to fix an OFF value, or to count a food in units eaten (1 biscuit, 1 baba) instead of grams. An import starts in the portion mode, a food changed starts in the values mode; the user can switch at any time. | done in POC (2026-11-12); final: commit 3 |
| 1.17 | The unit of a food used in dishes or standard meals changes (100 g becomes 1 biscuit). A line of a dish keeps its quantity in the unit it was saved with, so "50 g" would be read as "50 biscuits" and the carbs would be wrong without any sign. So: no new food is made; the dishes and meals that use the food show "Unit changed: check" instead of their total, cannot be sent to the wizard or the carbs dialog, and their line is marked in red in the pencil, with the old quantity. The user sets the quantity again and saves. After the change, a message says how many dishes and meals use the food. A portion changed in the same unit changes nothing for them (carbs per gram stay the same). "g" and " G " are the same unit. | done in POC (2026-11-12) |
| 1.18 | The category and subcategory fields: the arrow opens the list without the keyboard, so the keyboard does not hide the list. A tap on the text gives the focus, to type a new value. | done in POC (2026-11-14, bug found in use). The first fix (2026-11-13) did not work: the list box gives the focus to the text field each time the list opens. Now the field cannot take the focus while the list was opened with the arrow, and the arrow has a full touch area. A screen test taps the arrow for real. Final: commit 3 |
| 1.19 | A category filter on a category that has no food any more (its last food was moved to another category, or deleted) is removed, so the whole list comes back. Before, the filter stayed active with no chip shown to clear it, and the list was empty. Same for the subcategory. | done in POC (2026-11-13, bug found in use); final: commit 3 |
| 1.20 | Reorganise the library. Today the category and the subcategory are changed one food at a time: open the food, change it, save. To restructure a long list (new categories, a subcategory split in two), every food must be done by hand. There is no multiple selection and no drag and drop. Ideas, nothing decided: rename a category or a subcategory once for all its foods (for example a long press on its filter chip); select several foods and move them to a category and subcategory in one step; drag and drop between categories. To check first: AAPS reads the whole Nightscout food list again every few loads, and does not send foods to Nightscout. A change made on the phone to a food that came from Nightscout is then replaced by the Nightscout version (seen in the code, not tried). This is already true for the editor today. A reorganisation only lasts for foods made on the phone, unless it is also done in Nightscout, or AAPS can send foods to Nightscout (4.4). | idea (asked after use, 2026-11-14) |
| 1.21 | A scanned barcode that is already in the library: say so, with the name, category, subcategory and portion of the food (or foods) that have it, and ask before making a second food. Choices: open the food that is there, make another food anyway, or cancel. Not blocked: a second food for the same product can be wanted (one for 100 g, one for 1 pot). The check is made **before** the OFF request: it is local, works without network, and does not use one of the 15 product reads per minute (1.5). Only valid foods count. Also for a search by name: a result whose barcode is in the library is marked "already in your list". Limit: the barcode is not in Nightscout, so a food with the barcode made on another phone is not seen (a Nightscout reload keeps the local barcode: `Food.copyFrom` does not touch it). POC: the barcode is in the OFF links of the POC file; final: the `barcode` column. | done in POC (2026-11-15), to try in use; to do in (B) before it is opened |
| 1.22 | A scanned barcode that OFF does not know is kept. Today the import only says "not found" and the barcode is lost: the food is then typed by hand, without a barcode. Instead: a button "Make the food with this barcode" opens an empty editor (values mode) with the barcode on it; the user types the values of the package and can take a photo (2.10). The food then has its barcode for 1.21, for a later upload to OFF (5.1), and to look again later if OFF adds the product. To decide: when to look again (a button in the editor, or when the food is opened, at most once in a while because of the limits of 1.5); the values found are only offered, never written over the values of the user (as an import, 1.2). The marker "OFF has no photo" (2.2) must not stop this second look: "OFF does not know the product" and "OFF has no photo" are not the same. | done in POC (2026-11-15), to try in use: the button shows only after a search with a barcode; the food is saved with an OFF link marked "not in OFF" (`OffLink.inOff`, `notInOff` in the archive), without a photo to download. To do in (B) before it is opened. The second look at OFF comes later, once "when" is decided |
| 1.23 | Show the barcode of a food in a discreet way, so the user can check it (compare with the package, look the product up). Proposal: a small grey line at the bottom of the editor, "Barcode: 3017620422003", read only and easy to copy; it could later get the "see on Open Food Facts" link of 1.15. Better than a hidden gesture (a long press on the photo), which users do not find and screen readers do not offer. | done in POC (2026-11-15), to try in use; to do in (B) before it is opened |
| 1.24 | A shorter food editor (asked after use, 2026-11-18): on a tall screen about a quarter of the page was empty space, and a photo made the page scroll. The photo goes beside the title and the name (96 dp), with two icon buttons between them (take a photo, remove it), so adding a photo does not make the editor longer. 8 dp between the rows, as in the dish editor. Portion, unit and carbs on one row, as fat, protein and energy. The comment is one line when empty and grows while typing. The barcode goes on the row of the Cancel and Save buttons. The dish and meal editor gets the same photo header and one-line comment. Fields stay at the normal height of the app (56 dp). | done in POC (2026-11-19) |
| 1.25 | Category filters and sort of the food list (asked after use, 2026-11-20). Before: one chip per category and one per subcategory, on as many lines as needed, so many groups took room from the list; categories and subcategories looked the same; foods without a category could not be shown alone; the list was always the newest first. Now: one row of fixed height with two lists side by side, "Category" and "Subcategory" (their label tells them apart): "All" first, then the groups (A to Z), then "None" last, only when some items have no value and others have one. The subcategory list follows the chosen category, and goes back to "All" when the category changes. A list is not shown when no food uses it. A button on the right switches the order: "Recent" (the newest first, as before) or "A–Z" (case and accents do not count); the choice is kept (`BooleanNonKey.FoodSortByName`). "All" and "None" are new short strings, not the existing "All" and "None" of other screens: those are about a trend and a temp basal, and gendered languages need another form for a category. | done in POC (2026-11-20). Final: a small separate PR from `dev`, not in the OFF PRs: the chips are already in `dev` and do not depend on OFF or Room 37. Room 37 gets it from `dev` once that PR is merged |

**User-Agent and accounts (1.6, 2026-11-14)**

- The OFF documentation asks for `AppName/Version (ContactEmail)`, so an e-mail address. The POC
  and commit 3 send the GitHub page of the project. OFF does not check it, so it works, but it is
  not what they ask.
- Reading (what AAPS does): **one address, the same for every user**. The User-Agent names the
  program, not the person: OFF uses it to reach the people who can fix the code when a version
  sends too many requests, and every user builds the same code. An address per user would send
  the e-mail of the person to OFF with every barcode they look up, from a diabetes app: personal
  and health data. The OFF limits count per IP address, so each phone is already counted alone.
- Proposal for the maintainers: a generic address of the project (a role address, never the
  address of a person).
- Writing (section 5): OFF asks for a real account to write. It must be the **own OFF account of
  each user**, typed in the AAPS settings like a password and never in the code. One shared AAPS
  account is not possible: its password would be in the public code, and a ban by OFF would stop
  every AAPS user. With the account, OFF asks for `app_name`, `app_version` and `app_uuid` (a
  random id for each installation, so the moderators can stop one user without stopping AAPS).

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
| Has a photo | no | Flag in the POC file | Column `image`: the file name of the photo (OFF or taken by the user), see 2.10 |
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
| 2.1 | The database does not grow: only the barcode is kept with the food. The photo is a small file (OFF 200 px version, 10 to 30 kB) in the private files of the app, named after the barcode. | done in POC (the barcode is in the POC JSON file, not in the database); final: commit 3 (the `barcode` column) |
| 2.2 | A flag says whether OFF has a photo for the product. When it has none, no download is ever tried. | done in POC; final: commit 3, an empty marker file `<barcode>_none` instead of a flag |
| 2.3 | When a food is opened in the editor and its photo file is missing (new phone, restored backup, cleared data), the photo is downloaded again, without a button. | done in POC; final: commit 3 |
| 2.4 | OFF changes the photo address when a photo is replaced. When the old address is gone, the product is read again to get the new address. If OFF has no photo any more, the flag is set to false. If OFF does not answer, nothing changes and it is tried again next time. | done in POC; final: commit 3, without keeping the address: a missing photo always reads the product again |
| 2.5 | Photos are shown in the import results, on the food cards and at the top of the editor. | done in POC (Android only; desktop and iOS show foods without photos); final: commit 3 (the same) |
| 2.6 | Only https addresses on `openfoodfacts.org` are downloaded, and only small files. | done in POC; final: commit 3 |
| 2.7 | Photos are in the backup (see 4.3): a photo taken by the user cannot be downloaded again, and a restore on a new phone then needs no download. | done in POC (2026-11-12). Before: not in the backup. Final: in the archive of format 3 (4.5) |
| 2.8 | Delete the photo file when no food uses its barcode any more. | final: commit 3. The files that no food uses are deleted when the food screen opens, not when the food is deleted, so "undo delete" keeps the photo. Nothing is deleted when the food list is empty. The POC keeps every file. |
| 2.9 | Foods imported before 2.1 have no barcode, so they get no photo. They must be imported again. | known limit of the POC |
| 2.10 | A photo taken by the user, for any food (a home-made dish without a barcode, or to replace the OFF photo). It is shown instead of the OFF photo, which is kept: removing the photo of the user shows the OFF photo again, without a download. Small JPEG (512 px at most). Final: the `image` column holds the file name (Room 37); the file name is a random key, not the food id, which changes with a restore. | done in POC (2026-11-12, Android only); final: commit 3 for the foods |
| 2.11 | Photos also in the meal sheet, in the pencil of a dish or a meal, and in the detail of a meal of the history. | done in POC (2026-11-12) |
| 2.12 | A photo taken by the user for a dish or a standard meal (a home-made dish, or the whole plate), taken in its pencil. Optional: it does not replace the photos of the foods. Shown on the card of the dish, on a dish line of a meal, and on the card of the history (the detail of the meal still shows the photo of each food). The history keeps the photo of the dish as it was when the meal was eaten: a new photo or a deleted dish does not change the history, and the file is deleted only when nothing uses it any more. In the backup. Final: an `image` column in the dish and meal tables, as `Food.image`; the history keeps its own copy of the file name. | done in POC (2026-11-13, Android only; only meals sent after this date have it) |

## 3. Dishes, standard meals and meal history

| # | Requirement | Status |
|---|---|---|
| 3.1 | A dish is foods put together under one name. A standard meal is foods and dishes. Values are always computed from the foods, never stored. | done in POC |
| 3.2 | A meal is sent to the bolus wizard or to the carbs dialog. The history keeps a copy of what was eaten at that time. | done in POC |
| 3.3 | A meal is kept only when its treatment is really saved. It follows the treatment: when the treatment is deleted, the meal is removed too. A record that is only "not found" never removes a meal. | done in POC |
| 3.4 | Final: pass a meal id to the wizard and carbs dialogs, instead of finding the treatment again by note and time. | to do (final) |
| 3.5 | A line typed by hand in a meal: a name and the carbs the user estimated, for something that is not in the list (a home-made quiche next to a grated carrot and a dessert that are). It counts in the total sent to the wizard or the carbs dialog. It is marked "estimated" in the meal, the dishes, the history and the CSV. Its fat, protein and energy are not known, so the meal shows them as not known. It can be part of a saved dish or meal, with a fixed value. Something eaten often is simply added to the food list, with the estimate as its carbs: no "save as a food" button is needed. The food screen only makes sense when at least one food exists (otherwise the user types the carbs in the carbs dialog or the wizard), so the line typed by hand is added from the meal sheet, after a food. Foods are listed, not eaten in that order. | done in POC |
| 3.6 | A free text comment on each food, dish and standard meal. For the final version: a `note` column on the food (same migration as 1.9), and a field in the new tables. Decided (2026-11-18): each one has its own comment. The comment of a food stays on the food: it is not copied into the dishes that use it. A comment is not copied into the meal history, at least for now. It is shown only when the food, dish or meal is opened, never on its card: a card must not get taller, so the list keeps as many foods on screen. | done in POC (2026-11-18): the field "Notes" in the food editor and in the pencil of a dish or meal; the comments of the foods are kept in the POC file (`foodNotes`) and in the backup (`note` of a food, `note` of a dish or meal in format 3). Final: `note` on the food done (Room 37, commit 1). Dishes and meals: a `note` column in their tables, in (C) |
| 3.7 | A dish or a meal whose food was deleted can always be opened with its pencil. The line of the deleted food is shown in red, without carbs. It must be removed or replaced before the dish is saved. Before, the pencil only showed a message, and the dish could not be repaired. | done in POC (2026-11-12, bug found in use) |
| 3.8 | A dish or a meal with a line that cannot be computed (3.7, 1.17) is never saved or sent with this line counted as 0 g: that would give too few carbs to the wizard. | done in POC |
| 3.9 | History: a saved dish or meal sent as it is shows its name, with a button to show its lines. A meal put together on the spot, or a saved meal changed before it was sent ("croissant instead of bread"), always shows its lines, so what was really eaten is not hidden. The total stays visible. | done in POC, to try in use |
| 3.10 | History: a tap on a meal shows its detail, with the photo of each food. The wizard button keeps its own action (the detail of the bolus calculation). | done in POC (photos only for meals sent after 2026-11-12: older meals do not know their foods) |
| 3.11 | Fat, protein and energy of a dish, a standard meal (on its card), a meal put together on the spot, a dish being changed and a meal of the history (under the total). Only shown: these values are never sent to the wizard or the carbs dialog. A food often has no fat or protein value (a food typed by hand, a line typed by hand): the sum is then the part known, shown with ">" ("F: >12 g"), so a meal never looks lower in fat than it is. A value no food has is not shown. The CSV export keeps exact values only: a sum that is only the part known is left empty. | done in POC (2026-11-13) |
| 3.12 | Save a meal of the history as a dish or a standard meal. Today a meal put together on the spot can only be saved before it is sent: after that, all its lines must be typed again. The detail of a history meal (3.10) gets a button "Save as dish / standard meal" that asks for a name and the kind, and makes the dish or meal from the lines. The history card then shows the saved name, with its lines (3.9). To check first: a food line keeps its food (`foodId`) only for meals sent after 2026-11-12; a dish line keeps no dish id, only its name and photo (add one for new meals; for older ones, find the dish by name, or keep the line as a line typed by hand with its carbs); a line typed by hand stays one. A dish computes its values from the foods of today, so it can differ from what was eaten if a food was changed: show the new total before saving. The text sent with the treatment (`note`) must not change: the link between the meal and its treatment uses it. | done in POC (2026-11-15), to try in use. The detail of the meal has a button "Save as a dish or a meal": the same dialog as the meal sheet (name and kind), plus a line when the total of today differs from what was eaten, and a line when some lines are kept as typed by hand. A food line keeps its food and a dish line its dish while they are here, and the food is still counted in the same unit; an older line is found by its name when only one food (same unit) or one dish has it; any other line is kept as typed by hand with the carbs eaten. A dish line of a new meal now keeps its dish (`EatenItem.recipeId`). The meal then shows the saved name with its lines (`EatenMeal.savedLater`), and its note does not change |
| 3.13 | History: the space between the time and the first food of a meal put together on the spot is larger than between the time and the name of a saved dish or meal. The top line of the card is as high as its buttons (36 dp): the time alone sits in the middle of it, the time and the name fill it. On a full page this costs several lines. | done in POC (2026-11-15), to try in use: the lines go under the time (and the name), next to the total and its buttons |
| 3.14 | A category and a subcategory on a dish, as on a food, to find it in a long list. The dish editor has the same two fields as the food editor; they propose the groups of the foods and of the dishes, so a home-made dish can go with the foods it is close to, and the same letters as a group already used give that group. The dish tab has the same two filter lists as the food list (1.25), without the sort: dishes and meals are always A to Z, and they have no date. Not on the standard meals for now: they are few and the search is enough; the filter row can be added later if needed (for example breakfast, dinner). | done in POC (2026-11-20): `Recipe.category` and `subCategory` in the POC file and in the backup. Final: two columns in the dish table, in (C) |

UI rules agreed on 2026-11-05: a tap on a card adds it to the meal, the pencil changes it,
orange/green buttons send to Carbs/Wizard (theme colours). Delete only with a long press and the
delete button of the top bar, as in the treatments. Deleting a food that is still used elsewhere
shows a warning but is not blocked.

## 4. Export, backup and restore

| # | Requirement | Status |
|---|---|---|
| 4.1 | CSV export for a doctor's appointment: one file for the meal history, one for the catalogue. Period: 7, 14, 30 days or all. | done in POC |
| 4.2 | In the CSV, the insulin column is the insulin **really given**, not the calculated one: the user can change or stop a bolus, and the doctor compares meals with glucose results. | done in POC |
| 4.3 | Backup and restore of the POC data, to try the POC on a real phone and to move the data later into the final storage. A restore only adds what is missing, and never replaces a photo already on the phone. Format 2 (2026-11-12): one zip file with its own extension (`.aapsfood`, so Android does not offer to open it as a zip) holding the JSON and the photos. Format 3 (2026-11-14): the layout the final version reads, see below. The POC writes format 3 and still reads formats 1 and 2. | done in POC (personal use) |
| 4.4 | Final: check first how backup and sync can go through Nightscout (today AAPS only downloads foods from Nightscout), then the settings export as a second way. | to do (final) |
| 4.5 | Final: the archive fills the final version from the POC, so the photos are not lost. For the final version itself, the user wants a restore that brings back the whole library, photos included. Decision (2026-11-14): the archive of format 3 is the way from the POC to the final version, and the import and export of the final version. The POC writes it, so the final version reads format 3 only and has no code for the POC. Whether it stays the final backup is for the maintainers (4.4): at worst it is used once, to move from the POC. | decided; to do in (C) |
| 4.6 | The message bar shown after a food export does not look like the one shown after a settings export (seen in use, 2026-11-15). Rule: the food screen looks and behaves like the rest of AAPS, its messages too. Found in the code: both go to the same message bar of the app, in the same style (no type: dark bar, info icon, "Dismiss"). What differs: the text (settings: "Settings exported", one short line; food: "Backup saved in the exports folder of the AAPS directory.", two or three lines); the food screen has its own bottom bar and add button, and the message bar of the app is drawn over them; the food screen gives no type to its messages, so a failure ("It did not work") looks like a success, while other parts of AAPS show errors in red (`EventShowSnackbar.Type`). The POC also shows its messages through `LocalSnackbarHostState`, which the project rules ask not to use in new code (`dev` already uses it for the "undo delete" bar, which needs an action button: that use can stay). To do: find what the user sees (screenshot), then use the same texts, types and place as the settings export. The final version has no export yet: to do the same way in (C). | to do (asked after use, 2026-11-15) |

**Archive format 3 (2026-11-14)**

The archive is a zip file with the extension `.aapsfood`, saved in the exports folder of AAPS. It
holds `FoodBackup.json` and the photos in `images/<key>.jpg`: `<barcode>.jpg` for an OFF photo,
`user_<random>.jpg` for a photo taken by the user. The JSON file:

- `format` (3) and `exportedAt`. A file with a higher format is refused, not read wrong. Unknown
  fields are ignored, so a field can be added later without a new format.
- `foods`: `id` (a reference inside the file only: dishes and meals point to it), `nightscoutId`,
  `name`, `category`, `subCategory`, `portion`, `unit`, `carbs`, `fat`, `protein`, `energy`,
  `fiber` (all with decimals), `gi`, `barcode`, `noOffPhoto` (true only when OFF has no photo for
  the barcode), `notInOff` (true only when OFF did not know the barcode, 1.22), `image` (key of the
  photo taken by the user), `note`. Only the valid foods.
- `recipes`: the dishes and standard meals, as in format 2: `id` (random, the same on every phone),
  `name`, `kind` (DISH or MEAL), `photo`, and the lines (`foodId`, or `recipeId`, or
  `estimatedCarbs` for a line typed by hand).
- `eaten`: the meal history, as in format 2, with a copy of every value at the time. Without the
  database id of the treatment: `validatedAt` is the time of the treatment, and the treatment is
  found again by its time. A meal still waiting for its treatment is not saved. A dish line keeps
  its dish (`recipeId`), and `savedLater` marks a meal saved as a dish after it was eaten (3.12):
  both new on 2026-11-15, written only when set, so older files stay valid.

What changed since format 2, and why:

- The barcode and the photo are on the food, like the columns of Room 37, instead of in the lists
  `offLinks` and `photos`. The photo address is not kept: a missing photo reads the product again.
- `nightscoutId` is new. Nightscout finds a food again only by this id (`SyncNsFoodTransaction`):
  without it, a food restored before Nightscout sends its foods would be added a second time.
- The database id of the treatment of a meal is left out: it means nothing on another phone.
- The POC database keeps whole numbers: the POC writes them as decimals (12.0) and rounds the
  decimals of a file of the final version. It has no place for `fiber` and `note`: always empty
  in a file of the POC, and not read.

Restore rules of the final version (to do in C). The POC only adds what is missing.

- A food is found again by its `nightscoutId`, or else by name, portion, unit and carbs (capitals
  and spaces do not count).
- A food found keeps everything it has. Only its empty local fields are filled in: barcode, photo,
  note and fiber. The Nightscout fields (name, category, subcategory, portion, unit, carbs, fat,
  protein, energy, gi) are not changed: Nightscout would write its own values again. This cleans the library on a phone that
  moved from the POC to the final version.
- A food not found is added, with its `nightscoutId`. To check: a food deleted on the phone keeps
  its row and its `nightscoutId`, so the same id could then be on two rows.
- Dishes and meals: found again by `id`, or else by name and kind. Their lines point to the foods
  found on this phone.
- History: found again by `id`. Its lines point to the foods and dishes found on this phone. A
  photo file already on the phone is never replaced.
- Bug fixed in the POC on 2026-11-15: the lines of a restored meal kept the food ids of the other
  phone. On a new phone the detail of the meal could then show the photo of another food, and 3.12
  would have used the wrong food.
- The archive holds the meal history, which is health data, and it is not encrypted (the settings
  export of AAPS is). For the maintainers: encrypt it like the settings, or say clearly that it
  must not be shared.

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
| 6.1 | A food in Nightscout with a decimal value ("carbs": 10.5) must not make the whole food list fail. | done: in `dev` since 2026-11-17 (values rounded when read, upload format unchanged). Final, commit 2: decimals are kept when read; only the glycemic index group (`gi`), a whole number, is still rounded. |
| 6.2 | Final: keep decimals end to end (see 1.9). A whole value is still sent as a whole number (12, not 12.0), so a follower on an older version keeps reading every food without decimals. | final: commit 2 |
| 6.3 | Older AAPS versions. A Nightscout site belongs to one person, and when that person moves to AAPS 4 their followers (AAPSClient) move too. So there is no long mix of versions. There is a short time during the update: a follower still on 3.x loses its whole food list when the main phone uploads a decimal (the bug of 6.1). Accepted, or decimals are only sent when the value has some. | decision: no long-term support of whole numbers only |
| 6.4 | The Nightscout food editor (web page, `lib/food/food.js`) saves with `parseInt`: a food created by AAPS with 14.8 g becomes **14** when someone saves it in the web page (cut, not rounded). Today this already happens to `portion` (1.5 becomes 1). One line to change (`parseFloat`): propose a pull request to cgm-remote-monitor with the AAPS change. | to do |
| 6.5 | Other programs that use the Nightscout food list (checked on 2026-11-09): the Nightscout server stores the values as sent and its bolus calculator works with decimals. Nocturne (the .NET rewrite of Nightscout) already has portion, carbs, fat, protein and energy as `double` and `gi` as `int`, and has connectors that write foods. A community tool imports a Brazilian carbs table into Nightscout (writes only). No program was found that reads foods and needs whole numbers. The search was a web search and a read of these projects, not a code search of all GitHub: private tools and forks are not covered. | checked |

## 7. Final version: open points for the maintainers

- Database: values as `Double`, a `barcode` column on the food, tables for dishes, meals and the
  meal history. One migration, designed once from what the POC taught.
- What to keep from OFF (proposal, see the comparison in section 1): on the food, `barcode`,
  `image` and `note`, and `fiber` (1.12); `sugars` not kept. Done in Room 37 (commit 1).
  `hasImage` (a flag) was replaced by `image` (a file name) before the commit, so the photo of
  the user (2.10) needs no other migration. The photo address stays a local cache:
  it changes when OFF replaces a photo, and the product is read again to find it. Ingredients,
  allergens and scores are read from OFF when needed, never stored.
- Proposed as small pull requests: (A) values and barcode (commits 1 and 2), (B) OFF import
  (commit 3, plus 1.21, the first part of 1.22 and 1.23, decided on 2026-11-15), (C) dishes,
  meals, history and export. The tables of (C) go in the same Room 37
  migration (decision of 2026-11-14), so the final version has one migration for the food
  feature. A phone that already has a build of (A) or (B) must clear the app data when the
  schema of 37 changes: only the development phone gets these builds.
- Extended carbs from fat and protein: last, opt-in, and a decision for the maintainers and for
  clinicians, not only a technical one.
