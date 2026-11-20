# Crowdin API Workflow

How to translate AAPS strings on Crowdin through its API v2, **without touching the repo's
`values-xx/` files** (they come back from Crowdin's own sync, and an edit there would conflict).

## One-time setup: the token

1. Create a Personal Access Token at https://crowdin.com/settings#api-key with the scopes
   `Projects` and `Translations`. It is shown only once, at creation.
2. Save the bare token (nothing else in the file) in a file **outside every git checkout**, and set
   the environment variable `CROWDIN_TOKEN_FILE` to that file's path.
3. **Never show the token.** Do not `cat`, `type`, `echo`, `grep` or `sed` the token file, and never
   put the token on a command line or in a URL: command lines end up in logs and in permission
   prompts. Only the helper script below reads it, and it never prints it.

There is no Crowdin CLI step anywhere in this procedure. Everything runs as small Node scripts
(Node 18 or newer, for `fetch`). Save them in a new folder in the session scratchpad (or in
`%TEMP%` when there is no scratchpad), not in the repo, and run them from the repo root.

### The helper: `crowdin-api.js`

Every script below starts with `require('<scripts folder>/crowdin-api')`.

```js
// crowdin-api.js - Crowdin API v2 for the AndroidAPS project (309752). Reads the token from the
// file named in CROWDIN_TOKEN_FILE and never prints it.
const fs = require('fs')
const tokenFile = process.env.CROWDIN_TOKEN_FILE
if (!tokenFile || !fs.existsSync(tokenFile)) {
  console.error('Set CROWDIN_TOKEN_FILE to the file that holds the token.')
  process.exit(2)
}
const token = fs.readFileSync(tokenFile, 'utf8').trim()
const base = 'https://api.crowdin.com/api/v2/projects/309752'

async function call(method, path, body) {
  for (let attempt = 1; ; attempt++) {
    let r
    try {
      r = await fetch(base + path, {
        method,
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: body ? JSON.stringify(body) : undefined,
      })
    } catch (e) { // a short network drop
      if (attempt < 6) { await new Promise(res => setTimeout(res, 2000 * attempt)); continue }
      throw e
    }
    if ((r.status === 429 || r.status >= 500) && attempt < 6) { await new Promise(res => setTimeout(res, 2000 * attempt)); continue }
    const text = await r.text()
    const json = text ? JSON.parse(text) : {}
    if (!r.ok) { const e = new Error(`${method} ${path} -> HTTP ${r.status} ${text.slice(0, 300)}`); e.status = r.status; e.body = json; throw e }
    return json
  }
}
const get = path => call('GET', path)
const post = (path, body) => call('POST', path, body)

// Every page of a list, as plain objects.
async function all(path) {
  const out = []
  for (let offset = 0; ; offset += 500) {
    const page = await get(`${path}${path.includes('?') ? '&' : '?'}limit=500&offset=${offset}`)
    out.push(...page.data.map(x => x.data))
    if (page.data.length < 500) return out
  }
}

const devBranch = async () => (await all('/branches')).find(b => b.name === 'dev').id

// Add a translation; when the same text is already there, use that one. Approve it only when asked.
// Approving moves the approval away from an older translation of the same string.
async function addTranslation(stringId, languageId, text, approve) {
  let id
  try {
    id = (await post('/translations', { stringId, languageId, text })).data.id
  } catch (e) {
    if (e.status !== 400 && e.status !== 409) throw e
    const same = (await all(`/translations?stringId=${stringId}&languageId=${languageId}`)).find(t => t.text === text)
    if (!same) throw e
    id = same.id
  }
  if (approve) await post('/approvals', { translationId: id }).catch(e => { if (e.status !== 400 && e.status !== 409) throw e })
  return id
}

module.exports = { get, post, all, devBranch, addTranslation }
```

## Project specifics

- Project id `309752`. Everything lives in the Crowdin branch **`dev`**, so file paths in the API
  start with `/dev/` (for example `/dev/app/src/main/res/values/strings.xml`).
- Language ids are Crowdin's (`cs`, `pt-BR`, `es-ES`). The repo folders use the Android code
  (`values-cs-rCZ`, `values-pt-rBR`); the project's `targetLanguages` give both (`id`, `androidCode`).
- The project has **"Export only approved"** on. The repo's `values-xx/` files therefore hold only
  approved text. To see unapproved translations too, read them through the API (step 2 below).
- Sources reach Crowdin through Crowdin's GitHub integration. It syncs `dev` a few minutes after a
  push and reads **only the paths listed in `crowdin.yml`**. Translations come back as "New Crowdin
  updates" pull requests, which hold the last approved export - so the repo's `values-xx/` files
  are a record of what was approved.
- **The integration owns `crowdin.yml`.** Saving its settings in the Crowdin web UI rewrites the
  file and strips every comment (commit "Update Crowdin configuration file [ci skip]", e.g.
  `2be1bd2831`). Keep notes in commit messages or here, never in `crowdin.yml`.
- Some `values-xx/` folders in the repo are for languages that are not Crowdin target languages
  (in 2026-09: `cy`, `fi`, `sl`, `ta`). The API refuses translations for them.

## Workflow: translate one file

### 1. Find the file id

```js
// files.js - node files.js [part of a path]
const { all, devBranch } = require('<scripts folder>/crowdin-api')
;(async () => {
  const files = await all(`/files?branchId=${await devBranch()}&recursion=1`)
  for (const f of files) if (f.path.includes(process.argv[2] ?? '')) console.log(f.id, f.path)
})()
```

### 2. Read the state of one language

```js
// state.js - node state.js <fileId> <languageId>      e.g. node state.js 7 cs
const { all } = require('<scripts folder>/crowdin-api')
;(async () => {
  const [fileId, lang] = process.argv.slice(2)
  const strings = await all(`/strings?fileId=${fileId}`)
  const translations = new Map((await all(`/languages/${lang}/translations?fileId=${fileId}`)).map(t => [t.stringId, t]))
  const approved = new Set((await all(`/approvals?fileId=${fileId}&languageId=${lang}`)).map(a => a.translationId))
  for (const s of strings) {
    const t = translations.get(s.id)
    const state = !t ? 'missing' : approved.has(t.translationId) ? 'approved' : 'unapproved'
    console.log(`${s.identifier}\t${state}\t${t?.text ?? ''}`)
  }
})()
```

Look at how the existing translations of the file are written - escapes such as `\'` and `\n`,
and the placeholders - and write new ones the same way.

### 3. Add translations

Write the new texts to a JSON file, `{ "<string key>": "<text>", ... }`, then:

```js
// add.js - node add.js <fileId> <languageId> <json file> [--approve]
const fs = require('fs')
const { all, addTranslation } = require('<scripts folder>/crowdin-api')
;(async () => {
  const [fileId, lang, json, flag] = process.argv.slice(2)
  const wanted = JSON.parse(fs.readFileSync(json, 'utf8'))
  const ids = new Map((await all(`/strings?fileId=${fileId}`)).map(s => [s.identifier, s.id]))
  for (const [key, text] of Object.entries(wanted)) {
    if (!ids.has(key)) { console.log('no such string:', key); continue }
    await addTranslation(ids.get(key), lang, text, flag === '--approve')
    console.log('ok', key)
  }
})()
```

- **Without `--approve`** (the default) the translations wait for review in Crowdin. This is the
  normal way.
- **With `--approve`** they are approved at once. Use it only for text you trust.
- To approve a translation that is already there, unapproved: `post('/approvals', { translationId })`
  with the id from step 2.
- Never delete other people's translations. `DELETE /translations/<id>` is only for your own wrong
  suggestion.

Then run step 2 again and check that every string you meant to add is there.

## Common gotchas

- **A hand-written translation in a short folder** (e.g. `values-ko` next to Crowdin's
  `values-ko-rKR`) is not what Crowdin reads or writes. To bring it in, add its strings to Crowdin
  for that language (step 3), then delete the short folder, or Korean lives in two places and
  drifts (carelevo's `values-ko` was deleted on 2026-10-15 for this reason).
- **Changing the placeholders of an English string drops its translations on Crowdin.** Numbering
  `%s %s` to `%1$s %2$s` (carelevo, 2026-09-29) left those strings untranslated in every language.
  The old text is still offered by translation memory; enter it again with the new placeholders
  and approve it.
- **A string the same as the English text** (names, units, "STEP 1.") can still be added as a
  translation. Whether it counts as translated depends on the project settings, so the percentage
  on Crowdin may stay below 100 %.

## Wiki links in strings

The objectives and exam hints (and a few other strings) link to the wiki. Each language should
open the wiki in its own language when there is one.

- **Wiki languages:** cs de el en es fr he ko lt nl ru tr zh-cn zh-tw, as
  `https://wiki.aaps.app/<code>/latest/...`. The translated docs have no `dev` build, so a
  translation always points to `latest`. A page that exists only in English `dev` gets no
  translations at all.
- **In Crowdin:** for a wiki language the translation is the English link with that language's
  code, approved. Slovak gets the Czech wiki (close language, better than English). Every other
  language opens the English page: either it has no translation, or a plain copy of the English
  link. A copy is harmless - a later change of the English link drops it anyway. Do not delete
  copies: translators of other languages add them again within minutes to reach 100% (Italian and
  Norwegian did on 2026-10-15). Only a link to a *different* page or language needs fixing.
- **Language ids:** Crowdin `es-ES` → `es`, `zh-CN` → `zh-cn`, `zh-TW` → `zh-tw`, `sk` → `cs`;
  the others are the same.
- **Anchors: use the fixed label, never the heading anchor.** `#the-homescreen` is made from the
  heading text, so on a translated page it is `#die-startseite` or `#id2` and the link opens the
  top of the page. The docs source has labels such as `(AapsScreens-the-homescreen)=` before a
  heading; they are not translated and become `#aapsscreens-the-homescreen` (lower case) in every
  language. Check the label on the translated pages before using it.
- **A section with no label:** add one in AndroidAPSdocs (`docs/EN/...`, `(Page-name)=` on the
  line before the heading). In the AndroidAPSdocs Crowdin project, mark the new label string
  **hidden**, like the existing ones, so translators never see it. The translated pages get the
  label only with the next docs Crowdin export.
- **Changing an English link drops all its translations on Crowdin.** After the sync, set the
  wiki languages again. Handy when anchors change: there is nothing old left to delete.
- **Many links × 30 languages is not a job for the web editor.** Use the helper with a small script:
  list the strings of the file, `DELETE /translations?stringId=&languageId=` (only for these link
  strings, which nobody else edits), `addTranslation(..., true)`. Run it as a dry run first, and
  again after applying, until it reports nothing left to change.

## Fixing one string in the web editor

Without an API token, the editor is the only way, and every click has to be confirmed in the
browser extension, so it suits a handful of strings at most.

- **Fastest: a direct link to the string.** `https://crowdin.com/editor/androidaps/<fileId>/en-<lang>#<stringId>`
  opens the string already selected, in any language. Find both ids once: open the file in the
  editor, and the string's list item has the id `phrase_<stringId>`. Example: `tdd_line` in
  `plugins/sync` is file `5835`, string `216755`.
- Otherwise open `https://crowdin.com/editor/androidaps/all/en-<lang>?search_scope=everything` and
  type the string **key** in "Search strings" (the search matches keys too). Changing only `#q=` in
  the URL does not search again.
- `<lang>` is the editor slug, not the Android code and not always the Crowdin code: `es-ES` is `es`,
  `pt-PT` is `pt`, `zh-TW` is `zhtw`, `he` is `he`. A wrong slug shows a language picker. The
  language page `https://crowdin.com/project/androidaps/<Crowdin code>` links to the right slug.
- With the browser extension, the first search or typing after a page load is often lost. Check
  with a screenshot and type again.
- Saving adds a new translation next to the old one. Approve the new one - that removes approval
  from the old one. Only approved text is exported.
- After Save the editor moves on to the next search result, so check which string is selected
  before you approve.
- **Crowdin QA can block Save for a correct text**, e.g. the Korean grammar marker `(으)로` is
  reported as "extra symbol". Use "Save Anyway".

## Seeing the aapt warnings about translations

aapt prints "Multiple substitutions specified in non-positional format" only when it really merges
the resources. With the build cache on (`org.gradle.caching=true`), even a build after `clean` takes
the merge from the cache and prints nothing. Force only the merge tasks to run:

```bash
./gradlew.bat :app:mergeFullDebugResources --rerun :wear:mergeFullDebugResources --rerun --no-daemon --console=plain > res.log 2>&1
grep "Multiple substitutions" res.log
```

aapt does not see everything. A translation can use the wrong type (`%1$.0f` for an `Int`), the
wrong number (`%1$d` twice), a broken placeholder (`%2$小時`) or a single `%` in a formatted
string, and the build stays silent. These need a placeholder comparison against English.
`rh.gs()` catches the error and shows English instead; `stringResource()` in Compose crashes.

## Checking a "New Crowdin updates" pull request before merge

No CI runs on the `l10n_dev` pull requests, so nothing else checks them. The script is not in the
repo: first save the code block at the end of this section as `crowdin-pr-check.js` in the session
scratchpad (or `%TEMP%`). Then run it from the repo root, because it calls `git` there, with the
merge base and the fetched branch:

```bash
git fetch origin dev l10n_dev
node <scratchpad>/crowdin-pr-check.js $(git merge-base origin/dev origin/l10n_dev) origin/l10n_dev > check.log
```

It looks only at translations the pull request changes and compares each with English in the same
module: placeholders (index and type), a missing placeholder, a `%` that starts no valid
placeholder in a string that has arguments, more than one unnumbered placeholder, and an
unescaped apostrophe. No API token is needed.

Read every line yourself, many are harmless:

- **A literal `%` in a text without arguments is fine**, like `70%` or `50 %-profil`. The text is
  shown without arguments, so it is never formatted. The script does not know how the code uses a
  string; check with `git grep` when English has no placeholders either.
- **A missing placeholder does not crash**, the value is only not shown (German `a11y_bg_delta`
  was `Delta` without `%1$s`, 2026-10-31). Fix it in Crowdin anyway.
- **"type differs" with `s` in the translation is harmless**: `%1$s` prints a number too. `d` or
  `f` where English has `s` crashes in Compose.

Also check that the English files the pull request changes ("Update source file strings.xml") are
the same as in `dev` (`git diff origin/dev origin/l10n_dev -- '*/values/*.xml'` must be empty),
and that `git merge-tree --write-tree origin/dev origin/l10n_dev` merges cleanly. Crowdin copies
the English sources from `dev`; a difference means `dev` was not fetched or moved on since.

```js
// node crowdin-pr-check.js <base ref> <head ref>
const { execFileSync } = require('child_process')
const [base, head] = process.argv.slice(2)
const git = (...a) => execFileSync('git', a, { encoding: 'utf8', maxBuffer: 1 << 28 })
const show = (ref, p) => { try { return git('show', `${ref}:${p}`) } catch { return null } }

// name -> { text, attrs }; a plural item as name#quantity
function parse(xml) {
  const out = new Map()
  if (!xml) return out
  for (const m of xml.matchAll(/<string\s+([^>]*?)(?:\/>|>([\s\S]*?)<\/string>)/g)) {
    const name = /name="([^"]+)"/.exec(m[1])?.[1]
    if (name) out.set(name, { text: m[2] ?? '', attrs: m[1] })
  }
  for (const m of xml.matchAll(/<plurals\s+([^>]*)>([\s\S]*?)<\/plurals>/g)) {
    const name = /name="([^"]+)"/.exec(m[1])?.[1]
    for (const i of m[2].matchAll(/<item\s+quantity="([^"]+)"\s*>([\s\S]*?)<\/item>/g))
      out.set(`${name}#${i[1]}`, { text: i[2], attrs: m[1], plural: true })
  }
  return out
}

