package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
internal expect fun AfPlayCastButton(
    mediaUrl: String,
    title: String,
    subtitle: String?,
    positionMs: Long,
    subtitleUrl: String?,
    subtitleLanguage: String?,
    enabled: Boolean,
    buttonSize: Dp,
)
