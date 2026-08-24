package com.Obscrum.pchwmonitor.ui.dashboard

/**
 * Fixed-capacity ring of Float values backed by a plain array: append is O(1)
 * with zero allocations (no per-tick list shifting), and snapshot/downsample
 * copy out in order.
 */
class RingBuffer(capacity: Int = 60) {
    private var buffer = FloatArray(capacity.coerceAtLeast(1))
    private var head = 0
    private var count = 0

    val size: Int
        get() = synchronized(this) { count }

    fun append(value: Float) {
        synchronized(this) {
            buffer[head] = value
            head = (head + 1) % buffer.size
            if (count < buffer.size) count++
        }
    }

    fun snapshot(): List<Float> = synchronized(this) {
        val out = ArrayList<Float>(count)
        val start = if (count < buffer.size) 0 else head
        for (i in 0 until count) {
            out.add(buffer[(start + i) % buffer.size])
        }
        out
    }

    fun clear() {
        synchronized(this) {
            head = 0
            count = 0
        }
    }

    fun clearAndResize(newCapacity: Int) {
        synchronized(this) {
            buffer = FloatArray(newCapacity.coerceAtLeast(1))
            head = 0
            count = 0
        }
    }

    /**
     * Downsamples the series to at most [targetPoints] using the
     * Largest-Triangle-Three-Buckets algorithm, which keeps peaks and valleys
     * visually faithful so charts stay smooth even with an hour of samples.
     */
    fun downsample(targetPoints: Int): List<Float> {
        val data = snapshot()
        return lttbDownsample(data, targetPoints)
    }
}

internal fun lttbDownsample(data: List<Float>, targetPoints: Int): List<Float> {
    val n = data.size
    if (targetPoints <= 0 || n <= targetPoints) return data
    if (targetPoints == 1) return listOf(data.last())
    if (targetPoints == 2) return listOf(data.first(), data.last())

    val out = ArrayList<Float>(targetPoints)
    out.add(data.first())

    // Buckets over the interior points; bucket i is [floor((i-1)*every)+1, floor(i*every)].
    val every = (n - 2).toDouble() / (targetPoints - 2)
    var prevIndex = 0
    for (i in 1 until targetPoints - 1) {
        val bucketStart = ((i - 1) * every).toInt() + 1
        var bucketEnd = (i * every).toInt() + 1
        if (bucketEnd <= bucketStart) bucketEnd = bucketStart + 1
        if (bucketEnd > n - 1) bucketEnd = n - 1

        // Average of the following bucket acts as the third triangle vertex.
        val nextStart = minOf(((i + 1) * every).toInt() + 1, n - 1)
        var nextEnd = nextStart + every.toInt().coerceAtLeast(1)
        if (nextEnd > n) nextEnd = n
        var avgY = 0.0
        var avgCount = 0
        for (j in nextStart until nextEnd) {
            avgY += data[j]
            avgCount++
        }
        if (avgCount > 0) avgY /= avgCount else avgY = data[n - 1].toDouble()

        val ax = prevIndex.toDouble()
        val ay = data[prevIndex].toDouble()
        val cx = nextStart.toDouble()

        var bestIndex = bucketStart.coerceIn(0, n - 1)
        var bestScore = -1.0
        for (j in bucketStart until bucketEnd) {
            val bx = j.toDouble()
            val by = data[j].toDouble()
            // Area of triangle (prev, candidate, next-average).
            val area = Math.abs((ax - cx) * (by - ay) - (ax - bx) * (avgY - ay))
            if (area > bestScore) {
                bestScore = area
                bestIndex = j
            }
        }
        out.add(data[bestIndex])
        prevIndex = bestIndex
    }

    out.add(data.last())
    return out
}
