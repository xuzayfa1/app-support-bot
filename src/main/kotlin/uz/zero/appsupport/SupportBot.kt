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
import uz.zero.appsupport.services.*

@Component
class SupportBot(
    @Value("\${bot.token}") private val botToken: String,
    private val userService: UserService,
    private val ratingService: RatingService,
    private val chatService: ChatService,
    private val operatorService: OperatorService,
    private val keyboardService: KeyboardService,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val operatorStatusRepository: OperatorStatusRepository,
    private val operatorLanguageRepository: OperatorLanguageRepository,
    private val operatorStatisticsRepository: OperatorStatisticsRepository,
    private val chatRatingRepository: ChatRatingRepository
) : SpringLongPollingBot {

    private val telegramClient: TelegramClient = OkHttpTelegramClient(botToken)
    override fun getBotToken(): String = botToken
    private val tempSelectedLangs = mutableMapOf<Long, MutableSet<String>>()

    override fun getUpdatesConsumer() = LongPollingUpdateConsumer { updates ->
        updates.forEach { update ->
            try {
                if (update.hasCallbackQuery()) handleCallback(update)
                else if (update.hasMessage()) handleUpdate(update)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleUpdate(update: Update) {
        val msg = update.message ?: return
        val chatId = msg.chatId
        val user = userService.getOrCreateUser(msg.from)
        val incomingText = msg.text ?: ""


        if (user.role == UserRole.USER && msg.hasContact()) {
            userService.updatePhoneNumber(user, msg.contact.phoneNumber)
            send(chatId, "Rahmat! Tilni tanlang:", keyboardService.languageSelectionMenu(emptySet()))
            return
        }


        if (user.role == UserRole.USER && (user.phoneNumber == null || user.phoneNumber!!.startsWith("temp_"))) {
            send(chatId, "Botdan foydalanish uchun telefon raqamingizni yuboring:", keyboardService.contactMenu())
            return
        }


        when (incomingText) {
            "/start" -> handleStartCommand(user, chatId)
            "/admin", "🏠 Admin panel" -> handleAdminPanelCommand(user, chatId)
            "🏆 Operatorlar reytingi" -> {
                handleOperatorStats(user, chatId); return
            }

            "💬 Oxirgi baholashlar" -> {
                handleRecentRatings(user, chatId); return
            }

            "🚀 Ishni boshlash (Online)", "🟢 Online bo'lish" -> handleGoOnline(user, chatId)
            "🏁 Ishni yakunlash (Offline)", "🔴 Offline bo'lish" -> handleGoOffline(user, chatId)
            "⏭ Keyingi mijoz" -> handleNextClient(user, chatId)
            "❌ Suhbatni yakunlash" -> handleEndChat(user, chatId)
            "🆘 Operatorga bog'lanish" -> handleConnectToOperator(user, chatId)
            else -> {

                handleDefaultMessage(user, msg, incomingText)
            }
        }

    }

    private fun handleDefaultMessage(
        user: User,
        msg: org.telegram.telegrambots.meta.api.objects.message.Message,
        text: String
    ) {
        val activeChat = chatRepository.findActiveChatByParticipant(user).orElse(null)
        if (activeChat != null) {
            handleChatMessage(user, msg, activeChat)
        } else if (user.role == UserRole.USER && text.isNotBlank() && !text.startsWith("/")) {

            val opId = chatService.connectToOperator(user, text)
            if (opId != null) {
                send(
                    opId,
                    "🔔 Yangi mijoz: ${user.firstName}\n💬 $text",
                    keyboardService.operatorMenu(OperatorState.BUSY, true)
                )
                send(user.telegramId, "✅ Operator ulandi.", keyboardService.closeChatMenu())
            } else {
                send(user.telegramId, "⏳ Hozirda barcha operatorlar band. Navbatga qo'shildingiz.")
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
        var replyToId: Int? = null

        if (msg.replyToMessage != null) {
            val orig = if (sender.role == UserRole.OPERATOR)
                messageRepository.findByOperatorMessageId(msg.replyToMessage.messageId.toLong())
            else
                messageRepository.findByUserMessageId(msg.replyToMessage.messageId.toLong())

            replyToId =
                if (sender.role == UserRole.OPERATOR) orig?.userMessageId?.toInt() else orig?.operatorMessageId?.toInt()
        }

        val copy = CopyMessage.builder()
            .chatId(receiverId.toString())
            .fromChatId(sender.telegramId.toString())
            .messageId(msg.messageId)
            .apply { if (replyToId != null) replyToMessageId(replyToId) }
            .build()

        try {
            val sent = telegramClient.execute(copy)
            messageRepository.save(
                Message(
                    session = activeChat,
                    sender = sender,
                    content = msg.text ?: "[Media]",
                    messageType = MessageType.TEXT,
                    userMessageId = if (sender.role == UserRole.USER) msg.messageId.toLong() else sent.messageId.toLong(),
                    operatorMessageId = if (sender.role == UserRole.OPERATOR) msg.messageId.toLong() else sent.messageId.toLong()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun send(chatId: Long, text: String, keyboard: ReplyKeyboard? = null) {
        val sm = SendMessage(chatId.toString(), text)
        sm.enableMarkdown(true)
        if (keyboard != null) sm.replyMarkup = keyboard
        try {
            telegramClient.execute(sm)
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

    private fun handleStartCommand(user: User, chatId: Long) {
        when (user.role) {
            UserRole.ADMIN -> send(chatId, "🔐 Salom Admin! Panelga xush kelibsiz.", keyboardService.adminMenu())
            UserRole.OPERATOR -> {
                val status = operatorStatusRepository.findByOperator(user)?.status ?: OperatorState.OFFLINE
                send(chatId, "🎧 Xush kelibsiz, Operator! Holat: $status", keyboardService.operatorMenu(status))
            }

            else -> send(
                chatId,
                "👋 Xush kelibsiz! Botdan foydalanish uchun quyidagilardan birini tanlang:",
                keyboardService.userMenu()
            )
        }
    }

    private fun handleAdminPanelCommand(user: User, chatId: Long) {
        if (user.role == UserRole.ADMIN) send(chatId, "⚙️ Admin boshqaruv paneli:", keyboardService.adminMenu())
    }


    private fun handleGoOnline(user: User, chatId: Long) {
        if (user.role == UserRole.OPERATOR) {
            val langs = operatorLanguageRepository.findAllByOperator(user).map { it.language.code.name }.toMutableSet()
            tempSelectedLangs[user.telegramId] = langs
            send(chatId, "🌐 Tillarni tasdiqlang:", keyboardService.operatorLanguageMenu(langs))
        }
    }

    private fun handleGoOffline(user: User, chatId: Long) {
        if (user.role == UserRole.OPERATOR) {
            operatorService.goOffline(user)
            send(chatId, "🔴 Offlinedasiz.", keyboardService.operatorMenu(OperatorState.OFFLINE))
        }
    }

    private fun handleNextClient(user: User, chatId: Long) {
        if (user.role == UserRole.OPERATOR) {
            val chat = chatService.checkWaitingQueueAndConnect(user)
            if (chat != null) {
                send(chatId, "🔔 Mijoz: ${chat.user.firstName}", keyboardService.operatorMenu(OperatorState.BUSY, true))
                send(chat.user.telegramId, "✅ Operator ulandi!", keyboardService.closeChatMenu())
            } else send(chatId, "⏳ Navbat bo'sh.", keyboardService.operatorMenu(OperatorState.ONLINE, false))
        }
    }

    private fun handleEndChat(user: User, chatId: Long) {
        val res = chatService.endChat(user)
        if (res.isEmpty()) return
        val cId = res["chatId"] as Long
        val uTid = res["userTelegramId"] as Long
        val oTid = res["operatorTelegramId"] as Long

        if (user.role == UserRole.OPERATOR) {
            send(oTid, "✅ Yakunlandi.", keyboardService.operatorMenu(OperatorState.ONLINE, false))
            send(uTid, "🏁 Baholang:", keyboardService.ratingMenu(cId))
        } else {
            send(uTid, "🏁 Rahmat!", keyboardService.ratingMenu(cId))
            send(oTid, "👤 Mijoz yakunladi.", keyboardService.operatorMenu(OperatorState.ONLINE, false))
        }
    }

    private fun handleConnectToOperator(user: User, chatId: Long) {
        if (user.role == UserRole.USER) {
            val opId = chatService.connectToOperator(user, "Yordam so'rovi")
            if (opId != null) {
                send(opId, "🔔 Yangi mijoz: ${user.firstName}", keyboardService.operatorMenu(OperatorState.BUSY, true))
                send(chatId, "✅ Operator ulandi.", keyboardService.closeChatMenu())
            } else send(chatId, "⏳ Navbatga qo'shildingiz.")
        }
    }


    private fun handleOperatorStats(user: User, chatId: Long) {
        if (user.role == UserRole.ADMIN) {
            val stats = operatorStatisticsRepository.findAllByOrderByAverageRatingDesc()
            if (stats.isEmpty()) {
                send(chatId, "📭 Hozircha statistikalar mavjud emas.")
                return
            }
            val sb = StringBuilder("🏆 **Operatorlar reytingi:**\n\n")
            stats.forEach { s ->
                sb.append(
                    "👤 ${s.operator.firstName}: ${
                        String.format(
                            "%.2f",
                            s.averageRating
                        )
                    } ⭐ (${s.ratingsCount} ta baho)\n"
                )
            }
            send(chatId, sb.toString())
        }
    }


    private fun handleRecentRatings(user: User, chatId: Long) {
        if (user.role == UserRole.ADMIN) {

            val ratings = chatRatingRepository.findTop10ByOrderByRatedAtDesc()
            if (ratings.isEmpty()) {
                send(chatId, "📭 Hozircha baholar mavjud emas.")
                return
            }
            val sb = StringBuilder("💬 **Oxirgi 10 ta baholash:**\n\n")
            ratings.forEach { r ->
                sb.append("⭐ ${r.rating} - ${r.operator.firstName}ga (${r.user.firstName} tomonidan)\n")
                if (!r.comment.isNullOrBlank()) sb.append("📝 Izoh: ${r.comment}\n")
                sb.append("────────────────\n")
            }
            send(chatId, sb.toString())
        }
    }

    private fun handleCallback(update: Update) {
        val cb = update.callbackQuery
        val data = cb.data
        val uid = cb.from.id
        val cid = cb.message.chatId
        val mid = cb.message.messageId
        val user = userService.findByTelegramId(uid) ?: return

        when {
            data.startsWith("RATE_") -> {
                val p = data.split("_")
                chatRepository.findById(p[1].toLong()).ifPresent {
                    ratingService.rateOperator(it, p[2].toInt(), null)
                    editMenu(cid, mid, InlineKeyboardMarkup.builder().keyboard(emptyList()).build())
                    send(cid, "Rahmat! (${p[2]} ⭐)", keyboardService.userMenu())
                }
            }

            data.startsWith("OP_LANG_") -> {
                val code = data.substringAfter("OP_LANG_")
                val sel = tempSelectedLangs.getOrPut(uid) { mutableSetOf() }
                if (!sel.remove(code)) sel.add(code)
                editMenu(cid, mid, keyboardService.operatorLanguageMenu(sel))
            }

            data == "OP_CONFIRM_LANG" -> {
                val sel = tempSelectedLangs[uid]
                if (sel.isNullOrEmpty()) {
                    send(cid, "⚠️ Tilni tanlang!")
                } else {
                    operatorService.updateOperatorLanguages(user, sel)
                    operatorService.goOnline(user)
                    val codes = sel.map { LanguageCode.valueOf(it.uppercase()) }
                    val active = chatService.connectSpecificOperatorWithQueue(user, codes)
                    editMenu(cid, mid, InlineKeyboardMarkup.builder().keyboard(emptyList()).build())
                    if (active != null) {
                        send(cid, "✅ Mijozga ulandingiz!", keyboardService.operatorMenu(OperatorState.BUSY, true))
                        send(active.user.telegramId, "🔔 Operator ulandi!", keyboardService.closeChatMenu())
                    } else send(cid, "✅ Onlinedasiz.", keyboardService.operatorMenu(OperatorState.ONLINE, false))
                    tempSelectedLangs.remove(uid)
                }
            }

            data.startsWith("LANG_") -> {
                val code = data.substringAfter("LANG_")
                val sel = tempSelectedLangs.getOrPut(uid) { mutableSetOf() }
                if (!sel.remove(code)) sel.add(code)
                editMenu(cid, mid, keyboardService.languageSelectionMenu(sel))
            }

            data == "CONFIRM_LANG" -> {
                val sel = tempSelectedLangs[uid]
                if (sel.isNullOrEmpty()) send(cid, "⚠️ Tilni tanlang!") else {
                    editMenu(cid, mid, InlineKeyboardMarkup.builder().keyboard(emptyList()).build())
                    userService.saveUserLanguages(uid, sel)
                    tempSelectedLangs.remove(uid)
                    send(cid, "Tayyor!", keyboardService.userMenu())
                }
            }
        }
    }
}