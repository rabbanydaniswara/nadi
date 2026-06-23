package com.danis.nadi.history

import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NadiHistoryStoreTest {
    @Test
    fun mergeKeepsTransfersFromPreviousRooms() {
        val previous = historyItem("old-room-file", createdAt = 1000L)
        val current = historyItem("new-room-file", createdAt = 2000L)

        val merged = mergeTransferHistory(
            existing = listOf(previous),
            incoming = listOf(current),
            limit = 20
        )

        assertEquals(listOf("new-room-file", "old-room-file"), merged.map { it.transferId })
    }

    @Test
    fun incomingTransferReplacesOlderStatusForSameId() {
        val existing = historyItem("same-file", createdAt = 1000L, status = TransferStatus.RUNNING)
        val incoming = historyItem("same-file", createdAt = 1000L, status = TransferStatus.SUCCESS)

        val merged = mergeTransferHistory(
            existing = listOf(existing),
            incoming = listOf(incoming),
            limit = 20
        )

        assertEquals(1, merged.size)
        assertEquals(TransferStatus.SUCCESS, merged.single().status)
    }

    private fun historyItem(
        id: String,
        createdAt: Long,
        status: TransferStatus = TransferStatus.SUCCESS
    ): TransferHistoryItem {
        return TransferHistoryItem(
            transferId = id,
            fileName = "$id.txt",
            sizeBytes = 10L,
            direction = TransferDirection.UPLOAD,
            status = status,
            progress = if (status == TransferStatus.SUCCESS) 100 else 50,
            createdAt = createdAt,
            senderName = "Tester"
        )
    }
}
