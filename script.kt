package com.example.mazeswiper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random
import android.os.CountDownTimer


class MainActivity : AppCompatActivity() { // makes main screen
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // called when the app first opens
        val mazeView = MazeView(this) // creates a new maze view / instead of using xml, we directly show the maze
        setContentView(mazeView) // instead of using xml, we directly show the maze
    }
}

class MazeView(context: Context) : View(context) { // custom game board

    // Maze and game state
    private var maze: Array<IntArray>? = null // 2d grid that stores walls and paths
    private var rows = 0
    private var cols = 0

    // dot logical position (row/col) and pixel center
    private var dotRow = 1 // which cell its in
    private var dotCol = 1
    private var dotCx = 0f // pixel position on screen
    private var dotCy = 0f

    private var goalRow = 0 // where the green dot is
    private var goalCol = 0

    // layout
    private var cellSize = 0f // how big each square is
    private var offsetX = 0f // how much space to leave on the sides so the maze is centered
    private var offsetY = 0f

    // animation state
    private var isAnimating = false

    // Timer
    private var timer: CountDownTimer? = null
    private var timeLeft = 45  // seconds
    private val timerPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        textSize = 60f
        textAlign = Paint.Align.CENTER
    }


    // Paints
    private val wallPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.DKGRAY } // walls are dark gray
    private val floorPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.WHITE } // floors are white
    private val dotPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.RED } // the dot is red
    private val goalPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.GREEN } // the goal is green

    // Gesture detection
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true // required so it works

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean { // detects a swipe left right up down
            if (isAnimating) return true

            val dx = e2.x - (e1?.x ?: e2.x)
            val dy = e2.y - (e1?.y ?: e2.y)

            if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                if (dx > 0) moveDot(0, 1) else moveDot(0, -1)
            } else {
                if (dy > 0) moveDot(1, 0) else moveDot(-1, 0)
            }
            return true
        }
    })

    private fun placeGoal() {
        val grid = maze ?: return

        // Define candidate corners (row, col)
        val corners = listOf(
            Pair(1, 1),                          // top-left
            Pair(1, cols - 2),                   // top-right
            Pair(rows - 2, 1),                   // bottom-left
            Pair(rows - 2, cols - 2)             // bottom-right
        )

        // Filter out the corner if it coincides with the red dot
        val openCorners = corners.filter { (r, c) ->
            grid[r][c] == 0 && !(r == dotRow && c == dotCol)
        }

        if (openCorners.isNotEmpty()) {
            val (r, c) = openCorners.random()
            goalRow = r
            goalCol = c
        } else {
            // fallback: pick any open cell
            val openCells = mutableListOf<Pair<Int, Int>>()
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (grid[r][c] == 0 && !(r == dotRow && c == dotCol)) {
                        openCells.add(Pair(r, c))
                    }
                }
            }
            if (openCells.isNotEmpty()) {
                val (r, c) = openCells.random()
                goalRow = r
                goalCol = c
            }
        }

    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    // Maze generation
    // runs when the view first gets its size, calculates how big cells are, generates the maze, places the red dot at (1,1), places the green goal with placeGoal()
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (maze == null) {
            rows = 21
            cols = 21
            cellSize = min(w.toFloat() / cols, h.toFloat() / rows)
            offsetX = (w - cellSize * cols) / 2f
            offsetY = (h - cellSize * rows) / 2f

            maze = generateMazeDFS(rows, cols)

            // ensure starting dot is on open cell
            if (maze!![dotRow][dotCol] != 0) {
                loop@ for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        if (maze!![r][c] == 0) {
                            dotRow = r
                            dotCol = c
                            break@loop
                        }
                    }
                }
            }
            dotCx = offsetX + dotCol * cellSize + cellSize / 2f
            dotCy = offsetY + dotRow * cellSize + cellSize / 2f
        }

        maze = generateMazeDFS(rows, cols)
        dotRow = 1
        dotCol = 1
        dotCx = offsetX + dotCol * cellSize + cellSize / 2f
        dotCy = offsetY + dotRow * cellSize + cellSize / 2f

        placeGoal()
        startTimer()
    }

    private fun generateMazeDFS(gridRows: Int, gridCols: Int): Array<IntArray> { // builds the maze using a depth first search algorithm
        // starts with all walls, randomly carves passages, keeps track of visited cells, continues until every cell is reachable
        val maze = Array(gridRows) { IntArray(gridCols) { 1 } }
        val cellRows = (gridRows - 1) / 2
        val cellCols = (gridCols - 1) / 2
        val visited = Array(cellRows) { BooleanArray(cellCols) }
        val rand = Random(System.currentTimeMillis())

        fun gr(cr: Int) = 2 * cr + 1
        fun gc(cc: Int) = 2 * cc + 1

        val stack = ArrayDeque<Pair<Int, Int>>()
        visited[0][0] = true
        maze[gr(0)][gc(0)] = 0
        stack.add(Pair(0, 0))

        val dirs = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))

        while (stack.isNotEmpty()) {
            val (cr, cc) = stack.last()
            val neighbors = mutableListOf<Pair<Int, Int>>()
            for ((dr, dc) in dirs) {
                val nr = cr + dr
                val nc = cc + dc
                if (nr in 0 until cellRows && nc in 0 until cellCols && !visited[nr][nc]) {
                    neighbors.add(Pair(nr, nc))
                }
            }
            if (neighbors.isNotEmpty()) {
                val (nr, nc) = neighbors[rand.nextInt(neighbors.size)]
                val g1r = gr(cr); val g1c = gc(cc)
                val g2r = gr(nr); val g2c = gc(nc)
                maze[g2r][g2c] = 0
                maze[(g1r + g2r) / 2][(g1c + g2c) / 2] = 0
                visited[nr][nc] = true
                stack.add(Pair(nr, nc))
            } else stack.removeLast()
        }

        maze[1][1] = 0
        maze[gridRows - 2][gridCols - 2] = 0
        return maze
    }

    // Drawing
    // called whenever the screen updates
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.LTGRAY) // paints the background light gray

        val grid = maze ?: return
        for (r in 0 until rows) { // loop through every cell
            for (c in 0 until cols) {
                val left = offsetX + c * cellSize
                val top = offsetY + r * cellSize
                val right = left + cellSize
                val bottom = top + cellSize
                canvas.drawRect(left, top, right, bottom, if (grid[r][c] == 1) wallPaint else floorPaint) // walls are gray, path is white
            }
        }

        // draw dot
        val radius = cellSize * 0.35f
        canvas.drawCircle(dotCx, dotCy, radius, dotPaint)

        // draw goal dot
        val goalCx = offsetX + goalCol * cellSize + cellSize / 2f
        val goalCy = offsetY + goalRow * cellSize + cellSize / 2f
