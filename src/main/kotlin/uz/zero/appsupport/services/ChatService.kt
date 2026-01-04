package uz.zero.appsupport.services

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uz.zero.appsupport.*
import java.util.*

interface ChatService {
    fun startSupportRequest(user: User): String
    fun handleIncomingMessage(sender: User, text: String, telegramMsgId: Long): Long?
    fun endChat(user: User): Map<String, Any?>
    fun tryConnectWaitingUsers()
    fun connectToOperator(user: User, firstMessage: String): Long?
    fun checkWaitingQueueAndConnect(operator: User): Chat?
    fun connectSpecificOperatorWithQueue(operator: User, langCodes: List<LanguageCode>): Chat?
}

@Service
class ChatServiceImpl(
    private val chatRepository: ChatRepository,
    private val waitingUserRepository: WaitingUserRepository,
    private val operatorStatusRepository: OperatorStatusRepository,
    private val messageRepository: MessageRepository,
    private val operatorLanguageRepository: OperatorLanguageRepository
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
    override fun checkWaitingQueueAndConnect(operator: User): Chat? {

        val operatorLangCodes = operatorLanguageRepository.findAllByOperator(operator)
            .map { it.language.code }


        if (operatorLangCodes.isEmpty()) return null


        val waitingUser = waitingUserRepository.findFirstInQueue(operatorLangCodes).orElse(null)
            ?: return null


        val chat = chatRepository.save(
            Chat(
                user = waitingUser.user,
                operator = operator,
                language = waitingUser.language,
                status = ChatStatus.ACTIVE,
                startedAt = Date()
            )
        )


        operatorStatusRepository.findByOperator(operator)?.let {
            it.status = OperatorState.BUSY
            it.modifiedDate = Date()
            operatorStatusRepository.save(it)
        }

        waitingUser.deleted = true
        waitingUserRepository.save(waitingUser)

        return chat
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
        val userLang = user.selectedLanguage ?: return null
        val operatorStatus = operatorStatusRepository.findAvailableOperator(userLang.code)

        if (operatorStatus == null) {
            if (!waitingUserRepository.existsByUserAndDeletedFalse(user)) {
                waitingUserRepository.save(WaitingUser(user = user, language = userLang))
            }
            return null
        }

        val chat = chatRepository.save(
            Chat(
                user = user,
                operator = operatorStatus.operator,
                language = userLang,
                status = ChatStatus.ACTIVE,
                startedAt = Date()
            )
        )

        operatorStatus.status = OperatorState.BUSY
        operatorStatusRepository.save(operatorStatus)

        return operatorStatus.operator.telegramId
    }

    @Transactional
    override fun connectSpecificOperatorWithQueue(operator: User, langCodes: List<LanguageCode>): Chat? {
        if (langCodes.isEmpty()) return null


        val waitingUser = waitingUserRepository.findFirstInQueue(langCodes).orElse(null)
            ?: return null


        val chat = chatRepository.save(
            Chat(
                user = waitingUser.user,
                operator = operator,
                language = waitingUser.language,
                status = ChatStatus.ACTIVE,
                startedAt = Date()
            )
        )


        operatorStatusRepository.findByOperator(operator)?.let {
            it.status = OperatorState.BUSY
            operatorStatusRepository.save(it)
        }


        waitingUser.deleted = true
        waitingUserRepository.save(waitingUser)

        return chat
    }

    override fun tryConnectWaitingUsers() { /* Zarur bo'lsa implement qilinadi */
    }
}