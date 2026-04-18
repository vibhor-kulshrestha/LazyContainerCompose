package com.lazycontainer.compose.lazy.container

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * DSL scope for [LazyContainerColumn]. Mirrors `LazyListScope` (item/items) and
 * adds [containerSection] for emitting a visually-grouped container of items.
 *
 * Each entry added here becomes ONE individual lazy slot in the underlying
 * LazyLayout — virtualisation, key-based reuse, `contentType` pooling and
 * prefetching therefore work per-row, even for rows that visually belong to a
 * shared container.
 */
@DslMarker
annotation class LazyContainerDsl

@LazyContainerDsl
class LazyContainerScope internal constructor() {
    internal val intervalContent = LazyContainerIntervalContent()

    /** Emits a single lazy item. */
    fun item(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable () -> Unit,
    ) {
        intervalContent.addEntry(
            LazyContainerEntry(
                key = key,
                contentType = contentType,
                content = content,
                sectionIndex = null,
            )
        )
    }

    /** Emits [count] lazy items, one per index. */
    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        itemContent: @Composable (index: Int) -> Unit,
    ) {
        repeat(count) { i ->
            intervalContent.addEntry(
                LazyContainerEntry(
                    key = key?.invoke(i),
                    contentType = contentType(i),
                    content = { itemContent(i) },
                    sectionIndex = null,
                )
            )
        }
    }

    /**
     * Emits a visually-grouped container spanning the items defined inside
     * [content]. Each item inside is a normal lazy slot; the container chrome
     * (rounded corners, border, dividers) is drawn as a single continuous shape
     * at the layout level — no per-item slice, no sub-pixel seams.
     */
    fun containerSection(
        sectionKey: Any? = null,
        style: ContainerStyle = ContainerStyle.Default,
        content: LazyContainerSectionScope.() -> Unit,
    ) {
        val startItemIndex = intervalContent.entryCount
        val sectionIndex = intervalContent.sections.size
        val sectionScope = LazyContainerSectionScope(sectionIndex).apply(content)
        if (sectionScope.entries.isEmpty()) return
        sectionScope.entries.forEach { intervalContent.addEntry(it) }
        val endItemIndex = intervalContent.entryCount - 1
        intervalContent.sections += SectionInterval(
            sectionIndex = sectionIndex,
            key = sectionKey,
            startItemIndex = startItemIndex,
            endItemIndex = endItemIndex,
            style = style,
        )
    }
}

@LazyContainerDsl
class LazyContainerSectionScope internal constructor(
    private val sectionIndex: Int,
) {
    internal val entries = mutableListOf<LazyContainerEntry>()

    fun item(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable () -> Unit,
    ) {
        entries += LazyContainerEntry(
            key = key,
            contentType = contentType,
            content = content,
            sectionIndex = sectionIndex,
        )
    }

    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        itemContent: @Composable (index: Int) -> Unit,
    ) {
        repeat(count) { i ->
            entries += LazyContainerEntry(
                key = key?.invoke(i),
                contentType = contentType(i),
                content = { itemContent(i) },
                sectionIndex = sectionIndex,
            )
        }
    }
}

@Immutable
internal class LazyContainerEntry(
    val key: Any?,
    val contentType: Any?,
    val content: @Composable () -> Unit,
    /** Null for standalone items, otherwise the section this entry belongs to. */
    val sectionIndex: Int?,
)

@Immutable
internal data class SectionInterval(
    val sectionIndex: Int,
    val key: Any?,
    val startItemIndex: Int,
    val endItemIndex: Int,
    val style: ContainerStyle,
) {
    fun contains(itemIndex: Int): Boolean =
        itemIndex in startItemIndex..endItemIndex
}

internal class LazyContainerIntervalContent {
    private val _entries = ArrayList<LazyContainerEntry>()
    val sections = ArrayList<SectionInterval>()

    val entryCount: Int get() = _entries.size
    fun entryAt(index: Int): LazyContainerEntry = _entries[index]
    fun addEntry(entry: LazyContainerEntry) { _entries += entry }

    fun sectionForItem(itemIndex: Int): SectionInterval? {
        // Sections are contiguous and ordered; binary search would be nicer
        // but linear is cheap for typical section counts.
        for (s in sections) if (s.contains(itemIndex)) return s
        return null
    }
}
