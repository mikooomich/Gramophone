package org.akanework.gramophone.ui.components.player.outertune

import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlay
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlayState
import org.akanework.gramophone.ui.components.lyrics.LyricsPadding
import org.akanework.gramophone.ui.components.player.ActionBarRow2
import org.akanework.gramophone.ui.components.player.AnimatedColorScheme
import org.akanework.gramophone.ui.components.player.FullPlayerActions
import org.akanework.gramophone.ui.components.player.HANDLE_ALPHA
import org.akanework.gramophone.ui.components.player.MiniBarColors
import org.akanework.gramophone.ui.components.player.NowPlayingColors
import org.akanework.gramophone.ui.components.player.NowPlayingSheetState
import org.akanework.gramophone.ui.components.player.PlayerControls
import org.akanework.gramophone.ui.components.player.PlayerSheetController
import org.akanework.gramophone.ui.components.player.PlayerSheetPlayerState
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ART_TOP_GAP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LANDSCAPE_MARGIN
import org.akanework.gramophone.ui.components.player.PlayerUtilities.PORTRAIT_MARGIN
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_BOTTOM_PADDING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_THICKNESS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_TOP_PADDING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_WIDTH
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_PREVIEW_HEIGHT
import org.akanework.gramophone.ui.components.player.QueueHeader2
import org.akanework.gramophone.ui.components.player.SharedArtwork2
import org.akanework.gramophone.ui.components.player.TopButtonRow
import org.akanework.gramophone.ui.components.player.nowPlayingColors
import org.akanework.gramophone.ui.components.player.queueAlpha
import org.akanework.gramophone.ui.components.player.rememberAnimatedColorScheme
import org.akanework.gramophone.ui.components.player.rememberArtworkColorScheme
import org.akanework.gramophone.ui.components.player.rowAlpha
import org.akanework.gramophone.ui.fragments.compose.rememberMqState
import org.akanework.gramophone.ui.nav.NAV_TRANSITION_MS
import org.akanework.gramophone.ui.nav.NavAxisEasing
import org.akanework.gramophone.ui.theme.harmonizeBy
import kotlin.math.pow


@Composable
fun BottomSheetPlayer(
    sheetstate: BottomSheetState,
    sheetController: PlayerSheetController,
    modifier: Modifier = Modifier,

    ) {
    val TAG = "BottomSheetPlayer"
    Log.v(TAG, "PLR-1")

    val player = sheetController.playerState
    val pageTinted = sheetController.pageTinted
    val actions = sheetController.actions
    val state = sheetController.sheetState

// steal theme code and pray it is enough
    val context = LocalContext.current
    val density = LocalDensity.current
    val cookieCover = rememberBooleanPreference("cookie_cover", false).value
    val expandedArtCorner = rememberIntPreference(
        "album_round_corner", integerResource(R.integer.round_corner_radius)
    ).value.dp
    // Its colours are read in the draw phase where they can be, so a song change's colour
    // animation mostly redraws instead of recomposing the player every frame.
    val targetScheme = rememberArtworkColorScheme(player.artworkUri)
    // Without a cover it's the app theme, which animates by itself when the theme changes
    val coverScheme = rememberAnimatedColorScheme(
        targetScheme, live = targetScheme === MaterialTheme.colorScheme,
    )
//    var activeDialog by remember { mutableStateOf<PlayerDialog?>(null) }
//    PlayerDialogs(
//        activeDialog, coverScheme.target, dialogCallbacks, onDismiss = { activeDialog = null },
//    )

    // How far the bar leans towards the app's hue: fully, except on a page themed from a cover,
    // which shows the cover's own colours. Animated with the page transition. Only read in the
    // draw phase and in the mini bar content, so the animation does not recompose the sheet on
    // every frame.
    val harmony = animateFloatAsState(
        if (pageTinted) 0f else 1f,
        tween(NAV_TRANSITION_MS, easing = NavAxisEasing),
        label = "page harmony",
    )
    val appPrimary = rememberUpdatedState(MaterialTheme.colorScheme.primary)
    val barColors = remember(coverScheme) {
        derivedStateOf { nowPlayingColors(appPrimary.value, harmony.value, coverScheme::color) }
    }
    val miniColors = remember(coverScheme) {
        derivedStateOf {
            val primary = appPrimary.value
            val f = harmony.value
            MiniBarColors(
                content = coverScheme.color { onSurface }.harmonizeBy(primary, f),
                playButton = coverScheme.color { secondaryContainer }.harmonizeBy(primary, f),
                onPlayButton = coverScheme.color { onSecondaryContainer }.harmonizeBy(primary, f),
            )
        }
    }


    BottomSheet(
        state = sheetstate,
        modifier = modifier,
        contentBackground = {
            val expanded = MaterialTheme.colorScheme.surfaceContainerLow
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(lerp(barColors.value.bar, expanded, sheetstate.progress.coerceIn(0f, 1f)))
                    },
            )
        },
