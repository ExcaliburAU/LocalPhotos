package dev.exau.photos.edit

data class DrawStroke(
    val points: List<Pair<Float, Float>>,
    val color: Long = 0xFFFF3B30,
    val width: Float = 0.012f,
)

data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width: Float get() = (right - left).coerceAtLeast(0.05f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.05f)

    fun coerced(): CropRect {
        val l = left.coerceIn(0f, 0.95f)
        val t = top.coerceIn(0f, 0.95f)
        val r = right.coerceIn(l + 0.05f, 1f)
        val b = bottom.coerceIn(t + 0.05f, 1f)
        return CropRect(l, t, r, b)
    }
}

enum class PhotoFilter(val label: String) {
    None("Original"),
    Mono("Mono"),
    Warm("Warm"),
    Cool("Cool"),
    Fade("Fade"),
    Punch("Punch"),
}

data class EditState(
    val rotationDegrees: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val filter: PhotoFilter = PhotoFilter.None,
    val crop: CropRect = CropRect(),
    val strokes: List<DrawStroke> = emptyList(),
    val maxSide: Int = 0,
) {
    val hasCrop: Boolean
        get() = crop.left > 0.001f || crop.top > 0.001f || crop.right < 0.999f || crop.bottom < 0.999f

    fun rotateCw(): EditState = copy(rotationDegrees = (rotationDegrees + 90) % 360, crop = CropRect())
    fun rotateCcw(): EditState = copy(rotationDegrees = (rotationDegrees + 270) % 360, crop = CropRect())

    fun colorMatrixValues(): FloatArray {
        val result = android.graphics.ColorMatrix()
        result.postConcat(filterMatrix(filter))

        val sat = android.graphics.ColorMatrix()
        sat.setSaturation((1f + saturation).coerceIn(0f, 2f))
        result.postConcat(sat)

        val c = (1f + contrast).coerceIn(0.2f, 2.2f)
        val t = 128f * (1f - c)
        result.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )

        val b = brightness * 72f
        val w = warmth * 48f
        result.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, b + w,
                    0f, 1f, 0f, 0f, b,
                    0f, 0f, 1f, 0f, b - w,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        return result.array
    }

    companion object {
        fun auto(): EditState = EditState(
            brightness = 0.06f,
            contrast = 0.12f,
            saturation = 0.08f,
            warmth = 0.05f,
        )

        private fun filterMatrix(filter: PhotoFilter): android.graphics.ColorMatrix {
            val m = android.graphics.ColorMatrix()
            when (filter) {
                PhotoFilter.None -> {}
                PhotoFilter.Mono -> m.setSaturation(0f)
                PhotoFilter.Warm -> m.postConcat(
                    android.graphics.ColorMatrix(
                        floatArrayOf(
                            1.12f, 0f, 0f, 0f, 12f,
                            0f, 1.02f, 0f, 0f, 4f,
                            0f, 0f, 0.88f, 0f, -8f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
                PhotoFilter.Cool -> m.postConcat(
                    android.graphics.ColorMatrix(
                        floatArrayOf(
                            0.90f, 0f, 0f, 0f, -6f,
                            0f, 1.02f, 0f, 0f, 4f,
                            0f, 0f, 1.14f, 0f, 10f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
                PhotoFilter.Fade -> {
                    m.setSaturation(0.72f)
                    m.postConcat(
                        android.graphics.ColorMatrix(
                            floatArrayOf(
                                0.82f, 0f, 0f, 0f, 28f,
                                0f, 0.82f, 0f, 0f, 28f,
                                0f, 0f, 0.82f, 0f, 28f,
                                0f, 0f, 0f, 1f, 0f,
                            ),
                        ),
                    )
                }
                PhotoFilter.Punch -> {
                    m.setSaturation(1.28f)
                    m.postConcat(
                        android.graphics.ColorMatrix(
                            floatArrayOf(
                                1.18f, 0f, 0f, 0f, -12f,
                                0f, 1.18f, 0f, 0f, -12f,
                                0f, 0f, 1.18f, 0f, -12f,
                                0f, 0f, 0f, 1f, 0f,
                            ),
                        ),
                    )
                }
            }
            return m
        }
    }
}
