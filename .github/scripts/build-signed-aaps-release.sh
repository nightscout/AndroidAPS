#!/usr/bin/env bash
set -Eeuo pipefail

die() {
  echo "ERROR: $*" >&2
  exit 1
}

extract_certificate_sha256() {
  local verification_file="$1"
  awk '
    /certificate SHA-256 digest:/ {
      value = $0
      sub(/^.*certificate SHA-256 digest:[[:space:]]*/, "", value)
      gsub(/[[:space:]:]/, "", value)
      print tolower(value)
      exit
    }
  ' "${verification_file}"
}

required_variables=(
  ANDROID_KEYSTORE_B64
  ANDROID_KEYSTORE_PASSWORD
  ANDROID_KEY_ALIAS
  ANDROID_KEY_PASSWORD
)
for variable in "${required_variables[@]}"; do
  [[ -n "${!variable:-}" ]] || die "Missing GitHub secret ${variable}"
done

signing_dir="${RUNNER_TEMP}/aaps-signing"
output_dir="${RUNNER_TEMP}/aaps-release"
keystore_path="${signing_dir}/aaps-release.jks"
mkdir -p "${signing_dir}" "${output_dir}"
chmod 700 "${signing_dir}"
umask 077

printf '%s' "${ANDROID_KEYSTORE_B64}" | base64 --decode > "${keystore_path}"
chmod 600 "${keystore_path}"

keytool -list \
  -keystore "${keystore_path}" \
  -storepass "${ANDROID_KEYSTORE_PASSWORD}" \
  -alias "${ANDROID_KEY_ALIAS}" >/dev/null

set +e
./gradlew :app:assembleFullRelease \
  --stacktrace \
  -Dorg.gradle.jvmargs="-Xmx8g -XX:+UseParallelGC -Xss1024m" \
  -Dkotlin.daemon.jvm.options="-Xmx2g" \
  -Dkotlin.compiler.execution.strategy="in-process" \
  -Dorg.gradle.daemon=true \
  -Dorg.gradle.workers.max=4 \
  -Dorg.gradle.caching=true \
  "-Pandroid.injected.signing.store.file=${keystore_path}" \
  "-Pandroid.injected.signing.store.password=${ANDROID_KEYSTORE_PASSWORD}" \
  "-Pandroid.injected.signing.key.alias=${ANDROID_KEY_ALIAS}" \
  "-Pandroid.injected.signing.key.password=${ANDROID_KEY_PASSWORD}" \
  2>&1 | tee "${output_dir}/build.log"
gradle_status=${PIPESTATUS[0]}
set -e
[[ ${gradle_status} -eq 0 ]] || exit "${gradle_status}"

mapfile -d '' apks < <(
  find app/build/outputs/apk/full/release \
    -maxdepth 1 \
    -type f \
    -name '*.apk' \
    -print0
)
[[ ${#apks[@]} -eq 1 ]] || die "Expected exactly one full release APK; found ${#apks[@]}"
source_apk="${apks[0]}"

build_tools_dir="$(find "${ANDROID_HOME}/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
[[ -n "${build_tools_dir}" ]] || die "Android build-tools directory not found"
apksigner="${build_tools_dir}/apksigner"
aapt="${build_tools_dir}/aapt"
[[ -x "${apksigner}" ]] || die "apksigner not found at ${apksigner}"
[[ -x "${aapt}" ]] || die "aapt not found at ${aapt}"

verification_file="${output_dir}/signature-verification.txt"
"${apksigner}" verify --verbose --print-certs "${source_apk}" | tee "${verification_file}"

actual_cert="$(extract_certificate_sha256 "${verification_file}")"
expected_cert="$(
  keytool -exportcert \
    -keystore "${keystore_path}" \
    -storepass "${ANDROID_KEYSTORE_PASSWORD}" \
    -alias "${ANDROID_KEY_ALIAS}" \
    | openssl dgst -sha256 \
    | awk '{print tolower($NF)}'
)"
[[ "${actual_cert}" =~ ^[0-9a-f]{64}$ ]] || die "Could not read a valid APK signing-certificate digest"
[[ "${expected_cert}" =~ ^[0-9a-f]{64}$ ]] || die "Could not read a valid keystore certificate digest"
[[ "${actual_cert}" == "${expected_cert}" ]] || die "APK signer does not match configured keystore"

badging="$("${aapt}" dump badging "${source_apk}")"
application_id="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "${badging}" | head -n 1)"
version_name="$(sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p" <<< "${badging}" | head -n 1)"
[[ "${application_id}" == "info.nightscout.androidaps" ]] || die "Unexpected application ID: ${application_id}"
[[ -n "${version_name}" ]] || die "Could not determine APK version name"

safe_version="$(printf '%s' "${version_name}" | tr -cs 'A-Za-z0-9._-' '-' | sed 's/^-*//;s/-*$//')"
source_sha="$(git rev-parse HEAD)"
short_sha="${source_sha:0:7}"
artifact_name="AAPS-BYOESA-${safe_version}-${short_sha}.apk"
cp "${source_apk}" "${output_dir}/${artifact_name}"

apk_sha256="$(sha256sum "${output_dir}/${artifact_name}" | awk '{print $1}')"
python3 - \
  "${output_dir}/build-report.json" \
  "${artifact_name}" \
  "${version_name}" \
  "${application_id}" \
  "${apk_sha256}" \
  "${actual_cert}" \
  "${source_sha}" \
  "${GITHUB_RUN_NUMBER}" \
  "${SOURCE_BRANCH:-dev}" <<'PY'
import json
import sys

(
    report_path,
    apk,
    version,
    application_id,
    apk_sha256,
    cert_sha256,
    git_sha,
    run_number,
    source_branch,
) = sys.argv[1:]
report = {
    "status": "success",
    "source_branch": source_branch,
    "variant": "fullRelease",
    "apk": apk,
    "version_name": version,
    "application_id": application_id,
    "apk_sha256": apk_sha256,
    "signing_certificate_sha256": cert_sha256,
    "git_sha": git_sha,
    "github_run_number": int(run_number),
    "validation": {
        "signature_valid": True,
        "signer_matches_configured_keystore": True,
        "application_id_valid": True,
    },
}
with open(report_path, "w", encoding="utf-8") as stream:
    json.dump(report, stream, indent=2, sort_keys=True)
    stream.write("\n")
PY

(
  cd "${output_dir}"
  sha256sum "${artifact_name}" build-report.json > checksums.txt
)

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "version=${safe_version}" >> "${GITHUB_OUTPUT}"
  echo "artifact_name=${artifact_name}" >> "${GITHUB_OUTPUT}"
fi
