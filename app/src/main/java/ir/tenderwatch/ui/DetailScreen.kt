package ir.tenderwatch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.tenderwatch.model.Kind
import ir.tenderwatch.model.Notice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: MainViewModel, id: String) {
    val all by vm.items.collectAsState()
    val n = all.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    LaunchedEffect(id) { vm.markRead(id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (n?.kind == Kind.CONTRACT) "جزئیات قرارداد" else "جزئیات مناقصه") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") }
                },
                actions = {
                    if (n != null) {
                        IconButton(onClick = { vm.toggleStar(n.id) }) {
                            if (n.starred) Icon(Icons.Filled.Star, "حذف نشان", tint = Color(0xFFFFA000))
                            else Icon(Icons.Outlined.Star, "نشان کردن")
                        }
                        IconButton(onClick = { share(ctx, n) }) { Icon(Icons.Filled.Share, "اشتراک‌گذاری") }
                    }
                },
            )
        },
    ) { pad ->
        if (n == null) {
            Text("این مورد پیدا نشد.", Modifier.padding(pad).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(n.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("منبع: ${n.source}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((label, value) in n.details) {
                        Row {
                            Text(
                                "$label:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(130.dp),
                            )
                            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                    if (n.matched.isNotEmpty()) {
                        Row {
                            Text("کلمات منطبق:", fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp))
                            Text(n.matched.joinToString("، "), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            HorizontalDivider()
            if (n.kind == Kind.TENDER) {
                Text(
                    "اسناد و جزئیات کامل در ستاد پشت کد امنیتی است. در صفحه ستاد روی شماره فراخوان بزنید و کد امنیتی را وارد کنید. " +
                        "برای شرکت در مناقصه باید در ستاد ثبت‌نام و توکن امضای الکترونیکی داشته باشید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { vm.open(Screen.Web(n.url, "ستاد", null)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("مشاهده در سامانه ستاد") }
            } else {
                Text(
                    "طرف قرارداد همان برنده/مجری است؛ برای فروش لوله و اتصالات می‌توانید با او تماس بگیرید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { vm.open(Screen.Web(n.url, "پایگاه قراردادها", n.number)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("مشاهده در پایگاه قراردادها") }
            }
            OutlinedButton(
                onClick = { copy(ctx, n.number) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (n.kind == Kind.TENDER) "کپی شماره فراخوان" else "کپی شناسه قرارداد") }
        }
    }
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("number", text))
    Toast.makeText(ctx, "کپی شد: $text", Toast.LENGTH_SHORT).show()
}

private fun share(ctx: Context, n: Notice) {
    val text = buildString {
        appendLine(n.title)
        n.details.forEach { (k, v) -> if (k != "عنوان" && k != "عنوان قرارداد") appendLine("$k: $v") }
        append("منبع: ${n.source}")
    }
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
}
