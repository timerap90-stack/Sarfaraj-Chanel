package com.example.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class ObstacleType {
    LOW_LASER,
    TALL_SPIRE,
    DOUBLE_BARRIER,
    HOVER_DRONE
}

enum class CollectibleType {
    ENERGY_ORB,
    SPEED_BOOST,
    AURA_SHIELD
}

data class GameObstacle(
    val id: Int,
    val type: ObstacleType,
    var x: Float,
    val yOffset: Float, // distance above ground
    val width: Float,
    val height: Float,
    var passed: Boolean = false
)

data class GameCollectible(
    val id: Int,
    val type: CollectibleType,
    var x: Float,
    val baseY: Float,
    val radius: Float,
    var bobPhase: Float = 0f,
    var collected: Boolean = false
)

data class GameParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val size: Float,
    val colorType: ParticleColor
)

enum class ParticleColor {
    CYAN,
    MAGENTA,
    AMBER,
    EMERALD,
    RED
}

data class Shockwave(
    var x: Float,
    var y: Float,
    var radius: Float,
    val maxRadius: Float,
    var alpha: Float,
    val colorType: ParticleColor
)

data class FloatingPopup(
    var x: Float,
    var y: Float,
    val text: String,
    var life: Float,
    val maxLife: Float,
    val colorType: ParticleColor
)

sealed interface GameEvent {
    data object Jump : GameEvent
    data object DoubleJump : GameEvent
    data class OrbCollected(val combo: Int) : GameEvent
    data object ShieldPickedUp : GameEvent
    data object BoostPickedUp : GameEvent
    data object ShieldBroken : GameEvent
    data object ObstacleDestroyedByBoost : GameEvent
    data class GameOver(
        val score: Long,
        val distanceMeters: Long,
        val orbsCollected: Long,
        val bestCombo: Long,
        val shieldsUsed: Long,
        val boostsUsed: Long,
        val peakIntensity: Long
    ) : GameEvent
}

data class RunnerSnapshot(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val runnerX: Float = 180f,
    val runnerY: Float = 0f,
    val runnerVy: Float = 0f,
    val runCycle: Float = 0f,
    val squashStretch: Float = 1f,
    val jumpsUsed: Int = 0,
    val score: Long = 0L,
    val distanceMeters: Long = 0L,
    val orbsCollected: Long = 0L,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val intensity: Float = 0f,
    val intensityTier: Int = 1,
    val peakIntensityTier: Int = 1,
    val speedUnitsPerSec: Float = 420f,
    val shieldRemainingSec: Float = 0f,
    val boostRemainingSec: Float = 0f,
    val invulnerableSec: Float = 0f,
    val screenShake: Float = 0f,
    val scrollOffset: Float = 0f,
    val obstacles: List<GameObstacle> = emptyList(),
    val collectibles: List<GameCollectible> = emptyList(),
    val particles: List<GameParticle> = emptyList(),
    val shockwaves: List<Shockwave> = emptyList(),
    val popups: List<FloatingPopup> = emptyList(),
    val runnerTrailY: List<Float> = emptyList(),
    val shieldsUsed: Int = 0,
    val boostsUsed: Int = 0
)

/**
 * Deterministic-step 60fps endless runner physics and procedural obstacle/power-up director.
 * Operates in normalized world coordinates (width = 1000f, groundY = 0f, positive Y = up).
 */
class EndlessRunnerEngine {
    companion object {
        const val WORLD_WIDTH = 1000f
        const val RUNNER_X = 185f
        const val RUNNER_WIDTH = 44f
        const val RUNNER_HEIGHT = 74f

        private const val BASE_SPEED = 430f
        private const val MAX_BASE_SPEED = 860f
        private const val GRAVITY = -2550f
        private const val JUMP_VELOCITY = 920f
        private const val DOUBLE_JUMP_VELOCITY = 820f
        private const val SHIELD_DURATION = 9.0f
        private const val BOOST_DURATION = 5.5f
    }

    private var runnerY = 0f
    private var runnerVy = 0f
    private var jumpsUsed = 0
    private var coyoteTimer = 0f
    private var jumpBufferTimer = 0f
    private var runCycle = 0f
    private var squashStretch = 1f

