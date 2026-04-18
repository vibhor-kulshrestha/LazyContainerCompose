package com.lazycontainer.compose.lazy.container

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A LazyColumn-like vertical list that natively supports visually-grouped
 * "container" sections.
 *
 * Built directly on `LazyLayout` + `LazyLayoutItemProvider` +
 * `LazyLayoutPrefetchState` — the same primitives that back `LazyColumn` /
 * `LazyRow` — so every optimisation stays:
 *   - only the currently-visible items are measured/composed
 *   - items are pooled and reused by `contentType`
 *   - a small prefetch budget keeps fast scrolls jank-free
 *   - scroll state survives configuration changes via rememberSaveable
 *
 * The container chrome (rounded corners, border, inter-row dividers) for each
 * `containerSection` is drawn as ONE continuous shape at the layout level,
 * spanning all its visible items — not as per-item slices — so there are no
 * sub-pixel seams between rows regardless of display density.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyContainerColumn(
    modifier: Modifier = Modifier,
    state: LazyContainerListState = rememberLazyContainerListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    userScrollEnabled: Boolean = true,
    content: LazyContainerScope.() -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val latestContent by rememberUpdatedState(content)
    val itemProvider = remember {
        LazyContainerItemProvider { latestContent }
    }
    val prefetchState = remember { LazyLayoutPrefetchState() }
    val prefetchTracker = remember(prefetchState) { PrefetchHandleTracker(prefetchState) }
    DisposableEffect(prefetchTracker) {
        onDispose { prefetchTracker.cancelAll() }
    }

    val topPadPx = with(density) { contentPadding.calculateTopPadding().roundToPx() }
    val bottomPadPx = with(density) { contentPadding.calculateBottomPadding().roundToPx() }
    val startPadPx = with(density) { contentPadding.calculateStartPadding(layoutDirection).roundToPx() }
    val endPadPx = with(density) { contentPadding.calculateEndPadding(layoutDirection).roundToPx() }

    val policy = remember(
        state, itemProvider, prefetchTracker,
        topPadPx, bottomPadPx, startPadPx, endPadPx,
    ) {
        LazyContainerMeasurePolicy(
            state = state,
            itemProvider = itemProvider,
            prefetchTracker = prefetchTracker,
            contentPaddingTopPx = topPadPx,
            contentPaddingBottomPx = bottomPadPx,
            contentPaddingStartPx = startPadPx,
            contentPaddingEndPx = endPadPx,
        )
    }

    val flingBehavior = ScrollableDefaults.flingBehavior()

    LazyLayout(
        { itemProvider },
        prefetchState = prefetchState,
        modifier = modifier
            .then(state.remeasurementModifier)
            .scrollable(
                orientation = Orientation.Vertical,
                state = state,
                enabled = userScrollEnabled,
                flingBehavior = flingBehavior,
            )
            .clipToBounds()
            .drawBehind {
                // Reading state.layoutInfo here establishes a snapshot read;
                // every measure pass publishes a fresh instance (neverEqualPolicy),
                // which invalidates this draw node.
                drawSectionBackgrounds(state.layoutInfo)
            },
        measurePolicy = { constraints ->
            with(policy) { measure(constraints) }
        }
    )
}

/**
 * Paints every visible container as ONE continuous rounded-rect spanning its
 * items. Because this is a single shape per section (not per-item), adjacent
 * rows share pixel-perfect boundaries.
 */
private fun DrawScope.drawSectionBackgrounds(info: LazyContainerLayoutInfo) {
    if (info.visibleSections.isEmpty()) return
    info.visibleSections.forEach { section ->
        val style = section.style
        val topR = if (section.topRounded) style.cornerRadius.toPx() else 0f
        val botR = if (section.bottomRounded) style.cornerRadius.toPx() else 0f
        val stroke = style.borderStroke.toPx()

        val left = section.leftX.toFloat()
        val right = section.rightX.toFloat()
        val top = section.topY.toFloat()
        val bottom = section.bottomY.toFloat()
        if (right <= left || bottom <= top) return@forEach

        val rr = RoundRect(
            rect = Rect(left, top, right, bottom),
            topLeft = CornerRadius(topR, topR),
            topRight = CornerRadius(topR, topR),
            bottomRight = CornerRadius(botR, botR),
            bottomLeft = CornerRadius(botR, botR),
        )
        val path = Path().apply { addRoundRect(rr) }

        drawPath(path, style.containerColor)
        if (stroke > 0f) {
            drawPath(path, style.borderColor, style = Stroke(width = stroke))
        }

        if (style.drawDividers && section.dividerYs.isNotEmpty()) {
            val indent = style.dividerIndent.toPx()
            val startX = left + indent
            val endX = right - stroke
            for (divY in section.dividerYs) {
                val y = divY.toFloat()
                drawLine(
                    color = style.dividerColor,
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = stroke,
                )
            }
        }
    }
}
