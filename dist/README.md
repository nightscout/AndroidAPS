# Archived build artifacts

## AAPS-3.4.2.6-eternal-unsigned.apk

- Built 2026-08-28 from commit `fc1ff2f4` (eternal-build changes), variant `fullRelease`.
- SHA-256: `2bfee00b2121a61687b0ea150a88b6c3c9d13ddf6d3420245b2bf5d563d2659e`
- **UNSIGNED — it will not install as-is.** Sign it with the owner's keystore first
  (see `docs/BUILD_ENV_NOTES.md`):

  ```bash
  apksigner sign --ks aaps-eternal.jks --ks-key-alias aaps \
    --out AAPS-3.4.2.6-eternal-signed.apk AAPS-3.4.2.6-eternal-unsigned.apk
  apksigner verify --print-certs AAPS-3.4.2.6-eternal-signed.apk
  ```

- Verify the download before signing: `sha256sum AAPS-3.4.2.6-eternal-unsigned.apk` must match
  the hash above.
- This copy exists so the eternal build survives even if the build machine is lost. A locally
  built APK from the same commit is equally valid (byte-for-byte identity is not guaranteed
  between machines, functional identity is).
