package org.akanework.gramophone

import android.annotation.SuppressLint
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.services.GramophonePlaybackService
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter
import org.akanework.gramophone.ui.viewmodels.LibraryViewModel

@UnstableApi class MainActivity : AppCompatActivity() {

    private val libraryViewModel: LibraryViewModel by viewModels()
    private lateinit var sessionToken: SessionToken
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    private lateinit var bottomSheetPreviewCover: ImageView
    private lateinit var bottomSheetPreviewTitle: TextView
    private lateinit var bottomSheetPreviewSubtitle: TextView
    private lateinit var bottomSheetPreviewControllerButton: MaterialButton
    private lateinit var bottomSheetPreviewNextButton: MaterialButton

    private lateinit var standardBottomSheet: FrameLayout
    private lateinit var standardBottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    private lateinit var topAppBar: MaterialToolbar

    private var isPlayerPlaying = false

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            updateSongInfo(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            isPlayerPlaying = isPlaying
            val instance = controllerFuture.get()
            Log.d("TAG", "isPlaying, $isPlaying")
            if (isPlaying) {
                bottomSheetPreviewControllerButton.icon =
                    AppCompatResources.getDrawable(applicationContext, R.drawable.pause_art)
            } else if (instance.playbackState != 2) {
                Log.d("TAG", "Triggered, ${instance.playbackState}")
                bottomSheetPreviewControllerButton.icon =
                    AppCompatResources.getDrawable(applicationContext, R.drawable.play_art)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            Log.d("TAG", "PlaybackState: $playbackState")
        }
    }

