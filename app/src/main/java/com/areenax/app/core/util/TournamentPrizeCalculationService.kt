package com.areenax.app.core.util

import kotlin.math.ceil

/**
 * Centralized service for calculating tournament prizes based on Areenax production rules.
 *
 * Rules:
 * 1. Top-9 Prizes: Fixed percentage of the collected entry pool.
 *    Each prize is rounded UP to the nearest 5 Rs.
 * 2. Consolation Prizes: Remaining participants (39 players in a 48-slot tourney)
 *    share a percentage of the pool. Each prize is rounded UP to the nearest 1 Rupee.
 * 3. Prize Pool Display: Only the sum of Top-9 prizes is displayed as the "Prize Pool".
 */
object TournamentPrizeCalculationService {

    private const val TOTAL_SLOTS = 48
    private const val TOP_9_COUNT = 9
    private const val CONSOLATION_COUNT = 39 // 48 - 9

    private val TOP_9_PERCENTAGES = listOf(
        0.10,           // 1st
        0.08510638,     // 2nd
        0.07234043,     // 3rd
        0.06382979,     // 4th
        0.05531915,     // 5th
        0.05106383,     // 6th
        0.04680851,     // 7th
        0.04255319,     // 8th
        0.03404255      // 9th
    )

    private const val CONSOLATION_PER_USER_PERCENT = 0.00912166

    data class CalculationResult(
        val top9Prizes: List<Double>,
        val consolationPrize: Double,
        val totalPrizePool: Double, // Sum of Top-9
        val totalConsolationPool: Double,
        val companyBalance: Double
    )

    fun calculatePrizes(entryFee: Double): CalculationResult {
        // Collected entry pool: 47 paid participants (1 is free)
        val collectedPool = (TOTAL_SLOTS - 1) * entryFee

        // 1. Calculate Top-9
        val top9Prizes = TOP_9_PERCENTAGES.map { percent ->
            val raw = collectedPool * percent
            ceil(raw / 5.0) * 5.0
        }
        val totalTop9Pool = top9Prizes.sum()

        // 2. Calculate Consolation
        val rawConsolation = collectedPool * CONSOLATION_PER_USER_PERCENT
        val consolationPrize = ceil(rawConsolation)
        val totalConsolationPool = consolationPrize * CONSOLATION_COUNT

        // 3. Company Balance
        val companyBalance = collectedPool - totalTop9Pool - totalConsolationPool

        return CalculationResult(
            top9Prizes = top9Prizes,
            consolationPrize = consolationPrize,
            totalPrizePool = totalTop9Pool,
            totalConsolationPool = totalConsolationPool,
            companyBalance = companyBalance
        )
    }
}
