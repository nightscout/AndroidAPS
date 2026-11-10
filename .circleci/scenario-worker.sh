#!/usr/bin/env bash
# Runs items of the pump scenario work list on one emulator until the list is empty.
#
# The pump emulator scenarios (@ShardD) mostly wait: for a driver timeout, a lost answer, a link that does not come.
# So they are spread over every emulator that has nothing else to do. The list is made once by
# `scenario-worker.sh --make-list <emulator>`, and each emulator then takes the next item under a lock. An item is
# one part of one scenario class (the runner's own numShards/shardIndex), or the two app restart runs of a class,
# which need the same emulator one after the other.
#
#   scenario-worker.sh --make-list <emulator>   list the classes and write the work list
#   scenario-worker.sh <emulator>               take items until the list is empty
#
# Each item writes its result to $Q/rc/<item> (0 passed, 1 failed) and one line to $Q/summary. The Collect step
# waits until there is a result for every item. A new pump scenario class needs no change here.
set -u

Q=/tmp/scenarios
PKG=info.nightscout.androidaps
RUNNER=$PKG.test/app.aaps.runners.AapsTestRunner
# Parts per scenario class. More parts, more emulators can share one class.
PARTS=${SCENARIO_PARTS:-2}

if [ "${1:-}" = "--make-list" ]; then
  DEV=$2
  rm -rf "$Q"; mkdir -p "$Q/rc"
  # `-e log true` lists the tests without running them.
  CLASSES=$(adb -s "$DEV" shell am instrument -w -r -e log true -e annotation app.aaps.testcategories.ShardD $RUNNER 2>/dev/null \
    | tr -d '\r' | sed -n 's/^INSTRUMENTATION_STATUS: class=//p' | sort -u)
  [ -n "$CLASSES" ] || { echo "no scenario classes listed"; exit 1; }
  : > "$Q/list"
  # The long items first, so the short ones fill the gaps at the end.
  for c in $CLASSES; do
    for p in $(seq 0 $((PARTS - 1))); do echo "run $c $p" >> "$Q/list"; done
  done
  for c in $CLASSES; do echo "restart $c" >> "$Q/list"; done
  wc -l < "$Q/list" | tr -d ' ' > "$Q/count"
  echo "scenario work list, $(cat "$Q/count") items:"
  cat "$Q/list"
  exit 0
fi

DEV=$1
COVDIR=app/build/outputs/code_coverage/fullDebugAndroidTest/connected; mkdir -p "$COVDIR"
adb -s "$DEV" logcat -G 16M || true

# The next item, taken off the list under the lock. Empty when the list is done.
take() {
  (
    flock 9
    head -n 1 "$Q/list"
    sed -i '1d' "$Q/list"
  ) 9> "$Q/lock"
}

# Waits until the box is calm before a retry, as the app shards do: a retry into the same load spike crashed again.
settle() {
  nc=$(nproc 2>/dev/null || echo 8)
  for _ in $(seq 1 30); do
    la=$(cut -d' ' -f1 /proc/loadavg | cut -d. -f1)
    [ "${la:-99}" -lt "$((nc / 2))" ] && break
    # A line now and then: CircleCI stops a step after 10 minutes with no output.
    echo "$DEV: waiting for the load ($la) to drop before a retry"
    sleep 10
  done
}

# What the driver, the queue and the database did during each failed test of [out], from the logcat of this emulator.
# A scenario failure names only the check that failed; these lines say why.
dump_failed_tests() {
  local out=$1
  for t in $(sed -n 's/^[0-9]*) \([A-Za-z0-9_]*\)(.*/\1/p' "$out" | sort -u); do
    echo "--- what happened in $t ---"
    # Without the radio chatter of each packet (Dana logs four lines a packet), which pushed the command itself out.
    # Only BLEComm's: the history records the driver reads are logged from a processMessage too, and they are the cause.
    adb -s "$DEV" logcat -d -v time \
      | sed -n "/TestRunner.*started: $t(/,/TestRunner.*finished: $t(/p" \
      | grep -E "PUMP|DATABASE|TestRunner|AndroidRuntime| E " \
      | grep -vE "BLEComm.processMessage|no wait needed|notifyAll|handleMessage done|writeCharacteristic|onCharacteristic|>>>>> |<<<<< " \
      | tail -n 400 || true
  done
}

