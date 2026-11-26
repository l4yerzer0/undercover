package com.undercover.vpn.utils

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import com.undercover.vpn.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone

object LocationDetector {
    private const val TAG = "LocationDetector"
    private var cachedIsRussia: Boolean? = null

    /**
     * Synchronously checks system region indicators (Locale, SIM/Telephony, Timezone)
     * without making network calls.
     */
    fun isSystemRegionRussia(context: Context): Boolean {
        // 1. Locale country
        val localeCountry = runCatching { Locale.getDefault().country.uppercase() }.getOrNull().orEmpty()
        if (localeCountry == "RU") return true

        // 2. Telephony / SIM country ISO
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val netCountry = tm?.networkCountryIso?.uppercase().orEmpty()
            val simCountry = tm?.simCountryIso?.uppercase().orEmpty()
            if (netCountry == "RU" || simCountry == "RU") return true
        } catch (_: Exception) {
        }

        // 3. Timezone identifier (major Russian regional zones)
        val tzId = runCatching { TimeZone.getDefault().id.lowercase() }.getOrNull().orEmpty()
        val russianTzPrefixes = listOf(
            "europe/moscow", "europe/samara", "europe/kaliningrad", "europe/volgograd",
            "europe/kirov", "europe/astrakhan", "europe/saratov", "europe/ulyanovsk",
            "asia/yekaterinburg", "asia/omsk", "asia/novosibirsk", "asia/barnaul",
            "asia/tomsk", "asia/novokuznetsk", "asia/krasnoyarsk", "asia/irkutsk",
            "asia/chita", "asia/yakutsk", "asia/vladivostok", "asia/sakhalin",
            "asia/magadan", "asia/kamchatka", "asia/anadyr", "asia/srednekolymsk",
            "asia/ust-nera"
        )
        if (russianTzPrefixes.any { tzId.startsWith(it) }) {
            return true
        }

        return false
    }

    /**
     * Determines whether the user is located in Russia by checking both
     * the physical IP country (without VPN) and system region indicators.
     */
    suspend fun detectIsRussia(context: Context): Boolean = withContext(Dispatchers.IO) {
        cachedIsRussia?.let { return@withContext it }

        if (Settings.isUserInRussia) {
            cachedIsRussia = true
            return@withContext true
        }

        val systemRu = isSystemRegionRussia(context)
        val ipRu = checkIpIsRussia()

        val result = systemRu || ipRu
        cachedIsRussia = result
        Settings.isUserInRussia = result

        Log.i(TAG, "Location detection complete: systemRu=$systemRu, ipRu=$ipRu -> isRussia=$result")
        result
    }

    private fun checkIpIsRussia(): Boolean {
        val endpoints = listOf(
            "https://api.sypexgeo.net/json/",
            "https://ipwho.is/",
            "https://api.country.is/",
            "https://ipinfo.io/json",
            "http://ip-api.com/json/",
        )
        for (endpoint in endpoints) {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    useCaches = false
                    setRequestProperty("User-Agent", "Undercover/0.10")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val countryCode = when {
                        json.has("country") && json.optJSONObject("country") != null -> {
                            json.optJSONObject("country")?.optString("iso").orEmpty()
                        }
                        json.has("country_code") -> json.optString("country_code")
                        json.has("countryCode") -> json.optString("countryCode")
                        else -> json.optString("country")
                    }.uppercase()

                    if (countryCode.isNotEmpty()) {
                        return countryCode == "RU"
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Direct IP check failed for $endpoint: ${e.message}")
            }
        }
        return false
    }
}
