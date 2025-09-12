package com.example.mazeswiper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var mazeView: MazeView
    private var hardMode = false
    private var roundCount = 0
    private var timer: CountDownTimer? = null
    private lateinit var modeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mazeView = MazeView(this) { onRoundComplete() }

        modeButton = Button(this).apply {
            text = "Switch to Hard Mode"
            setOnClickListener {
                hardMode = !hardMode
                text = if (hardMode) "Switch to Easy Mode" else "Switch to Hard Mode"
                mazeView.setHardMode(hardMode)
            }
        }

        val layout = FrameLayout(this)
        layout.addView(mazeView)
        layout.addView(modeButton)

        setContentView(layout)

        startNewRound()
    }

    private fun startNewRound() {
        timer?.cancel()
        mazeView.resetRound()

        timer = object : CountDownTimer(45000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                mazeView.setTime((millisUntilFinished / 1000).toInt())
            }

            override fun onFinish() {
                mazeView.resetRound()
                startNewRound()
            }
        }.start()
    }

    private fun onRoundComplete() {
        roundCount++
        if (roundCount % 3 == 0) {
            showPopup()
        }
        startNewRound()
    }

    private fun showPopup() {
        val messages = listOf(
            "Stand up and stretch!",
            "Take a deep breath.",
            "Try doing one good deed today!"
        )
        val message = messages.random()

        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("✕") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}

class MazeView(context: Context, val onRoundComplete: () -> Unit) :
    View(context), GestureDetector.OnGestureListener {

    private val paintRed = Paint().apply { color = Color.RED }
    private val paintGreen = Paint().apply { color = Color.GREEN }
    private val paintText = Paint().apply {
        color = Color.BLACK
        textSize = 60f
    }

    private val gestureDetector = GestureDetector(context, this)

    private var redX = 100f
    private var redY = 100f
    private var greenX = 700f
    private var greenY = 700f

    private var showGreen = false
    private var hardMode = false
    private var timeLeft = 45

    private var greenMoveRunnable: Runnable? = null

    init {
        // Delay green dot appearance
        postDelayed({ showGreen = true; invalidate() }, 10000)
    }

    fun resetRound() {
        redX = 100f
        redY = 100f
        showGreen = false
        timeLeft = 45

        // place green in corner
        val corners = listOf(
            Pair(100f, 100f),
            Pair(width - 100f, 100f),
            Pair(100f, height - 100f),
            Pair(width - 100f, height - 100f)
        )
        val (gx, gy) = corners.random()
        greenX = gx
        greenY = gy

        // reset hard mode movement
        removeCallbacks(greenMoveRunnable)
        if (hardMode) {
            startMovingGreenDot()
        }

        postDelayed({ showGreen = true; invalidate() }, 10000)
        invalidate()
    }

    fun setHardMode(enabled: Boolean) {
        hardMode = enabled
        removeCallbacks(greenMoveRunnable)
        if (enabled && showGreen) {
            startMovingGreenDot()
        }
    }

    fun setTime(time: Int) {
        timeLeft = time
        invalidate()
    }

    private fun startMovingGreenDot() {
        greenMoveRunnable = object : Runnable {
            override fun run() {
                // random small move
                greenX += listOf(-50f, 0f, 50f).random()
                greenY += listOf(-50f, 0f, 50f).random()

                // keep inside bounds
                greenX = greenX.coerceIn(50f, width - 50f)
                greenY = greenY.coerceIn(50f, height - 50f)

                invalidate()
                postDelayed(this, 1000) // move every second
            }
        }
        post(greenMoveRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(redX, redY, 40f, paintRed)
        if (showGreen) {
            canvas.drawCircle(greenX, greenY, 40f, paintGreen)
        }
        canvas.drawText("Time: $timeLeft", 50f, 100f, paintText)

        if (showGreen && distance(redX, redY, greenX, greenY) < 80) {
            onRoundComplete()
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    // Swipe detection
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val dx = (e2?.x ?: 0f) - (e1?.x ?: 0f)
        val dy = (e2?.y ?: 0f) - (e1?.y ?: 0f)

        if (Math.abs(dx) > Math.abs(dy)) {
            // horizontal
            if (dx > 0) redX += 200 else redX -= 200
        } else {
            // vertical
            if (dy > 0) redY += 200 else redY -= 200
        }

        invalidate()
        return true
    }

    // unused gesture methods
    override fun onDown(e: MotionEvent?) = true
    override fun onShowPress(e: MotionEvent?) {}
    override fun onSingleTapUp(e: MotionEvent?) = false
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, dX: Float, dY: Float) = false
    override fun onLongPress(e: MotionEvent?) {}
}
