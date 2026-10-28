package ir.tenderwatch.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.tenderwatch.data.Defaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val p = vm.prefs
    val ctx = LocalContext.current
    var tenderKw by remember { mutableStateOf(p.tenderKeywords.joinToString("\n")) }
    var contractKw by remember { mutableStateOf(p.contractTerms.joinToString("\n")) }
    var excludes by remember { mutableStateOf(p.excludes.joinToString("\n")) }
    var interval by remember { mutableIntStateOf(p.intervalHours) }
    var contractsEvery by remember { mutableIntStateOf(p.contractsEveryHours) }
    var wifiOnly by remember { mutableStateOf(p.wifiOnly) }

    fun lines(s: String) = s.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("کلمات کلیدی مناقصات ستاد (هر خط یک کلمه)")
            Hint("در عنوان، شرح و نام دستگاه همه مناقصه‌های ستاد جستجو می‌شود. فاصله و نیم‌فاصله و «ي/ی» مهم نیست.")
            OutlinedTextField(tenderKw, { tenderKw = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp))
            TextButton(onClick = { tenderKw = Defaults.TENDER_KEYWORDS.joinToString("\n") }) { Text("بازگشت به پیش‌فرض") }

            Section("عبارت‌های جستجوی قراردادها (هر خط یک عبارت)")
            Hint("هر عبارت یک جستجوی جدا در پایگاه قراردادها است و حجم اینترنت زیادی مصرف می‌کند (حدود ۷ مگابایت برای هر عبارت)؛ فهرست را کوتاه نگه دارید.")
            OutlinedTextField(contractKw, { contractKw = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp))
            TextButton(onClick = { contractKw = Defaults.CONTRACT_TERMS.joinToString("\n") }) { Text("بازگشت به پیش‌فرض") }

            Section("کلمات استثنا (هر خط یک کلمه)")
            Hint("مواردی که این کلمات را داشته باشند نمایش داده نمی‌شوند.")
            OutlinedTextField(excludes, { excludes = it }, Modifier.fillMaxWidth().heightIn(min = 80.dp))

            HorizontalDivider()
            Section("بررسی خودکار مناقصات ستاد هر")
            Choices(listOf(1, 3, 6, 12, 24), interval) { interval = it }
            Section("بررسی خودکار قراردادها هر")
            Choices(listOf(12, 24, 48, 72), contractsEvery) { contractsEvery = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("فقط با وای‌فای", Modifier.weight(1f))
                Switch(wifiOnly, { wifiOnly = it })
            }

            Button(
                onClick = {
                    vm.saveSettings(lines(tenderKw), lines(contractKw), lines(excludes), interval, contractsEvery, wifiOnly)
                    Toast.makeText(ctx, "ذخیره شد", Toast.LENGTH_SHORT).show()
                    vm.back()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیره") }

            HorizontalDivider()
            OutlinedButton(
                onClick = {
                    vm.sync(forceContracts = true)
                    vm.back()
                },
                enabled = !vm.syncing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("بروزرسانی کامل همین حالا (ستاد + قراردادها)") }
            OutlinedButton(
                onClick = {
                    p.resetCursors()
                    Toast.makeText(ctx, "در بروزرسانی بعدی همه موارد دوباره بررسی می‌شوند", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("بررسی دوباره همه موارد از ابتدا") }
            if (vm.status.isNotEmpty()) Hint(vm.status)
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choices(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (h in options) {
            FilterChip(selected = h == selected, onClick = { onSelect(h) }, label = { Text("$h ساعت") })
        }
    }
}
