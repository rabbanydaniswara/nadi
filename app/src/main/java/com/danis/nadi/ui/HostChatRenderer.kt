package com.danis.nadi.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.danis.nadi.R
import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class HostChatRenderer(
    private val context: Context,
    private val scrollView: NestedScrollView,
    private val container: LinearLayout,
    private val hostIdProvider: () -> String,
    private val hostNameProvider: () -> String,
    private val roomIdProvider: () -> String?,
    private val attachmentProvider: (String) -> TransferItem?,
    private val onPreviewImage: (TransferItem) -> Unit,
    private val onOpenAttachment: (TransferItem) -> Unit
) {
    private val chatTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var lastRenderedRoomId: String? = null
    private val renderedMessageIds = linkedSetOf<String>()
    private val renderedMessageSignatures = mutableMapOf<String, String>()

    var forceScrollToBottom: Boolean = false

    fun render(messages: List<ChatMessage>) {
        val shouldScrollToBottom = forceScrollToBottom || isNearBottom()
        forceScrollToBottom = false

        val roomId = roomIdProvider()
        val currentMessageIds = messages.mapTo(mutableSetOf()) { it.messageId }
        val hasStatusChange = messages.any { message ->
            renderedMessageSignatures[message.messageId] != null &&
                renderedMessageSignatures[message.messageId] != message.renderSignature()
        }
        val shouldRebuild = roomId != lastRenderedRoomId ||
            !currentMessageIds.containsAll(renderedMessageIds) ||
            hasStatusChange
        if (shouldRebuild) {
            container.removeAllViews()
            renderedMessageIds.clear()
            renderedMessageSignatures.clear()
            lastRenderedRoomId = roomId
        }

        if (messages.isEmpty()) {
            if (container.childCount == 0) {
                container.addView(emptyState())
            }
            scrollView.post { scrollView.scrollTo(0, 0) }
            return
        }

        val newMessages = messages.filterNot { it.messageId in renderedMessageIds }
        if (newMessages.isEmpty()) return
        if (renderedMessageIds.isEmpty() && container.childCount > 0) {
            container.removeAllViews()
        }

        val hostId = hostIdProvider()
        val hostName = hostNameProvider()
        newMessages.forEach { message ->
            val isHost = message.senderId == hostId || message.senderName == hostName
            val attachment = message.attachmentTransferId?.let(attachmentProvider)
            container.addView(messageBubble(message, attachment, isHost))
            renderedMessageIds.add(message.messageId)
            renderedMessageSignatures[message.messageId] = message.renderSignature()
        }

        if (shouldScrollToBottom) {
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun isNearBottom(): Boolean {
        if (scrollView.childCount == 0) return true
        val content = scrollView.getChildAt(0)
        val distanceToBottom = content.bottom - (scrollView.scrollY + scrollView.height)
        return distanceToBottom <= 48.dp()
    }

    private fun emptyState(): View {
        val title = TextView(context).apply {
            text = context.getString(R.string.chat_empty)
            setTextColor(ContextCompat.getColor(context, R.color.nadi_graphite))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val hint = TextView(context).apply {
            text = context.getString(R.string.chat_empty_hint)
            setTextColor(ContextCompat.getColor(context, R.color.nadi_soft_ink))
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6.dp()
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20.dp(), 56.dp(), 20.dp(), 56.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(title)
            addView(hint)
        }
    }

    private fun messageBubble(message: ChatMessage, attachment: TransferItem?, isHost: Boolean): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (isHost) Gravity.END else Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
            }
        }

        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = chatBubbleBackground(isHost)
            elevation = 1.dp().toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isHost) Gravity.END else Gravity.START
            }
        }
        val bubbleTextColor = ContextCompat.getColor(
            context,
            if (isHost) R.color.white else R.color.nadi_graphite
        )
        val bubbleMetaColor = ContextCompat.getColor(
            context,
            if (isHost) R.color.nadi_chat_meta_on_outgoing else R.color.nadi_soft_ink
        )

        if (!isHost) {
            bubble.addView(TextView(context).apply {
                text = message.senderName
                setTextColor(ContextCompat.getColor(context, R.color.nadi_green))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                maxWidth = chatBubbleMaxWidth()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(12.dp(), 8.dp(), 12.dp(), 2.dp())
                }
            })
        }

        val isImage = attachment?.isPreviewableImage() == true
        val imageUri = if (isImage && attachment?.status != TransferStatus.EXPIRED) attachment.previewUri() else null
        if (imageUri != null && attachment != null) {
            bubble.addView(imageAttachmentCard(attachment, imageUri, isHost))
        }

        if (message.text.isNotBlank()) {
            bubble.addView(TextView(context).apply {
                text = message.text
                setTextColor(bubbleTextColor)
                textSize = 15f
                maxWidth = chatBubbleMaxWidth()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    val top = if (isImage) 4.dp() else if (isHost) 8.dp() else 2.dp()
                    setMargins(12.dp(), top, 12.dp(), 2.dp())
                }
            })
        }

        val hasFile = !message.attachmentFileName.isNullOrBlank()
        if (hasFile && imageUri == null) {
            bubble.addView(fileAttachmentCard(message, attachment))
        }

        bubble.addView(TextView(context).apply {
            text = chatTimeFormat.format(Date(message.createdAt))
            setTextColor(bubbleMetaColor)
            textSize = 10f
            gravity = Gravity.END
            maxWidth = chatBubbleMaxWidth()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                val top = if (hasFile && imageUri == null) 2.dp() else 4.dp()
                setMargins(12.dp(), top, 12.dp(), 6.dp())
            }
        })

        row.addView(bubble)
        return row
    }

    private fun imageAttachmentCard(attachment: TransferItem, imageUri: Uri, isHost: Boolean): View {
        return MaterialCardView(context).apply {
            radius = 12.dp().toFloat()
            elevation = 0f
            strokeWidth = 1.dp()
            strokeColor = android.graphics.Color.parseColor(if (isHost) "#C0E8AA" else "#E5E5E5")
            setCardBackgroundColor(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT))
            clipToOutline = true
            isClickable = true
            isFocusable = true
            foreground = selectableItemBackground()
            setOnClickListener { onPreviewImage(attachment) }
            layoutParams = LinearLayout.LayoutParams(
                chatBubbleMaxWidth(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            }
            addView(ImageView(context).apply {
                adjustViewBounds = true
                maxHeight = 240.dp()
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = attachment.fileName
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                runCatching { setImageURI(imageUri) }
            })
        }
    }

    private fun fileAttachmentCard(message: ChatMessage, attachment: TransferItem?): View {
        val card = MaterialCardView(context).apply {
            radius = 8.dp().toFloat()
            elevation = 0f
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(context, R.color.nadi_line)
            setCardBackgroundColor(
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.nadi_surface))
            )
            layoutParams = LinearLayout.LayoutParams(
                chatBubbleMaxWidth(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(6.dp(), 6.dp(), 6.dp(), 4.dp())
            }
        }

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val fileIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_document)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.nadi_green))
            layoutParams = LinearLayout.LayoutParams(32.dp(), 32.dp()).apply {
                gravity = Gravity.CENTER_VERTICAL
                rightMargin = 8.dp()
            }
        }

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val fileNameText = TextView(context).apply {
            text = message.attachmentFileName
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.nadi_graphite))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val fileMetaText = TextView(context).apply {
            val size = attachment?.let { FileSizeFormatter.format(it.sizeBytes) }.orEmpty()
            val extension = message.attachmentFileName.orEmpty()
                .substringAfterLast('.', missingDelimiterValue = "")
                .uppercase()
            val status = attachment?.status?.label() ?: message.attachmentStatus.statusLabel()
            text = listOf(extension, size, status).filter { it.isNotBlank() }.joinToString(" - ")
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.nadi_soft_ink))
        }

        textLayout.addView(fileNameText)
        textLayout.addView(fileMetaText)
        cardContent.addView(fileIcon)
        cardContent.addView(textLayout)
        card.addView(cardContent)

        if (attachment != null && attachment.status != TransferStatus.EXPIRED) {
            card.isClickable = true
            card.isFocusable = true
            card.foreground = selectableItemBackground()
            card.setOnClickListener { onOpenAttachment(attachment) }
        }
        return card
    }

    private fun TransferStatus.label(): String {
        return when (this) {
            TransferStatus.PENDING -> "Menunggu"
            TransferStatus.RUNNING -> "Berjalan"
            TransferStatus.SUCCESS -> "Tersedia"
            TransferStatus.DOWNLOADED -> "Diunduh"
            TransferStatus.EXPIRED -> "Kedaluwarsa"
            TransferStatus.FAILED -> "Gagal"
        }
    }

    private fun String.statusLabel(): String {
        return when (lowercase()) {
            "pending" -> "Menunggu"
            "running" -> "Berjalan"
            "success" -> "Tersedia"
            "downloaded" -> "Diunduh"
            "expired" -> "Kedaluwarsa"
            "failed" -> "Gagal"
            else -> ""
        }
    }

    private fun ChatMessage.renderSignature(): String {
        val transferStatus = attachmentTransferId?.let(attachmentProvider)?.status?.name.orEmpty()
        return listOf(
            text,
            attachmentTransferId.orEmpty(),
            attachmentFileName.orEmpty(),
            attachmentStatus,
            transferStatus
        ).joinToString("|")
    }

    private fun TransferItem.isPreviewableImage(): Boolean {
        return mimeType.orEmpty().lowercase().startsWith("image/") || fileName.isImageFileName()
    }

    private fun TransferItem.previewUri(): Uri? {
        val value = localUri?.takeIf { it.isNotBlank() } ?: return null
        return if (value.startsWith("content://") || value.startsWith("file://")) {
            Uri.parse(value)
        } else {
            Uri.fromFile(File(value))
        }
    }

    private fun String.isImageFileName(): Boolean {
        return substringAfterLast('.', missingDelimiterValue = "").lowercase() in IMAGE_ATTACHMENT_EXTENSIONS
    }

    private fun selectableItemBackground(): android.graphics.drawable.Drawable? {
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return ContextCompat.getDrawable(context, outValue.resourceId)
    }

    private fun chatBubbleBackground(isHost: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            val radius = 16.dp().toFloat()
            val smallRadius = 4.dp().toFloat()
            if (isHost) {
                cornerRadii = floatArrayOf(radius, radius, radius, radius, smallRadius, smallRadius, radius, radius)
                setColor(ContextCompat.getColor(context, R.color.nadi_chat_outgoing))
            } else {
                cornerRadii = floatArrayOf(radius, radius, radius, radius, radius, radius, smallRadius, smallRadius)
                setColor(ContextCompat.getColor(context, R.color.nadi_chat_incoming))
                setStroke(1.dp(), ContextCompat.getColor(context, R.color.nadi_line))
            }
        }
    }

    private fun chatBubbleMaxWidth(): Int {
        return (context.resources.displayMetrics.widthPixels * 0.72f).toInt()
    }

    private fun Int.dp(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}

private val IMAGE_ATTACHMENT_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
