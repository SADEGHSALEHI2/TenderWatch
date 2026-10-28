package ir.tenderwatch.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import ir.tenderwatch.source.CdbSource

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(vm: MainViewModel, s: Screen.Web) {
    val ctx = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else vm.back()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.title) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") }
                },
                actions = {
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webView?.url ?: s.url)))
                    }) { Text("مرورگر") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        var injected = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                loading = false
                                // Fill the contract id into the cdb search form and submit it, once.
                                val id = s.contractId
                                if (id != null && !injected && url.startsWith(CdbSource.URL)) {
                                    injected = true
                                    val safe = id.replace("'", "")
                                    view.evaluateJavascript(
                                        "(function(){var i=document.querySelector('[name=\"${CdbSource.CONTRACT_ID}\"]');" +
                                            "var b=document.getElementById('btnSearch');" +
                                            "if(i&&b){i.value='$safe';b.click();}})();",
                                        null,
                                    )
                                }
                            }
                        }
                        loadUrl(s.url)
                        webView = this
                    }
                },
            )
        }
    }
}
