package com.github.numq.vad.silero.model

internal interface SileroOnnxModel : AutoCloseable {
    fun process(input: Array<FloatArray>): Result<FloatArray>
    fun reset(batchSize: Int = 0): Result<Unit>
}