package com.staticradio.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Best-effort ISO country code -> representative IANA timezone, for
 * converting a station's entered live-broadcast time into the listener's
 * local time. Countries spanning multiple zones (US, RU, BR, etc.) use one
 * representative zone — not exact for every region of those countries, but
 * good enough for "roughly when is this station live" context.
 */
private val COUNTRY_ZONE_MAP = mapOf(
    "US" to "America/New_York", "GB" to "Europe/London", "DE" to "Europe/Berlin",
    "FR" to "Europe/Paris", "ES" to "Europe/Madrid", "IT" to "Europe/Rome",
    "NL" to "Europe/Amsterdam", "BE" to "Europe/Brussels", "IE" to "Europe/Dublin",
    "PT" to "Europe/Lisbon", "SE" to "Europe/Stockholm", "NO" to "Europe/Oslo",
    "DK" to "Europe/Copenhagen", "FI" to "Europe/Helsinki", "PL" to "Europe/Warsaw",
    "AT" to "Europe/Vienna", "CH" to "Europe/Zurich", "GR" to "Europe/Athens",
    "CZ" to "Europe/Prague", "HU" to "Europe/Budapest", "RO" to "Europe/Bucharest",
    "RU" to "Europe/Moscow", "UA" to "Europe/Kyiv", "TR" to "Europe/Istanbul",
    "CA" to "America/Toronto", "MX" to "America/Mexico_City", "BR" to "America/Sao_Paulo",
    "AR" to "America/Argentina/Buenos_Aires", "CL" to "America/Santiago",
    "AU" to "Australia/Sydney", "NZ" to "Pacific/Auckland",
    "JP" to "Asia/Tokyo", "CN" to "Asia/Shanghai", "KR" to "Asia/Seoul",
    "IN" to "Asia/Kolkata", "ID" to "Asia/Jakarta", "TH" to "Asia/Bangkok",
    "PH" to "Asia/Manila", "VN" to "Asia/Ho_Chi_Minh", "MY" to "Asia/Kuala_Lumpur",
    "SG" to "Asia/Singapore", "ZA" to "Africa/Johannesburg", "EG" to "Africa/Cairo",
    "NG" to "Africa/Lagos", "AE" to "Asia/Dubai", "SA" to "Asia/Riyadh", "IL" to "Asia/Jerusalem"
)

object CountryTimeZones {

    fun zoneFor(countryCode: String?): ZoneId? =
        countryCode?.let { COUNTRY_ZONE_MAP[it.uppercase()] }?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    /** Converts an "HH:mm" time in the station's country to the device's local "HH:mm", or null if unknown/unparseable. */
    fun toLocalEquivalent(countryCode: String?, hhmm: String): String? {
        val zone = zoneFor(countryCode) ?: return null
        val parts = hhmm.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return runCatching {
            val source = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(hour, minute), zone)
            val local = source.withZoneSameInstant(ZoneId.systemDefault())
            "%02d:%02d".format(local.hour, local.minute)
        }.getOrNull()
    }
}
