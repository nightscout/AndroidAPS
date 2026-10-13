package app.aaps.utils

import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import leakcanary.EventListener
import leakcanary.EventListener.Event.HeapAnalysisDone
import shark.HeapAnalysisSuccess
import shark.Leak
import shark.LeakTrace

/**
 * LeakCanary EventListener that uploads memory leaks to Firebase Crashlytics.
 *
 * This service intercepts heap analysis results and converts leak traces to Throwable objects
 * that can be recorded by Firebase Crashlytics. Each leak trace line is converted to a
 * StackTraceElement to avoid truncation in Firebase.
 *
 * Based on: https://blog.ah.technology/upload-leakcanary-memory-leaks-to-firebase-f79c31615d76
 */
class LeakUploadService(
    private val fabricPrivacy: FabricPrivacy
) : EventListener {

    override fun onEvent(event: EventListener.Event) {
        if (event is HeapAnalysisDone<*>) {
            val heapAnalysis = event.heapAnalysis
            if (heapAnalysis is HeapAnalysisSuccess) {
                val allLeaks = heapAnalysis.allLeaks.toList()

                allLeaks.forEach { leak ->
                    leak.leakTraces.forEach { leakTrace ->
                        val throwable = leak.toThrowable(leakTrace)
                        fabricPrivacy.logException(throwable)
                    }
                }
            }
        }
    }
}

/**
 * Extension function to convert a Leak to a Throwable for Firebase Crashlytics.
 *
 * The leak trace is converted to StackTraceElements to preserve the full trace
 * without truncation in Firebase.
 */
private fun Leak.toThrowable(leakTrace: LeakTrace): Throwable {
    val exception = MemoryLeakException(shortDescription)
    val stackTraceElements = mutableListOf<StackTraceElement>()

    // Add header element using declaringClass for the title in Firebase
    stackTraceElements.add(
        StackTraceElement(
            shortDescription,  // declaringClass - shown as title in Firebase
            "",                // methodName
            null,              // fileName
            0                  // lineNumber - 0 means it won't be shown
        )
    )

    // Convert each line of the leak trace to a StackTraceElement
    // Using methodName property as it provides the cleanest output in Firebase
    leakTrace.toString().lines().forEach { line ->
        if (line.isNotBlank()) {
            stackTraceElements.add(
                StackTraceElement(
                    "",       // declaringClass
                    line,     // methodName - the actual leak trace line
                    null,     // fileName
                    0         // lineNumber
                )
            )
        }
    }

    exception.stackTrace = stackTraceElements.toTypedArray()
    return exception
}

/**
 * Custom exception class for memory leaks.
 * This allows Firebase to properly group and display memory leak reports.
 */
class MemoryLeakException(message: String) : Exception(message)
