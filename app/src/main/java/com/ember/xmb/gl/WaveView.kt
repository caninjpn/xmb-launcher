package com.ember.xmb.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

/**
 * Full-screen background.
 *
 * Layering note — this is a SurfaceView, so its surface is composited BELOW the app
 * window. The activity theme must keep a transparent window background or none of this
 * is visible. See res/values/themes.xml.
 */
class WaveView(context: Context) : GLSurfaceView(context) {

    val renderer = WaveRenderer()

    private val pumpHandler = Handler(Looper.getMainLooper())
    private val pump = object : Runnable {
        override fun run() {
            requestRender()
            // Read each tick: the renderer slows this down once it has seen the GPU.
            pumpHandler.postDelayed(this, renderer.frameIntervalMs)
        }
    }

    init {
        setEGLContextClientVersion(2)
        // RGB888, no alpha/depth/stencil: 565 would band visibly across these gradients.
        setEGLConfigChooser(8, 8, 8, 0, 0, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onResume() {
        super.onResume()
        pumpHandler.removeCallbacks(pump)
        pumpHandler.post(pump)
    }

    override fun onPause() {
        pumpHandler.removeCallbacks(pump)
        super.onPause()
    }
}

/**
 * Draws the background at full native resolution in four passes: one full-screen quad
 * for the gradient, then one additive strip per ribbon band.
 *
 * There is deliberately no offscreen buffer here. An earlier version rendered everything
 * into a half- or quarter-size FBO and stretched it back, because the fragment shader did
 * six HSL conversions and nine sines per pixel and that was unaffordable at native size.
 * Both of those turned out to be the wrong place for the work — the conversions are
 * constant for the whole frame and the sines are constant down each column — so they
 * moved to the CPU and the vertex shader respectively. See [Shaders] for the reasoning.
 *
 * What that buys: full resolution instead of 0.28–0.5x, edges that are no longer
 * quantised by fp16 cancellation, a dither that actually survives to the framebuffer,
 * and the offscreen texture given back rather than merely shrunk — 289KB at 0.28x on a
 * 720p panel, around 2MB at 0.5x on a 1080p one.
 *
 * Measured on the Mali-450 box (GLES 2.0 only, 720p), which is the weakest target:
 *
 * | Version                        | Resolution | GPU per frame |
 * |--------------------------------|-----------:|--------------:|
 * | one pass, per-pixel everything |   1280x720 |         200ms |
 * | one pass into a 0.28x FBO      |    358x202 |          34ms |
 * | this one                       |   1280x720 |        11.7ms |
 *
 * So it is three times cheaper than the version that rendered an eighth of the pixels,
 * and seventeen times cheaper than the one that rendered the same pixels. Against the
 * 66ms pump that is 18% of the GPU, which is where it should stay: the headroom is there
 * to be left alone on a passively cooled box, not spent on frames nobody can see on a
 * ribbon this slow.
 */
class WaveRenderer : GLSurfaceView.Renderer {

    /** Written from the UI thread, read on the GL thread. */
    @Volatile var hue: Float = 30f
    @Volatile var shade: Float = 1f
    @Volatile var axisFraction: Float = 0.26f
    @Volatile var speed: Float = 1f

    /** Raised on weak GPUs once [onSurfaceCreated] has identified the renderer. */
    @Volatile var frameIntervalMs: Long = FRAME_MS_DEFAULT

    private var backdropProgram = 0
    private var aPosBackdrop = 0
    private var uBg0 = 0
    private var uBg1 = 0
    private var uTint = 0

    private var bandProgram = 0
    private var aPosBand = 0
    private var uRes = 0
    private var uAxis = 0
    private var uGeom = 0
    private var uPhase = 0
    private var uBody = 0
    private var uTopEdge = 0
    private var uBotEdge = 0
    private var uAlpha = 0

    private var viewWidth = 1
    private var viewHeight = 1
    private var startNanos = 0L

    private var lastFrameNanos = 0L
    private var framesInWindow = 0
    private var windowNanos = 0L
    private var worstFrameNanos = 0L
    private var timingWindowsLogged = 0

    /** The six frame-constant colours, as flat rgb triples. Rebuilt only on change. */
    private val colours = FloatArray(18)
    private var colourHue = Float.NaN
    private var colourShade = Float.NaN

    private val quad: FloatBuffer = floatBuffer(QUAD)

    /**
     * One static strip reused by all three bands: [SLICES] + 1 columns, two vertices
     * each, x normalised to 0..1 and y marking which side of the centreline. The band
     * parameters arrive as uniforms, so nothing here is ever re-uploaded.
     */
    private val strip: FloatBuffer = floatBuffer(
        FloatArray((SLICES + 1) * 4).also { v ->
            for (c in 0..SLICES) {
                val x = c.toFloat() / SLICES
                v[c * 4] = x; v[c * 4 + 1] = -1f
                v[c * 4 + 2] = x; v[c * 4 + 3] = 1f
            }
        }
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        startNanos = System.nanoTime()

        val rendererName = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
        val vendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty()
        val weak = LOW_END_GPUS.any { rendererName.contains(it, ignoreCase = true) }
        frameIntervalMs = if (weak) FRAME_MS_LOW_END else FRAME_MS_DEFAULT
        Log.i(TAG, "gpu='$rendererName' vendor='$vendor' weak=$weak")

        backdropProgram = buildProgram(Shaders.BACKDROP_VERTEX, Shaders.BACKDROP_FRAGMENT)
        if (backdropProgram != 0) {
            aPosBackdrop = GLES20.glGetAttribLocation(backdropProgram, "aPos")
            uBg0 = GLES20.glGetUniformLocation(backdropProgram, "uBg0")
            uBg1 = GLES20.glGetUniformLocation(backdropProgram, "uBg1")
            uTint = GLES20.glGetUniformLocation(backdropProgram, "uTint")
        }

        bandProgram = buildProgram(Shaders.BAND_VERTEX, Shaders.BAND_FRAGMENT)
        if (bandProgram != 0) {
            aPosBand = GLES20.glGetAttribLocation(bandProgram, "aPos")
            uRes = GLES20.glGetUniformLocation(bandProgram, "uRes")
            uAxis = GLES20.glGetUniformLocation(bandProgram, "uAxis")
            uGeom = GLES20.glGetUniformLocation(bandProgram, "uGeom")
            uPhase = GLES20.glGetUniformLocation(bandProgram, "uPhase")
            uBody = GLES20.glGetUniformLocation(bandProgram, "uBody")
            uTopEdge = GLES20.glGetUniformLocation(bandProgram, "uTopEdge")
            uBotEdge = GLES20.glGetUniformLocation(bandProgram, "uBotEdge")
            uAlpha = GLES20.glGetUniformLocation(bandProgram, "uAlpha")
        }

        colourHue = Float.NaN   // force a rebuild against the new program
        GLES20.glClearColor(0.012f, 0.016f, 0.039f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
        Log.i(TAG, "surface ${viewWidth}x$viewHeight, drawn at native resolution")
    }

    override fun onDrawFrame(gl: GL10?) {
        recordFrameTiming()
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        // Kept even though the backdrop covers the viewport: on a tiling GPU a clear is
        // what tells the driver it need not read the previous frame back into the tile.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (backdropProgram == 0) return
        syncColours()
        drawBackdrop()

        if (bandProgram == 0) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
        drawBands()
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawBackdrop() {
        GLES20.glUseProgram(backdropProgram)
        GLES20.glUniform3f(uBg0, colours[0], colours[1], colours[2])
        GLES20.glUniform3f(uBg1, colours[3], colours[4], colours[5])
        GLES20.glUniform3f(uTint, colours[6], colours[7], colours[8])

        quad.position(0)
        GLES20.glEnableVertexAttribArray(aPosBackdrop)
        GLES20.glVertexAttribPointer(aPosBackdrop, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosBackdrop)
    }

    private fun drawBands() {
        // Elapsed time stays here, in double precision, and only the wrapped phase
        // reaches the GPU. Sending seconds-since-start would destroy accuracy within
        // minutes, and Mali-400/450 has no fragment highp to fall back on.
        val t = (System.nanoTime() - startNanos) / 1_000_000_000.0 * speed

        // Band offsets, amplitudes and thicknesses are tuned in 1080-height terms, so
        // scale them with the surface rather than letting them shrink against it.
        val k = viewHeight / 1080f

        GLES20.glUseProgram(bandProgram)
        GLES20.glUniform2f(uRes, viewWidth.toFloat(), viewHeight.toFloat())
        GLES20.glUniform1f(uAxis, axisFraction)
        GLES20.glUniform3f(uBody, colours[9], colours[10], colours[11])
        GLES20.glUniform3f(uTopEdge, colours[12], colours[13], colours[14])
        GLES20.glUniform3f(uBotEdge, colours[15], colours[16], colours[17])

        strip.position(0)
        GLES20.glEnableVertexAttribArray(aPosBand)
        GLES20.glVertexAttribPointer(aPosBand, 2, GLES20.GL_FLOAT, false, 0, strip)

        for (b in BANDS) {
            GLES20.glUniform4f(
                uGeom, b.offset * k, b.amplitude * k, b.thickness * k, b.frequency / k,
            )
            // The two static per-band offsets fold into the uploaded phases, which is
            // why the shader can get away with three uniforms instead of five.
            GLES20.glUniform3f(
                uPhase,
                wrapTau(t * b.speed),
                wrapTau(t * b.speed * 0.6) - b.phaseB,
                wrapTau(t * b.speed * 0.85) + b.phaseC,
            )
            GLES20.glUniform1f(uAlpha, b.alpha)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, STRIP_VERTICES)
        }

        GLES20.glDisableVertexAttribArray(aPosBand)
    }

    /**
     * Achieved frame interval, logged once per [TIMING_WINDOW] frames for the first
     * [TIMING_WINDOWS] windows and then never again — enough to characterise startup
     * without a launcher trickling into logcat for the rest of the day.
     *
     * This measures the interval between successive draws rather than the GPU cost in
     * isolation: `eglSwapBuffers` blocks once the GPU falls behind, so in steady state
     * the interval converges on whichever of the pump rate and the GPU is slower.
     * Compared against [frameIntervalMs] it says whether there is headroom left. It has
     * been needed twice now to settle whether a change to this file helped or hurt, so
     * it stays.
     *
     * To get at the GPU cost alone, add a `glFinish()` at the end of [onDrawFrame] and
     * time it; that is how the 11.7ms figure in the class comment was taken.
     */
    private fun recordFrameTiming() {
        if (timingWindowsLogged >= TIMING_WINDOWS) return
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) {
            val dt = now - lastFrameNanos
            windowNanos += dt
            if (dt > worstFrameNanos) worstFrameNanos = dt
            if (++framesInWindow >= TIMING_WINDOW) {
                Log.i(
                    TAG,
                    "interval avg=${windowNanos / framesInWindow / 1_000_000}ms " +
                        "worst=${worstFrameNanos / 1_000_000}ms " +
                        "target=${frameIntervalMs}ms at ${viewWidth}x$viewHeight",
                )
                framesInWindow = 0
                windowNanos = 0L
                worstFrameNanos = 0L
                timingWindowsLogged++
            }
        }
        lastFrameNanos = now
    }

    /**
     * Rebuilds the six frame-constant colours when the theme or the time-of-day shade
     * moves. These were six `hsl2rgb` calls per pixel; here they are six per theme
     * change. The formulas must stay in step with [com.ember.xmb.ui.XmbPalette], which
     * derives the matching foreground colours the same way.
     */
    private fun syncColours() {
        val h = hue
        val s = shade
        if (h == colourHue && s == colourShade) return
        colourHue = h
        colourShade = s

        // analogous background family: shadow / core / highlight
        hsl(h - 28f, 0.42f, 0.03f + s * 0.02f, 0)
        hsl(h, 0.28f + s * 0.10f, 0.07f + s * 0.05f, 3)
        hsl(h + 22f, 0.50f + s * 0.22f, 0.07f + s * 0.09f, 6)
        // the ribbon: body, lit upper edge, darker lower edge
        hsl(h, 0.88f, 0.50f + s * 0.12f, 9)
        hsl(h + 22f, 0.96f, 0.74f + s * 0.12f, 12)
        hsl(h - 28f, 0.86f, 0.36f + s * 0.12f, 15)
    }

    /** HSL to RGB, written into [colours] at [at]. Mirrors the old GLSL `hsl2rgb`. */
    private fun hsl(hDeg: Float, s: Float, l: Float, at: Int) {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (((hDeg % 360f) + 360f) % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val m = l - c * 0.5f
        val r: Float
        val g: Float
        val b: Float
        when {
            hp < 1f -> { r = c; g = x; b = 0f }
            hp < 2f -> { r = x; g = c; b = 0f }
            hp < 3f -> { r = 0f; g = c; b = x }
            hp < 4f -> { r = 0f; g = x; b = c }
            hp < 5f -> { r = x; g = 0f; b = c }
            else -> { r = c; g = 0f; b = x }
        }
        colours[at] = r + m
        colours[at + 1] = g + m
        colours[at + 2] = b + m
    }

    private fun wrapTau(value: Double): Float {
        var r = value % TAU
        if (r < 0.0) r += TAU
        return r.toFloat()
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer = ByteBuffer
        .allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(values); position(0) }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) return 0

        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vs)
        GLES20.glAttachShader(id, fs)
        GLES20.glLinkProgram(id)

        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "link failed: " + GLES20.glGetProgramInfoLog(id))
            GLES20.glDeleteProgram(id)
            return 0
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return id
    }

    private fun compile(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "compile failed: " + GLES20.glGetShaderInfoLog(id))
            GLES20.glDeleteShader(id)
            return 0
        }
        return id
    }

    /** Geometry and timing for one ribbon, in 1080-height pixel terms. */
    private class Band(
        val offset: Float,
        val amplitude: Float,
        val thickness: Float,
        val frequency: Float,
        val alpha: Float,
        /** Static phase offsets, folded into the uploaded travel phases. */
        val phaseB: Float,
        val phaseC: Float,
        /** Radians per second. */
        val speed: Double,
    )

    private companion object {
        const val TAG = "WaveRenderer"
        const val TAU = 2.0 * Math.PI

        const val FRAME_MS_DEFAULT = 33L     // 30fps
        const val FRAME_MS_LOW_END = 66L     // 15fps; the wave is far too slow to tell

        /** Frames per [recordFrameTiming] log line, and how many lines to log. */
        const val TIMING_WINDOW = 60
        const val TIMING_WINDOWS = 3

        /**
         * Vertical slices per band strip. The highest-frequency ribbon has a period
         * around 1200px, so at 256 slices the linear interpolation between columns is
         * off by roughly five thousandths of a pixel — far below anything a panel can
         * show. The cost is 514 vertices per band, which is nothing even on Utgard.
         */
        const val SLICES = 256
        const val STRIP_VERTICES = (SLICES + 1) * 2

        /**
         * GPUs where per-pixel maths is the bottleneck. Mali-4xx is Utgard: no unified
         * shaders, and no highp in fragment shaders at all. Measured on an SDMC/Nebula
         * box (Mali-450 MP, GLES 2.0 only, 720p). These now only pick the frame rate;
         * the resolution no longer depends on the GPU.
         */
        val LOW_END_GPUS = listOf(
            "Mali-400", "Mali-450", "Mali-470",
            "VideoCore IV", "Vivante GC", "PowerVR SGX",
        )

        val BANDS = arrayOf(
            Band(-46f, 30f, 24f, 0.0035f, 0.30f, 0.0f, 1.1f, 1.04),
            Band(14f, 48f, 42f, 0.0024f, 0.42f, 2.1f, 0.4f, 0.72),
            Band(78f, 22f, 17f, 0.0052f, 0.24f, 4.2f, 2.6f, 1.36),
        )

        /** Clip-space triangle strip covering the viewport. */
        val QUAD = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        )
    }
}
