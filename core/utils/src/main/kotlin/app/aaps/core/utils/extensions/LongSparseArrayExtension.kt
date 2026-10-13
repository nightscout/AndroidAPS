package app.aaps.core.utils.extensions

import androidx.collection.LongSparseArray

fun <E> LongSparseArray<E>.put(key: Long, value: E?) = value?.let { put(key, it) }
