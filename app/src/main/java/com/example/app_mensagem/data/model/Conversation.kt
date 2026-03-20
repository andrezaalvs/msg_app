package com.example.app_mensagem.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.PropertyName

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val profilePictureUrl: String? = null,
    val lastMessage: String = "",
    val lastSenderId: String = "",
    val timestamp: Long = 0L,
    val pinnedMessageId: String? = null,
    val isMuted: Boolean = false, // Silenciar notificações
    val isHighPriority: Boolean = false, // Notificação em prioridade alta
    val vibrationEnabled: Boolean = true, // Vibração ligada para esta conversa

    @get:PropertyName("isGroup")
    val isGroup: Boolean = false
)