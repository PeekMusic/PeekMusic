package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.R
import com.metrolist.music.constants.BlockedArtistsKey
import com.metrolist.music.models.BlockedArtist
import com.metrolist.music.models.BlockedArtistManager
import androidx.compose.material3.TopAppBar

import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedArtistsSettings(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val blockedArtistsFlow = remember(context) {
        context.dataStore.data.map { prefs ->
            val json = prefs[BlockedArtistsKey]
            val all = BlockedArtistManager.parse(json)
            val now = System.currentTimeMillis()
            // Keep all, but filter out expired ones and save them out
            val valid = all.filter { it.blockExpiryTime == 0L || it.blockExpiryTime > now }
            if (valid.size != all.size) {
                coroutineScope.launch {
                    context.dataStore.edit { editPrefs ->
                        editPrefs[BlockedArtistsKey] = BlockedArtistManager.encode(valid)
                    }
                }
            }
            valid
        }
    }
    
    val blockedArtists by blockedArtistsFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blocked_artists)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.blocked_artists_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            if (blockedArtists.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Keine Künstler blockiert",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(blockedArtists) { artist ->
                    BlockedArtistItem(
                        artist = artist,
                        onUnblock = {
                            coroutineScope.launch {
                                context.dataStore.edit { prefs ->
                                    val current = BlockedArtistManager.parse(prefs[BlockedArtistsKey])
                                    val newList = current.filterNot { it.id == artist.id }
                                    prefs[BlockedArtistsKey] = BlockedArtistManager.encode(newList)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BlockedArtistItem(
    artist: BlockedArtist,
    onUnblock: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium
            )
            
            val expiryText = if (artist.blockExpiryTime == 0L) {
                stringResource(R.string.block_forever)
            } else {
                val dateStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(java.util.Date(artist.blockExpiryTime))
                stringResource(R.string.blocked_until, dateStr)
            }
            
            Text(
                text = expiryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        TextButton(onClick = onUnblock) {
            Text(text = stringResource(R.string.unblock))
        }
    }
}
