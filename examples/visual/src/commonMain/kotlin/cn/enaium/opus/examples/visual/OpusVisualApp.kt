/*
 * Copyright (c) 2026 Enaium
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cn.enaium.opus.examples.visual

import cn.enaium.imgui.ImGui
import cn.enaium.imgui.ImGuiCond
import cn.enaium.imgui.ImGuiWindowFlags
import cn.enaium.imgui.ImVec2
import cn.enaium.opus.Opus
import cn.enaium.opus.OpusApplication
import cn.enaium.opus.OpusDecoder
import cn.enaium.opus.OpusEncoder
import cn.enaium.opus.OpusSignal

/**
 * The visual example: an Opus encoder/decoder pair driven by a synthetic
 * speech-like signal, with the input, the decoded output and the packet size
 * history plotted through ImPlot.
 */
internal class OpusVisualApp(
    private var muted: Boolean = false,
    initialChannels: Int = 1,
    initialMicrophone: Boolean = false,
) {

    private val applications = OpusApplication.entries
    private val durationsMs = doubleArrayOf(2.5, 5.0, 10.0, 20.0, 40.0, 60.0)

    private var applicationIndex = 1
    private var durationIndex = 3
    private var channelIndex = (initialChannels - 1).coerceIn(0, 1)
    private var sourceIndex = if (initialMicrophone) SOURCE_MICROPHONE else SOURCE_SYNTHETIC
    private var bitrate = 32_000
    private var complexity = 5
    private var vbr = true
    private var fec = false
    private var dtx = false
    private var tone = 440f
    private var amplitude = 0.30f
    private var running = true

    private var encoder: OpusEncoder? = null
    private var decoder: OpusDecoder? = null
    private var frameSize = 960
    private val microphone = MicrophoneSource()
    private var microphoneError: String? = null

    private var leftSource = Signal(SAMPLE_RATE, 440.0)
    private var rightSource = Signal(SAMPLE_RATE, 660.0, seed = 0x51EE)

    private val inputScope = SignalScope(SAMPLE_RATE)
    private val outputScope = SignalScope(SAMPLE_RATE)
    private val packetHistory = HistoryRing(240)
    private val inputPlot = FloatArray(SAMPLE_RATE)
    private val outputPlot = FloatArray(SAMPLE_RATE)
    private val packetPlot = FloatArray(240)

    private val inputSeries = PlotSeries("input", Colors.INPUT)
    private val outputSeries = PlotSeries("decoded", Colors.OUTPUT)
    private val packetSeries = PlotSeries("packet bytes", Colors.PACKET)

    private var frames = 0L
    private var packets = 0L
    private var dtxDrops = 0L
    private var totalPacketBytes = 0L
    private var lastPacketBytes = 0

    init {
        recreate()
    }

    private val channels: Int get() = channelIndex + 1
    private val durationMs: Double get() = durationsMs[durationIndex]

    fun draw() {
        if (running) pump()

        ImGui.setNextWindowPos(ImVec2(0f, 0f), ImGuiCond.ALWAYS)
        ImGui.setNextWindowSize(ImGui.getIO().displaySize, ImGuiCond.ALWAYS)
        if (ImGui.begin("opus-kmp visual example", null, WINDOW_FLAGS)) {
            ImGui.text(
                "Opus ${Opus.version} | ${SAMPLE_RATE} Hz | ${channels} channel(s) | " +
                    "${fixed(durationMs, 1)} ms frames (${frameSize} samples)",
            )
            ImGui.separator()

            FlowLayout.begin()

            val sourceChoice = intArrayOf(sourceIndex)
            if (FlowLayout.combo("source", sourceChoice, SOURCE_LABELS, 220f)) {
                sourceIndex = sourceChoice[0]
                applySource()
            }

            val applicationChoice = intArrayOf(applicationIndex)
            if (FlowLayout.combo("application", applicationChoice, APPLICATION_LABELS, 220f)) {
                applicationIndex = applicationChoice[0]
                recreate()
            }

            val durationChoice = intArrayOf(durationIndex)
            if (FlowLayout.combo("frame duration", durationChoice, DURATION_LABELS, 220f)) {
                durationIndex = durationChoice[0]
                recreate()
            }

            val channelChoice = intArrayOf(channelIndex)
            if (FlowLayout.combo("channels", channelChoice, CHANNEL_LABELS, 220f)) {
                channelIndex = channelChoice[0]
                recreate()
            }

            val bitrateValue = intArrayOf(bitrate)
            if (FlowLayout.sliderInt("bitrate (bit/s)", bitrateValue, 6_000, 510_000, "%d", 320f)) {
                bitrate = bitrateValue[0]
                encoder?.bitrate = bitrate
            }

            val complexityValue = intArrayOf(complexity)
            if (FlowLayout.sliderInt("complexity", complexityValue, 0, 10, "%d", 320f)) {
                complexity = complexityValue[0]
                encoder?.complexity = complexity
            }

            val toneValue = floatArrayOf(tone)
            if (FlowLayout.sliderFloat("tone (Hz)", toneValue, 100f, 2_000f, "%.0f", 320f)) {
                tone = toneValue[0]
                leftSource = Signal(SAMPLE_RATE, tone.toDouble())
                rightSource = Signal(SAMPLE_RATE, tone * 1.5, seed = 0x51EE)
            }

            val amplitudeValue = floatArrayOf(amplitude)
            if (FlowLayout.sliderFloat("amplitude", amplitudeValue, 0.02f, 0.95f, "%.2f", 320f)) {
                amplitude = amplitudeValue[0]
            }

            val vbrValue = booleanArrayOf(vbr)
            if (FlowLayout.checkbox("VBR", vbrValue)) {
                vbr = vbrValue[0]
                encoder?.vbr = vbr
            }
            val fecValue = booleanArrayOf(fec)
            if (FlowLayout.checkbox("in-band FEC", fecValue)) {
                fec = fecValue[0]
                encoder?.inbandFec = fec
                encoder?.packetLossPercent = if (fec) 10 else 0
            }
            val dtxValue = booleanArrayOf(dtx)
            if (FlowLayout.checkbox("DTX", dtxValue)) {
                dtx = dtxValue[0]
                encoder?.dtx = dtx
            }
            val muteValue = booleanArrayOf(muted)
            if (FlowLayout.checkbox("mute input", muteValue)) {
                muted = muteValue[0]
            }
            if (FlowLayout.button(if (running) "Pause" else "Resume")) {
                running = !running
            }
            if (FlowLayout.button("Reset")) {
                recreate()
            }

            if (sourceIndex == SOURCE_MICROPHONE) {
                val state = microphoneError
                    ?: "microphone: ${microphone.deviceName ?: "opening..."} " +
                    "(backlog ${microphone.backlogFrames()} frames)"
                ImGui.text(state)
            }

            ImGui.separator()
            val seconds = frames * durationMs / 1000.0
            val averageKbit = if (seconds > 0) totalPacketBytes * 8.0 / seconds / 1000.0 else 0.0
            ImGui.text(
                "frames=$frames packets=$packets DTX drops=$dtxDrops | last packet $lastPacketBytes bytes | " +
                    "average ${fixed(averageKbit, 1)} kbit/s",
            )
            ImGui.text(
                "bandwidth=${encoder?.bandwidth} lookahead=${encoder?.lookahead} " +
                    "final range=${encoder?.finalRange} decoder gain=${decoder?.gain}",
            )

            val height = maxOf((ImGui.getContentRegionAvail().y - 12f) / 2f, 100f * UiMetrics.scale)
            val inputPeak = inputScope.snapshot(inputPlot)
            val outputPeak = outputScope.snapshot(outputPlot)
            val scale = maxOf(inputPeak, outputPeak, 0.02f) * 1.15f

            inputSeries.values = inputPlot
            outputSeries.values = outputPlot
            inputSeries.xScale = 1000.0 / SAMPLE_RATE
            outputSeries.xScale = 1000.0 / SAMPLE_RATE

            val packetCount = packetHistory.copyTo(packetPlot)
            packetSeries.values = packetPlot
            packetSeries.length = packetCount
            packetSeries.xScale = durationMs

            Chart.plot(
                "waveform: input vs decoded (left channel)",
                listOf(inputSeries, outputSeries),
                height,
                1000.0,
                -scale.toDouble(),
                scale.toDouble(),
            )

            var maxPacket = 16f
            for (index in 0 until packetCount) if (packetPlot[index] > maxPacket) maxPacket = packetPlot[index]
            Chart.plot(
                "packet size",
                listOf(packetSeries),
                height,
                240 * durationMs,
                0.0,
                maxPacket * 1.2,
                yLabel = "bytes",
            )
        }
        ImGui.end()
    }

    fun printSummary() {
        val seconds = frames * durationMs / 1000.0
        val averageKbit = if (seconds > 0) totalPacketBytes * 8.0 / seconds / 1000.0 else 0.0
        report(
            "opus: version=${Opus.version}, source=${SOURCE_LABELS[sourceIndex]}, " +
                "application=${applications[applicationIndex]}, channels=$channels, " +
                "frame=${fixed(durationMs, 1)} ms, bitrate=$bitrate, complexity=$complexity, " +
                "vbr=$vbr, fec=$fec, dtx=$dtx, frames=$frames, packets=$packets, " +
                "DTX drops=$dtxDrops, last=$lastPacketBytes bytes, average=${fixed(averageKbit, 1)} kbit/s" +
                (microphoneError?.let { ", microphone error: $it" } ?: ""),
        )
    }

    fun close() {
        microphone.close()
        encoder?.close()
        decoder?.close()
        encoder = null
        decoder = null
    }

    private fun pump() {
        when (sourceIndex) {
            SOURCE_MICROPHONE -> pumpMicrophone()
            else -> pumpSynthetic()
        }
    }

    private fun pumpSynthetic() {
        val left = leftSource.nextPcm(frameSize, amplitude, muted = muted)
        val frame = if (channels == 1) {
            left
        } else {
            val right = rightSource.nextPcm(frameSize, amplitude, muted = muted)
            ShortArray(frameSize * 2) { index ->
                if (index % 2 == 0) left[index / 2] else right[index / 2]
            }
        }
        processFrame(frame)
    }

    private fun pumpMicrophone() {
        for (mono in microphone.poll(frameSize)) {
            val frame = if (channels == 1) {
                mono
            } else {
                // Capture is mono; feed the same signal to both Opus channels.
                ShortArray(frameSize * 2) { index -> mono[index / 2] }
            }
            if (muted) frame.fill(0)
            processFrame(frame)
        }
    }

    private fun processFrame(frame: ShortArray) {
        val encoder = encoder ?: return
        val decoder = decoder ?: return

        inputScope.appendChannel(frame, frameSize, 0, channels)
        val packet = encoder.encode(frame)
        frames++
        if (packet.isNotEmpty()) {
            packets++
            totalPacketBytes += packet.size
            lastPacketBytes = packet.size
            packetHistory.append(packet.size.toFloat())
            val decoded = decoder.decode(packet)
            if (decoded.size >= frameSize * channels) {
                outputScope.appendChannel(decoded, frameSize, 0, channels)
            } else {
                outputScope.append(FloatArray(frameSize), frameSize)
            }
        } else {
            dtxDrops++
            lastPacketBytes = 0
            packetHistory.append(0f)
            outputScope.append(FloatArray(frameSize), frameSize)
        }
    }

    /** Opens or closes the capture stream when the source combo changes. */
    private fun applySource() {
        if (sourceIndex != SOURCE_MICROPHONE) {
            microphone.close()
            microphoneError = null
            return
        }
        if (microphone.open(SAMPLE_RATE, frameSize)) {
            microphoneError = null
            report("microphone source: ${microphone.deviceName}")
        } else {
            microphoneError = microphone.error ?: "microphone unavailable"
            report("microphone unavailable: $microphoneError")
            sourceIndex = SOURCE_SYNTHETIC
        }
    }

    private fun recreate() {
        encoder?.close()
        decoder?.close()
        microphone.close()

        val selectedApplication = applications[applicationIndex]
        val selectedBitrate = bitrate
        val selectedComplexity = complexity
        val selectedVbr = vbr
        val selectedFec = fec
        val selectedDtx = dtx

        encoder = OpusEncoder.open(SAMPLE_RATE, channels, selectedApplication).apply {
            bitrate = selectedBitrate
            complexity = selectedComplexity
            vbr = selectedVbr
            inbandFec = selectedFec
            packetLossPercent = if (selectedFec) 10 else 0
            dtx = selectedDtx
            signal = OpusSignal.MUSIC
        }
        decoder = OpusDecoder.open(SAMPLE_RATE, channels)

        frameSize = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        leftSource = Signal(SAMPLE_RATE, tone.toDouble())
        rightSource = Signal(SAMPLE_RATE, tone * 1.5, seed = 0x51EE)
        inputScope.clear()
        outputScope.clear()
        packetHistory.clear()
        frames = 0
        packets = 0
        dtxDrops = 0
        totalPacketBytes = 0
        lastPacketBytes = 0
        if (sourceIndex == SOURCE_MICROPHONE) applySource()
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val SOURCE_SYNTHETIC = 0
        const val SOURCE_MICROPHONE = 1
        val SOURCE_LABELS = arrayOf("synthetic", "microphone")
        val APPLICATION_LABELS = OpusApplication.entries.map { it.name.lowercase() }.toTypedArray()
        val DURATION_LABELS = arrayOf("2.5 ms", "5 ms", "10 ms", "20 ms", "40 ms", "60 ms")
        val CHANNEL_LABELS = arrayOf("mono", "stereo")
        const val WINDOW_FLAGS = ImGuiWindowFlags.NO_TITLE_BAR or
            ImGuiWindowFlags.NO_RESIZE or
            ImGuiWindowFlags.NO_MOVE or
            ImGuiWindowFlags.NO_SCROLLBAR or
            ImGuiWindowFlags.NO_COLLAPSE or
            ImGuiWindowFlags.NO_SAVED_SETTINGS or
            ImGuiWindowFlags.NO_BRING_TO_FRONT_ON_FOCUS or
            ImGuiWindowFlags.NO_NAV_FOCUS
    }
}

/**
 * Runs the visual example until the window closes or [frames] frames were
 * drawn. Returns `false` when no window could be opened.
 */
fun runVisualExample(
    frames: Int = Int.MAX_VALUE,
    mute: Boolean = false,
    channels: Int = 1,
    microphone: Boolean = false,
): Boolean {
    report(
        "opus-kmp visual example (frames=$frames, mute=$mute, channels=$channels, " +
            "source=${if (microphone) "microphone" else "synthetic"})",
    )
    val app = OpusVisualApp(mute, channels, microphone)
    var closed = false
    val displayed = ImGuiSdlApp.run(
        "opus-kmp visual example",
        frames,
        onClose = {
            app.printSummary()
            app.close()
            closed = true
        },
    ) { app.draw() }
    // No window was opened, so the normal close hook did not run.
    if (!closed) {
        app.printSummary()
        app.close()
    }
    report(if (displayed) "done" else "aborted")
    return displayed
}
