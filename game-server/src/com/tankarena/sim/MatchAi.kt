package com.tankarena.sim

// Driving the computer tanks (src/tanks/computer.c, ported in ComputerTankAi).


/** Legacy control_computer_tank() for every computer tank (ComputerTankAi). */
internal fun Match.stepAiTanks() {
    aiObjectsTick = -1
    for (ai in allTanks) {
        if (ai.playerIndex >= 0 || !ai.computer || ai.armor <= 0) continue
        ai.applyIntent(ComputerTankAi.control(ai, aiWorld))
    }
}

/**
 * The legacy object list as the computer tanks see it: every object with its height `h`,
 * radius `r` (0 for the ones nothing bumps into) and goal value `gc`. Wrecks stay obstacles.
 */
internal fun Match.aiObjects(): List<AiObject> {
    val list = ArrayList<AiObject>()
    for (actor in objects.all) {
        val (x, y) = centreOf(actor) ?: continue
        when (actor) {
            is Tank -> list += AiObject(actor, x, y, actor.height, actor.spec.radius, tank = actor)
            is Train -> if (actor.alive) list += AiObject(actor, x, y, 1, Train.RADIUS, goal = actor.goalContribution)
            is B52 -> if (actor.alive) list += AiObject(actor, x, y, actor.height, B52.RADIUS, goal = actor.goalContribution)
            is Zeppelin -> if (actor.alive) list += AiObject(actor, x, y, 20, 20)
            is Mine -> if (!actor.isDead) list += AiObject(actor, x, y, 0, 3)
            is Rocket -> list += AiObject(actor, x, y, 6, 3)
            is Turret -> if (actor.isAlive) list += AiObject(actor, x, y, 1, 0, goal = actor.goalContribution)
            is Goal -> if (!actor.isClaimed) {
                list += AiObject(actor, x, y, 24, 0, goal = actor.contribution, privateGoal = actor.who == 3)
            }
            is Lock -> if (!actor.isFired) list += AiObject(actor, x, y, 24, 0, goal = actor.goalContribution)
            is Destroyer -> if (!actor.isFired) list += AiObject(actor, x, y, 24, 0, goal = actor.goalContribution)
            is Enforcer -> list += AiObject(actor, x, y, 24, 0)
            is Flag -> list += AiObject(actor, x, y, 0, 4)
            is Product -> list += AiObject(actor, x, y, 0, 4)
            else -> Unit
        }
    }
    for (soldier in soldiers) if (!soldier.removed) list += AiObject(soldier, soldier.x, soldier.y, 0, 5)
    for (bonus in bonuses) if (!bonus.removed) list += AiObject(bonus, bonus.x, bonus.y, 0, 2)
    for (bomb in abombs) if (!bomb.removed) list += AiObject(bomb, bomb.x, bomb.y, 0, 3)
    return list
}
