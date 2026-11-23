
package com.videoshrink.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private var selectedVideo: Uri? = null
    private lateinit var videoInfo: TextView
    private lateinit var compressButton: Button

    private val videoPicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                selectedVideo = uri
                videoInfo.text = "Selected: ${getFileName(uri)}"
                compressButton.isEnabled = true
            }
        }

    private fun getFileName(uri: Uri): String {
        var name = "Selected video"
        val cursor: Cursor? = contentResolver.query(
            uri, null, null, null, null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = it.getString(index)
            }
        }
        return name
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(18, 20, 30))
        }

        val title = TextView(this).apply {
            text = "VIDEO SHRINK"
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(this).apply {
            text = "Make your videos smaller. Keep the memories."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        val selectButton = Button(this).apply {
            text = "SELECT VIDEO"
            setOnClickListener {
                videoPicker.launch("video/*")
            }
        }

        videoInfo = TextView(this).apply {
            text = "No video selected yet"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val qualityTitle = TextView(this).apply {
            text = "Choose video quality"
            textSize = 18f
            setTextColor(Color.WHITE)
        }

        val quality = Spinner(this)
        val options = arrayOf("480p", "720p", "1080p")
        quality.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            options
        )

        compressButton = Button(this).apply {
            text = "COMPRESS VIDEO"
            isEnabled = false
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Compression engine coming next!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(Space(this), LinearLayout.LayoutParams(1, 45))
        layout.addView(selectButton)
        layout.addView(videoInfo)
        layout.addView(Space(this), LinearLayout.LayoutParams(1, 30))
        layout.addView(qualityTitle)
        layout.addView(quality)
        layout.addView(Space(this), LinearLayout.LayoutParams(1, 25))
        layout.addView(compressButton)

        setContentView(layout)
    }
}
