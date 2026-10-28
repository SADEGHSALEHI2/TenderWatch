package ir.tenderwatch.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.tenderwatch.data.Prefs
import ir.tenderwatch.data.Store
import ir.tenderwatch.model.Notice
import ir.tenderwatch.sync.SyncRunner
import ir.tenderwatch.sync.SyncWorker
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Detail(val id: String) : Screen
    /** [contractId] set: auto-search that contract on cdb.mporg.ir once the page loads. */
    data class Web(val url: String, val title: String, val contractId: String? = null) : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val store = Store.get(app)
    val items: StateFlow<List<Notice>> = store.items

    private val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    var tab by mutableIntStateOf(0)
    var query by mutableStateOf("")
    var onlyOpen by mutableStateOf(false)
    var syncing by mutableStateOf(false)
        private set
    var progress by mutableStateOf("")
        private set
    var status by mutableStateOf(prefs.lastStatus)
        private set

    fun open(s: Screen) {
        stack.add(s)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun sync(forceContracts: Boolean) {
        if (syncing) return
        syncing = true
        viewModelScope.launch {
            try {
                SyncRunner.run(getApplication(), forceContracts) { progress = it }
            } finally {
                syncing = false
                progress = ""
                status = prefs.lastStatus
            }
        }
    }

    fun toggleStar(id: String) = store.update(id) { it.copy(starred = !it.starred) }

    fun markRead(id: String) = store.update(id) { if (it.isNew) it.copy(isNew = false) else it }

    fun saveSettings(
        tenderKeywords: List<String>,
        contractTerms: List<String>,
        excludes: List<String>,
        intervalHours: Int,
        contractsEveryHours: Int,
        wifiOnly: Boolean,
    ) {
        val keywordsChanged = tenderKeywords != prefs.tenderKeywords || contractTerms != prefs.contractTerms
        prefs.tenderKeywords = tenderKeywords
        prefs.contractTerms = contractTerms
        prefs.excludes = excludes
        prefs.intervalHours = intervalHours
        prefs.contractsEveryHours = contractsEveryHours
        prefs.wifiOnly = wifiOnly
        // New keywords should also be applied to items already scanned, so rescan from scratch next time.
        if (keywordsChanged) prefs.resetCursors()
        SyncWorker.schedule(getApplication())
    }
}
