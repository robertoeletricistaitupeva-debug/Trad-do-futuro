package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.DemoPlaceholderAiRepository
import com.example.data.FirebaseAiChartRepository
import com.example.model.BoConfig
import com.example.model.BoSensitivity
import com.example.model.ChartAnalysisOutcome
import com.example.model.SignalDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChartValidationAndBoTest {

    private val repo = DemoPlaceholderAiRepository()

    @Test
    fun `rejects solid blank dark screen as non-chart`() {
        val bmp = createSolidBitmap(400, 300, Color.rgb(19, 23, 34))

        val outcome = repo.analyzeBitmapForBo(bmp, "teste", BoConfig())
        assertTrue("Expected InvalidChart for blank screen, got $outcome", outcome is ChartAnalysisOutcome.InvalidChart)
    }

    @Test
    fun `rejects nature photo gradient with green grass and blue sky`() {
        val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        for (y in 0 until 300) {
            for (x in 0 until 400) {
                val color = if (y < 140) {
                    Color.rgb(90 + (x % 20), 150 + (y / 3), 220 + (y % 15))
                } else {
                    Color.rgb(45 + ((x * 7 + y) % 35), 130 + ((x + y * 3) % 55), 40 + (y % 25))
                }
                bmp.setPixel(x, y, color)
            }
        }

        val outcome = repo.analyzeBitmapForBo(bmp, "galeria", BoConfig())
        assertTrue("Expected InvalidChart for nature photo, got $outcome", outcome is ChartAnalysisOutcome.InvalidChart)
    }

    @Test
    fun `rejects portrait skin-tone photo without chart candles`() {
        val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        for (y in 0 until 300) {
            for (x in 0 until 400) {
                val dx = x - 200
                val dy = y - 150
                val dist = dx * dx + dy * dy
                val color = if (dist < 8500) {
                    Color.rgb(212 - (y % 18), 162 - (x % 15), 134 - (y % 12))
                } else {
                    Color.rgb(120 + (x / 10), 112 + (y / 10), 104 + ((x + y) % 14))
                }
                bmp.setPixel(x, y, color)
            }
        }

        val outcome = repo.analyzeBitmapForBo(bmp, "galeria", BoConfig())
        assertTrue("Expected InvalidChart for portrait photo, got $outcome", outcome is ChartAnalysisOutcome.InvalidChart)
    }

    @Test
    fun `rejects flat horizontal Abaixo and Acima UI buttons`() {
        val bmp = createSolidBitmap(400, 300, Color.rgb(19, 26, 38))
        fillRect(bmp, 24, 180, 190, 224, Color.rgb(235, 77, 75))
        fillRect(bmp, 210, 180, 376, 224, Color.rgb(38, 175, 115))

        val outcome = repo.analyzeBitmapForBo(bmp, "iqoption.com", BoConfig())
        assertTrue("Expected InvalidChart for flat UI buttons, got $outcome", outcome is ChartAnalysisOutcome.InvalidChart)
    }

    @Test
    fun `accepts dark candlestick uptrend chart and detects CALL_UP`() {
        val bmp = createCandlestickChartBitmap(
            bgColor = Color.rgb(21, 28, 42),
            uptrend = true
        )

        val outcome = repo.analyzeBitmapForBo(
            bmp,
            "iqoption.com",
            BoConfig(expirySeconds = 60, sensitivity = BoSensitivity.MODERATE)
        )
        assertTrue("Expected ValidChart for uptrend chart, got $outcome", outcome is ChartAnalysisOutcome.ValidChart)
        val result = (outcome as ChartAnalysisOutcome.ValidChart).result
        assertEquals(SignalDirection.CALL_UP, result.direction)
        assertEquals("M1 (1 min)", result.timeframeLabel)
    }

    @Test
    fun `accepts light candlestick downtrend chart and detects PUT_DOWN`() {
        val bmp = createCandlestickChartBitmap(
            bgColor = Color.rgb(245, 247, 250),
            uptrend = false
        )

        val outcome = repo.analyzeBitmapForBo(
            bmp,
            "Print da galeria",
            BoConfig(expirySeconds = 120, sensitivity = BoSensitivity.MODERATE)
        )
        assertTrue("Expected ValidChart for downtrend chart, got $outcome", outcome is ChartAnalysisOutcome.ValidChart)
        val result = (outcome as ChartAnalysisOutcome.ValidChart).result
        assertEquals(SignalDirection.PUT_DOWN, result.direction)
        assertEquals("M2 (2 min)", result.timeframeLabel)
    }

    @Test
    fun `detects PUT_DOWN on IQ Option style downtrend with RSI lines and empty right side`() {
        val bmp = createSolidBitmap(480, 320, Color.rgb(21, 28, 42))
        val green = Color.rgb(38, 175, 115)
        val red = Color.rgb(235, 77, 75)

        // Horizontal 70% green and 30% red RSI lines spanning full width including empty right third
        fillRect(bmp, 0, 240, 480, 242, green)
        fillRect(bmp, 0, 278, 480, 280, red)

        // Candles ONLY occupy left 62% of width, trending clearly downward
        val candleCount = 14
        for (i in 0 until candleCount) {
            val x = 24 + i * 19
            val progress = i.toFloat() / (candleCount - 1)
            val centerY = (55f + progress * 135f).toInt()
            val isBull = (i == 2 || i == 6)
            val color = if (isBull) green else red
            fillRect(bmp, x - 1, centerY - 22, x + 1, centerY + 22, color)
            fillRect(bmp, x - 5, centerY - 14, x + 5, centerY + 14, color)
        }

        val outcome = repo.analyzeBitmapForBo(
            bmp,
            "iqoption.com",
            BoConfig(expirySeconds = 60, sensitivity = BoSensitivity.CONSERVATIVE)
        )
        assertTrue("Expected ValidChart, got $outcome", outcome is ChartAnalysisOutcome.ValidChart)
        val result = (outcome as ChartAnalysisOutcome.ValidChart).result
        assertEquals(SignalDirection.PUT_DOWN, result.direction)
    }

    @Test
    fun `detects PUT_DOWN on bearish reversal after earlier uptrend`() {
        val bmp = createSolidBitmap(480, 320, Color.rgb(21, 28, 42))
        val green = Color.rgb(38, 175, 115)
        val red = Color.rgb(235, 77, 75)

        val candleCount = 16
        for (i in 0 until candleCount) {
            val x = 30 + i * 26
            val isBull = i < 8
            val centerY = if (i < 8) {
                210 - i * 18
            } else {
                84 + (i - 8) * 23
            }
            val color = if (isBull) green else red
            fillRect(bmp, x - 1, centerY - 22, x + 1, centerY + 22, color)
            fillRect(bmp, x - 6, centerY - 14, x + 6, centerY + 14, color)
        }

        val outcome = repo.analyzeBitmapForBo(
            bmp,
            "iqoption.com",
            BoConfig(expirySeconds = 60, sensitivity = BoSensitivity.MODERATE)
        )
        assertTrue("Expected ValidChart, got $outcome", outcome is ChartAnalysisOutcome.ValidChart)
        val result = (outcome as ChartAnalysisOutcome.ValidChart).result
        assertEquals(SignalDirection.PUT_DOWN, result.direction)
    }

    @Test
    fun `detects NEUTRAL on flat sideways alternating candles`() {
        val bmp = createSolidBitmap(480, 320, Color.rgb(21, 28, 42))
        val green = Color.rgb(38, 175, 115)
        val red = Color.rgb(235, 77, 75)

        val candleCount = 16
        for (i in 0 until candleCount) {
            val x = 30 + i * 26
            val isBull = i % 2 == 0
            val centerY = if (isBull) 150 else 172
            val color = if (isBull) green else red
            fillRect(bmp, x - 1, centerY - 22, x + 1, centerY + 22, color)
            fillRect(bmp, x - 6, centerY - 14, x + 6, centerY + 14, color)
        }

        val outcome = repo.analyzeBitmapForBo(
            bmp,
            "iqoption.com",
            BoConfig(expirySeconds = 60, sensitivity = BoSensitivity.CONSERVATIVE)
        )
        assertTrue("Expected ValidChart, got $outcome", outcome is ChartAnalysisOutcome.ValidChart)
        val result = (outcome as ChartAnalysisOutcome.ValidChart).result
        assertEquals(SignalDirection.NEUTRAL, result.direction)
    }

    @Test
    fun `detects CALL_UP on bullish reversal after earlier downtrend`() {
        val bmp = createSolidBitmap(480, 320, Color.rgb(21, 28, 42))
        val green = Color.rgb(38, 175, 115)
        val red = Color.rgb(235, 77, 75)

        val candleCount = 16
        for (i in 0 until candleCount) {
            val x = 30 + i * 26
            val isBull = i >= 8
            val centerY = if (i < 8) {
                75 + i * 20
            } else {
                215 - (i - 8) * 20
            }
            val color = if (isBull) green else red
            fillRect(bmp, x - 1, centerY - 22, x + 1, centerY + 22, color)
            fillRect(bmp, x - 6, centerY - 14, x + 6, centerY + 14, color)
        }

        val outcome = repo.analyzeBitmapForBo(
            bmp,
            "iqoption.com",
            BoConfig(expirySeconds = 30, sensitivity = BoSensitivity.CONSERVATIVE)
        )
        assertTrue("Expected ValidChart, got $outcome", outcome is ChartAnalysisOutcome.ValidChart)
        val result = (outcome as ChartAnalysisOutcome.ValidChart).result
        assertEquals(SignalDirection.CALL_UP, result.direction)
    }

    @Test
    fun `recognizes real IQ Option screenshot chart region with RSI and MA lines`() {
        val screenshotFile = listOf(
            java.io.File("public/assets/aistudio/Screenshot_20261116-185830.OnSpace.AI_ba12e2fa.png"),
            java.io.File("../public/assets/aistudio/Screenshot_20261116-185830.OnSpace.AI_ba12e2fa.png")
        ).firstOrNull { it.exists() }

        if (screenshotFile != null) {
            val buffered = javax.imageio.ImageIO.read(screenshotFile)
            val fullW = buffered.width
            val fullH = buffered.height
            // Crop the chart + RSI region (roughly y: 31%..57%, x: 4%..96%)
            val cropX = (fullW * 0.04f).toInt()
            val cropY = (fullH * 0.31f).toInt()
            val cropW = (fullW * 0.92f).toInt()
            val cropH = (fullH * 0.26f).toInt()

            val bmp = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
            for (y in 0 until cropH) {
                for (x in 0 until cropW) {
                    val argb = buffered.getRGB(cropX + x, cropY + y)
                    bmp.setPixel(x, y, argb)
                }
            }

            val outcome = repo.analyzeBitmapForBo(
                bmp,
                "iqoption.com",
                BoConfig(expirySeconds = 30, sensitivity = BoSensitivity.MODERATE)
            )
            assertTrue("Expected ValidChart on real IQ Option screenshot, got $outcome", outcome is ChartAnalysisOutcome.ValidChart)
            val tightResult = (outcome as ChartAnalysisOutcome.ValidChart).result
            assertEquals("Expected CALL_UP on real IQ Option uptrend, got $tightResult", SignalDirection.CALL_UP, tightResult.direction)

            // Also verify a wider selection that includes floating left buttons and bottom 65%/35% bar
            val wideX = (fullW * 0.03f).toInt()
            val wideY = (fullH * 0.28f).toInt()
            val wideW = (fullW * 0.94f).toInt()
            val wideH = (fullH * 0.33f).toInt()
            val wideBmp = Bitmap.createBitmap(wideW, wideH, Bitmap.Config.ARGB_8888)
            for (y in 0 until wideH) {
                for (x in 0 until wideW) {
                    wideBmp.setPixel(x, y, buffered.getRGB(wideX + x, wideY + y))
                }
            }
            val wideOutcome = repo.analyzeBitmapForBo(
                wideBmp,
                "iqoption.com",
                BoConfig(expirySeconds = 30, sensitivity = BoSensitivity.CONSERVATIVE)
            )
            assertTrue("Expected ValidChart on wide IQ Option screenshot, got $wideOutcome", wideOutcome is ChartAnalysisOutcome.ValidChart)
            val wideResult = (wideOutcome as ChartAnalysisOutcome.ValidChart).result
            assertEquals("Expected CALL_UP on wide IQ Option uptrend, got $wideResult", SignalDirection.CALL_UP, wideResult.direction)
        }
    }

    @Test
    fun `auto-crop and full-bounds crop safely analyze Chrome PWA screenshot without recycling source`() {
        val chromeScreenshot = listOf(
            java.io.File("public/assets/aistudio/Screenshot_20261117-182344.Chrome_a07b2de9.png"),
            java.io.File("../public/assets/aistudio/Screenshot_20261117-182344.Chrome_a07b2de9.png")
        ).firstOrNull { it.exists() } ?: return

        val buffered = javax.imageio.ImageIO.read(chromeScreenshot)
        val fullW = buffered.width
        val fullH = buffered.height
        val fullBmp = Bitmap.createBitmap(fullW, fullH, Bitmap.Config.ARGB_8888)
        for (y in 0 until fullH) {
            for (x in 0 until fullW) {
                fullBmp.setPixel(x, y, buffered.getRGB(x, y))
            }
        }

        // Verify full-bounds crop does NOT recycle the original source bitmap
        val fullCropped = com.example.ui.components.cropBitmapRegion(
            fullBmp,
            com.example.model.NormalizedRect(0f, 0f, 1f, 1f)
        )
        assertTrue("Expected fullCropped not null", fullCropped != null)
        assertTrue("Expected source bitmap to stay unrecycled", !fullBmp.isRecycled)
        fullCropped?.recycle()

        // Verify defaultChartRegionOnBitmap auto-crops the real Chrome PWA chart and validates cleanly
        val autoArea = com.example.ui.components.defaultChartRegionOnBitmap(fullBmp)
        val autoCropped = com.example.ui.components.cropBitmapRegion(fullBmp, autoArea)
        assertTrue("Expected autoCropped not null", autoCropped != null)
        assertTrue("Expected source bitmap to stay unrecycled after auto-crop", !fullBmp.isRecycled)

        val outcome = repo.analyzeBitmapForBo(
            autoCropped!!,
            "Print da galeria",
            BoConfig(expirySeconds = 60, sensitivity = BoSensitivity.MODERATE)
        )
        assertTrue("Expected ValidChart on auto-cropped Chrome PWA screenshot, got $outcome", outcome is ChartAnalysisOutcome.ValidChart)

        // Verify viewport mapping round-trips cleanly with ContentScale.Fit letterboxing
        val viewportRect = com.example.ui.components.mapBitmapRectToViewport(fullBmp, 1080, 1800, autoArea)
        val fittedCropped = com.example.ui.components.cropFittedBitmapRegion(fullBmp, 1080, 1800, viewportRect)
        assertTrue("Expected fittedCropped not null", fittedCropped != null)
        val fittedOutcome = repo.analyzeBitmapForBo(
            fittedCropped!!,
            "Print da galeria",
            BoConfig(expirySeconds = 60, sensitivity = BoSensitivity.MODERATE)
        )
        assertTrue("Expected ValidChart on viewport-fitted Chrome PWA crop, got $fittedOutcome", fittedOutcome is ChartAnalysisOutcome.ValidChart)
        autoCropped.recycle()
        fittedCropped.recycle()
        fullBmp.recycle()
    }

    @Test
    fun `firebase ai parses valid CALL_UP json response with non-demo flag`() {
        val aiRepo = FirebaseAiChartRepository()
        val json = """
            {
              "isValidChart": true,
              "direction": "CALL_UP",
              "confidencePercent": 82,
              "rsiEstimated": 64,
              "trendLabel": "Alta (M1)",
              "patternLabel": "Fundos ascendentes e rompimento de topo",
              "supportResistanceLabel": "Rompimento de resistência",
              "summary": "Sequência de candles compradores com rompimento de resistência para M1."
            }
        """.trimIndent()

        val outcome = aiRepo.parseGeminiOutcome(
            jsonText = json,
            sourceDescription = "iqoption.com",
            boConfig = BoConfig(expirySeconds = 60, sensitivity = BoSensitivity.CONSERVATIVE)
        )
        assertTrue(outcome is ChartAnalysisOutcome.ValidChart)
        val result = (outcome as ChartAnalysisOutcome.ValidChart).result
        assertEquals(SignalDirection.CALL_UP, result.direction)
        assertEquals(82, result.confidencePercent)
        assertEquals(64, result.rsiValue)
        assertFalse(result.isDemoMode)
    }

    @Test
    fun `firebase ai conservative filter converts low-confidence PUT_DOWN to NEUTRAL`() {
        val aiRepo = FirebaseAiChartRepository()
        val json = """
            {
              "isValidChart": true,
              "direction": "PUT_DOWN",
              "confidencePercent": 66,
              "rsiEstimated": 42,
              "trendLabel": "Baixa moderada (30s)",
              "patternLabel": "",
              "supportResistanceLabel": "Zona intermediária",
              "summary": "Pressão vendedora moderada."
            }
        """.trimIndent()

        val outcome = aiRepo.parseGeminiOutcome(
            jsonText = json,
            sourceDescription = "Print da galeria",
            boConfig = BoConfig(expirySeconds = 30, sensitivity = BoSensitivity.CONSERVATIVE)
        )
        assertTrue(outcome is ChartAnalysisOutcome.ValidChart)
        val result = (outcome as ChartAnalysisOutcome.ValidChart).result
        assertEquals(SignalDirection.NEUTRAL, result.direction)
    }

    @Test
    fun `firebase ai rejects non-chart json response`() {
        val aiRepo = FirebaseAiChartRepository()
        val json = """
            {
              "isValidChart": false,
              "invalidReason": "A imagem mostra apenas a tela de login da corretora, sem gráfico de velas.",
              "direction": "NEUTRAL",
              "confidencePercent": 45,
              "rsiEstimated": 50,
              "trendLabel": "Indisponível",
              "patternLabel": "Sem candles",
              "supportResistanceLabel": "N/A",
              "summary": "Não foi possível identificar velas financeiras."
            }
        """.trimIndent()

        val outcome = aiRepo.parseGeminiOutcome(
            jsonText = json,
            sourceDescription = "iqoption.com",
            boConfig = BoConfig()
        )
        assertTrue(outcome is ChartAnalysisOutcome.InvalidChart)
    }

    @Test
    fun `default configuration uses 60-second 1 minute interval and balanced high-precision sensitivity`() {
        val config = BoConfig()
        assertEquals(60, config.expirySeconds)
        assertEquals("M1 (1 min)", config.expiryLabel)
        assertEquals(BoSensitivity.CONSERVATIVE, config.sensitivity)

        val uiState = com.example.ui.TrendLensUiState()
        assertEquals(60, uiState.autoScanIntervalSec)
        assertEquals(com.example.ui.FAST_TREND_HUNT_INTERVAL_SEC, uiState.countdownSec)
        assertEquals(60, uiState.boConfig.expirySeconds)
        assertEquals(BoSensitivity.CONSERVATIVE, uiState.boConfig.sensitivity)
    }

    @Test
    fun `firebase ai allows breakout trend labels and coerces opposite direction contradictions`() {
        val aiRepo = FirebaseAiChartRepository()
        val breakoutCallJson = """
            {
              "isValidChart": true,
              "trendLabel": "Rompendo consolidação em alta",
              "patternLabel": "Candles verdes fortes com fechamentos ascendentes",
              "supportResistanceLabel": "Rompimento de resistência",
              "rsiEstimated": 68,
              "confidencePercent": 74,
              "direction": "CALL_UP",
              "summary": "Pressão compradora rompeu faixa lateral."
            }
        """.trimIndent()

        val breakoutOutcome = aiRepo.parseGeminiOutcome(
            jsonText = breakoutCallJson,
            sourceDescription = "iqoption.com",
            boConfig = BoConfig(sensitivity = BoSensitivity.MODERATE)
        )
        assertTrue(breakoutOutcome is ChartAnalysisOutcome.ValidChart)
        val breakoutResult = (breakoutOutcome as ChartAnalysisOutcome.ValidChart).result
        assertEquals(SignalDirection.CALL_UP, breakoutResult.direction)

        val oppositeContradictionJson = """
            {
              "isValidChart": true,
              "trendLabel": "Tendência de baixa",
              "patternLabel": "Topos e fundos descendentes",
              "supportResistanceLabel": "Abaixo das médias móveis",
              "rsiEstimated": 38,
              "confidencePercent": 81,
              "direction": "CALL_UP",
              "summary": "Pressão vendedora."
            }
        """.trimIndent()

        val oppositeOutcome = aiRepo.parseGeminiOutcome(
            jsonText = oppositeContradictionJson,
            sourceDescription = "iqoption.com",
            boConfig = BoConfig()
        )
        assertTrue(oppositeOutcome is ChartAnalysisOutcome.ValidChart)
        assertEquals(
            SignalDirection.NEUTRAL,
            (oppositeOutcome as ChartAnalysisOutcome.ValidChart).result.direction
        )
    }

    @Test
    fun `dom button parser extracts valid coordinates and rejects invalid or missing buttons`() {
        val validJson = "\"{\\\"found\\\":true,\\\"normX\\\":0.74,\\\"normY\\\":0.85,\\\"label\\\":\\\"Acima\\\"}\""
        val hit = com.example.ui.components.parseDomButtonResult(validJson)
        assertTrue("Expected valid DomButtonHit", hit != null)
        assertEquals(0.74f, hit!!.normX, 0.001f)
        assertEquals(0.85f, hit.normY, 0.001f)
        assertEquals("Acima", hit.label)

        val missingJson = "\"{\\\"found\\\":false}\""
        assertEquals(null, com.example.ui.components.parseDomButtonResult(missingJson))
        assertEquals(null, com.example.ui.components.parseDomButtonResult("null"))
    }

    @Test
    fun `robot auto-click default and calibration sequence work properly`() {
        val vm = com.example.ui.TrendLensViewModel(repo)
        assertTrue(vm.uiState.value.autoClickEnabled)
        assertEquals(60, vm.uiState.value.autoClickCooldownSec)

        vm.startButtonCalibration()
        assertEquals(SignalDirection.CALL_UP, vm.uiState.value.calibratingButton)
        vm.recordCalibratedTap(0.75f, 0.85f)
        assertEquals(SignalDirection.PUT_DOWN, vm.uiState.value.calibratingButton)
        vm.recordCalibratedTap(0.25f, 0.85f)
        assertEquals(null, vm.uiState.value.calibratingButton)
        assertEquals(0.75f, vm.uiState.value.calibratedCallPoint!!.normX, 0.001f)
        assertEquals(0.25f, vm.uiState.value.calibratedPutPoint!!.normX, 0.001f)
    }

    @Test
    fun `broker money parser and balance outcome resolver handle BRL and USD accurately`() {
        assertEquals(1250.40, com.example.ui.components.parseBrokerMoneyValue("R$ 1.250,40")!!, 0.001)
        assertEquals(1250.40, com.example.ui.components.parseBrokerMoneyValue("$1,250.40")!!, 0.001)
        assertEquals(10.50, com.example.ui.components.parseBrokerMoneyValue("R$ 10,50")!!, 0.001)
        assertEquals(1000.0, com.example.ui.components.parseBrokerMoneyValue("$1,000")!!, 0.001)
        assertEquals(null, com.example.ui.components.parseBrokerMoneyValue("Demo"))

        // Before click: 1000.00, stake: 10.00 -> after WIN (85% payout): 1008.50
        assertEquals(
            com.example.model.TradeOutcome.WIN,
            com.example.ui.components.resolveTradeOutcomeFromBalance(1000.0, 1008.5, 10.0)
        )
        // Before click: 1000.00, stake: 10.00 -> after LOSS: 990.00
        assertEquals(
            com.example.model.TradeOutcome.LOSS,
            com.example.ui.components.resolveTradeOutcomeFromBalance(1000.0, 990.0, 10.0)
        )
        // Unchanged balance (tie or click didn't fire) -> null
        assertEquals(
            null,
            com.example.ui.components.resolveTradeOutcomeFromBalance(1000.0, 1000.0, 10.0)
        )
    }

    @Test
    fun `martingale doubles stake on loss and resets to initial stake on win`() {
        val vm = com.example.ui.TrendLensViewModel(repo)
        vm.setRiskStrategy(com.example.model.RiskStrategy.MARTINGALE)
        assertTrue(vm.uiState.value.martingaleConfig.enabled)
        assertEquals(com.example.model.RiskStrategy.MARTINGALE, vm.uiState.value.martingaleConfig.effectiveStrategy)

        vm.setMartingaleInitialStake(5.0)
        vm.setMartingaleMaxSteps(3)

        assertTrue(vm.uiState.value.martingaleConfig.enabled)
        assertEquals(0, vm.uiState.value.currentMartingaleStep)
        assertEquals(5.0, vm.uiState.value.currentBotStake, 0.001)

        // Loss 1 -> doubles to 10.0 (Gale 1)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS)
        assertEquals(1, vm.uiState.value.currentMartingaleStep)
        assertEquals(10.0, vm.uiState.value.currentBotStake, 0.001)
        assertEquals(1, vm.uiState.value.sessionLosses)

        // Loss 2 -> doubles to 20.0 (Gale 2)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS)
        assertEquals(2, vm.uiState.value.currentMartingaleStep)
        assertEquals(20.0, vm.uiState.value.currentBotStake, 0.001)
        assertEquals(2, vm.uiState.value.sessionLosses)

        // Win -> returns immediately to initial stake 5.0 (step 0)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.WIN)
        assertEquals(0, vm.uiState.value.currentMartingaleStep)
        assertEquals(5.0, vm.uiState.value.currentBotStake, 0.001)
        assertEquals(1, vm.uiState.value.sessionWins)

        // Automatic balance detection after click
        vm.onAutoClickExecuted(
            SignalDirection.CALL_UP,
            com.example.ui.components.BotExecutionResult(
                success = true,
                detail = "Robô clicou em Acima",
                balanceBeforeClick = 1000.0,
                stakeAppliedInDom = true
            )
        )
        // Loss deducted R$ 5 -> balance now 995.0 -> next stake becomes 10.0
        val nextAfterLoss = vm.resolvePendingTradeFromBalance(995.0)
        assertEquals(10.0, nextAfterLoss!!, 0.001)
        assertEquals(1, vm.uiState.value.currentMartingaleStep)

        // Next click at 10.0 wins -> returns to 5.0
        vm.onAutoClickExecuted(
            SignalDirection.PUT_DOWN,
            com.example.ui.components.BotExecutionResult(
                success = true,
                detail = "Robô clicou em Abaixo",
                balanceBeforeClick = 995.0,
                stakeAppliedInDom = true
            )
        )
        val nextAfterWin = vm.resolvePendingTradeFromBalance(1003.5)
        assertEquals(5.0, nextAfterWin!!, 0.001)
        assertEquals(0, vm.uiState.value.currentMartingaleStep)
    }

    @Test
    fun `robot config options account asset indicator and risk strategies update state properly`() {
        val vm = com.example.ui.TrendLensViewModel(repo)

        vm.setAccountType(com.example.model.BrokerAccountType.REAL)
        assertEquals(com.example.model.BrokerAccountType.REAL, vm.uiState.value.accountType)

        vm.setTradingAsset(com.example.model.TradingAsset.GOLD_SILVER)
        assertEquals(com.example.model.TradingAsset.GOLD_SILVER, vm.uiState.value.boConfig.tradingAsset)

        vm.setTechnicalIndicator(com.example.model.TechnicalIndicator.BOLLINGER_RSI)
        assertEquals(com.example.model.TechnicalIndicator.BOLLINGER_RSI, vm.uiState.value.boConfig.technicalIndicator)

        // Soros strategy increases stake on win and resets on loss
        vm.setMartingaleInitialStake(2.0)
        vm.setSorosMaxLevels(2)
        vm.setRiskStrategy(com.example.model.RiskStrategy.SOROS)
        assertTrue(vm.uiState.value.martingaleConfig.enabled)
        assertEquals(2.0, vm.uiState.value.currentBotStake, 0.001)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.WIN)
        assertEquals(1, vm.uiState.value.currentMartingaleStep)
        assertEquals(3.70, vm.uiState.value.currentBotStake, 0.01)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS)
        assertEquals(0, vm.uiState.value.currentMartingaleStep)
        assertEquals(2.0, vm.uiState.value.currentBotStake, 0.001)

        // Fixed value keeps stake unchanged on loss or win
        vm.setRiskStrategy(com.example.model.RiskStrategy.FIXED)
        assertFalse(vm.uiState.value.martingaleConfig.enabled)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS)
        assertEquals(2.0, vm.uiState.value.currentBotStake, 0.001)
    }

    @Test
    fun `amount field calibration and stake apply outcome parser work accurately`() {
        val vm = com.example.ui.TrendLensViewModel(repo)
        assertFalse(vm.uiState.value.calibratingAmountField)
        assertEquals(null, vm.uiState.value.calibratedAmountPoint)

        vm.startAmountCalibration()
        assertTrue(vm.uiState.value.calibratingAmountField)
        assertEquals(null, vm.uiState.value.calibratingButton)

        vm.recordCalibratedAmountTap(0.82f, 0.72f)
        assertFalse(vm.uiState.value.calibratingAmountField)
        assertEquals(0.82f, vm.uiState.value.calibratedAmountPoint!!.normX, 0.001f)
        assertEquals(0.72f, vm.uiState.value.calibratedAmountPoint!!.normY, 0.001f)

        vm.clearCalibratedButtons()
        assertEquals(null, vm.uiState.value.calibratedAmountPoint)

        val appliedDirect = com.example.ui.components.parseStakeApplyOutcome("\"{\\\"found\\\":true,\\\"applied\\\":\\\"4\\\"}\"")
        assertTrue(appliedDirect.applied)
        assertFalse(appliedDirect.alreadyMatches)

        val alreadyMatched = com.example.ui.components.parseStakeApplyOutcome("\"{\\\"found\\\":true,\\\"alreadyMatches\\\":true,\\\"applied\\\":\\\"$ 2\\\"}\"")
        assertTrue(alreadyMatched.applied)
        assertTrue(alreadyMatched.alreadyMatches)

        val tapFallback = com.example.ui.components.parseStakeApplyOutcome("\"{\\\"found\\\":false,\\\"tapNormX\\\":0.84,\\\"tapNormY\\\":0.71}\"")
        assertFalse(tapFallback.applied)
        assertEquals(0.84f, tapFallback.tapNormX!!, 0.001f)
        assertEquals(0.71f, tapFallback.tapNormY!!, 0.001f)

        val notFound = com.example.ui.components.parseStakeApplyOutcome("\"{\\\"found\\\":false}\"")
        assertFalse(notFound.applied)
        assertEquals(null, notFound.tapNormX)

        val stakeScript = com.example.ui.components.buildSetStakeAmountScript(2.0)
        assertTrue(stakeScript.contains("hasStrictCurrency"))
        assertTrue(stakeScript.contains("alreadyMatches"))
        assertTrue(stakeScript.contains("expir|tempo|time|durat"))
    }

    @Test
    fun `transient null balance keeps pending trade alive and visual stake parser rejects expiration time`() = runTest {
        val vm = com.example.ui.TrendLensViewModel(repo)
        vm.setRiskStrategy(com.example.model.RiskStrategy.MARTINGALE)
        vm.setMartingaleInitialStake(2.0)
        vm.onAutoClickExecuted(
            SignalDirection.CALL_UP,
            com.example.ui.components.BotExecutionResult(
                success = true,
                detail = "Robô clicou em Acima",
                balanceBeforeClick = 1000.0,
                stakeAppliedInDom = true
            )
        )

        val emissions = mutableListOf<Double>()
        val job = backgroundScope.launch(Dispatchers.Unconfined) {
            vm.applyStakeTrigger.collect { emissions.add(it) }
        }

        // Transient null balance reading must NOT wipe pending trade tracking
        vm.onBrokerBalanceObserved(null)
        assertEquals(SignalDirection.CALL_UP, vm.uiState.value.pendingTradeDirection)
        assertEquals(1000.0, vm.uiState.value.pendingTradeBalanceBefore!!, 0.001)

        // Real-time loss detected -> doubles stake to 4.0 and emits applyStakeTrigger immediately
        vm.onBrokerBalanceObserved(998.0)
        assertEquals(1, vm.uiState.value.currentMartingaleStep)
        assertEquals(4.0, vm.uiState.value.currentBotStake, 0.001)
        assertEquals(listOf(4.0), emissions)
        job.cancel()

        // Visual stake parser accepts currency amounts and rejects expiration times / payout percentages
        val aiRepo = FirebaseAiChartRepository()
        val validStake = aiRepo.parseVisualStakeBoxJson("""{"found":true,"normX":0.78,"normY":0.52,"amountText":"$ 2"}""")
        assertTrue(validStake != null)
        assertEquals(2.0, validStake!!.currentAmount!!, 0.001)

        val rejectedExpiry = aiRepo.parseVisualStakeBoxJson("""{"found":true,"normX":0.82,"normY":0.52,"amountText":"2 min"}""")
        assertEquals(null, rejectedExpiry)

        val rejectedPayout = aiRepo.parseVisualStakeBoxJson("""{"found":true,"normX":0.82,"normY":0.52,"amountText":"+89%"}""")
        assertEquals(null, rejectedPayout)
    }

    @Test
    fun `trend parser preserves contextual recovery trends, rescues clear neutral trends, and auto-detect sets up scan`() {
        val aiRepo = FirebaseAiChartRepository()
        assertEquals(64, com.example.model.BoSensitivity.MODERATE.minConfidence)
        assertEquals(72, com.example.model.BoSensitivity.CONSERVATIVE.minConfidence)
        assertEquals(80, com.example.model.BoSensitivity.SNIPER.minConfidence)

        // Contextual phrase "após baixa" with CALL_UP must NOT be misclassified as contradiction
        val recoveryCallJson = """
            {
              "isValidChart": true,
              "trendLabel": "Recuperação de alta após baixa",
              "patternLabel": "Engolfo comprador na base",
              "supportResistanceLabel": "Suporte imediato respeitado",
              "rsiEstimated": 54,
              "confidencePercent": 70,
              "direction": "CALL_UP",
              "summary": "Compradores retomaram força após recuo."
            }
        """.trimIndent()
        val recoveryOutcome = aiRepo.parseGeminiOutcome(
            jsonText = recoveryCallJson,
            sourceDescription = "iqoption.com",
            boConfig = BoConfig(sensitivity = BoSensitivity.MODERATE)
        )
        assertTrue(recoveryOutcome is ChartAnalysisOutcome.ValidChart)
        assertEquals(
            SignalDirection.CALL_UP,
            (recoveryOutcome as ChartAnalysisOutcome.ValidChart).result.direction
        )

        // Strict "NEUTRAL" direction from model with clear bullish trendLabel & >= 66% in MODERATE gets rescued to CALL_UP
        val rescuedNeutralJson = """
            {
              "isValidChart": true,
              "trendLabel": "Tendência de alta moderada",
              "patternLabel": "Fundos ascendentes",
              "supportResistanceLabel": "Suporte em LTA",
              "rsiEstimated": 58,
              "confidencePercent": 68,
              "direction": "NEUTRAL",
              "summary": "Inclinação compradora leve."
            }
        """.trimIndent()
        val rescuedOutcome = aiRepo.parseGeminiOutcome(
            jsonText = rescuedNeutralJson,
            sourceDescription = "iqoption.com",
            boConfig = BoConfig(sensitivity = BoSensitivity.MODERATE)
        )
        assertTrue(rescuedOutcome is ChartAnalysisOutcome.ValidChart)
        assertEquals(
            SignalDirection.CALL_UP,
            (rescuedOutcome as ChartAnalysisOutcome.ValidChart).result.direction
        )

        // 1-tap auto-detect sets default area, turns on auto-scan, collapses panel
        val vm = com.example.ui.TrendLensViewModel(repo)
        vm.setAnalysisExpanded(true)
        vm.autoMarkDefaultChartAndScan()
        assertTrue(vm.uiState.value.markedArea != null)
        assertTrue(vm.uiState.value.autoScanEnabled)
        assertFalse(vm.uiState.value.isAnalysisExpanded)
    }

    @Test
    fun `iq option app launcher opens installed broker app or falls back cleanly`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

        // Without installed IQ Option / Play Store in Robolectric sandbox, falls back to built-in traderoom
        var fellBackToTraderoom = false
        val missingOutcome = com.example.ui.components.openIqOptionApp(context) {
            fellBackToTraderoom = true
        }
        assertEquals(com.example.ui.components.BrokerLaunchOutcome.UNAVAILABLE, missingOutcome)
        assertTrue(fellBackToTraderoom)
        assertEquals(null, com.example.ui.components.findInstalledIqOptionPackage(context))

        // intent:// URI with browser_fallback_url routes cleanly into WebView fallback
        var capturedFallbackUrl: String? = null
        val handledIntent = com.example.ui.components.handleExternalBrokerIntentUrl(
            context = context,
            rawUrl = "intent://traderoom#Intent;scheme=iqoption;package=com.iqoption;S.browser_fallback_url=https%3A%2F%2Fiqoption.com%2Ftraderoom;end",
            webViewFallback = { capturedFallbackUrl = it }
        )
        assertTrue(handledIntent)
        assertEquals("https://iqoption.com/traderoom", capturedFallbackUrl)

        // When com.iqoption launcher activity exists, findInstalledIqOptionPackage & openIqOptionApp detect and open it
        val shadowPm = org.robolectric.Shadows.shadowOf(context.packageManager)
        val iqComponent = android.content.ComponentName("com.iqoption", "com.iqoption.MainActivity")
        val resolveInfo = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.iqoption"
                name = "com.iqoption.MainActivity"
                exported = true
            }
        }
        val mainLauncherFilter = android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        shadowPm.addActivityIfNotPresent(iqComponent)
        shadowPm.addIntentFilterForActivity(iqComponent, mainLauncherFilter)
        @Suppress("DEPRECATION")
        shadowPm.addResolveInfoForIntent(
            android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage("com.iqoption"),
            resolveInfo
        )

        assertEquals("com.iqoption", com.example.ui.components.findInstalledIqOptionPackage(context))
        val launchedOutcome = com.example.ui.components.openIqOptionApp(context)
        assertEquals(com.example.ui.components.BrokerLaunchOutcome.OPENED_INSTALLED_APP, launchedOutcome)

        // Automatic iqoption:// and market:// redirects inside WebView are blocked without leaving the app
        assertTrue(
            com.example.ui.components.handleExternalBrokerIntentUrl(
                context = context,
                rawUrl = "iqoption://traderoom",
                webViewFallback = {}
            )
        )
        assertTrue(
            com.example.ui.components.handleExternalBrokerIntentUrl(
                context = context,
                rawUrl = "market://details?id=com.iqoption",
                webViewFallback = {}
            )
        )
    }

    @Test
    fun `default martingale doubles 2 to 4, 8, 16, 32, 65, 130, 260 and matches IQ Valor pill`() = runTest {
        val vm = com.example.ui.TrendLensViewModel(repo)
        vm.setRiskStrategy(com.example.model.RiskStrategy.MARTINGALE)
        assertEquals(7, vm.uiState.value.martingaleConfig.maxSteps)
        assertEquals("2 → 4 → 8 → 16 → 32 → 65 → 130 → 260", vm.uiState.value.martingaleConfig.martingaleSequencePreview())

        val emissions = mutableListOf<Double>()
        val job = backgroundScope.launch(Dispatchers.Unconfined) {
            vm.applyStakeTrigger.collect { emissions.add(it) }
        }

        val expectedSequence = listOf(4.0, 8.0, 16.0, 32.0, 65.0, 130.0, 260.0)
        expectedSequence.forEach { expected ->
            vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS)
            assertEquals(expected, vm.uiState.value.currentBotStake, 0.001)
        }
        assertEquals(expectedSequence, emissions)

        // Win after deep Gale resets immediately back to $2 and emits 2.0
        vm.recordTradeOutcome(com.example.model.TradeOutcome.WIN)
        assertEquals(0, vm.uiState.value.currentMartingaleStep)
        assertEquals(2.0, vm.uiState.value.currentBotStake, 0.001)
        assertEquals(expectedSequence + listOf(2.0), emissions)
        job.cancel()

        // Visual stake parser accepts IQ Option mobile "Valor ($) 2" & "2" while still rejecting "Horário 1 min"
        val aiRepo = FirebaseAiChartRepository()
        val valorPill = aiRepo.parseVisualStakeBoxJson("""{"found":true,"normX":0.74,"normY":0.82,"amountText":"Valor ($) 2"}""")
        assertTrue(valorPill != null)
        assertEquals(2.0, valorPill!!.currentAmount!!, 0.001)

        val bareNumber = aiRepo.parseVisualStakeBoxJson("""{"found":true,"normX":0.74,"normY":0.82,"amountText":"2"}""")
        assertTrue(bareNumber != null)
        assertEquals(2.0, bareNumber!!.currentAmount!!, 0.001)

        assertEquals(
            null,
            aiRepo.parseVisualStakeBoxJson("""{"found":true,"normX":0.25,"normY":0.82,"amountText":"Horário 1 min"}""")
        )

        val stakeScript = com.example.ui.components.buildSetStakeAmountScript(4.0)
        assertTrue(stakeScript.contains("hasValorLabelAndNumber"))
        assertTrue(stakeScript.contains("isNumericUnderValorLabel"))
    }

    @Test
    fun `broker session prefs always start directly on IQ Option PWA Traderoom and clear session on logout`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        com.example.data.BrokerSessionPrefs.clearSavedBrokerUrl(context)

        assertEquals("https://iqoption.com/pwa/traderoom", com.example.ui.TrendLensUiState.TRADEROOM_URL)

        // Default startup opens IQ Option PWA Traderoom immediately
        assertEquals(
            com.example.ui.TrendLensUiState.TRADEROOM_URL,
            com.example.data.BrokerSessionPrefs.getInitialBrokerUrl(context, hasActiveSessionCookies = false)
        )
        assertEquals(
            com.example.ui.TrendLensUiState.TRADEROOM_URL,
            com.example.data.BrokerSessionPrefs.getInitialBrokerUrl(context, hasActiveSessionCookies = true)
        )

        // Even after visiting other IQ Option pages (landing, login, deposit, or legacy traderoom),
        // next app launch still loads https://iqoption.com/pwa/traderoom immediately
        com.example.data.BrokerSessionPrefs.saveBrokerUrlIfEligible(context, "https://share.google/OqfoTHowXlyisQfbq")
        com.example.data.BrokerSessionPrefs.saveBrokerUrlIfEligible(context, "https://app.iqoption.com/pt/")
        com.example.data.BrokerSessionPrefs.saveBrokerUrlIfEligible(context, "https://iqoption.com/pt/login")
        com.example.data.BrokerSessionPrefs.saveBrokerUrlIfEligible(context, "https://iqoption.com/traderoom")
        assertEquals(
            "https://iqoption.com/pwa/traderoom",
            com.example.data.BrokerSessionPrefs.getInitialBrokerUrl(context, hasActiveSessionCookies = false)
        )

        // Explicit /logout clears persisted session while keeping startup on PWA Traderoom
        com.example.data.BrokerSessionPrefs.saveBrokerUrlIfEligible(context, "https://iqoption.com/logout")
        assertEquals(
            "https://iqoption.com/pwa/traderoom",
            com.example.data.BrokerSessionPrefs.getInitialBrokerUrl(context, hasActiveSessionCookies = false)
        )
    }

    @Test
    fun `broker session prefs persist and restore session cookies across cold restarts without clobbering live login`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        com.example.data.BrokerSessionPrefs.clearSavedBrokerUrl(context)

        // Anonymous cookies without auth tokens are ignored
        val savedAnon = com.example.data.BrokerSessionPrefs.persistSessionCookiesSnapshot(
            context = context,
            synchronous = true,
            cookieReader = { "lang=pt_PT; theme=dark" }
        )
        assertFalse(savedAnon)

        // Live logged-in cookies with ssid are persisted
        val savedLive = com.example.data.BrokerSessionPrefs.persistSessionCookiesSnapshot(
            context = context,
            synchronous = true,
            cookieReader = { url ->
                when (url) {
                    "https://iqoption.com" -> "ssid=live_token_12345; lang=pt_PT"
                    "https://app.iqoption.com" -> "platform=9; ssid=live_token_12345"
                    else -> ""
                }
            }
        )
        assertTrue(savedLive)

        // When CookieManager already has live ssid, restore does not overwrite it
        val skippedWrites = mutableListOf<Pair<String, String>>()
        val restoredWhileLive = com.example.data.BrokerSessionPrefs.restoreSessionCookiesIfMissing(
            context = context,
            cookieReader = { "ssid=newer_live_token" },
            cookieWriter = { url, header -> skippedWrites.add(url to header) },
            flusher = {}
        )
        assertFalse(restoredWhileLive)
        assertTrue(skippedWrites.isEmpty())

        // After a cold restart where Chromium dropped session cookies, saved ssid is restored with Max-Age
        val restoredWrites = mutableListOf<Pair<String, String>>()
        var flushed = false
        val restoredAfterRestart = com.example.data.BrokerSessionPrefs.restoreSessionCookiesIfMissing(
            context = context,
            cookieReader = { "" },
            cookieWriter = { url, header -> restoredWrites.add(url to header) },
            flusher = { flushed = true }
        )
        assertTrue(restoredAfterRestart)
        assertTrue(flushed)
        assertTrue(restoredWrites.any { (u, h) -> u == "https://iqoption.com" && h.contains("ssid=live_token_12345") && h.contains("Max-Age=") })
        assertTrue(restoredWrites.any { (u, h) -> u == "https://app.iqoption.com" && h.contains("platform=9") && h.contains("Max-Age=") })

        // Explicit logout clears saved cookies so they are not restored again
        com.example.data.BrokerSessionPrefs.saveBrokerUrlIfEligible(context, "https://iqoption.com/logout")
        val restoredAfterLogout = com.example.data.BrokerSessionPrefs.restoreSessionCookiesIfMissing(
            context = context,
            cookieReader = { "" },
            cookieWriter = { _, _ -> },
            flusher = {}
        )
        assertFalse(restoredAfterLogout)
    }

    @Test
    fun `brazil broker lock enforces portuguese locale cookies and blocks external escape redirects`() {
        // 1. Missing or English lang cookie is replaced with Portuguese (pt_PT)
        val writtenCookies = mutableListOf<Pair<String, String>>()
        var flushed = false
        val seeded = com.example.data.BrokerSessionPrefs.ensureBrazilLocaleCookies(
            cookieReader = { "lang=en_US; theme=dark" },
            cookieWriter = { url, header -> writtenCookies.add(url to header) },
            flusher = { flushed = true }
        )
        assertTrue(seeded)
        assertTrue(flushed)
        assertTrue(writtenCookies.any { (u, h) -> u == "https://iqoption.com" && h.contains("lang=pt_PT") })
        assertTrue(writtenCookies.any { (u, h) -> u == "https://app.iqoption.com" && h.contains("lang=pt_PT") })

        // 2. Existing Portuguese cookie is preserved without redundant writes
        val skippedWrites = mutableListOf<Pair<String, String>>()
        val alreadyPt = com.example.data.BrokerSessionPrefs.ensureBrazilLocaleCookies(
            cookieReader = { "lang=pt_PT; ssid=abc" },
            cookieWriter = { url, header -> skippedWrites.add(url to header) },
            flusher = {}
        )
        assertFalse(alreadyPt)
        assertTrue(skippedWrites.isEmpty())

        // 3. External escape URLs (Play Store, AppsFlyer, social links) are blocked while broker/auth stay allowed
        assertTrue(com.example.data.BrokerSessionPrefs.isBlockedExternalEscapeUrl("https://play.google.com/store/apps/details?id=com.iqoption"))
        assertTrue(com.example.data.BrokerSessionPrefs.isBlockedExternalEscapeUrl("https://app.appsflyer.com/com.iqoption"))
        assertTrue(com.example.data.BrokerSessionPrefs.isBlockedExternalEscapeUrl("https://t.me/iqoption"))
        assertTrue(com.example.data.BrokerSessionPrefs.isBlockedExternalEscapeUrl("https://www.instagram.com/iqoption/"))
        assertFalse(com.example.data.BrokerSessionPrefs.isBlockedExternalEscapeUrl("https://iqoption.com/pwa/traderoom"))
        assertFalse(com.example.data.BrokerSessionPrefs.isBlockedExternalEscapeUrl("https://accounts.google.com/o/oauth2/auth"))

        // 4. Marketing/English landing pages are detected without touching traderoom, login, or deposit routes
        assertTrue(com.example.data.BrokerSessionPrefs.isEnglishOrMarketingLandingUrl("https://iqoption.com/en"))
        assertTrue(com.example.data.BrokerSessionPrefs.isEnglishOrMarketingLandingUrl("https://iqoption.com/en/"))
        assertTrue(com.example.data.BrokerSessionPrefs.isEnglishOrMarketingLandingUrl("https://iqoption.com/"))
        assertTrue(com.example.data.BrokerSessionPrefs.isEnglishOrMarketingLandingUrl("https://iqoption.com/lp/mobile"))
        assertFalse(com.example.data.BrokerSessionPrefs.isEnglishOrMarketingLandingUrl("https://iqoption.com/pwa/traderoom"))
        assertFalse(com.example.data.BrokerSessionPrefs.isEnglishOrMarketingLandingUrl("https://iqoption.com/pt/login"))
        assertFalse(com.example.data.BrokerSessionPrefs.isEnglishOrMarketingLandingUrl("https://iqoption.com/deposit"))
        assertFalse(com.example.data.BrokerSessionPrefs.isEnglishOrMarketingLandingUrl("https://app.iqoption.com/"))

        // 5. Brazil Mode defaults: pt-BR locale, Brasília timezone, and R$ currency
        assertEquals("pt", com.example.data.BrazilLocaleConfig.BRAZIL_LOCALE.language)
        assertEquals("BR", com.example.data.BrazilLocaleConfig.BRAZIL_LOCALE.country)
        assertEquals("America/Sao_Paulo", com.example.data.BrazilLocaleConfig.BRASILIA_TIMEZONE.id)
        assertEquals("R$", com.example.model.MartingaleConfig().currencySymbol)
        val brTime = com.example.data.BrazilLocaleConfig.formatBrasiliaTime(java.util.Date(0L))
        assertEquals("21:00:00", brTime)
    }

    @Test
    fun `qr code generator and public share url validation reject internal preview links`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        assertEquals(
            null,
            com.example.data.BrokerSessionPrefs.normalizeShareUrl(
                "https://ais-pre-3cbp7aggdq6fs6jwt43fow-762585569733.us-west2.run.app"
            )
        )
        assertEquals(
            null,
            com.example.data.BrokerSessionPrefs.normalizeShareUrl(
                "https://ais-dev-3cbp7aggdq6fs6jwt43fow-762585569733.us-west2.run.app"
            )
        )
        val saved = com.example.data.BrokerSessionPrefs.saveCustomAppShareUrl(
            context,
            "drive.google.com/file/d/abc123/view"
        )
        assertEquals("https://drive.google.com/file/d/abc123/view", saved)
        assertEquals(
            "https://drive.google.com/file/d/abc123/view",
            com.example.data.BrokerSessionPrefs.getCustomAppShareUrl(context)
        )

        val qr = com.example.ui.components.generateQrCodeBitmap(
            content = saved!!,
            sizePx = 256
        )
        assertTrue(qr != null)
        assertEquals(256, qr!!.width)
        assertEquals(256, qr.height)

        var hasBlack = false
        var hasWhite = false
        for (y in 0 until qr.height step 4) {
            for (x in 0 until qr.width step 4) {
                val px = qr.getPixel(x, y)
                if (px == Color.BLACK) hasBlack = true
                if (px == Color.WHITE) hasWhite = true
            }
        }
        assertTrue("QR code must contain dark modules", hasBlack)
        assertTrue("QR code must contain light modules", hasWhite)
        assertEquals(null, com.example.ui.components.generateQrCodeBitmap("", sizePx = 256))
    }

    @Test
    fun `gemini parser normalizes direction aliases, fenced json, and line chart outcomes`() {
        val aiRepo = FirebaseAiChartRepository()
        val callOutcome = aiRepo.parseGeminiOutcome(
            jsonText = """
                ```json
                {
                  "isValidChart": true,
                  "trendLabel": "Tendência de alta",
                  "patternLabel": "Linha de preço ascendente no gráfico de área",
                  "supportResistanceLabel": "Suporte imediato",
                  "rsiEstimated": 64,
                  "confidencePercent": "78%",
                  "direction": "ACIMA",
                  "summary": "Gráfico de linha em inclinação ascendente contínua."
                }
                ```
            """.trimIndent(),
            sourceDescription = "iqoption.com",
            boConfig = BoConfig(sensitivity = BoSensitivity.MODERATE)
        )
        assertTrue(callOutcome is ChartAnalysisOutcome.ValidChart)
        val callResult = (callOutcome as ChartAnalysisOutcome.ValidChart).result
        assertEquals(SignalDirection.CALL_UP, callResult.direction)
        assertEquals(78, callResult.confidencePercent)

        val putOutcome = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"trendLabel":"Tendência de baixa","patternLabel":"Topos descendentes","supportResistanceLabel":"Resistência superior","rsiEstimated":35,"confidencePercent":74,"direction":"PUT","summary":"Pressão vendedora predominante."}""",
            sourceDescription = "iqoption.com",
            boConfig = BoConfig(sensitivity = BoSensitivity.MODERATE)
        )
        assertTrue(putOutcome is ChartAnalysisOutcome.ValidChart)
        assertEquals(SignalDirection.PUT_DOWN, (putOutcome as ChartAnalysisOutcome.ValidChart).result.direction)
    }

    @Test
    fun `sniper high-accuracy guardrails block traps and accept strong aligned confluence`() {
        val aiRepo = FirebaseAiChartRepository()
        val sniperConfig = BoConfig(sensitivity = BoSensitivity.SNIPER)

        // 1. Opposing wick trap is blocked to NEUTRAL even if confidence is high
        val wickTrap = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"macroTrend":"UP","microMomentum":"UP","entryQuality":"STRONG","riskFlag":"OPPOSING_WICK","trendLabel":"Tendência de alta","patternLabel":"Pavio superior longo","supportResistanceLabel":"Topo","rsiEstimated":62,"confidencePercent":86,"direction":"CALL_UP","summary":"Pavio de rejeição no topo."}""",
            sourceDescription = "iqoption.com",
            boConfig = sniperConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, wickTrap.result.direction)
        assertTrue(wickTrap.result.patternLabel.contains("Pavio"))

        // 2. Extreme RSI overbought on CALL_UP is blocked to NEUTRAL
        val rsiTrap = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"macroTrend":"UP","microMomentum":"UP","entryQuality":"STRONG","riskFlag":"NONE","trendLabel":"Tendência de alta","patternLabel":"Esticado","supportResistanceLabel":"Resistência","rsiEstimated":79,"confidencePercent":85,"direction":"CALL_UP","summary":"RSI esticado."}""",
            sourceDescription = "iqoption.com",
            boConfig = sniperConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, rsiTrap.result.direction)
        assertTrue(rsiTrap.result.patternLabel.contains("sobrecompra"))

        // 3. Macro/micro conflict is blocked to NEUTRAL
        val conflict = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"macroTrend":"DOWN","microMomentum":"UP","entryQuality":"STRONG","riskFlag":"NONE","trendLabel":"Tendência de alta","patternLabel":"Repique","supportResistanceLabel":"Zona média","rsiEstimated":52,"confidencePercent":84,"direction":"CALL_UP","summary":"Contra a macro."}""",
            sourceDescription = "iqoption.com",
            boConfig = sniperConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, conflict.result.direction)

        // 4. Clean STRONG aligned setup with NONE risk flag and healthy RSI passes in SNIPER mode
        val cleanSniper = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"macroTrend":"UP","microMomentum":"UP","entryQuality":"STRONG","riskFlag":"NONE","trendLabel":"Tendência de alta","patternLabel":"Pullback limpo na EMA com candle comprador","supportResistanceLabel":"Espaço livre até próxima resistência","rsiEstimated":58,"confidencePercent":86,"direction":"CALL_UP","summary":"Confluência máxima de alta."}""",
            sourceDescription = "iqoption.com",
            boConfig = sniperConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.CALL_UP, cleanSniper.result.direction)
        assertEquals(86, cleanSniper.result.confidencePercent)

        // 5. ViewModel tracks consecutive losses and resets on WIN
        val vm = com.example.ui.TrendLensViewModel(repo)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS, emitStakeSync = false)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS, emitStakeSync = false)
        assertEquals(2, vm.uiState.value.consecutiveLosses)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.WIN, emitStakeSync = false)
        assertEquals(0, vm.uiState.value.consecutiveLosses)
    }

    @Test
    fun `institutional 6-pillar intelligence blocks no-trend consolidation, mixed candles, low confluence, and accepts verified uptrends`() {
        val aiRepo = FirebaseAiChartRepository()
        val conservativeConfig = BoConfig(sensitivity = BoSensitivity.CONSERVATIVE)

        // 1. Consolidation / horizontal range is strictly blocked even if raw confidence was inflated
        val rangeBlocked = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"CONSOLIDATION_RANGE","macroTrend":"SIDEWAYS","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":42,"confluenceCount":2,"entryQuality":"MODERATE","riskFlag":"NONE","trendLabel":"Tendência de alta","patternLabel":"Caixa lateral","supportResistanceLabel":"Meio da caixa","rsiEstimated":55,"confidencePercent":82,"direction":"CALL_UP","summary":"Lateral."}""",
            sourceDescription = "iqoption.com",
            boConfig = conservativeConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, rangeBlocked.result.direction)
        assertTrue(rangeBlocked.result.patternLabel.contains("lateral", ignoreCase = true))

        // 2. Mixed alternating candles are blocked to NEUTRAL
        val mixedBlocked = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"HH_HL_UPTREND","macroTrend":"UP","microMomentum":"UP","candleDominance":"MIXED_INDECISION","trendStrengthScore":70,"confluenceCount":3,"entryQuality":"MODERATE","riskFlag":"NONE","trendLabel":"Tendência de alta","patternLabel":"Alternância","supportResistanceLabel":"Meio","rsiEstimated":56,"confidencePercent":78,"direction":"CALL_UP","summary":"Velas mistas."}""",
            sourceDescription = "iqoption.com",
            boConfig = conservativeConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, mixedBlocked.result.direction)

        // 3. Insufficient confluence pillars (< 3/5) or weak trend strength (< 65) is blocked
        val weakTrendBlocked = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"HH_HL_UPTREND","macroTrend":"UP","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":52,"confluenceCount":2,"entryQuality":"MODERATE","riskFlag":"NONE","trendLabel":"Tendência de alta","patternLabel":"Fraco","supportResistanceLabel":"Meio","rsiEstimated":57,"confidencePercent":76,"direction":"CALL_UP","summary":"Força baixa."}""",
            sourceDescription = "iqoption.com",
            boConfig = conservativeConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, weakTrendBlocked.result.direction)

        // 4. Counter-trend CALL against LH_LL_DOWNTREND is blocked in Alta Precisão
        val counterTrendBlocked = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"LH_LL_DOWNTREND","macroTrend":"DOWN","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":76,"confluenceCount":3,"entryQuality":"MODERATE","riskFlag":"NONE","trendLabel":"Tendência de alta","patternLabel":"Repique","supportResistanceLabel":"Resistência","rsiEstimated":54,"confidencePercent":80,"direction":"CALL_UP","summary":"Repique contra macro."}""",
            sourceDescription = "iqoption.com",
            boConfig = conservativeConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, counterTrendBlocked.result.direction)

        // 5. Verified institutional uptrend (HH_HL + BUYERS_CONTROL + 84 strength + 4/5 pillars) passes with telemetry
        val cleanTrend = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"HH_HL_UPTREND","macroTrend":"UP","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":84,"confluenceCount":4,"entryQuality":"STRONG","riskFlag":"NONE","trendLabel":"Tendência de alta","patternLabel":"Fundos ascendentes + rompimento limpo","supportResistanceLabel":"Espaço livre até topo anterior","rsiEstimated":61,"confidencePercent":84,"direction":"CALL_UP","summary":"Alta confirmada com 4/5 pilares."}""",
            sourceDescription = "iqoption.com",
            boConfig = conservativeConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.CALL_UP, cleanTrend.result.direction)
        assertEquals(84, cleanTrend.result.trendStrengthScore)
        assertEquals(4, cleanTrend.result.confluenceCount)
        assertEquals(86, cleanTrend.result.confidencePercent)
    }

    @Test
    fun `capture content detector and webview chart readiness probe validate live chart state`() {
        val blankBitmap = createSolidBitmap(200, 140, Color.rgb(12, 16, 24))
        assertFalse(com.example.ui.components.hasVisibleChartContent(blankBitmap))

        val transparentBitmap = createSolidBitmap(200, 140, Color.TRANSPARENT)
        assertFalse(com.example.ui.components.hasVisibleChartContent(transparentBitmap))

        val chartBitmap = createCandlestickChartBitmap(Color.rgb(12, 16, 24), uptrend = true)
        assertTrue(com.example.ui.components.hasVisibleChartContent(chartBitmap))

        assertTrue(com.example.ui.components.parseChartReadyResult("""{"ready":true}"""))
        assertFalse(com.example.ui.components.parseChartReadyResult("""{"ready":false,"reason":"login_visible"}"""))
        val readyScript = com.example.ui.components.buildCheckChartReadyScript()
        assertTrue(readyScript.contains("input[type=\"password\"]"))
        assertTrue(readyScript.contains("canvas"))
    }

    @Test
    fun `viewmodel auto-starts chart reading when canvas is ready or robot is enabled while unmarked`() = runTest {
        val vm = com.example.ui.TrendLensViewModel(repo)
        val capturedAreas = mutableListOf<com.example.model.NormalizedRect>()
        val job = backgroundScope.launch(Dispatchers.Unconfined) {
            vm.captureTrigger.collect { capturedAreas.add(it) }
        }

        assertEquals(null, vm.uiState.value.markedArea)

        vm.tryAutoStartChartReadingIfUnmarked()
        assertTrue(vm.uiState.value.markedArea != null)
        assertEquals(1, capturedAreas.size)

        // Manual clear suppresses repeated auto-start until user toggles robot or taps Ler gráfico
        vm.clearMarkedArea()
        assertEquals(null, vm.uiState.value.markedArea)
        vm.tryAutoStartChartReadingIfUnmarked()
        assertEquals(null, vm.uiState.value.markedArea)
        assertEquals(1, capturedAreas.size)

        // Turning robot ON while unmarked auto-frames and triggers capture
        vm.toggleAutoClick(true)
        assertTrue(vm.uiState.value.markedArea != null)
        assertEquals(2, capturedAreas.size)
        job.cancel()
    }

    @Test
    fun `fast trend hunt rescans in 8s on neutral and accepts confirmed reversals in conservative mode`() = runTest {
        val aiRepo = FirebaseAiChartRepository()
        val confirmedReversalJson = """
            {
              "isValidChart": true,
              "marketStructure": "REVERSAL_TRANSITION",
              "macroTrend": "UP",
              "microMomentum": "UP",
              "candleDominance": "BUYERS_CONTROL",
              "trendStrengthScore": 72,
              "confluenceCount": 3,
              "entryQuality": "STRONG",
              "riskFlag": "NONE",
              "trendLabel": "Tendência de alta",
              "patternLabel": "Rompimento comprador com 3 velas fortes",
              "supportResistanceLabel": "Espaço livre acima",
              "rsiEstimated": 59,
              "confidencePercent": 76,
              "direction": "CALL_UP",
              "summary": "Virada compradora confirmada com domínio de velas."
            }
        """.trimIndent()

        // Accepted promptly in Alta Precisão (CONSERVATIVE)
        val conservativeOutcome = aiRepo.parseGeminiOutcome(
            jsonText = confirmedReversalJson,
            sourceDescription = "iqoption.com",
            boConfig = BoConfig(sensitivity = BoSensitivity.CONSERVATIVE)
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.CALL_UP, conservativeOutcome.result.direction)

        // Still blocked in ultra-strict SNIPER mode
        val sniperOutcome = aiRepo.parseGeminiOutcome(
            jsonText = confirmedReversalJson,
            sourceDescription = "iqoption.com",
            boConfig = BoConfig(sensitivity = BoSensitivity.SNIPER)
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, sniperOutcome.result.direction)

        // ViewModel switches to fast 20-second countdown when NEUTRAL is returned, and resets to 60s on CALL_UP
        var nextDirection = SignalDirection.NEUTRAL
        val stubRepo = object : com.example.data.AiRepository {
            override suspend fun analyzeChartRegion(
                bitmap: Bitmap,
                sourceDescription: String,
                boConfig: BoConfig,
                previousReadingHint: String?
            ): ChartAnalysisOutcome = ChartAnalysisOutcome.ValidChart(
                com.example.model.ChartAnalysisResult(
                    id = "stub",
                    direction = nextDirection,
                    confidencePercent = if (nextDirection == SignalDirection.NEUTRAL) 52 else 82,
                    assetLabel = "iqoption.com",
                    timeframeLabel = "M1 (1 min)",
                    rsiValue = 55,
                    trendLabel = if (nextDirection == SignalDirection.NEUTRAL) "Mercado lateral" else "Tendência de alta",
                    patternLabel = "Teste",
                    supportResistanceLabel = "Livre",
                    summary = "Resumo",
                    timestampLabel = "12:00:00",
                    isDemoMode = false,
                    noticeMessage = "ok",
                    trendStrengthScore = if (nextDirection == SignalDirection.NEUTRAL) 40 else 80,
                    confluenceCount = if (nextDirection == SignalDirection.NEUTRAL) 1 else 4
                )
            )
        }
        val vm = com.example.ui.TrendLensViewModel(stubRepo)
        val bmpNeutral = createCandlestickChartBitmap(Color.rgb(19, 26, 38), uptrend = false)
        val bmpUptrend = createCandlestickChartBitmap(Color.rgb(19, 26, 38), uptrend = true)

        vm.onRegionBitmapCaptured(bmpNeutral, force = true)
        kotlinx.coroutines.delay(100L)
        assertEquals(com.example.ui.FAST_TREND_HUNT_INTERVAL_SEC, vm.uiState.value.countdownSec)

        nextDirection = SignalDirection.CALL_UP
        vm.onRegionBitmapCaptured(bmpUptrend, force = true)
        kotlinx.coroutines.delay(100L)
        assertEquals(60, vm.uiState.value.countdownSec)
    }

    @Test
    fun `quota exhaustion triggers progressive backoff, perceptual static frames skip noise, and resumeAiAndScanNow unpauses immediately`() = runTest {
        assertEquals(14, com.example.ui.FAST_TREND_HUNT_INTERVAL_SEC)
        assertEquals(18, com.example.ui.QUOTA_BACKOFF_INTERVAL_SEC)
        assertTrue(com.example.data.shouldPreferLitePrimary(liteCooling = false, flashCooling = false))
        assertTrue(com.example.data.shouldPreferLitePrimary(liteCooling = false, flashCooling = true))
        assertFalse(com.example.data.shouldPreferLitePrimary(liteCooling = true, flashCooling = false))
        assertTrue(com.example.data.shouldPreferLitePrimary(liteCooling = true, flashCooling = true))
        assertEquals(15_000L, com.example.data.computeProgressiveQuotaCooldownMs(1))
        assertEquals(30_000L, com.example.data.computeProgressiveQuotaCooldownMs(2))
        assertEquals(60_000L, com.example.data.computeProgressiveQuotaCooldownMs(3))
        assertEquals(90_000L, com.example.data.computeProgressiveQuotaCooldownMs(4))
        assertTrue(com.example.data.isQuotaExceededThrowable(IllegalStateException("HTTP 429 RESOURCE_EXHAUSTED")))
        assertTrue(com.example.data.isQuotaExceededThrowable(RuntimeException("outer", IllegalStateException("Too Many Requests"))))
        assertFalse(com.example.data.isQuotaExceededThrowable(IllegalStateException("Network timeout")))

        var callCount = 0
        var clearedCooldownCount = 0
        var shouldThrowQuota = false
        val countingRepo = object : com.example.data.AiRepository {
            override fun clearQuotaCooldowns() {
                clearedCooldownCount++
            }

            override suspend fun analyzeChartRegion(
                bitmap: Bitmap,
                sourceDescription: String,
                boConfig: BoConfig,
                previousReadingHint: String?
            ): ChartAnalysisOutcome {
                callCount++
                if (shouldThrowQuota) {
                    throw IllegalStateException(
                        "Limite momentâneo da IA Gemini atingido — pausa inteligente ativada."
                    )
                }
                return ChartAnalysisOutcome.ValidChart(
                    com.example.model.ChartAnalysisResult(
                        id = "q-$callCount",
                        direction = SignalDirection.NEUTRAL,
                        confidencePercent = 54,
                        assetLabel = "iqoption.com",
                        timeframeLabel = "M1 (1 min)",
                        rsiValue = 51,
                        trendLabel = "Mercado lateral",
                        patternLabel = "Lateralização",
                        supportResistanceLabel = "Faixa central",
                        summary = "Aguardando definição.",
                        timestampLabel = "12:00:00",
                        isDemoMode = false,
                        noticeMessage = "ok",
                        trendStrengthScore = 40,
                        confluenceCount = 1
                    )
                )
            }
        }

        val vm = com.example.ui.TrendLensViewModel(countingRepo)
        val bmp = createCandlestickChartBitmap(Color.rgb(19, 26, 38), uptrend = true)

        // First scan hits Gemini and stores perceptual frame signature
        vm.onRegionBitmapCaptured(bmp, force = true)
        kotlinx.coroutines.delay(80L)
        assertEquals(1, callCount)
        assertEquals(com.example.ui.FAST_TREND_HUNT_INTERVAL_SEC, vm.uiState.value.countdownSec)

        // Minor 1-cell timer tick noise (<4.5% visual delta) MUST skip duplicate Gemini call automatically
        val bmpMinorTick = bmp.copy(Bitmap.Config.ARGB_8888, true)
        fillRect(bmpMinorTick, 0, 0, 8, 8, Color.WHITE)
        vm.onRegionBitmapCaptured(bmpMinorTick, force = false)
        kotlinx.coroutines.delay(80L)
        assertEquals(1, callCount)

        // Real chart change (downtrend candles) MUST trigger a new Gemini call automatically
        val bmpDowntrend = createCandlestickChartBitmap(Color.rgb(19, 26, 38), uptrend = false)
        vm.onRegionBitmapCaptured(bmpDowntrend, force = false)
        kotlinx.coroutines.delay(80L)
        assertEquals(2, callCount)

        // Manual scan MUST still execute Gemini even on identical image
        vm.autoMarkDefaultChartAndScan()
        vm.requestManualScan()
        vm.onRegionBitmapCaptured(bmpDowntrend, force = true)
        kotlinx.coroutines.delay(80L)
        assertEquals(3, callCount)

        // Consecutive quota exceptions step progressive backoff: 18s -> 35s -> 60s
        shouldThrowQuota = true
        vm.requestManualScan()
        vm.onRegionBitmapCaptured(bmp, force = true)
        kotlinx.coroutines.delay(80L)
        assertEquals(4, callCount)
        assertEquals(18, vm.uiState.value.countdownSec)
        assertTrue(vm.uiState.value.isAnalysisExpanded)
        assertTrue(vm.uiState.value.analysisState is com.example.model.AnalysisUiState.Error)

        vm.onRegionBitmapCaptured(bmp, force = true)
        kotlinx.coroutines.delay(80L)
        assertEquals(5, callCount)
        assertEquals(35, vm.uiState.value.countdownSec)

        vm.onRegionBitmapCaptured(bmp, force = true)
        kotlinx.coroutines.delay(80L)
        assertEquals(6, callCount)
        assertEquals(60, vm.uiState.value.countdownSec)

        // Pausing auto-scan then tapping resumeAiAndScanNow clears cooldowns, resets backoff, turns autoScan back ON, and triggers capture
        vm.toggleAutoScan(false)
        assertFalse(vm.uiState.value.autoScanEnabled)
        val captures = mutableListOf<com.example.model.NormalizedRect>()
        val captureJob = backgroundScope.launch(Dispatchers.Unconfined) {
            vm.captureTrigger.collect { captures.add(it) }
        }
        val cooldownClearsBefore = clearedCooldownCount
        vm.resumeAiAndScanNow()
        assertTrue(vm.uiState.value.autoScanEnabled)
        assertTrue("Expected clearQuotaCooldowns() to be called", clearedCooldownCount > cooldownClearsBefore)
        assertEquals(1, captures.size)
        captureJob.cancel()
    }

    @Test
    fun `ai recovery strategy defaults active, computes exact payout deficit recovery, parses Gemini plans, and handles split recovery`() = runTest {
        // 1. AI_RECOVERY is the default strategy and computes exact payout-adjusted recovery (85% payout)
        val defaultConfig = com.example.model.MartingaleConfig()
        assertEquals(com.example.model.RiskStrategy.AI_RECOVERY, defaultConfig.effectiveStrategy)

        // Losing R$ 2.00 at 85% payout requires (2.00 + 1.70) / 0.85 = R$ 4.36 to recover loss + full profit
        val firstRecovery = defaultConfig.computeRecoveryStake(
            accumulatedDeficit = 2.0,
            mode = com.example.model.AiRecoveryMode.FULL_RECOVERY_NOW,
            liveBalance = 100.0
        )
        assertEquals(4.36, firstRecovery, 0.01)
        assertTrue("Win at 85% must cover deficit + target profit", firstRecovery * 0.85 >= 2.0 + 1.70 - 0.01)

        // Bankroll safety cap prevents risking > 35% of known broker balance on one entry
        val cappedRecovery = defaultConfig.computeRecoveryStake(
            accumulatedDeficit = 40.0,
            mode = com.example.model.AiRecoveryMode.FULL_RECOVERY_NOW,
            liveBalance = 50.0
        )
        assertTrue("Single stake must not exceed 35% of R$ 50 balance (R$ 17.50), got $cappedRecovery", cappedRecovery <= 17.50)

        // 2. Gemini JSON parser validates SPLIT_RECOVERY_2X and PAUSE_PROTECT plans
        val aiRepo = FirebaseAiChartRepository()
        val req = com.example.model.AiRecoveryRequest(
            accumulatedDeficit = 10.0,
            initialStake = 2.0,
            payoutRate = 0.85,
            consecutiveLosses = 2,
            maxSteps = 7,
            liveBalance = 100.0,
            currencySymbol = "R$",
            baseMinConfidence = 72,
            recentMarketContext = "CALL_UP(78%)"
        )
        val splitPlan = aiRepo.parseAiRecoveryPlanJson(
            raw = """{"mode":"SPLIT_RECOVERY_2X","recommendedStake":7.50,"minRequiredConfidence":80,"extraWaitSeconds":15,"rationale":"Recuperação em 2 etapas com filtro alto."}""",
            request = req
        )
        assertTrue(splitPlan != null)
        assertEquals(com.example.model.AiRecoveryMode.SPLIT_RECOVERY_2X, splitPlan!!.mode)
        assertEquals(80, splitPlan.minRequiredConfidence)
        assertEquals(15, splitPlan.extraWaitSeconds)
        assertTrue(splitPlan.isFromGemini)

        // 3. ViewModel tracks exact accumulated deficit across split recovery wins until deficit hits 0
        val stubAiRepo = object : com.example.data.AiRepository {
            override suspend fun analyzeChartRegion(
                bitmap: Bitmap,
                sourceDescription: String,
                boConfig: BoConfig,
                previousReadingHint: String?
            ): ChartAnalysisOutcome = ChartAnalysisOutcome.InvalidChart("stub")

            override suspend fun planLossRecoveryStrategy(
                request: com.example.model.AiRecoveryRequest
            ): com.example.model.AiRecoveryPlan {
                return if (request.accumulatedDeficit >= 6.0) {
                    com.example.model.AiRecoveryPlan(
                        mode = com.example.model.AiRecoveryMode.SPLIT_RECOVERY_2X,
                        recommendedStake = 5.0,
                        minRequiredConfidence = 80,
                        extraWaitSeconds = 10,
                        rationale = "Recuperação em 2 etapas.",
                        isFromGemini = true
                    )
                } else {
                    com.example.model.AiRecoveryPlan(
                        mode = com.example.model.AiRecoveryMode.FULL_RECOVERY_NOW,
                        recommendedStake = com.example.model.MartingaleConfig().computeRecoveryStake(
                            accumulatedDeficit = request.accumulatedDeficit,
                            mode = com.example.model.AiRecoveryMode.FULL_RECOVERY_NOW
                        ),
                        minRequiredConfidence = 76,
                        extraWaitSeconds = 0,
                        rationale = "Etapa final para zerar déficit.",
                        isFromGemini = true
                    )
                }
            }
        }
        val vm = com.example.ui.TrendLensViewModel(stubAiRepo)
        assertEquals(com.example.model.RiskStrategy.AI_RECOVERY, vm.uiState.value.martingaleConfig.effectiveStrategy)

        // Loss 1 (R$ 2.00) -> deficit = 2.00, stake = 4.36
        vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS, emitStakeSync = false)
        kotlinx.coroutines.delay(80L)
        assertEquals(2.0, vm.uiState.value.accumulatedLossDeficit, 0.01)
        assertEquals(4.36, vm.uiState.value.currentBotStake, 0.01)

        // Loss 2 (R$ 4.36) -> deficit = 6.36 -> stub Gemini selects SPLIT_RECOVERY_2X with R$ 5.00
        vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS, emitStakeSync = false)
        kotlinx.coroutines.delay(80L)
        assertEquals(6.36, vm.uiState.value.accumulatedLossDeficit, 0.01)
        assertEquals(com.example.model.AiRecoveryMode.SPLIT_RECOVERY_2X, vm.uiState.value.aiRecoveryPlan?.mode)
        assertEquals(5.0, vm.uiState.value.currentBotStake, 0.01)

        // Partial Win on R$ 5.00 -> net profit = R$ 4.25 -> remaining deficit = R$ 2.11 (NOT reset yet!)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.WIN, emitStakeSync = false)
        kotlinx.coroutines.delay(80L)
        assertEquals(2.11, vm.uiState.value.accumulatedLossDeficit, 0.02)
        assertTrue("Stake must cover remaining deficit", vm.uiState.value.currentBotStake >= 4.48)

        // Final Win -> deficit completely cleared back to 0.0 and stake returns to R$ 2.00!
        vm.recordTradeOutcome(com.example.model.TradeOutcome.WIN, emitStakeSync = false)
        assertEquals(0.0, vm.uiState.value.accumulatedLossDeficit, 0.01)
        assertEquals(null, vm.uiState.value.aiRecoveryPlan)
        assertEquals(2.0, vm.uiState.value.currentBotStake, 0.01)
    }

    @Test
    fun `reset app wipes persisted broker session prefs, calibrations, robot overrides, and session counters`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        com.example.data.BrokerSessionPrefs.saveBrokerUrlIfEligible(context, "https://iqoption.com/pwa/traderoom")
        com.example.data.BrokerSessionPrefs.saveCustomAppShareUrl(context, "https://example.com/app.apk")
        com.example.data.BrokerSessionPrefs.persistSessionCookiesSnapshot(
            context = context,
            synchronous = true,
            cookieReader = { "ssid=secret123; lang=pt_PT" }
        )
        assertEquals("https://example.com/app.apk", com.example.data.BrokerSessionPrefs.getCustomAppShareUrl(context))

        com.example.data.BrokerSessionPrefs.resetAllAppData(context)
        assertEquals(null, com.example.data.BrokerSessionPrefs.getCustomAppShareUrl(context))
        val restored = com.example.data.BrokerSessionPrefs.restoreSessionCookiesIfMissing(
            context = context,
            cookieReader = { null },
            cookieWriter = { _, _ -> },
            flusher = {}
        )
        assertFalse("Saved cookies must be wiped by resetAllAppData", restored)

        val vm = com.example.ui.TrendLensViewModel(repo)
        vm.setAccountType(com.example.model.BrokerAccountType.REAL)
        vm.setMartingaleInitialStake(25.0)
        vm.setRiskStrategy(com.example.model.RiskStrategy.SOROS)
        vm.startButtonCalibration()
        vm.recordCalibratedTap(0.75f, 0.85f)
        vm.recordCalibratedTap(0.25f, 0.85f)
        vm.startAmountCalibration()
        vm.recordCalibratedAmountTap(0.80f, 0.70f)
        vm.autoMarkDefaultChartAndScan()
        vm.recordTradeOutcome(com.example.model.TradeOutcome.WIN, emitStakeSync = false)
        vm.setRobotConfigOpen(true)

        vm.resetAllAppState()
        val resetState = vm.uiState.value
        assertEquals(com.example.model.BrokerAccountType.DEMO, resetState.accountType)
        assertEquals(com.example.model.RiskStrategy.AI_RECOVERY, resetState.martingaleConfig.effectiveStrategy)
        assertEquals(2.0, resetState.currentBotStake, 0.001)
        assertEquals(null, resetState.calibratedCallPoint)
        assertEquals(null, resetState.calibratedPutPoint)
        assertEquals(null, resetState.calibratedAmountPoint)
        assertEquals(null, resetState.markedArea)
        assertEquals(0, resetState.sessionWins)
        assertEquals(0, resetState.sessionLosses)
        assertFalse(resetState.isRobotConfigOpen)
        assertTrue(resetState.analysisState is com.example.model.AnalysisUiState.Unavailable)
    }

    @Test
    fun `background continuity defaults active and only stops when manually paused, then restores on resume`() = runTest {
        val vm = com.example.ui.TrendLensViewModel(repo)
        assertTrue(vm.uiState.value.keepRunningInBackground)
        assertTrue(vm.uiState.value.autoScanEnabled)
        assertTrue(vm.uiState.value.autoClickEnabled)

        // Manual pause stops both AI scanning and robot entries
        vm.pauseAllMonitoring()
        assertFalse(vm.uiState.value.autoScanEnabled)
        assertFalse(vm.uiState.value.autoClickEnabled)

        // Resuming restores both AI scanning and robot auto-click and emits immediate capture
        val captures = mutableListOf<com.example.model.NormalizedRect>()
        val job = backgroundScope.launch(Dispatchers.Unconfined) {
            vm.captureTrigger.collect { captures.add(it) }
        }
        vm.resumeAiAndScanNow()
        assertTrue(vm.uiState.value.autoScanEnabled)
        assertTrue(vm.uiState.value.autoClickEnabled)
        assertTrue(vm.uiState.value.markedArea != null)
        assertEquals(1, captures.size)
        job.cancel()

        // Toggle background mode off and back on cleanly
        vm.toggleKeepRunningInBackground(false)
        assertFalse(vm.uiState.value.keepRunningInBackground)
        vm.toggleKeepRunningInBackground(true)
        assertTrue(vm.uiState.value.keepRunningInBackground)
    }

    @Test
    fun `broker anti-block config builds clean Chrome UA, preserves trust scripts, detects blocks, and clears WAF cookies`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val rawWvUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/134.0.6998.135 Mobile Safari/537.36"

        val mobileUa = com.example.data.BrokerSessionPrefs.buildCleanChromeUserAgent(rawWvUa, desktopMode = false)
        assertFalse("Mobile UA must strip '; wv'", mobileUa.contains("; wv"))
        assertFalse("Mobile UA must strip 'Version/4.0'", mobileUa.contains("Version/4.0"))
        assertTrue("Mobile UA must keep real Chromium version", mobileUa.contains("Chrome/134.0.6998.135"))

        val desktopUa = com.example.data.BrokerSessionPrefs.buildCleanChromeUserAgent(rawWvUa, desktopMode = true)
        assertTrue("Desktop UA must advertise Windows desktop Chrome", desktopUa.contains("Windows NT 10.0"))
        assertTrue("Desktop UA must keep aligned Chromium version", desktopUa.contains("Chrome/134.0.6998.135"))
        assertFalse("Desktop UA must not contain Mobile token", desktopUa.contains("Mobile"))

        // Headers enforce pt-BR and NEVER send a malformed empty X-Requested-With header
        assertEquals("pt-BR,pt;q=0.9", com.example.data.BrokerSessionPrefs.BRAZIL_PT_HEADERS["Accept-Language"])
        assertFalse(
            "Empty X-Requested-With header must not be sent",
            com.example.data.BrokerSessionPrefs.BRAZIL_PT_HEADERS.containsKey("X-Requested-With")
        )

        // Ephemeral Cloudflare / WAF / FingerprintJS cookies must NEVER be persisted or replayed
        assertTrue(com.example.data.BrokerSessionPrefs.isEphemeralWafCookieName("__cf_bm"))
        assertTrue(com.example.data.BrokerSessionPrefs.isEphemeralWafCookieName("cf_clearance"))
        assertTrue(com.example.data.BrokerSessionPrefs.isEphemeralWafCookieName("_cfuvid"))
        assertTrue(com.example.data.BrokerSessionPrefs.isEphemeralWafCookieName("cf_chl_2"))
        assertTrue(com.example.data.BrokerSessionPrefs.isEphemeralWafCookieName("__cflb"))
        assertTrue(com.example.data.BrokerSessionPrefs.isEphemeralWafCookieName("__cfruid"))
        assertFalse(com.example.data.BrokerSessionPrefs.isEphemeralWafCookieName("ssid"))
        assertFalse(com.example.data.BrokerSessionPrefs.isEphemeralWafCookieName("auth_token"))

        com.example.data.BrokerSessionPrefs.clearSavedBrokerUrl(context)
        val saved = com.example.data.BrokerSessionPrefs.persistSessionCookiesSnapshot(
            context = context,
            synchronous = true,
            cookieReader = { url ->
                if (url == "https://iqoption.com") {
                    "ssid=real_session_abc; __cf_bm=stale_bot_cookie; cf_clearance=old_cf_token; _cfuvid=old_uvid; cf_chl_2=stale_chl; lang=pt_PT"
                } else {
                    null
                }
            }
        )
        assertTrue("Auth session must still persist cleanly", saved)
        val restoredHeaders = mutableListOf<String>()
        val restored = com.example.data.BrokerSessionPrefs.restoreSessionCookiesIfMissing(
            context = context,
            cookieReader = { null },
            cookieWriter = { _, header -> restoredHeaders.add(header) },
            flusher = {}
        )
        assertTrue("Valid auth cookies must restore", restored)
        assertTrue(restoredHeaders.any { it.startsWith("ssid=real_session_abc;") })
        assertTrue(restoredHeaders.any { it.startsWith("lang=pt_PT;") })
        assertFalse("Must never replay __cf_bm", restoredHeaders.any { it.startsWith("__cf_bm=") })
        assertFalse("Must never replay cf_clearance", restoredHeaders.any { it.startsWith("cf_clearance=") })
        assertFalse("Must never replay _cfuvid", restoredHeaders.any { it.startsWith("_cfuvid=") })
        assertFalse("Must never replay cf_chl_2", restoredHeaders.any { it.startsWith("cf_chl_2=") })

        // Desktop mode preference persists cleanly
        com.example.data.BrokerSessionPrefs.setDesktopModeEnabled(context, true)
        assertTrue(com.example.data.BrokerSessionPrefs.isDesktopModeEnabled(context))
        com.example.data.BrokerSessionPrefs.setDesktopModeEnabled(context, false)
        assertFalse(com.example.data.BrokerSessionPrefs.isDesktopModeEnabled(context))

        // Only unreachable TURN relays are blocked; IQ Option's FingerprintJS trust scripts stay allowed
        assertTrue(com.example.ui.components.isBlockedTurnRelayHost("use1-turn.fpjs.io"))
        assertTrue(com.example.ui.components.isBlockedTurnRelayHost("turn.fpjs.io"))
        assertFalse(com.example.ui.components.isBlockedTurnRelayHost("fpcdn.io"))
        assertFalse(com.example.ui.components.isBlockedTurnRelayHost("api.fpjs.io"))
        assertTrue(com.example.ui.components.TURN_RELAY_ICE_SANITIZER_JS.contains("RTCPeerConnection"))
        assertTrue(com.example.ui.components.TURN_RELAY_ICE_SANITIZER_JS.contains("fpjs\\.io"))

        // Security challenges and auth pages are protected from redirect loops
        assertTrue(com.example.data.BrokerSessionPrefs.isSecurityChallengeOrAuthUrl("https://iqoption.com/cdn-cgi/challenge-platform/h/b"))
        assertTrue(com.example.data.BrokerSessionPrefs.isSecurityChallengeOrAuthUrl("https://challenges.cloudflare.com/turnstile/v0"))
        assertTrue(com.example.data.BrokerSessionPrefs.isSecurityChallengeOrAuthUrl("https://login.iqoption.com/pt/login"))
        assertTrue(com.example.data.BrokerSessionPrefs.isSecurityChallengeOrAuthUrl("https://accounts.google.com/o/oauth2/v2/auth"))
        assertFalse(com.example.data.BrokerSessionPrefs.isSecurityChallengeOrAuthUrl("https://iqoption.com/pwa/traderoom"))

        // Block classifier distinguishes WAF/Cloudflare, Geo-block, Browser block, and clean pages
        assertEquals(
            com.example.ui.components.BrokerBlockKind.WAF_OR_CLOUDFLARE,
            com.example.ui.components.detectBrokerBlockKindFromText("Just a moment... Verify you are human Cloudflare Ray ID: 1234", "https://iqoption.com")
        )
        assertEquals(
            com.example.ui.components.BrokerBlockKind.GEO_BLOCK,
            com.example.ui.components.detectBrokerBlockKindFromText("Sorry, IQ Option is unavailable in your country.", "https://iqoption.com")
        )
        assertEquals(
            com.example.ui.components.BrokerBlockKind.BROWSER_OR_LOGIN_BLOCK,
            com.example.ui.components.detectBrokerBlockKindFromText("Error 403: disallowed_useragent", "https://accounts.google.com")
        )
        assertEquals(
            com.example.ui.components.BrokerBlockKind.NONE,
            com.example.ui.components.detectBrokerBlockKindFromText("EUR/USD 85% Acima Abaixo Saldo R$ 10.000", "https://iqoption.com/pwa/traderoom")
        )
        assertEquals(
            com.example.ui.components.BrokerBlockKind.NONE,
            com.example.ui.components.detectBrokerBlockKindFromText("Entrar na IQ Option E-mail Senha Esqueceu a senha?", "https://login.iqoption.com/pt/login")
        )

        // Targeted WAF cleanup expires Cloudflare cookies without wiping unrelated app state
        val expiredCookies = mutableListOf<Pair<String, String>>()
        var flushed = false
        com.example.data.BrokerSessionPrefs.clearAntiBlockChallengeCookies(
            webView = null,
            cookieWriter = { domain, header -> expiredCookies.add(domain to header) },
            flusher = { flushed = true }
        )
        assertTrue(flushed)
        assertTrue(expiredCookies.any { (_, h) -> h.startsWith("cf_clearance=;") && h.contains("Max-Age=0") })
        assertTrue(expiredCookies.any { (_, h) -> h.startsWith("__cf_bm=;") && h.contains("Max-Age=0") })
    }

    @Test
    fun `ai adaptive auto-expiry parses 30s 1m 2m 5m and switches robot duration and broker trigger automatically`() = kotlinx.coroutines.runBlocking {
        // 1. Normalization snaps arbitrary values cleanly to 30, 60, 120, or 300
        assertEquals(30, com.example.model.normalizeAdaptiveExpirySeconds(30))
        assertEquals(30, com.example.model.normalizeAdaptiveExpirySeconds(40))
        assertEquals(60, com.example.model.normalizeAdaptiveExpirySeconds(60))
        assertEquals(120, com.example.model.normalizeAdaptiveExpirySeconds(120))
        assertEquals(120, com.example.model.normalizeAdaptiveExpirySeconds(180))
        assertEquals(300, com.example.model.normalizeAdaptiveExpirySeconds(300))

        // 2. Gemini JSON parser extracts recommendedExpirySeconds & expiryRationale when autoExpiryEnabled=true
        val aiRepo = FirebaseAiChartRepository()
        val macro5mJson = """
            {
              "isValidChart": true,
              "marketStructure": "HH_HL_UPTREND",
              "macroTrend": "UP",
              "microMomentum": "UP",
              "candleDominance": "BUYERS_CONTROL",
              "trendStrengthScore": 88,
              "confluenceCount": 5,
              "entryQuality": "STRONG",
              "riskFlag": "NONE",
              "trendLabel": "Tendência de alta",
              "patternLabel": "Fundos ascendentes limpos",
              "supportResistanceLabel": "Espaço aberto até resistência",
              "rsiEstimated": 56,
              "confidencePercent": 88,
              "direction": "CALL_UP",
              "recommendedExpirySeconds": 300,
              "expiryRationale": "Macrotendência limpa com amplo espaço para 5 min",
              "summary": "Alta sustentada com 5 pilares alinhados."
            }
        """.trimIndent()
        val parsed5m = aiRepo.parseGeminiOutcome(
            jsonText = macro5mJson,
            sourceDescription = "iqoption.com",
            boConfig = BoConfig(expirySeconds = 60, autoExpiryEnabled = true)
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.CALL_UP, parsed5m.result.direction)
        assertEquals(300, parsed5m.result.recommendedExpirySeconds)
        assertEquals("M5 (5 min)", parsed5m.result.timeframeLabel)
        assertTrue(parsed5m.result.expirySelectionReason.contains("5 min"))

        // When autoExpiryEnabled=false, fixed duration is respected and recommendedExpirySeconds is null
        val fixedParsed = aiRepo.parseGeminiOutcome(
            jsonText = macro5mJson,
            sourceDescription = "iqoption.com",
            boConfig = BoConfig(expirySeconds = 60, autoExpiryEnabled = false)
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(null, fixedParsed.result.recommendedExpirySeconds)
        assertEquals("M1 (1 min)", fixedParsed.result.timeframeLabel)

        // 3. Deterministic structure fallback selects 30s (near S/R or RSI edge), 2m (moderate structured), 5m (strong macro)
        assertEquals(
            30,
            aiRepo.chooseAdaptiveExpiryFromStructure(
                direction = SignalDirection.CALL_UP,
                marketStructure = "HH_HL_UPTREND",
                entryQuality = "STRONG",
                riskFlag = "SR_WALL",
                trendStrength = 76,
                confluenceCount = 4,
                rsi = 62
            )
        )
        assertEquals(
            120,
            aiRepo.chooseAdaptiveExpiryFromStructure(
                direction = SignalDirection.PUT_DOWN,
                marketStructure = "LH_LL_DOWNTREND",
                entryQuality = "MODERATE",
                riskFlag = "NONE",
                trendStrength = 75,
                confluenceCount = 4,
                rsi = 44
            )
        )
        assertEquals(
            300,
            aiRepo.chooseAdaptiveExpiryFromStructure(
                direction = SignalDirection.CALL_UP,
                marketStructure = "HH_HL_UPTREND",
                entryQuality = "STRONG",
                riskFlag = "NONE",
                trendStrength = 88,
                confluenceCount = 5,
                rsi = 54
            )
        )

        // 4. ViewModel updates expiry state & emits applyExpiryTrigger + AutoClickCommand.expirySecondsToApply
        var scriptedExpiry = 120
        val fakeRepo = object : com.example.data.AiRepository {
            override suspend fun analyzeChartRegion(
                bitmap: Bitmap,
                sourceDescription: String,
                boConfig: BoConfig,
                previousReadingHint: String?
            ): ChartAnalysisOutcome {
                val chosen = if (boConfig.autoExpiryEnabled) scriptedExpiry else null
                return ChartAnalysisOutcome.ValidChart(
                    com.example.model.ChartAnalysisResult(
                        id = "test-adaptive-$scriptedExpiry",
                        direction = SignalDirection.CALL_UP,
                        confidencePercent = 85,
                        assetLabel = "iqoption.com • EMA+RSI • ${com.example.model.formatExpiryLabel(chosen ?: boConfig.expirySeconds)}",
                        timeframeLabel = com.example.model.formatExpiryLabel(chosen ?: boConfig.expirySeconds),
                        rsiValue = 58,
                        trendLabel = "Tendência de alta",
                        patternLabel = "Pullback limpo em alta",
                        supportResistanceLabel = "Espaço livre",
                        summary = "Tendência clara.",
                        timestampLabel = "10:00:00",
                        isDemoMode = false,
                        noticeMessage = "IA",
                        trendStrengthScore = 84,
                        confluenceCount = 4,
                        recommendedExpirySeconds = chosen,
                        expirySelectionReason = if (chosen != null) "Melhor precisão para ${com.example.model.formatExpiryShortLabel(chosen)}" else ""
                    )
                )
            }
        }

        val vm = com.example.ui.TrendLensViewModel(fakeRepo, clockEpochMs = { 600_000L })
        assertTrue(vm.uiState.value.boConfig.autoExpiryEnabled)
        assertEquals(60, vm.uiState.value.boConfig.expirySeconds)

        val expiryTriggers = mutableListOf<Int>()
        val clickCommands = mutableListOf<com.example.ui.AutoClickCommand>()
        val collectJob1 = launch(Dispatchers.Unconfined) {
            vm.applyExpiryTrigger.collect { expiryTriggers.add(it) }
        }
        val collectJob2 = launch(Dispatchers.Unconfined) {
            vm.autoClickTrigger.collect { clickCommands.add(it) }
        }
        kotlinx.coroutines.yield()

        val bmp = createCandlestickChartBitmap(Color.rgb(18, 26, 36), uptrend = true)
        vm.onRegionBitmapCaptured(bmp, force = true)
        repeat(20) {
            if (vm.uiState.value.boConfig.expirySeconds == 120 && clickCommands.isNotEmpty()) return@repeat
            kotlinx.coroutines.delay(25L)
        }
        assertEquals(120, vm.uiState.value.boConfig.expirySeconds)
        assertEquals(120, vm.uiState.value.autoScanIntervalSec)
        assertEquals(120, vm.uiState.value.autoClickCooldownSec)
        assertTrue("Expected broker expiry trigger for 120s", expiryTriggers.contains(120))
        assertTrue("Expected auto-click command with expirySecondsToApply=120", clickCommands.any { it.expirySecondsToApply == 120 })

        // Manual lock disables autoExpiryEnabled and emits broker sync for 30s
        vm.setAutoScanInterval(30, disableAutoExpiry = true)
        kotlinx.coroutines.yield()
        assertFalse(vm.uiState.value.boConfig.autoExpiryEnabled)
        assertEquals(30, vm.uiState.value.boConfig.expirySeconds)
        assertTrue("Expected broker expiry trigger for manual 30s", expiryTriggers.contains(30))

        // Re-enabling Auto IA switches back to adaptive mode
        scriptedExpiry = 300
        vm.toggleAutoExpiry(true)
        assertTrue(vm.uiState.value.boConfig.autoExpiryEnabled)
        vm.onRegionBitmapCaptured(bmp, force = true)
        repeat(20) {
            if (vm.uiState.value.boConfig.expirySeconds == 300) return@repeat
            kotlinx.coroutines.delay(25L)
        }
        assertEquals(300, vm.uiState.value.boConfig.expirySeconds)
        assertTrue("Expected broker expiry trigger for 300s", expiryTriggers.contains(300))

        collectJob1.cancel()
        collectJob2.cancel()

        // 5. WebViewBotController expiry probe & picker scripts and parsers
        val probeMatch = com.example.ui.components.parseExpiryBoxProbeOutcome(
            "\"{\\\"alreadyMatches\\\":true,\\\"tapNormX\\\":0.25,\\\"tapNormY\\\":0.82,\\\"currentText\\\":\\\"2 min\\\"}\""
        )
        assertTrue(probeMatch.alreadyMatches)
        assertEquals("2 min", probeMatch.currentText)

        val probeNeedsTap = com.example.ui.components.parseExpiryBoxProbeOutcome(
            "\"{\\\"alreadyMatches\\\":false,\\\"tapNormX\\\":0.24,\\\"tapNormY\\\":0.81,\\\"currentText\\\":\\\"1 min\\\"}\""
        )
        assertFalse(probeNeedsTap.alreadyMatches)
        assertEquals(0.24f, probeNeedsTap.tapNormX!!, 0.001f)
        assertEquals(0.81f, probeNeedsTap.tapNormY!!, 0.001f)

        assertTrue(com.example.ui.components.parseExpirySelectResult("\"{\\\"selected\\\":true}\""))
        assertFalse(com.example.ui.components.parseExpirySelectResult("\"{\\\"selected\\\":false}\""))
        assertTrue(com.example.ui.components.buildFindExpiryBoxScript(30).contains("30"))
        assertTrue(com.example.ui.components.buildSelectExpiryOptionScript(300).contains("5 min"))
    }

    @Test
    fun `chart area auto-detects live canvas bounds, auto-redefines on layout shift, persists across restarts, and survives mode switches`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

        // 1. Live canvas bounds parser extracts exact chart coordinates safely
        val parsedBounds = com.example.ui.components.parseChartBoundsResult(
            "\"{\\\"ready\\\":true,\\\"left\\\":0.04,\\\"top\\\":0.13,\\\"right\\\":0.96,\\\"bottom\\\":0.67}\""
        )
        assertTrue(parsedBounds.ready)
        assertTrue(parsedBounds.bounds != null)
        assertEquals(0.04f, parsedBounds.bounds!!.left, 0.001f)
        assertEquals(0.13f, parsedBounds.bounds!!.top, 0.001f)
        assertEquals(0.96f, parsedBounds.bounds!!.right, 0.001f)
        assertEquals(0.67f, parsedBounds.bounds!!.bottom, 0.001f)

        // 2. ViewModel auto-frames and automatically redefines when the broker canvas shifts
        val vm = com.example.ui.TrendLensViewModel(repo)
        assertTrue(vm.uiState.value.autoChartAreaEnabled)

        val initialBounds = com.example.model.NormalizedRect(0.04f, 0.13f, 0.96f, 0.67f)
        vm.applyAutoDetectedChartArea(initialBounds)
        assertEquals(initialBounds, vm.uiState.value.markedArea)
        assertTrue(vm.uiState.value.autoChartAreaEnabled)

        val shiftedBounds = com.example.model.NormalizedRect(0.05f, 0.18f, 0.95f, 0.72f)
        vm.applyAutoDetectedChartArea(shiftedBounds)
        assertEquals("Auto mode must redefine area automatically when canvas shifts", shiftedBounds, vm.uiState.value.markedArea)

        // 3. Switching to Gallery and back to WebView preserves the framed WebView chart area
        vm.setSourceMode(com.example.model.CaptureSourceMode.GALLERY)
        assertEquals(null, vm.uiState.value.markedArea)
        vm.setSourceMode(com.example.model.CaptureSourceMode.WEBVIEW)
        assertEquals(shiftedBounds, vm.uiState.value.markedArea)

        // 4. Manual adjustment locks custom area until user taps Auto again
        val manualBounds = com.example.model.NormalizedRect(0.10f, 0.20f, 0.90f, 0.62f)
        vm.startMarkingArea()
        vm.updateDraftSelection(manualBounds)
        vm.confirmMarkedArea()
        assertEquals(manualBounds, vm.uiState.value.markedArea)
        assertFalse(vm.uiState.value.autoChartAreaEnabled)
        vm.applyAutoDetectedChartArea(initialBounds)
        assertEquals("Manual override must not be overwritten automatically", manualBounds, vm.uiState.value.markedArea)

        // Snapping back to Auto re-enables automatic live re-framing
        vm.enableAutoChartArea(initialBounds)
        assertTrue(vm.uiState.value.autoChartAreaEnabled)
        assertEquals(initialBounds, vm.uiState.value.markedArea)

        // 5. BrokerSessionPrefs persists and restores chart area across cold app restarts
        com.example.data.BrokerSessionPrefs.saveChartArea(context, shiftedBounds, isAutoManaged = true)
        val loaded = com.example.data.BrokerSessionPrefs.loadSavedChartArea(context)
        assertTrue(loaded != null)
        assertTrue(loaded!!.isAutoManaged)
        assertEquals(shiftedBounds.left, loaded.rect.left, 0.001f)
        assertEquals(shiftedBounds.top, loaded.rect.top, 0.001f)
        assertEquals(shiftedBounds.right, loaded.rect.right, 0.001f)
        assertEquals(shiftedBounds.bottom, loaded.rect.bottom, 0.001f)

        val freshVm = com.example.ui.TrendLensViewModel(repo)
        freshVm.restoreSavedChartArea(loaded.rect, loaded.isAutoManaged)
        assertEquals(loaded.rect, freshVm.uiState.value.markedArea)
        assertTrue(freshVm.uiState.value.autoChartAreaEnabled)
    }

    @Test
    fun `secure broker session manager persists encrypted tokens, migrates legacy plaintext, blocks injection, and enforces expiry`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        com.example.data.SecureBrokerSessionManager.resetInternalCacheForTesting()
        com.example.data.SecureBrokerSessionManager.clearSession(context, clearWebViewCookies = false)

        try {
            var fakeNowMs = 1_700_000_000_000L
            com.example.data.SecureBrokerSessionManager.clockProvider = { fakeNowMs }

            // 1. Legacy plaintext session cookies are migrated into the secure vault and scrubbed immediately
            val legacyPlainPrefs = context.getSharedPreferences("trendlens_broker_session", android.content.Context.MODE_PRIVATE)
            legacyPlainPrefs.edit()
                .putString("saved_cookies_iq_root", "ssid=legacy_ssid_999; __cf_bm=stale_waf; lang=pt_PT")
                .commit()

            assertTrue(com.example.data.SecureBrokerSessionManager.hasValidSavedSession(context))
            assertEquals("legacy_ssid_999", com.example.data.SecureBrokerSessionManager.getAuthToken(context))
            assertEquals(
                "Legacy plaintext cookie key must be scrubbed after migration",
                null,
                legacyPlainPrefs.getString("saved_cookies_iq_root", null)
            )

            // 2. Explicit token persistence rejects CR/LF header injection & blank/deleted values
            assertFalse(com.example.data.SecureBrokerSessionManager.saveAuthToken(context, "bad\r\nSet-Cookie: evil=1"))
            assertFalse(com.example.data.SecureBrokerSessionManager.saveAuthToken(context, "deleted"))
            assertFalse(com.example.data.SecureBrokerSessionManager.saveAuthToken(context, "   "))

            assertTrue(
                com.example.data.SecureBrokerSessionManager.saveAuthToken(
                    context = context,
                    token = "secure_token_xyz_2026",
                    tokenName = "ssid",
                    refreshToken = "refresh_abc_789",
                    ttlMillis = 3_600_000L,
                    synchronous = true
                )
            )
            assertEquals("secure_token_xyz_2026", com.example.data.SecureBrokerSessionManager.getAuthToken(context))
            assertEquals("refresh_abc_789", com.example.data.SecureBrokerSessionManager.getRefreshToken(context))
            val status = com.example.data.SecureBrokerSessionManager.getSessionStatus(context)
            assertTrue(status.hasSavedSession)
            assertEquals("ssid", status.primaryTokenName)
            assertEquals(fakeNowMs + 3_600_000L, status.expiresAtEpochMs)

            // Never leak tokens into plaintext SharedPreferences
            assertEquals(null, legacyPlainPrefs.getString("saved_cookies_iq_root", null))
            assertEquals(null, legacyPlainPrefs.getString("broker_primary_auth_token", null))

            // 3. Restores auth + refresh token cookies into WebView when missing, with Secure & Max-Age
            val written = mutableListOf<Pair<String, String>>()
            var flushed = false
            val restored = com.example.data.SecureBrokerSessionManager.restoreToWebViewIfMissing(
                context = context,
                cookieReader = { null },
                cookieWriter = { domain, header -> written.add(domain to header) },
                flusher = { flushed = true }
            )
            assertTrue(restored)
            assertTrue(flushed)
            assertTrue(written.any { (d, h) ->
                d == "https://iqoption.com" &&
                    h.startsWith("ssid=secure_token_xyz_2026;") &&
                    h.contains("Secure;") &&
                    h.contains("Max-Age=3600")
            })
            assertTrue(written.any { (_, h) -> h.startsWith("refresh_token=refresh_abc_789;") })

            // 4. Never overwrites active live WebView auth cookies
            val activeSkipped = com.example.data.SecureBrokerSessionManager.restoreToWebViewIfMissing(
                context = context,
                cookieReader = { "ssid=live_fresher_token" },
                cookieWriter = { _, _ -> throw AssertionError("Must not overwrite active session cookies") },
                flusher = {}
            )
            assertFalse(activeSkipped)

            // 5. Enforces expiration automatically and clears expired tokens
            fakeNowMs += 3_600_001L
            assertFalse(com.example.data.SecureBrokerSessionManager.hasValidSavedSession(context))
            assertEquals(null, com.example.data.SecureBrokerSessionManager.getAuthToken(context))
            assertEquals(null, com.example.data.SecureBrokerSessionManager.getRefreshToken(context))

            // 6. Captures live WebView cookies, strips ephemeral WAF cookies, and clears cleanly on logout
            assertTrue(
                com.example.data.SecureBrokerSessionManager.captureAndPersistFromWebView(
                    context = context,
                    synchronous = true,
                    cookieReader = { url ->
                        if (url == "https://iqoption.com") {
                            "ssid=live_captured_888; refresh_token=ref_888; __cf_bm=waf_drop; cf_clearance=cf_drop"
                        } else null
                    }
                )
            )
            assertEquals("live_captured_888", com.example.data.SecureBrokerSessionManager.getAuthToken(context))
            assertEquals("ref_888", com.example.data.SecureBrokerSessionManager.getRefreshToken(context))

            com.example.data.SecureBrokerSessionManager.clearSession(context, clearWebViewCookies = false)
            assertFalse(com.example.data.SecureBrokerSessionManager.hasValidSavedSession(context))
            assertEquals(null, com.example.data.SecureBrokerSessionManager.getAuthToken(context))
        } finally {
            com.example.data.SecureBrokerSessionManager.resetInternalCacheForTesting()
        }
    }

    @Test
    fun `visual canvas button detector locates green Acima and red Abaixo buttons and conservative mode enters clean trends with normal wicks`() = runTest {
        // 1. IQ Option style dark lower panel with solid red PUT (left) and solid green CALL (right)
        val panel = createSolidBitmap(480, 200, Color.rgb(20, 26, 36))
        // Small stray candle lines at top of bottom strip must be ignored
        fillRect(panel, 60, 4, 65, 36, Color.rgb(44, 185, 95))
        fillRect(panel, 90, 4, 95, 36, Color.rgb(225, 65, 55))
        // Solid PUT red button on lower-left (x: 16..228, y: 120..186)
        fillRect(panel, 16, 120, 228, 186, Color.rgb(219, 73, 49))
        // Solid CALL green button on lower-right (x: 252..464, y: 120..186)
        fillRect(panel, 252, 120, 464, 186, Color.rgb(44, 172, 64))

        val callPt = com.example.ui.components.detectTradeButtonFromBottomBitmap(panel, SignalDirection.CALL_UP, regionTopNorm = 0.65f)
        val putPt = com.example.ui.components.detectTradeButtonFromBottomBitmap(panel, SignalDirection.PUT_DOWN, regionTopNorm = 0.65f)
        assertTrue("Expected visual detector to locate green CALL button", callPt != null)
        assertTrue("Expected visual detector to locate red PUT button", putPt != null)
        assertTrue("Expected CALL button on right half, got ${callPt!!.normX}", callPt.normX in 0.60f..0.90f)
        assertTrue("Expected PUT button on left half, got ${putPt!!.normX}", putPt.normX in 0.10f..0.40f)
        assertTrue("Expected buttons in bottom WebView band, got ${callPt.normY}", callPt.normY in 0.80f..0.985f)

        // Blank panel returns null cleanly
        val blankPanel = createSolidBitmap(480, 200, Color.rgb(20, 26, 36))
        assertEquals(null, com.example.ui.components.detectTradeButtonFromBottomBitmap(blankPanel, SignalDirection.CALL_UP))

        // 2. Balanced CONSERVATIVE mode accepts clean trend even with normal minor wick (OPPOSING_WICK)
        val aiRepo = FirebaseAiChartRepository()
        val conservativeConfig = BoConfig(sensitivity = BoSensitivity.CONSERVATIVE)
        val cleanWithNormalWick = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"HH_HL_UPTREND","macroTrend":"UP","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":70,"confluenceCount":3,"entryQuality":"MODERATE","riskFlag":"OPPOSING_WICK","trendLabel":"Tendência de alta","patternLabel":"Continuação com pavio normal","supportResistanceLabel":"Espaço aberto","rsiEstimated":59,"confidencePercent":78,"direction":"CALL_UP","summary":"Alta firme com pavio normal."}""",
            sourceDescription = "iqoption.com",
            boConfig = conservativeConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.CALL_UP, cleanWithNormalWick.result.direction)
        assertTrue("Expected confidence >= 72 after minor wick penalty", cleanWithNormalWick.result.confidencePercent >= 72)

        // 3. ViewModel uses cached auto-detected trade point automatically on trend signal
        val trendRepo = object : com.example.data.AiRepository {
            override suspend fun analyzeChartRegion(
                bitmap: Bitmap,
                sourceDescription: String,
                boConfig: BoConfig,
                previousReadingHint: String?
            ): ChartAnalysisOutcome = cleanWithNormalWick
        }
        val vm = com.example.ui.TrendLensViewModel(trendRepo, clockEpochMs = { 600_000L })
        vm.cacheAutoDetectedTradePoint(SignalDirection.CALL_UP, callPt)

        val emitted = mutableListOf<com.example.ui.AutoClickCommand>()
        val collectJob = backgroundScope.launch(Dispatchers.Unconfined) {
            vm.autoClickTrigger.collect { emitted.add(it) }
        }
        val chartBmp = createCandlestickChartBitmap(Color.rgb(19, 26, 38), uptrend = true)
        vm.onRegionBitmapCaptured(chartBmp, force = true)
        kotlinx.coroutines.delay(80L)
        assertEquals(1, emitted.size)
        assertEquals(SignalDirection.CALL_UP, emitted.first().direction)
        assertEquals(callPt, emitted.first().calibratedPoint)
        collectJob.cancel()
    }

    @Test
    fun `three-condition strategy requires 3 consecutive candles, RSI turn out of extreme, and rejects sideways market`() {
        val aiRepo = FirebaseAiChartRepository()
        val strictConfig = BoConfig(
            sensitivity = BoSensitivity.CONSERVATIVE,
            strictThreeCandleRsiStrategy = true,
            waitForCandleClose = true
        )

        // 1. Fails Rule 1: only 2 consecutive candles -> blocked to NEUTRAL
        val onlyTwoCandles = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"HH_HL_UPTREND","macroTrend":"UP","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":82,"confluenceCount":4,"entryQuality":"STRONG","riskFlag":"NONE","consecutiveSameDirCandles":2,"rsiTurnState":"LEAVING_OVERSOLD_UP","candleCloseConfirmed":true,"trendLabel":"Tendência de alta","patternLabel":"2 velas verdes","supportResistanceLabel":"Espaço livre","rsiEstimated":36,"confidencePercent":84,"direction":"CALL_UP","summary":"Apenas duas velas seguidas."}""",
            sourceDescription = "iqoption.com",
            boConfig = strictConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, onlyTwoCandles.result.direction)
        assertTrue(onlyTwoCandles.result.patternLabel.contains("3 velas"))

        // 2. Fails Rule 2: 3 candles ok, but RSI is flat/not leaving oversold -> blocked to NEUTRAL
        val badRsiCall = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"HH_HL_UPTREND","macroTrend":"UP","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":84,"confluenceCount":4,"entryQuality":"STRONG","riskFlag":"NONE","consecutiveSameDirCandles":3,"rsiTurnState":"FLAT_OR_MIDDLE","candleCloseConfirmed":true,"trendLabel":"Tendência de alta","patternLabel":"3 velas verdes","supportResistanceLabel":"Espaço livre","rsiEstimated":52,"confidencePercent":85,"direction":"CALL_UP","summary":"RSI sem virada."}""",
            sourceDescription = "iqoption.com",
            boConfig = strictConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, badRsiCall.result.direction)
        assertTrue(badRsiCall.result.patternLabel.contains("RSI"))

        // 3. Fails Sideways Filter: even in MODERATE, sideways macroTrend is strictly blocked
        val sidewaysModerate = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"CONSOLIDATION_RANGE","macroTrend":"SIDEWAYS","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":74,"confluenceCount":4,"entryQuality":"STRONG","riskFlag":"NONE","consecutiveSameDirCandles":3,"rsiTurnState":"LEAVING_OVERSOLD_UP","candleCloseConfirmed":true,"trendLabel":"Mercado lateral","patternLabel":"Caixa lateral","supportResistanceLabel":"Topo da caixa","rsiEstimated":35,"confidencePercent":82,"direction":"CALL_UP","summary":"Mercado lateralizado."}""",
            sourceDescription = "iqoption.com",
            boConfig = strictConfig.copy(sensitivity = BoSensitivity.MODERATE)
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, sidewaysModerate.result.direction)
        assertTrue(sidewaysModerate.result.patternLabel.contains("lateral", ignoreCase = true))

        // 4. All 3 conditions align for CALL (3 bullish candles + RSI leaving oversold up + candle closed)
        val validCall = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"HH_HL_UPTREND","macroTrend":"UP","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":86,"confluenceCount":4,"entryQuality":"STRONG","riskFlag":"NONE","consecutiveSameDirCandles":3,"rsiTurnState":"LEAVING_OVERSOLD_UP","candleCloseConfirmed":true,"trendLabel":"Tendência de alta","patternLabel":"3 velas verdes firmes","supportResistanceLabel":"Espaço aberto","rsiEstimated":35,"confidencePercent":87,"direction":"CALL_UP","summary":"3 velas de alta com RSI saindo da sobrevenda."}""",
            sourceDescription = "iqoption.com",
            boConfig = strictConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.CALL_UP, validCall.result.direction)
        assertEquals(3, validCall.result.consecutiveSameDirectionCandles)
        assertEquals("LEAVING_OVERSOLD_UP", validCall.result.rsiTurnState)
        assertTrue(validCall.result.candleCloseConfirmed)
        assertEquals(3, validCall.result.strategyRulesMetCount)

        // 5. All 3 conditions align for PUT (3 bearish candles + RSI leaving overbought down + candle closed)
        val validPut = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"LH_LL_DOWNTREND","macroTrend":"DOWN","microMomentum":"DOWN","candleDominance":"SELLERS_CONTROL","trendStrengthScore":85,"confluenceCount":4,"entryQuality":"STRONG","riskFlag":"NONE","consecutiveSameDirCandles":4,"rsiTurnState":"LEAVING_OVERBOUGHT_DOWN","candleCloseConfirmed":true,"trendLabel":"Tendência de baixa","patternLabel":"4 velas vermelhas firmes","supportResistanceLabel":"Espaço aberto","rsiEstimated":66,"confidencePercent":86,"direction":"PUT_DOWN","summary":"4 velas de baixa com RSI saindo da sobrecompra."}""",
            sourceDescription = "iqoption.com",
            boConfig = strictConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.PUT_DOWN, validPut.result.direction)
        assertEquals(4, validPut.result.consecutiveSameDirectionCandles)
        assertEquals("LEAVING_OVERBOUGHT_DOWN", validPut.result.rsiTurnState)
        assertEquals(3, validPut.result.strategyRulesMetCount)
    }

    @Test
    fun `robot waits for candle close mid-candle, pauses 5 min after 2 consecutive losses, enforces session stop loss, and prevents infinite martingale`() = runTest {
        val validSignalOutcome = ChartAnalysisOutcome.ValidChart(
            com.example.model.ChartAnalysisResult(
                id = "sig-3-rules",
                direction = SignalDirection.CALL_UP,
                confidencePercent = 88,
                assetLabel = "EUR/USD • EMA+RSI • M1 (1 min)",
                timeframeLabel = "M1 (1 min)",
                rsiValue = 35,
                trendLabel = "Tendência de alta",
                patternLabel = "3 velas consecutivas de alta",
                supportResistanceLabel = "Espaço livre",
                summary = "3 velas + RSI saindo da sobrevenda.",
                timestampLabel = "10:00:00",
                isDemoMode = false,
                noticeMessage = "IA",
                trendStrengthScore = 86,
                confluenceCount = 4,
                recommendedExpirySeconds = 60,
                consecutiveSameDirectionCandles = 3,
                rsiTurnState = "LEAVING_OVERSOLD_UP",
                candleCloseConfirmed = true,
                strategyRulesMetCount = 3
            )
        )
        val signalRepo = object : com.example.data.AiRepository {
            override suspend fun analyzeChartRegion(
                bitmap: Bitmap,
                sourceDescription: String,
                boConfig: BoConfig,
                previousReadingHint: String?
            ): ChartAnalysisOutcome = validSignalOutcome
        }

        // 1. Mid-candle (:25 of a 60s candle) -> NEVER clicks immediately; arms candle-close countdown
        var nowMs = 1_700_000_040_000L + 25_000L // :25 within a 60s minute
        val vm = com.example.ui.TrendLensViewModel(signalRepo, clockEpochMs = { nowMs })
        val emitted = mutableListOf<com.example.ui.AutoClickCommand>()
        val collectJob = backgroundScope.launch(Dispatchers.Unconfined) {
            vm.autoClickTrigger.collect { emitted.add(it) }
        }
        val chartBmp = createCandlestickChartBitmap(Color.rgb(19, 26, 38), uptrend = true)

        vm.onRegionBitmapCaptured(chartBmp, force = true)
        kotlinx.coroutines.delay(80L)
        assertEquals("Robot must NOT click mid-candle!", 0, emitted.size)
        assertEquals(SignalDirection.CALL_UP, vm.uiState.value.pendingCandleCloseDirection)
        assertEquals(35, vm.uiState.value.pendingCandleCloseRemainingSec)
        vm.cancelPendingCandleClose()

        // 2. Two consecutive losses trigger mandatory 5-minute (300s) pause and block auto-click even at candle close
        nowMs = 1_700_000_040_000L // exact :00 candle close boundary
        vm.setSessionMaxLossLimit(50.0)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS)
        assertEquals(0, vm.uiState.value.lossPauseRemainingSec)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS)
        assertEquals(300, vm.uiState.value.lossPauseRemainingSec)
        assertEquals(nowMs + 300_000L, vm.uiState.value.lossPauseUntilEpochMs)

        vm.onRegionBitmapCaptured(chartBmp, force = true)
        kotlinx.coroutines.delay(80L)
        assertEquals("2-loss 5-minute pause must block auto-entries", 0, emitted.size)

        // After 5 minutes pass, pause expires cleanly and robot enters on candle close
        nowMs += 300_000L // still exact :00 boundary
        vm.onRegionBitmapCaptured(chartBmp, force = true)
        kotlinx.coroutines.delay(80L)
        assertEquals("Robot should resume after 5-minute pause expires", 1, emitted.size)

        // 3. Session Max Loss Limit (Stop Loss) stops robot completely until user resets session
        vm.resetSessionRiskGuards()
        vm.setRiskStrategy(com.example.model.RiskStrategy.FIXED)
        vm.setMartingaleInitialStake(10.0)
        vm.setSessionMaxLossLimit(10.0)
        vm.recordTradeOutcome(com.example.model.TradeOutcome.LOSS)
        assertTrue(vm.uiState.value.isSessionStopLossHit)
        assertEquals(10.0, vm.uiState.value.sessionNetLoss, 0.001)

        nowMs += 120_000L
        vm.onRegionBitmapCaptured(chartBmp, force = true)
        kotlinx.coroutines.delay(80L)
        assertEquals("Session Stop Loss must block all auto-entries", 1, emitted.size)

        // Resetting session clears Stop Loss & resumes cleanly
        vm.resetSessionRiskGuards()
        assertFalse(vm.uiState.value.isSessionStopLossHit)
        assertEquals(0.0, vm.uiState.value.sessionNetLoss, 0.001)

        // 4. No infinite martingale: maxSteps is strictly capped finite (<= 7)
        vm.setMartingaleMaxSteps(999)
        assertEquals(7, vm.uiState.value.martingaleConfig.maxSteps)

        collectJob.cancel()
    }

    @Test
    fun `encrypted auto-login vault stores credentials safely, masks email, survives session expiry, and wipes on full reset`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        com.example.data.SecureBrokerSessionManager.resetInternalCacheForTesting()
        com.example.data.SecureBrokerSessionManager.clearAutoLoginCredentials(context, synchronous = true)

        // Rejects invalid email or control characters
        assertFalse(
            com.example.data.SecureBrokerSessionManager.saveAutoLoginCredentials(
                context = context,
                email = "invalid-email",
                password = "SyntheticPass!2026",
                enabled = true,
                synchronous = true
            )
        )
        assertFalse(
            com.example.data.SecureBrokerSessionManager.saveAutoLoginCredentials(
                context = context,
                email = "trader@example.com\r\n",
                password = "SyntheticPass!2026",
                enabled = true,
                synchronous = true
            )
        )

        // Valid synthetic credentials are saved in secure vault and never written to legacy plaintext prefs
        val saved = com.example.data.SecureBrokerSessionManager.saveAutoLoginCredentials(
            context = context,
            email = "  trader@example.com  ",
            password = "SyntheticPass!2026",
            enabled = true,
            synchronous = true
        )
        assertTrue(saved)

        val status = com.example.data.SecureBrokerSessionManager.getAutoLoginStatus(context)
        assertTrue(status.hasCredentials)
        assertTrue(status.enabled)
        assertEquals("tr***@example.com", status.maskedEmail)

        val creds = com.example.data.SecureBrokerSessionManager.getAutoLoginCredentials(context)
        assertEquals("trader@example.com", creds?.email)
        assertEquals("SyntheticPass!2026", creds?.password)

        // Verify legacy unencrypted preferences contain no credentials
        val legacyDump = context.getSharedPreferences("trendlens_broker_session", android.content.Context.MODE_PRIVATE)
            .all.toString()
        assertFalse(legacyDump.contains("SyntheticPass!2026"))
        assertFalse(legacyDump.contains("trader@example.com"))

        // Disabling pauses auto-login without erasing vault
        com.example.data.SecureBrokerSessionManager.setAutoLoginEnabled(context, enabled = false, synchronous = true)
        assertFalse(com.example.data.SecureBrokerSessionManager.getAutoLoginStatus(context).enabled)
        assertTrue(com.example.data.SecureBrokerSessionManager.getAutoLoginStatus(context).hasCredentials)
        assertEquals(null, com.example.data.SecureBrokerSessionManager.getAutoLoginCredentials(context))

        // Re-enabling restores access; token-only clearSession preserves the auto-login vault
        com.example.data.SecureBrokerSessionManager.setAutoLoginEnabled(context, enabled = true, synchronous = true)
        com.example.data.SecureBrokerSessionManager.clearSession(
            context = context,
            clearWebViewCookies = false,
            clearAutoLoginVault = false
        )
        assertTrue(com.example.data.SecureBrokerSessionManager.getAutoLoginStatus(context).hasCredentials)
        assertTrue(com.example.data.SecureBrokerSessionManager.getAutoLoginStatus(context).enabled)

        // Full reset (clearAutoLoginVault = true) wipes credentials completely
        com.example.data.SecureBrokerSessionManager.clearSession(
            context = context,
            clearWebViewCookies = false,
            clearAutoLoginVault = true
        )
        val afterWipe = com.example.data.SecureBrokerSessionManager.getAutoLoginStatus(context)
        assertFalse(afterWipe.hasCredentials)
        assertFalse(afterWipe.enabled)
        assertEquals("", afterWipe.maskedEmail)
        assertEquals(null, com.example.data.SecureBrokerSessionManager.getAutoLoginCredentials(context))
    }

    @Test
    fun `auto-login URL allowlist permits only official HTTPS IQ Option hosts and blocks spoofed origins`() {
        assertTrue(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl("https://iqoption.com/pwa/traderoom"))
        assertTrue(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl("https://www.iqoption.com/pt"))
        assertTrue(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl("https://login.iqoption.com/pt/login"))
        assertTrue(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl("https://auth.iqoption.com/login"))
        assertTrue(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl("https://app.iqoption.com/traderoom"))

        assertFalse(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl("http://iqoption.com/pt/login"))
        assertFalse(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl("https://evil-iqoption.com/login"))
        assertFalse(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl("https://iqoption.com.attacker.example/login"))
        assertFalse(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl("https://accounts.google.com/signin"))
        assertFalse(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl("javascript:alert(1)"))
        assertFalse(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl(""))
        assertFalse(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl(null))
    }

    @Test
    fun `auto-login script safely escapes special characters and outcome parser decodes all statuses`() {
        val script = com.example.ui.components.buildAutoLoginScript(
            email = "user+test@example.com",
            password = "P@ss\"word'\\2026"
        )
        // Strict in-page HTTPS + host regex guard + CAPTCHA/2FA guards must be present
        assertTrue(script.contains("window.location.protocol !== 'https:'"))
        assertTrue(script.contains("iqoption\\.com"))
        assertTrue(script.contains("PAUSED_CHALLENGE"))
        assertTrue(script.contains("PAUSED_ERROR"))
        // Password quotes/backslashes must be JSON-escaped safely
        assertTrue(script.contains("\"P@ss\\\"word'\\\\2026\""))

        val submitted = com.example.ui.components.parseAutoLoginOutcome(
            "\"{\\\"status\\\":\\\"SUBMITTED\\\",\\\"detail\\\":\\\"login_submitted\\\"}\""
        )
        assertEquals(com.example.ui.components.AutoLoginStepStatus.SUBMITTED, submitted.status)
        assertEquals("login_submitted", submitted.detail)

        val challenge2fa = com.example.ui.components.parseAutoLoginOutcome(
            "{\"status\":\"PAUSED_CHALLENGE\",\"detail\":\"2fa\"}"
        )
        assertEquals(com.example.ui.components.AutoLoginStepStatus.PAUSED_CHALLENGE, challenge2fa.status)
        assertEquals("2fa", challenge2fa.detail)

        val invalidCreds = com.example.ui.components.parseAutoLoginOutcome(
            "{\"status\":\"PAUSED_ERROR\",\"detail\":\"invalid_credentials\"}"
        )
        assertEquals(com.example.ui.components.AutoLoginStepStatus.PAUSED_ERROR, invalidCreds.status)

        val malformed = com.example.ui.components.parseAutoLoginOutcome("not-json")
        assertEquals(com.example.ui.components.AutoLoginStepStatus.NO_FORM, malformed.status)
    }

    @Test
    fun `encrypted localStorage snapshot strips WAF keys, preserves password on email-only update, and captures api subdomain cookies`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        com.example.data.SecureBrokerSessionManager.resetInternalCacheForTesting()
        com.example.data.SecureBrokerSessionManager.clearSession(context, clearWebViewCookies = false, clearAutoLoginVault = true)

        // 1. localStorage snapshot saves valid SPA keys and strips WAF / Cloudflare / FPJS keys
        val rawSnapshot = """{"iq_user_pref":"pt_BR","pwa_session_state":"active_123","cf_clearance":"bad","fpjs_visitor":"drop","turnstile_token":"drop"}"""
        assertTrue(
            com.example.data.SecureBrokerSessionManager.saveWebLocalStorageSnapshot(
                context = context,
                rawJson = rawSnapshot,
                synchronous = true
            )
        )
        val stored = com.example.data.SecureBrokerSessionManager.getWebLocalStorageSnapshot(context).orEmpty()
        assertTrue(stored.contains("iq_user_pref"))
        assertTrue(stored.contains("pwa_session_state"))
        assertFalse(stored.contains("cf_clearance"))
        assertFalse(stored.contains("fpjs_visitor"))
        assertFalse(stored.contains("turnstile_token"))

        // 2. Restore script enforces strict HTTPS IQ Option host guard and safely quotes JSON
        val restoreJs = com.example.ui.components.buildLocalStorageRestoreScript(stored)
        assertTrue(restoreJs.contains("window.location.protocol !== 'https:'"))
        assertTrue(restoreJs.contains("iqoption\\.com"))
        assertTrue(restoreJs.contains("window.localStorage.getItem(k) === null"))

        // 3. Captures auth cookies from api.iqoption.com and eu.iqoption.com
        val captured = com.example.data.SecureBrokerSessionManager.captureAndPersistFromWebView(
            context = context,
            synchronous = true,
            cookieReader = { url ->
                when (url) {
                    "https://api.iqoption.com" -> "ws_ssid=api_token_abc123; cf_bm=ignore"
                    "https://eu.iqoption.com" -> "iq_auth=eu_token_xyz"
                    else -> null
                }
            }
        )
        assertTrue(captured)
        assertEquals("api_token_abc123", com.example.data.SecureBrokerSessionManager.getAuthToken(context))

        // 4. Updating email with blank password preserves existing encrypted password
        assertTrue(
            com.example.data.SecureBrokerSessionManager.saveAutoLoginCredentials(
                context = context,
                email = "first@example.com",
                password = "SyntheticKeepPass!99",
                enabled = true,
                synchronous = true
            )
        )
        assertTrue(
            com.example.data.SecureBrokerSessionManager.saveAutoLoginCredentials(
                context = context,
                email = "updated@example.com",
                password = "",
                enabled = true,
                synchronous = true
            )
        )
        assertEquals("updated@example.com", com.example.data.SecureBrokerSessionManager.getSavedAutoLoginEmail(context))
        assertEquals(
            "SyntheticKeepPass!99",
            com.example.data.SecureBrokerSessionManager.getAutoLoginCredentials(context)?.password
        )

        // 5. clearSession wipes localStorage snapshot as well
        com.example.data.SecureBrokerSessionManager.clearSession(context, clearWebViewCookies = false, clearAutoLoginVault = true)
        assertEquals(null, com.example.data.SecureBrokerSessionManager.getWebLocalStorageSnapshot(context))
    }

    @Test
    fun `auto-login and login-capture scripts support shadow DOM, remember-me auto-tick, and eu subdomain`() {
        assertTrue(com.example.data.SecureBrokerSessionManager.isAllowlistedAutoLoginUrl("https://eu.iqoption.com/pwa/traderoom"))
        val autoJs = com.example.ui.components.buildAutoLoginScript("user@example.com", "Pass!123")
        assertTrue(autoJs.contains("shadowRoot"))
        assertTrue(autoJs.contains("lembrar"))
        assertTrue(autoJs.contains("remember"))
        assertTrue(autoJs.contains("eu\\."))

        val captureJs = com.example.ui.components.BROKER_LOGIN_CAPTURE_AND_REMEMBER_JS
        assertTrue(captureJs.contains("window.location.protocol !== 'https:'"))
        assertTrue(captureJs.contains("TrendLensVaultBridge.onBrokerLoginSubmitted"))
        assertTrue(captureJs.contains("shadowRoot"))
        assertTrue(captureJs.contains("lembrar"))
    }

    @Test
    fun `three-candle RSI strategy accepts genuine oversold reversal after drop and momentum-aligned trends without blocking`() {
        val aiRepo = FirebaseAiChartRepository()
        val strictConfig = BoConfig(
            sensitivity = BoSensitivity.CONSERVATIVE,
            strictThreeCandleRsiStrategy = true,
            waitForCandleClose = true
        )

        // 1. Oversold RSI reversal: 3 green candles + LEAVING_OVERSOLD_UP right after a drop (macroTrend=DOWN, REVERSAL_TRANSITION)
        val oversoldReversal = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"REVERSAL_TRANSITION","macroTrend":"DOWN","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":62,"confluenceCount":3,"entryQuality":"STRONG","riskFlag":"NONE","consecutiveSameDirCandles":3,"rsiTurnState":"LEAVING_OVERSOLD_UP","candleCloseConfirmed":true,"trendLabel":"Reversão de alta após queda","patternLabel":"3 velas verdes saindo da sobrevenda","supportResistanceLabel":"Suporte confirmado","rsiEstimated":34,"confidencePercent":80,"direction":"CALL_UP","summary":"3 velas verdes com RSI saindo da sobrevenda."}""",
            sourceDescription = "iqoption.com",
            boConfig = strictConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.CALL_UP, oversoldReversal.result.direction)
        assertEquals(3, oversoldReversal.result.strategyRulesMetCount)

        // 2. Momentum-aligned uptrend (e.g. RSI sub-panel hidden on chart, 3+ solid green candles)
        val momentumUp = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"HH_HL_UPTREND","macroTrend":"UP","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":74,"confluenceCount":4,"entryQuality":"STRONG","riskFlag":"NONE","consecutiveSameDirCandles":3,"rsiTurnState":"MOMENTUM_ALIGNED_UP","candleCloseConfirmed":true,"trendLabel":"Tendência de alta","patternLabel":"3 velas verdes consecutivas","supportResistanceLabel":"Espaço aberto","rsiEstimated":56,"confidencePercent":82,"direction":"CALL_UP","summary":"3 velas fortes de alta com momentum alinhado."}""",
            sourceDescription = "iqoption.com",
            boConfig = strictConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.CALL_UP, momentumUp.result.direction)
        assertEquals(3, momentumUp.result.strategyRulesMetCount)

        // 3. Momentum-aligned downtrend
        val momentumDown = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"LH_LL_DOWNTREND","macroTrend":"DOWN","microMomentum":"DOWN","candleDominance":"SELLERS_CONTROL","trendStrengthScore":72,"confluenceCount":4,"entryQuality":"STRONG","riskFlag":"NONE","consecutiveSameDirCandles":4,"rsiTurnState":"MOMENTUM_ALIGNED_DOWN","candleCloseConfirmed":true,"trendLabel":"Tendência de baixa","patternLabel":"4 velas vermelhas seguidas","supportResistanceLabel":"Espaço livre","rsiEstimated":42,"confidencePercent":81,"direction":"PUT_DOWN","summary":"4 velas fortes de baixa com momentum alinhado."}""",
            sourceDescription = "iqoption.com",
            boConfig = strictConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.PUT_DOWN, momentumDown.result.direction)
        assertEquals(3, momentumDown.result.strategyRulesMetCount)

        // 4. Flat/middle RSI and sideways market remain strictly blocked
        val flatBlocked = aiRepo.parseGeminiOutcome(
            jsonText = """{"isValidChart":true,"marketStructure":"HH_HL_UPTREND","macroTrend":"UP","microMomentum":"UP","candleDominance":"BUYERS_CONTROL","trendStrengthScore":75,"confluenceCount":4,"entryQuality":"STRONG","riskFlag":"NONE","consecutiveSameDirCandles":3,"rsiTurnState":"FLAT_OR_MIDDLE","candleCloseConfirmed":true,"trendLabel":"Tendência de alta","patternLabel":"3 velas","supportResistanceLabel":"Livre","rsiEstimated":50,"confidencePercent":80,"direction":"CALL_UP","summary":"RSI neutro."}""",
            sourceDescription = "iqoption.com",
            boConfig = strictConfig
        ) as ChartAnalysisOutcome.ValidChart
        assertEquals(SignalDirection.NEUTRAL, flatBlocked.result.direction)
    }

    private fun createCandlestickChartBitmap(bgColor: Int, uptrend: Boolean): Bitmap {
        val bmp = createSolidBitmap(480, 320, bgColor)
        val green = Color.rgb(38, 175, 115)
        val red = Color.rgb(235, 77, 75)

        val candleCount = 18
        for (i in 0 until candleCount) {
            val x = 36 + i * 23
            val progress = i.toFloat() / (candleCount - 1)
            val baseY = if (uptrend) {
                245f - progress * 165f + if (i % 4 == 1) 12f else 0f
            } else {
                75f + progress * 165f - if (i % 4 == 1) 12f else 0f
            }
            val isBull = if (uptrend) (i % 4 != 1) else (i % 4 == 1)
            val color = if (isBull) green else red
            val bodyH = 22 + (i % 3) * 8
            val top = (baseY.toInt() - bodyH / 2).coerceIn(20, 270)
            val bottom = (top + bodyH).coerceIn(top + 10, 295)

            fillRect(bmp, x - 1, top - 10, x + 1, bottom + 10, color)
            fillRect(bmp, x - 6, top, x + 6, bottom, color)
        }
        return bmp
    }

    private fun createSolidBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    private fun fillRect(bmp: Bitmap, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        val l = left.coerceIn(0, bmp.width)
        val r = right.coerceIn(0, bmp.width)
        val t = top.coerceIn(0, bmp.height)
        val b = bottom.coerceIn(0, bmp.height)
        for (y in t until b) {
            for (x in l until r) {
                bmp.setPixel(x, y, color)
            }
        }
    }
}
