package com.neko.v2ray.handler

import android.content.Context
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import com.neko.v2ray.AppConfig
import com.neko.v2ray.R
import com.neko.v2ray.dto.IPAPIInfo
import com.neko.v2ray.extension.responseLength
import com.neko.v2ray.util.HttpUtil
import com.neko.v2ray.util.JsonUtil
import kotlinx.coroutines.isActive
import libv2ray.Libv2ray
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import kotlin.coroutines.coroutineContext

object SpeedtestManager {

    private val tcpTestingSockets = ArrayList<Socket?>()

    suspend fun tcping(url: String, port: Int): Long {
        var time = -1L
        for (k in 0 until 2) {
            val one = socketConnectTime(url, port)
            if (!coroutineContext.isActive) {
                break
            }
            if (one != -1L && (time == -1L || one < time)) {
                time = one
            }
        }
        return time
    }

    fun realPing(config: String): Long {
        return try {
            Libv2ray.measureOutboundDelay(config, SettingsManager.getDelayTestUrl())
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to measure outbound delay", e)
            -1L
        }
    }

    fun ping(url: String): String {
        try {
            val command = "/system/bin/ping -c 3 $url"
            val process = Runtime.getRuntime().exec(command)
            val allText = process.inputStream.bufferedReader().use { it.readText() }
            if (!TextUtils.isEmpty(allText)) {
                val tempInfo = allText.substring(allText.indexOf("min/avg/max/mdev") + 19)
                val temps =
                    tempInfo.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (temps.count() > 0 && temps[0].length < 10) {
                    return temps[0].toFloat().toInt().toString() + "ms"
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to ping URL: $url", e)
        }
        return "-1ms"
    }

    fun socketConnectTime(url: String, port: Int): Long {
        try {
            val socket = Socket()
            synchronized(this) {
                tcpTestingSockets.add(socket)
            }
            val start = System.currentTimeMillis()
            socket.connect(InetSocketAddress(url, port), 3000)
            val time = System.currentTimeMillis() - start
            synchronized(this) {
                tcpTestingSockets.remove(socket)
            }
            socket.close()
            return time
        } catch (e: UnknownHostException) {
            Log.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            Log.e(AppConfig.TAG, "socketConnectTime IOException: $e")
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
        }
        return -1
    }

    fun closeAllTcpSockets() {
        synchronized(this) {
            tcpTestingSockets.forEach {
                it?.close()
            }
            tcpTestingSockets.clear()
        }
    }

    fun testConnection(context: Context, port: Int): Pair<Long, String> {
        var result: String
        var elapsed = -1L

        val conn = HttpUtil.createProxyConnection(SettingsManager.getDelayTestUrl(), port, 15000, 15000) ?: return Pair(elapsed, "")
        try {
            val start = SystemClock.elapsedRealtime()
            val code = conn.responseCode
            elapsed = SystemClock.elapsedRealtime() - start

            if (code == 204 || code == 200 && conn.responseLength == 0L) {
                result = context.getString(R.string.connection_test_available, elapsed)
            } else {
                throw IOException(
                    context.getString(
                        R.string.connection_test_error_status_code,
                        code
                    )
                )
            }
        } catch (e: IOException) {
            Log.e(AppConfig.TAG, "Connection test IOException", e)
            result = context.getString(R.string.connection_test_error, e.message)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Connection test Exception", e)
            result = context.getString(R.string.connection_test_error, e.message)
        } finally {
            conn.disconnect()
        }

        return Pair(elapsed, result)
    }

    fun getRemoteIPInfo(): String? {
        val httpPort = SettingsManager.getHttpPort()
        var content = HttpUtil.getUrlContent(AppConfig.IP_API_URL, 5000, httpPort) ?: return null

        var ipInfo = JsonUtil.fromJson(content, IPAPIInfo::class.java) ?: return null
        var ip = ipInfo.ip ?: ipInfo.clientIp ?: ipInfo.ip_addr ?: ipInfo.query
        var country = ipInfo.country_code ?: ipInfo.country ?: ipInfo.countryCode

        return "(${country ?: "unknown"}) $ip"
    }

    /**
     * Gets the version of the V2Ray library.
     *
     * @return The version of the V2Ray library.
     */
    fun getLibVersion(): String {
        return Libv2ray.checkVersionX()
    }

}
