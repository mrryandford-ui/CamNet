package com.camnet.app

import android.util.Log
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import kotlin.concurrent.thread

/**
 * Thin SSL termination proxy: listens on [sslPort] with a self-signed cert and
 * forwards each connection (byte-for-byte) to [backendPort] on localhost.
 *
 * This gives camera phones an HTTPS origin so navigator.mediaDevices is available
 * (secure-context requirement) without touching Ktor's TLS stack at all.
 * WebSocket upgrades (wss://) pass through transparently because we proxy raw TCP.
 */
class SslProxy(
    private val sslPort: Int,
    private val backendPort: Int,
    keyStore: KeyStore,
) {
    private val sslContext: SSLContext = SSLContext.getInstance("TLS").also { ctx ->
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, "camnet-ssl".toCharArray())
        ctx.init(kmf.keyManagers, null, null)
    }

    @Volatile private var serverSocket: SSLServerSocket? = null
    @Volatile private var running = false

    fun start() {
        running = true
        thread(name = "camnet-ssl-proxy", isDaemon = true) {
            try {
                val ss = sslContext.serverSocketFactory.createServerSocket() as SSLServerSocket
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("0.0.0.0", sslPort))
                serverSocket = ss
                Log.i("CamNet", "SSL proxy listening on :$sslPort → localhost:$backendPort")
                while (running) {
                    try {
                        val client = ss.accept()
                        thread(name = "camnet-ssl-bridge", isDaemon = true) { bridge(client) }
                    } catch (e: Exception) {
                        if (running) Log.w("CamNet", "SSL proxy accept: $e")
                    }
                }
            } catch (e: Exception) {
                Log.e("CamNet", "SSL proxy failed: $e")
            }
        }
    }

    private fun bridge(ssl: java.net.Socket) {
        try {
            ssl.soTimeout = 30_000
            java.net.Socket("127.0.0.1", backendPort).use { local ->
                local.soTimeout = 30_000
                // Two threads: one per direction. copyTo blocks until EOF.
                val upstream = thread(isDaemon = true, name = "camnet-ssl-up") {
                    try {
                        val out = local.getOutputStream()
                        ssl.getInputStream().copyTo(out)
                        out.flush()
                    } catch (_: Exception) {}
                    finally { runCatching { local.shutdownOutput() } }
                }
                try {
                    val out = ssl.getOutputStream()
                    local.getInputStream().copyTo(out)
                    out.flush()
                } catch (_: Exception) {}
                finally { runCatching { ssl.shutdownOutput() } }
                // Wait briefly for the upstream thread to finish after local input is closed
                upstream.join(2_000)
            }
        } catch (e: Exception) {
            Log.w("CamNet", "SSL bridge: $e")
        } finally {
            runCatching { ssl.close() }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