//        val radius = (cellSize * 0.35f).coerceAtMost(100f)
        canvas.drawCircle(goalCx, goalCy, radius, goalPaint)
        // draw timer
        canvas.drawText("Time: $timeLeft", width / 2f, 80f, timerPaint)
    }

    // Dot movement & animation
    private fun moveDot(dr: Int, dc: Int) {
        if (isAnimating) return
        val grid = maze ?: return
        var r = dotRow
        var c = dotCol
        // dr = row change (up or down)
        // dc = column change (left or right)

        // slide until wall or goal
        while (true) {
            val nr = r + dr
            val nc = c + dc
            if (nr !in 0 until rows || nc !in 0 until cols) break
            if (grid[nr][nc] == 1) break
            r = nr
            c = nc

            // Stop if we hit the goal
            if (r == goalRow && c == goalCol) break
        }

        if (r == dotRow && c == dotCol) return

        // starts from current row / col, keeps moving in that direction until it hits a wall or the goal, calculates the start and end pixels, animates from start to end using value animator
        val startX = dotCx
        val startY = dotCy
        val endX = offsetX + c * cellSize + cellSize / 2f
        val endY = offsetY + r * cellSize + cellSize / 2f
        val distance = hypot(endX - startX, endY - startY)
        val duration = ((1 / (distance + 1)) * 1000 + 200).toLong().coerceAtMost(1500L)

        // animates a number from 0 to 1, each frame it moves the dot closer to the end
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = duration
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { anim ->
            val f = anim.animatedValue as Float
            dotCx = startX + (endX - startX) * f
            dotCy = startY + (endY - startY) * f
            // as f goes from 0 to 1, the dot moves smoothly

            // Calculate current row/col based on pixel position
            val curRow = ((dotCy - offsetY) / cellSize).toInt().coerceIn(0, rows - 1)
            val curCol = ((dotCx - offsetX) / cellSize).toInt().coerceIn(0, cols - 1)

            // Reset immediately if we reach the goal
            if (curRow == goalRow && curCol == goalCol) {
                animator.cancel()  // stop current animation
                resetMaze()
            }

            invalidate()
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: android.animation.Animator) {
                isAnimating = true
            }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                dotRow = r
                dotCol = c
                isAnimating = false
            }
        })
        animator.start()
    }

    private fun resetMaze() { // makes a new maze, puts the red dot back at the start, places a new green goal, calls invalidate to redraw screen
        maze = generateMazeDFS(rows, cols)
        dotRow = 1
        dotCol = 1
        dotCx = offsetX + dotCol * cellSize + cellSize / 2f
        dotCy = offsetY + dotRow * cellSize + cellSize / 2f
        placeGoal()
        startTimer()
        invalidate()
    }

    private fun startTimer() {
        timer?.cancel()
        timeLeft = 45   // reset to 45 seconds
        timer = object : CountDownTimer(45_000, 1000) { // 45 seconds
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = (millisUntilFinished / 1000).toInt()
                invalidate()
            }

            override fun onFinish() {
                resetMaze()
            }
        }.start()
    }
}
