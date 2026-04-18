package com.lazycontainer.compose.lazy.container

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.runtime.Composable

/**
 * [LazyLayoutItemProvider] implementation backed by a re-derived
 * [LazyContainerIntervalContent]. The provider is created once and kept stable
 * across recompositions, while [lambdaProvider] always returns the latest
 * content DSL lambda; we re-walk the DSL only when the lambda identity
 * actually changes.
 *
 * Each DSL entry (standalone or inside a `containerSection`) becomes exactly
 * one lazy item — container grouping is purely visual, virtualisation stays
 * at row granularity.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class LazyContainerItemProvider(
    private val lambdaProvider: () -> (LazyContainerScope.() -> Unit),
) : LazyLayoutItemProvider {

    private var cachedLambda: (LazyContainerScope.() -> Unit)? = null
    private var cachedInterval: LazyContainerIntervalContent = LazyContainerIntervalContent()
    private var cachedKeyMap: Map<Any, Int> = emptyMap()

    /** Returns the up-to-date interval content, re-walking the DSL only on lambda change. */
    internal fun intervalContent(): LazyContainerIntervalContent {
        val latest = lambdaProvider()
        if (latest !== cachedLambda) {
            cachedLambda = latest
            cachedInterval = LazyContainerScope().apply(latest).intervalContent
            cachedKeyMap = cachedInterval.buildKeyIndexMap()
        }
        return cachedInterval
    }

    override val itemCount: Int get() = intervalContent().entryCount

    override fun getKey(index: Int): Any =
        intervalContent().entryAt(index).key ?: DefaultKey(index)

    override fun getContentType(index: Int): Any? {
        val entry = intervalContent().entryAt(index)
        val base = entry.contentType ?: "row"
        // Items living in different sections have different measured widths
        // (due to the section's horizontal inset), so they can't share a reuse
        // pool safely — encode the section into contentType.
        return ContentTypeKey(base, entry.sectionIndex ?: -1)
    }

    override fun getIndex(key: Any): Int {
        intervalContent()
        return cachedKeyMap[key] ?: -1
    }

    @Composable
    override fun Item(index: Int, key: Any) {
        intervalContent().entryAt(index).content()
    }

    private data class DefaultKey(val index: Int)
    private data class ContentTypeKey(val base: Any, val sectionIndex: Int)
}

/** Build a stable key → index map for fast `getIndex`. */
internal fun LazyContainerIntervalContent.buildKeyIndexMap(): Map<Any, Int> {
    if (entryCount == 0) return emptyMap()
    val map = HashMap<Any, Int>(entryCount)
    for (i in 0 until entryCount) {
        val k = entryAt(i).key ?: continue
        map[k] = i
    }
    return map
}
