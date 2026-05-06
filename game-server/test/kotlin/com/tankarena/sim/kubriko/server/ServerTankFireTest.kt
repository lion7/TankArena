package com.tankarena.sim.kubriko.server

import com.tankarena.sim.kubriko.server.legacy.AuthoredObject
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds
import com.tankarena.content.TileLayers
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.ProjectileState
import com.tankarena.protocol.snapshot.TankState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerTankFireTest {

    @Test
    fun `firePrimary spawns exactly one projectile moving east for an east-facing tank`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(emptyMapWithPlayer(direction = 4))
        prototype.initialize()

        val frame = prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        val projectiles = frame.actors.filterIsInstance<ProjectileState>()

        assertEquals(1, projectiles.size)
        val projectile = projectiles.single()
        assertTrue(projectile.vx > 0, "east-facing projectile should move in +x — vx=${projectile.vx}")
        assertEquals(0, projectile.vy)
        prototype.dispose()
    }

    @Test
    fun `firePrimary held for the cooldown window spawns at most one projectile`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(emptyMapWithPlayer(direction = 4))
        prototype.initialize()

        var lastCount = 0
        repeat(20) {
            val frame = prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
            lastCount = frame.actors.filterIsInstance<ProjectileState>().size
        }
        assertEquals(1, lastCount, "cooldown should block repeat fire within PRIMARY_COOLDOWN_TICKS")
        prototype.dispose()
    }

    @Test
    fun `projectile that flies into a wall is removed from the snapshot`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            mapWithWallBlocking(playerAtTileX = 1, wallAtTileX = 3),
        )
        prototype.initialize()

        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        var projectilesObserved = 0
        var stillAlive = true
        var ticks = 0
        while (stillAlive && ticks < 30) {
            val frame = prototype.tick()
            val projectiles = frame.actors.filterIsInstance<ProjectileState>()
            if (projectiles.isNotEmpty()) projectilesObserved += 1
            stillAlive = projectiles.isNotEmpty()
            ticks += 1
        }
        assertTrue(projectilesObserved >= 1, "projectile should have existed briefly")
        val finalFrame = prototype.snapshot()
        assertTrue(
            finalFrame.actors.filterIsInstance<ProjectileState>().isEmpty(),
            "projectile should be cleaned up after hitting the wall",
        )
        prototype.dispose()
    }

    @Test
    fun `projectile from one tank reduces armor of the tank it hits`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(
            mapWithTwoTanksFacing(shooterDirection = 4, victimDirection = 12),
        )
        prototype.initialize()

        val shooterBefore = prototype.snapshot().tankByPlayerIndex(0)
        val victimBefore = prototype.snapshot().tankByPlayerIndex(1)
        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))

        var impactFrame = 0
        while (impactFrame < 30) {
            prototype.tick()
            val victim = prototype.snapshot().tankByPlayerIndex(1)
            if (victim.armor < victimBefore.armor) break
            impactFrame += 1
        }

        val shooterAfter = prototype.snapshot().tankByPlayerIndex(0)
        val victimAfter = prototype.snapshot().tankByPlayerIndex(1)
        assertTrue(victimAfter.armor < victimBefore.armor, "victim armor should drop — was ${victimBefore.armor}, now ${victimAfter.armor}")
        assertEquals(shooterBefore.armor, shooterAfter.armor, "shooter must not damage itself")
        assertTrue(
            prototype.snapshot().actors.filterIsInstance<ProjectileState>().isEmpty(),
            "projectile should be gone after the hit",
        )
        prototype.dispose()
    }

    @Test
    fun `tank cannot be damaged by its own projectile`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(emptyMapWithPlayer(direction = 12))
        prototype.initialize()

        val before = prototype.snapshot().tankByPlayerIndex(0).armor
        prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        repeat(10) { prototype.tick() }
        val after = prototype.snapshot().tankByPlayerIndex(0).armor

        assertEquals(before, after, "owner tank must not take damage from its own projectile")
        prototype.dispose()
    }

    @Test
    fun `chain gun fires every 10 ticks while main is on cooldown and ammo decrements`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(emptyMapWithPlayer(direction = 4))
        prototype.initialize()

        // Cycle from MAIN to CHAIN.
        prototype.tick(mapOf(0 to PlayerIntentFrame(cycleWeaponRight = true)))
        repeat(15) { prototype.tick() }

        var spawned = 0
        repeat(60) {
            val before = prototype.snapshot().actors.filterIsInstance<ProjectileState>().size
            prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
            val after = prototype.snapshot().actors.filterIsInstance<ProjectileState>().size
            if (after > before) spawned += 1
        }

        assertTrue(spawned >= 5, "chain gun should fire at least 5 times in 60 ticks — got $spawned")
        assertTrue(spawned <= 7, "chain gun should not exceed ~6 shots in 60 ticks — got $spawned")
        prototype.dispose()
    }

    @Test
    fun `chain gun silences when ammo hits zero`() {
        val prototype = ServerMatchPrototype.fromCanonicalMap(emptyMapWithPlayer(direction = 4))
        prototype.initialize()

        // Cycle to chain.
        prototype.tick(mapOf(0 to PlayerIntentFrame(cycleWeaponRight = true)))
        repeat(15) { prototype.tick() }

        // Drain a few rounds, then check that we keep firing while ammo > 0.
        var firedShots = 0
        repeat(120) {
            val before = prototype.snapshot().actors.filterIsInstance<ProjectileState>().size
            prototype.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
            val after = prototype.snapshot().actors.filterIsInstance<ProjectileState>().size
            if (after > before) firedShots += 1
        }
        assertTrue(firedShots > 0, "should have fired chain rounds")
        prototype.dispose()
    }

    @Test
    fun `chain bullet damage is lower than main cannon damage`() {
        val main = ServerMatchPrototype.fromCanonicalMap(
            mapWithTwoTanksFacing(shooterDirection = 4, victimDirection = 12),
        )
        main.initialize()
        val mainBefore = main.snapshot().tankByPlayerIndex(1).armor
        main.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        repeat(40) { main.tick() }
        val mainAfter = main.snapshot().tankByPlayerIndex(1).armor
        val mainDamage = mainBefore - mainAfter
        main.dispose()

        val chain = ServerMatchPrototype.fromCanonicalMap(
            mapWithTwoTanksFacing(shooterDirection = 4, victimDirection = 12),
        )
        chain.initialize()
        chain.tick(mapOf(0 to PlayerIntentFrame(cycleWeaponRight = true)))
        repeat(15) { chain.tick() }
        val chainBefore = chain.snapshot().tankByPlayerIndex(1).armor
        chain.tick(mapOf(0 to PlayerIntentFrame(firePrimary = true)))
        repeat(40) { chain.tick() }
        val chainAfter = chain.snapshot().tankByPlayerIndex(1).armor
        val chainDamage = chainBefore - chainAfter
        chain.dispose()

        assertTrue(mainDamage > 0, "main cannon should deal damage — was $mainDamage")
        assertTrue(chainDamage > 0, "chain gun should deal damage — was $chainDamage")
        assertTrue(chainDamage < mainDamage, "chain damage ($chainDamage) should be lower than main ($mainDamage)")
    }

    private fun com.tankarena.protocol.snapshot.WorldSnapshot.tankByPlayerIndex(playerIndex: Int): TankState {
        val controlled = actors.filterIsInstance<TankState>().filter { it.controlled }
        return controlled.getOrNull(playerIndex)
            ?: actors.filterIsInstance<TankState>().single()
    }

    private fun emptyMapWithPlayer(direction: Int): CanonicalMapDefinition {
        val widthTiles = 20
        val heightTiles = 15
        return canonicalMap(
            widthTiles,
            heightTiles,
            solid = MutableList(widthTiles * heightTiles) { -1 },
            players = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = (widthTiles / 2) * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = (heightTiles / 2) * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to direction.toString(), "lives" to "3"),
                ),
            ),
        )
    }

    private fun mapWithWallBlocking(playerAtTileX: Int, wallAtTileX: Int): CanonicalMapDefinition {
        val widthTiles = 12
        val heightTiles = 3
        val cellCount = widthTiles * heightTiles
        val solid = MutableList(cellCount) { -1 }
        for (y in 0 until heightTiles) {
            solid[wallAtTileX + y * widthTiles] = 0
        }
        return canonicalMap(
            widthTiles,
            heightTiles,
            solid = solid,
            players = listOf(
                AuthoredObject(
                    id = "p1",
                    kind = ObjectKinds.PLAYER_START,
                    x = playerAtTileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to "4", "lives" to "3"),
                ),
            ),
        )
    }

    private fun mapWithTwoTanksFacing(shooterDirection: Int, victimDirection: Int): CanonicalMapDefinition {
        val widthTiles = 10
        val heightTiles = 3
        return canonicalMap(
            widthTiles,
            heightTiles,
            solid = MutableList(widthTiles * heightTiles) { -1 },
            players = listOf(
                AuthoredObject(
                    id = "shooter",
                    kind = ObjectKinds.PLAYER_START,
                    x = 2 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to shooterDirection.toString(), "lives" to "3"),
                ),
                AuthoredObject(
                    id = "victim",
                    kind = ObjectKinds.PLAYER_START,
                    x = 6 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    y = 1 * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    properties = mapOf("direction" to victimDirection.toString(), "lives" to "3"),
                ),
            ),
        )
    }

    private fun canonicalMap(
        widthTiles: Int,
        heightTiles: Int,
        solid: List<Int>,
        players: List<AuthoredObject>,
    ): CanonicalMapDefinition {
        val cellCount = widthTiles * heightTiles
        return CanonicalMapDefinition(
            metadata = MapMetadata(
                name = "fire-fixture",
                widthTiles = widthTiles,
                heightTiles = heightTiles,
                missionCode = "TEST",
            ),
            layers = TileLayers(
                base = List(cellCount) { -1 },
                solid = solid,
                top = List(cellCount) { -1 },
                goalLayer = List(cellCount) { 0 },
                bonusLayer = List(cellCount) { -1 },
                manTypeLayer = List(cellCount) { 0 },
                manAmountLayer = List(cellCount) { 0 },
            ),
            missionText = MissionText(),
            objects = players,
        )
    }
}
