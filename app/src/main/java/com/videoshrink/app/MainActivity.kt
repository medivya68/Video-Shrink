
package com.videoshrink.app

import android.os.Bundle
import android.net.Uri
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.database.Cursor
import android.provider.OpenableColumns
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Presentation
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExportException
import java.io.File
import java.io.FileInputStream

class MainActivity : ComponentActivity() {

    private var selectedVideo: Uri? = null
    private lateinit var videoInfo: TextView
    private lateinit var compressButton: Button
    private lateinit var quality: Spinner
    private lateinit var statusText: TextView

    private val videoPicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                selectedVideo = uri
                videoInfo.text = "Selected: ${getFileName(uri)}"
                compressButton.isEnabled = true
                statusText.text = "Ready to compress"
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

    private fun startCompression() {
        val inputUri = selectedVideo ?: return

        val height = when (quality.selectedItem.toString()) {
            "480p" -> 480
            "720p" -> 720
            else -> 1080
        }

        compressButton.isEnabled = false
        statusText.text = "Compressing your video... Please wait."

        val outputFile = File(
            cacheDir,
            "compressed_${System.currentTimeMillis()}.mp4"
        )

        try {
            val editedItem = EditedMediaItem.Builder(
                MediaItem.fromUri(inputUri)
            ).setEffects(
                Effects(
                    emptyList(),
                    listOf(Presentation.createForHeight(height))
                )
            ).build()

            val transformer = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {

                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        try {
                            saveToGallery(outputFile)
                            statusText.text = "Compression complete! Video saved."
                            Toast.makeText(
                                this@MainActivity,
                                "Compressed video saved!",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            statusText.text = "Video created, but saving failed."
                            Toast.makeText(
                                this@MainActivity,
                                "Saving error: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            outputFile.delete()
                            compressButton.isEnabled = true
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        outputFile.delete()
                        statusText.text = "Compression failed: ${exportException.message}"
                        compressButton.isEnabled = true
                    }
                })
                .build()

            transformer.start(editedItem, outputFile.absolutePath)

        } catch (e: Exception) {
            outputFile.delete()
            statusText.text = "Error: ${e.message}"
            compressButton.isEnabled = true
        }
    }

    private fun saveToGallery(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/VideoShrink"
                )
            }
        }

        val uri = contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw Exception("Could not create video entry")

        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(file).use { input ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Could not save video")
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
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

        quality = Spinner(this)
        quality.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("480p", "720p", "1080p")
        )

        compressButton = Button(this).apply {
            text = "COMPRESS VIDEO"
            isEnabled = false
            setOnClickListener {
                startCompression()
            }
        }

        statusText = TextView(this).apply {
            text = "Select a video to begin"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
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
        layout.addView(statusText)

        setContentView(layout)
    }
}
