package com.undercover.vpn.byedpi

import org.junit.Assert.assertEquals
import org.junit.Test

class ByeDpiStrategyTest {

    @Test
    fun testStrategyResultCalculation() {
        val result = ByeDpiStrategyTester.StrategyResult(
            command = "--split 1+s --diso",
            success = 8,
            total = 10,
            completed = true
        )

        assertEquals(0.8f, result.ratio, 0.001f)
        assertEquals(80, result.percent)
        assertEquals(true, result.completed)
    }

    @Test
    fun testStrategyResultZeroDivisionProtection() {
        val zeroResult = ByeDpiStrategyTester.StrategyResult(
            command = "--split 2",
            success = 0,
            total = 0,
            completed = false
        )

        assertEquals(0f, zeroResult.ratio, 0.001f)
        assertEquals(0, zeroResult.percent)
    }

    @Test
    fun testStrategyResultFullSuccess() {
        val perfectResult = ByeDpiStrategyTester.StrategyResult(
            command = "--fake -1",
            success = 5,
            total = 5,
            completed = true
        )

        assertEquals(1.0f, perfectResult.ratio, 0.001f)
        assertEquals(100, perfectResult.percent)
    }
}
