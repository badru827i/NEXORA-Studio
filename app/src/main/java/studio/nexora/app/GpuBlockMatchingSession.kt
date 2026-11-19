package studio.nexora.app

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLES31
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class GpuMatch(val x: Int, val y: Int, val confidence: Float)

/**
 * Optional GLES 3.1 compute backend for coarse template matching.
 * It has no third-party dependencies. Creation fails closed on unsupported drivers;
 * callers must retain their CPU fallback.
 */
class GpuBlockMatchingSession private constructor() : AutoCloseable {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var program = 0
    private val buffers = IntArray(3)
    var successfulDispatches: Int = 0
        private set
    private var usable = true

    fun disable() { usable = false }

    init {
        initializeEgl()
        program = buildProgram(SHADER)
        GLES31.glGenBuffers(3, buffers, 0)
        check(buffers.all { it != 0 }) { "Unable to allocate GPU tracking buffers." }
    }

    fun findBestMatch(
        template: FloatArray,
        templateWidth: Int,
        templateHeight: Int,
        frame: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        oldLeft: Int,
        oldTop: Int
    ): GpuMatch? {
        if (!usable) return null
        checkCurrent()
        val radiusX = max(templateWidth, (frameWidth * 0.18f).toInt())
        val radiusY = max(templateHeight, (frameHeight * 0.18f).toInt())
        val step = max(2, min(templateWidth, templateHeight) / 8)
        val minX = (oldLeft - radiusX).coerceAtLeast(0)
        val maxX = (oldLeft + radiusX).coerceAtMost(frameWidth - templateWidth)
        val minY = (oldTop - radiusY).coerceAtLeast(0)
        val maxY = (oldTop + radiusY).coerceAtMost(frameHeight - templateHeight)
        if (maxX < minX || maxY < minY) return null
        val gridWidth = (maxX - minX) / step + 1
        val gridHeight = (maxY - minY) / step + 1
        val count = gridWidth * gridHeight

        val templateInts = IntArray(template.size) { template[it].toInt().coerceIn(0, 255) }
        val frameInts = IntArray(frame.size) { frame[it].toInt() and 0xff }
        val output = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())

