package uz.zero.appsupport

import java.util.*

data class UserDto(
    val telegramId: Long,
    val firstName: String?,
    val lastName: String?,
    val username: String?,
    val phoneNumber: String?,
    val role: UserRole,
    val languageCode: String
)

data class ChatSessionDto(
    val chatId: Long,
    val userTelegramId: Long,
    val userName: String,
    val operatorTelegramId: Long?,
    val status: String,
    val startedAt: Date
)

data class MessageDto(
    val senderId: Long,
    val senderName: String,
    val content: String,
    val timestamp: Date,
    val isFromOperator: Boolean
)

data class OperatorStatusDto(
    val operatorId: Long,
    val fullName: String,
    val state: String,
    val activeChatsCount: Int
)