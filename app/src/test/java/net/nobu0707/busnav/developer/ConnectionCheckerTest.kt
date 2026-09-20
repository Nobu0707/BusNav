package net.nobu0707.busnav.developer

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class ConnectionCheckerTest {
    private val checker = ConnectionChecker(OkHttpClient.Builder().callTimeout(300, TimeUnit.MILLISECONDS)
        .readTimeout(200, TimeUnit.MILLISECONDS).followRedirects(false).build())

    @Test fun probesCorrectPathsAndClassifiesBothServices() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            for (service in ConnectionService.entries) {
                val url = server.url("/").toString()
                server.enqueue(MockResponse().setBody("{}"))
                assertEquals(ConnectionStatus.SUCCESS, checker.check(url, service).status)
                assertEquals(service.path, server.takeRequest().path)
                val code = if (service == ConnectionService.VALHALLA) 500 else 404
                server.enqueue(MockResponse().setResponseCode(code))
                assertEquals(ConnectionResult(ConnectionStatus.HTTP_ERROR, code), checker.check(url, service))
                server.takeRequest()
                server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                assertEquals(ConnectionStatus.TIMEOUT, checker.check(url, service).status)
                server.takeRequest()
            }
        }
    }
    @Test fun rejectsInvalidUrlAndReportsUnreachableHost() = runBlocking {
        assertEquals(ConnectionStatus.INVALID_URL, checker.check("http://host/?token=x", ConnectionService.VALHALLA).status)
        val server = MockWebServer(); server.start()
        val url = server.url("/").toString(); server.shutdown()
        assertEquals(ConnectionStatus.HOST_ERROR, checker.check(url, ConnectionService.VALHALLA).status)
    }
    @Test fun redirectsAreReportedWithoutSendingAnotherRequest() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "http://other-host/"))
            assertEquals(ConnectionStatus.HTTP_ERROR, checker.check(server.url("/").toString(), ConnectionService.BASEMAP).status)
            assertEquals(1, server.requestCount)
        }
    }
}
