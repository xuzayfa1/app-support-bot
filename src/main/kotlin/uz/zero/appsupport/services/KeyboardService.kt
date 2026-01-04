package uz.zero.appsupport.services

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import uz.zero.appsupport.OperatorState

@Service
class KeyboardService {

    fun languageSelectionMenu(selectedCodes: Set<String>): InlineKeyboardMarkup {

        val rows = mutableListOf<InlineKeyboardRow>()

        val languages = mapOf("UZ" to "O'zbek tili", "RU" to "Русский язык", "EN" to "English")

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
            .text("✅ Tasdiqlash")
            .callbackData("CONFIRM_LANG")
            .build()

        rows.add(InlineKeyboardRow(confirmButton))

        return InlineKeyboardMarkup.builder().keyboard(rows).build()
    }

    fun contactMenu(): ReplyKeyboardMarkup {
        val row = KeyboardRow()
        row.add(KeyboardButton.builder().text("📱 Telefon raqamni yuborish").requestContact(true).build())

        return ReplyKeyboardMarkup.builder()
            .keyboard(listOf(row))
            .resizeKeyboard(true)
            .oneTimeKeyboard(true)
            .build()
    }

    fun userMenu(): ReplyKeyboardMarkup {
        val row = KeyboardRow()
        row.add(KeyboardButton("🆘 Operatorga bog'lanish"))
        row.add(KeyboardButton("🌐 Tilni o'zgartirish"))

        return ReplyKeyboardMarkup.builder()
            .keyboard(listOf(row))
            .resizeKeyboard(true)
            .build()
    }


    fun operatorMenu(state: OperatorState, hasActiveChat: Boolean = false): ReplyKeyboardMarkup {
        val keyboard = mutableListOf<KeyboardRow>()

        when {

            state == OperatorState.OFFLINE -> {
                val row = KeyboardRow()
                row.add(KeyboardButton("🚀 Ishni boshlash (Online)"))
                keyboard.add(row)
            }


            hasActiveChat || state == OperatorState.BUSY -> {
                val row = KeyboardRow()
                row.add(KeyboardButton("❌ Suhbatni yakunlash"))
                keyboard.add(row)
            }


            state == OperatorState.ONLINE -> {
                val row1 = KeyboardRow()
                row1.add(KeyboardButton("⏭ Keyingi mijoz"))
                row1.add(KeyboardButton("🏁 Ishni yakunlash (Offline)"))

                val row2 = KeyboardRow()
                row2.add(KeyboardButton("🌐 Tilni o'zgartirish"))

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

    fun closeChatMenu(): ReplyKeyboardMarkup {
        val row = KeyboardRow()
        row.add(KeyboardButton("❌ Suhbatni yakunlash"))
        return ReplyKeyboardMarkup.builder()
            .keyboard(listOf(row))
            .resizeKeyboard(true)
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

    fun adminMenu(): ReplyKeyboardMarkup {
        val row1 = KeyboardRow().apply {
            add("🏆 Operatorlar reytingi")
            add("💬 Oxirgi baholashlar")
        }
        return ReplyKeyboardMarkup.builder()
            .keyboard(listOf(row1))
            .resizeKeyboard(true)
            .build()
    }
}