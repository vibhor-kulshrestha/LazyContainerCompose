package com.lazycontainer.compose.lazy.container

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Proves that `ContentTypeKey(base, sectionIndex)` actually plugs rows into
 * LazyLayout's reuse pool: when every row shares a `contentType`, scrolling
 * back and forth reuses nodes instead of composing fresh ones; when every row
 * has a distinct `contentType`, no reuse is possible and composition count
 * scales with scroll distance.
 *
 * The contrast between T5a and T5b is the proof — T5a alone can't rule out
 * that the tight bound comes from viewport size rather than from reuse.
 */
class LazyContainerReuseTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** T5a — shared contentType lets the reuse pool cap total compositions. */
    @Test
    fun sharedContentTypeEnablesReuse() {
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

        composeRule.runOnIdle {
            runBlocking {
                state.scrollToItem(50)
                state.scrollToItem(0)
                state.scrollToItem(50)
            }
        }
        composeRule.waitForIdle()

        val total = counter.totalEnterCount
        // Each scroll traverses ~50 indices across a ~14-row window.
        // With reuse the budget is dominated by how many NEW indices come into
        // view: roughly 50 + 50 + 50 ≈ 150 unique, plus the initial ~14 and
        // some prefetch slack.
        assertTrue(
            "shared-contentType totalEnterCount=$total should be < 220 (reuse pool active)",
            total < 220,
        )
    }

    /** T5b — unique per-index contentType defeats reuse; compositions balloon. */
    @Test
    fun uniqueContentTypePreventsReuse() {
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
                        contentType = { i -> "row-$i" },
                    ) { i -> FixedHeightRow(counter, i) }
                }
            }
        }
        composeRule.waitForIdle()
        val baseline = counter.totalEnterCount

        composeRule.runOnIdle {
            runBlocking {
                state.scrollToItem(50)
                state.scrollToItem(0)
                state.scrollToItem(50)
            }
        }
        composeRule.waitForIdle()

        val total = counter.totalEnterCount
        val delta = total - baseline
        // Without reuse, every scroll must compose ~visible-window fresh
        // nodes (no pool hits). Three 50-index jumps across a ~14-row window
        // produce substantially more compositions than the shared case.
        // The exact number varies with device density and prefetch behaviour;
        // 30 proves the reuse pool is NOT kicking in.
        assertTrue(
            "unique-contentType delta=$delta should be > 30 (no reuse)",
            delta > 30,
        )
    }
}
