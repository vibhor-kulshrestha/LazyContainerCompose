package com.lazycontainer.compose.lazy.container

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Proves that `LazyContainerColumn` only composes items inside the viewport
 * (plus a tiny prefetch window), even when the total item count is very large
 * and when the items live inside a `containerSection`.
 */
class LazyContainerVirtualisationTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** T1 — plain `items(1000)` outside any section. */
    @Test
    fun onlyVisibleItemsAreComposed() {
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

        val visible = state.layoutInfo.visibleItems.size
        // 800dp / 64dp = 12.5 → 12 or 13 fully visible + 1 partial.
        assertTrue("visible=$visible should be in 10..16", visible in 10..16)
        // active = visible + up to 2 prefetched.
        val active = counter.activeIndices.size
        assertTrue("active=$active should be in 10..18", active in 10..18)
        assertEquals(1000, state.layoutInfo.totalItemCount)
    }

    /** T2 — scroll into the middle; window advances, bound still holds. */
    @Test
    fun visibleIndexWindowAdvancesOnScroll() {
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

        composeRule.runOnIdle { runBlocking { state.scrollToItem(500) } }
        composeRule.waitForIdle()

        val first = state.layoutInfo.visibleItems.first().index
        val last = state.layoutInfo.visibleItems.last().index
        assertTrue("first=$first should be in 498..502", first in 498..502)
        assertTrue("last=$last should be < 520", last < 520)

        val active = counter.activeIndices.size
        assertTrue("active=$active should be ≤ 18", active <= 18)
    }

    /** T6 — same virtualisation guarantees when all items live in a section. */
    @Test
    fun containerSectionRowsStayIndependentlyVirtualised() {
        val state = LazyContainerListState()
        val counter = TestCompositionCounter()

        composeRule.setContent {
            FixedViewport {
                LazyContainerColumn(
                    modifier = Modifier.size(width = 400.dp, height = 800.dp),
                    state = state,
                ) {
                    containerSection(sectionKey = "all") {
                        items(
                            count = 1000,
                            key = { i -> "sec-row-$i" },
                            contentType = { "row" },
                        ) { i -> FixedHeightRow(counter, i) }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val visible = state.layoutInfo.visibleItems.size
        assertTrue("visible=$visible should be in 10..16", visible in 10..16)
        val active = counter.activeIndices.size
        assertTrue("active=$active should be in 10..18", active in 10..18)
        assertEquals(1000, state.layoutInfo.totalItemCount)
    }
}

@Composable
internal fun FixedViewport(content: @Composable () -> Unit) {
    Box(modifier = Modifier.size(width = 400.dp, height = 800.dp)) { content() }
}

@Composable
internal fun FixedHeightRow(counter: TestCompositionCounter, index: Int) {
    TrackedRow(counter, index) {
        Box(modifier = Modifier.height(64.dp)) { Text("row $index") }
    }
}
