package ir.tenderwatch.source

import ir.tenderwatch.model.Kind
import ir.tenderwatch.model.Notice
import ir.tenderwatch.net.getText
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder

/**
 * Reads the public tender list of setadiran.ir (the grid on etend's home page).
 * The grid is served as JSON, newest first, and needs only the session cookie and csrf token of index.action.
 * Tender details/documents sit behind a captcha, so the app opens those in a WebView instead.
 */
class SetadSource(private val http: OkHttpClient) {

    /** Returns every tender newer than [cutoff] (all of them when [cutoff] is empty), up to [maxPages] pages of 200. */
    fun fetch(cutoff: String, maxPages: Int, progress: (String) -> Unit): List<Notice> {
        // index.action only sets the session cookie; the grid and its csrf token are in welcome.action,
        // which a browser loads by ajax right after.
        http.getText(INDEX)
        val html = http.getText(WELCOME, mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to INDEX))
        val token = Jsoup.parse(html).selectFirst("input[name=csrf_token]")?.attr("value")
        if (token.isNullOrBlank()) throw IOException("توکن سایت ستاد پیدا نشد")

        val out = ArrayList<Notice>()
        var page = 1
        while (page <= maxPages) {
            progress("ستاد: صفحه $page")
            val ts = System.currentTimeMillis()
            val url = "$BASE/callMainPageCartable-anonymous.action?struts.token.name=csrf_token" +
                "&csrf_token=${URLEncoder.encode(token, "UTF-8")}&_search=false&nd=$ts" +
                "&rows=$ROWS&page=$page&sidx=&sord=asc&_=$ts"
            val json = JSONObject(
                http.getText(url, mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to INDEX))
            )
            val arr = json.optJSONArray("gridModel") ?: break
            if (arr.length() == 0) break

            var older = 0
            for (i in 0 until arr.length()) {
                val n = parse(arr.getJSONObject(i))
                out += n
                if (cutoff.isNotEmpty() && n.date < cutoff) older++
            }
            if (older == arr.length() || page >= json.optInt("total", page)) break
            page++
        }
        return out
    }

    private fun parse(o: JSONObject): Notice {
        val org = o.optJSONObject("organization")?.let { s(it, "name") } ?: ""
        val subjects = buildList {
            if (o.optBoolean("allowedCommodity")) add("کالا")
            if (o.optBoolean("allowedServices")) add("خدمات")
            if (o.optBoolean("allowedContractor")) add("پیمانکاری")
            if (o.optBoolean("allowedConsultation")) add("مشاوره")
            if (o.optBoolean("allowedOther")) add("سایر")
        }.joinToString("، ")
        val province = s(o, "operationProvinceName")
        val city = s(o, "operationCityName")
        val deadlines = listOf("proposalDeadlineDate", "inquiryDeadlineDate", "documentsDeadlineDate", "evaluationDeadlineDate")
            .map { s(o, it) }.filter { it.isNotEmpty() }

        val details = listOf(
            "شماره فراخوان" to s(o, "number"),
            "عنوان" to s(o, "title"),
            "نوع فراخوان" to s(o, "typeName"),
            "دستگاه مناقصه‌گزار" to org,
            "استان" to province,
            "شهر" to city,
            "موضوع" to subjects,
            "شرح حوزه فعالیت" to s(o, "domainsDescription"),
            "تاریخ انتشار" to s(o, "publicNotificationDate"),
            "مهلت دریافت اسناد" to s(o, "documentsDeadlineDate"),
            "مهلت پاسخ فراخوان/استعلام" to s(o, "inquiryDeadlineDate"),
            "مهلت ارسال پیشنهاد" to s(o, "proposalDeadlineDate"),
            "مهلت ارزیابی" to s(o, "evaluationDeadlineDate"),
            "زمان بازگشایی" to s(o, "openingDate"),
            "مبلغ خرید اسناد" to s(o, "tenderDocumentsPrice"),
            "تعداد آگهی" to s(o, "advertisementCount").takeIf { it != "0" }.orEmpty(),
        ).filter { it.second.isNotEmpty() }

        return Notice(
            id = "setad:" + s(o, "id"),
            kind = Kind.TENDER,
            source = "ستاد",
            title = s(o, "title"),
            desc = s(o, "domainsDescription"),
            org = org,
            place = listOf(province, city).filter { it.isNotEmpty() }.distinct().joinToString(" - "),
            number = s(o, "number"),
            type = s(o, "typeName"),
            date = s(o, "publicNotificationDate"),
            deadline = deadlines.maxOrNull().orEmpty(),
            url = INDEX,
            details = details,
        )
    }

    /** org.json's optString returns "null" for JSON nulls, so read through this instead. */
    private fun s(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.opt(key).toString().trim()

    companion object {
        const val BASE = "https://etend.setadiran.ir/etend"
        const val INDEX = "$BASE/index.action"
        private const val WELCOME = "$BASE/welcome.action"
        private const val ROWS = 200
    }
}
