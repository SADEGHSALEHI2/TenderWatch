package ir.tenderwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.tenderwatch.model.Kind
import ir.tenderwatch.model.Notice
import ir.tenderwatch.util.Fa
import ir.tenderwatch.util.Jalali

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val all by vm.items.collectAsState()
    val now = remember { Jalali.now() }
    val tenders = remember(all) { all.filter { it.kind == Kind.TENDER } }
    val contracts = remember(all) { all.filter { it.kind == Kind.CONTRACT } }
    val shown = remember(all, vm.tab, vm.query, vm.onlyOpen) {
        val base = when (vm.tab) {
            0 -> tenders
            1 -> contracts
            else -> all.filter { it.starred }
        }
        val q = Fa.normalize(vm.query)
        base.filter { n ->
            (q.isEmpty() || Fa.normalize(n.searchText()).contains(q)) &&
                (!vm.onlyOpen || n.kind != Kind.TENDER || n.deadline.isEmpty() || n.deadline >= now)
        }.sortedByDescending { it.date }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مناقصه‌یاب لوله و اتصالات") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                actions = {
                    if (vm.syncing) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    } else {
                        // On the contracts tab the refresh also forces the (heavier) contracts search.
                        IconButton(onClick = { vm.sync(forceContracts = vm.tab == 1) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "بروزرسانی")
                        }
                    }
                    IconButton(onClick = { vm.open(Screen.Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "تنظیمات")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = vm.tab) {
                Tab(vm.tab == 0, { vm.tab = 0 }, text = { Text("مناقصات (${tenders.size})") })
                Tab(vm.tab == 1, { vm.tab = 1 }, text = { Text("قراردادها (${contracts.size})") })
                Tab(vm.tab == 2, { vm.tab = 2 }, text = { Text("نشان‌شده") })
            }
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("جستجو: عنوان، دستگاه، استان، برنده...") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vm.tab == 0) {
                    FilterChip(
                        selected = vm.onlyOpen,
                        onClick = { vm.onlyOpen = !vm.onlyOpen },
                        label = { Text("فقط مهلت‌دار") },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (vm.tab < 2 && shown.any { it.isNew }) {
                    TextButton(onClick = { vm.store.markAllRead(if (vm.tab == 0) Kind.TENDER else Kind.CONTRACT) }) {
                        Text("همه خوانده شد")
                    }
                }
            }
            val statusText = vm.progress.ifEmpty { vm.status }
            if (statusText.isNotEmpty()) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (all.isEmpty()) "هنوز موردی نیست. دکمه بروزرسانی بالای صفحه را بزنید.\nاولین بروزرسانی چند دقیقه طول می‌کشد."
                        else "موردی با این فیلتر پیدا نشد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { n ->
                        NoticeCard(n, now) { vm.open(Screen.Detail(n.id)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeCard(n: Notice, now: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (n.isNew) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (n.isNew) {
                    Text(
                        "جدید",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .background(Color(0xFF1565C0), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                Text(
                    n.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (n.starred) Icon(Icons.Filled.Star, null, tint = Color(0xFFFFA000), modifier = Modifier.size(18.dp))
            }
            if (n.org.isNotEmpty()) Line("کارفرما", n.org)
            if (n.kind == Kind.CONTRACT && n.party.isNotEmpty()) Line("طرف قرارداد", n.party, MaterialTheme.colorScheme.secondary)
            if (n.place.isNotEmpty()) Line("محل", n.place)
            if (n.kind == Kind.TENDER) {
                Line("انتشار", n.date)
                if (n.deadline.isNotEmpty()) {
                    val open = n.deadline >= now
                    Line("مهلت", n.deadline + if (open) "" else " (گذشته)", if (open) MaterialTheme.colorScheme.tertiary else Color.Gray)
                }
            } else {
                Line("تاریخ عقد", n.date)
                if (n.amount.isNotEmpty()) Line("مبلغ", "${n.amount} ریال  (${Fa.rialHuman(n.amount)})")
            }
            if (n.matched.isNotEmpty()) {
                Text(
                    "کلمات: " + n.matched.joinToString("، "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, color: Color = Color.Unspecified) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}
