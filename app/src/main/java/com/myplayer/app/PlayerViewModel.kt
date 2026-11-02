package com.myplayer.app

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    // ---------- المكتبة ----------
    var audio by mutableStateOf<List<MediaEntry>>(emptyList())
        private set
    var videos by mutableStateOf<List<MediaEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    // ---------- قوائم التشغيل ----------
    var playlists by mutableStateOf<Map<String, List<String>>>(loadPlaylists())
        private set

    // ---------- الثيم: 0 حسب النظام، 1 فاتح، 2 داكن ----------
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0))
        private set

    // ---------- شاشة التشغيل ----------
    var showNowPlaying by mutableStateOf(false)

    // ---------- حالة المشغّل ----------
    var controller by mutableStateOf<MediaController?>(null)
        private set
    var currentItem by mutableStateOf<MediaItem?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var shuffleOn by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var sleepMinutes by mutableIntStateOf(0)
        private set

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingItem: MediaEntry? = null
    private var sleepJob: Job? = null

    private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }
    }

    init {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val c = future.get()
                    c.addListener(listener)
                    controller = c
                    sync(c)
                    val waiting = pendingItem
                    if (waiting != null) {
                        pendingItem = null
                        play(listOf(waiting), 0)
                    }
                } catch (e: Exception) {
                    // الاتصال فشل، التطبيق هيكمل من غير تحكم
                }
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    private fun sync(p: Player) {
        currentItem = p.currentMediaItem
        isPlaying = p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)
        shuffleOn = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        speed = p.playbackParameters.speed
        val d = p.duration
        durationMs = if (d == C.TIME_UNSET || d < 0L) 0L else d
    }

    override fun onCleared() {
        sleepJob?.cancel()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }

    // ================= التحكم في التشغيل =================

    fun play(list: List<MediaEntry>, startIndex: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        val maxQueue = 500
        val from = startIndex.coerceIn(0, list.lastIndex)
        val queue: List<MediaEntry>
        val start: Int
        if (list.size > maxQueue) {
            queue = list.subList(from, minOf(list.size, from + maxQueue))
            start = 0
        } else {
            queue = list
            start = from
        }
        c.setMediaItems(queue.map { it.toMediaItem() }, start, 0L)
        c.prepare()
        c.play()
    }

    fun playNext(entry: MediaEntry) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(entry), 0)
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, entry.toMediaItem())
        }
    }

    fun addToQueue(entry: MediaEntry) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(entry), 0)
        } else {
            c.addMediaItem(entry.toMediaItem())
        }
    }

    fun playExternal(uri: Uri, mime: String) {
        val video = mime.startsWith("video")
        val name = displayName(uri)
        val entry = MediaEntry(
            key = "x:$uri",
            uri = uri,
            title = name.substringBeforeLast('.'),
            subtitle = "ملف خارجي",
            folder = "",
            durationMs = 0L,
            isVideo = video,
        )
        if (video) showNowPlaying = true
        if (controller != null) {
            play(listOf(entry), 0)
        } else {
            pendingItem = entry
        }
    }

    private fun displayName(uri: Uri): String {
        val fallback = uri.lastPathSegment ?: "ملف"
        return try {
            val resolver = getApplication<Application>().contentResolver
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0L)
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val d = c.duration
        val upper = if (d == C.TIME_UNSET || d <= 0L) Long.MAX_VALUE else d
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0L, upper))
    }

    fun position(): Long {
        val p = controller?.currentPosition ?: 0L
        return if (p < 0L) 0L else p
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun cycleSpeed() {
        val c = controller ?: return
        val cur = c.playbackParameters.speed
        val idx = speeds.indexOfFirst { it > cur + 0.01f }
        val next = if (idx == -1) speeds.first() else speeds[idx]
        c.setPlaybackSpeed(next)
    }

    fun cycleSleepTimer() {
        val next = when (sleepMinutes) {
            0 -> 15
            15 -> 30
            30 -> 60
            else -> 0
        }
        sleepJob?.cancel()
        sleepMinutes = next
        if (next > 0) {
            sleepJob = viewModelScope.launch {
                delay(next * 60_000L)
                controller?.pause()
                sleepMinutes = 0
            }
        }
    }

    // ================= الثيم =================

    fun toggleTheme(currentlyDark: Boolean) {
        themeMode = if (currentlyDark) 1 else 2
        prefs.edit().putInt("theme", themeMode).apply()
    }

    // ================= المكتبة =================

    fun loadLibrary() {
        viewModelScope.launch {
            loading = true
            val a = withContext(Dispatchers.IO) { queryMedia(false) }
            val v = withContext(Dispatchers.IO) { queryMedia(true) }
            audio = a
            videos = v
            loading = false
        }
    }

    private fun queryMedia(video: Boolean): List<MediaEntry> {
        val resolver = getApplication<Application>().contentResolver
        val collection: Uri = if (video) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val columns = arrayListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        )
        if (!video) {
            columns.add(MediaStore.Audio.AudioColumns.ARTIST)
            columns.add(MediaStore.Audio.AudioColumns.IS_RINGTONE)
            columns.add(MediaStore.Audio.AudioColumns.IS_NOTIFICATION)
            columns.add(MediaStore.Audio.AudioColumns.IS_ALARM)
        }

        val result = ArrayList<MediaEntry>()
        try {
            val cursor = resolver.query(
                collection,
                columns.toTypedArray(),
                null,
                null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC",
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val durCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val bucketCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                var artistCol = -1
                var ringCol = -1
                var notifCol = -1
                var alarmCol = -1
                if (!video) {
                    artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST)
                    ringCol = c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.IS_RINGTONE)
                    notifCol = c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.IS_NOTIFICATION)
                    alarmCol = c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.IS_ALARM)
                }

                while (c.moveToNext()) {
                    val duration = c.getLong(durCol)
                    if (!video) {
                        if (c.getInt(ringCol) != 0 || c.getInt(notifCol) != 0 || c.getInt(alarmCol) != 0) continue
                        if (duration in 1L..2999L) continue
                    }

                    val id = c.getLong(idCol)
                    val rawTitle = c.getString(titleCol)
                    val fileName = c.getString(nameCol)
                    val bucketRaw = c.getString(bucketCol)
                    val bucket = if (bucketRaw.isNullOrBlank()) "أخرى" else bucketRaw
                    val title = if (!rawTitle.isNullOrBlank()) {
                        rawTitle
                    } else if (!fileName.isNullOrBlank()) {
                        fileName.substringBeforeLast('.')
                    } else {
                        "بدون عنوان"
                    }

                    var subtitle = bucket
                    if (artistCol >= 0) {
                        val artist = c.getString(artistCol)
                        if (!artist.isNullOrBlank() && artist != "<unknown>") subtitle = artist
                    }

                    result.add(
                        MediaEntry(
                            key = (if (video) "v:" else "a:") + id,
                            uri = ContentUris.withAppendedId(collection, id),
                            title = title,
                            subtitle = subtitle,
                            folder = bucket,
                            durationMs = duration,
                            isVideo = video,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            // مفيش إذن أو حصل خطأ، بنرجّع اللي اتجمع
        }
        return result
    }

    // ================= قوائم التشغيل =================

    private fun loadPlaylists(): Map<String, List<String>> {
        val raw = prefs.getString("playlists", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val map = LinkedHashMap<String, List<String>>()
            val names = obj.keys()
            while (names.hasNext()) {
                val name = names.next()
                val arr = obj.getJSONArray(name)
                val items = ArrayList<String>()
                for (i in 0 until arr.length()) items.add(arr.getString(i))
                map[name] = items
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun savePlaylists() {
        val obj = JSONObject()
        for ((name, keys) in playlists) {
            val arr = JSONArray()
            for (k in keys) arr.put(k)
            obj.put(name, arr)
        }
        prefs.edit().putString("playlists", obj.toString()).apply()
    }

    fun createPlaylist(name: String) {
        val n = name.trim()
        if (n.isEmpty() || playlists.containsKey(n)) return
        playlists = playlists + (n to emptyList<String>())
        savePlaylists()
    }

    fun deletePlaylist(name: String) {
        playlists = playlists - name
        savePlaylists()
    }

    fun addToPlaylist(name: String, key: String) {
        val current = playlists[name] ?: return
        if (key in current) return
        playlists = playlists + (name to (current + key))
        savePlaylists()
    }

    fun removeFromPlaylist(name: String, key: String) {
        val current = playlists[name] ?: return
        playlists = playlists + (name to current.filter { it != key })
        savePlaylists()
    }
}
