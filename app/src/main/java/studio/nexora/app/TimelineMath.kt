package studio.nexora.app
fun safeTrimStart(value: Float, end: Float): Float = value.coerceIn(0f, (end - 1f).coerceAtLeast(0f))
fun safeTrimEnd(value: Float, start: Float): Float = value.coerceIn((start + 1f).coerceAtMost(100f), 100f)
fun safeSpeed(value: Float): Float = value.coerceIn(0.25f, 3f)