    fun updateSongInfo(mediaItem: MediaItem?) {
        Log.d("TAG", "${!controllerFuture.get().isPlaying}")
        val instance = controllerFuture.get()
        if (instance.mediaItemCount != 0) {
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    Log.d("TAG", "PlaybackState: ${instance.playbackState}, isPlaying: ${instance.isPlaying}")
                    if (instance.isPlaying) {
                        Log.d("TAG", "REACHED1")
                        bottomSheetPreviewControllerButton.icon =
                            AppCompatResources.getDrawable(applicationContext, R.drawable.pause_art)
                    } else if (instance.playbackState != 2) {
                        Log.d("TAG", "REACHED2")
                        bottomSheetPreviewControllerButton.icon =
                            AppCompatResources.getDrawable(applicationContext, R.drawable.play_art)
                    }
                    standardBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    Handler(Looper.getMainLooper()).postDelayed({
                        standardBottomSheetBehavior.isHideable = false
                    }, 200 )}, 200)
            Glide.with(bottomSheetPreviewCover)
                .load(mediaItem?.mediaMetadata?.artworkUri)
                .placeholder(R.drawable.ic_default_cover)
                .into(bottomSheetPreviewCover)
            bottomSheetPreviewTitle.text = mediaItem?.mediaMetadata?.title
            bottomSheetPreviewSubtitle.text = mediaItem?.mediaMetadata?.artist
        } else {
            if (!standardBottomSheetBehavior.isHideable) {
                standardBottomSheetBehavior.isHideable = true
            }
            Handler(Looper.getMainLooper()).postDelayed({
                standardBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }, 200)
        }
    }

    override fun onStart() {
        sessionToken = SessionToken(this, ComponentName(this, GramophonePlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken)
            .buildAsync()
        controllerFuture.addListener(
            {   val controller = controllerFuture.get()
                controller.addListener(playerListener)
                topAppBar.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.shuffle -> {
                            libraryViewModel.mediaItemList.value?.let { it1 ->
                                controller.setMediaItems(
                                    it1
                                )
                                controller.shuffleModeEnabled = true
                                controller.prepare()
                                controller.play()
                            }
                        }
                        R.id.search -> {

                        }
                        else -> throw IllegalStateException()
                    }
                    true
                }
                bottomSheetPreviewControllerButton.setOnClickListener {
                    if (controller.isPlaying) {
                        controllerFuture.get().pause()
                    } else {
                        controllerFuture.get().play()
                    }
                }
                bottomSheetPreviewNextButton.setOnClickListener {
                    controllerFuture.get().seekToNextMediaItem()
                }
                updateSongInfo(controller.currentMediaItem)
            },
            MoreExecutors.directExecutor()
        )
        Log.d("TAG", "onStart")
        super.onStart()
    }

    fun getSession() = sessionToken

    fun getPlayer() = controllerFuture.get()

    @SuppressLint("StringFormatMatches")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (libraryViewModel.mediaItemList.value!!.isEmpty()) {
            CoroutineScope(Dispatchers.Default).launch {
                val pairObject = MediaStoreUtils.getAllSongs(applicationContext)
                withContext(Dispatchers.Main) {
                    libraryViewModel.mediaItemList.value = pairObject.songList
                    libraryViewModel.albumItemList.value = pairObject.albumList
                    libraryViewModel.artistItemList.value = pairObject.artistList
                    libraryViewModel.genreItemList.value = pairObject.genreList
                    libraryViewModel.dateItemList.value = pairObject.dateList
                }
            }
        }

        val params = window.attributes
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = params

        // Set content Views.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        // Initialize layouts.
        val viewPager2 = findViewById<ViewPager2>(R.id.fragment_viewpager)
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        topAppBar = findViewById(R.id.topAppBar)
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)

        standardBottomSheet = findViewById(R.id.player_layout)
        standardBottomSheetBehavior = BottomSheetBehavior.from(standardBottomSheet)

        bottomSheetPreviewCover = findViewById(R.id.preview_album_cover)
        bottomSheetPreviewTitle = findViewById(R.id.preview_song_name)
        bottomSheetPreviewSubtitle = findViewById(R.id.preview_artist_name)
        bottomSheetPreviewControllerButton = findViewById(R.id.preview_control)
        bottomSheetPreviewNextButton = findViewById(R.id.preview_next)

        standardBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        topAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.shuffle -> {
                    true
                }
                else -> {
                    true
                }
            }
        }

        navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.songs -> {
                    viewPager2.setCurrentItem(0, true)
                    drawerLayout.close()
                    true
                }
                R.id.albums -> {
                    viewPager2.setCurrentItem(1, true)
                    drawerLayout.close()
                    true
                }
                R.id.artists -> {
                    viewPager2.setCurrentItem(2, true)
                    drawerLayout.close()
                    true
                }
                R.id.genres -> {
                    viewPager2.setCurrentItem(3, true)
                    drawerLayout.close()
                    true
                }
                R.id.dates -> {
                    viewPager2.setCurrentItem(4, true)
                    drawerLayout.close()
                    true
                }
                R.id.playlists -> {
                    viewPager2.setCurrentItem(5, true)
                    drawerLayout.close()
                    true
                }
                R.id.refresh -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        val pairObject = MediaStoreUtils.getAllSongs(applicationContext)
                        withContext(Dispatchers.Main) {
                            libraryViewModel.mediaItemList.value = pairObject.songList
                            libraryViewModel.albumItemList.value = pairObject.albumList
                            libraryViewModel.artistItemList.value = pairObject.artistList
                            libraryViewModel.genreItemList.value = pairObject.genreList
                            libraryViewModel.dateItemList.value = pairObject.dateList
                            val snackBar = Snackbar.make(viewPager2,
                                getString(
                                    R.string.refreshed_songs,
                                    libraryViewModel.mediaItemList.value!!.size
                                ), Snackbar.LENGTH_LONG)
                            snackBar.setAction(R.string.dismiss) {
                                snackBar.dismiss()
                            }
                            snackBar.setBackgroundTint(
                                MaterialColors.getColor(
                                snackBar.view,
                                com.google.android.material.R.attr.colorSurface
                            ))
                            snackBar.setActionTextColor(
                                MaterialColors.getColor(
                                snackBar.view,
                                com.google.android.material.R.attr.colorPrimary
                            ))
                            snackBar.setTextColor(
                                MaterialColors.getColor(
                                snackBar.view,
                                com.google.android.material.R.attr.colorOnSurface
                            ))
                            snackBar.anchorView = standardBottomSheet
                            snackBar.show()
                        }
                    }
                    drawerLayout.close()
                    true
                }
                R.id.settings -> {
                    drawerLayout.close()
                    true
                }
                else -> throw IllegalStateException()
            }
        }

        // Handle click for navigationIcon.
        topAppBar.setNavigationOnClickListener {
            drawerLayout.open()
            when (viewPager2.currentItem) {
                0 -> {
                    navigationView.setCheckedItem(R.id.songs)
                }
                1 -> {
                    navigationView.setCheckedItem(R.id.albums)
                }
                2 -> {
                    navigationView.setCheckedItem(R.id.artists)
                }
                3 -> {
                    navigationView.setCheckedItem(R.id.genres)
                }
                4 -> {
                    navigationView.setCheckedItem(R.id.dates)
                }
                5 -> {
                    navigationView.setCheckedItem(R.id.playlists)
                }
                else -> throw IllegalStateException()
            }
        }


        // Connect ViewPager2.
        viewPager2.adapter = ViewPager2Adapter(this)
        TabLayoutMediator(tabLayout, viewPager2) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.category_songs)
                1 -> getString(R.string.category_albums)
                2 -> getString(R.string.category_artists)
                3 -> getString(R.string.category_genres)
                4 -> getString(R.string.category_dates)
                5 -> getString(R.string.category_playlists)
                else -> "Unknown"
            }
        }.attach()

    }

    override fun onStop() {
        super.onStop()
        controllerFuture.get().removeListener(playerListener)
        controllerFuture.get().release()
    }
}