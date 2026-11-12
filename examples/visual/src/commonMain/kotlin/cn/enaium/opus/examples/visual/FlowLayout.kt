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

/**
 * A wrapping horizontal row of controls.
 *
 * Each helper measures the widget (plus its label) before drawing it and calls
 * `sameLine` only while the rest of the current row is wide enough. A control
 * that does not fit is left where ImGui would normally put it, on the next
 * line, so the toolbar flows across a phone in landscape and a desktop window
 * without a hand-tuned layout for either.
 */
internal object FlowLayout {

    private const val ITEM_GAP = 10f
    private const val LABEL_GAP = 6f
    private const val BUTTON_PADDING = 8f

    private var rowWidth = 0f
    private var rowAvailable = 0f

    private val scale: Float get() = UiMetrics.scale

    /** Starts a new flow row at the current cursor position. */
    fun begin() {
        rowWidth = 0f
        // Capture the full row width once: GetContentRegionAvail shrinks as
        // sameLine() advances the cursor, and comparing against it would wrap
        // far earlier than necessary.
        rowAvailable = ImGui.getContentRegionAvail().x
    }

    fun combo(
        label: String,
        current: IntArray,
        items: Array<String>,
        itemWidth: Float,
    ): Boolean {
        reserve(itemWidth * scale + LABEL_GAP * scale + labelWidth(label))
        ImGui.setNextItemWidth(itemWidth * scale)
        return ImGui.combo(label, current, items)
    }

    fun sliderInt(
        label: String,
        values: IntArray,
        min: Int,
        max: Int,
        format: String,
        itemWidth: Float,
    ): Boolean {
        reserve(itemWidth * scale + LABEL_GAP * scale + labelWidth(label))
        ImGui.setNextItemWidth(itemWidth * scale)
        return ImGui.sliderInt(label, values, min, max, format)
    }

    fun sliderFloat(
        label: String,
        values: FloatArray,
        min: Float,
        max: Float,
        format: String,
        itemWidth: Float,
    ): Boolean {
        reserve(itemWidth * scale + LABEL_GAP * scale + labelWidth(label))
        ImGui.setNextItemWidth(itemWidth * scale)
        return ImGui.sliderFloat(label, values, min, max, format)
    }

    fun checkbox(label: String, values: BooleanArray): Boolean {
        reserve(ImGui.getFrameHeight() + LABEL_GAP * scale + labelWidth(label))
        return ImGui.checkbox(label, values)
    }

    fun button(label: String): Boolean {
        reserve(labelWidth(label) + BUTTON_PADDING * 2f * scale)
        return ImGui.button(label)
    }

    private fun reserve(width: Float) {
        if (rowWidth <= 0f) {
            rowWidth = width
            return
        }
        val needed = rowWidth + ITEM_GAP * scale + width
        if (needed > rowAvailable) {
            // Do not call sameLine: ImGui advances to the next line itself.
            rowWidth = width
            return
        }
        ImGui.sameLine(0f, ITEM_GAP * scale)
        rowWidth = needed
    }

    private fun labelWidth(label: String): Float =
        ImGui.calcTextSize(label.substringBefore("##")).x
}
