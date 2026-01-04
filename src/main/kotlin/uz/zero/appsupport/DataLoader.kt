package uz.zero.appsupport

import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class DataLoader(
    private val languageRepository: LanguageRepository,
    private val userRepository: UserRepository
) : CommandLineRunner {

    override fun run(vararg args: String?) {

        if (languageRepository.count() == 0L) {
            val languages = listOf(
                Language(code = LanguageCode.UZ, name = "O'zbekcha"),
                Language(code = LanguageCode.RU, name = "Русский"),
                Language(code = LanguageCode.EN, name = "English")
            )
            languageRepository.saveAll(languages)
            println("--- Tillar bazaga yuklandi ---")
        }


        val operatorId = 123456789L
        if (userRepository.findByTelegramId(operatorId) == null) {
            val adminOperator = User(
                telegramId = operatorId,
                phoneNumber = "+998901234567",
                password = "admin",
                firstName = "Asosiy",
                lastName = "Operator",
                role = UserRole.OPERATOR,
                selectedLanguage = languageRepository.findByCode(LanguageCode.UZ)
            )
            userRepository.save(adminOperator)
            println("--- Test operator bazaga yuklandi ---")
        }
    }
}