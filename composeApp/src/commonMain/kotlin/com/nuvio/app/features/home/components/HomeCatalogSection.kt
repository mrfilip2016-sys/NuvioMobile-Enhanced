package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.watching.application.WatchingState

@Composable
fun HomeCatalogRowSection(
    section: HomeCatalogSection,
    modifier: Modifier = Modifier,
    entries: List<MetaPreview> = section.items,
    watchedKeys: Set<String> = emptySet(),
    fullyWatchedSeriesKeys: Set<String> = emptySet(),
    sectionPadding: Dp? = null,
    onViewAllClick: (() -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    if (sectionPadding != null) {
        HomeCatalogRowSectionContent(
            section = section,
            entries = entries,
            watchedKeys = watchedKeys,
            fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            modifier = modifier.fillMaxWidth(),
            sectionPadding = sectionPadding,
            onViewAllClick = onViewAllClick,
            onPosterClick = onPosterClick,
            onPosterLongClick = onPosterLongClick,
        )
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            HomeCatalogRowSectionContent(
                section = section,
                entries = entries,
                watchedKeys = watchedKeys,
                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                modifier = Modifier.fillMaxWidth(),
                sectionPadding = homeSectionHorizontalPaddingForWidth(maxWidth.value),
                onViewAllClick = onViewAllClick,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
            )
        }
    }
}

@Composable
private fun HomeCatalogRowSectionContent(
    section: HomeCatalogSection,
    entries: List<MetaPreview>,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    modifier: Modifier,
    sectionPadding: Dp,
    onViewAllClick: (() -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val topTenTitle = when (section.key) {
        "com.linvo.cinemeta:movie:top" -> "Top 10 Filme"
        "com.linvo.cinemeta:series:top" -> "Top 10 Seriale"
        else -> null
    }

    if (topTenTitle != null) {
        val rankedEntries = entries.take(10).mapIndexed { index, item -> (index + 1) to item }
        NuvioShelfSection(
            title = topTenTitle,
            entries = rankedEntries,
            modifier = modifier,
            headerHorizontalPadding = sectionPadding,
            rowContentPadding = PaddingValues(horizontal = sectionPadding),
            itemSpacing = 4.dp,
            onViewAllClick = onViewAllClick,
            viewAllPillSize = NuvioViewAllPillSize.Compact,
            key = { ranked -> ranked.second.stableKey() },
        ) { ranked ->
            val rank = ranked.first
            val item = ranked.second
            val rankInset = if (rank >= 10) 62.dp else 44.dp
            Box(
                modifier = Modifier.padding(start = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.20f),
                    fontWeight = FontWeight.Black,
                    fontSize = 112.sp,
                    lineHeight = 112.sp,
                    maxLines = 1,
                )
                HomePosterCard(
                    item = item,
                    modifier = Modifier.padding(start = rankInset),
                    useLandscapeBackdropMode = posterCardStyle.catalogLandscapeModeEnabled,
                    isWatched = WatchingState.isPosterWatched(
                        watchedKeys = watchedKeys,
                        item = item,
                        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                    ),
                    onClick = onPosterClick?.let { { it(item) } },
                    onLongClick = onPosterLongClick?.let { { it(item) } },
                )
            }
        }
        return
    }

    NuvioShelfSection(
        title = section.title,
        entries = entries,
        modifier = modifier,
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        onViewAllClick = onViewAllClick,
        viewAllPillSize = NuvioViewAllPillSize.Compact,
        key = { item -> item.stableKey() },
    ) { item ->
        HomePosterCard(
            item = item,
            useLandscapeBackdropMode = posterCardStyle.catalogLandscapeModeEnabled,
            isWatched = WatchingState.isPosterWatched(
                watchedKeys = watchedKeys,
                item = item,
                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
            ),
            onClick = onPosterClick?.let { { it(item) } },
            onLongClick = onPosterLongClick?.let { { it(item) } },
        )
    }
}
