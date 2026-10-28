package ir.tenderwatch.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** A fresh client per sync so every run starts a clean server session. */
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(MemoryCookieJar())
        // Both sites are old IIS/Java servers; plain HTTP/1.1 avoids h2 quirks with multi-MB form posts.
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", UA)
                    .header("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.5")
                    .build()
            )
        }
        .build()
}

fun OkHttpClient.getText(url: String, headers: Map<String, String> = emptyMap()): String {
    val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
    return execute(req)
}

fun OkHttpClient.postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
    val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
    val req = Request.Builder().url(url).post(body).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
    return execute(req)
}

/** Mobile links drop big transfers now and then (cdb pages are ~3.5 MB each way), so retry a few times. */
private fun OkHttpClient.execute(req: Request, attempts: Int = 3): String {
    var last: IOException? = null
    repeat(attempts) { i ->
        try {
            return newCall(req).execute().use {
                if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                it.body?.string() ?: ""
            }
        } catch (e: IOException) {
            last = e
            if (i < attempts - 1) Thread.sleep(3000L * (i + 1))
        }
    }
    throw last ?: IOException("network error")
}

/** Short Persian explanation for the status line. */
fun describe(e: Exception): String {
    val msg = e.message.orEmpty()
    return when {
        msg.contains("reset", true) || msg.contains("closed", true) || e is java.io.EOFException ->
            "اتصال توسط سایت قطع شد (اگر VPN روشن است خاموش کنید؛ این سایت‌ها فقط از اینترنت ایران جواب می‌دهند) [$msg]"
        e is java.net.UnknownHostException -> "سایت پیدا نشد؛ اینترنت را بررسی کنید [$msg]"
        e is java.net.SocketTimeoutException -> "سایت دیر جواب داد (timeout)؛ دوباره تلاش کنید"
        e is javax.net.ssl.SSLException -> "خطای اتصال امن (SSL) [$msg]"
        else -> msg.ifEmpty { e.javaClass.simpleName }
    }
}

private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) {
            this.cookies.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            this.cookies += c
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }
}
