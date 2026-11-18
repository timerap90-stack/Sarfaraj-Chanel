package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Low-latency procedural synthwave music & polyphonic sound effects engine.
 * Synthesizes 16-bit PCM directly into an Android AudioTrack so tempo, drum density,
 * bass filter brightness, and arpeggio layers respond seamlessly to gameplay intensity.
 */
class ProceduralAudioEngine {
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHUNK_SAMPLES = 512
        private const val MAX_SFX_VOICES = 10

        // A minor / Dorian synthwave bass MIDI notes (16 steps)
        private val BASS_PATTERN = intArrayOf(
            33, 33, 45, 33, 36, 36, 48, 36,
            29, 29, 41, 29, 31, 31, 43, 40
        )

        // Arpeggio lead MIDI notes (16 steps)
        private val ARP_PATTERN = intArrayOf(
            57, 60, 64, 67, 69, 67, 64, 60,
            53, 57, 60, 64, 55, 59, 62, 67
        )

        private fun midiToHz(midi: Int): Float {
            return (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
        }
    }

    private enum class VoiceKind {
        SINE_SWEEP,
        SAW_SWEEP,
        SQUARE_CHIME,
        NOISE_BURST
    }

    private class SfxVoice {
        var active = false
        var kind = VoiceKind.SINE_SWEEP
        var startFreq = 440f
        var endFreq = 440f
        var elapsed = 0f
        var duration = 0.2f
        var gain = 0.5f
        var phase = 0f
    }

    @Volatile var musicEnabled: Boolean = true
    @Volatile var sfxEnabled: Boolean = true
    @Volatile var isInGameplay: Boolean = false
    @Volatile var isPaused: Boolean = false
    @Volatile var intensity: Float = 0.0f // 0f..1f
    @Volatile var boostActive: Boolean = false
    @Volatile var shieldActive: Boolean = false
    @Volatile var currentBpm: Int = 118
        private set

    @Volatile private var isRunning = false
    @Volatile private var isForeground = true

    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    private val sfxLock = Any()
    private val voices = Array(MAX_SFX_VOICES) { SfxVoice() }

