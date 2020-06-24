package com.almasb.zeph.character.components

import com.almasb.fxgl.dsl.FXGL
import com.almasb.fxgl.dsl.spawn
import com.almasb.fxgl.entity.Entity
import com.almasb.fxgl.entity.SpawnData
import com.almasb.fxgl.entity.component.Component
import com.almasb.fxgl.entity.state.EntityState
import com.almasb.fxgl.entity.state.StateComponent
import com.almasb.zeph.Config
import com.almasb.zeph.ZephyriaApp
import com.almasb.zeph.character.CharacterEntity
import com.almasb.zeph.entity.character.component.NewAStarMoveComponent
import com.almasb.zeph.entity.character.component.NewCellMoveComponent
import com.almasb.zeph.item.Armor
import com.almasb.zeph.item.UsableItem
import com.almasb.zeph.item.Weapon
import com.almasb.zeph.skill.Skill
import javafx.geometry.Point2D

/**
 *
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
class CharacterActionComponent : Component() {

    private var skillCastTarget: CharacterEntity? = null
    private var attackTarget: CharacterEntity? = null
    private var pickUpTarget: Entity? = null
    private var moveTarget: Point2D? = null

    private lateinit var state: StateComponent
    private lateinit var char: CharacterEntity
    private lateinit var animationComponent: AnimationComponent
    private lateinit var moveComponent: NewCellMoveComponent

    private val astar: NewAStarMoveComponent
        get() = entity.getComponent(NewAStarMoveComponent::class.java)

    private val CASTING: EntityState = object : EntityState() {

    }

    private val MOVE: EntityState = object : EntityState() {

        override fun onUpdate(tpf: Double) {
            if (astar.isAtDestination) {
                // identify why we came here

                if (attackTarget != null) {
                    attack(attackTarget!!)
                } else if (pickUpTarget != null) {
                    pickUp(pickUpTarget!!)
                } else {
                    state.changeStateToIdle()
                }
            } else {
                if (attackTarget != null) {
                    if (moveTarget!!.x.toInt() != attackTarget!!.characterComponent.getTileX()
                            || moveTarget!!.y.toInt() != attackTarget!!.characterComponent.getTileY()) {
                        orderAttack(attackTarget!!)
                    }
                }
            }
        }
    }

    private val ATTACK: EntityState = object : EntityState() {
        override fun onEnteredFrom(prevState: EntityState) {
            // TODO: figure out correct attack loop
            animationComponent.loopAttack()
        }

        override fun onUpdate(tpf: Double) {
            if (char.characterComponent.isInWeaponRange(attackTarget!!)) {

                if (char.characterComponent.canAttack()) {
                    val dmg = char.characterComponent.attack(attackTarget!!)
                    FXGL.getAppCast<ZephyriaApp>().showDamage(dmg, attackTarget!!.center)

                    char.characterComponent.resetAtkTick()

                    if (attackTarget!!.hp.isZero) {
                        FXGL.getAppCast<ZephyriaApp>().playerKilledChar(attackTarget!!)
                        state.changeStateToIdle()
                    }
                }

            } else {
                orderAttack(attackTarget!!)
            }
        }
    }

    override fun onAdded() {
        char = entity as CharacterEntity
    }

    override fun onUpdate(tpf: Double) {
        if (state.isIdle) {
            when {
                animationComponent.isFacingLeft -> animationComponent.loopIdleLeft()
                animationComponent.isFacingRight -> animationComponent.loopIdleRight()
                animationComponent.isFacingUp -> animationComponent.loopIdleUp()
                animationComponent.isFacingDown -> animationComponent.loopIdleDown()
            }
            return
        }

        when {
            moveComponent.isMovingDown -> animationComponent.loopWalkDown()
            moveComponent.isMovingUp -> animationComponent.loopWalkUp()
            moveComponent.isMovingLeft -> animationComponent.loopWalkLeft()
            moveComponent.isMovingRight -> animationComponent.loopWalkRight()
        }
    }

    fun orderMove(x: Int, y: Int) {
        reset()
        state.changeState(MOVE)
        move(x, y)
    }

    fun orderAttack(target: CharacterEntity) {
        reset()
        attackTarget = target

        if (char.characterComponent.isInWeaponRange(target)) {
            attack(attackTarget!!)
        } else {
            move(attackTarget!!.cellX, attackTarget!!.cellY)
        }
    }

    fun orderSkillCast(skillIndex: Int, target: CharacterEntity) {
        reset()
        skillCastTarget = target

        // TODO: if char in cast range ...
        val isInCastRange = true

        if (isInCastRange) {
            val skill: Skill = char.characterComponent.skills[skillIndex]

            castSkill(skill, target)

        } else {
            move(target.cellX, target.cellY)
        }
    }

    private fun castSkill(skill: Skill, target: CharacterEntity) {
        state.changeState(CASTING)

        val castCallback = Runnable {
            if (!state.isIn(CASTING))
                return@Runnable

            state.changeStateToIdle()

            val projTextureName = skill.data.projectileTextureName

            val direction: Point2D = target.position.subtract(char.position)

            spawn("skillProjectile",
                    SpawnData(char.position)
                            .put("projectileTextureName", projTextureName)
                            .put("target", target)
                            .put("dir", direction)
            )
        }

        if (target.cellX > char.cellX) {
            animationComponent.playCastRight(castCallback)
        } else if (target.cellX < char.cellX) {
            animationComponent.playCastLeft(castCallback)
        } else if (target.cellY < char.cellY) {
            animationComponent.playCastUp(castCallback)
        } else {
            animationComponent.playCastDown(castCallback)
        }
    }

    fun orderPickUp(item: Entity) {
        reset()
        pickUpTarget = item

        if (char.distance(item) <= Config.SPRITE_SIZE) {
            pickUp(item)
        } else {
            // TODO: items should have smth like ItemComponent with cellX,cellY...
            val x = item.x.toInt() / Config.TILE_SIZE
            val y = item.y.toInt() / Config.TILE_SIZE
            move(x, y)
        }
    }

    fun orderIdle() {
        state.changeStateToIdle()
    }

    private fun move(cellX: Int, cellY: Int) {
        moveTarget = Point2D(cellX.toDouble(), cellY.toDouble())
        state.changeState(MOVE)
        entity.getComponent(NewAStarMoveComponent::class.java).moveToCell(cellX, cellY)
    }

    private fun attack(target: CharacterEntity) {
        state.changeState(ATTACK)
    }

    private fun pickUp(item: Entity) {
        item.getPropertyOptional<Weapon>("weapon").ifPresent {
            char.inventory.addItem(it)
        }

        item.getPropertyOptional<Armor>("armor").ifPresent {
            char.inventory.addItem(it)
        }

        item.getPropertyOptional<UsableItem>("usable").ifPresent {
            char.inventory.addItem(it)
        }

        item.removeFromWorld()

        state.changeStateToIdle()
    }

    private fun reset() {
        skillCastTarget = null
        attackTarget = null
        pickUpTarget = null
        moveTarget = null
    }
}