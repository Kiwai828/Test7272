package com.recapmaker.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

fun Uri.copyToFile(context: Context, destFile: File): Boolean {
    return try {
        context.contentResolver.openInputStream(this)?.use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        }
        true
    } catch (e: Exception) { false }
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

fun getCostForDuration(seconds: Int, tiers: List<com.recapmaker.app.data.model.PricingTier>): Int {
    val sorted = tiers.sortedBy { it.max_seconds }
    for (tier in sorted) {
        if (seconds <= tier.max_seconds) return tier.cost
    }
    return -1 // too long
}