    fun start() {
        if (isRunning) return
        isRunning = true
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_SAMPLES * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        audioThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            runSynthLoop(track)
        }, "NeonDashAudioThread").apply { start() }
    }

    fun setForeground(foreground: Boolean) {
        isForeground = foreground
        val track = audioTrack ?: return
        try {
            if (foreground) {
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
            } else {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
            }
        } catch (_: IllegalStateException) {
        }
    }

    fun release() {
        isRunning = false
        try {
            audioThread?.join(300)
        } catch (_: InterruptedException) {
        }
        audioThread = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: IllegalStateException) {
        }
        audioTrack = null
    }

    fun playJump() {
        if (!sfxEnabled) return
        triggerVoice(VoiceKind.SINE_SWEEP, 230f, 610f, 0.13f, 0.42f)
    }

    fun playDoubleJump() {
        if (!sfxEnabled) return
        triggerVoice(VoiceKind.SAW_SWEEP, 340f, 920f, 0.16f, 0.36f)
        triggerVoice(VoiceKind.SINE_SWEEP, 520f, 1180f, 0.14f, 0.30f)
    }

    fun playOrbCollect(combo: Int) {
        if (!sfxEnabled) return
        val scale = floatArrayOf(523.25f, 587.33f, 659.25f, 783.99f, 880.0f, 1046.5f)
        val base = scale[(combo.coerceAtLeast(0)) % scale.size]
        triggerVoice(VoiceKind.SQUARE_CHIME, base, base * 1.5f, 0.11f, 0.25f)
    }

    fun playShieldPickup() {
        if (!sfxEnabled) return
        triggerVoice(VoiceKind.SINE_SWEEP, 392f, 784f, 0.24f, 0.40f)
        triggerVoice(VoiceKind.SQUARE_CHIME, 587.33f, 1174.66f, 0.28f, 0.28f)
    }

    fun playBoostPickup() {
        if (!sfxEnabled) return
        triggerVoice(VoiceKind.SAW_SWEEP, 180f, 1240f, 0.32f, 0.42f)
        triggerVoice(VoiceKind.SINE_SWEEP, 440f, 1320f, 0.28f, 0.32f)
    }

    fun playShieldBreak() {
        if (!sfxEnabled) return
        triggerVoice(VoiceKind.NOISE_BURST, 900f, 200f, 0.26f, 0.55f)
        triggerVoice(VoiceKind.SINE_SWEEP, 540f, 110f, 0.24f, 0.45f)
    }

    fun playCrash() {
        if (!sfxEnabled) return
        triggerVoice(VoiceKind.SAW_SWEEP, 210f, 38f, 0.42f, 0.58f)
        triggerVoice(VoiceKind.NOISE_BURST, 400f, 80f, 0.38f, 0.50f)
    }

    fun playNewHighScore() {
        if (!sfxEnabled) return
        triggerVoice(VoiceKind.SQUARE_CHIME, 523.25f, 523.25f, 0.12f, 0.30f)
        triggerVoice(VoiceKind.SQUARE_CHIME, 659.25f, 783.99f, 0.24f, 0.32f)
        triggerVoice(VoiceKind.SINE_SWEEP, 783.99f, 1046.50f, 0.35f, 0.38f)
    }

    private fun triggerVoice(
        kind: VoiceKind,
        startFreq: Float,
        endFreq: Float,
        duration: Float,
        gain: Float
    ) {
        synchronized(sfxLock) {
            val slot = voices.firstOrNull { !it.active } ?: voices[0]
            slot.active = true
            slot.kind = kind
            slot.startFreq = startFreq
            slot.endFreq = endFreq
            slot.elapsed = 0f
            slot.duration = duration
            slot.gain = gain
            slot.phase = 0f
        }
    }

    private fun runSynthLoop(track: AudioTrack) {
        val pcmBuffer = ShortArray(CHUNK_SAMPLES)
        val dt = 1f / SAMPLE_RATE

        var stepIndex = 0
        var stepTimer = 0f
        var kickEnv = 0f
        var kickPhase = 0f
        var snareEnv = 0f
        var hatEnv = 0f

        var bassPhase = 0f
        var bassEnv = 0f
        var bassFreq = midiToHz(BASS_PATTERN[0])
        var bassLowPass = 0f

        var arpPhase = 0f
        var arpEnv = 0f
        var arpFreq = midiToHz(ARP_PATTERN[0])

        var padPhase = 0f
        var smoothedIntensity = 0f

        while (isRunning) {
            if (!isForeground) {
                try {
                    Thread.sleep(35)
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            val targetIntensity = if (isInGameplay && !isPaused) intensity.coerceIn(0f, 1f) else 0.08f
            val boost = boostActive && isInGameplay && !isPaused
            val playMusic = musicEnabled && !isPaused

            val targetBpm = when {
                !isInGameplay -> 108f
                isPaused -> 100f
                else -> 118f + targetIntensity * 40f + if (boost) 16f else 0f
            }
            currentBpm = targetBpm.toInt()
            val stepDuration = 60f / targetBpm / 4f // 16th-note step duration

            for (i in 0 until CHUNK_SAMPLES) {
                smoothedIntensity += (targetIntensity - smoothedIntensity) * 0.0005f

                stepTimer += dt
                if (stepTimer >= stepDuration) {
                    stepTimer -= stepDuration
                    stepIndex = (stepIndex + 1) and 15

                    if (playMusic) {
                        // Kick on 0, 4, 8, 12
                        if ((stepIndex and 3) == 0) {
                            kickEnv = 1f
                            kickPhase = 0f
                        }
                        // Snare on 4, 12 + intensity fills on step 15
                        if ((stepIndex == 4 || stepIndex == 12) && (smoothedIntensity > 0.14f || isInGameplay)) {
                            snareEnv = 0.7f + 0.3f * smoothedIntensity
                        } else if (stepIndex == 15 && (smoothedIntensity > 0.68f || boost)) {
                            snareEnv = 0.65f
                        }
                        // Hi-hat on offbeats -> all 16ths at high intensity
                        val hatTrigger = when {
                            boost || smoothedIntensity > 0.62f -> (stepIndex and 1) == 1 || stepIndex % 4 == 2
                            smoothedIntensity > 0.22f -> (stepIndex and 3) == 2
                            else -> false
                        }
                        if (hatTrigger) {
                            hatEnv = 0.55f + 0.35f * smoothedIntensity
                        }

                        // Bass trigger on every 16th step
                        bassFreq = midiToHz(BASS_PATTERN[stepIndex])
                        bassEnv = 1f

                        // Arpeggio lead trigger
                        val arpTranspose = if (boost) 12 else 0
                        arpFreq = midiToHz(ARP_PATTERN[stepIndex] + arpTranspose)
                        if (smoothedIntensity > 0.18f || boost || !isInGameplay) {
                            arpEnv = 1f
                        }
                    }
                }

                var mix = 0f

                if (playMusic) {
                    val musicMaster = if (isInGameplay) 0.42f else 0.20f

                    // 1. Kick drum
                    var kickSample = 0f
                    if (kickEnv > 0.001f) {
                        val kFreq = 46f + 115f * kickEnv * kickEnv
                        kickPhase += kFreq * dt
                        if (kickPhase >= 1f) kickPhase -= 1f
                        kickSample = sin(kickPhase * 2f * PI.toFloat()) * kickEnv * 0.65f
                        kickEnv *= 0.9987f
                    }

                    // 2. Snare burst
                    var snareSample = 0f
                    if (snareEnv > 0.001f) {
                        val white = Random.nextFloat() * 2f - 1f
                        snareSample = white * snareEnv * 0.28f
                        snareEnv *= 0.9978f
                    }

                    // 3. Hi-hat
                    var hatSample = 0f
                    if (hatEnv > 0.001f) {
                        val white = Random.nextFloat() * 2f - 1f
                        hatSample = white * hatEnv * 0.14f
                        hatEnv *= 0.9945f
                    }

                    // 4. Sawtooth Synth Bass with dynamic low-pass filter
                    bassPhase += bassFreq * dt
                    if (bassPhase >= 1f) bassPhase -= 1f
                    val rawSaw = (bassPhase * 2f - 1f)
                    val cutoff = if (isInGameplay) {
                        (0.06f + smoothedIntensity * 0.18f + if (boost) 0.14f else 0f).coerceAtMost(0.45f)
                    } else 0.05f
                    bassLowPass += (rawSaw - bassLowPass) * cutoff
                    val bassSample = bassLowPass * bassEnv * 0.34f
                    bassEnv *= 0.9993f

                    // 5. Arpeggio synth lead (layers in cleanly with intensity)
                    arpPhase += arpFreq * dt
                    if (arpPhase >= 1f) arpPhase -= 1f
                    val triWave = 1f - 4f * abs(arpPhase - 0.5f)
                    val arpLevel = if (isInGameplay) {
                        ((smoothedIntensity - 0.1f) * 0.35f + if (boost) 0.14f else 0f).coerceIn(0.05f, 0.32f)
                    } else 0.14f
                    val arpSample = triWave * arpEnv * arpLevel
                    arpEnv *= 0.9991f

                    // 6. Warm A2 Sub Pad (more audible during shield or chill menu)
                    padPhase += 110f * dt
                    if (padPhase >= 1f) padPhase -= 1f
                    val padBoost = if (shieldActive) 0.12f else 0.06f
                    val padSample = sin(padPhase * 2f * PI.toFloat()) * padBoost

                    mix += (kickSample + snareSample + hatSample + bassSample + arpSample + padSample) * musicMaster
                }

                // 7. Polyphonic SFX Mixer
                synchronized(sfxLock) {
                    for (v in voices) {
                        if (!v.active) continue
                        v.elapsed += dt
                        if (v.elapsed >= v.duration) {
                            v.active = false
                            continue
                        }
                        val progress = v.elapsed / v.duration
                        val env = (1f - progress) * (1f - progress)
                        val freq = v.startFreq + (v.endFreq - v.startFreq) * progress
                        v.phase += freq * dt
                        if (v.phase >= 1f) v.phase -= 1f

                        val sample = when (v.kind) {
                            VoiceKind.SINE_SWEEP -> sin(v.phase * 2f * PI.toFloat())
                            VoiceKind.SAW_SWEEP -> v.phase * 2f - 1f
                            VoiceKind.SQUARE_CHIME -> if (v.phase < 0.5f) 0.7f else -0.7f
                            VoiceKind.NOISE_BURST -> (Random.nextFloat() * 2f - 1f)
                        }
                        mix += sample * env * v.gain
                    }
                }

                // Soft saturation limiter prevents clipping
                val limited = tanh(mix.toDouble()).toFloat()
                pcmBuffer[i] = (limited * 32767f).toInt().coerceIn(-32767, 32767).toShort()
            }

            try {
                track.write(pcmBuffer, 0, CHUNK_SAMPLES)
            } catch (_: IllegalStateException) {
                break
            }
        }
    }
}
