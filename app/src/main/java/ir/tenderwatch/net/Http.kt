package ir.tenderwatch.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** A fresh client per sync so every run starts a clean server session. */
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(MemoryCookieJar())
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
    return newCall(req).execute().use {
        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
        it.body?.string() ?: ""
    }
}

fun OkHttpClient.postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
    val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
    val req = Request.Builder().url(url).post(body).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
    return newCall(req).execute().use {
        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
        it.body?.string() ?: ""
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
