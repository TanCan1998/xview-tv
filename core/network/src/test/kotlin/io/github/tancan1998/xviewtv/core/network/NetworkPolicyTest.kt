package io.github.tancan1998.xviewtv.core.network

import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class NetworkPolicyTest {
    @Test fun validatesAddressAndPort() {
        assertNull(ProxyConfig().validate())
        assertNull(ProxyConfig(ProxyMode.HTTP, "127.0.0.1", 7890).validate())
        assertNull(ProxyConfig(ProxyMode.SOCKS5, "::1", 1080).validate())
        for (host in listOf("", "https://proxy.test", "user@proxy.test", "host/path", "host\r\n")) {
            assertNotNull(ProxyConfig(ProxyMode.HTTP, host, 8080).validate())
        }
        assertNotNull(ProxyConfig(ProxyMode.SOCKS5, "localhost", 0).validate())
        assertNotNull(ProxyConfig(ProxyMode.HTTP, "localhost", 65536).validate())
    }

    @Test fun appliesOnlyExplicitProxyAndRejectsRedirects() {
        for ((mode, type) in listOf(ProxyMode.DIRECT to Proxy.Type.DIRECT, ProxyMode.HTTP to Proxy.Type.HTTP, ProxyMode.SOCKS5 to Proxy.Type.SOCKS)) {
            val client = NetworkPolicy.client(ProxyConfig(mode, "127.0.0.1", 12345))
            assertEquals(type, client.proxy!!.type())
            assertFalse(client.followRedirects)
            assertFalse(client.followSslRedirects)
            assertFalse(client.retryOnConnectionFailure)
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun httpRequestActuallyUsesConfiguredProxy() {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val received = AtomicReference<String>()
            val worker = thread {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader()
                    received.set(reader.readLine())
                    while (!reader.readLine().isNullOrEmpty()) { /* consume headers */ }
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".toByteArray())
                }
            }
            val client = NetworkPolicy.client(ProxyConfig(ProxyMode.HTTP, "127.0.0.1", server.localPort))
            client.newCall(Request.Builder().url("http://synthetic.invalid/resource").build()).execute().use {
                assertEquals(200, it.code)
                assertEquals("OK", it.body!!.string())
            }
            worker.join(5000)
            assertEquals("GET http://synthetic.invalid/resource HTTP/1.1", received.get())
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun unavailableProxyDoesNotFallBackToReachableDestination() {
        ServerSocket(0).use { destination ->
            destination.soTimeout = 300
            val unavailablePort = ServerSocket(0).use { it.localPort }
            val client = NetworkPolicy.client(ProxyConfig(ProxyMode.HTTP, "127.0.0.1", unavailablePort))
            assertThrows(java.io.IOException::class.java) {
                client.newCall(Request.Builder().url("http://127.0.0.1:${destination.localPort}/").build()).execute().close()
            }
            assertThrows(java.net.SocketTimeoutException::class.java) { destination.accept().close() }
            client.dispatcher.executorService.shutdown()
        }
    }
}