    private var distanceWorld = 0f
    private var bonusScore = 0.0
    private var orbsCollected = 0L
    private var combo = 0
    private var bestCombo = 0
    private var shieldsUsed = 0
    private var boostsUsed = 0
    private var peakIntensityTier = 1

    private var shieldTimer = 0f
    private var boostTimer = 0f
    private var invulnTimer = 0f
    private var screenShake = 0f

    private var nextEntityId = 1
    private var spawnDistanceCursor = 480f
    private var powerUpCooldownDistance = 1200f
    private var elapsedPlayTime = 0f

    var isRunning = false
        private set
    var isPaused = false
        private set
    var isGameOver = false
        private set

    private val obstacles = mutableListOf<GameObstacle>()
    private val collectibles = mutableListOf<GameCollectible>()
    private val particles = mutableListOf<GameParticle>()
    private val shockwaves = mutableListOf<Shockwave>()
    private val popups = mutableListOf<FloatingPopup>()
    private val trailY = ArrayDeque<Float>()

    fun startNewRun() {
        runnerY = 0f
        runnerVy = 0f
        jumpsUsed = 0
        coyoteTimer = 0f
        jumpBufferTimer = 0f
        runCycle = 0f
        squashStretch = 1f

        distanceWorld = 0f
        bonusScore = 0.0
        orbsCollected = 0L
        combo = 0
        bestCombo = 0
        shieldsUsed = 0
        boostsUsed = 0
        peakIntensityTier = 1

        shieldTimer = 0f
        boostTimer = 0f
        invulnTimer = 0f
        screenShake = 0f

        spawnDistanceCursor = 550f
        powerUpCooldownDistance = 900f
        elapsedPlayTime = 0f

        obstacles.clear()
        collectibles.clear()
        particles.clear()
        shockwaves.clear()
        popups.clear()
        trailY.clear()

        isRunning = true
        isPaused = false
        isGameOver = false
    }

    fun setPaused(paused: Boolean) {
        if (!isGameOver && isRunning) {
            isPaused = paused
        }
    }

    fun requestJump(): List<GameEvent> {
        if (!isRunning || isPaused || isGameOver) return emptyList()
        val events = mutableListOf<GameEvent>()
        if (runnerY <= 1f || coyoteTimer > 0f) {
            executeFirstJump(events)
        } else if (jumpsUsed < 2) {
            executeDoubleJump(events)
        } else {
            jumpBufferTimer = 0.12f
        }
        return events
    }

    private fun executeFirstJump(events: MutableList<GameEvent>) {
        runnerVy = JUMP_VELOCITY
        jumpsUsed = 1
        coyoteTimer = 0f
        squashStretch = 1.24f
        spawnBurst(RUNNER_X, 6f, 8, ParticleColor.CYAN, upwardBias = 120f)
        events += GameEvent.Jump
    }

    private fun executeDoubleJump(events: MutableList<GameEvent>) {
        runnerVy = DOUBLE_JUMP_VELOCITY
        jumpsUsed = 2
        squashStretch = 1.22f
        spawnBurst(RUNNER_X, runnerY + 12f, 12, ParticleColor.MAGENTA, upwardBias = -80f)
        shockwaves += Shockwave(
            x = RUNNER_X,
            y = runnerY + 10f,
            radius = 10f,
            maxRadius = 68f,
            alpha = 0.9f,
            colorType = ParticleColor.MAGENTA
        )
        events += GameEvent.DoubleJump
    }

