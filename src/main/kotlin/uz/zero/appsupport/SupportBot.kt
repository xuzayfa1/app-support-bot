package uz.zero.appsupport

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot
import org.telegram.telegrambots.meta.api.methods.CopyMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.generics.TelegramClient
import uz.zero.appsupport.services.ChatService
import uz.zero.appsupport.services.KeyboardService
import uz.zero.appsupport.services.OperatorService
import uz.zero.appsupport.services.UserService

@Component
class SupportBot(
    @Value("\${bot.token}") private val botToken: String,
    private val userService: UserService,
    private val chatService: ChatService,
    private val operatorService: OperatorService,
    private val keyboardService: KeyboardService,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val operatorStatusRepository: OperatorStatusRepository,
    private val operatorLanguageRepository: OperatorLanguageRepository
) : SpringLongPollingBot {

    private val telegramClient: TelegramClient = OkHttpTelegramClient(botToken)

    override fun getBotToken(): String = botToken

    private val tempSelectedLangs = mutableMapOf<Long, MutableSet<String>>()

    override fun getUpdatesConsumer() = LongPollingUpdateConsumer { updates ->
        updates.forEach { update ->
            if (update.hasCallbackQuery()) {
                handleCallback(update)
            } else if (update.hasMessage()) {
                handleUpdate(update)
            }
        }
    }

    private fun handleCallback(update: Update) {
        val callback = update.callbackQuery
        val data = callback.data
        val userId = callback.from.id
        val chatId = callback.message.chatId
        val messageId = callback.message.messageId

        val user = userService.findByTelegramId(userId) ?: return

        try {
            when {

                data.startsWith("OP_LANG_") -> {
                    val code = data.substringAfter("OP_LANG_")
                    val selected = tempSelectedLangs.getOrPut(userId) { mutableSetOf() }

                    if (selected.contains(code)) selected.remove(code) else selected.add(code)


                    editMenu(chatId, messageId, keyboardService.operatorLanguageMenu(selected))
                }


                data == "OP_CONFIRM_LANG" -> {
                    val selectedStrings = tempSelectedLangs[userId]

                    if (selectedStrings.isNullOrEmpty()) {
                        send(chatId, "⚠️ Iltimos, kamida bitta tilni tanlang!")
                    } else {

                        operatorService.updateOperatorLanguages(user, selectedStrings)

                        operatorService.goOnline(user)


                        val langCodes = selectedStrings.mapNotNull { str ->
                            try {
                                LanguageCode.valueOf(str.uppercase().trim())
                            } catch (e: Exception) {
                                null
                            }
                        }


                        val activeChat = chatService.connectSpecificOperatorWithQueue(user, langCodes)


                        editMenu(chatId, messageId, InlineKeyboardMarkup.builder().keyboard(emptyList()).build())

                        if (activeChat != null) {
                            send(
                                chatId, "✅ Onlinedasiz va yangi mijozga ulandingiz!",
                                keyboardService.operatorMenu(OperatorState.BUSY, true)
                            )
                            send(activeChat.user.telegramId, "🔔 Operator ulandi!", keyboardService.closeChatMenu())
                        } else {
                            send(
                                chatId, "✅ Onlinedasiz. Hozircha navbat bo'sh (mijoz kutilyapti).",
                                keyboardService.operatorMenu(OperatorState.ONLINE, false)
                            )
                        }


                        tempSelectedLangs.remove(userId)
                    }
                }


                data.startsWith("LANG_") -> {
                    val code = data.substringAfter("LANG_")
                    val selected = tempSelectedLangs.getOrPut(userId) { mutableSetOf() }

                    if (selected.contains(code)) selected.remove(code) else selected.add(code)

                    editMenu(chatId, messageId, keyboardService.languageSelectionMenu(selected))
                }


                data == "CONFIRM_LANG" -> {
                    val selected = tempSelectedLangs[userId]
                    if (selected.isNullOrEmpty()) {
                        send(chatId, "⚠️ Iltimos, muloqot tilini tanlang!")
                    } else {

                        editMenu(chatId, messageId, InlineKeyboardMarkup.builder().keyboard(emptyList()).build())

                        userService.saveUserLanguages(userId, selected)
                        tempSelectedLangs.remove(userId)

                        send(
                            chatId, "Muvaffaqiyatli saqlandi! Endi operatorga bog'lanish tugmasini bosishingiz mumkin.",
                            keyboardService.userMenu()
                        )
                    }
                }
            }


        } catch (e: Exception) {
            e.printStackTrace()
            send(chatId, "⚠️ Xatolik yuz berdi: ${e.message}")
        }
    }


    private fun handleUpdate(update: Update) {
        val msg = update.message ?: return
        val chatId = msg.chatId
        val user = userService.getOrCreateUser(msg.from)
        val incomingText = msg.text ?: ""


        if (user.role == UserRole.USER) {
            if (msg.hasContact()) {
                userService.updatePhoneNumber(user, msg.contact.phoneNumber)
                send(
                    chatId,
                    "Ajoyib! Endi xizmat ko'rsatish tilini tanlang:",
                    keyboardService.languageSelectionMenu(emptySet())
                )
                return
            }
            if (user.phoneNumber == null || user.phoneNumber.startsWith("temp_")) {
                send(
                    chatId,
                    "Botdan foydalanish uchun avval telefon raqamingizni yuboring:",
                    keyboardService.contactMenu()
                )
                return
            }
        }


        when (incomingText) {
            "/start" -> {
                if (user.role == UserRole.OPERATOR) {
                    val status = operatorStatusRepository.findByOperator(user)?.status ?: OperatorState.OFFLINE
                    send(chatId, "Xush kelibsiz, Operator! Holatingiz: $status", keyboardService.operatorMenu(status))
                } else {
                    send(chatId, "Xush kelibsiz!", keyboardService.userMenu())
                }
                return
            }


            "🚀 Ishni boshlash (Online)", "🟢 Online bo'lish" -> {
                if (user.role == UserRole.OPERATOR) {

                    val operatorLangs = operatorLanguageRepository.findAllByOperator(user)
                        .map { it.language.code.name }.toMutableSet()


                    tempSelectedLangs[user.telegramId] = operatorLangs

                    send(
                        chatId, "Qaysi tillarda xizmat ko'rsatasiz? Tillarni belgilab 'Tasdiqlash' tugmasini bosing:",
                        keyboardService.operatorLanguageMenu(operatorLangs)
                    )
                }
                return
            }


            "🌐 Tilni o'zgartirish" -> {
                if (user.role == UserRole.OPERATOR) {
                    val operatorLangs = operatorLanguageRepository.findAllByOperator(user)
                        .map { it.language.code.name }.toMutableSet()
                    tempSelectedLangs[user.telegramId] = operatorLangs
                    send(
                        chatId,
                        "Xizmat ko'rsatish tillarini tahrirlang:",
                        keyboardService.operatorLanguageMenu(operatorLangs)
                    )
                } else {
                    send(chatId, "Muloqot tilini tanlang:", keyboardService.languageSelectionMenu(emptySet()))
                }
                return
            }


            "🏁 Ishni yakunlash (Offline)", "🔴 Offline bo'lish" -> {
                operatorService.goOffline(user)
                send(chatId, "Siz offlinedasiz (tanaffus).", keyboardService.operatorMenu(OperatorState.OFFLINE))
                return
            }


            "⏭ Keyingi mijoz" -> {
                val chat = chatService.checkWaitingQueueAndConnect(user)
                if (chat != null) {
                    send(
                        chatId, "🔔 **Navbatdagi mijoz:** ${chat.user.firstName}",
                        keyboardService.operatorMenu(OperatorState.BUSY, true)
                    )
                    send(chat.user.telegramId, "✅ Operator ulandi!", keyboardService.closeChatMenu())
                } else {
                    send(chatId, "Hozircha navbat bo'sh.", keyboardService.operatorMenu(OperatorState.ONLINE, false))
                }
                return
            }


            "❌ Suhbatni yakunlash" -> {
                val receiverId = chatService.endChat(user)
                if (user.role == UserRole.OPERATOR) {
                    send(
                        chatId, "Suhbat yakunlandi ✅. Yangi mijoz olish uchun 'Keyingi mijoz'ni bosing.",
                        keyboardService.operatorMenu(OperatorState.ONLINE, false)
                    )
                    if (receiverId != null) {
                        send(receiverId, "Suhbat yakunlandi. Rahmat!", keyboardService.userMenu())
                    }
                } else {
                    send(chatId, "Suhbat yakunlandi. Rahmat!", keyboardService.userMenu())
                    if (receiverId != null) {
                        send(
                            receiverId,
                            "Mijoz suhbatni yakunladi.",
                            keyboardService.operatorMenu(OperatorState.ONLINE, false)
                        )
                    }
                }
                return
            }


            "🆘 Operatorga bog'lanish" -> {
                if (user.role == UserRole.USER) {
                    val connectedOpId = chatService.connectToOperator(user, "🆘 Yordam so'rovi")
                    if (connectedOpId != null) {
                        send(
                            connectedOpId, "🔔 **Yangi mijoz:** ${user.firstName}",
                            keyboardService.operatorMenu(OperatorState.BUSY, true)
                        )
                        send(chatId, "✅ Operator ulandi!", keyboardService.closeChatMenu())
                    } else {
                        send(chatId, "⏳ Hozirda operatorlar band. Navbatga qo'shildingiz.")
                    }
                }
                return
            }
        }


        val activeChat = chatRepository.findActiveChatByParticipant(user).orElse(null)
        if (activeChat != null) {

            handleChatMessage(user, msg, activeChat)
        } else if (user.role == UserRole.USER && incomingText.isNotBlank()) {

            val connectedOpId = chatService.connectToOperator(user, incomingText)
            if (connectedOpId != null) {
                send(
                    connectedOpId,
                    "🔔 **Yangi mijoz:** ${user.firstName}\n💬 $incomingText",
                    keyboardService.operatorMenu(OperatorState.BUSY, true)
                )
                send(chatId, "✅ Operator ulandi.", keyboardService.closeChatMenu())
            }
        }
    }

    private fun handleChatMessage(
        sender: User,
        msg: org.telegram.telegrambots.meta.api.objects.message.Message,
        activeChat: Chat
    ) {
        val receiverId =
            if (activeChat.user.id == sender.id) activeChat.operator.telegramId else activeChat.user.telegramId


        var replyToIdOnReceiverSide: Int? = null
        if (msg.replyToMessage != null) {
            val originalMsg = if (sender.role == UserRole.OPERATOR) {
                messageRepository.findByOperatorMessageId(msg.replyToMessage.messageId.toLong())
            } else {
                messageRepository.findByUserMessageId(msg.replyToMessage.messageId.toLong())
            }

            replyToIdOnReceiverSide = if (sender.role == UserRole.OPERATOR) {
                originalMsg?.userMessageId?.toInt()
            } else {
                originalMsg?.operatorMessageId?.toInt()
            }
        }


        val copy = CopyMessage.builder()
            .chatId(receiverId.toString())
            .fromChatId(sender.telegramId.toString())
            .messageId(msg.messageId)
            .apply {
                if (replyToIdOnReceiverSide != null) {
                    replyToMessageId(replyToIdOnReceiverSide)
                }
            }
            .build()

        try {
            val sentMsg = telegramClient.execute(copy)


            messageRepository.save(
                Message(
                    session = activeChat,
                    sender = sender,
                    content = msg.text ?: "[Media]",
                    messageType = MessageType.TEXT,
                    userMessageId = if (sender.role == UserRole.USER) msg.messageId.toLong() else sentMsg.messageId.toLong(),
                    operatorMessageId = if (sender.role == UserRole.OPERATOR) msg.messageId.toLong() else sentMsg.messageId.toLong()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun forwardOldMessages(chat: Chat) {
        val oldMessages = messageRepository.findAllBySenderAndSessionIsNull(chat.user)
        oldMessages.forEach { msg ->
            msg.telegramMessageId?.let { msgId ->
                copyMessage(chat.operator.telegramId, chat.user.telegramId, msgId.toInt())
                msg.session = chat
                messageRepository.save(msg)
            }
        }
    }

    private fun send(chatId: Long, text: String, keyboard: ReplyKeyboard? = null) {
        val sm = SendMessage(chatId.toString(), text)
        if (keyboard != null) sm.replyMarkup = keyboard
        try {
            telegramClient.execute(sm)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyMessage(toChatId: Long, fromChatId: Long, messageId: Int) {
        val copy = CopyMessage.builder()
            .chatId(toChatId.toString())
            .fromChatId(fromChatId.toString())
            .messageId(messageId)
            .build()
        try {
            telegramClient.execute(copy)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun editMenu(chatId: Long, messageId: Int, keyboard: InlineKeyboardMarkup) {
        val edit = EditMessageReplyMarkup.builder()
            .chatId(chatId.toString())
            .messageId(messageId)
            .replyMarkup(keyboard)
            .build()
        try {
            telegramClient.execute(edit)
        } catch (e: Exception) {
        }
    }
}