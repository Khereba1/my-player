@file:OptIn(ExperimentalMaterial3Api::class)

package com.myplayer.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun LibraryScreen(
    vm: PlayerViewModel,
    dark: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    var openPlaylist by rememberSaveable { mutableStateOf<String?>(null) }
    var addTarget by remember { mutableStateOf<MediaEntry?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    BackHandler(enabled = openPlaylist != null) { openPlaylist = null }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "مشغّلي",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.toggleTheme(dark) }) {
                    Icon(painterResource(R.drawable.ic_theme), contentDescription = "تغيير الثيم")
                }
            }
        },
        bottomBar = { MiniPlayer(vm) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val titles = listOf("الصوت", "الفيديو", "القوائم")
            TabRow(selectedTabIndex = tab) {
                titles.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i,
                        onClick = {
                            tab = i
                            folder = null
                            openPlaylist = null
                        },
                        text = { Text(t) },
                    )
                }
            }

            if (!hasPermission) {
                PermissionNeeded(onRequestPermission)
            } else if (tab == 2) {
                PlaylistsTab(
                    vm = vm,
                    openPlaylist = openPlaylist,
                    onOpen = { openPlaylist = it },
                    onCreate = { showCreate = true },
                )
            } else {
                MediaListTab(
                    vm = vm,
                    source = if (tab == 0) vm.audio else vm.videos,
                    query = query,
                    onQuery = { query = it },
                    folder = folder,
                    onFolder = { folder = it },
                    sortByName = sortByName,
                    onToggleSort = { sortByName = !sortByName },
                    onAddToPlaylist = { addTarget = it },
                )
            }
        }
    }

    // نافذة "إضافة إلى قائمة تشغيل"
    val target = addTarget
    if (target != null && !showCreate) {
        AlertDialog(
            onDismissRequest = { addTarget = null },
            title = { Text("إضافة إلى قائمة تشغيل") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    vm.playlists.keys.forEach { name ->
                        TextButton(
                            onClick = {
                                vm.addToPlaylist(name, target.key)
                                addTarget = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(name) }
                    }
                    TextButton(
                        onClick = { showCreate = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("+ قائمة جديدة") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addTarget = null }) { Text("إلغاء") } },
        )
    }

    // نافذة "قائمة جديدة"
    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("قائمة تشغيل جديدة") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("اسم القائمة") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = name.trim()
                        if (n.isNotEmpty()) {
                            vm.createPlaylist(n)
                            val pending = addTarget
                            if (pending != null) vm.addToPlaylist(n, pending.key)
                        }
                        addTarget = null
                        showCreate = false
                    },
                ) { Text("إنشاء") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun PermissionNeeded(onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "محتاج إذن الوصول للملفات عشان أعرض الصوت والفيديو اللي على جهازك.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequestPermission) { Text("السماح بالوصول") }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            },
        ) { Text("فتح إعدادات التطبيق") }
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaListTab(
    vm: PlayerViewModel,
    source: List<MediaEntry>,
    query: String,
    onQuery: (String) -> Unit,
    folder: String?,
    onFolder: (String?) -> Unit,
    sortByName: Boolean,
    onToggleSort: () -> Unit,
    onAddToPlaylist: (MediaEntry) -> Unit,
) {
    val folders = remember(source) { source.map { it.folder }.distinct().sorted() }
    val activeFolder = if (folder != null && folders.contains(folder)) folder else null
    val shown = remember(source, query, activeFolder, sortByName) {
        val filtered = source.filter { e ->
            (activeFolder == null || e.folder == activeFolder) &&
                (query.isBlank() ||
                    e.title.contains(query, ignoreCase = true) ||
                    e.subtitle.contains(query, ignoreCase = true))
        }
        if (sortByName) filtered.sortedBy { it.title.lowercase() } else filtered
    }
    val currentId = vm.currentItem?.mediaId

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("ابحث بالاسم...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        if (folders.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = activeFolder == null,
                        onClick = { onFolder(null) },
                        label = { Text("الكل") },
                    )
                }
                items(folders) { f ->
                    FilterChip(
                        selected = activeFolder == f,
                        onClick = { onFolder(f) },
                        label = { Text(f) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${shown.size} عنصر",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToggleSort) { Text(if (sortByName) "الاسم" else "الأحدث") }
            TextButton(
                onClick = { vm.play(shown, 0) },
                enabled = shown.isNotEmpty(),
            ) { Text("تشغيل الكل") }
            TextButton(
                onClick = { vm.play(shown.shuffled(), 0) },
                enabled = shown.isNotEmpty(),
            ) { Text("عشوائي") }
        }

        if (vm.loading && source.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (shown.isEmpty()) {
            EmptyText("مفيش ملفات هنا.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(shown, key = { _, e -> e.key }) { index, e ->
                    MediaRow(
                        entry = e,
                        isCurrent = currentId == e.key,
                        onClick = {
                            vm.play(shown, index)
                            if (e.isVideo) vm.showNowPlaying = true
                        },
                        menu = listOf(
                            "تشغيل بعده" to { vm.playNext(e) },
                            "إضافة لقائمة الانتظار" to { vm.addToQueue(e) },
                            "إضافة لقائمة تشغيل" to { onAddToPlaylist(e) },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    vm: PlayerViewModel,
    openPlaylist: String?,
    onOpen: (String?) -> Unit,
    onCreate: () -> Unit,
) {
    val byKey = remember(vm.audio, vm.videos) { (vm.audio + vm.videos).associateBy { it.key } }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    if (openPlaylist == null || !vm.playlists.containsKey(openPlaylist)) {
        Column(Modifier.fillMaxSize()) {
            Button(onClick = onCreate, modifier = Modifier.padding(12.dp)) {
                Text("+ قائمة تشغيل جديدة")
            }
            if (vm.playlists.isEmpty()) {
                EmptyText("لسه معندكش قوائم تشغيل. اعمل واحدة، وبعدين ضيف أي ملف ليها من القائمة (⋮) اللي جنب كل ملف.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.playlists.keys.toList()) { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(name) }
                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_music),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${vm.playlists[name]?.size ?: 0} عنصر",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { deleteTarget = name }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = "حذف القائمة")
                            }
                        }
                    }
                }
            }
        }
    } else {
        val playlistName: String = openPlaylist
        val keys = vm.playlists[playlistName] ?: emptyList()
        val entries = keys.mapNotNull { byKey[it] }
        val currentId = vm.currentItem?.mediaId
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onOpen(null) }) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "رجوع")
                }
                Text(
                    text = playlistName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { vm.play(entries, 0) },
                    enabled = entries.isNotEmpty(),
                ) { Text("تشغيل الكل") }
            }
            if (entries.isEmpty()) {
                EmptyText("القائمة فاضية. ضيف ملفات ليها من القائمة (⋮) اللي جنب أي ملف.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(entries, key = { _, e -> e.key }) { index, e ->
                        MediaRow(
                            entry = e,
                            isCurrent = currentId == e.key,
                            onClick = {
                                vm.play(entries, index)
                                if (e.isVideo) vm.showNowPlaying = true
                            },
                            menu = listOf(
                                "تشغيل بعده" to { vm.playNext(e) },
                                "حذف من القائمة" to { vm.removeFromPlaylist(playlistName, e.key) },
                            ),
                        )
                    }
                }
            }
        }
    }

    val toDelete = deleteTarget
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف القائمة؟") },
            text = { Text("هتتحذف القائمة \"$toDelete\" (الملفات نفسها مش هتتمسح).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deletePlaylist(toDelete)
                        deleteTarget = null
                    },
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun MediaRow(
    entry: MediaEntry,
    isCurrent: Boolean,
    onClick: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
) {
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isCurrent) cs.primary else cs.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (entry.isVideo) R.drawable.ic_video else R.drawable.ic_music),
                contentDescription = null,
                tint = if (isCurrent) cs.onPrimary else cs.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) cs.primary else cs.onSurface,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = entry.subtitle + " • " + formatTime(entry.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(painterResource(R.drawable.ic_more), contentDescription = "المزيد")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                menu.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.first) },
                        onClick = {
                            expanded = false
                            item.second()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(vm: PlayerViewModel) {
    val item = vm.currentItem ?: return
    val cs = MaterialTheme.colorScheme
    val meta = item.mediaMetadata
    val isVideo = meta.extras?.getBoolean(MediaEntry.EXTRA_VIDEO) == true

    Surface(
        tonalElevation = 4.dp,
        color = cs.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { vm.showNowPlaying = true },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (isVideo) R.drawable.ic_video else R.drawable.ic_music),
                    contentDescription = null,
                    tint = cs.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = meta.title?.toString().orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = meta.artist?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.previous() }) {
                        Icon(painterResource(R.drawable.ic_prev), contentDescription = "السابق")
                    }
                    IconButton(onClick = { vm.togglePlay() }) {
                        Icon(
                            painter = painterResource(if (vm.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = if (vm.isPlaying) "إيقاف مؤقت" else "تشغيل",
                        )
                    }
                    IconButton(onClick = { vm.next() }) {
                        Icon(painterResource(R.drawable.ic_next), contentDescription = "التالي")
                    }
                }
            }
        }
    }
}
