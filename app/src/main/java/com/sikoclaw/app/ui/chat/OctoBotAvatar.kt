package com.sikoclaw.app.ui.chat

import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import com.sikoclaw.app.agent.AgentAnimationState

@Composable
fun OctoBotAvatar(size: Dp, modifier: Modifier = Modifier, description: String = "OctoBot") {
    val motion by AgentAnimationState.state.collectAsState()
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.size(size).clip(CircleShape),
        factory = {
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = description
            }
        },
        update = { view ->
            view.setImageResource(AgentAnimationState.drawable(motion))
            (view.drawable as? AnimatedImageDrawable)?.start()
        },
    )
}
