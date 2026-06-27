package com.danis.nadi

import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.model.TransferItem

fun MainActivity.renderTransferList(container: LinearLayout, items: List<TransferItem>, emptyText: String) {
    container.removeAllViews()
    if (items.isEmpty()) {
        container.addView(transferEmptyState(emptyText))
        return
    }
    items.forEachIndexed { index, item ->
        container.addView(transferRow(item, index > 0))
    }
}

fun MainActivity.transferEmptyState(text: String): View {
    val activity = this
    val card = com.google.android.material.card.MaterialCardView(activity).apply {
        radius = 8.dp().toFloat()
        elevation = 0f
        strokeWidth = 1.dp()
        strokeColor = ContextCompat.getColor(activity, R.color.nadi_line)
        setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.nadi_mist)
        ))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    card.addView(TextView(activity).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(activity, R.color.nadi_soft_ink))
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(14.dp(), 18.dp(), 14.dp(), 18.dp())
    })
    return card
}

fun MainActivity.transferRow(item: TransferItem, hasTopMargin: Boolean): View {
    val activity = this
    val card = com.google.android.material.card.MaterialCardView(activity).apply {
        radius = 8.dp().toFloat()
        elevation = 0f
        strokeWidth = 1.dp()
        strokeColor = ContextCompat.getColor(activity, R.color.nadi_line)
        setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.nadi_mist)
        ))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            if (hasTopMargin) topMargin = 8.dp()
        }
    }
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
    val title = TextView(activity).apply {
        text = item.fileName
        setTextColor(ContextCompat.getColor(activity, R.color.nadi_graphite))
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val meta = TextView(activity).apply {
        text = listOf(
            item.direction.label(),
            FileSizeFormatter.format(item.sizeBytes),
            item.status.label(item.progress)
        ).joinToString(" - ")
        setTextColor(ContextCompat.getColor(activity, R.color.nadi_soft_ink))
        textSize = 12f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 2.dp()
        }
    }
    val sender = item.senderName?.takeIf { it.isNotBlank() }?.let { senderName ->
        TextView(activity).apply {
            text = senderName
            setTextColor(ContextCompat.getColor(activity, R.color.nadi_green))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2.dp()
            }
        }
    }
    textColumn.addView(title)
    textColumn.addView(meta)
    sender?.let(textColumn::addView)
    row.addView(icon)
    row.addView(textColumn)
    card.addView(row)
    return card
}
