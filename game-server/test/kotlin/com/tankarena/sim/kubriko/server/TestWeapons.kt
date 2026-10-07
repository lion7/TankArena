package com.tankarena.sim.kubriko.server

import com.tankarena.input.PlayerIntentFrame

/** Cycle [playerIndex]'s weapon to [weapon]; fails instead of looping when the tank does not carry it. */
internal fun ServerMatchPrototype.selectWeaponForTest(weapon: Int, playerIndex: Int = 0) {
    val tank = checkNotNull(playerTankForTest(playerIndex)) { "player $playerIndex has no tank" }
    repeat(WEAPON_COUNT) {
        if (tank.currentWeapon == weapon) return
        tick(mapOf(playerIndex to PlayerIntentFrame(cycleWeaponRight = true)))
        repeat(16) { tick() }
    }
    check(tank.currentWeapon == weapon) { "tank never reached weapon $weapon (has ${tank.ammoCounts})" }
}
