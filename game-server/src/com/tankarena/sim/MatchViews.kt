package com.tankarena.sim

import com.tankarena.core.DetMath
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.protocol.snapshot.TerrainMaterial
import com.tankarena.protocol.Team
import com.tankarena.protocol.snapshot.AbombState
import com.tankarena.protocol.snapshot.ActorState
import com.tankarena.protocol.snapshot.BonusState
import com.tankarena.protocol.snapshot.ManState
import com.tankarena.protocol.snapshot.TileState
import com.tankarena.protocol.snapshot.B52State
import com.tankarena.protocol.snapshot.DestroyerState
import com.tankarena.protocol.snapshot.EnforcerState
import com.tankarena.protocol.snapshot.FlagState
import com.tankarena.protocol.snapshot.FlamePartState
import com.tankarena.protocol.snapshot.GoalState
import com.tankarena.protocol.snapshot.LockState
import com.tankarena.protocol.snapshot.ProductState
import com.tankarena.protocol.snapshot.TrainState
import com.tankarena.protocol.snapshot.WarpState
import com.tankarena.protocol.snapshot.ZeppelinState
import com.tankarena.protocol.snapshot.LockMarker
import com.tankarena.protocol.snapshot.LockMarkerState
import com.tankarena.protocol.snapshot.MineState
import com.tankarena.protocol.snapshot.ProjectileKind
import com.tankarena.protocol.snapshot.ProjectileState
import com.tankarena.protocol.snapshot.RadarColor
import com.tankarena.protocol.snapshot.RadarContact
import com.tankarena.protocol.snapshot.RadarContactKind
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.protocol.snapshot.TurretState
import com.tankarena.protocol.snapshot.WorldSnapshot
import kotlin.math.roundToInt

// What a match shows: the snapshot sent to clients, each player's radar and rocket lock marker.

/**
 * Legacy write_radar() (src/game/radar.c): every live tank including the player's own, every
 * turret and every rocket; the client keeps those within 15 tiles of the player.
 */
/**
 * Legacy write_radar() (src/game/radar.c): the vehicles (not wrecks, not cloaked ones — the viewer's
 * own included), bombers, trains, turrets and rockets, coloured for the viewer. The client shows those
 * within 15 tiles.
 */
internal fun Match.buildRadar(
    self: Tank,
    tanks: List<Tank>,
    turrets: List<Turret>,
    @Suppress("UNUSED_PARAMETER") goals: List<Goal>,
): List<RadarContact> {
    val contacts = mutableListOf<RadarContact>()
    fun add(actor: Any, x: Int, y: Int, kind: RadarContactKind, color: RadarColor) {
        val id = actorIds[actor] ?: return
        contacts += RadarContact(actorId = id, approximateX = x, approximateY = y, kind = kind, color = color)
    }
    val dual = mode == MatchMode.DUAL
    for (tank in tanks) {
        if (tank.armor <= 0 || tank.cloaked) continue
        val color = when {
            tank === self -> RadarColor.SELF
            dual -> if (tank.team == self.team) RadarColor.FRIEND else RadarColor.ENEMY
            tank.computer && tank.team != 0 -> RadarColor.ENEMY
            else -> RadarColor.FRIEND
        }
        add(tank, tank.positionX, tank.positionY, RadarContactKind.TANK, color)
    }
    for (b52 in b52s) {
        if (b52.alive && b52.armor > 0) add(b52, b52.positionX, b52.positionY, RadarContactKind.TANK, RadarColor.ENEMY)
    }
    for (car in trains) {
        if (car.alive) add(car, car.positionX, car.positionY, RadarContactKind.TANK, RadarColor.ENEMY)
    }
    // A turret is white when it shoots at the other side than the viewer's.
    val viewerGood = self.team == 0
    for (turret in turrets) {
        if (!turret.isAlive) continue
        val friendly = when (turret.shootAt) {
            1 -> !viewerGood
            2 -> viewerGood
            3 -> dual && self.playerIndex.side == 0
            4 -> dual && self.playerIndex.side == 1
            else -> false
        }
        add(turret, turret.positionX, turret.positionY, RadarContactKind.TURRET, if (friendly) RadarColor.FRIEND else RadarColor.ENEMY)
    }
    for (rocket in objects.all.filterIsInstance<Rocket>()) {
        if (!rocket.isDead) add(rocket, rocket.positionX.toInt(), rocket.positionY.toInt(), RadarContactKind.PROJECTILE, RadarColor.ROCKET)
    }
    return contacts
}

