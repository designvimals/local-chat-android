package com.example.privatevault.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeUtils {
    private val displayFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())

    fun nowIso(): String = Instant.now().toString()

    fun display(isoTimestamp: String): String = runCatching {
        displayFormatter.format(Instant.parse(isoTimestamp))
    }.getOrElse { isoTimestamp }
}
