package com.lazycontainer.compose.lazy.container

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual style applied to a [containerSection] in a [LazyContainerColumn].
 *
 * The container is drawn as ONE continuous shape spanning every item in the
 * section (not as per-item slices), so no sub-pixel seams can appear between
 * adjacent rows. The items themselves stay independent lazy slots.
 */
@Immutable
data class ContainerStyle(
    val containerColor: Color,
    val borderColor: Color,
    val dividerColor: Color,
    val cornerRadius: Dp = 16.dp,
    val borderStroke: Dp = 1.dp,
    val horizontalPadding: Dp = 16.dp,
    val verticalPaddingTop: Dp = 0.dp,
    val verticalPaddingBottom: Dp = 0.dp,
    val dividerIndent: Dp = 16.dp,
    val drawDividers: Boolean = true,
) {
    companion object {
        val Default = ContainerStyle(
            containerColor = Color.White,
            borderColor = Color(0xFFE2E2E2),
            dividerColor = Color(0xFFECECEC),
        )
    }
}
