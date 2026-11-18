package com.chronos.app.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.chronos.app.data.local.ChronosSettingsPrefs
import com.chronos.app.data.local.FeedbackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class CyberFeedbackManager private constructor(
    context: Context,
    private val settingsPrefs: ChronosSettingsPrefs
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var clickSoundId: Int = 0

    @Volatile
    private var beepProgressSoundId: Int = 0

    @Volatile
    private var errorGlitchSoundId: Int = 0

    @Volatile
    private var successChimeSoundId: Int = 0

    private val loadedSoundIds = mutableSetOf<Int>()

    private val soundPool: SoundPool by lazy {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build().apply {
                setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        synchronized(loadedSoundIds) {
                            loadedSoundIds.add(sampleId)
                        }
                    }
                }
            }
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        scope.launch {
            preloadSoundEffects()
        }
    }

    fun isSoundEnabled(): Boolean = settingsPrefs.feedbackSettings.value.soundEffectsEnabled

    fun isHapticsEnabled(): Boolean = settingsPrefs.feedbackSettings.value.hapticsEnabled

    fun setSoundEnabled(enabled: Boolean) {
        settingsPrefs.updateFeedbackSettings { it.copy(soundEffectsEnabled = enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        settingsPrefs.updateFeedbackSettings { it.copy(hapticsEnabled = enabled) }
    }

    fun playTerminalClick(withHaptic: Boolean = true) {
        playLoadedSound(clickSoundId, volume = 0.72f, rate = 1.0f)
        if (withHaptic) {
            hapticTerminalClick()
        }
    }

    fun playBeepProgress(withHaptic: Boolean = true) {
        playLoadedSound(beepProgressSoundId, volume = 0.80f, rate = 1.0f)
        if (withHaptic) {
            hapticTerminalClick()
        }
    }

    fun playErrorGlitch(withHaptic: Boolean = true) {
        playLoadedSound(errorGlitchSoundId, volume = 0.85f, rate = 1.0f)
        if (withHaptic) {
            hapticErrorGlitch()
        }
    }

    fun playSuccessChime(withHaptic: Boolean = false) {
        playLoadedSound(successChimeSoundId, volume = 0.90f, rate = 1.0f)
        if (withHaptic) {
            hapticDoubleConfirm()
        }
    }

    fun hapticTerminalClick() {
        if (!isHapticsEnabled()) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(18L, 195))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(18L)
        }
    }

    fun hapticDoubleConfirm() {
        if (!isHapticsEnabled()) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val timings = longArrayOf(0L, 38L, 65L, 48L)
        val amplitudes = intArrayOf(0, 210, 0, 255)
        vibrateWaveformCompat(vib, timings, amplitudes)
    }

    fun hapticOverclockAlert() {
        if (!isHapticsEnabled()) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val timings = longArrayOf(0L, 90L, 55L, 120L, 55L, 175L)
        val amplitudes = intArrayOf(0, 220, 0, 255, 0, 255)
        vibrateWaveformCompat(vib, timings, amplitudes)
    }

    fun hapticErrorGlitch() {
        if (!isHapticsEnabled()) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val timings = longArrayOf(0L, 42L, 34L, 42L, 34L, 68L)
        val amplitudes = intArrayOf(0, 235, 0, 180, 0, 240)
        vibrateWaveformCompat(vib, timings, amplitudes)
    }

    private fun vibrateWaveformCompat(vib: Vibrator, timings: LongArray, amplitudes: IntArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (vib.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(timings, -1)
        }
    }

    private fun playLoadedSound(soundId: Int, volume: Float, rate: Float) {
        if (!isSoundEnabled() || soundId == 0) return
        val isReady = synchronized(loadedSoundIds) { loadedSoundIds.contains(soundId) }
        if (!isReady) return
        soundPool.play(soundId, volume, volume, 1, 0, rate)
    }

    private fun preloadSoundEffects() {
        val rawDir = File(appContext.cacheDir, "chronos_sfx").apply { mkdirs() }
        clickSoundId = loadRawOrSynthesized("sfx_terminal_click", rawDir, SynthPreset.CLICK)
        beepProgressSoundId = loadRawOrSynthesized("sfx_beep_progress", rawDir, SynthPreset.BEEP_PROGRESS)
        errorGlitchSoundId = loadRawOrSynthesized("sfx_error_glitch", rawDir, SynthPreset.ERROR_GLITCH)
        successChimeSoundId = loadRawOrSynthesized("sfx_success_chime", rawDir, SynthPreset.SUCCESS_CHIME)
    }

    private fun loadRawOrSynthesized(rawName: String, cacheDir: File, preset: SynthPreset): Int {
        val rawResId = appContext.resources.getIdentifier(rawName, "raw", appContext.packageName)
        if (rawResId != 0) {
            return soundPool.load(appContext, rawResId, 1)
        }
        val wavFile = File(cacheDir, "$rawName.wav")
        if (!wavFile.exists() || wavFile.length() < 44L) {
            val wavBytes = CyberSynthWaveGenerator.generateWavBytes(preset)
            FileOutputStream(wavFile).use { it.write(wavBytes) }
        }
        return soundPool.load(wavFile.absolutePath, 1)
    }

    fun release() {
        synchronized(loadedSoundIds) {
            loadedSoundIds.clear()
        }
        soundPool.release()
    }

    enum class SynthPreset {
        CLICK,
        BEEP_PROGRESS,
        ERROR_GLITCH,
        SUCCESS_CHIME
    }

    companion object {
        @Volatile
        private var INSTANCE: CyberFeedbackManager? = null

        fun getInstance(context: Context): CyberFeedbackManager {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val sharedPrefs = (appContext as? com.chronos.app.ChronosApplication)?.settingsPrefs
                    ?: ChronosSettingsPrefs(appContext)
                INSTANCE ?: CyberFeedbackManager(
                    context = appContext,
                    settingsPrefs = sharedPrefs
                ).also { INSTANCE = it }
            }
        }
    }
}