    fun step(rawDt: Float): Pair<RunnerSnapshot, List<GameEvent>> {
        val dt = rawDt.coerceIn(0f, 0.04f)
        val events = mutableListOf<GameEvent>()

        if (!isRunning || isPaused || isGameOver) {
            updateVisualEffects(dt)
            return snapshot() to events
        }

        elapsedPlayTime += dt

        // Calculate speed & intensity (scales smoothly with distance + overdrive boost)
        val distanceMeters = (distanceWorld / 14f).toLong()
        val baseIntensity = ((distanceMeters.toFloat() / 1600f) * 0.85f + (elapsedPlayTime / 140f) * 0.15f)
            .coerceIn(0f, 1f)
        val baseSpeed = BASE_SPEED + (MAX_BASE_SPEED - BASE_SPEED) * baseIntensity
        val boostMultiplier = if (boostTimer > 0f) 1.65f else 1.0f
        val currentSpeed = baseSpeed * boostMultiplier

        val intensity = if (boostTimer > 0f) maxOf(baseIntensity, 0.92f) else baseIntensity
        val tier = when {
            boostTimer > 0f || intensity >= 0.78f -> 4
            intensity >= 0.50f -> 3
            intensity >= 0.24f -> 2
            else -> 1
        }
        if (tier > peakIntensityTier) peakIntensityTier = tier

        // Advance world scroll
        val dx = currentSpeed * dt
        distanceWorld += dx
        val scoreMult = if (boostTimer > 0f) 3.0 else (1.0 + combo * 0.1).coerceAtMost(2.5)
        bonusScore += (dx * 0.08) * scoreMult

        // Timers
        if (shieldTimer > 0f) shieldTimer = (shieldTimer - dt).coerceAtLeast(0f)
        if (boostTimer > 0f) boostTimer = (boostTimer - dt).coerceAtLeast(0f)
        if (invulnTimer > 0f) invulnTimer = (invulnTimer - dt).coerceAtLeast(0f)
        if (screenShake > 0f) screenShake = (screenShake - dt * 28f).coerceAtLeast(0f)
        if (jumpBufferTimer > 0f) jumpBufferTimer = (jumpBufferTimer - dt).coerceAtLeast(0f)

        // Runner vertical physics
        val wasGrounded = runnerY <= 0.5f
        runnerVy += GRAVITY * dt
        runnerY += runnerVy * dt

        if (runnerY <= 0f) {
            if (!wasGrounded && runnerVy < -180f) {
                squashStretch = 0.76f
                spawnBurst(RUNNER_X, 4f, 6, ParticleColor.CYAN, upwardBias = 70f)
            }
            runnerY = 0f
            runnerVy = 0f
            jumpsUsed = 0
            coyoteTimer = 0.09f
            if (jumpBufferTimer > 0f) {
                jumpBufferTimer = 0f
                executeFirstJump(events)
            }
        } else {
            if (coyoteTimer > 0f) coyoteTimer = (coyoteTimer - dt).coerceAtLeast(0f)
        }

        squashStretch += (1f - squashStretch) * (dt * 14f).coerceAtMost(1f)
        runCycle += (dx * 0.042f)

        val dustChance = if (boostTimer > 0f) 0.9f else 0.35f
        if (runnerY <= 1f && Random.nextFloat() < dustChance) {
            particles += GameParticle(
                x = RUNNER_X - 18f,
                y = 4f + Random.nextFloat() * 8f,
                vx = -currentSpeed * 0.45f - Random.nextFloat() * 80f,
                vy = 20f + Random.nextFloat() * 65f,
                life = 0.28f,
                maxLife = 0.28f,
                size = if (boostTimer > 0f) 5f else 3f,
                colorType = if (boostTimer > 0f) ParticleColor.MAGENTA else ParticleColor.CYAN
            )
        }

        // Trail history
        trailY.addFirst(runnerY + RUNNER_HEIGHT * 0.52f)
        if (trailY.size > 14) trailY.removeLast()

        // Spawn procedural obstacles & power-ups
        spawnDistanceCursor -= dx
        powerUpCooldownDistance -= dx
        if (spawnDistanceCursor <= 0f) {
            spawnNextPattern(baseIntensity)
        }

        // Move obstacles & check collisions
        val runnerLeft = RUNNER_X - RUNNER_WIDTH * 0.36f
        val runnerRight = RUNNER_X + RUNNER_WIDTH * 0.36f
        val runnerBottom = runnerY + 4f
        val runnerTop = runnerY + RUNNER_HEIGHT - 8f

        val obstacleIter = obstacles.iterator()
        while (obstacleIter.hasNext()) {
            val obs = obstacleIter.next()
            obs.x -= dx

            if (!obs.passed && obs.x + obs.width * 0.5f < runnerLeft) {
                obs.passed = true
                bonusScore += 15.0 * scoreMult
            }

            if (obs.x + obs.width < -120f) {
                obstacleIter.remove()
                continue
            }

            val obsLeft = obs.x - obs.width * 0.42f
            val obsRight = obs.x + obs.width * 0.42f
            val obsBottom = obs.yOffset
            val obsTop = obs.yOffset + obs.height

            val overlapsX = runnerRight > obsLeft && runnerLeft < obsRight
            val overlapsY = runnerTop > obsBottom && runnerBottom < obsTop

            if (overlapsX && overlapsY) {
                if (boostTimer > 0f) {
                    // Overdrive Speed Boost smashes right through obstacles!
                    obstacleIter.remove()
                    screenShake = 9f
                    bonusScore += 60.0
                    spawnBurst(obs.x, obsBottom + obs.height * 0.5f, 22, ParticleColor.MAGENTA, upwardBias = 160f)
                    shockwaves += Shockwave(obs.x, obsBottom + obs.height * 0.5f, 14f, 110f, 1f, ParticleColor.MAGENTA)
                    popups += FloatingPopup(obs.x, obsTop + 24f, "SMASH +60!", 0.75f, 0.75f, ParticleColor.MAGENTA)
                    events += GameEvent.ObstacleDestroyedByBoost
                } else if (invulnTimer > 0f) {
                    // Grace period after shield break
                } else if (shieldTimer > 0f) {
                    // Shield absorbs hit!
                    shieldTimer = 0f
                    invulnTimer = 0.85f
                    screenShake = 14f
                    obstacleIter.remove()
                    spawnBurst(RUNNER_X, runnerY + RUNNER_HEIGHT * 0.5f, 28, ParticleColor.CYAN, upwardBias = 140f)
                    shockwaves += Shockwave(RUNNER_X, runnerY + RUNNER_HEIGHT * 0.5f, 20f, 150f, 1f, ParticleColor.CYAN)
                    popups += FloatingPopup(RUNNER_X + 10f, runnerY + RUNNER_HEIGHT + 30f, "SHIELD SAVED!", 0.9f, 0.9f, ParticleColor.CYAN)
                    events += GameEvent.ShieldBroken
                } else {
                    // Fatal crash!
                    isRunning = false
                    isGameOver = true
                    screenShake = 20f
                    spawnBurst(RUNNER_X, runnerY + RUNNER_HEIGHT * 0.5f, 36, ParticleColor.RED, upwardBias = 180f)
                    shockwaves += Shockwave(RUNNER_X, runnerY + RUNNER_HEIGHT * 0.5f, 15f, 175f, 1f, ParticleColor.RED)
                    val finalScore = bonusScore.toLong()
                    val finalDist = (distanceWorld / 14f).toLong()
                    events += GameEvent.GameOver(
                        score = finalScore,
                        distanceMeters = finalDist,
                        orbsCollected = orbsCollected,
                        bestCombo = bestCombo.toLong(),
                        shieldsUsed = shieldsUsed.toLong(),
                        boostsUsed = boostsUsed.toLong(),
                        peakIntensity = peakIntensityTier.toLong()
                    )
                    break
                }
            }
        }

        // Move & collect collectibles / power-ups
        val colIter = collectibles.iterator()
        while (colIter.hasNext()) {
            val col = colIter.next()
            col.x -= dx
            col.bobPhase += dt * 5f
            val actualY = col.baseY + sin(col.bobPhase) * 8f

            if (col.x < -100f) {
                if (col.type == CollectibleType.ENERGY_ORB && !col.collected) {
                    combo = 0
                }
                colIter.remove()
                continue
            }

            val cx = RUNNER_X
            val cy = runnerY + RUNNER_HEIGHT * 0.5f
            val distSq = (col.x - cx) * (col.x - cx) + (actualY - cy) * (actualY - cy)
            val hitRadius = col.radius + 32f

            if (!col.collected && distSq <= hitRadius * hitRadius) {
                col.collected = true
                when (col.type) {
                    CollectibleType.ENERGY_ORB -> {
                        orbsCollected++
                        combo++
                        if (combo > bestCombo) bestCombo = combo
                        val pts = (25 * scoreMult).toLong()
                        bonusScore += pts.toDouble()
                        spawnBurst(col.x, actualY, 10, ParticleColor.AMBER, upwardBias = 90f)
                        val label = if (combo >= 3) "+$pts (${combo}x)" else "+$pts"
                        popups += FloatingPopup(col.x, actualY + 18f, label, 0.65f, 0.65f, ParticleColor.AMBER)
                        events += GameEvent.OrbCollected(combo)
                    }
                    CollectibleType.SPEED_BOOST -> {
                        boostsUsed++
                        boostTimer = BOOST_DURATION
                        bonusScore += 100.0
                        screenShake = 6f
                        spawnBurst(col.x, actualY, 22, ParticleColor.MAGENTA, upwardBias = 120f)
                        shockwaves += Shockwave(RUNNER_X, cy, 16f, 140f, 1f, ParticleColor.MAGENTA)
                        popups += FloatingPopup(RUNNER_X, runnerY + RUNNER_HEIGHT + 28f, "HYPER BOOST 3X!", 1.0f, 1.0f, ParticleColor.MAGENTA)
                        events += GameEvent.BoostPickedUp
                    }
                    CollectibleType.AURA_SHIELD -> {
                        shieldsUsed++
                        shieldTimer = SHIELD_DURATION
                        bonusScore += 50.0
                        spawnBurst(col.x, actualY, 20, ParticleColor.CYAN, upwardBias = 110f)
                        shockwaves += Shockwave(RUNNER_X, cy, 16f, 130f, 1f, ParticleColor.CYAN)
                        popups += FloatingPopup(RUNNER_X, runnerY + RUNNER_HEIGHT + 28f, "AURA SHIELD!", 1.0f, 1.0f, ParticleColor.CYAN)
                        events += GameEvent.ShieldPickedUp
                    }
                }
                colIter.remove()
            }
        }

        updateVisualEffects(dt)

        return snapshot(intensity, tier, currentSpeed) to events
    }

