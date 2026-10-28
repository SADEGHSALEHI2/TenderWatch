package ir.tenderwatch.source

import ir.tenderwatch.model.Kind
import ir.tenderwatch.model.Notice
import ir.tenderwatch.net.getText
import ir.tenderwatch.net.postForm
import ir.tenderwatch.util.Fa
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Searches the national contracts database (cdb.mporg.ir, "پایگاه اطلاعات قراردادهای کشور").
 * It is an ASP.NET WebForms page: a search is a postback of the whole form (with a ~3 MB __VIEWSTATE),
 * and paging is another postback on the DevExpress pager with argument "PN<pageIndex>". 10 rows per page.
 */
class CdbSource(private val http: OkHttpClient) {

    /** Runs one search per term for contracts signed on or after [dateFrom] (Jalali "1405/07/01"). */
    fun fetch(terms: List<String>, dateFrom: String, maxPages: Int, progress: (String) -> Unit): List<Notice> {
        progress("قراردادها: باز کردن سامانه")
        val first = Jsoup.parse(http.getText(URL), URL)
        val base = formFields(first)
        val btnValue = first.getElementById("btnSearch")?.attr("value") ?: "جستجو"

        val out = LinkedHashMap<String, Notice>()
        for (term in terms) {
            val f = LinkedHashMap(base)
            f["__EVENTTARGET"] = ""
            f["__EVENTARGUMENT"] = ""
            f[SUBJECT] = term
            f[DATE_FROM] = dateFrom
            f[BTN] = btnValue

            progress("قراردادها: «$term»")
            var doc = post(f)
            var pageIndex = 0
            while (true) {
                for (n in rows(doc)) {
                    val prev = out[n.id]
                    out[n.id] = prev?.copy(matched = (prev.matched + term).distinct()) ?: n.copy(matched = listOf(term))
                }
                val (current, total) = pageInfo(doc)
                pageIndex++
                if (current >= total || pageIndex >= maxPages) break
                progress("قراردادها: «$term» صفحه ${pageIndex + 1} از $total")
                val pf = formFields(doc)
                pf["__EVENTTARGET"] = PAGER
                pf["__EVENTARGUMENT"] = "PN$pageIndex"
                doc = post(pf)
            }
        }
        return out.values.toList()
    }

    private fun post(fields: Map<String, String>): Document =
        Jsoup.parse(http.postForm(URL, fields, mapOf("Referer" to URL, "Origin" to "https://cdb.mporg.ir")), URL)

    /** Mirrors what a browser would submit for the page's single form (buttons and unchecked boxes left out). */
    private fun formFields(doc: Document): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        val form = doc.selectFirst("form") ?: return out
        for (el in form.select("input, select, textarea")) {
            val name = el.attr("name")
            if (name.isEmpty()) continue
            when (el.tagName()) {
                "input" -> {
                    val type = el.attr("type").lowercase()
                    if (type in setOf("submit", "button", "image", "reset")) continue
                    if ((type == "checkbox" || type == "radio") && !el.hasAttr("checked")) continue
                    out[name] = el.attr("value")
                }
                "select" -> {
                    val opt = el.selectFirst("option[selected]") ?: el.selectFirst("option")
                    out[name] = opt?.attr("value") ?: ""
                }
                else -> out[name] = el.text()
            }
        }
        return out
    }

    /** "1 از 18 صفحه (177 مورد)" -> (1, 18); a missing pager means a single page. */
    private fun pageInfo(doc: Document): Pair<Int, Int> {
        val text = Fa.normalize(doc.selectFirst(".dxp-summary")?.text().orEmpty())
        val m = Regex("(\\d+)\\s*از\\s*(\\d+)").find(text) ?: return 1 to 1
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    private fun rows(doc: Document): List<Notice> = doc.select("tr.dxgvDataRow").mapNotNull { tr ->
        val c = tr.children()
        if (c.size < 12) return@mapNotNull null
        val category = cell(c[1])
        val title = cell(c[2])
        val id = cell(c[3])
        val employer = cell(c[4])
        val party = cell(c[5])
        val place = cell(c[6])
        val registered = cell(c[7])
        val signed = cell(c[8])
        val ends = cell(c[9])
        val amount = cell(c[10])
        val status = cell(c[11])
        val qualification = if (c.size > 12) cell(c[12]) else ""
        if (id.isEmpty()) return@mapNotNull null

        val human = Fa.rialHuman(amount)
        Notice(
            id = "cdb:$id",
            kind = Kind.CONTRACT,
            source = "پایگاه قراردادها",
            title = title,
            org = employer,
            party = party,
            place = place,
            number = id,
            type = category,
            date = signed.ifEmpty { registered },
            deadline = ends,
            amount = amount,
            url = URL,
            details = listOf(
                "عنوان قرارداد" to title,
                "شناسه قرارداد" to id,
                "طبقه‌بندی" to category,
                "کارفرما" to employer,
                "طرف قرارداد (برنده)" to party,
                "محل اجرا" to place,
                "تاریخ عقد قرارداد" to signed,
                "تاریخ ثبت در سامانه" to registered,
                "تاریخ پایان" to ends,
                "مبلغ (ریال)" to amount,
                "مبلغ تقریبی" to human,
                "وضعیت" to status,
                "صلاحیت" to qualification,
            ).filter { it.second.isNotEmpty() },
        )
    }

    /** Long values are truncated in the grid text; the full value is kept in a title attribute. */
    private fun cell(td: Element): String {
        val full = td.selectFirst("[title]")?.attr("title")?.trim().orEmpty()
        val v = full.ifEmpty { td.text().trim() }
        return if (v.all { it == '-' }) "" else v
    }

    companion object {
        const val URL = "https://cdb.mporg.ir/AdvancedSearchPage"
        const val SUBJECT = "AdvancedSearchUC\$txtCntrSubject"
        const val CONTRACT_ID = "AdvancedSearchUC\$txtCntrID"
        private const val DATE_FROM = "AdvancedSearchUC\$txtAgreDateFrom"
        private const val BTN = "AdvancedSearchUC\$btnSearch"
        private const val PAGER = "AdvancedSearchUC\$ASPxPager1"
    }
}
