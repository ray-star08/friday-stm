package com.gynda.fridaystm.ui.component

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Embeds a YouTube video by its 11-char [videoId] using the iframe player inside a
 * [WebView] — no Google Maps/YouTube SDK, so no billing or extra dependency
 * (task constraint). `AndroidView` is used only for this framework view, per
 * SKILL.md's allowance for CameraX/map/web host views.
 *
 * JavaScript is enabled (the iframe player requires it) but the WebView loads only
 * our own generated embed HTML pointing at `youtube-nocookie.com`; it does not
 * expose a JS bridge, so the attack surface is just YouTube's own player.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                setBackgroundColor(0)
            }
        },
        update = { webView ->
            webView.loadData(embedHtml(videoId), "text/html", "utf-8")
        },
    )
}

/**
 * Minimal responsive iframe embed. The player fills the WebView; the 16:9 ratio is
 * enforced by the composable modifier above. `youtube-nocookie.com` avoids setting
 * tracking cookies until playback starts.
 */
private fun embedHtml(videoId: String): String = """
    <!DOCTYPE html>
    <html>
      <head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
      <body style="margin:0;padding:0;background:#000;">
        <iframe width="100%" height="100%"
          src="https://www.youtube-nocookie.com/embed/$videoId"
          frameborder="0"
          allow="accelerometer; encrypted-media; gyroscope; picture-in-picture"
          allowfullscreen
          style="position:absolute;top:0;left:0;width:100%;height:100%;">
        </iframe>
      </body>
    </html>
""".trimIndent()
