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


        if (incomingText == "/start") {
            tempSelectedLangs.remove(user.telegramId)
            send(
                chatId, "Assalomu alaykum! Iltimos, tilni tanlang:\nПожалуйста, выберите язык:",
                keyboardService.languageSelectionMenu(emptySet())
            )
            return
        }


        if (user.selectedLanguages.isEmpty()) {
            send(chatId, "Iltimos, davom etish uchun tilni tanlang!\nПожалуйста, выберите язык для продолжения!")
            return
        }

        val userLang = user.selectedLanguages.first().code


        if (user.role == UserRole.USER && (user.phoneNumber == null || user.phoneNumber!!.startsWith("temp_"))) {
            if (msg.hasContact()) {
                val contact = msg.contact
                if (contact.userId != user.telegramId) {
                    val errorMsg = if (userLang == LanguageCode.UZ)
                        "Faqat o'zingizning kontaktingizni yuboring! ⚠️"
                    else "Отправьте только свой контакт! ⚠️"
                    send(chatId, errorMsg)
                    return
                }
                userService.updatePhoneNumber(user, contact.phoneNumber)
                val successMsg =
                    if (userLang == LanguageCode.UZ) "Rahmat! Endi foydalanishingiz mumkin." else "Спасибо! Теперь вы можете использовать бот."
                send(chatId, successMsg, keyboardService.userMenu(userLang))
            } else {
                val askPhone = if (userLang == LanguageCode.UZ)
                    "Botdan foydalanish uchun telefon raqamingizni yuboring:"
                else "Для использования бота отправьте свой номер телефона:"
                send(chatId, askPhone, keyboardService.contactMenu(userLang))
            }
            return
        }


        when (incomingText) {
            "❌ Suhbatni yakunlash", "❌ Завершить чат" -> {
                handleEndChat(user, chatId)
                return
            }

            "🏠 Admin panel", "/admin" -> {
                handleAdminPanelCommand(user, chatId); return
            }
        }


        val activeChat = chatService.getActiveChat(user)
        if (activeChat != null) {
            handleChatMessage(user, msg, activeChat)
            return
        }



        when (incomingText) {

            "🏆 Operatorlar reytingi", "🏆 Рейтинг операторов" -> handleOperatorStats(user, chatId)
            "💬 Oxirgi baholashlar", "💬 Последние оценки" -> handleRecentRatings(user, chatId)


            "🚀 Ishni boshlash (Online)", "🚀 Начать работу (Online)",
            "🟢 Online bo'lish", "🟢 Стать Online" -> handleGoOnline(user, chatId)

            "🏁 Ishni yakunlash (Offline)", "🏁 Завершить работу (Offline)",
            "🔴 Offline bo'lish", "🔴 Стать Offline" -> handleGoOffline(user, chatId)

            "⏭ Keyingi mijoz", "⏭ Следующий клиент" -> handleNextClient(user, chatId)


            "🆘 Operatorga bog'lanish", "🆘 Связаться с оператором" -> handleConnectToOperator(user, chatId)
            "🌐 Tilni o'zgartirish", "🌐 Изменить язык" -> handleLanguageChange(user, chatId)

            else -> {

                val unknownMsg = if (userLang == LanguageCode.UZ)
                    "Iltimos, menyudagi tugmalardan foydalaning. 😊"
                else "Пожалуйста, используйте кнопки меню. 😊"

                when (user.role) {
                    UserRole.USER -> send(chatId, unknownMsg, keyboardService.userMenu(userLang))

                    UserRole.OPERATOR -> {
                        val status = operatorStatusRepository.findByOperator(user)?.status ?: OperatorState.OFFLINE
                        val hasActiveChat = chatService.getActiveChat(user) != null
                        send(chatId, unknownMsg, keyboardService.operatorMenu(status, userLang, hasActiveChat))
                    }

                    UserRole.ADMIN -> send(chatId, unknownMsg, keyboardService.adminMenu(userLang))
                }
            }
        }
    }


    private fun handleLanguageChange(user: User, chatId: Long) {

        val selectedCodes = user.selectedLanguages.map { it.code.name }.toSet()

        val text = if (selectedCodes.contains("UZ"))
            "Yangi tilni tanlang:"
        else "Выберите новый язык:"

        send(chatId, text, keyboardService.languageSelectionMenu(selectedCodes))
    }


    private fun handleChatMessage(
        sender: User,
        msg: org.telegram.telegrambots.meta.api.objects.message.Message,
        activeChat: Chat
    ) {

        val receiverId = if (sender.role == UserRole.USER) {
            activeChat.operator?.telegramId
        } else {
            activeChat.user.telegramId
        }


        if (receiverId == null) {
            val userLang = sender.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ
            val waitMsg = if (userLang == LanguageCode.UZ)
                "Operator ulanishini kuting..."
            else "Ожидайте подключения оператора..."
            send(sender.telegramId, waitMsg)
            return
        }


        var replyToId: Int? = null
        if (msg.replyToMessage != null) {
            val originalId = msg.replyToMessage.messageId.toLong()
            val orig = if (sender.role == UserRole.OPERATOR)
                messageRepository.findByOperatorMessageId(originalId)
            else
                messageRepository.findByUserMessageId(originalId)

            replyToId = if (sender.role == UserRole.OPERATOR)
                orig?.userMessageId?.toInt()
            else orig?.operatorMessageId?.toInt()
        }


        val copy = CopyMessage.builder()
            .chatId(receiverId.toString())
            .fromChatId(sender.telegramId.toString())
            .messageId(msg.messageId)
            .apply { if (replyToId != null) replyToMessageId(replyToId) }
            .build()

        try {
            val sentId = telegramClient.execute(copy)


            messageRepository.save(
                uz.zero.appsupport.Message(
                    session = activeChat,
                    sender = sender,
                    content = msg.text ?: "[Media]",
                    messageType = MessageType.TEXT,
                    userMessageId = if (sender.role == UserRole.USER) msg.messageId.toLong() else sentId.messageId.toLong(),
                    operatorMessageId = if (sender.role == UserRole.OPERATOR) msg.messageId.toLong() else sentId.messageId.toLong()
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

    private fun editMenu(chatId: Long, messageId: Int, keyboard: InlineKeyboardMarkup?) {
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

        val userLang = user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ

        when (user.role) {
            UserRole.ADMIN -> {
                send(chatId, "🔐 Salom Admin! Panelga xush kelibsiz.", keyboardService.adminMenu(userLang))
            }

            UserRole.OPERATOR -> {
                val status = operatorStatusRepository.findByOperator(user)?.status ?: OperatorState.OFFLINE
                val welcomeMsg = if (userLang == LanguageCode.UZ)
                    "🎧 Xush kelibsiz, Operator! Holat: $status"
                else "🎧 Добро пожаловать, Оператор! Статус: $status"

                val opLang = user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ
                send(
                    chatId,
                    welcomeMsg,
                    keyboardService.operatorMenu(status, opLang, false)
                )
            }

            else -> {

                val welcomeMsg = if (userLang == LanguageCode.UZ)
                    "👋 Xush kelibsiz! Botdan foydalanish uchun quyidagilardan birini tanlang:"
                else "👋 Добро пожаловать! Для использования бота выберите один из следующих вариантов:"

                send(chatId, welcomeMsg, keyboardService.userMenu(userLang))
            }
        }
    }

    private fun handleAdminPanelCommand(user: User, chatId: Long) {
        val userLang = user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ
        if (user.role == UserRole.ADMIN) send(chatId, "⚙️ Admin boshqaruv paneli:", keyboardService.adminMenu(userLang))
    }


    private fun handleGoOnline(user: User, chatId: Long) {
        if (user.role == UserRole.OPERATOR) {

            val opKnownLanguages = operatorLanguageRepository.findAllByOperator(user)
                .map { it.language.code }

            val opLangCode = user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ

            if (opKnownLanguages.isNotEmpty()) {
                operatorService.goOnline(user)

                val activeChat = chatService.connectSpecificOperatorWithQueue(user, opKnownLanguages)

                if (activeChat != null) {
                    val opMsg = if (opLangCode == LanguageCode.UZ)
                        "✅ Online! Mijoz ulandi: ${activeChat.user.firstName}\n🌐 Til: ${activeChat.language.code}"
                    else "✅ Online! Клиент подключен: ${activeChat.user.firstName}\n🌐 Язык: ${activeChat.language.code}"

                    send(chatId, opMsg, keyboardService.operatorMenu(OperatorState.BUSY, opLangCode, true))
                } else {
                    val onlineMsg = if (opLangCode == LanguageCode.UZ)
                        "✅ Onlinedasiz. Mijozlar kutilmoqda..."
                    else "✅ Вы в сети. Ожидание клиентов..."

                    send(chatId, onlineMsg, keyboardService.operatorMenu(OperatorState.ONLINE, opLangCode, false))
                }
            } else {
                val askLangMsg = if (opLangCode == LanguageCode.UZ)
                    "⚠️ Sizda hali ishchi tillar sozlanmagan. Iltimos, tillarni tanlang:"
                else "⚠️ У вас еще не настроены рабочие языки. Пожалуйста, выберите языки:"


                send(chatId, askLangMsg, keyboardService.operatorLanguageMenu(emptySet()))
            }
        }
    }


    private fun handleGoOffline(user: User, chatId: Long) {
        if (user.role == UserRole.OPERATOR) {
            operatorService.goOffline(user)
            val opLang = user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ
            val msg = if (opLang == LanguageCode.UZ) "🔴 Offlinedasiz." else "🔴 Вы не в сети (Offline)."
            send(chatId, msg, keyboardService.operatorMenu(OperatorState.OFFLINE, opLang, false))
        }
    }

    private fun handleNextClient(user: User, chatId: Long) {
        if (user.role == UserRole.OPERATOR) {
            val opLang = user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ

            val opKnownLanguages = operatorLanguageRepository.findAllByOperator(user)
                .map { it.language.code }

            if (opKnownLanguages.isEmpty()) {
                val noLangMsg = if (opLang == LanguageCode.UZ)
                    "⚠️ Sizda tillar sozlanmagan! Sozlamalardan tillarni tanlang."
                else "⚠️ У вас не настроены языки! Выберите языки в настройках."
                send(chatId, noLangMsg)
                return
            }


            val chat = chatService.checkWaitingQueueAndConnect(user, opKnownLanguages)

            if (chat != null) {
                val opMsg = if (opLang == LanguageCode.UZ)
                    "🔔 Mijoz ulandi: ${chat.user.firstName}"
                else "🔔 Клиент подключен: ${chat.user.firstName}"

                send(chatId, opMsg, keyboardService.operatorMenu(OperatorState.BUSY, opLang, true))

                val uLang = chat.user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ
                val uMsg = if (uLang == LanguageCode.UZ) "✅ Operator ulandi!" else "✅ Оператор подключен!"
                send(chat.user.telegramId, uMsg, keyboardService.closeChatMenu(uLang))
            } else {
                val emptyMsg = if (opLang == LanguageCode.UZ)
                    "⏳ Sizning tillaringizda navbatda hech kim yo'q."
                else "⏳ В очереди никого нет на ваших языках."

                send(chatId, emptyMsg, keyboardService.operatorMenu(OperatorState.ONLINE, opLang, false))
            }
        }
    }

    private fun handleEndChat(user: User, chatId: Long) {
        val res = chatService.endChat(user)
        if (res.isEmpty()) return
        val cId = res["chatId"] as Long
        val uTid = res["userTelegramId"] as Long
        val oTid = res["operatorTelegramId"] as Long


        val operator = userService.findByTelegramId(oTid)
        val opLang = operator?.selectedLanguages?.firstOrNull()?.code ?: LanguageCode.UZ

        val client = userService.findByTelegramId(uTid)
        val clientLang = client?.selectedLanguages?.firstOrNull()?.code ?: LanguageCode.UZ

        val ratingMsg = if (clientLang == LanguageCode.UZ)
            "🏁 Suhbat yakunlandi iltimos operator xizmatini baholang:"
        else "🏁 Чат завершен, пожалуйста, оцените работу оператора:"

        if (user.role == UserRole.OPERATOR) {
            val opMsg = if (opLang == LanguageCode.UZ) "✅ Yakunlandi." else "✅ Завершено."
            send(oTid, opMsg, keyboardService.operatorMenu(OperatorState.ONLINE, opLang, false))
            send(uTid, ratingMsg, keyboardService.ratingMenu(cId))
        } else {
            val opFinishMsg = if (opLang == LanguageCode.UZ) "👤 Mijoz yakunladi." else "👤 Клиент завершил чат."
            send(uTid, ratingMsg, keyboardService.ratingMenu(cId))
            send(oTid, opFinishMsg, keyboardService.operatorMenu(OperatorState.ONLINE, opLang, false))
        }
    }

    private fun handleConnectToOperator(user: User, chatId: Long) {
        if (user.role == UserRole.USER) {
            val userLang = user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ


            val opId = chatService.connectToOperator(user, "Support Request")

            if (opId != null) {
                val operator = userService.findByTelegramId(opId)
                val opMenuLang = operator?.selectedLanguages?.firstOrNull()?.code ?: LanguageCode.UZ


                val opMsg = if (opMenuLang == LanguageCode.UZ)
                    "🔔 Yangi mijoz: ${user.firstName}\n🌐 Til: ${userLang.name}"
                else "🔔 Новый клиент: ${user.firstName}\n🌐 Язык: ${userLang.name}"

                send(opId, opMsg, keyboardService.operatorMenu(OperatorState.BUSY, opMenuLang, true))


                val successMsg = if (userLang == LanguageCode.UZ)
                    "✅ Operator ulandi. Marhamat, savolingizni yo'llang."
                else "✅ Оператор подключен. Пожалуйста, задавайте свой вопрос."

                send(chatId, successMsg, keyboardService.closeChatMenu(userLang))
            } else {

                val waitMsg = if (userLang == LanguageCode.UZ)
                    "⏳ Hozircha sizning tilingizda bo'sh operatorlar yo'q. Navbatga qo'shildingiz, iltimos kuting."
                else "⏳ На данный момент нет свободных операторов на вашем языке. Вы добавлены в очередь."

                send(chatId, waitMsg)
            }
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
                chatRepository.findById(p[1].toLong()).ifPresent { chat ->

                    ratingService.rateOperator(chat, p[2].toInt(), null)


                    val userLang = chat.user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ


                    editMenu(cid, mid, null)


                    val thankMsg = if (userLang == LanguageCode.UZ)
                        "Rahmat! Bahoyingiz qabul qilindi (${p[2]} ⭐)"
                    else "Спасибо! Ваша оценка принята (${p[2]} ⭐)"

                    send(cid, thankMsg, keyboardService.userMenu(userLang))
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
                    val opLangCode = user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ
                    val errorMsg =
                        if (opLangCode == LanguageCode.UZ) "⚠️ Kamida bitta tilni tanlang!" else "⚠️ Выберите хотя бы один язык!"
                    send(cid, errorMsg)
                } else {

                    operatorService.updateOperatorLanguages(user, sel)


                    operatorService.goOnline(user)

                    val codes = sel.map { LanguageCode.valueOf(it.uppercase()) }
                    val active = chatService.connectSpecificOperatorWithQueue(user, codes)


                    editMenu(cid, mid, null)

                    val opLang = user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ

                    if (active != null) {
                        val opMsg = if (opLang == LanguageCode.UZ) "✅ Mijoz ulandi!" else "✅ Клиент подключен!"
                        send(cid, opMsg, keyboardService.operatorMenu(OperatorState.BUSY, opLang, true))

                        val clientLang = active.user.selectedLanguages.firstOrNull()?.code ?: LanguageCode.UZ
                        val uMsg = if (clientLang == LanguageCode.UZ) "🔔 Operator ulandi!" else "🔔 Оператор подключен!"
                        send(active.user.telegramId, uMsg, keyboardService.closeChatMenu(clientLang))
                    } else {
                        val onlineMsg = if (opLang == LanguageCode.UZ) "✅ Onlinedasiz." else "✅ Вы в сети."
                        send(cid, onlineMsg, keyboardService.operatorMenu(OperatorState.ONLINE, opLang, false))
                    }
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
                if (sel.isNullOrEmpty()) {
                    send(cid, "⚠️ Iltimos, kamida bitta tilni tanlang! / Пожалуйста, выберите хотя бы один язык!")
                } else {

                    userService.saveUserLanguages(uid, sel)


                    val selectedCode = LanguageCode.valueOf(sel.first().uppercase())


                    editMenu(cid, mid, null)


                    tempSelectedLangs.remove(uid)


                    val currentUser = userService.findByTelegramId(uid)


                    if (currentUser?.phoneNumber == null || currentUser.phoneNumber.startsWith("temp_")) {
                        val askPhone = if (selectedCode == LanguageCode.UZ)
                            "✅ Til saqlandi. Botdan foydalanish uchun telefon raqamingizni yuboring:"
                        else "✅ Язык сохранен. Отправьте свой номер телефона, чтобы продолжить:"

                        send(cid, askPhone, keyboardService.contactMenu(selectedCode))
                    } else {
                        val successMsg = if (selectedCode == LanguageCode.UZ)
                            "✅ Tillaringiz muvaffaqiyatli yangilandi!"
                        else "✅ Ваши языки успешно обновлены!"


                        val replyKeyboard = when (currentUser.role) {
                            UserRole.USER -> keyboardService.userMenu(selectedCode)

                            UserRole.ADMIN -> keyboardService.adminMenu(selectedCode)

                            UserRole.OPERATOR -> {
                                val status = operatorStatusRepository.findByOperator(currentUser)?.status
                                    ?: OperatorState.OFFLINE
                                val hasActiveChat = chatService.getActiveChat(currentUser) != null

                                keyboardService.operatorMenu(status, selectedCode, hasActiveChat)
                            }
                        }

                        send(cid, successMsg, replyKeyboard)
                    }
                }
            }
        }
    }
}