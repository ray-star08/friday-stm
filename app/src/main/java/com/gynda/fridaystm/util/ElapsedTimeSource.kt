package com.gynda.fridaystm.util

/** Monotonic duration clock, independent of school date and demo phase controls. */
fun interface ElapsedTimeSource {
    fun elapsedMillis(): Long
}

/** Only elapsed differences are meaningful; the origin is process-independent JVM time. */
object SystemElapsedTimeSource : ElapsedTimeSource {
    override fun elapsedMillis(): Long = System.nanoTime() / 1_000_000L
}
