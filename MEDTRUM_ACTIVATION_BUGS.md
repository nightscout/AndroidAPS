# Medtrum Patch Activation Bugs (2026-04-08)

User report: Activation requires multiple AAPS restarts. Filling progress stalls, NEXT button stays grayed out, priming shows loading but no progress. Samsung S22 Ultra, Android 16, AAPS v4.

## Bug 1: Race condition - two concurrent startPrime() calls

**File:** `pump/medtrum/src/main/kotlin/app/aaps/pump/medtrum/compose/MedtrumPatchViewModel.kt:372-384`

`startPrime()` uses `scope.launch` with no guard against concurrent invocation. Logs show two coroutines entering `sendPacketAndGetResponse` simultaneously:

```
12:44.495 [worker-6]  CommandState.onEnter()          -- first call gets through
12:44.495 [worker-14] Send packet attempt: CommandState -- second call fails immediately  
12:44.495 [worker-14] startPrime: failure! -> setupStep: ERROR
12:46.239 [worker-6]  startPrime: success!  (but ERROR already set by worker-14)
12:46.756 pump notification: PRIMING -> overwrites ERROR with PRIMING
```

The failing second call sets `setupStep = ERROR` even when the first call succeeds. Brief ERROR flash in UI.

**Current code:**
```kotlin
fun startPrime() {
    scope.launch {
        if (medtrumPump.pumpState == MedtrumPumpState.PRIMING) {
            aapsLogger.info(LTag.PUMP, "startPrime: already priming!")
        } else {
            if (medtrumService?.startPrime() == true) {
                aapsLogger.info(LTag.PUMP, "startPrime: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "startPrime: failure!")
                updateSetupStep(SetupStep.ERROR)
            }
        }
    }
}
```

**Suggested fix - add guard flag:**
```kotlin
private var isPriming = false

fun startPrime() {
    scope.launch {
        if (isPriming) return@launch
        isPriming = true
        try {
            if (medtrumPump.pumpState == MedtrumPumpState.PRIMING) {
                aapsLogger.info(LTag.PUMP, "startPrime: already priming!")
            } else {
                if (medtrumService?.startPrime() == true) {
                    aapsLogger.info(LTag.PUMP, "startPrime: success!")
                } else {
                    aapsLogger.info(LTag.PUMP, "startPrime: failure!")
                    updateSetupStep(SetupStep.ERROR)
                }
            }
        } finally {
            isPriming = false
        }
    }
}
```

Same pattern should be applied to `startActivate()` at line 387.

---

## Bug 2: PrimeStep retry doesn't re-trigger startPrime (MAIN USER-FACING BUG)

**File:** `pump/medtrum/src/main/kotlin/app/aaps/pump/medtrum/compose/steps/PrimeStep.kt:65-68`

Retry handler:
```kotlin
onRetry = {
    viewModel.updateSetupStep(MedtrumPatchViewModel.SetupStep.FILLED)
    viewModel.moveStep(PatchStep.PRIMING)  // patchStep is ALREADY PRIMING!
},
```

The trigger for startPrime is `LaunchedEffect(patchStep)` at line 40-44:
```kotlin
LaunchedEffect(patchStep) {
    if (patchStep == PatchStep.PRIMING) {
        viewModel.startPrime()
    }
}
```

Since `_patchStep` MutableStateFlow already holds `PRIMING`, setting it again does NOT re-emit. LaunchedEffect key doesn't change, so it won't re-fire. `startPrime()` is never called.

Result: User sees perpetual loading spinner with no prime command sent. Matches report: *"priming status screen showed, but no progress, and patch was not priming"*

**Log evidence:**
```
07:34.502 setupStep: ERROR -> FILLED  (retry clicked)
07:34.503 moveStep: PRIMING -> PRIMING  (StateFlow no-op!)
         ... no startPrime call follows ...
```

**Suggested fix - call startPrime directly from retry:**
```kotlin
onRetry = {
    viewModel.updateSetupStep(MedtrumPatchViewModel.SetupStep.FILLED)
    viewModel.startPrime()
},
```

---

## Bug 3: sendPacketAndGetResponse fails instantly when service busy

**File:** `pump/medtrum/src/main/kotlin/app/aaps/pump/medtrum/services/MedtrumService.kt:867-879`

```kotlin
private fun sendPacketAndGetResponse(packet: MedtrumPacket, ...): Boolean {
    var result = false
    if (currentState is ReadyState) {
        toState(CommandState())
        mPacket = packet
        mPacket?.getRequest()?.let { bleComm.sendMessage(it) }
        result = currentState.waitForResponse(timeout)
        SystemClock.sleep(100)
    } else {
        aapsLogger.error(LTag.PUMPCOMM, "Send packet attempt when in state: $currentState")
    }
    return result  // false if not ReadyState - no wait, no retry
}
```

After BLE connect, AAPS command queue may run readPumpStatus/loadEvents which hold the service in `CommandState`. The prime command fails instantly.

**Log evidence:**
```
07:25.199 ReadyState reached (connection established)
07:32.095 CommandState entered (readPumpStatus or race with first startPrime)
07:32.096 startPrime: "Send packet attempt when in state: CommandState" -> FAIL

08:46.377 startPrime: "Send packet attempt when in state: IdleState" -> FAIL (disconnected)
```

**Suggested fix - add retry with delay in ViewModel's startPrime:**
```kotlin
fun startPrime() {
    scope.launch {
        if (isPriming) return@launch
        isPriming = true
        try {
            if (medtrumPump.pumpState == MedtrumPumpState.PRIMING) {
                aapsLogger.info(LTag.PUMP, "startPrime: already priming!")
                return@launch
            }
            var retries = 0
            var result = false
            while (retries < 3 && !result) {
                if (medtrumService?.isConnected == true) {
                    result = medtrumService?.startPrime() == true
                }
                if (!result) {
                    retries++
                    if (retries < 3) {
                        aapsLogger.info(LTag.PUMP, "startPrime: retry $retries after delay")
                        delay(3000)
                    }
                }
            }
            if (result) {
                aapsLogger.info(LTag.PUMP, "startPrime: success!")
            } else {
                aapsLogger.info(LTag.PUMP, "startPrime: failure after $retries retries")
                updateSetupStep(SetupStep.ERROR)
            }
        } finally {
            isPriming = false
        }
    }
}
```

---

## How the bugs interact (failure cascade)

1. User presses Next to prime
2. **Bug 1**: Two concurrent startPrime calls race. One enters CommandState, the other fails, setting ERROR
3. If the first call also fails (BLE write error or Bug 3), user sees ERROR with Retry button
4. User presses Retry
5. **Bug 2**: moveStep(PRIMING) is no-op (already PRIMING), LaunchedEffect doesn't fire, startPrime never called
6. User sees perpetual loading spinner with no progress
7. User restarts AAPS, reconnects, tries again
8. **Bug 3**: After reconnect, command queue runs readStatus, service in CommandState, startPrime fails again
9. Cycle repeats until lucky timing where service is in ReadyState AND no race condition

## Files to modify

1. `pump/medtrum/src/main/kotlin/app/aaps/pump/medtrum/compose/MedtrumPatchViewModel.kt`
   - Add `isPriming` guard flag (Bug 1)
   - Add retry loop with delay in `startPrime()` (Bug 3)
   - Apply same pattern to `startActivate()`

2. `pump/medtrum/src/main/kotlin/app/aaps/pump/medtrum/compose/steps/PrimeStep.kt`
   - Fix retry to call `viewModel.startPrime()` directly (Bug 2)
