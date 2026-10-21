package be.wimvandonck.noter.data.api

import be.wimvandonck.noter.logic.SignInGrant
import be.wimvandonck.noter.logic.SignIns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Noter's sign-in helper (`worker/`, [SignInConfig.HELPER_URL]): the only address besides Notion's
 * API the app talks to (PRIVACY.md). It renews "Connect to Notion" tokens with the connection's
 * secret, which can't be kept in the app.
 */
class SignInClient(
    /** Only tests change this (to a server on the test machine). */
    private val helperUrl: String = SignInConfig.HELPER_URL,
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** New tokens for [refreshToken]; null when Notion refused it or the helper couldn't be reached. */
    suspend fun refresh(refreshToken: String): SignInGrant? = withContext(Dispatchers.IO) {
        if (helperUrl.isBlank()) return@withContext null
        val body = mapOf("refresh_token" to refreshToken).toJsonElement().toString().toRequestBody(JSON)
        val request = Request.Builder().url("${helperUrl.trimEnd('/')}/refresh").post(body).build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) SignIns.grant(response.body.string()) else null
            }
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
