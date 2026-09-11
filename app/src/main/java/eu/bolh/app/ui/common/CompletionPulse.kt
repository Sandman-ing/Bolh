package eu.bolh.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.bolh.app.R

/**
 * Момент достижения цели.
 *
 * Цель закрывают раз в несколько месяцев, и это единственное место, где
 * приложению позволено обрадоваться. Держится меньше секунды и само зовёт
 * дальше: празднование, которое надо закрывать руками, превращается в помеху.
 */
@Composable
fun CompletionPulse(
    accent: Color,
    onFinished: () -> Unit,
) {
    val scale = remember { Animatable(0.6f) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(140))
        scale.animateTo(1.15f, tween(260))
        scale.animateTo(1f, tween(160))
        fade.animateTo(0f, tween(220))
        onFinished()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .size(120.dp)
                .clip(CircleShape)
                .background(accent),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}
