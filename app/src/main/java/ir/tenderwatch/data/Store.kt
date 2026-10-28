package ir.tenderwatch.data

import android.content.Context
import ir.tenderwatch.model.Kind
import ir.tenderwatch.model.Notice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/** Matched notices persisted as a single JSON file; small enough (a few thousand rows) that a database is not needed. */
class Store private constructor(private val file: File) {
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Notice>> = _items

    /** Adds notices not stored yet and returns exactly those. */
    @Synchronized
    fun addNew(list: List<Notice>): List<Notice> {
        val known = _items.value.mapTo(HashSet()) { it.id }
        val fresh = list.filter { it.id !in known }.distinctBy { it.id }
        if (fresh.isNotEmpty()) save(fresh + _items.value)
        return fresh
    }

    @Synchronized
    fun update(id: String, f: (Notice) -> Notice) {
        save(_items.value.map { if (it.id == id) f(it) else it })
    }

    @Synchronized
    fun markAllRead(kind: Kind) {
        save(_items.value.map { if (it.kind == kind && it.isNew) it.copy(isNew = false) else it })
    }

    private fun load(): List<Notice> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { Notice.fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(list: List<Notice>) {
        val (starred, rest) = list.partition { it.starred }
        val kept = starred + rest.take(MAX_ITEMS)
        _items.value = kept
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONArray(kept.map { it.toJson() }).toString())
        tmp.renameTo(file)
    }

    companion object {
        private const val MAX_ITEMS = 4000

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(File(context.applicationContext.filesDir, "notices.json")).also { instance = it }
        }
    }
}