    private fun spawnNextPattern(intensity: Float) {
        val spawnX = WORLD_WIDTH + 120f
        val roll = Random.nextFloat()

        val obsType = when {
            intensity < 0.18f -> if (roll < 0.72f) ObstacleType.LOW_LASER else ObstacleType.TALL_SPIRE
            intensity < 0.48f -> when {
                roll < 0.40f -> ObstacleType.LOW_LASER
                roll < 0.72f -> ObstacleType.TALL_SPIRE
                else -> ObstacleType.DOUBLE_BARRIER
            }
            else -> when {
                roll < 0.28f -> ObstacleType.LOW_LASER
                roll < 0.56f -> ObstacleType.TALL_SPIRE
                roll < 0.80f -> ObstacleType.DOUBLE_BARRIER
                else -> ObstacleType.HOVER_DRONE
            }
        }

        val obs = when (obsType) {
            ObstacleType.LOW_LASER -> GameObstacle(
                id = nextEntityId++,
                type = obsType,
                x = spawnX,
                yOffset = 0f,
                width = 34f,
                height = 66f
            )
            ObstacleType.TALL_SPIRE -> GameObstacle(
                id = nextEntityId++,
                type = obsType,
                x = spawnX,
                yOffset = 0f,
                width = 36f,
                height = 116f
            )
            ObstacleType.DOUBLE_BARRIER -> GameObstacle(
                id = nextEntityId++,
                type = obsType,
                x = spawnX,
                yOffset = 0f,
                width = 78f,
                height = 72f
            )
            ObstacleType.HOVER_DRONE -> GameObstacle(
                id = nextEntityId++,
                type = obsType,
                x = spawnX,
                yOffset = 94f, // Player can run right underneath OR double-jump high over it!
                width = 56f,
                height = 38f
            )
        }
        obstacles += obs

        // Spawn collectible orbs or a power-up
        if (powerUpCooldownDistance <= 0f) {
            val powerType = if (Random.nextBoolean()) CollectibleType.SPEED_BOOST else CollectibleType.AURA_SHIELD
            val powerY = if (obsType == ObstacleType.HOVER_DRONE) 42f else 148f
            collectibles += GameCollectible(
                id = nextEntityId++,
                type = powerType,
                x = spawnX + 185f,
                baseY = powerY,
                radius = 22f
            )
            powerUpCooldownDistance = 1750f + Random.nextFloat() * 900f
        } else if (Random.nextFloat() < 0.72f) {
            // Orb arc over obstacle (or under drone!)
            val count = if (obsType == ObstacleType.DOUBLE_BARRIER) 3 else 2
            for (i in 0 until count) {
                val offset = (i - (count - 1) / 2f) * 48f
                val arcY = if (obsType == ObstacleType.HOVER_DRONE) {
                    38f
                } else {
                    obs.height + 58f - abs(offset) * 0.25f
                }
                collectibles += GameCollectible(
                    id = nextEntityId++,
                    type = CollectibleType.ENERGY_ORB,
                    x = spawnX + offset,
                    baseY = arcY,
                    radius = 14f,
                    bobPhase = i * 0.7f
                )
            }
        }

        // Dynamic gap shrinks gently with intensity while always remaining fair & jumpable
        val minGap = 390f - intensity * 85f
        val extraGap = Random.nextFloat() * (220f - intensity * 70f)
        spawnDistanceCursor = minGap + extraGap
    }

