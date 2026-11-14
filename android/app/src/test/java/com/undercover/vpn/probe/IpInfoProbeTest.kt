package com.undercover.vpn.probe

import com.undercover.vpn.utils.IpInfoProbe
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpInfoProbeTest {

    @Test
    fun testGetLocalIpAddress() {
        val ip = IpInfoProbe.getLocalIpAddress()
        assertNotNull(ip)
        assertTrue(ip.isNotEmpty())
    }

    @Test
    fun testGetDeviceTimeZone() {
        val tz = IpInfoProbe.getDeviceTimeZone()
        assertNotNull(tz)
        assertTrue(tz.startsWith("UTC"))
    }

    @Test
    fun testGetDeviceLocale() {
        val locale = IpInfoProbe.getDeviceLocale()
        assertNotNull(locale)
        assertTrue(locale.isNotEmpty())
    }
}