        upload(buffers[0], templateInts)
        upload(buffers[1], frameInts)
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, buffers[2])
        GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, count * 4, output, GLES31.GL_DYNAMIC_READ)
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, buffers[0])
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, buffers[1])
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, buffers[2])

        GLES31.glUseProgram(program)
        uniform("uTemplateWidth", templateWidth)
        uniform("uTemplateHeight", templateHeight)
        uniform("uFrameWidth", frameWidth)
        uniform("uFrameHeight", frameHeight)
        uniform("uMinX", minX)
        uniform("uMinY", minY)
        uniform("uStep", step)
        uniform("uGridWidth", gridWidth)
        uniform("uGridHeight", gridHeight)
        uniform("uSampleStep", max(1, min(templateWidth, templateHeight) / 24))
        GLES31.glDispatchCompute((gridWidth + 7) / 8, (gridHeight + 7) / 8, 1)
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)
        check(GLES31.glGetError() == GLES31.GL_NO_ERROR) { "GPU tracking dispatch failed." }

        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, buffers[2])
        val mapped = GLES30.glMapBufferRange(
            GLES31.GL_SHADER_STORAGE_BUFFER, 0L, count * 4L, GLES30.GL_MAP_READ_BIT
        ) as? ByteBuffer ?: return null
        mapped.order(ByteOrder.nativeOrder())
        var bestIndex = -1
        var bestScore = -1f
        for (index in 0 until count) {
            val score = mapped.getFloat(index * 4)
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }
        GLES30.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER)
        if (bestIndex < 0 || !bestScore.isFinite()) return null
        successfulDispatches++
        return GpuMatch(
            minX + (bestIndex % gridWidth) * step,
            minY + (bestIndex / gridWidth) * step,
            bestScore.coerceIn(-1f, 1f)
        )
    }

    private fun upload(buffer: Int, values: IntArray) {
        val bytes = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
        val ints: IntBuffer = bytes.asIntBuffer()
        ints.put(values)
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, buffer)
        GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, values.size * 4, bytes, GLES31.GL_DYNAMIC_DRAW)
    }

    private fun uniform(name: String, value: Int) {
        val location = GLES31.glGetUniformLocation(program, name)
        if (location >= 0) GLES31.glUniform1i(location, value)
    }

    private fun initializeEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display." }
        val versions = IntArray(2)
        check(EGL14.eglInitialize(display, versions, 0, versions, 1)) { "EGL initialization failed." }
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "No GLES 3 EGL config."
        }
        val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "Unable to create GLES 3 context." }
        val surfaceAttributes = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttributes, 0)
        check(surface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL pbuffer." }
        checkCurrent()
        val version = GLES31.glGetString(GLES31.GL_VERSION).orEmpty()
        check(version.contains("OpenGL ES 3.1") || version.contains("OpenGL ES 3.2")) {
            "GLES 3.1 compute shaders are unavailable."
        }
    }

    private fun checkCurrent() {
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "Unable to activate GPU context." }
    }

    override fun close() {
        runCatching {
            if (display != EGL14.EGL_NO_DISPLAY && context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(display, surface, surface, context)
                if (program != 0) GLES31.glDeleteProgram(program)
                GLES31.glDeleteBuffers(buffers.size, buffers, 0)
            }
            if (display != EGL14.EGL_NO_DISPLAY) {
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
        }
    }

    private fun buildProgram(source: String): Int {
        val shader = GLES31.glCreateShader(GLES31.GL_COMPUTE_SHADER)
        GLES31.glShaderSource(shader, source)
        GLES31.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, compiled, 0)
        check(compiled[0] == GLES31.GL_TRUE) {
            "GPU tracking shader compile failed: " + GLES31.glGetShaderInfoLog(shader)
        }
        val result = GLES31.glCreateProgram()
        GLES31.glAttachShader(result, shader)
        GLES31.glLinkProgram(result)
        val linked = IntArray(1)
        GLES31.glGetProgramiv(result, GLES31.GL_LINK_STATUS, linked, 0)
        GLES31.glDeleteShader(shader)
        check(linked[0] == GLES31.GL_TRUE) {
            "GPU tracking shader link failed: " + GLES31.glGetProgramInfoLog(result)
        }
        return result
    }

    companion object {
        private const val EGL_OPENGL_ES3_BIT_KHR = 0x0040
        private val SHADER = """
            #version 310 es
            layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;
            layout(std430, binding = 0) readonly buffer TemplateData { uint a[]; };
            layout(std430, binding = 1) readonly buffer FrameData { uint b[]; };
            layout(std430, binding = 2) writeonly buffer ScoreData { float scores[]; };
            uniform int uTemplateWidth;
            uniform int uTemplateHeight;
            uniform int uFrameWidth;
            uniform int uFrameHeight;
            uniform int uMinX;
            uniform int uMinY;
            uniform int uStep;
            uniform int uGridWidth;
            uniform int uGridHeight;
            uniform int uSampleStep;
            void main() {
                uint gx = gl_GlobalInvocationID.x;
                uint gy = gl_GlobalInvocationID.y;
                if (gx >= uint(uGridWidth) || gy >= uint(uGridHeight)) return;
                int left = uMinX + int(gx) * uStep;
                int top = uMinY + int(gy) * uStep;
                float sumA = 0.0, sumAA = 0.0, sumB = 0.0, sumBB = 0.0, sumAB = 0.0;
                int samples = 0;
                for (int y = 0; y < uTemplateHeight; y += uSampleStep) {
                    for (int x = 0; x < uTemplateWidth; x += uSampleStep) {
                        float av = float(a[y * uTemplateWidth + x]);
                        float bv = float(b[(top + y) * uFrameWidth + left + x]);
                        sumA += av; sumAA += av * av;
                        sumB += bv; sumBB += bv * bv;
                        sumAB += av * bv;
                        samples++;
                    }
                }
                float n = float(max(samples, 1));
                float covariance = sumAB - sumA * sumB / n;
                float energyA = max(0.0, sumAA - sumA * sumA / n);
                float energyB = max(0.0, sumBB - sumB * sumB / n);
                float denominator = sqrt(energyA * energyB);
                float score = denominator > 0.0001 ? covariance / denominator : -1.0;
                scores[gy * uint(uGridWidth) + gx] = clamp(score, -1.0, 1.0);
            }
        """.trimIndent()

        fun createOrNull(): GpuBlockMatchingSession? = runCatching {
            GpuBlockMatchingSession()
        }.getOrNull()
    }
}
