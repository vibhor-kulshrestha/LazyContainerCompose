package com.lazycontainer.compose.lazy.container

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Remeasurement
import androidx.compose.ui.layout.RemeasurementModifier
import kotlin.math.abs

/**
 * Scroll + visibility state for [LazyContainerColumn].
 *
 * Mirrors [androidx.compose.foundation.lazy.LazyListState] in spirit: the state
 * holds the index + sub-pixel offset of the first visible item, and the
 * measure policy normalizes these every pass. Scroll deltas arriving from the
 * `scrollable` modifier are accumulated into [scrollToBeConsumed] and folded
 * into the state the next time measure runs (which we force via
 * [Remeasurement.forceRemeasure]).
 */
@Stable
class LazyContainerListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
) : ScrollableState {

    internal var firstVisibleItemIndex by mutableStateOf(initialFirstVisibleItemIndex)
    internal var firstVisibleItemScrollOffset by mutableStateOf(initialFirstVisibleItemScrollOffset)

    /** Fractional + pending scroll delta the measure pass should apply. */
    internal var scrollToBeConsumed: Float = 0f

    /** Updated by the measure policy every pass. */
    internal var mCanScrollForward: Boolean by mutableStateOf(true)
    internal var mCanScrollBackward: Boolean by mutableStateOf(false)

    /**
     * Snapshot-backed layout info for observers (including the background
     * drawer that paints container chrome) — written once per measure pass.
     */
    internal var layoutInfo: LazyContainerLayoutInfo by mutableStateOf(
        LazyContainerLayoutInfo.Empty, policy = NeverEqualPolicy
    )

    private var remeasurement: Remeasurement? = null

    internal val remeasurementModifier: Modifier = object : RemeasurementModifier {
        override fun onRemeasurementAvailable(r: Remeasurement) {
            remeasurement = r
        }
    }

    private val scrollable = ScrollableState { -onScroll(-it) }

    override val isScrollInProgress: Boolean get() = scrollable.isScrollInProgress
    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit
    ) =
        scrollable.scroll(scrollPriority, block)

    override fun dispatchRawDelta(delta: Float): Float = scrollable.dispatchRawDelta(delta)

    /**
     * Convenience: snap to an item (like LazyListState.scrollToItem), without
     * animation. Animated version can be layered on via `animateScrollBy`.
     */
    suspend fun scrollToItem(index: Int, scrollOffset: Int = 0) {
        scroll {
            firstVisibleItemIndex = index
            firstVisibleItemScrollOffset = scrollOffset
            scrollToBeConsumed = 0f
            remeasurement?.forceRemeasure()
        }
    }

    suspend fun animateScrollBy(value: Float) {
        scrollable.scrollBy(value)
    }

    /**
     * Internal convention: positive `distance` = scrolling FORWARD (revealing
     * items with higher index). We accumulate into [scrollToBeConsumed] and
     * force a remeasure; the measure pass folds it into the first-visible
     * offset and writes back any unused residual (e.g. when we hit an edge).
     */
    private fun onScroll(distance: Float): Float {
        if ((distance > 0f && !mCanScrollForward) || (distance < 0f && !mCanScrollBackward)) {
            return 0f
        }
        scrollToBeConsumed += distance
        if (abs(scrollToBeConsumed) > 0.5f) {
            remeasurement?.forceRemeasure()
        }
        return if (abs(scrollToBeConsumed) <= 0.5f) {
            distance
        } else {
            val consumed = distance - scrollToBeConsumed
            scrollToBeConsumed = 0f
            consumed
        }
    }

    companion object {
        val Saver: Saver<LazyContainerListState, *> = listSaver(
            save = { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
            restore = { LazyContainerListState(it[0], it[1]) }
        )
    }
}

@Composable
fun rememberLazyContainerListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyContainerListState = rememberSaveable(saver = LazyContainerListState.Saver) {
    LazyContainerListState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset)
}

/**
 * Layout info published by the measure policy. Consumers (tests, background
 * drawer, paging-style "near end" triggers, etc.) read this from
 * [LazyContainerListState.layoutInfo].
 */
@Immutable
data class LazyContainerLayoutInfo(
    val visibleItems: List<VisibleItem>,
    /** Every section that overlaps the viewport, with pixel bounds for drawing. */
    internal val visibleSections: List<SectionDrawInfo>,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val totalItemCount: Int,
) {
    companion object {
        val Empty = LazyContainerLayoutInfo(
            visibleItems = emptyList(),
            visibleSections = emptyList(),
            viewportWidth = 0,
            viewportHeight = 0,
            totalItemCount = 0,
        )
    }
}

@Immutable
data class VisibleItem(
    val index: Int,
    val key: Any,
    val offsetY: Int,
    val height: Int,
)

@Immutable
data class SectionDrawInfo(
    val sectionIndex: Int,
    val style: ContainerStyle,
    val leftX: Int,
    val rightX: Int,
    val topY: Int,
    val bottomY: Int,
    /** True if the top of the container is the start of the section (top should be rounded). */
    val topRounded: Boolean,
    val bottomRounded: Boolean,
    /** Y positions (in viewport cords) where a divider should be drawn between rows. */
    val dividerYs: IntArray,
)

private object NeverEqualPolicy :
    androidx.compose.runtime.SnapshotMutationPolicy<LazyContainerLayoutInfo> {
    override fun equivalent(a: LazyContainerLayoutInfo, b: LazyContainerLayoutInfo): Boolean = false
}