/** Bonuses, soldiers and waiting A-bombs as wire states. */
internal fun Match.smallObjectStates(): List<ActorState> {
    if (bonuses.isEmpty() && soldiers.isEmpty() && abombs.isEmpty() && flameParts.isEmpty()) return emptyList()
    val states = ArrayList<ActorState>(bonuses.size + soldiers.size + abombs.size + flameParts.size)
    for (part in flameParts) states += FlamePartState(part.id, part.x.toInt(), part.y.toInt(), part.frame)
    for (bonus in bonuses) states += BonusState(bonus.id, bonus.x, bonus.y, bonus.type)
    for (soldier in soldiers) states += ManState(soldier.id, soldier.x, soldier.y, soldier.direction, soldier.frame)
    for (bomb in abombs) {
        val tile = terrainGrid.at(bomb.x / LEGACY_TILE_SIZE, bomb.y / LEGACY_TILE_SIZE)
        states += AbombState(
            actorId = bomb.id,
            x = bomb.x,
            y = bomb.y,
            countdown = bomb.countdown,
            exploding = bomb.exploding,
            hidden = tile.material == TerrainMaterial.WATER,
        )
    }
    return states
}

/** Legacy tank_write_marker(): which marker picture to show, and where. */
internal fun Match.lockMarkerFor(tank: Tank): LockMarker? {
    if (tank.lockCameraTicks <= 0) return null
    val state = when {
        !tank.lockActive -> LockMarkerState.LAUNCHED
        mode != MatchMode.DUAL && !withinRadius(tank.positionX, tank.positionY, tank.lockX, tank.lockY, LOCK_OUT_OF_RANGE_PX) ->
            LockMarkerState.OUT_OF_RANGE
        rocketTargetAt(tank.lockX, tank.lockY, tank) != null -> LockMarkerState.ON_TARGET
        else -> LockMarkerState.SEARCHING
    }
    return LockMarker(tank.lockX, tank.lockY, state)
}

internal fun Match.buildSnapshot(drainEvents: Boolean): WorldSnapshot {
    val states = objects.all.mapNotNull { actor ->
        val id = actorIds[actor] ?: return@mapNotNull null
        projectToWireState(actor, id)
    }
    val events = when {
        pendingEvents.isEmpty() -> emptyList()
        drainEvents -> pendingEvents.toList().also { pendingEvents.clear() }
        else -> pendingEvents.toList()
    }
    return WorldSnapshot(
        tick = currentTick,
        actors = states + smallObjectStates(),
        events = events,
        changedTiles = changedTileStates(),
        flash = flash,
    )
}

/** The tiles whose pictures changed (legacy rewrite_block()), for the client's terrain. */
internal fun Match.changedTileStates(): List<TileState> = structures.changedTiles.map { index ->
    val tileX = index % mapMetadata.widthTiles
    val tileY = index / mapMetadata.widthTiles
    TileState(
        index = index,
        base = structures.base[index],
        solid = structures.solid[index],
        top = structures.top[index],
        solidStanding = structures.isStanding(tileX, tileY),
        topIntact = structures.isTopIntact(tileX, tileY),
    )
}

