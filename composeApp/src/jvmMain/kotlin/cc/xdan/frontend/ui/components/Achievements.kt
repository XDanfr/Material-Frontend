package cc.xdan.frontend.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import cc.xdan.frontend.data.AppPaths
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.ui.theme.LocalApp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A remote image cached on disk under cache/remote (badges and the like). */
@Composable
fun rememberRemoteImage(url: String?, maxDim: Int = 128): State<ImageBitmap?> {
    val app = LocalApp.current
    return produceState<ImageBitmap?>(null, url) {
        val u = url ?: return@produceState
        val f = File(AppPaths.cache, "remote/" + u.substringAfter("://").replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val ok = withContext(Dispatchers.IO) {
            if (f.isFile && f.length() > 0) true else runCatching {
                val r = app.http.get(u)
                if (!r.status.isSuccess()) false else { f.parentFile.mkdirs(); f.writeBytes(r.bodyAsBytes()); true }
            }.getOrDefault(false)
        }
        if (ok) value = ImageCache.load(f.path, maxDim)
    }
}

/** RetroAchievements summary for a game page: count, points, a bar and the first badges. Empty until loaded / when not applicable. */
@Composable
fun AchievementsStrip(game: Game, big: Boolean, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    LaunchedEffect(game.id) { app.loadAchievements(game) }
    val all by app.achievements.collectAsState()
    val p = all[game.id] ?: return
    if (p.total == 0) return
    val badge: Dp = if (big) 48.dp else 36.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.EmojiEvents, null, tint = MaterialTheme.colorScheme.tertiary)
            Text("${p.earned} of ${p.total} achievements · ${p.points}/${p.totalPoints} points",
                style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(progress = { p.earned / p.total.toFloat() }, modifier = Modifier.width(if (big) 420.dp else 280.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            p.achievements.take(if (big) 12 else 8).forEach { a ->
                val img by rememberRemoteImage(a.badgeUrl, 96)
                img?.let { Image(it, a.title, Modifier.size(badge).clip(RoundedCornerShape(10.dp))) }
            }
        }
    }
}
