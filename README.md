# LazyContainerCompose

A Jetpack Compose `LazyColumn`-like component that natively supports visually-grouped **container sections** — the kind of rounded, bordered groups you see on iOS/Android settings screens — **without sacrificing per-row virtualisation**.

Built directly on `androidx.compose.foundation.lazy.layout.LazyLayout`, the same primitive that powers the official `LazyColumn` / `LazyRow` / `LazyVerticalGrid`. Every row inside a container is still an individual lazy slot: prefetching, content-type pooling, measurement and recycling behave identically to a vanilla lazy list.

The container chrome (rounded corners, border, inter-row dividers) is drawn as **one continuous shape per visible section at the layout level**, spanning all its visible items. No per-item slice, no sub-pixel seams between rows on any display density.

## Why

`LazyColumn` forces a trade-off whenever you want a "grouped" look:

- Put the whole group inside a single `item { Card { Column { ... } } }` and you lose per-row virtualisation — the entire card is composed and measured as one giant slot.
- Emit each row as its own `item { }` and draw a per-item background "slice" (rounded top / flat / rounded bottom) and you get seams between rows at fractional pixel offsets on non-integer density displays.

`LazyContainerColumn` keeps each row as its own lazy slot AND draws the container as a single shape that spans them. Best of both worlds.

## Highlights

- **Built on `LazyLayout`** — the same low-level API used by `LazyColumn`, so every optimisation is preserved:
  - only visible items are composed / measured / placed
  - `contentType` partitions the composable reuse pool
  - `LazyLayoutPrefetchState` prefetches the next off-screen items on idle frames
  - scroll state survives config changes via `rememberSaveable`
- **`containerSection { ... }` DSL** mirrors `item { }` / `items(count) { }` from `LazyListScope`
- **Continuous container rendering** — no gap between rows regardless of DPI
- **Per-section styling** via `ContainerStyle` (container color, border color, divider color, corner radius, stroke, horizontal inset, divider indent)
- **Zero dependencies beyond Compose foundation + material3**

## Usage

```kotlin
@Composable
fun MyScreen() {
    LazyContainerColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        // Plain items behave exactly like LazyColumn.item { }
        item(key = "title") { ScreenTitle("Settings") }

        // A visually-grouped container. Each row below is still its
        // own lazy slot; the rounded container chrome is drawn as
        // one continuous shape spanning them.
        containerSection(sectionKey = "account") {
            item(key = "profile",       contentType = "profileRow") { ProfileRow(...) }
            item(key = "password",      contentType = "navRow")     { NavRow(...) }
            item(key = "notifications", contentType = "toggleRow")  { ToggleRow(...) }
        }

        // A large list inside a section still virtualises per-row.
        containerSection(sectionKey = "favourites") {
            items(
                count = favourites.size,
                key = { i -> "fav-$i" },
                contentType = { "favouriteRow" },
            ) { i ->
                NavRow(title = favourites[i])
            }
        }

        // Per-section styling.
        containerSection(
            sectionKey = "danger",
            style = ContainerStyle.Default.copy(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                borderColor    = MaterialTheme.colorScheme.error,
            ),
        ) {
            item(key = "logout") { NavRow(title = "Log out") }
        }
    }
}
```

## How it works

The code lives in `app/src/main/java/com/lazycontainer/compose/lazy/container/` and is deliberately organised as a drop-in library package:

| File | Responsibility |
| --- | --- |
| `LazyContainerColumn.kt` | Public `@Composable` entry point; wires LazyLayout + scrollable + background drawing. |
| `LazyContainerScope.kt` | DSL types (`LazyContainerScope`, `LazyContainerSectionScope`) + interval model (entries + section ranges). |
| `LazyContainerItemProvider.kt` | `LazyLayoutItemProvider` implementation — stable across recompositions, re-walks the DSL only on content-lambda identity change. |
| `LazyContainerListState.kt` | `ScrollableState` implementation; holds first-visible-item index + offset; publishes a `LazyContainerLayoutInfo` snapshot every measure pass. |
| `LazyContainerMeasurePolicy.kt` | The heart of the component. Applies pending scroll, measures the visible range (walking backward if needed, "pulling back" at the end of the list), schedules prefetch for the next two off-screen items, and computes per-section pixel bounds. |
| `ContainerStyle.kt` | Per-section visual style (colors, corner radius, border stroke, padding, divider indent). |