# One part of a scenario class, with the retry and coverage rules of the app shards.
run_part() {
  local cls=$1 part=$2 id=$3 rc=1
  for attempt in 1 2 3; do
    [ "$attempt" -gt 1 ] && settle
    adb -s "$DEV" logcat -c || true
    adb -s "$DEV" shell am instrument -w -r -e class "$cls" -e numShards "$PARTS" -e shardIndex "$part" \
      -e coverage true -e coverageFile /data/data/$PKG/cov-$id.ec \
      $RUNNER > "$Q/out-$id-$attempt.txt" 2>&1 || true
    tail -n 120 "$Q/out-$id-$attempt.txt"
    echo "--- $id attempt $attempt on $DEV: retries and failure dumps from logcat ---"
    adb -s "$DEV" logcat -d -v time | grep -E "RetryRule: |E2E_" || true
    adb -s "$DEV" exec-out run-as $PKG cat cov-$id.ec > "$COVDIR/cov-$id.ec" 2>/dev/null || true
    sz=$(wc -c < "$COVDIR/cov-$id.ec" 2>/dev/null || echo 0)
    echo "$id attempt $attempt coverage: ${sz:-0} bytes"
    if grep -qE "Process crashed|INSTRUMENTATION_FAILED" "$Q/out-$id-$attempt.txt" || [ "${sz:-0}" -lt 50000 ]; then
      echo "$id attempt $attempt: process crash or missing coverage - retry with a fresh process"
      echo "--- $id attempt $attempt: what crashed ---"
      adb -s "$DEV" logcat -d -v time | grep -E -A 40 "FATAL EXCEPTION|ANR in " | head -n 120 || true
      continue
    fi
    if grep -qE "FAILURES!!!" "$Q/out-$id-$attempt.txt"; then
      rc=1
      dump_failed_tests "$Q/out-$id-$attempt.txt"
    else
      rc=0
    fi
    break
  done
  return $rc
}

# The app restart of a class: run 1 ends with its process, run 2 is a new one on the same emulator.
# See .claude/skills/pump-emulator-scenarios/SKILL.md. The restart runs collect no coverage.
run_restart() {
  local cls=$1 id=$2
  for part in 1 2; do
    adb -s "$DEV" logcat -c || true
    adb -s "$DEV" shell am instrument -w -r -e killRestart $part -e class "$cls#appKilledDuringABolus" \
      $RUNNER > "$Q/out-$id-$part.txt" 2>&1 || true
    echo "--- $cls app restart, run $part on $DEV ---"
    tail -n 40 "$Q/out-$id-$part.txt"
    if ! grep -q "^OK (1 test)" "$Q/out-$id-$part.txt"; then
      echo "app restart run $part of $cls FAILED"
      adb -s "$DEV" logcat -d -v time | grep -E "RetryRule: |E2E_|PUMPCOMM|PUMPQUEUE" | tail -n 120 || true
      return 1
    fi
  done
  return 0
}

while :; do
  item=$(take)
  [ -n "$item" ] || break
  set -- $item
  kind=$1; cls=$2; part=${3:-}
  id=$(echo "${cls##*.}-$kind${part:+-$part}" | tr -c 'A-Za-z0-9\n-' '_')
  echo "=== $DEV takes $item ==="
  start=$(date +%s)
  if [ "$kind" = run ]; then run_part "$cls" "$part" "$id"; else run_restart "$cls" "$id"; fi
  rc=$?
  echo $rc > "$Q/rc/$id"
  echo "$id on $DEV: rc=$rc in $(( $(date +%s) - start )) s" | tee -a "$Q/summary"
done
echo "$DEV: scenario work list done"
