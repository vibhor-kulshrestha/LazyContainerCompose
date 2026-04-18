package com.lazycontainer.compose.lazy.container

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScope
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density

/**
 * The custom measure policy that powers [LazyContainerColumn]. Responsibilities:
 *   1. Apply any pending scroll delta accumulated by [LazyContainerListState].
 *   2. Walk backward if the scroll offset went negative, measuring prior items.
 *   3. Walk forward from the first visible item, measuring until the viewport
 *      is full or items run out.
 *   4. "Pull back" at the end of the list so we never show empty space below
 *      when items could fill it.
 *   5. Compute per-section pixel bounds (`SectionDrawInfo`) so the background
 *      drawer can paint each container as ONE continuous shape spanning its
 *      visible items — eliminating the sub-pixel seams that a per-item slice
 *      approach suffers from.
 *   6. Schedule prefetch for the next couple of off-screen items via
 *      [LazyLayoutPrefetchState] so fast scrolling stays jank-free.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class LazyContainerMeasurePolicy(
    private val state: LazyContainerListState,
    private val itemProvider: LazyContainerItemProvider,
    private val prefetchTracker: PrefetchHandleTracker,
    private val contentPaddingTopPx: Int,
    private val contentPaddingBottomPx: Int,
    private val contentPaddingStartPx: Int,
    private val contentPaddingEndPx: Int,
) {
    fun LazyLayoutMeasureScope.measure(constraints: Constraints): MeasureResult {
        val content = itemProvider.intervalContent()
        val itemCount = content.entryCount
        val viewportW = constraints.maxWidth
        val viewportH = constraints.maxHeight
        val contentTop = contentPaddingTopPx
        val contentBottom = viewportH - contentPaddingBottomPx

        if (itemCount == 0) {
            state.firstVisibleItemIndex = 0
            state.firstVisibleItemScrollOffset = 0
            state.scrollToBeConsumed = 0f
            state.mCanScrollForward = false
            state.mCanScrollBackward = false
            state.layoutInfo = LazyContainerLayoutInfo.Empty.copy(
                viewportWidth = viewportW, viewportHeight = viewportH, totalItemCount = 0
            )
            return layout(viewportW, viewportH) {}
        }

        // Inline helpers (closed over Density via the measure scope).
        fun childConstraintsFor(index: Int): Constraints {
            val entry = content.entryAt(index)
            val sectionHPadPx = if (entry.sectionIndex != null) {
                val s = content.sectionForItem(index)!!.style
                s.horizontalPadding.roundToPx() * 2
            } else 0
            val total = sectionHPadPx + contentPaddingStartPx + contentPaddingEndPx
            return Constraints(maxWidth = (viewportW - total).coerceAtLeast(0))
        }

        fun leftXFor(index: Int): Int {
            val entry = content.entryAt(index)
            return if (entry.sectionIndex != null) {
                val s = content.sectionForItem(index)!!.style
                contentPaddingStartPx + s.horizontalPadding.roundToPx()
            } else {
                contentPaddingStartPx
            }
        }

        // ---- Step 1: apply pending scroll ----
        var firstIdx = state.firstVisibleItemIndex.coerceIn(0, itemCount - 1)
        val totalPending = state.firstVisibleItemScrollOffset + state.scrollToBeConsumed
        var firstOff = totalPending.toInt()
        val leftover = totalPending - firstOff
        state.scrollToBeConsumed = leftover

        // ---- Step 2: walk backward while offset is negative ----
        while (firstOff < 0 && firstIdx > 0) {
            firstIdx--
            val prevH = measureHeight(firstIdx, childConstraintsFor(firstIdx))
            firstOff += prevH
        }
        if (firstOff < 0) {
            // At top, clamp; discard the residual we couldn't scroll.
            state.scrollToBeConsumed = 0f
            firstOff = 0
        }

        // ---- Step 3: walk forward, measuring + placing until viewport filled ----
        val placed = ArrayList<MeasuredItem>(16)
        var y = contentTop - firstOff
        var idx = firstIdx
        while (idx < itemCount) {
            val p = measure(idx, childConstraintsFor(idx)).firstOrNull()
            if (p == null) { idx++; continue }
            placed += MeasuredItem(
                index = idx,
                placeable = p,
                key = itemProvider.getKey(idx),
                leftX = leftXFor(idx),
                topY = y,
            )
            y += p.height
            idx++
            if (y >= contentBottom) break
        }

        // ---- Step 4: pull back if we ran out before filling viewport ----
        if (y < contentBottom && (firstIdx > 0 || firstOff > 0)) {
            var need = contentBottom - y
            // reclaim from offset first
            if (firstOff > 0) {
                val take = minOf(firstOff, need)
                firstOff -= take
                for (i in placed.indices) {
                    placed[i] = placed[i].copy(topY = placed[i].topY + take)
                }
                y += take
                need -= take
            }
            // prepend previous items
            while (need > 0 && firstIdx > 0) {
                firstIdx--
                val p = measure(firstIdx, childConstraintsFor(firstIdx)).firstOrNull() ?: continue
                val h = p.height
                for (i in placed.indices) {
                    placed[i] = placed[i].copy(topY = placed[i].topY + h)
                }
                val newTop = (placed.firstOrNull()?.topY ?: (contentTop + h)) - h
                placed.add(
                    0,
                    MeasuredItem(
                        index = firstIdx,
                        placeable = p,
                        key = itemProvider.getKey(firstIdx),
                        leftX = leftXFor(firstIdx),
                        topY = newTop,
                    )
                )
                y += h
                need -= h
            }
            if (y < contentBottom && firstIdx == 0) {
                firstOff = 0
                val currentFirstTop = placed.firstOrNull()?.topY ?: contentTop
                val shift = contentTop - currentFirstTop
                if (shift != 0) {
                    for (i in placed.indices) {
                        placed[i] = placed[i].copy(topY = placed[i].topY + shift)
                    }
                }
            }
        }

        // ---- Step 5: write state ----
        state.firstVisibleItemIndex = firstIdx
        state.firstVisibleItemScrollOffset = firstOff
        state.mCanScrollForward = idx < itemCount ||
            (placed.lastOrNull()?.let { it.topY + it.placeable.height > contentBottom } == true)
        state.mCanScrollBackward = firstIdx > 0 || firstOff > 0

        val sectionDraw = buildSectionDrawInfo(content, placed, viewportW, this)

        state.layoutInfo = LazyContainerLayoutInfo(
            visibleItems = placed.map {
                VisibleItem(
                    index = it.index,
                    key = it.key,
                    offsetY = it.topY,
                    height = it.placeable.height,
                )
            },
            visibleSections = sectionDraw,
            viewportWidth = viewportW,
            viewportHeight = viewportH,
            totalItemCount = itemCount,
        )

        // ---- Step 6: prefetch ----
        prefetchTracker.updatePrefetch(
            forwardIndex = idx,
            itemCount = itemCount,
            constraintsFor = { i -> childConstraintsFor(i) }
        )

        return layout(viewportW, viewportH) {
            placed.forEach { it.placeable.placeRelative(it.leftX, it.topY) }
        }
    }

    private fun LazyLayoutMeasureScope.measureHeight(
        index: Int,
        constraints: Constraints,
    ): Int = measure(index, constraints).firstOrNull()?.height ?: 0

    private fun buildSectionDrawInfo(
        content: LazyContainerIntervalContent,
        placed: List<MeasuredItem>,
        viewportW: Int,
        density: Density,
    ): List<SectionDrawInfo> {
        if (content.sections.isEmpty() || placed.isEmpty()) return emptyList()
        val out = ArrayList<SectionDrawInfo>(content.sections.size)
        content.sections.forEach { section ->
            val inSection = placed.filter { it.index in section.startItemIndex..section.endItemIndex }
            if (inSection.isEmpty()) return@forEach
            val first = inSection.first()
            val last = inSection.last()
            val topClipped = first.index != section.startItemIndex
            val bottomClipped = last.index != section.endItemIndex
            val style = section.style
            val padTop = with(density) { style.verticalPaddingTop.roundToPx() }
            val padBottom = with(density) { style.verticalPaddingBottom.roundToPx() }
            val hPad = with(density) { style.horizontalPadding.roundToPx() }
            val dividerYs =
                if (style.drawDividers && inSection.size > 1) {
                    IntArray(inSection.size - 1) { i -> inSection[i + 1].topY }
                } else IntArray(0)
            out += SectionDrawInfo(
                sectionIndex = section.sectionIndex,
                style = style,
                leftX = contentPaddingStartPx + hPad,
                rightX = viewportW - contentPaddingEndPx - hPad,
                topY = first.topY - padTop,
                bottomY = last.topY + last.placeable.height + padBottom,
                topRounded = !topClipped,
                bottomRounded = !bottomClipped,
                dividerYs = dividerYs,
            )
        }
        return out
    }
}

internal data class MeasuredItem(
    val index: Int,
    val placeable: Placeable,
    val key: Any,
    val leftX: Int,
    val topY: Int,
)

/**
 * Keeps track of the prefetch handles currently scheduled. Each measure pass
 * tells it which items are JUST off-screen; we cancel handles outside that
 * window and schedule any new ones. Using [LazyLayoutPrefetchState] means the
 * sub-composition + measurement for those items happens on idle frames, so a
 * fling doesn't stall when a new item scrolls into view.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class PrefetchHandleTracker(
    private val prefetchState: LazyLayoutPrefetchState,
    private val prefetchAhead: Int = 2,
) {
    private val active = HashMap<Int, LazyLayoutPrefetchState.PrefetchHandle>(prefetchAhead * 2)

    fun updatePrefetch(
        forwardIndex: Int,
        itemCount: Int,
        constraintsFor: (Int) -> Constraints,
    ) {
        val targets = HashSet<Int>(prefetchAhead)
        for (offset in 0 until prefetchAhead) {
            val i = forwardIndex + offset
            if (i in 0 until itemCount) targets += i
        }
        val it = active.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in targets) {
                e.value.cancel()
                it.remove()
            }
        }
        targets.forEach { idx ->
            if (idx !in active) {
                active[idx] = prefetchState.schedulePrefetch(idx, constraintsFor(idx))
            }
        }
    }

    fun cancelAll() {
        active.values.forEach { it.cancel() }
        active.clear()
    }
}