internal fun Match.projectToWireState(actor: SceneObject<*>, id: Long): ActorState? {
    return when (actor) {
        // A player's vehicle whose wreck blew up waits for its respawn unseen.
        is Tank -> if (actor.wreckGone) null else {
            TankState(
                actorId = id,
                team = actor.team.toProtocolTeam(),
                tankType = actor.tankType,
                x = actor.positionX,
                y = actor.positionY,
                vx = actor.velocityX,
                vy = actor.velocityY,
                bodyDirection = actor.bodyDirection,
                turretDirection = actor.turretDirection,
                armor = actor.armor,
                alive = actor.armor > 0,
                invulnerable = actor.invulnerableTicks > 0,
                controlled = actor.playerIndex >= 0,
                primaryCooldownTicks = actor.primaryCooldownTicks,
                currentWeapon = actor.currentWeapon,
                height = actor.height,
                // Legacy write_tank(): ARMOR overlay while the shield key works or the start shield blinks on.
                shielded = actor.shieldRingVisible,
                chopper = actor.kind == VehicleKind.CHOPPER,
                cloaked = actor.cloaked,
                cloakFlash = actor.cloakFlash,
                lights = actor.headlights(),
            )
        }

        is Turret -> {
            val size = actor.body.size
            val position = actor.body.position
            TurretState(
                actorId = id,
                team = actor.team.toProtocolTeam(),
                turretType = actor.turretType,
                x = (position.x + size.width / 2f).toInt(),
                y = (position.y + size.height / 2f).toInt(),
                turretDirection = actor.turretDirection,
                alive = actor.armor > 0,
                primaryCooldownTicks = actor.cooldownTicks,
            )
        }

        is Goal -> {
            val size = actor.body.size
            val position = actor.body.position
            GoalState(
                actorId = id,
                team = when (actor.who) {
                    0 -> Team.PLAYER
                    1 -> Team.ENEMY
                    else -> Team.NEUTRAL
                },
                x = (position.x + size.width / 2f).toInt(),
                y = (position.y + size.height / 2f).toInt(),
                captured = actor.isClaimed,
            )
        }

        is Projectile -> ProjectileState(
            actorId = id,
            ownerId = actor.ownerActorId,
            x = actor.positionX,
            y = actor.positionY,
            vx = actor.velocityX.roundToInt(),
            vy = actor.velocityY.roundToInt(),
            ownerKind = actor.ownerKind,
            kind = when (actor.weaponKind) {
                WEAPON_CHAIN -> ProjectileKind.CHAIN
                WEAPON_FLAME -> ProjectileKind.FLAME
                SHOT_SMALL_ROCKET -> ProjectileKind.SROCK
                else -> ProjectileKind.BULLET
            },
            rangeLeft = actor.rangeLeft,
            direction = com.tankarena.core.LegacyDirections.fromAngle((actor.velocityX * 1000).roundToInt(), (actor.velocityY * 1000).roundToInt()),
        )

        is Rocket -> ProjectileState(
            actorId = id,
            ownerId = actor.ownerActorId,
            x = actor.positionX.toInt(),
            y = actor.positionY.toInt(),
            kind = ProjectileKind.ROCKET,
        )

        is Mortar -> ProjectileState(
            actorId = id,
            ownerId = actor.ownerActorId,
            x = actor.positionX.toInt(),
            y = actor.positionY.toInt(),
            kind = if (actor.dropHeight > 0) ProjectileKind.PLANEBOMB else ProjectileKind.MORTAR,
            rangeLeft = (actor.travelTicksRemaining * DetMath.hypot(actor.velocityX, actor.velocityY)).toInt(),
            dropHeight = actor.dropHeight,
        )

        is Mine -> MineState(
            actorId = id,
            x = actor.positionX,
            y = actor.positionY,
            armed = actor.isActive,
            heavy = actor.heavy,
            hidden = terrainGrid.at(actor.positionX / LEGACY_TILE_SIZE, actor.positionY / LEGACY_TILE_SIZE).material == TerrainMaterial.WATER,
        )

        is Flag -> {
            val size = actor.body.size
            val position = actor.body.position
            FlagState(
                actorId = id,
                flagType = actor.flagType,
                number = actor.number,
                x = (position.x + size.width / 2f).toInt(),
                y = (position.y + size.height / 2f).toInt(),
                isCarried = actor.isCarried,
            )
        }

        is Product -> {
            val size = actor.body.size
            val position = actor.body.position
            ProductState(
                actorId = id,
                productType = actor.productType,
                price = actor.price,
                x = (position.x + size.width / 2f).toInt(),
                y = (position.y + size.height / 2f).toInt(),
                isCollected = actor.isCollected,
            )
        }

        is Lock -> {
            val size = actor.body.size
            val position = actor.body.position
            LockState(
                actorId = id,
                activation = actor.activation,
                target = actor.target,
                x = (position.x + size.width / 2f).toInt(),
                y = (position.y + size.height / 2f).toInt(),
                isFired = actor.isFired,
            )
        }

        is Warp -> {
            val size = actor.body.size
            val position = actor.body.position
            WarpState(
                actorId = id,
                targetX = actor.targetX,
                targetY = actor.targetY,
                x = (position.x + size.width / 2f).toInt(),
                y = (position.y + size.height / 2f).toInt(),
            )
        }

        is Destroyer -> {
            val size = actor.body.size
            val position = actor.body.position
            DestroyerState(
                actorId = id,
                radius = actor.radius,
                what = actor.what,
                immediate = actor.immediate,
                x = (position.x + size.width / 2f).toInt(),
                y = (position.y + size.height / 2f).toInt(),
                isFired = actor.isFired,
            )
        }

        is Enforcer -> {
            val size = actor.body.size
            val position = actor.body.position
            EnforcerState(
                actorId = id,
                radius = actor.radius,
                weapon = actor.weapon,
                delay = actor.delay,
                good = actor.good,
                bad = actor.bad,
                x = (position.x + size.width / 2f).toInt(),
                y = (position.y + size.height / 2f).toInt(),
            )
        }

        is Train -> TrainState(
            actorId = id,
            x = actor.positionX,
            y = actor.positionY,
            isEngine = actor.isEngine,
            armor = actor.armor,
            alive = actor.alive,
            picture = actor.picture,
            direction = actor.direction,
        )

        is Zeppelin -> ZeppelinState(
            actorId = id,
            x = actor.positionX,
            y = actor.positionY,
            alive = actor.alive,
            frame = actor.frame,
        )

        is B52 -> B52State(
            actorId = id,
            x = actor.positionX,
            y = actor.positionY,
            armor = actor.armor,
            alive = actor.alive,
            bombCount = actor.bombCount,
            heading = actor.heading,
            height = actor.height,
            bombBay = actor.bomb,
            wreck = actor.phase == B52.Phase.WRECK,
            onWater = terrainGrid.at(actor.positionX / LEGACY_TILE_SIZE, actor.positionY / LEGACY_TILE_SIZE).material == TerrainMaterial.WATER,
            lightOn = actor.lightOn,
        )

        else -> null
    }
}
