package com.dramathon.dramathonplus.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dramathon.dramathonplus.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DramaDetailView(
    detail: JSONObject, 
    episodes: JSONArray, 
    watchedEpisodes: Set<String> = emptySet(),
    isUnlocked: Boolean = false,
    initialScrollIndex: Int = -1,
    isFavorite: Boolean = false,
    episodesError: String? = null,
    onEpisodeClick: (JSONObject) -> Unit,
    onUnlock: (String, (Boolean, String?) -> Unit) -> Unit,
    onFavoriteToggle: () -> Unit,
    onRefresh: () -> Unit
) {
    val list = remember(episodes) {
        List(episodes.length()) { episodes.getJSONObject(it) }
    }
    val bookId = detail.optLong("bookId")
    val cover = detail.optString("coverWap").ifEmpty { 
        detail.optString("coverUrl").ifEmpty { 
            detail.optString("cover") 
        } 
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val thumbnailFocusRequester = remember { FocusRequester() }
    val episodesHeaderFocusRequester = remember { FocusRequester() }
    val episodeFocusRequesters = remember(list.size) { 
        List(list.size) { FocusRequester() } 
    }
    
    var showCodeEntryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (initialScrollIndex < 0) {
            thumbnailFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(initialScrollIndex) {
        if (initialScrollIndex >= 0 && initialScrollIndex < list.size) {
            // Header is item 0, episodes start at 1
            listState.scrollToItem(initialScrollIndex + 1)
            // Small delay to ensure the item is in composition after scroll
            delay(100)
            try {
                episodeFocusRequesters[initialScrollIndex].requestFocus()
            } catch (e: Exception) {
                // Ignore if requester not yet attached
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.imePadding(),
            contentPadding = PaddingValues(24.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val imageRequest = remember(cover) {
                        ImageRequest.Builder(context)
                            .data(cover)
                            .addHeader("User-Agent", "okhttp/4.12.0")
                            .build()
                    }
                    Surface(
                        onClick = { },
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(2.dp, Color.Transparent)),
                            focusedBorder = Border(BorderStroke(2.dp, Accent))
                        ),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.focusRequester(thumbnailFocusRequester)
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            modifier = Modifier.width(240.dp).aspectRatio(3f/4f),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = detail.optString("bookName"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "${detail.optInt("chapterCount")} episodes", color = TextSecondary, fontSize = 16.sp)
                        val proto = detail.optString("protagonist")
                        if (proto.isNotEmpty()) {
                            Text(text = "Protagonist: $proto", color = TextSecondary, fontSize = 16.sp)
                        }
                        
                        val tags = detail.optJSONArray("tags")
                        if (tags != null && tags.length() > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                               for (i in 0 until tags.length()) {
                                   Surface(
                                       colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                       shape = RoundedCornerShape(12.dp),
                                       border = Border(BorderStroke(1.dp, Color.Gray))
                                   ) {
                                       Text(
                                           text = tags.getString(i), 
                                           fontSize = 12.sp, 
                                           modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), 
                                           color = TextSecondary
                                       )
                                   }
                               }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = detail.optString("introduction"), 
                    color = TextSecondary, 
                    fontSize = 16.sp, 
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Surface(
                    onClick = onRefresh,
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.focusRequester(episodesHeaderFocusRequester)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = "Episodes (${list.size})", 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Episodes",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (episodesError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = episodesError,
                        color = Accent,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (list.isEmpty()) {
                item {
                    if (episodesError != null) {
                        Column(
                            modifier = Modifier.padding(vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                onClick = onRefresh,
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Color.White.copy(alpha = 0.1f)
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Retry",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        Text("No episodes available or still loading...", color = TextSecondary, modifier = Modifier.padding(vertical = 20.dp))
                    }
                }
            } else {
                itemsIndexed(list) { index, ep ->
                    val isWatched = watchedEpisodes.contains("${bookId}_${ep.optInt("chapterIndex")}")
                    EpisodeItem(
                        ep = ep,
                        detail = detail,
                        isWatched = isWatched,
                        onClick = onEpisodeClick,
                        modifier = Modifier.focusRequester(episodeFocusRequesters[index])
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (!isUnlocked && detail.optInt("chapterCount") > list.size) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        UnlockSection(onClick = { showCodeEntryDialog = true })
                    }
                }
            }
        }
        
        // Favorite toggle at top right
        IconButton(
            onClick = onFavoriteToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .focusProperties {
                    left = thumbnailFocusRequester
                    down = episodesHeaderFocusRequester
                },
            colors = IconButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = Accent,
                focusedContentColor = Color.White
            )
        ) {
            Text(if (isFavorite) "❤️" else "🤍", fontSize = 24.sp)
        }

        if (showCodeEntryDialog) {
            CodeEntryDialog(
                onDismiss = { showCodeEntryDialog = false },
                onUnlock = onUnlock
            )
        }
    }
}

@Composable
fun UnlockSection(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.colors(containerColor = Accent),
        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Text("Unlock All Episodes", fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeItem(
    ep: JSONObject, 
    detail: JSONObject, 
    isWatched: Boolean, 
    onClick: (JSONObject) -> Unit,
    modifier: Modifier = Modifier
) {
    val img = ep.optString("chapterImg").ifEmpty {
        ep.optString("videoPic").ifEmpty {
            detail.optString("coverWap").ifEmpty {
                detail.optString("coverUrl").ifEmpty {
                    detail.optString("cover")
                }
            }
        }
    }
    val isLocked = ep.optBoolean("isLocked", false)
    val episodeError = ep.optString("playbackError")
    
    val context = LocalContext.current
    val imageRequest = remember(img) {
        ImageRequest.Builder(context)
            .data(img)
            .addHeader("User-Agent", "okhttp/4.12.0")
            .build()
    }
    
    Surface(
        onClick = { onClick(ep) },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                if (img.isNotEmpty()) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier.width(140.dp).height(80.dp).clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.width(140.dp).height(80.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No preview", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                if (isLocked) {
                    Surface(
                        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.7f)),
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.Center).size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🔒", fontSize = 14.sp)
                        }
                    }
                }
                if (isWatched) {
                    Surface(
                        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.6f)),
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.Center).size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("👁", fontSize = 16.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                val index = ep.optInt("chapterIndex")
                val title = ep.optString("chapterName")
                // Some providers already name the chapter "Episode N", the label
                // would only repeat the same text.
                val hasEpisodeLabel = !title.startsWith("Episode", ignoreCase = true)
                Text(
                    text = title.ifEmpty { "EP ${index + 1}" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isWatched) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface
                )
                if (episodeError.isNotEmpty() || hasEpisodeLabel) {
                    Text(
                        text = if (episodeError.isNotEmpty()) episodeError else "Episode ${index + 1}",
                        color = if (episodeError.isNotEmpty()) Accent else TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isWatched) {
                Text("Watched", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
