package com.dramathon.dramathonplus.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.tv.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dramathon.dramathonplus.DeviceIdManager
import com.dramathon.dramathonplus.DramaViewModel
import com.dramathon.dramathonplus.MainActivity
import com.dramathon.dramathonplus.R
import com.dramathon.dramathonplus.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(viewModel: DramaViewModel = viewModel()) {
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onTimeout = { showSplash = false })
    } else {
        MainContent(viewModel)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainContent(viewModel: DramaViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dramas by viewModel.dramas.collectAsState()
    val selectedDrama by viewModel.selectedDrama.collectAsState()
    val detail by viewModel.selectedDramaDetail.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val watchedEpisodes by viewModel.watchedEpisodes.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val pipMode by viewModel.pipMode.collectAsState()
    val playingEpisode by viewModel.playingEpisode.collectAsState()
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val liveStatus by viewModel.liveStatus.collectAsState()
    val isVersionKilled by viewModel.isVersionKilled.collectAsState()
    val isEndReached by viewModel.isEndReached.collectAsState()

    var searchText by remember { mutableStateOf("") }
    var lastPlayedIndex by remember { mutableIntStateOf(-1) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val deviceId = remember(context) {
        DeviceIdManager.getStableId(context)
    }

    LaunchedEffect(Unit) {
        viewModel.initUnlockStatus(context, deviceId)
        viewModel.checkForUpdatesAuto(com.dramathon.dramathonplus.BuildConfig.VERSION_NAME)
    }

    LaunchedEffect(updateAvailable) {
        updateAvailable?.let { update ->
            showUpdateNotification(context, update)
        }
    }

    LaunchedEffect(playingEpisode) {
        playingEpisode?.let { ep ->
            val drama = detail ?: selectedDrama
            drama?.let {
                viewModel.markAsWatched(it.optLong("bookId"), ep.optInt("chapterIndex"), context)
            }
            lastPlayedIndex = ep.optInt("chapterIndex")
        }
    }

    LaunchedEffect(playingEpisode, selectedDrama, pipMode) {
        if ((playingEpisode != null || selectedDrama != null || pipMode) && drawerState.currentValue == DrawerValue.Open) {
            scope.launch { drawerState.setValue(DrawerValue.Closed) }
        }
    }

    val episodeList = remember(episodes) {
        List(episodes.length()) { episodes.getJSONObject(it) }
    }

    val tabs = listOf(
        TabInfo("explore", "Explore", Icons.Default.Explore),
        TabInfo("more", "More Series", Icons.Default.GridView),
        TabInfo("myfavorite", "My Favorites", Icons.Default.Bookmarks),
        TabInfo("settings", "Settings", Icons.Default.Settings)
    )

    var isSidebarExpanded by remember { mutableStateOf(false) }
    val sidebarWidth by animateDpAsState(if (isSidebarExpanded) 240.dp else 80.dp)

    BackHandler(enabled = isSidebarExpanded || selectedDrama != null || playingEpisode != null) {
        if (isSidebarExpanded) {
            isSidebarExpanded = false
        } else if (playingEpisode != null) {
            viewModel.setPlayingEpisode(null)
        } else {
            viewModel.backToList()
            lastPlayedIndex = -1
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Netflix-Style Sidebar
        Box(
            modifier = Modifier
                .width(sidebarWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.dramatronplus),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    if (isSidebarExpanded) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "DramaThon",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Accent,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                tabs.forEach { tab ->
                    val isUpdateHighlight = tab.key == "settings" && updateAvailable != null
                    
                    Surface(
                        selected = currentTab == tab.key,
                        onClick = {
                            scope.launch {
                                searchText = ""
                                viewModel.loadTab(tab.key)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    isSidebarExpanded = true
                                }
                            },
                        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.05f),
                        colors = SelectableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Accent,
                            selectedContainerColor = Accent.copy(alpha = 0.5f),
                            focusedContentColor = Color.White,
                            selectedContentColor = Color.White
                        ),
                        shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (currentTab == tab.key) Color.White else TextSecondary
                            )
                            if (isSidebarExpanded) {
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = tab.label,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isUpdateHighlight) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.Red, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Main Content Area
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onFocusChanged {
                    if (it.isFocused) {
                        isSidebarExpanded = false
                    }
                },
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (playingEpisode == null) {
                    Column {
                        if (selectedDrama == null && currentTab != "settings" && !pipMode) {
                            // TV Search Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = searchText,
                                    onValueChange = {
                                        searchText = it
                                        if (it.isEmpty()) {
                                            scope.launch { viewModel.loadTab(currentTab) }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)),
                                    singleLine = true,
                                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        scope.launch { viewModel.search(searchText) }
                                    }),
                                    cursorBrush = SolidColor(Accent),
                                    decorationBox = { innerTextField ->
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                if (searchText.isEmpty()) {
                                                    Text("Search dramas...", color = TextSecondary, fontSize = 15.sp)
                                                }
                                                innerTextField()
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            if (selectedDrama != null) {
                                val bookId = selectedDrama!!.optLong("bookId")
                                DramaDetailView(
                                    detail = detail ?: selectedDrama!!,
                                    episodes = episodes,
                                    watchedEpisodes = watchedEpisodes,
                                    isUnlocked = isUnlocked,
                                    initialScrollIndex = lastPlayedIndex,
                                    isFavorite = favorites.containsKey(bookId),
                                    onEpisodeClick = { ep ->
                                        viewModel.setPlayingEpisode(ep)
                                    },
                                    onUnlock = { code, onResult ->
                                        viewModel.unlock(code, deviceId, context, onResult)
                                    },
                                    onFavoriteToggle = {
                                        viewModel.toggleFavorite(detail ?: selectedDrama!!, context)
                                    },
                                    onRefresh = {
                                        scope.launch {
                                            viewModel.selectDrama(selectedDrama!!, forceRefresh = true)
                                        }
                                    }
                                )
                            } else if (currentTab == "more" && searchText.isEmpty()) {
                                CategoryGrid(onCategoryClick = { category ->
                                    searchText = category
                                    scope.launch { viewModel.search(category) }
                                })
                            } else if (currentTab == "settings") {
                                SettingsScreen(viewModel)
                            } else {
                                DramaGrid(
                                    dramas = dramas,
                                    onDramaClick = { drama ->
                                        scope.launch { viewModel.selectDrama(drama) }
                                    },
                                    onLoadMore = { viewModel.loadMore() },
                                    isLoading = loading,
                                    isEndReached = isEndReached
                                )
                            }
                        }
                    }
                }

                if (loading) {
                    CustomProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                if (playingEpisode != null) {
                    VideoPlayer(
                        episode = playingEpisode!!,
                        episodes = episodeList,
                        watchedEpisodes = watchedEpisodes,
                        bookId = (detail ?: selectedDrama)?.optLong("bookId") ?: 0L,
                        isUnlocked = isUnlocked,
                        isInPipMode = pipMode,
                        onClose = { viewModel.setPlayingEpisode(null) },
                        onNextEpisode = { viewModel.setPlayingEpisode(it) },
                        onUnlock = { code, onResult ->
                            viewModel.unlock(code, deviceId, context, onResult)
                        }
                    )
                }

                if (!liveStatus || isVersionKilled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.9f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isVersionKilled) "This version is no longer supported.\nPlease update to the latest version." else "Service is temporarily unavailable.\nPlease try again later.",
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

data class TabInfo(val key: String, val label: String, val icon: ImageVector)

fun showUpdateNotification(context: Context, update: JSONObject) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        return
    }

    val channelId = "app_updates"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(channelId, "App Updates", NotificationManager.IMPORTANCE_DEFAULT)
    notificationManager.createNotificationChannel(channel)

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("tab", "settings")
    }
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.dramatronplus)
        .setContentTitle("Update Available")
        .setContentText("A new version (${update.optString("tag_name")}) is available.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    try {
        notificationManager.notify(1001, notification)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
