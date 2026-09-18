# Crowdin CLI Workflow

Working commands for translating AAPS strings via the Crowdin CLI **without touching the
repo's `values-xx/` files** (which would conflict with Crowdin's own sync).

## One-time setup

1. Install Crowdin CLI (Java 17+ required). Default Windows install:
   `C:\Program Files (x86)\CrowdinCLI\crowdin-cli.jar`.
2. Create a Personal Access Token at https://crowdin.com/settings#api-key with scopes
   `Projects` + `Translations`. Token is only shown at creation — copy it then.
3. Store credentials in `~/.crowdin.yml` (NOT in the repo's `crowdin.yml`):
   ```yaml
   api_token: "<token>"
   project_id: "309752"
   base_url: "https://api.crowdin.com"
   ```
   Proper YAML, one key per line — no JSON-style quoted keys on a single line.
4. Invoke the CLI as `crowdin ...`. The installer does put a `crowdin.bat` on PATH — check with
   `where crowdin`, which resolves to `C:\Program Files (x86)\CrowdinCLI\crowdin.bat`. Wrap it as
   `powershell.exe -Command "crowdin ..."` so it starts with an allowed command prefix.
   The `java -jar "C:/Program Files (x86)/CrowdinCLI/crowdin-cli.jar" ...` form below still works and
   is the fallback if `where crowdin` finds nothing.

## Project specifics

- Project id: `309752`, branch: **`dev`** — every command must include `-b dev`.
- Android locale mapping: `%android_code%` resolves to full locale (e.g. `cs` → `cs-rCZ`,
  `pt-BR` → `pt-rBR`), so translations land in `values-cs-rCZ/`, not `values-cs/`.
- Project has **"Export only approved"** enabled. Downloads hide unapproved translations
  even when they exist. To see all (approved + unapproved), query the REST API:
  `GET /api/v2/projects/309752/languages/{lang}/translations?fileId={id}`.
- Sources reach Crowdin through Crowdin's GitHub integration, not through the CLI. It syncs `dev` a
  few minutes after a push and reads **only the paths listed in `crowdin.yml`**. Translations come
  back as "New Crowdin updates" pull requests, which hold the last approved export - so the repo's
  `values-xx/` files are a record of what was approved.
- **The integration owns `crowdin.yml`.** Saving its settings in the Crowdin web UI rewrites the
  file and strips every comment (commit "Update Crowdin configuration file [ci skip]", e.g.
  `2be1bd2831`). Keep notes in commit messages or here, never in `crowdin.yml`.
- Some `values-xx/` folders in the repo are for languages that are not Crowdin target languages
  (in 2026-09: `cy`, `fi`, `sl`, `ta`). `upload translations` skips them without a message.

## Workflow — translate without touching the repo

### 1. Prepare a temp workspace

Create a minimal `crowdin.yml` in a temp directory, listing only the file(s) being worked
on. **`preserve_hierarchy: true` is required** or the CLI looks for files at the wrong
branch-internal path.

```yaml
preserve_hierarchy: true
files:
  - source: /app/src/main/res/values/strings.xml
    translation: /app/src/main/res/values-%android_code%/strings.xml
```

Copy the source file(s) from the repo into the temp dir at the same relative path — the
CLI refuses to work if the source is missing under `--base-path`:

```bash
mkdir -p /tmp/crowdin-work/app/src/main/res/values
cp app/src/main/res/values/strings.xml /tmp/crowdin-work/app/src/main/res/values/
```

### 2. Download current translations (inspect state)

```bash
java -jar "C:/Program Files (x86)/CrowdinCLI/crowdin-cli.jar" download \
  -l cs -b dev \
  --base-path="/tmp/crowdin-work" \
  -c "/tmp/crowdin-work/crowdin.yml" \
  --no-progress --plain
```

Writes to `/tmp/crowdin-work/app/src/main/res/values-cs-rCZ/strings.xml`.

### 3. Edit the translation file

Add new translations, keep existing ones. Keep all source keys present.

### 4. Upload translations

**Unapproved (default — recommended for review):**

```bash
java -jar "C:/Program Files (x86)/CrowdinCLI/crowdin-cli.jar" upload translations \
  -l cs -b dev \
  --base-path="/tmp/crowdin-work" \
  -c "/tmp/crowdin-work/crowdin.yml" \
  --no-progress
```

**Auto-approved (skip review — only when you trust the output):**

```bash
java -jar "C:/Program Files (x86)/CrowdinCLI/crowdin-cli.jar" upload translations \
  -l cs -b dev \
  --base-path="/tmp/crowdin-work" \
  -c "/tmp/crowdin-work/crowdin.yml" \
  --auto-approve-imported \
  --no-progress
```

Note: re-uploading the same text that already exists as unapproved won't retroactively
approve it — Crowdin dedupes. To approve existing unapproved translations, use the REST
API (see below) or the web editor.

### 5. Approve existing unapproved translations via REST API

```bash
# List unapproved translations for a language+file
TOKEN=$(sed -n 's/^api_token: "\(.*\)"/\1/p' ~/.crowdin.yml)
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.crowdin.com/api/v2/projects/309752/languages/cs/translations?fileId=7&limit=50"

# Approve a single translation by id
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"translationId":<id>}' \
  "https://api.crowdin.com/api/v2/projects/309752/approvals"
```

## Common gotchas

- **"Couldn't find any file to download"** — you forgot `-b dev`, or the source file isn't
  present under `--base-path`.
- **"file 'dev\strings.xml' is missing in the project"** on upload — `preserve_hierarchy`
  is missing from the minimal `crowdin.yml`.
- **Using the repo's committed `crowdin.yml` with a temp `--base-path`** fails because it
  lists 40+ files whose sources don't exist in temp. Use a minimal temp yml listing only
  what you're touching.
- **`--dest-folder` does not exist** — use `--base-path` instead (CLI error message
  suggests `--dest`, which is a different option for file rename).
- **`crowdin file list` prints only "Fetching project info"** unless you pass
  `-c <yml> --base-path=<dir>`.

## Moving a source file

What Crowdin does, as seen when the Dana strings moved in 2026-09:

- **The new path is a new file** on Crowdin, with a new id. Translation memory fills it within a
  few minutes (18% → 70% → 100%), approvals included - **but not all of them.** Where the memory
  holds more than one translation of the same English text (from other files), it can pick one
  that is not approved. `pump/dana/common` lost approval on 22 strings in 8 languages that way.
  Only approved translations are exported, so those strings would have dropped out of the app with
  the next "New Crowdin updates" PR. Comments on strings do not carry over.
- **The old file** was deleted at the sync of the push that took its path out of `crowdin.yml`.
  Do not rely on it: `pump/omnipod-dash` and `pump/omnipod-eros` left `crowdin.yml` in 2025 and
  their files stayed on Crowdin, unchanged, until 2026-09.
- **An empty file at the old path works only if it is listed and is a valid resource file.** The
  integration only reads listed paths, so an unlisted file is never seen. Tried on the stale
  `omnipod-dash` and `omnipod-eros` files in 2026-09-18:
  - a zero-byte file, listed: ignored - no change after a quarter of an hour;
  - `<resources></resources>`, listed: the old Crowdin file had 0 strings a minute and a half after
    the push. The live `omnipod/dash` and `omnipod/eros` files were not affected.

Steps:

1. Move the file and change its path in `crowdin.yml`, in one commit. Keep the commit a pure move -
   nothing left at the old path - so git records a rename and `git log --follow` keeps working.
   Push, and wait a few minutes for the sync.
2. **Check that the old file is gone** (the script in the next section, no arguments needed). If
   it is still there, either:
   - put `<resources></resources>` at the old path and list it in `crowdin.yml` for one sync, which
     empties the old file; then take both out again, or
   - delete it: `crowdin file delete <path on Crowdin, without /dev> -b dev`.

   Nothing is lost either way - the new file has its own translations, and translation memory
   keeps the old ones. Check the new file with step 3 afterwards all the same.
3. **Check that no string drops out** (the same script, with the new file's id and `res` folder).
   Do it before the next "New Crowdin updates" PR is merged.
4. If any do, upload the repo's translations of that file with auto-approve, in a temp workspace
   as in the workflow above (a `crowdin.yml` listing only that file, the source and all its
   `values-xx/` folders copied in):

   ```bash
   crowdin upload translations -b dev --base-path=<dir> -c <dir>/crowdin.yml --auto-approve-imported --no-progress --plain
   ```

   The repo holds the last approved export, so this approves exactly what was approved before.
   Strings already approved with the same text are left alone. Run step 3 again - it must print
   nothing.

## Checking for stale files and strings that would drop out

Save as a `.js` file in a scratch directory and run with `node` from the repo root. It prints the
Crowdin files that have no `source:` line in `crowdin.yml` (and the other way round). With
`<fileId>:<res dir>` arguments it also prints, per language, the strings that the repo has
translated but Crowdin has not approved - the ones the next export would drop. No output means
everything is fine.

```js
// node crowdin-check.js [<fileId>:<res dir> ...]   e.g. 5881:pump/dana/common/src/main/res
const fs = require('fs'), os = require('os'), path = require('path')
const token = /api_token:\s*"([^"]+)"/.exec(fs.readFileSync(path.join(os.homedir(), '.crowdin.yml'), 'utf8'))[1]
const base = 'https://api.crowdin.com/api/v2/projects/309752'
const get = async url => (await fetch(url, { headers: { Authorization: `Bearer ${token}` } })).json()
const names = xml => [...xml.matchAll(/<string\s+name="([^"]+)"/g)].map(m => m[1])

;(async () => {
  const onCrowdin = (await get(`${base}/files?branchId=1&recursion=1&limit=500`)).data.map(x => x.data.path.replace(/^\/dev/, ''))
  const listed = [...fs.readFileSync('crowdin.yml', 'utf8').matchAll(/^\s*- source:\s*(\S+)/gm)].map(m => m[1])
  onCrowdin.filter(p => !listed.includes(p)).forEach(p => console.log('only on Crowdin:', p))
  listed.filter(p => !onCrowdin.includes(p)).forEach(p => console.log('not on Crowdin yet:', p))

  const languages = (await get(base)).data.targetLanguages
  for (const arg of process.argv.slice(2)) {
    const [fileId, dir] = arg.split(':')
    const strings = (await get(`${base}/strings?fileId=${fileId}&limit=500`)).data.map(x => x.data)
    const nameOf = new Map(strings.map(s => [s.id, s.identifier]))
    for (const l of languages) {
      const repoFile = path.join(dir, `values-${l.androidCode}`, 'strings.xml')
      if (!fs.existsSync(repoFile)) continue
      const approved = new Set((await get(`${base}/approvals?fileId=${fileId}&languageId=${l.id}&limit=500`)).data.map(x => nameOf.get(x.data.stringId)))
      const lost = names(fs.readFileSync(repoFile, 'utf8')).filter(n => strings.some(s => s.identifier === n) && !approved.has(n))
      if (lost.length) console.log(`${dir} ${l.id}: ${lost.join(', ')}`)
    }
  }
})()
```

It reads 500 strings and 500 approvals per file and language, which covers every file today; a
bigger file needs `offset` paging.

## Finding file IDs in the project

```bash
TOKEN=$(sed -n 's/^api_token: "\(.*\)"/\1/p' ~/.crowdin.yml)
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.crowdin.com/api/v2/projects/309752/files?limit=500" \
  | grep -oE '"id":[0-9]+[^}]*"path":"[^"]*"'
```

Paths in the API include the branch prefix, e.g.
`/dev/app/src/main/res/values/strings.xml`.
