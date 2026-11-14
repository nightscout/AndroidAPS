# Material and Sport custom watchfaces for AndroidAPS 4

Material 1.11 and Sport 1.14 are maintained in [BernhardRo/AndroidAPS, branch `dev`](https://github.com/BernhardRo/AndroidAPS/tree/dev). Integrated from `AAPS-Material-Sport-Watchface-Patch-2026-11-07.zip` on 2026-11-08.

- [Material source and usage](material/README.md)
- [Sport source and usage](sport/README.md)
- Importable assets: `plugins/configuration/src/androidMain/assets/AAPS Material.zip` and `AAPS Sport.zip`.

The archive's 31 project files were imported. Asset paths, Compose icon references and contract-test paths were adapted to this checkout's Android/common source sets. The matching-divider contract test follows the current renderer's additional preference-label argument while retaining its false default. The archive's workspace-specific `AGENTS.md` and `Project.md` are reference documents and were not installed as project instructions.

Both assets were rebuilt with the pinned Python dependencies. Their ZIP compression differs from the archive, but every contained file is byte-identical. The existing AAPS V2 asset and native renderer are unchanged. These are CWF packages, not standalone WFF applications.

## Local verification

Use an isolated environment from the repository root (PowerShell):

```powershell
python -m venv build/watchface-venv
./build/watchface-venv/Scripts/python.exe -m pip install -r wear/watchfaces/sport/requirements.txt
./build/watchface-venv/Scripts/python.exe wear/watchfaces/material/build_watchface.py --check
./build/watchface-venv/Scripts/python.exe wear/watchfaces/sport/build_watchface.py --check
./build/watchface-venv/Scripts/python.exe -m unittest discover -s wear/watchfaces/material -p "test_*.py" -v
./build/watchface-venv/Scripts/python.exe -m unittest discover -s wear/watchfaces/sport -p "test_*.py" -v
```

On integration: Material's 26 tests and Sport's 45 tests passed; both builder consistency checks and `:plugins:configuration:mergeAndroidMainAssets` succeeded. ZIP CRCs and contained-file equality against the original package were checked. The per-face `TESTRESULTS.md` files are historical records from the source checkout. No APK installation or actual-watch verification was performed for this integration.

AAPS automatically loads bundled CWF assets only when its existing import list is empty. For an existing populated list, copy the two ZIPs into the selected AAPS `exports` folder before importing; see the face-specific instructions.