//        collapsedBackgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
        onDismiss = {
//            playerConnection.softKillPlayer()
        },
        collapsedContent = {
            MiniPlayer(
                bottomSheetState = sheetstate,
                nowPlayingSheetState = state,
                playerSheetPlayerState = player,
                actions = actions,
                miniBarColors = miniColors,
                nowPlayingColors = barColors
            )
        }) {
        Log.v(TAG, "PLR-3.0")

        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LandscapePlayer(

            )
        } else {
            PortraitPlayer(
                bottomSheetState = sheetstate,
                nowPlayingSheetState = state,
                playerSheetPlayerState = player,
                mediaController = sheetController.mediaController,
                actions = actions,
                miniBarColors = miniColors,
                nowPlayingColors = barColors,
                lyrics = sheetController.lyrics,
                scheme = coverScheme
            )
        }
    }
}


@Composable
fun PortraitPlayer(
    bottomSheetState: BottomSheetState,
    nowPlayingSheetState: NowPlayingSheetState,
    playerSheetPlayerState: PlayerSheetPlayerState,
    mediaController: MediaControllerViewModel,
    actions: FullPlayerActions,
    miniBarColors: State<MiniBarColors>,
    nowPlayingColors: State<NowPlayingColors>,
    lyrics: LyricsOverlayState,
    scheme: AnimatedColorScheme,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LyricsOverlay(
        state = lyrics,
        scheme = scheme,
        padding = LyricsPadding(0, 0, 0, 0),
//            padding = LyricsPadding(
//                left = geometry.leftInset.toInt(),
//                top = geometry.statusTop.toInt(),
//                right = geometry.rightInset.toInt(),
//                bottom = geometry.bottomInset.toInt(),
//            ),
//                    modifier = Modifier . pageFollowingCover (frame),
    )
    Column(
        Modifier
            .fillMaxSize()
//                .padding(insets),
    ) {
        TopButtonRow(playerSheetPlayerState, actions, scheme, { /*onopendialog*/ })
        Spacer(Modifier.height(ART_TOP_GAP))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .aspectRatio(1f)
        ) {
            SharedArtwork2(
                playerSheetPlayerState = playerSheetPlayerState,
                requestWidth = maxHeight.value,
                cookie = false,// cookieCover,
                onClick = actions.openSongDetails
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PlayerControls(
                playerSheetPlayerState, actions, scheme, { /*onOpenDialog*/ }, PORTRAIT_MARGIN, Modifier.fillMaxWidth(),
            )
        }
        // The bottom row's place: the row is on top of the queue, see QueueDrawer
        Spacer(Modifier.height(QueueHeight))

    }
    val queueSheetState = rememberBottomSheetState(
        dismissedBound = 0.dp,
        collapsedBound = QueueHeight + 20.dp,
        expandedBound = bottomSheetState.expandedBound - QUEUE_PREVIEW_HEIGHT,
        initialAnchor = 1,
    )

    BottomSheet(
        state = queueSheetState,
        contentBackground = {
            val expanded = MaterialTheme.colorScheme.surfaceContainerLow
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val down = scheme.color { surfaceContainerLow }
                        val alpha =
                            (queueSheetState.progress.toDouble().pow((1f / 3f).toDouble()) * 1.5).toFloat()
                                .coerceAtMost(1f)
                        drawRect(
                            lerp(down, scheme.color { surfaceContainer }, queueSheetState.progress),
                            alpha = alpha
                        )
                    },

                )
        },
