package com.Obscrum.pchwmonitor.ui.dashboard

import java.util.Collections

class RingBuffer(capacity: Int = 60) {
    private val buffer = Collections.synchronizedList(mutableListOf<Float>())
    private var capacity: Int

    init { this.capacity = capacity }

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

    fun clearAndResize(newCapacity: Int) {
        synchronized(buffer) {
            capacity = newCapacity
            buffer.clear()
        }
    }
}
