package uz.zero.appsupport.services

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import uz.zero.appsupport.LanguageCode
import uz.zero.appsupport.OperatorState

@Service
class KeyboardService {

    fun languageSelectionMenu(selectedCodes: Set<String>): InlineKeyboardMarkup {
        val rows = mutableListOf<InlineKeyboardRow>()


        val languages = mapOf("UZ" to "O'zbek tili", "RU" to "Русский язык")

        languages.forEach { (code, name) ->
            val isSelected = selectedCodes.contains(code)
            val text = if (isSelected) "✅ $name" else name

            val button = InlineKeyboardButton.builder()
                .text(text)
                .callbackData("LANG_$code")
                .build()

            rows.add(InlineKeyboardRow(button))
        }


        val confirmButton = InlineKeyboardButton.builder()
            .text("✅ Tasdiqlash / Подтвердить")
            .callbackData("CONFIRM_LANG")
            .build()

        rows.add(InlineKeyboardRow(confirmButton))

        return InlineKeyboardMarkup.builder()
            .keyboard(rows)
            .build()
    }

    fun contactMenu(langCode: LanguageCode): ReplyKeyboardMarkup {
        val row = KeyboardRow()


        val buttonText = if (langCode == LanguageCode.UZ) {
            "📱 Telefon raqamni yuborish"
        } else {
            "📱 Отправить номер телефона"
        }

        row.add(
            KeyboardButton.builder()
                .text(buttonText)
                .requestContact(true)
                .build()
        )

        return ReplyKeyboardMarkup.builder()
            .keyboard(listOf(row))
            .resizeKeyboard(true)
            .oneTimeKeyboard(true)
            .build()
    }

    fun userMenu(langCode: LanguageCode): ReplyKeyboardMarkup {
        val row1 = KeyboardRow()
        if (langCode == LanguageCode.UZ) {
            row1.add("🆘 Operatorga bog'lanish")
            row1.add("🌐 Tilni o'zgartirish")
        } else {
            row1.add("🆘 Связаться с оператором")
            row1.add("🌐 Изменить язык")
        }
        return ReplyKeyboardMarkup.builder()
            .keyboard(listOf(row1))
            .resizeKeyboard(true)
            .build()
    }


    fun operatorMenu(
        state: OperatorState,
        langCode: LanguageCode,
        hasActiveChat: Boolean = false
    ): ReplyKeyboardMarkup {
        val keyboard = mutableListOf<KeyboardRow>()
        val isUz = langCode == LanguageCode.UZ

        when {
            state == OperatorState.OFFLINE -> {
                val row = KeyboardRow()
                row.add(KeyboardButton(if (isUz) "🚀 Ishni boshlash (Online)" else "🚀 Начать работу (Online)"))
                keyboard.add(row)
            }

            hasActiveChat || state == OperatorState.BUSY -> {
                val row = KeyboardRow()
                row.add(KeyboardButton(if (isUz) "❌ Suhbatni yakunlash" else "❌ Завершить чат"))
                keyboard.add(row)
            }

            state == OperatorState.ONLINE -> {
                val row1 = KeyboardRow()
                row1.add(KeyboardButton(if (isUz) "⏭ Keyingi mijoz" else "⏭ Следующий клиент"))
                row1.add(KeyboardButton(if (isUz) "🏁 Ishni yakunlash (Offline)" else "🏁 Завершить работу (Offline)"))

                val row2 = KeyboardRow()
                row2.add(KeyboardButton(if (isUz) "🌐 Tilni o'zgartirish" else "🌐 Изменить язык"))

                keyboard.add(row1)
                keyboard.add(row2)
            }
        }

        return ReplyKeyboardMarkup.builder()
            .keyboard(keyboard)
            .resizeKeyboard(true)
            .build()
    }

    fun operatorLanguageMenu(selectedCodes: Set<String>): InlineKeyboardMarkup {
        val rows = mutableListOf<InlineKeyboardRow>()
        val languages = mapOf("UZ" to "O'zbekcha", "RU" to "Русский", "EN" to "English")

        languages.forEach { (code, name) ->
            val isSelected = selectedCodes.contains(code)
            val text = if (isSelected) "✅ $name" else name
            val button = InlineKeyboardButton.builder()
                .text(text)
                .callbackData("OP_LANG_$code")
                .build()

            rows.add(InlineKeyboardRow(button))
        }

        val confirmButton = InlineKeyboardButton.builder()
            .text("✅ Tasdiqlash va online bo'lish")
            .callbackData("OP_CONFIRM_LANG")
            .build()

        rows.add(InlineKeyboardRow(confirmButton))

        return InlineKeyboardMarkup.builder().keyboard(rows).build()
    }

    fun closeChatMenu(langCode: LanguageCode): ReplyKeyboardMarkup {
        val row = KeyboardRow()


        val buttonText = if (langCode == LanguageCode.UZ) {
            "❌ Suhbatni yakunlash"
        } else {
            "❌ Завершить чат"
        }

        row.add(KeyboardButton(buttonText))

        return ReplyKeyboardMarkup.builder()
            .keyboard(listOf(row))
            .resizeKeyboard(true)
            .oneTimeKeyboard(false)
            .build()
    }

    fun ratingMenu(chatId: Long): InlineKeyboardMarkup {
        val row = InlineKeyboardRow()


        for (i in 1..5) {
            row.add(
                InlineKeyboardButton.builder()
                    .text("$i ⭐")
                    .callbackData("RATE_${chatId}_$i")
                    .build()
            )
        }

        return InlineKeyboardMarkup.builder().keyboard(listOf(row)).build()
    }

    fun adminMenu(langCode: LanguageCode): ReplyKeyboardMarkup {
        val isUz = langCode == LanguageCode.UZ
        val row1 = KeyboardRow().apply {
            add(if (isUz) "🏆 Operatorlar reytingi" else "🏆 Рейтинг операторов")
            add(if (isUz) "💬 Oxirgi baholashlar" else "💬 Последние оценки")
        }
        return ReplyKeyboardMarkup.builder()
            .keyboard(listOf(row1))
            .resizeKeyboard(true)
            .build()
    }
}