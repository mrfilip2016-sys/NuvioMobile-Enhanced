package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
internal actual fun AfPlayCastButton(
    mediaUrl: String,
    title: String,
    subtitle: String?,
    positionMs: Long,
    subtitleUrl: String?,
    subtitleLanguage: String?,
    enabled: Boolean,
    buttonSize: Dp,
) = Unit