//        collapsedBackgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
        onDismiss = {
//            playerConnection.softKillPlayer()
        },
        collapsedContent = {
            ActionBarRow2(
                queueSheetState, playerSheetPlayerState, actions, scheme,
                horizontalMargin = if (false) {
                    LANDSCAPE_MARGIN
                } else {
                    PORTRAIT_MARGIN
                },
                modifier = Modifier
                    .graphicsLayer { alpha = rowAlpha(queueSheetState.progress) },
            )
        },
        contentShape = null,
    ) {

        val isWideLandscape = false
        val mqState = rememberMqState(context, scope, mediaController, isWideLandscape, queueSheetState::collapseSoft)
            ?: return@BottomSheet
        DisposableEffect(mqState) {
            mqState.onShow()
            onDispose { mqState.onHide() }
        }



        Column(
            Modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .drawBehind {
                    val down = scheme.color { surfaceContainerLow }
                    val alpha =
                        (queueSheetState.progress.toDouble().pow((1f / 3f).toDouble()) * 1.5).toFloat()
                            .coerceAtMost(1f)
                    drawRect(
                        lerp(down, scheme.color { surfaceContainer }, queueSheetState.progress),
                        alpha = alpha
                    )
                },
        ) {


            val description = stringResource(R.string.current_playlist)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = if (queueSheetState.isExpanded) stringResource(R.string.expand_less) else null,
                        onClick = { if (queueSheetState.isExpanded) queueSheetState.collapseSoft() else queueSheetState.expandSoft() },
                    )
                    .semantics { contentDescription = description }
                    .padding(top = QUEUE_HANDLE_TOP_PADDING, bottom = QUEUE_HANDLE_BOTTOM_PADDING),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .size(QUEUE_HANDLE_WIDTH, QUEUE_HANDLE_THICKNESS)
                        .graphicsLayer { alpha = queueAlpha(queueSheetState.progress) }
                        .clip(CircleShape)
                        .drawBehind {
                            drawRect(scheme.color { onSurfaceVariant }.copy(alpha = HANDLE_ALPHA))
                        },
                )
            }


            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
//                if (queueComposed) {
//                    MaterialTheme(colorScheme = scheme.target) {
//                        QueueBody(
//                            mqState,
//                            reveal = queue,
//                            modifier = Modifier
//                                .fillMaxSize()
//                                .padding(sides)
//                                .graphicsLayer { alpha = queueAlpha(queue.progress) },
//                        ) {
//                            if (queue.shown && geometry.isWideLandscape) {
//                                QueueHeader(
//                                    player, mqState, actions, scheme, queue, drag, geometry, sides
//                                )
//                            }
//                        }
//                    }
//                }
            }
        }


        if (!queueSheetState.isCollapsed) {

            Box(
                Modifier
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        val p = queueSheetState.progress.coerceIn(0f, 1f)
                        translationY =
                            -(queueSheetState.expandedBound - queueSheetState.value + QUEUE_PREVIEW_HEIGHT).roundToPx() - size.height * (1f - p)
                        alpha = p
                    }
            ) {
                QueueHeader2(
                    queueSheetState, playerSheetPlayerState, mqState, actions, scheme,
                )
            }
        }
    }
}


@Composable
fun LandscapePlayer(
) {
    Row(
        modifier = Modifier

            .fillMaxSize()
    ) {

    }
}

