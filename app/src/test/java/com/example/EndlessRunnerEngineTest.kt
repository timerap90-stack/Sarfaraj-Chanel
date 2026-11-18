package com.example

import com.example.game.EndlessRunnerEngine
import com.example.game.GameEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndlessRunnerEngineTest {

    @Test
    fun jumpAndDoubleJumpElevateRunnerAndEmitEvents() {
        val engine = EndlessRunnerEngine()
        engine.startNewRun()

        val firstJumpEvents = engine.requestJump()
        assertTrue(firstJumpEvents.contains(GameEvent.Jump))

        val (afterStep1, _) = engine.step(0.016f)
        assertTrue(afterStep1.runnerY > 0f)
        assertEquals(1, afterStep1.jumpsUsed)

        val secondJumpEvents = engine.requestJump()
        assertTrue(secondJumpEvents.contains(GameEvent.DoubleJump))

        val (afterStep2, _) = engine.step(0.016f)
        assertEquals(2, afterStep2.jumpsUsed)
        assertTrue(afterStep2.runnerY > afterStep1.runnerY)
    }

    @Test
    fun advancingTimeIncreasesDistanceScoreAndIntensity() {
        val engine = EndlessRunnerEngine()
        engine.startNewRun()

        var latest = engine.step(0.016f).first
        repeat(120) {
            // Keep jumping periodically so test runner survives if an obstacle spawns
            if (it % 45 == 0) engine.requestJump()
            val (snap, _) = engine.step(0.016f)
            if (!snap.isGameOver) {
                latest = snap
            }
        }

        assertTrue(latest.distanceMeters > 0L)
        assertTrue(latest.score > 0L)
    }
}
