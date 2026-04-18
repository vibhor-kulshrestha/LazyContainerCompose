package com.lazycontainer.compose.lazy.container

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Tracks which row indices are currently composed and how many times rows have
 * entered composition in total. Used by the instrumented tests to verify:
 *   - virtualisation (only a small window of `activeIndices` at a time)
 *   - prefetch window (indices in `activeIndices` not in `visibleItems`)
 *   - contentType reuse pool (bounded `totalEnterCount` vs scroll distance)
 */
internal class TestCompositionCounter {
    private val lock = Any()
    private val _activeIndices = LinkedHashSet<Int>()
    private var _totalEnterCount = 0

    val activeIndices: Set<Int>
        get() = synchronized(lock) { LinkedHashSet(_activeIndices) }

    val totalEnterCount: Int
        get() = synchronized(lock) { _totalEnterCount }

    fun reset() = synchronized(lock) {
        _activeIndices.clear()
        _totalEnterCount = 0
    }

    fun onEnter(index: Int) = synchronized(lock) {
        _activeIndices += index
        _totalEnterCount++
    }

    fun onLeave(index: Int) = synchronized(lock) {
        _activeIndices -= index
    }
}

/**
 * Registers the composable's entry/exit with the given counter. The `index`
 * key ensures that when LazyLayout reuses a node to render a different index,
 * we record a fresh enter/leave cycle — which is exactly what a reuse-pool
 * swap looks like from the composition side.
 */
@Composable
internal fun TrackedRow(
    counter: TestCompositionCounter,
    index: Int,
    content: @Composable () -> Unit,
) {
    DisposableEffect(index) {
        counter.onEnter(index)
        onDispose { counter.onLeave(index) }
    }
    content()
}
