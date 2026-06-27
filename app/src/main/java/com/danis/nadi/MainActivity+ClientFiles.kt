package com.danis.nadi

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.model.TransferDirection
import org.json.JSONObject
import java.io.File

fun MainActivity.renderClientFiles(files: List<JSONObject>) {
    clientSharedFilesList.removeAllViews()
    if (files.isEmpty()) {
        clientSharedFilesList.addView(simpleStateCard("Belum ada berkas yang dibagikan."))
        return
    }

    files.forEachIndexed { index, fileJson ->
        val transferId = fileJson.getString("transferId")
        val fileName = fileJson.getString("fileName")
        val sizeBytes = fileJson.getLong("sizeBytes")
        val senderName = fileJson.optString("senderName", "Host")
        val mimeType = fileJson.optString("mimeType").takeIf { it.isNotEmpty() }

        val card = clientFileCard(transferId, fileName, sizeBytes, senderName, mimeType, index > 0)
        clientSharedFilesList.addView(card)
    }
}

fun MainActivity.clientFileCard(
    transferId: String,
    fileName: String,
    sizeBytes: Long,
    senderName: String,
    mimeType: String?,
    hasTopMargin: Boolean
): View {
    val activity = this
    val card = baseInfoCard(hasTopMargin)
    val row = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
    }
    val icon = ImageView(activity).apply {
        setImageResource(R.drawable.ic_document)
        imageTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.nadi_green)
        )
        layoutParams = LinearLayout.LayoutParams(34.dp(), 34.dp()).apply {
            marginEnd = 10.dp()
        }
    }
    val textColumn = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    textColumn.addView(TextView(activity).apply {
        text = fileName
        setTextColor(ContextCompat.getColor(activity, R.color.nadi_graphite))
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    })
    textColumn.addView(TextView(activity).apply {
        text = FileSizeFormatter.format(sizeBytes) + " - Oleh: " + senderName
        setTextColor(ContextCompat.getColor(activity, R.color.nadi_soft_ink))
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 2.dp()
        }
    })

    val targetFolder = controller.fileStore.roomFolder(null, "received")
    val targetFile = File(targetFolder, fileName)
    val exists = targetFile.exists()

    val actionBtn = com.google.android.material.button.MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonStyle).apply {
        text = if (exists) "Buka" else "Unduh"
        textSize = 11f
        setPadding(10.dp(), 0, 10.dp(), 0)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            36.dp()
        ).apply {
            marginStart = 8.dp()
        }
        if (exists) {
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(ContextCompat.getColor(activity, R.color.nadi_green))
            strokeColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.nadi_green))
            strokeWidth = 1.dp()
        } else {
            backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.nadi_green))
            setTextColor(Color.WHITE)
        }
        setOnClickListener {
            if (exists) {
                val uri = FileProvider.getUriForFile(activity, "${packageName}.fileprovider", targetFile)
                val opened = runCatching {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType ?: fileName.inferredMimeType())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(Intent.createChooser(intent, "Buka berkas"))
                }.isSuccess
                if (!opened) {
                    Toast.makeText(activity, "Tidak ada aplikasi untuk membuka file ini.", Toast.LENGTH_LONG).show()
                }
            } else {
                downloadClientSharedFile(transferId, fileName, mimeType, senderName)
            }
        }
    }

    row.addView(icon)
    row.addView(textColumn)
    row.addView(actionBtn)
    card.addView(row)
    return card
}

fun MainActivity.downloadClientSharedFile(
    transferId: String,
    fileName: String,
    mimeType: String?,
    senderName: String
) {
    val client = roomClient ?: return
    Toast.makeText(this, "Mengunduh $fileName...", Toast.LENGTH_SHORT).show()
    val tempDir = File(cacheDir, "downloads")
    client.downloadFile(transferId, fileName, tempDir) { success, tempFile ->
        if (success && tempFile != null) {
            try {
                tempFile.inputStream().use { input ->
                    controller.fileStore.saveRoomFile(
                        fileName = fileName,
                        mimeType = mimeType,
                        inputStream = input,
                        roomId = null,
                        folderName = "received",
                        direction = TransferDirection.DOWNLOAD,
                        senderName = senderName
                    )
                }
                tempFile.delete()
                Toast.makeText(this, "Unduhan selesai: $fileName", Toast.LENGTH_SHORT).show()
                client.fetchFiles()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Gagal menyimpan file.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Gagal mengunduh file.", Toast.LENGTH_SHORT).show()
        }
    }
}

fun MainActivity.handleClientFileUpload(uri: Uri, isAttachment: Boolean) {
    val client = roomClient ?: return
    val tempFile = copyUriToTempFile(uri)
    if (tempFile == null) {
        Toast.makeText(this, "Gagal memproses file.", Toast.LENGTH_SHORT).show()
        return
    }

    if (isAttachment) {
        handleClientChatAttachmentSelected(uri)
        tempFile.delete()
    } else {
        clientUploadProgress.visibility = View.VISIBLE
        clientUploadProgress.progress = 0
        clientUploadStatusText.visibility = View.VISIBLE
        clientUploadStatusText.text = "Mengirim ${tempFile.name}..."
        clientUploadFileButton.isEnabled = false
        client.uploadFile(tempFile, isAttachment = false, text = null, onProgress = { progress ->
            clientUploadProgress.progress = progress
            clientUploadStatusText.text = "Mengirim ${tempFile.name}: $progress%"
        }, onFinished = { success, _ ->
            clientUploadFileButton.isEnabled = true
            clientUploadProgress.visibility = View.GONE
            clientUploadStatusText.visibility = View.GONE
            tempFile.delete()
            if (success) {
                Toast.makeText(this, "File berhasil dikirim ke room.", Toast.LENGTH_SHORT).show()
                client.fetchFiles()
            } else {
                Toast.makeText(this, "Gagal mengirim file.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
