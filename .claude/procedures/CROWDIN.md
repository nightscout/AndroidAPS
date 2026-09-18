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

Changing the path in `crowdin.yml` is not enough. A source that just disappears from the repo stays
on Crowdin as a stale copy beside the new one. `pump/omnipod-dash` and `pump/omnipod-eros` sat there
from their 2025 move until 2026-09, when a comparison of Crowdin's file list with `crowdin.yml`
found them.

1. Move the file and change its path in `crowdin.yml`, in one commit. Keep this commit a pure move
   (no file left at the old path), so git records a rename and `git log --follow` keeps working.
2. In a second commit, create an empty file at each old path and list it in a
   `# TEMPORARY` comment at the top of `crowdin.yml`. Do not add the old path back as an entry. Push
   both together - Crowdin syncs `dev` a few minutes after a push. The Dana and Omnipod files in
   2026-09 were zero bytes; the KMP move before them used `<resources></resources>`. Step 4 is
   where you find out whether the one you used worked.
3. On that sync Crowdin sees a source with no strings and drops it. The new path is a new file on
   Crowdin; translation memory fills it again, approvals included - the 2026-09 KMP move came back
   fully translated and approved. Comments on strings do not carry over.
4. When Crowdin no longer shows the old paths, delete the empty files and the comment.

To find stale files, compare the paths from the file list request below (without the `/dev`
prefix) with the `source:` lines in `crowdin.yml`. Every Crowdin file should have a line.

## Finding file IDs in the project

```bash
TOKEN=$(sed -n 's/^api_token: "\(.*\)"/\1/p' ~/.crowdin.yml)
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://api.crowdin.com/api/v2/projects/309752/files?limit=500" \
  | grep -oE '"id":[0-9]+[^}]*"path":"[^"]*"'
```

Paths in the API include the branch prefix, e.g.
`/dev/app/src/main/res/values/strings.xml`.
