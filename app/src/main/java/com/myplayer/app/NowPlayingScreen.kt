package com.myplayer.app

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(vm: PlayerViewModel) {
    BackHandler { vm.showNowPlaying = false }

    val item = vm.currentItem ?: return
    val meta = item.mediaMetadata
    val title = meta.title?.toString().orEmpty()
    val sub = meta.artist?.toString().orEmpty()
    val isVideo = meta.extras?.getBoolean(MediaEntry.EXTRA_VIDEO) == true
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val headerText = if (isVideo) title else "يعمل الآن"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                MediaArea(
                    vm = vm,
                    isVideo = isVideo,
                    title = title,
                    sub = sub,
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight(),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    NowPlayingHeader(vm, headerText)
                    Controls(vm)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                NowPlayingHeader(vm, headerText)
                MediaArea(
                    vm = vm,
                    isVideo = isVideo,
                    title = title,
                    sub = sub,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                Controls(vm, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun NowPlayingHeader(vm: PlayerViewModel, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { vm.showNowPlaying = false }) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = "رجوع")
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MediaArea(
    vm: PlayerViewModel,
    isVideo: Boolean,
    title: String,
    sub: String,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    if (isVideo) {
        VideoSurface(
            player = vm.controller,
            modifier = modifier.background(Color.Black),
        )
    } else {
        Column(
            modifier = modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_music),
                    contentDescription = null,
                    tint = cs.onPrimaryContainer,
                    modifier = Modifier.size(88.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoSurface(player: Player?, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
            }
        },
        update = { view -> view.player = player },
        onRelease = { view -> view.player = null },
    )
}

@Composable
private fun Controls(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    // أزرار التحكم والشريط بتفضل بنفس ترتيب المشغلات المعتاد (يسار → يمين)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SeekBar(vm)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shuffle),
                        contentDescription = "عشوائي",
                        tint = if (vm.shuffleOn) cs.primary else cs.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { vm.previous() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_prev),
                        contentDescription = "السابق",
                        modifier = Modifier.size(32.dp),
                    )
                }
                FilledIconButton(
                    onClick = { vm.togglePlay() },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        painter = painterResource(if (vm.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (vm.isPlaying) "إيقاف مؤقت" else "تشغيل",
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(
                    onClick = { vm.next() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "التالي",
                        modifier = Modifier.size(32.dp),
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    IconButton(onClick = { vm.cycleRepeat() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_repeat),
                            contentDescription = "تكرار",
                            tint = if (vm.repeatMode != Player.REPEAT_MODE_OFF) cs.primary else cs.onSurfaceVariant,
                        )
                    }
                    if (vm.repeatMode == Player.REPEAT_MODE_ONE) {
                        Text(
                            text = "1",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { vm.seekBy(-10_000L) }) { Text("-10ث") }
                TextButton(onClick = { vm.cycleSpeed() }) { Text(speedLabel(vm.speed)) }
                TextButton(onClick = { vm.cycleSleepTimer() }) {
                    Text(if (vm.sleepMinutes == 0) "مؤقت" else "نوم ${vm.sleepMinutes}د")
                }
                TextButton(onClick = { vm.seekBy(10_000L) }) { Text("+10ث") }
            }
        }
    }
}

@Composable
private fun SeekBar(vm: PlayerViewModel) {
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(vm.controller) {
        while (true) {
            if (!dragging) position = vm.position()
            delay(300L)
        }
    }

    val duration = vm.durationMs
    val upper = maxOf(duration, 1L).toFloat()
    val shown = if (dragging) dragValue else position.toFloat()

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown.coerceIn(0f, upper),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                vm.seekTo(dragValue.toLong())
                position = dragValue.toLong()
                dragging = false
            },
            valueRange = 0f..upper,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(shown.toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
