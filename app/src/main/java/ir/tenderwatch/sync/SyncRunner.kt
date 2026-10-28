package ir.tenderwatch.sync

import android.content.Context
import ir.tenderwatch.data.Prefs
import ir.tenderwatch.data.Store
import ir.tenderwatch.model.Notice
import ir.tenderwatch.net.Http
import ir.tenderwatch.net.describe
import ir.tenderwatch.source.CdbSource
import ir.tenderwatch.source.SetadSource
import ir.tenderwatch.util.Jalali
import ir.tenderwatch.util.KeywordMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SyncResult(
    val newTenders: List<Notice>,
    val newContracts: List<Notice>,
    val errors: List<String>,
)

/** One sync pass over all sources; shared by the background worker and the refresh button. */
object SyncRunner {
    private val mutex = Mutex()
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    suspend fun run(
        context: Context,
        forceContracts: Boolean,
        progress: (String) -> Unit = {},
    ): SyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val prefs = Prefs(context)
            val store = Store.get(context)
            val http = Http.create()
            val errors = mutableListOf<String>()
            val now = System.currentTimeMillis()

            val newTenders: List<Notice> = try {
                val cutoff = prefs.setadLastMax
                val all = SetadSource(http).fetch(cutoff, if (cutoff.isEmpty()) 40 else 10, progress)
                val matcher = KeywordMatcher(prefs.tenderKeywords, prefs.excludes)
                val matched = all.mapNotNull { n ->
                    val m = matcher.match(n.searchText())
                    if (m.isEmpty()) null else n.copy(matched = m)
                }
                all.maxOfOrNull { it.date }?.let { if (it > cutoff) prefs.setadLastMax = it }
                store.addNew(matched)
            } catch (e: Exception) {
                errors += "ستاد: ${describe(e)}"
                emptyList()
            }

            val last = prefs.cdbLastSuccess
            val due = forceContracts || now - last >= prefs.contractsEveryHours * HOUR
            val newContracts: List<Notice> = if (!due) emptyList() else try {
                val lookback = if (last == 0L) 30 else ((now - last) / DAY + 3).toInt().coerceIn(3, 60)
                val ex = KeywordMatcher(emptyList(), prefs.excludes)
                val found = CdbSource(http)
                    .fetch(prefs.contractTerms, Jalali.date(-lookback), if (last == 0L) 5 else 3, progress)
                    .filterNot { ex.excluded(it.title) }
                prefs.cdbLastSuccess = now
                store.addNew(found)
            } catch (e: Exception) {
                errors += "قراردادها: ${describe(e)}"
                emptyList()
            }

            prefs.lastSync = now
            prefs.lastStatus = buildString {
                append("آخرین بروزرسانی ${Jalali.now().substring(0, 16)}: ")
                append("${newTenders.size} مناقصه جدید")
                if (due) append("، ${newContracts.size} قرارداد جدید")
                if (errors.isNotEmpty()) append("\nخطا: " + errors.joinToString(" | "))
            }
            SyncResult(newTenders, newContracts, errors)
        }
    }
}