// aapt drops the backslash of an unknown escape, so \%2$d is still a placeholder at run time
const SPEC = /%(?:(\d+)\$)?[-#+0,(<]*\d*(?:\.\d+)?([sdfxXcegob])/g
const LONE = /%(?!(\d+\$)?[-#+0,(<]*\d*(\.\d+)?[sdfxXcegob])/g
const kind = t => ('s'.includes(t) ? 's' : 'dxXo'.includes(t) ? 'd' : 'feg'.includes(t) ? 'f' : t)
const keys = text => [...text.replace(/%%/g, '').matchAll(SPEC)].map(m => `${m[1] ?? '-'}${kind(m[2])}`)

const files = git('diff', '--name-only', base, head).split('\n')
  .filter(f => /\/values-[a-z]{2,3}(-r[A-Z]{2})?\/[^/]+\.xml$/.test(f))
let checked = 0
for (const f of files) {
  const xml = show(head, f)
  if (xml == null) continue
  if (!/<\/resources>\s*$/.test(xml)) console.log(`${f} | - | <resources> not closed`)
  const tr = parse(xml), old = parse(show(base, f)), en = parse(show(head, f.replace(/\/values-[^/]+\//, '/values/')))
  for (const [name, { text }] of tr) {
    if (old.get(name)?.text === text) continue // not changed by this pull request
    checked++
    const say = msg => console.log(`${f} | ${name} | ${msg}: ${text.slice(0, 80)}`)
    const english = en.get(name) || en.get(`${name.split('#')[0]}#other`)
    if (!english) { say('no English string'); continue }
    if (text.replace(/\\./g, '').replace(/"[^"]*"/g, '').includes("'")) say('unescaped apostrophe')
    if (/formatted="false"/.test(english.attrs)) continue
    const e = keys(english.text), t = keys(text)
    if (e.length && (text.replace(/%%/g, '').match(LONE) || []).length) say('% that is no placeholder')
    if (t.filter(k => k.startsWith('-')).length > 1) say('several unnumbered placeholders')
    for (const k of t) if (!e.includes(k))
      say(e.some(x => x.slice(0, -1) === k.slice(0, -1)) ? `type differs: ${k}, English ${e}` : `not in English: ${k}`)
    if (!english.plural) for (const k of e) if (!t.includes(k)) say(`missing placeholder ${k}`)
  }
}
console.log(`${files.length} files, ${checked} changed strings checked`)
```

## Moving a source file

The version that worked, on the stale Omnipod files in 2026-09-18. Each sync came a minute or
two after the push.

1. **Move.** Move the file and change its path in `crowdin.yml`, in one commit. Keep it a pure
   move - nothing left at the old path - so git records a rename and `git log --follow` keeps
   working.
2. **Placeholder.** In a second commit, put a file containing exactly `<resources></resources>` at
   the old path, and list the old path in `crowdin.yml` again. Push both commits. At the sync
   Crowdin empties the old file to 0 strings. Say in the commit message that the entry is
   temporary - not in a comment in `crowdin.yml`, which the integration strips.
3. **Check that no string drops out** of the new file (`crowdin-check.js` in the next section, with
   the new file's id and `res` folder). If any do, restore their approvals with
   `crowdin-check.js --fix` - see below. Do it before the next "New Crowdin updates" PR is merged.
4. **Cleanup.** Once the old file has 0 strings, remove the placeholder and its `crowdin.yml`
   entry, and push. At the sync Crowdin deletes the empty old file.
5. **Check** with the script and no arguments: it must print nothing.

Why each step is there:

- **The new path is a new file** on Crowdin, with a new id. Translation memory refills it within a
  few minutes (18% → 70% → 100%) - **but not all approvals come back.** Where the memory holds more
  than one translation of the same English text (from other files), it can pick one that is not
  approved. `pump/dana/common` lost approval on 22 strings in 8 languages that way, and only
  approved translations are exported. Comments on strings do not carry over.
- **Only changing the path is not enough.** The Dana files were deleted at the sync that took their
  paths out of `crowdin.yml`, but `pump/omnipod-dash` and `pump/omnipod-eros` left it in 2025 and
  their files stayed on Crowdin, unchanged, until 2026-09.
- **The placeholder must be listed, and must be a valid resource file.** The integration reads
  only listed paths, so an unlisted file is never seen. A zero-byte file was ignored even when
  listed - no change after a quarter of an hour.
- The live `omnipod/dash` and `omnipod/eros` files were not affected by emptying and deleting the
  old ones.

If a stale file is found some other time, the same steps 2, 4 and 5 remove it.

## Checking for stale files and strings that would drop out

Save as `crowdin-check.js` next to `crowdin-api.js` and run it with `node` from the repo root. It
prints the Crowdin files that have no `source:` line in `crowdin.yml` (and the other way round).
With `<fileId>:<res dir>` arguments it also prints, per language, the strings that the repo has
translated but Crowdin has not approved - the ones the next export would drop. No output means
everything is fine.

A string whose **English text changed** since the last export is listed too, and that is expected:
Crowdin dropped the approval because the old translation no longer fits, and translators must
redo it. (On 2026-11-19 `plugins/sync` listed such strings in nine languages, after the English
fixes of 2026-11-01.)

With `--fix` it approves the listed strings again: the repo holds the last approved export, so for
each one it looks for a translation on Crowdin with exactly the repo's text and approves it. It
never adds or deletes text. A string it cannot match (the text differs, for example in its escapes)
is printed as `not fixed`; handle those in the web editor.

**Use `--fix` only right after a file move, when the English text did not change.** After an
English change the repo's text is a translation of the *old* English, and `--fix` would approve it
again.

```js
// node crowdin-check.js [--fix] [<fileId>:<res dir> ...]   e.g. 5881:pump/dana/common/src/main/res
const fs = require('fs'), path = require('path')
const { get, post, all, devBranch } = require('./crowdin-api')
const fix = process.argv.includes('--fix')
const args = process.argv.slice(2).filter(a => a !== '--fix')
// name -> text of the <string> entries of a resource file
const texts = xml => new Map([...xml.matchAll(/<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)].map(m => [m[1], m[2]]))

;(async () => {
  const onCrowdin = (await all(`/files?branchId=${await devBranch()}&recursion=1`)).map(f => f.path.replace(/^\/dev/, ''))
  const listed = [...fs.readFileSync('crowdin.yml', 'utf8').matchAll(/^\s*- source:\s*(\S+)/gm)].map(m => m[1])
  onCrowdin.filter(p => !listed.includes(p)).forEach(p => console.log('only on Crowdin:', p))
  listed.filter(p => !onCrowdin.includes(p)).forEach(p => console.log('not on Crowdin yet:', p))

  const languages = (await get('')).data.targetLanguages
  for (const arg of args) {
    const [fileId, dir] = arg.split(':')
    const strings = await all(`/strings?fileId=${fileId}`)
    const idOf = new Map(strings.map(s => [s.identifier, s.id]))
    for (const l of languages) {
      const repoFile = path.join(dir, `values-${l.androidCode}`, 'strings.xml')
      if (!fs.existsSync(repoFile)) continue
      const approvedIds = new Set((await all(`/approvals?fileId=${fileId}&languageId=${l.id}`)).map(a => a.stringId))
      const repo = texts(fs.readFileSync(repoFile, 'utf8'))
      const lost = [...repo.keys()].filter(n => idOf.has(n) && !approvedIds.has(idOf.get(n)))
      if (!lost.length) continue
      console.log(`${dir} ${l.id}: ${lost.join(', ')}`)
      if (!fix) continue
      for (const name of lost) {
        const same = (await all(`/translations?stringId=${idOf.get(name)}&languageId=${l.id}`)).find(t => t.text === repo.get(name))
        if (same) await post('/approvals', { translationId: same.id })
        else console.log(`  not fixed: ${l.id} ${name}`)
      }
    }
  }
})()
```

Then run it again without `--fix`: it must print nothing.
