package uz.zero.appsupport.services

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uz.zero.appsupport.*
import java.time.LocalDateTime
import java.util.*

interface ChatService {
    fun startSupportRequest(user: User): String
    fun handleIncomingMessage(sender: User, text: String, telegramMsgId: Long): Long?
    fun endChat(user: User): Map<String, Any?>
    fun tryConnectWaitingUsers()
    fun connectToOperator(user: User, firstMessage: String): Long?
    fun connectSpecificOperatorWithQueue(operator: User, langCodes: List<LanguageCode>): Chat?
    fun getActiveChat(user: User): Chat?
    fun checkWaitingQueueAndConnect(operator: User, opKnownLanguages: List<LanguageCode>): Chat?
}

@Service
class ChatServiceImpl(
    private val chatRepository: ChatRepository,
    private val waitingUserRepository: WaitingUserRepository,
    private val operatorStatusRepository: OperatorStatusRepository,
    private val messageRepository: MessageRepository,
    private val operatorLanguageRepository: OperatorLanguageRepository,
) : ChatService {

    @Transactional
    override fun startSupportRequest(user: User): String {

        val hasActiveChat = chatRepository.existsByUserAndStatusInAndDeletedFalse(
            user, listOf(ChatStatus.ACTIVE, ChatStatus.WAITING)
        )
        if (hasActiveChat) return "Sizda allaqachon faol so'rov mavjud."

        return "Iltimos, muammoyingizni batafsil yozib qoldiring. Operatorlarimiz tez orada javob berishadi."
    }

    @Transactional
    override fun checkWaitingQueueAndConnect(operator: User, opKnownLanguages: List<LanguageCode>): Chat? {

        val chatOptional = chatRepository.findFirstWaitingChatByLanguages(
            ChatStatus.WAITING,
            opKnownLanguages
        )

        if (chatOptional.isEmpty) {
            return null
        }

        val chat = chatOptional.get()


        chat.operator = operator
        chat.status = ChatStatus.ACTIVE


        operatorStatusRepository.findByOperator(operator)?.let {
            it.status = OperatorState.BUSY
            operatorStatusRepository.save(it)
        }

        return chatRepository.save(chat)
    }

    @Transactional
    override fun endChat(user: User): Map<String, Any?> {

        val chat = chatRepository.findActiveChatByParticipant(user).orElse(null)
            ?: return emptyMap()


        chat.status = ChatStatus.ENDED
        chat.endedAt = Date()
        chatRepository.save(chat)


        operatorStatusRepository.findByOperator(chat.operator)?.let {
            it.status = OperatorState.ONLINE
            it.modifiedDate = Date()
            operatorStatusRepository.save(it)
        }


        val receiverTelegramId = if (user.id == chat.user.id) {
            chat.operator.telegramId
        } else {
            chat.user.telegramId
        }

        return mapOf(
            "chat" to chat,
            "chatId" to chat.id,
            "userTelegramId" to chat.user.telegramId,
            "operatorTelegramId" to chat.operator.telegramId,
            "receiverTelegramId" to receiverTelegramId
        )
    }

    @Transactional
    override fun handleIncomingMessage(sender: User, text: String, telegramMsgId: Long): Long? {

        val chat = chatRepository.findActiveChatByParticipant(sender).orElse(null) ?: return null

        messageRepository.save(
            Message(
                session = chat,
                sender = sender,
                content = text,
                telegramMessageId = telegramMsgId,
                messageType = MessageType.TEXT
            )
        )

        return if (sender.role == UserRole.USER) chat.operator.telegramId else chat.user.telegramId
    }

    @Transactional
    override fun connectToOperator(user: User, firstMessage: String): Long? {

        val userLanguage = user.selectedLanguages.firstOrNull() ?: return null


        val operatorStatus = operatorStatusRepository.findAvailableOperator(userLanguage.code)

        if (operatorStatus == null) {

            if (!waitingUserRepository.existsByUserAndDeletedFalse(user)) {
                waitingUserRepository.save(WaitingUser(user = user, language = userLanguage))
            }
            return null
        }


        chatRepository.save(
            Chat(
                user = user,
                operator = operatorStatus.operator,
                language = userLanguage,
                status = ChatStatus.ACTIVE,
                startedAt = Date()
            )
        )


        operatorStatus.status = OperatorState.BUSY
        operatorStatus.updatedAt = LocalDateTime.now()
        operatorStatusRepository.save(operatorStatus)

        return operatorStatus.operator.telegramId
    }

    @Transactional
    override fun connectSpecificOperatorWithQueue(operator: User, langCodes: List<LanguageCode>): Chat? {

        val chatOptional = chatRepository.findFirstWaitingChatByLanguages(
            ChatStatus.WAITING,
            langCodes
        )

        if (chatOptional.isEmpty) return null

        val chat = chatOptional.get()
        chat.operator = operator
        chat.status = ChatStatus.ACTIVE
        chat.startedAt = Date()


        operatorStatusRepository.findByOperator(operator)?.let {
            it.status = OperatorState.BUSY
            operatorStatusRepository.save(it)
        }

        return chatRepository.save(chat)
    }

    override fun tryConnectWaitingUsers() { /* Zarur bo'lsa implement qilinadi */
    }

    override fun getActiveChat(user: User): Chat? {


        return chatRepository.findActiveChatByParticipant(user).orElse(null)
    }


    fun getChatForUser(user: User): Chat? {
        return chatRepository.findFirstByUserAndStatusInAndDeletedFalse(
            user, listOf(ChatStatus.ACTIVE)
        )
    }
}