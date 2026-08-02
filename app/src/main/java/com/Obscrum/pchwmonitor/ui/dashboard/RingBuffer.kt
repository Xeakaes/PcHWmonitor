package com.Obscrum.pchwmonitor.ui.dashboard

import java.util.Collections

class RingBuffer(private val capacity: Int = 60) {
    private val buffer = Collections.synchronizedList(mutableListOf<Float>())

    fun append(value: Float) {
        synchronized(buffer) {
            buffer.add(value)
            if (buffer.size > capacity) buffer.removeAt(0)
        }
    }

    fun snapshot(): List<Float> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
    }
}
