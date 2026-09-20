package net.nobu0707.busnav.developer

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

enum class ConnectionService(val path: String) {
    VALHALLA("/status"), BASEMAP(DeveloperConnectionSettings.STYLE_PATH),
    KANTO("/data/kanto.json"), CHUBU("/data/chubu.json"),
}
enum class ConnectionStatus { SUCCESS, HTTP_ERROR, TIMEOUT, HOST_ERROR, INVALID_URL }
data class ConnectionResult(val status: ConnectionStatus, val httpCode: Int? = null)

class ConnectionChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS).connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
) {
    suspend fun check(input: String, service: ConnectionService): ConnectionResult {
        val base = runCatching { normalizeBaseUrl(input) }.getOrNull()
            ?: return ConnectionResult(ConnectionStatus.INVALID_URL)
        return try {
            val call = client.newCall(Request.Builder().url(base + service.path).build())
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val result = response.use {
                            ConnectionResult(if (it.isSuccessful) ConnectionStatus.SUCCESS else ConnectionStatus.HTTP_ERROR, it.code)
                        }
                        if (continuation.isActive) continuation.resumeWith(Result.success(result))
                    }
                })
            }
        } catch (e: CancellationException) { throw e
        } catch (e: SocketTimeoutException) { ConnectionResult(ConnectionStatus.TIMEOUT)
        } catch (e: java.io.InterruptedIOException) { ConnectionResult(ConnectionStatus.TIMEOUT)
        } catch (e: IOException) { ConnectionResult(ConnectionStatus.HOST_ERROR) }
    }
}
