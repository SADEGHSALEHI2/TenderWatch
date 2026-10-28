package ir.tenderwatch.data

import android.content.Context

object Defaults {
    /** Matched locally against every Setad tender (title + description + organization). */
    val TENDER_KEYWORDS = listOf(
        "لوله", "اتصالات", "پلی اتیلن", "PE", "HDPE", "پی وی سی", "PVC", "UPVC", "پلیکا",
        "کاروگیت", "GRP", "آبرسانی", "خط انتقال آب", "شبکه توزیع آب", "شبکه آب", "فاضلاب",
        "انشعاب آب", "مخزن آب", "آبیاری تحت فشار", "لوله گذاری",
    )

    /** Each term is one server-side search on cdb.mporg.ir, so keep this list short. */
    val CONTRACT_TERMS = listOf(
        "لوله", "اتصالات", "پلی اتیلن", "پلی‌اتیلن", "پی وی سی", "PVC", "آبرسانی", "فاضلاب",
    )

    val EXCLUDES = listOf("لوله بازکنی")
}

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private fun list(key: String, def: List<String>): List<String> =
        sp.getString(key, null)?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: def

    private fun putList(key: String, v: List<String>) =
        sp.edit().putString(key, v.joinToString("\n")).apply()

    var tenderKeywords: List<String>
        get() = list("tender_keywords", Defaults.TENDER_KEYWORDS)
        set(v) = putList("tender_keywords", v)

    var contractTerms: List<String>
        get() = list("contract_terms", Defaults.CONTRACT_TERMS)
        set(v) = putList("contract_terms", v)

    var excludes: List<String>
        get() = list("excludes", Defaults.EXCLUDES)
        set(v) = putList("excludes", v)

    var intervalHours: Int
        get() = sp.getInt("interval_hours", 3)
        set(v) = sp.edit().putInt("interval_hours", v).apply()

    var contractsEveryHours: Int
        get() = sp.getInt("contracts_every_hours", 24)
        set(v) = sp.edit().putInt("contracts_every_hours", v).apply()

    var wifiOnly: Boolean
        get() = sp.getBoolean("wifi_only", false)
        set(v) = sp.edit().putBoolean("wifi_only", v).apply()

    /** Newest Setad publish date seen; paging stops once a page is entirely older than this. */
    var setadLastMax: String
        get() = sp.getString("setad_last_max", "") ?: ""
        set(v) = sp.edit().putString("setad_last_max", v).apply()

    var cdbLastSuccess: Long
        get() = sp.getLong("cdb_last_success", 0L)
        set(v) = sp.edit().putLong("cdb_last_success", v).apply()

    var lastSync: Long
        get() = sp.getLong("last_sync", 0L)
        set(v) = sp.edit().putLong("last_sync", v).apply()

    var lastStatus: String
        get() = sp.getString("last_status", "") ?: ""
        set(v) = sp.edit().putString("last_status", v).apply()

    /** Forces the next sync to rescan everything (e.g. after keywords change). */
    fun resetCursors() {
        setadLastMax = ""
        cdbLastSuccess = 0L
    }
}
