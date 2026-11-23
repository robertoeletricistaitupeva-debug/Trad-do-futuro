package com.example.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.example.model.ChartAnalysisResult
import com.example.model.SignalDirection
import java.util.Locale

private const val TAG = "TrendVoiceSpeaker"

class TrendVoiceSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var isReady = false
    private var pendingUtterance: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ptBr = Locale.Builder().setLanguage("pt").setRegion("BR").build()
            val result = tts?.setLanguage(ptBr)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            selectMaleVoice(ptBr)
            isReady = true
            pendingUtterance?.let { text ->
                pendingUtterance = null
                speakInternal(text)
            }
        } else {
            Log.w(TAG, "TextToSpeech initialization failed with status=$status")
        }
    }

    private fun selectMaleVoice(preferredLocale: Locale) {
        val engine = tts ?: return
        val availableVoices = runCatching { engine.voices }.getOrNull().orEmpty()
        val ptVoices = availableVoices.filter { voice ->
            voice.locale.language.equals("pt", ignoreCase = true)
        }
        val candidates = if (ptVoices.isNotEmpty()) ptVoices else availableVoices.toList()

        val scoredMale = candidates
            .map { voice -> voice to scoreMaleVoice(voice, preferredLocale) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }

        if (scoredMale != null) {
            val chosen = scoredMale.first
            engine.voice = chosen
            engine.setPitch(0.94f)
            engine.setSpeechRate(1.02f)
            Log.d(TAG, "Selected male TTS voice: ${chosen.name} (${chosen.locale})")
        } else {
            // Fallback when the device only has a single default/female voice installed:
            // keep best pt-BR voice and lower pitch into a masculine register.
            val bestPtBr = ptVoices
                .sortedByDescending { voice ->
                    (if (voice.locale.country.equals("BR", ignoreCase = true)) 10 else 0) +
                        (if (!voice.isNetworkConnectionRequired) 5 else 0)
                }
                .firstOrNull()
            if (bestPtBr != null) {
                engine.voice = bestPtBr
            }
            engine.setPitch(0.78f)
            engine.setSpeechRate(0.98f)
            Log.d(TAG, "No explicit male TTS voice found; applied masculine pitch fallback.")
        }
    }

    private fun scoreMaleVoice(voice: Voice, preferredLocale: Locale): Int {
        val name = voice.name.lowercase(Locale.ROOT)
        val features = runCatching { voice.features }.getOrNull().orEmpty()
            .joinToString(" ") { it.lowercase(Locale.ROOT) }
        val combined = "$name $features"

        val hasFemaleMarker = combined.contains("female") ||
            combined.contains("femin") ||
            combined.contains("mulher") ||
            combined.contains("afs") ||
            combined.contains("luciana") ||
            combined.contains("maria") ||
            combined.contains("vitoria") ||
            combined.contains("francisca") ||
            combined.contains("thalita") ||
            combined.contains("raquel")

        val hasMaleMarker = (!combined.contains("female") && combined.contains("male")) ||
            combined.contains("masculin") ||
            combined.contains("homem") ||
            combined.contains("#male") ||
            combined.contains("-m-") ||
            combined.contains("_m_") ||
            combined.contains("ptd") ||
            combined.contains("daniel") ||
            combined.contains("ricardo") ||
            combined.contains("felipe") ||
            combined.contains("antonio") ||
            combined.contains("thiago") ||
            combined.contains("carlos")

        if (!hasMaleMarker || hasFemaleMarker) return 0

        var score = 100
        if (voice.locale.language.equals(preferredLocale.language, ignoreCase = true)) score += 30
        if (voice.locale.country.equals(preferredLocale.country, ignoreCase = true)) score += 25
        if (!voice.isNetworkConnectionRequired) score += 15
        if (voice.quality >= Voice.QUALITY_HIGH) score += 10
        return score
    }

    fun speakAnalysis(result: ChartAnalysisResult, intervalSec: Int = 60) {
        val message = buildSpokenMessage(result, intervalSec)
        if (!isReady) {
            pendingUtterance = message
            return
        }
        speakInternal(message)
    }

    fun stop() {
        pendingUtterance = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop TTS", e)
        }
    }

    fun shutdown() {
        pendingUtterance = null
        isReady = false
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to shutdown TTS", e)
        } finally {
            tts = null
        }
    }

    private fun speakInternal(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "trend_utterance_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking trend analysis", e)
        }
    }

    private fun buildSpokenMessage(result: ChartAnalysisResult, intervalSec: Int): String {
        val horizon = when (intervalSec) {
            30 -> "trinta segundos"
            60 -> "um minuto"
            120 -> "dois minutos"
            300 -> "cinco minutos"
            else -> if (intervalSec >= 60) "${intervalSec / 60} minutos" else "$intervalSec segundos"
        }
        return when (result.direction) {
            SignalDirection.CALL_UP ->
                "Tendência de alta para $horizon! Sinal acima."
            SignalDirection.PUT_DOWN ->
                "Tendência de baixa para $horizon! Sinal abaixo."
            SignalDirection.NEUTRAL ->
                "Sem tendência definida para $horizon."
        }
    }
}
