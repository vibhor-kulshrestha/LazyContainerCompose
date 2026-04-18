package com.lazycontainer.compose.lazy.container

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Proves that `PrefetchHandleTracker.updatePrefetch` actually schedules
 * sub-compositions for items just past the visible window, and cancels them
 * when the window shifts.
 *
 * We can't directly observe `schedulePrefetch(...)` call arguments without
 * modifying production code, so we infer them from the composition side-effect:
 * a prefetched item runs its `DisposableEffect`, which the counter records.
 * The set difference `activeIndices - visibleIndices` therefore equals the set
 * of currently-prefetched rows.
 */
class LazyContainerPrefetchTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** T3 — after first layout, exactly the next ≤2 off-screen items are composed. */
    @Test
    fun prefetchComposesNextOffScreenIndices() {
        val state = LazyContainerListState()
        val counter = TestCompositionCounter()

        composeRule.setContent {
            FixedViewport {
                LazyContainerColumn(
                    modifier = Modifier.size(width = 400.dp, height = 800.dp),
                    state = state,
                ) {
                    items(
                        count = 1000,
                        key = { i -> "row-$i" },
                        contentType = { "row" },
                    ) { i -> FixedHeightRow(counter, i) }
                }
            }
        }
        composeRule.waitForIdle()
        // Prefetch fires on idle frames; give it a moment to settle.
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        val visibleIndices = state.layoutInfo.visibleItems.map { it.index }.toSet()
        val lastVisible = visibleIndices.max()
        val offScreen = counter.activeIndices - visibleIndices

        assertTrue("offScreen=$offScreen should have ≤ 2 entries", offScreen.size <= 2)
        val expected = setOf(lastVisible + 1, lastVisible + 2)
        assertTrue(
            "offScreen=$offScreen should ⊆ $expected",
            offScreen.all { it in expected },
        )
    }

    /** T4 — scrolling away cancels previously-prefetched handles. */
    @Test
    fun prefetchedItemsReleaseOnScrollAway() {
        val state = LazyContainerListState()
        val counter = TestCompositionCounter()

        composeRule.setContent {
            FixedViewport {
                LazyContainerColumn(
                    modifier = Modifier.size(width = 400.dp, height = 800.dp),
                    state = state,
                ) {
                    items(
                        count = 1000,
                        key = { i -> "row-$i" },
                        contentType = { "row" },
                    ) { i -> FixedHeightRow(counter, i) }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        val initialVisible = state.layoutInfo.visibleItems.map { it.index }.toSet()
        val initialOffScreen = counter.activeIndices - initialVisible
        // Precondition: there IS something prefetched, otherwise the test is vacuous.
        assertTrue(
            "precondition: initial prefetch window should be non-empty, got $initialOffScreen",
            initialOffScreen.isNotEmpty(),
        )

        composeRule.runOnIdle { runBlocking { state.scrollToItem(100) } }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        val nowActive = counter.activeIndices
        val stillActive = initialOffScreen.intersect(nowActive)
        assertTrue(
            "old prefetched indices $initialOffScreen should all be released after scroll, still active: $stillActive",
            stillActive.isEmpty(),
        )
    }
}
