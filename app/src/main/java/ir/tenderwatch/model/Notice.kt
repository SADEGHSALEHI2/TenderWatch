package ir.tenderwatch.model

import org.json.JSONArray
import org.json.JSONObject

enum class Kind { TENDER, CONTRACT }

/** One tender (from Setad) or one signed contract (from cdb.mporg.ir). Dates are Jalali strings, e.g. "1405/08/06 09:30:00". */
data class Notice(
    val id: String,
    val kind: Kind,
    val source: String,
    val title: String,
    val desc: String = "",
    val org: String = "",
    val party: String = "",
    val place: String = "",
    val number: String = "",
    val type: String = "",
    val date: String = "",
    val deadline: String = "",
    val amount: String = "",
    val url: String,
    val details: List<Pair<String, String>> = emptyList(),
    val matched: List<String> = emptyList(),
    val firstSeen: Long = System.currentTimeMillis(),
    val isNew: Boolean = true,
    val starred: Boolean = false,
) {
    fun searchText(): String = listOf(title, desc, org, party, place, number, type).joinToString(" ")

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("kind", kind.name); put("source", source); put("title", title)
        put("desc", desc); put("org", org); put("party", party); put("place", place)
        put("number", number); put("type", type); put("date", date); put("deadline", deadline)
        put("amount", amount); put("url", url)
        put("details", JSONArray(details.map { JSONArray(listOf(it.first, it.second)) }))
        put("matched", JSONArray(matched))
        put("firstSeen", firstSeen); put("isNew", isNew); put("starred", starred)
    }

    companion object {
        fun fromJson(o: JSONObject): Notice {
            val det = o.optJSONArray("details") ?: JSONArray()
            val mat = o.optJSONArray("matched") ?: JSONArray()
            return Notice(
                id = o.getString("id"),
                kind = Kind.valueOf(o.getString("kind")),
                source = o.optString("source"),
                title = o.optString("title"),
                desc = o.optString("desc"),
                org = o.optString("org"),
                party = o.optString("party"),
                place = o.optString("place"),
                number = o.optString("number"),
                type = o.optString("type"),
                date = o.optString("date"),
                deadline = o.optString("deadline"),
                amount = o.optString("amount"),
                url = o.optString("url"),
                details = (0 until det.length()).map {
                    val p = det.getJSONArray(it); p.optString(0) to p.optString(1)
                },
                matched = (0 until mat.length()).map { mat.optString(it) },
                firstSeen = o.optLong("firstSeen"),
                isNew = o.optBoolean("isNew"),
                starred = o.optBoolean("starred"),
            )
        }
    }
}
