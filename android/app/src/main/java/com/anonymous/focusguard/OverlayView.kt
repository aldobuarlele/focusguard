package com.anonymous.focusguard

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlin.random.Random

/**
 * OverlayView - Dynamic blocking overlay for FocusGuard with Custom NumPad
 * 
 * CRITICAL FIX: This overlay uses FLAG_NOT_FOCUSABLE to prevent the 
 * AccessibilityService from detecting our own overlay window as a 
 * "foreground app change" event, which would cause an infinite trigger loop.
 * 
 * The overlay CONSUMES all touch events (isClickable = true) to prevent
 * interaction with the blocked app underneath. FLAG_NOT_TOUCHABLE is NOT used.
 * 
 * PHASE 3: Dynamic UI with Custom NumPad to avoid Focus/Keyboard paradox
 */
class OverlayView(context: Context) : FrameLayout(context) {
    
    private var currentPackageName: String = ""
    private var currentBlockLevel: Int = 0
    private var onBypassRequested: ((Int) -> Unit)? = null
    private var onGoHome: (() -> Unit)? = null
    
    // Dynamic durations for bypass levels
    private var level1Duration: Int = 5  // Default 5 minutes
    private var level2Duration: Int = 15 // Default 15 minutes
    
    // For Level 2 (Gamification)
    private var mathAnswer: String = ""
    private var correctAnswer: Int = 0
    private lateinit var answerDisplay: TextView
    
    init {
        setupView()
    }
    
    private fun setupView() {
        // Set layout parameters for full screen overlay
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        // CRITICAL: Make overlay CONSUME all touch events
        // isClickable = true ensures touches are swallowed by the overlay
        // and do NOT pass through to the blocked app underneath
        // isFocusable = false prevents focus stealing that causes infinite loops
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false
    }
    
    /**
     * Show overlay with specific block level and callbacks
     * @param packageName The package name of the blocked app
     * @param blockLevel The block level (1=nudge, 2=challenge, 3=hard block)
     * @param durationL1 Duration in minutes for Level 1 bypass
     * @param durationL2 Duration in minutes for Level 2 bypass
     * @param onBypassRequested Callback when user requests bypass with duration in minutes
     * @param onGoHome Callback when user wants to go home
     */
    fun showOverlay(
        packageName: String, 
        blockLevel: Int, 
        durationL1: Int,
        durationL2: Int,
        onBypassRequested: (Int) -> Unit,
        onGoHome: () -> Unit
    ) {
        this.currentPackageName = packageName
        this.currentBlockLevel = blockLevel
        this.onBypassRequested = onBypassRequested
        this.onGoHome = onGoHome
        
        // Store durations for UI generation
        this.level1Duration = durationL1
        this.level2Duration = durationL2
        
        // Remove all existing views
        removeAllViews()
        
        // Build UI based on block level
        when (blockLevel) {
            1 -> buildLevel1NudgeUI()
            2 -> buildLevel2GamificationUI()
            else -> buildLevel3HardBlockUI()
        }
    }
    
    /**
     * Level 1: Nudge UI
     * Background: Semi-transparent black (#CC000000)
     * Content: "Yakin mau buka aplikasi ini?" with "Tutup" and "Lanjutkan (5 Menit)" buttons
     */
    private fun buildLevel1NudgeUI() {
        // Set semi-transparent black background
        setBackgroundColor(Color.parseColor("#CC000000"))
        
        // Create vertical LinearLayout
        val verticalLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        // Question TextView
        val questionText = TextView(context).apply {
            text = "Yakin mau buka aplikasi ini?"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 50)
        }
        
        // "Tutup" Button (Go Home)
        val closeButton = Button(context).apply {
            text = "Tutup"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#FF5722"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                onGoHome?.invoke()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
        }
        
        // "Lanjutkan (X Menit)" Button (Bypass for dynamic duration)
        val continueButton = Button(context).apply {
            text = "Lanjutkan ($level1Duration Menit)"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                onBypassRequested?.invoke(level1Duration)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Add views to layout
        verticalLayout.addView(questionText)
        verticalLayout.addView(closeButton)
        verticalLayout.addView(continueButton)
        
        // Add layout to overlay
        addView(verticalLayout)
    }
    
