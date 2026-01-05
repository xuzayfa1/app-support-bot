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
            )
            languageRepository.saveAll(languages)
            println("--- Tillar bazaga yuklandi ---")
        }


        val langUz = languageRepository.findByCode(LanguageCode.UZ)
            ?: throw RuntimeException("O'zbek tili bazadan topilmadi!")


        val operatorId = 123456789L
        if (userRepository.findByTelegramId(operatorId) == null) {
            val adminOperator = User(
                telegramId = operatorId,
                phoneNumber = "+998901234567",
                password = "admin",
                firstName = "Asosiy",
                lastName = "Operator",
                role = UserRole.OPERATOR,

                selectedLanguages = mutableSetOf(langUz)
            )
            userRepository.save(adminOperator)
            println("--- Test operator bazaga yuklandi ---")
        }
    }
}