    private fun spawnBurst(x: Float, y: Float, count: Int, color: ParticleColor, upwardBias: Float) {
        repeat(count) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 90f + Random.nextFloat() * 280f
            val maxL = 0.28f + Random.nextFloat() * 0.35f
            particles += GameParticle(
                x = x,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed + upwardBias,
                life = maxL,
                maxLife = maxL,
                size = 3f + Random.nextFloat() * 5f,
                colorType = color
            )
        }
    }

    private fun updateVisualEffects(dt: Float) {
        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy -= 520f * dt
            p.life -= dt
            if (p.life <= 0f) pIter.remove()
        }

        val sIter = shockwaves.iterator()
        while (sIter.hasNext()) {
            val s = sIter.next()
            s.radius += (s.maxRadius - s.radius) * (dt * 11f).coerceAtMost(1f)
            s.alpha -= dt * 2.6f
            if (s.alpha <= 0f) sIter.remove()
        }

        val fIter = popups.iterator()
        while (fIter.hasNext()) {
            val f = fIter.next()
            f.y += 45f * dt
            f.life -= dt
            if (f.life <= 0f) fIter.remove()
        }
    }

    private fun snapshot(
        intensity: Float = 0f,
        tier: Int = 1,
        speed: Float = BASE_SPEED
    ): RunnerSnapshot {
        return RunnerSnapshot(
            isRunning = isRunning,
            isPaused = isPaused,
            isGameOver = isGameOver,
            runnerX = RUNNER_X,
            runnerY = runnerY,
            runnerVy = runnerVy,
            runCycle = runCycle,
            squashStretch = squashStretch,
            jumpsUsed = jumpsUsed,
            score = bonusScore.toLong(),
            distanceMeters = (distanceWorld / 14f).toLong(),
            orbsCollected = orbsCollected,
            combo = combo,
            bestCombo = bestCombo,
            intensity = intensity,
            intensityTier = tier,
            peakIntensityTier = peakIntensityTier,
            speedUnitsPerSec = speed,
            shieldRemainingSec = shieldTimer,
            boostRemainingSec = boostTimer,
            invulnerableSec = invulnTimer,
            screenShake = screenShake,
            scrollOffset = distanceWorld,
            obstacles = obstacles.map { it.copy() },
            collectibles = collectibles.map { it.copy() },
            particles = particles.map { it.copy() },
            shockwaves = shockwaves.map { it.copy() },
            popups = popups.map { it.copy() },
            runnerTrailY = trailY.toList(),
            shieldsUsed = shieldsUsed,
            boostsUsed = boostsUsed
        )
    }
}
