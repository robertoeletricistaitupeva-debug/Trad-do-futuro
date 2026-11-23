package com.example.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Centralizes Brazil Mode (Modo Brasil): Portuguese (pt-BR) locale, Brasília timezone
 * (America/Sao_Paulo), and Brazilian Real (R$) formatting defaults across the app and WebView.
 */
object BrazilLocaleConfig {
    val BRAZIL_LOCALE: Locale = Locale.Builder()
        .setLanguage("pt")
        .setRegion("BR")
        .build()

    val BRASILIA_TIMEZONE: TimeZone = TimeZone.getTimeZone("America/Sao_Paulo")

    const val DEFAULT_CURRENCY_SYMBOL: String = "R$"

    fun applyBrazilProcessDefaults() {
        runCatching { Locale.setDefault(BRAZIL_LOCALE) }
        runCatching { TimeZone.setDefault(BRASILIA_TIMEZONE) }
    }

    fun wrapContextInBrazilLocale(base: Context): Context {
        applyBrazilProcessDefaults()
        return runCatching {
            val config = Configuration(base.resources.configuration)
            config.setLocale(BRAZIL_LOCALE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(BRAZIL_LOCALE))
            }
            base.createConfigurationContext(config)
        }.getOrDefault(base)
    }

    fun formatBrasiliaTime(date: Date = Date()): String {
        val formatter = SimpleDateFormat("HH:mm:ss", BRAZIL_LOCALE).apply {
            timeZone = BRASILIA_TIMEZONE
        }
        return formatter.format(date)
    }
}
