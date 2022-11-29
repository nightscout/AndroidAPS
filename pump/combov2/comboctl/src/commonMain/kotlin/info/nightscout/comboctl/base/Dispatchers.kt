package info.nightscout.comboctl.base

import kotlinx.coroutines.Dispatchers

// Dispatcher that enforces sequential execution of coroutines,
// thus disallowing parallelism. This is important, since parallel
// IO is not supported by the Combo and only causes IO errors.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal val sequencedDispatcher = Dispatchers.Default.limitedParallelism(1)
