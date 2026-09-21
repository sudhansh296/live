package com.coderlobby.hivo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * status = HTTP status, or 0 when the request never reached the server.
 * code = the server's short error code. field = which request field the server did not like, if it said so.
 */
class ApiException(val status: Int, val code: String, val field: String? = null) : Exception("$status $code")

/**
 * Talks to the Hivo Live server. Sends the access token on every call and, when the server answers 401,
 * silently gets a new one with the refresh token (one refresh at a time) and retries the call once.
 */
class ApiClient(private val baseUrl: String, private val store: SecureStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var accessToken: String? = null

    /** Called when the server says the session is over (refresh token rejected). */
    @Volatile
    var onSessionExpired: (() -> Unit)? = null

    private val refreshLock = Any()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val token = accessToken
            val outgoing = if (token != null && !isAuthPath(request)) {
                request.newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                request
            }
            chain.proceed(outgoing)
        }
        .authenticator(Authenticator { _, response -> reauthenticate(response) })
        .build()

    // ── Public calls ──────────────────────────────────────────────────────────

    suspend fun loginWithFirebase(idToken: String, termsVersion: String, adultConfirmed: Boolean): TokenResponse {
        val body = json.encodeToString(
            FirebaseLoginRequest.serializer(),
            FirebaseLoginRequest(idToken, termsVersion, adultConfirmed),
        )
        val tokens = execute(post("auth/firebase", body)) { json.decodeFromString(TokenResponse.serializer(), it) }
        accept(tokens)
        return tokens
    }

    /** Returns the signed-in user, or null if there is no valid session. Throws ApiException(0, "network") when offline. */
    suspend fun restoreSession(): UserDto? = withContext(Dispatchers.IO) {
        if (store.readRefreshToken() == null) return@withContext null
        when (refreshBlocking()) {
            Refresh.Ok -> me()
            Refresh.Rejected -> null
            Refresh.Offline -> throw ApiException(0, "network")
        }
    }

    suspend fun me(): UserDto =
        execute(Request.Builder().url(baseUrl + "me").get().build()) { json.decodeFromString(MeResponse.serializer(), it).user }

    /** Live "is this @name free?" check while the user types. */
    suspend fun checkUsername(name: String): UsernameCheck {
        val url = (baseUrl + "profile/username-available").toHttpUrl().newBuilder().addQueryParameter("u", name).build()
        return execute(Request.Builder().url(url).get().build()) { json.decodeFromString(UsernameCheck.serializer(), it) }
    }

    /** First-time profile setup. The server decides whether the person is old enough. */
    suspend fun saveProfile(profile: ProfileRequest): UserDto {
        val body = json.encodeToString(ProfileRequest.serializer(), profile)
        val request = Request.Builder().url(baseUrl + "me/profile").put(body.toRequestBody(jsonType)).build()
        return execute(request) { json.decodeFromString(MeResponse.serializer(), it).user }
    }

    /** Uploads a JPEG. The server checks it, crops it square, shrinks it and removes hidden data. */
    suspend fun uploadAvatar(jpeg: ByteArray): UserDto {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("avatar", "avatar.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        return execute(Request.Builder().url(baseUrl + "me/avatar").post(form).build()) {
            json.decodeFromString(MeResponse.serializer(), it).user
        }
    }

    /** Downloads a public file (a profile photo) from our server. Returns null if it cannot be fetched. */
    suspend fun fetchPublicFile(path: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(baseUrl + path.trimStart('/')).get().build()
            http.newCall(request).execute().use { response -> if (response.isSuccessful) response.body.bytes() else null }
        } catch (_: IOException) {
            null
        }
    }

    suspend fun deleteAvatar(): UserDto =
        execute(Request.Builder().url(baseUrl + "me/avatar").delete().build()) {
            json.decodeFromString(MeResponse.serializer(), it).user
        }

    /** Best effort: tells the server to end this login, then forgets everything locally. */
    suspend fun logout() {
        val refreshToken = store.readRefreshToken()
        if (refreshToken != null) {
            try {
                val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
                execute(post("auth/logout", body)) { }
            } catch (_: ApiException) {
                // Offline or already invalid: local sign-out still happens below.
            }
        }
        accessToken = null
        store.clear()
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun post(path: String, body: String): Request =
        Request.Builder().url(baseUrl + path).post(body.toRequestBody(jsonType)).build()

    private suspend fun <T> execute(request: Request, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) throw apiError(response.code, text)
                parse(text)
            }
        } catch (e: IOException) {
            throw ApiException(0, "network")
        }
    }

    private fun apiError(status: Int, text: String): ApiException =
        try {
            val body = json.decodeFromString(ErrorBody.serializer(), text)
            ApiException(status, body.error ?: "unknown", body.field)
        } catch (_: Exception) {
            ApiException(status, "unknown")
        }

    private fun accept(tokens: TokenResponse) {
        accessToken = tokens.accessToken
        store.saveRefreshToken(tokens.refreshToken)
    }

    private fun isAuthPath(request: Request) = request.url.encodedPath.startsWith("/auth/")

    private fun reauthenticate(response: Response): Request? {
        if (isAuthPath(response.request) || priorResponses(response) >= 1) return null
        synchronized(refreshLock) {
            val current = accessToken
            val used = response.request.header("Authorization")?.removePrefix("Bearer ")
            // Another call already refreshed while we waited for the lock: just retry with the new token.
            if (current != null && current != used) return retryWith(response, current)
            return if (refreshBlocking() == Refresh.Ok) accessToken?.let { retryWith(response, it) } else null
        }
    }

    private fun retryWith(response: Response, token: String): Request =
        response.request.newBuilder().header("Authorization", "Bearer $token").build()

    private fun priorResponses(response: Response): Int {
        var count = 0
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private enum class Refresh { Ok, Rejected, Offline }

    private fun refreshBlocking(): Refresh {
        val refreshToken = store.readRefreshToken() ?: return Refresh.Rejected
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
        return try {
            http.newCall(post("auth/refresh", body)).execute().use { response ->
                val text = response.body.string()
                when {
                    response.isSuccessful -> {
                        accept(json.decodeFromString(TokenResponse.serializer(), text))
                        Refresh.Ok
                    }
                    response.code == 401 || response.code == 403 -> {
                        accessToken = null
                        store.clear()
                        onSessionExpired?.invoke()
                        Refresh.Rejected
                    }
                    else -> Refresh.Offline // server trouble: keep the session and try again later
                }
            }
        } catch (_: IOException) {
            Refresh.Offline
        }
    }
}
