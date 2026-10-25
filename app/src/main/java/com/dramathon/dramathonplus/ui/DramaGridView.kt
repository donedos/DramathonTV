package com.dramathon.dramathonplus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dramathon.dramathonplus.ui.theme.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DramaGrid(
    dramas: JSONArray, 
    onDramaClick: (JSONObject, Int) -> Unit,
    onLoadMore: () -> Unit = {},
    isLoading: Boolean = false,
    isEndReached: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState(),
    initialFocusIndex: Int = -1
) {
    val list = remember(dramas) {
        List(dramas.length()) { dramas.getJSONObject(it) }
    }
    
    val focusRequesters = remember(list.size) { 
        List(list.size) { FocusRequester() } 
    }
    
    LaunchedEffect(initialFocusIndex) {
        if (initialFocusIndex >= 0 && initialFocusIndex < list.size) {
            gridState.scrollToItem(initialFocusIndex)
            delay(100)
            try {
                focusRequesters[initialFocusIndex].requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(gridState, list.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: -1
                if (lastVisibleIndex >= list.size - 10 && list.size >= 20 && !isLoading && !isEndReached) {
                    onLoadMore()
                }
            }
    }

    if (list.isEmpty() && !isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No dramas found", color = TextSecondary)
        }
    } else {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(180.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            itemsIndexed(list) { index, drama ->
                DramaCard(
                    drama = drama, 
                    onClick = { onDramaClick(it, index) },
                    modifier = Modifier.focusRequester(focusRequesters[index])
                )
            }
            
            if (isLoading && list.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CustomProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DramaCard(
    drama: JSONObject, 
    onClick: (JSONObject) -> Unit,
    modifier: Modifier = Modifier
) {
    val cover = drama.optString("coverWap").ifEmpty { 
        drama.optString("coverUrl").ifEmpty { 
            drama.optString("cover") 
        } 
    }
    
    val context = LocalContext.current
    val imageRequest = remember(cover) {
        ImageRequest.Builder(context)
            .data(cover)
            .addHeader("User-Agent", "okhttp/4.12.0")
            .build()
    }
    
    Surface(
        onClick = { onClick(drama) },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Box {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier.aspectRatio(3f/4f).fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
                
                val epCount = drama.optInt("chapterCount", 0)
                if (epCount > 0) {
                    Surface(
                        colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Text(
                            text = "$epCount eps",
                            color = Accent,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = drama.optString("bookName"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val proto = drama.optString("protagonist")
                if (proto.isNotEmpty()) {
                    Text(
                        text = proto, 
                        color = TextSecondary, 
                        fontSize = 12.sp, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
