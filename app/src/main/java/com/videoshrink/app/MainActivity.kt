
package com.videoshrink.app

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.database.Cursor
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExportException
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import android.app.Activity
import android.app.AlertDialog
import androidx.activity.result.IntentSenderRequest


class MainActivity : ComponentActivity() {

    private var selectedVideo: Uri? = null
    private var selectedVideoSizeBytes: Long? = null
    private var savedVideoUri: Uri? = null

    private lateinit var videoInfo: TextView
    private lateinit var compressButton: Button
    private lateinit var playButton: Button
    private lateinit var quality: Spinner
    private lateinit var statusText: TextView
    private lateinit var comparisonText: TextView

    private val videoPicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                selectedVideo = uri
                selectedVideoSizeBytes = getFileSize(uri)
                savedVideoUri = null

                videoInfo.text = "Selected: ${getFileName(uri)}"

                compressButton.isEnabled = true
                playButton.isEnabled = false

                comparisonText.text =
                    "Original size: ${
                        selectedVideoSizeBytes?.let { formatSize(it) }
                            ?: "Unavailable"
                    }\nCompressed size: —\nSpace saved: —"

                statusText.text = "Ready to compress"
            }
        }


private val deleteOriginalLauncher =
    registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->

        if (result.resultCode == Activity.RESULT_OK) {
            statusText.text = "Original video deleted."

            Toast.makeText(
                this,
                "Original video deleted.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            statusText.text = "Original video kept."
        }
    }
    

private fun showDeleteOriginalDialog() {
    val originalUri = selectedVideo ?: return

    AlertDialog.Builder(this)
        .setTitle("Compression successful!")
        .setMessage(
            "Your compressed video has been saved.\n\n" +
            "Would you like to delete the original video?"
        )
        .setNegativeButton("Keep Original") { _, _ ->
            statusText.text = "Original video kept."
        }
        .setPositiveButton("Delete Original") { _, _ ->
            requestDeleteOriginal(originalUri)
        }
        .setCancelable(true)
        .show()
}