internal object CyberSynthWaveGenerator {
    private const val SAMPLE_RATE = 22050

    fun generateWavBytes(preset: CyberFeedbackManager.SynthPreset): ByteArray {
        val pcmSamples = when (preset) {
            CyberFeedbackManager.SynthPreset.CLICK -> synthTerminalClick()
            CyberFeedbackManager.SynthPreset.BEEP_PROGRESS -> synthBeepProgress()
            CyberFeedbackManager.SynthPreset.ERROR_GLITCH -> synthErrorGlitch()
            CyberFeedbackManager.SynthPreset.SUCCESS_CHIME -> synthSuccessChime()
        }
        return encodePcm16MonoWav(pcmSamples, SAMPLE_RATE)
    }

    private fun synthTerminalClick(): ShortArray {
        val count = (SAMPLE_RATE * 0.036).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 1750.0 - (t / 0.036) * 850.0
            val env = exp(-t * 115.0)
            val wave = sin(2.0 * PI * freq * t) + 0.35 * sin(2.0 * PI * (freq * 2.1) * t)
            (wave * env * 13000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun synthBeepProgress(): ShortArray {
        val count = (SAMPLE_RATE * 0.095).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val freq = if (t < 0.045) 880.0 else 1320.0
            val localT = if (t < 0.045) t else (t - 0.045)
            val env = exp(-localT * 32.0) * (1.0 - exp(-localT * 400.0))
            val wave = sin(2.0 * PI * freq * t)
            (wave * env * 14000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun synthErrorGlitch(): ShortArray {
        val count = (SAMPLE_RATE * 0.18).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val freq = if ((i / 650) % 2 == 0) 210.0 else 155.0
            val env = exp(-t * 14.0)
            val raw = sin(2.0 * PI * freq * t) + 0.5 * sin(2.0 * PI * (freq * 1.49) * t)
            val clipped = raw.coerceIn(-0.75, 0.75)
            (clipped * env * 15000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun synthSuccessChime(): ShortArray {
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
        val noteDur = 0.095
        val count = (SAMPLE_RATE * (notes.size * noteDur + 0.08)).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            var mix = 0.0
            notes.forEachIndexed { idx, freq ->
                val start = idx * noteDur
                if (t >= start) {
                    val dt = t - start
                    val env = exp(-dt * 10.5) * (1.0 - exp(-dt * 350.0))
                    mix += sin(2.0 * PI * freq * dt) * env * 0.45
                }
            }
            (mix * 15000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun encodePcm16MonoWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataBytes = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataBytes)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 36 + dataBytes)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 16)
        writeShortLE(out, 1)
        writeShortLE(out, 1)
        writeIntLE(out, sampleRate)
        writeIntLE(out, sampleRate * 2)
        writeShortLE(out, 2)
        writeShortLE(out, 16)
        out.write("data".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, dataBytes)
        samples.forEach { s -> writeShortLE(out, s.toInt()) }
        return out.toByteArray()
    }

    private fun writeIntLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }
}