    /**
     * Level 2: Gamification/Challenge UI with Custom NumPad
     * Background: Solid dark gray (#2C3E50)
     * Content: Math question with custom NumPad (0-9, Clear, Submit)
     */
    private fun buildLevel2GamificationUI() {
        // Set dark gray background
        setBackgroundColor(Color.parseColor("#2C3E50"))
        
        // Generate random math question
        val a = Random.nextInt(10, 51) // 10 to 50
        val b = Random.nextInt(10, 51) // 10 to 50
        correctAnswer = a + b
        mathAnswer = ""
        
        // Create vertical LinearLayout
        val verticalLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        // Instruction TextView
        val instructionText = TextView(context).apply {
            text = "Selesaikan untuk akses $level2Duration Menit:"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        
        // Math question TextView
        val mathQuestionText = TextView(context).apply {
            text = "$a + $b = ?"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        
        // Answer display TextView
        answerDisplay = TextView(context).apply {
            text = "Jawaban: "
            textSize = 24f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        
        // Create GridLayout for NumPad (3 columns)
        val numPadGrid = GridLayout(context).apply {
            columnCount = 3
            rowCount = 4
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // NumPad buttons: 1-9, 0, Clear, Submit
        val buttonLabels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Clear", "0", "Submit")
        
        for (label in buttonLabels) {
            val button = Button(context).apply {
                text = label
                textSize = 18f
                setBackgroundColor(Color.parseColor("#34495E"))
                setTextColor(Color.WHITE)
                
                val params = GridLayout.LayoutParams().apply {
                    width = 120
                    height = 120
                    setMargins(5, 5, 5, 5)
                }
                layoutParams = params
                
                setOnClickListener {
                    handleNumPadButtonClick(label)
                }
            }
            numPadGrid.addView(button)
        }
        
        // Add views to layout
        verticalLayout.addView(instructionText)
        verticalLayout.addView(mathQuestionText)
        verticalLayout.addView(answerDisplay)
        verticalLayout.addView(numPadGrid)
        
        // Add layout to overlay
        addView(verticalLayout)
    }
    
    /**
     * Handle NumPad button clicks for Level 2
     */
    private fun handleNumPadButtonClick(label: String) {
        when (label) {
            "Clear" -> {
                mathAnswer = ""
                answerDisplay.text = "Jawaban: "
            }
            "Submit" -> {
                if (mathAnswer.isNotEmpty()) {
                    val userAnswer = mathAnswer.toIntOrNull()
                    if (userAnswer == correctAnswer) {
                        // Correct answer - grant dynamic duration bypass
                        onBypassRequested?.invoke(level2Duration)
                    } else {
                        // Wrong answer - clear and show error
                        mathAnswer = ""
                        answerDisplay.text = "Salah! Coba lagi: "
                        // Show red error briefly
                        answerDisplay.setTextColor(Color.RED)
                        postDelayed({
                            answerDisplay.setTextColor(Color.YELLOW)
                        }, 1000)
                    }
                }
            }
            else -> {
                // Number button (0-9)
                if (mathAnswer.length < 3) { // Limit to 3 digits
                    mathAnswer += label
                    answerDisplay.text = "Jawaban: $mathAnswer"
                }
            }
        }
    }
    
    /**
     * Level 3: Hard Block UI
     * Background: Solid Red (#B71C1C)
     * Content: "FOCUSGUARD: APLIKASI DIBLOKIR!" with no buttons or escape
     */
    private fun buildLevel3HardBlockUI() {
        // Set solid red background
        setBackgroundColor(Color.parseColor("#B71C1C"))
        
        // Create blocking text view
        val blockingText = TextView(context).apply {
            text = "FOCUSGUARD: APLIKASI DIBLOKIR!"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(50, 100, 50, 100)
        }
        
        // Add text view to overlay
        addView(blockingText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }
    
    /**
     * CRITICAL WindowManager flags to prevent self-triggering loop:
     * 
     * FLAG_NOT_FOCUSABLE: Window will not receive input focus (KEEPS infinite loop fix)
     * FLAG_LAYOUT_IN_SCREEN: Place window within the entire screen
     * 
     * NOTE: FLAG_NOT_TOUCHABLE is NOT used - we WANT the overlay to consume touches
     * so the blocked app underneath cannot be interacted with.
     * The view's isClickable = true ensures touch events are swallowed.
     */
    fun getWindowManagerLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            // FLAG_NOT_FOCUSABLE prevents AccessibilityService self-trigger loop
            // NO FLAG_NOT_TOUCHABLE - overlay must consume touches to block the app
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = android.graphics.PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
    }
}