At the end of every measure pass the policy publishes a `LazyContainerLayoutInfo` containing the pixel bounds of each visible section. A `Modifier.drawBehind { }` on the outer node reads that snapshot and paints each section as one rounded-rect path — so adjacent rows share pixel-perfect boundaries.

## Demo

The app module ships a settings-style demo (`com.lazycontainer.compose.demo.SettingsScreen`) that includes both short grouped sections AND a 120-row "Favourites" section inside a single container — scroll it and observe that only the rows inside the viewport are composed.

To run:

```
./gradlew :app:installDebug
```

(Requires the Android SDK. Set `ANDROID_HOME` or add `sdk.dir=...` to a local `local.properties` file.)

## Testing

Seeing rows render on-device only proves the UI composes — it does not prove the internal optimisations (virtualisation, prefetch, `contentType` reuse) are actually firing. The module ships an instrumented test suite that asserts each behaviour directly against `state.layoutInfo` and a composition counter wired through `DisposableEffect`.

### What each test proves

| File | Case | What it asserts |
| --- | --- | --- |
| `LazyContainerVirtualisationTest` | `onlyVisibleItemsAreComposed` | 1000 rows → `state.layoutInfo.visibleItems.size in 10..16`, active compositions ≤ 18. Fails loudly if the whole list composes. |
| `LazyContainerVirtualisationTest` | `visibleIndexWindowAdvancesOnScroll` | `state.scrollToItem(500)` → first visible index in 498..502, active ≤ 18. |
| `LazyContainerVirtualisationTest` | `containerSectionRowsStayIndependentlyVirtualised` | Same bounds hold when all 1000 rows live inside one `containerSection { }` — proves the section is layout-level chrome, not a `Card { Column }` megaslot. |
| `LazyContainerPrefetchTest` | `prefetchComposesNextOffScreenIndices` | After first layout, the set `activeIndices − visibleIndices` has ≤ 2 entries and is a subset of `{lastVisible+1, lastVisible+2}`. Observable shadow of `PrefetchHandleTracker.updatePrefetch`. |
| `LazyContainerPrefetchTest` | `prefetchedItemsReleaseOnScrollAway` | After scrolling to index 100, every previously-prefetched index is gone from `activeIndices` — proves `schedulePrefetch` handles are cancelled when the window shifts. |
| `LazyContainerReuseTest` | `sharedContentTypeEnablesReuse` / `uniqueContentTypePreventsReuse` | Same scroll pattern run twice: shared `contentType = "row"` keeps cumulative compositions bounded (reuse pool hits); unique `contentType = "row-$i"` blows it up (no reuse possible). The contrast proves `ContentTypeKey(base, sectionIndex)` is correctly feeding the pool. |

### Running the tests

```
./gradlew :app:assembleDebugAndroidTest      # compile check
./gradlew :app:connectedDebugAndroidTest     # needs an emulator/device attached
```

Filter one class with:

```
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lazycontainer.compose.lazy.container.LazyContainerPrefetchTest
```

Test sources live under `app/src/androidTest/java/com/lazycontainer/compose/lazy/container/` — the **same package** as the production code so `internal` state (`layoutInfo`, `firstVisibleItemIndex`) is readable without widening the public API. No production code changes were needed to make the behaviours observable.

### Manual sanity checks

Two zero-code cross-checks for when you change the measure policy and want a fast sniff test before writing a unit test:

- **Android Studio Layout Inspector** — install debug, attach inspector, scroll the 120-row Favourites section. The `LazyLayout` node should have ≤ ~16 children at all times, and each row inside the container should sit as a direct child of `LazyLayout` (not nested under a single section node).
- **CPU Profiler / Perfetto system trace** — record during a 3-second fling. Look for `LazyLayoutPrefetcher` slices between `Choreographer#doFrame` slices; their presence on idle frames confirms prefetch is running. Composable slices per frame should track the visible window, not the total item count.

## Requirements

- Android minSdk 24
- Compose foundation >= 1.5 (ships `LazyLayoutItemProvider.Item(index, key)`)
- Kotlin 1.7.20+

The module uses `@ExperimentalFoundationApi` because `LazyLayout` is still marked experimental upstream. The shape has been stable since Compose 1.5.

## License

MIT License. See [LICENSE](LICENSE).
