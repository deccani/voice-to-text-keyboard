package com.example.dictationdaddy.ime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.example.dictationdaddy.R
import com.example.dictationdaddy.audio.AudioRecorder
import com.example.dictationdaddy.network.WhisperService
import java.io.File

//Main Logic to update the UI and output

class CustomIME : InputMethodService() {

    private var startTime: Long = 0

    private var recordButton: ImageButton? = null
    private var audioRecorder: AudioRecorder? = null
    private var outputFile: File? = null

    private var micPulseView: View? = null
    private var pulseHandler: Handler? = null
    private var pulsing = false
    private var statusText: TextView? = null

    private var processingHandler: Handler? = null
    private var dotCount = 0

    private var sideDrawer: View? = null
    private var drawerToggle: ImageButton? = null
    private var drawerOpen = false

    override fun onCreateInputView(): View {
        val keyboardView = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        recordButton = keyboardView.findViewById(R.id.recordButton)
        micPulseView = keyboardView.findViewById(R.id.micPulse)
        statusText = keyboardView.findViewById(R.id.statusText)

        // Drawer
        sideDrawer = keyboardView.findViewById(R.id.sideDrawer)
        drawerToggle = keyboardView.findViewById(R.id.btnDrawerToggle)
        drawerToggle?.setOnClickListener { toggleDrawer() }


        keyboardView.findViewById<Button>(R.id.btnSpace).setOnClickListener {
            currentInputConnection.commitText(" ", 1)
        }
        keyboardView.findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            currentInputConnection.deleteSurroundingText(1, 0)
        }
        val switchKeyboardButton = keyboardView.findViewById<Button>(R.id.btnSwitchKeyboard)


        switchKeyboardButton.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }
        // drawer end


        // record
        recordButton?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startRecording()

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopRecording()
            }
            true
        }

        return keyboardView
    }

    private fun toggleDrawer() {
        val drawer = sideDrawer ?: return
        val toggle = drawerToggle ?: return

        if (drawerOpen) {

            drawer.animate()
                .translationX(drawer.width.toFloat())
                .setDuration(250)
                .start()
            toggle.setImageResource(R.drawable.ic_arrow_left)
        } else {

            drawer.animate()
                .translationX(0f)
                .setDuration(250)
                .start()
            toggle.setImageResource(R.drawable.ic_arrow_right)
        }
        drawerOpen = !drawerOpen
    }

    private fun showProcessingDots() {
        statusText?.visibility = View.VISIBLE
        statusText?.text = "Processing"
        dotCount = 0

        if (processingHandler == null) {
            processingHandler = Handler(Looper.getMainLooper())
        }

        val runnable = object : Runnable {
            override fun run() {
                if (statusText?.text?.startsWith("Processing") == true) {
                    dotCount = (dotCount + 1) % 4
                    val dots = ".".repeat(dotCount)
                    statusText?.text = "Processing$dots"
                    processingHandler?.postDelayed(this, 500)
                }
            }
        }
        processingHandler?.post(runnable)
    }

    private fun stopProcessingDots(success: Boolean) {
        processingHandler?.removeCallbacksAndMessages(null)
        if (success) {
            statusText?.text = "✔ Inserted"
            Handler(Looper.getMainLooper()).postDelayed({
                statusText?.text = "Hold to Record"
            }, 1000)
        } else {
            statusText?.text = "❌ Failed"
            Handler(Looper.getMainLooper()).postDelayed({
                statusText?.text = "Hold to Record"
            }, 2000)
        }
    }


    // Pulse Logic
    private fun startAmplitudePulse() {
        val pulse = micPulseView ?: return
        pulse.visibility = View.VISIBLE
        pulse.alpha = 1f
        pulse.scaleX = 1f
        pulse.scaleY = 1f
        pulsing = true

        if (pulseHandler == null) pulseHandler = Handler(Looper.getMainLooper())

        val tick = object : Runnable {
            override fun run() {
                if (!pulsing) return

                // 0..32767 - smooth + clamp
                val amp = audioRecorder?.getAmplitude() ?: 0
                // Convert amplitude to a 1.0..1.35 scale for nice subtle expansion
                val norm = (amp / 6000f).coerceIn(0f, 1f)       // normalize
                val scale = 1f + (0.30f * norm)                  // up to +35%
                val fade = 0.6f + (0.4f * (1f - norm))           // alpha 0.6..1.0

                pulse.scaleX = scale
                pulse.scaleY = scale
                pulse.alpha = fade

                pulseHandler?.postDelayed(this, 50) // ~20 FPS
            }
        }
        pulseHandler?.post(tick)
    }

    private fun stopAmplitudePulse() {
        pulsing = false
        pulseHandler?.removeCallbacksAndMessages(null)
        micPulseView?.apply {
            animate().alpha(0f).setDuration(120).withEndAction {
                visibility = View.GONE
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
            }.start()
        }
    }

    private fun startRecording() {
        outputFile = File(cacheDir, "recording.m4a")
        audioRecorder = AudioRecorder(outputFile!!)
        audioRecorder?.start()
        startTime = System.currentTimeMillis()

        statusText?.visibility = View.INVISIBLE
        startAmplitudePulse()
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        val duration = System.currentTimeMillis() - startTime
        stopAmplitudePulse()

        if (duration < 500) {
            // Short Recording
            audioRecorder?.cancel()
            Toast.makeText(this, "Recording too short", Toast.LENGTH_SHORT).show()
            return
        }

        audioRecorder?.stop()
        showProcessingDots()

        val file = audioRecorder?.outputFile ?: return
        WhisperService.transcribe(file) { result ->
            Handler(Looper.getMainLooper()).post {
//                deleteLastPlaceholder()
                if (result != null) {
                    currentInputConnection.commitText(result, 1)
                    stopProcessingDots(true)
                } else {
                    stopProcessingDots(false)
                }
            }
        }
    }

//    private fun deleteLastPlaceholder() {
//        // Delete placeholder ("[Processing transcription...]") if it was inserted
//        val placeholder = "[Processing transcription...]"
//        currentInputConnection.deleteSurroundingText(placeholder.length, 0)
//    }

}