private fun requestDeleteOriginal(uri: Uri) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        statusText.text =
            "Original kept. Safe deletion confirmation requires Android 11 or newer."
        return
    }

    if (uri.scheme != "content" ||
        uri.authority != MediaStore.AUTHORITY
    ) {
        statusText.text =
            "Original kept. This video source doesn't support safe deletion from Video Shrink."
        Toast.makeText(
            this,
            "Please delete the original from its Gallery or file app.",
            Toast.LENGTH_LONG
        ).show()
        return
    }

    try {
        val deleteRequest = MediaStore.createDeleteRequest(
            contentResolver,
            listOf(uri)
        )

        val request = IntentSenderRequest.Builder(
            deleteRequest.intentSender
        ).build()

        deleteOriginalLauncher.launch(request)

    } catch (e: Exception) {
    statusText.text =
    "Deletion error: ${e.javaClass.simpleName}: ${e.message}"
        Toast.makeText(
            this,
            "Could not request deletion. The original was kept.",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun getFileName(uri: Uri): String {
        var name = "Selected video"

        val cursor: Cursor? = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val index =
                    it.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                if (index >= 0 && !it.isNull(index)) {
                    name = it.getString(index)
                }
            }
        }

        return name
    }

    private fun getFileSize(uri: Uri): Long? {
        val cursor = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val index =
                    it.getColumnIndex(OpenableColumns.SIZE)

                if (index >= 0 && !it.isNull(index)) {
                    val size = it.getLong(index)

                    if (size >= 0L) {
                        return size
                    }
                }
            }
        }

        return try {
            contentResolver
                .openAssetFileDescriptor(uri, "r")
                ?.use { descriptor ->
                    if (descriptor.length >= 0L) {
                        descriptor.length
                    } else {
                        null
                    }
                }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024L) {
            return "$bytes B"
        }

        val units = arrayOf("KB", "MB", "GB")
        var size = bytes.toDouble() / 1024.0
        var unitIndex = 0

        while (size >= 1024.0 && unitIndex < units.lastIndex) {
            size /= 1024.0
            unitIndex++
        }

        return String.format(
            Locale.US,
            "%.2f %s",
            size,
            units[unitIndex]
        )
    }

    private fun startCompression() {
        val inputUri = selectedVideo ?: return

        val height = when (quality.selectedItem.toString()) {
            "480p" -> 480
            "720p" -> 720
            else -> 1080
        }

        compressButton.isEnabled = false
        playButton.isEnabled = false

        statusText.text =
            "Compressing your video... Please wait."

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
                            val compressedSize = outputFile.length()

                            if (compressedSize <= 0L) {
                                throw Exception(
                                    "Compressed video is empty"
                                )
                            }

                            val galleryUri =
                                saveToGallery(outputFile)

                            savedVideoUri = galleryUri
                            playButton.isEnabled = true

                            showSizeComparison(compressedSize)

                            statusText.text =
                                "Compression complete! Video saved."

showDeleteOriginalDialog()

                            Toast.makeText(
                                this@MainActivity,
                                "Compressed video saved!",
                                Toast.LENGTH_LONG
                            ).show()

                        } catch (e: Exception) {
                            savedVideoUri = null
                            playButton.isEnabled = false

                            statusText.text =
                                "Video created, but saving failed."

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

                        statusText.text =
                            "Compression failed: ${exportException.message}"

                        compressButton.isEnabled = true
                        playButton.isEnabled = false
                    }
                })
                .build()

            transformer.start(
                editedItem,
                outputFile.absolutePath
            )

        } catch (e: Exception) {
            outputFile.delete()

            statusText.text = "Error: ${e.message}"

            compressButton.isEnabled = true
            playButton.isEnabled = false
        }
    }

    private fun showSizeComparison(compressedSize: Long) {
        val originalSize = selectedVideoSizeBytes

        if (originalSize == null || originalSize <= 0L) {
            comparisonText.text =
                "Original size: Unavailable\n" +
                "Compressed size: ${formatSize(compressedSize)}\n" +
                "Space saved: Unable to calculate"

            return
        }

        val difference = originalSize - compressedSize

        if (difference >= 0L) {
            val percentage =
                difference.toDouble() / originalSize.toDouble() * 100.0

            comparisonText.text =
                "Original size: ${formatSize(originalSize)}\n" +
                "Compressed size: ${formatSize(compressedSize)}\n" +
                "Space saved: ${formatSize(difference)} " +
                "(${String.format(Locale.US, "%.1f", percentage)}% smaller)"

        } else {
            val increasedBy = compressedSize - originalSize
            val percentage =
                increasedBy.toDouble() / originalSize.toDouble() * 100.0

            comparisonText.text =
                "Original size: ${formatSize(originalSize)}\n" +
                "Compressed size: ${formatSize(compressedSize)}\n" +
                "File size increased by ${formatSize(increasedBy)} " +
                "(${String.format(Locale.US, "%.1f", percentage)}% larger)"
        }
    }

    private fun playSavedVideo() {
        val uri = savedVideoUri ?: return

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "No video player was found on this device.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not open the video: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveToGallery(file: File): Uri {
        val values = ContentValues().apply {
            put(
                MediaStore.Video.Media.DISPLAY_NAME,
                file.name
            )

            put(
                MediaStore.Video.Media.MIME_TYPE,
                "video/mp4"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/VideoShrink"
                )

                put(
                    MediaStore.Video.Media.IS_PENDING,
                    1
                )
            }
        }

        val uri = contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw Exception(
            "Could not create video entry"
        )

        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(file).use { input ->
                    input.copyTo(output)
                }
            } ?: throw Exception(
                "Could not save video"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completedValues = ContentValues().apply {
                    put(
                        MediaStore.Video.Media.IS_PENDING,
                        0
                    )
                }

                contentResolver.update(
                    uri,
                    completedValues,
                    null,
                    null
                )
            }

            return uri

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

        playButton = Button(this).apply {
            text = "PLAY COMPRESSED VIDEO"
            isEnabled = false

            setOnClickListener {
                playSavedVideo()
            }
        }

        comparisonText = TextView(this).apply {
            text = "Original size: —\n" +
                   "Compressed size: —\n" +
                   "Space saved: —"

            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
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
        layout.addView(playButton)
        layout.addView(comparisonText)
        layout.addView(statusText)

        setContentView(layout)
    }
}
