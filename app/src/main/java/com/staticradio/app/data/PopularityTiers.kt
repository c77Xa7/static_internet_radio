package com.staticradio.app.data

import com.staticradio.app.data.local.StationDao

/**
 * Percentile-bucketed emoji tier from PROJECT_CONTEXT.md's data model:
 * ❄️ <20th / 🧊 20-40th / 😐 40-60th / 🔥 60-80th / 🌋 80th+. clickCountSnapshot
 * is a one-time snapshot from Radio Browser (no live sync), so this only needs
 * recomputing when a station with a snapshot is added or removed — never on
 * every render.
 */
object PopularityTiers {

    suspend fun recompute(stationDao: StationDao) {
        val counts = stationDao.getClickCounts()
        if (counts.isEmpty()) return

        val sorted = counts.sortedBy { it.clickCountSnapshot }
        val n = sorted.size

        sorted.forEachIndexed { index, entry ->
            val percentile = index.toDouble() / n * 100
            val tier = when {
                percentile < 20 -> "❄️"
                percentile < 40 -> "🧊"
                percentile < 60 -> "😐"
                percentile < 80 -> "🔥"
                else -> "🌋"
            }
            stationDao.updatePopularityTier(entry.id, tier)
        }
    }
}
