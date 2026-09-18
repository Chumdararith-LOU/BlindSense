package com.blindbelt.prototype

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Change UDP_HOST if the motor board ever gets a different IP from the hotspot
// TEMP: Mac listener IP for tonight's chain test — restore to 10.25.32.135 afterwards
private const val UDP_HOST = "10.118.98.66"
private const val UDP_PORT = 8888

class UdpHapticSender {
    private var socket: DatagramSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        Log.d("BeltSteering", "UDP target: $UDP_HOST:$UDP_PORT")
        try {
            socket = DatagramSocket()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun commandFor(direction: Direction, dropOff: Boolean, nearest: Float): Pair<Int, Int> {
        val cmd = when {
            dropOff -> 4                                   // DROP
            direction == Direction.STOP -> 3               // STOP
            direction == Direction.LEFT -> 1               // STEER_LEFT
            direction == Direction.RIGHT -> 2              // STEER_RIGHT
            else -> 0                                      // CLEAR
        }
        val band = when {
            nearest <= 0f || nearest >= 1.5f -> 0
            nearest < 1.0f -> 2
            else -> 1
        }
        return cmd to band
    }

    fun sendV2(cmd: Int, band: Int) {
        val bytes = byteArrayOf(0xA5.toByte(), cmd.toByte(), band.toByte(),
                                (0xA5 xor cmd xor band).toByte())
        scope.launch {
            try {
                val address = InetAddress.getByName(UDP_HOST)
                val packet = DatagramPacket(bytes, bytes.size, address, UDP_PORT)
                socket?.send(packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        Log.d("BeltSteering", "CMD=$cmd BAND=$band")
    }

    fun close() {
        socket?.close()
    }
}
