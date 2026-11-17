package org.akanework.gramophone.ui.components.player.outertune

import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.components.player.MiniBarColors
import org.akanework.gramophone.ui.components.player.PlayerSheetController
import org.akanework.gramophone.ui.components.player.nowPlayingColors
import org.akanework.gramophone.ui.components.player.rememberAnimatedColorScheme
import org.akanework.gramophone.ui.components.player.rememberArtworkColorScheme
import org.akanework.gramophone.ui.nav.NAV_TRANSITION_MS
import org.akanework.gramophone.ui.nav.NavAxisEasing
import org.akanework.gramophone.ui.theme.harmonizeBy


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
        background = {
//            PlayerBackground(
//                playerConnection = playerConnection,
//                playerBackground = playerBackground,
//                showLyrics = showLyrics,
//                useDarkTheme = useDarkTheme,
//            )
        },
        collapsedBackgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
        onDismiss = {
//            playerConnection.softKillPlayer()
        },
        collapsedContent = {
            MiniPlayer(
                state = state,
                playerSheetPlayerState = player,
                actions = actions,
                miniBarColors = { miniColors.value },
                barColour = { barColors.value.fill }
            )
        }) {
        Log.v(TAG, "PLR-3.0")

        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LandscapePlayer()
        } else {
            PortraitPlayer()
        }
    }
}


@Composable
fun PortraitPlayer() {

}


@Composable
fun LandscapePlayer(
) {

}

