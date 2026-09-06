package com.staticradio.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Decides whether a station's user-defined live hours (liveTimesFrom/
 * liveTimesTo, entered in the STATION's local time — see Edit Station's
 * local-time-equivalent hint) currently put it Online or Offline.
 *
 * Rules (per the feature brief):
 *  - no defined times, or 24/7 -> always Online (included in Next/Shuffle)
 *  - defined times -> Online only when the current wall-clock time in the
 *    user's chosen time zone (Settings -> Time zone, falling back to the
 *    device default) falls inside the window converted out of the station's
 *    country zone
 *  - unparseable times are treated as "no defined times" (Online), never as
 *    a silent skip — wrong data shouldn't make stations unreachable
 *
 * Overnight windows wrap past midnight (22:00-02:00 covers 22:00-23:59 and
 * 00:00-02:00). Windows where both ends convert equally (e.g. a window that
 * maps to "00:00-00:00" through zones offset by whole days) are treated as
 * Online all day rather than never.
 */
object StationLiveWindow {

    fun isOnline(
        liveTimesFrom: String?,
        liveTimesTo: String?,
        is24x7: Boolean,
        stationCountryCode: String?,
        userZoneId: ZoneId = ZoneId.systemDefault(),
        now: ZonedDateTime = ZonedDateTime.now(userZoneId)
    ): Boolean {
        if (is24x7) return true
        val from = parseHhmm(liveTimesFrom) ?: return true
        val to = parseHhmm(liveTimesTo) ?: return true

        // Convert the station's country-local window into the user's zone.
        val stationZone = CountryTimeZones.zoneFor(stationCountryCode) ?: userZoneId
        val date = now.withZoneSameInstant(stationZone).toLocalDate()
        val fromInstant = date.atTime(from).atZone(stationZone).toInstant()
        val toInstant = date.atTime(to).atZone(stationZone).toInstant()
        val nowInstant = now.toInstant()

        return if (fromInstant == toInstant) {
            // Zero-length after conversion: the zone offsets cancel the window
            // out. Read as "always on" rather than "never on".
            true
        } else if (fromInstant < toInstant) {
            // Same-day window.
            nowInstant >= fromInstant && nowInstant < toInstant
        } else {
            // Overnight window — wraps past midnight.
            nowInstant >= fromInstant || nowInstant < toInstant
        }
    }

    /** True when the user has actually defined live hours for this station. */
    fun hasDefinedTimes(liveTimesFrom: String?, liveTimesTo: String?, is24x7: Boolean): Boolean =
        !is24x7 && parseHhmm(liveTimesFrom) != null && parseHhmm(liveTimesTo) != null

    private fun parseHhmm(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        val parts = value.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
}
