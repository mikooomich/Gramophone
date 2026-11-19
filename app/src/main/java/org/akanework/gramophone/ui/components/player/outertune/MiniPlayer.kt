/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package org.akanework.gramophone.ui.components.player.outertune

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.player.FullPlayerActions
import org.akanework.gramophone.ui.components.player.MiniBarColors
import org.akanework.gramophone.ui.components.player.NowPlayingSheetState
import org.akanework.gramophone.ui.components.player.PlayPauseIcon
import org.akanework.gramophone.ui.components.player.PlayerSheetPlayerState
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_BUTTON_SIZE
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_ICON_SIZE
import org.akanework.gramophone.ui.components.player.SharedArtwork2

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MiniPlayer(
    state: NowPlayingSheetState,
    playerSheetPlayerState: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    miniBarColors: () -> MiniBarColors,
    barColour: () -> Color,
    backgroundColour: Color = MaterialTheme.colorScheme.surfaceContainer,
    modifier: Modifier = Modifier,
) {

    val (contentColor, playButtonContainer, playButtonContent) = miniBarColors()
    val title = playerSheetPlayerState.title
    val artist = playerSheetPlayerState.artist

    val cookieCover = rememberBooleanPreference("cookie_cover", false).value

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .drawBehind {
                val extra = 64.dp.toPx()
                val totalHeight = size.height + extra

                // overdraw fade out
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to backgroundColour,
                            (size.height / totalHeight) to backgroundColour,
                            1f to Color.Transparent,
                        ),
                        startY = 0f,
                        endY = totalHeight,
                    ),
                    size = Size(size.width, totalHeight),
                )
            },
    ) {
        LinearProgressIndicator(
            progress = { playerSheetPlayerState.positionFraction.coerceIn(0f, 1f) },
            drawStopIndicator = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter),
        )
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(playerSheetPlayerState.positionFraction.coerceIn(0f, 1f))
                .drawBehind { drawRect(barColour()) },
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier

                .fillMaxSize(),
        ) {
            Box(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = modifier
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(48.dp)
                    ) {
                        SharedArtwork2(
                            playerSheetPlayerState = playerSheetPlayerState,
                            requestWidth = maxHeight.value,
                            cookie = cookieCover,
                            onClick = actions.openSongDetails
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    ) {


                        Text(
                            text = title?.toString().orEmpty(),
//                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )


                        Text(
                            text = artist?.toString().orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            FilledIconButton(
                onClick = actions.playPause,
                modifier = Modifier.size(MINI_BUTTON_SIZE),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = playButtonContainer,
                    contentColor = playButtonContent,
                ),
            ) {
                PlayPauseIcon(
                    playing = playerSheetPlayerState.showPause,
                    tint = playButtonContent,
                    modifier = Modifier.size(MINI_ICON_SIZE),
                    contentDescription = stringResource(
                        if (playerSheetPlayerState.showPause) R.string.pause else R.string.play
                    ),
                )
            }
            IconButton(onClick = actions.next, modifier = Modifier.size(MINI_BUTTON_SIZE)) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription = stringResource(R.string.skip_next),
                    tint = contentColor,
                    modifier = Modifier.size(MINI_ICON_SIZE),
                )
            }
        }
    }
}
