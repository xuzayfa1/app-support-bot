package uz.zero.appsupport.services

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uz.zero.appsupport.*
import org.telegram.telegrambots.meta.api.objects.User as TelegramUser

interface UserService {
    fun getOrCreateUser(telegramUser: TelegramUser): User
    fun findByTelegramId(telegramId: Long): User?
    fun deleteUser(id: Long)
    fun updatePhoneNumber(user: User, phoneNumber: String)
    fun saveUserLanguages(telegramId: Long, langCodes: Set<String>)
    fun promoteToOperator(telegramId: Long): String
    fun updateUserRole(telegramId: Long, newRole: UserRole): String
}


@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val operatorStatusRepository: OperatorStatusRepository
) : UserService {

    @Transactional
    override fun getOrCreateUser(telegramUser: TelegramUser): User {
        val existingUser = userRepository.findByTelegramId(telegramUser.id)
        if (existingUser != null) return existingUser


        val newUser = User(
            telegramId = telegramUser.id,
            phoneNumber = "temp_${telegramUser.id}",
            password = "no_password",
            firstName = telegramUser.firstName,
            lastName = telegramUser.lastName,
            role = UserRole.USER
        )

        return userRepository.save(newUser)
    }

    @Transactional
    override fun saveUserLanguages(telegramId: Long, langCodes: Set<String>) {
        val user = userRepository.findByTelegramId(telegramId) ?: return


        user.selectedLanguages.clear()


        val codeStr = langCodes.firstOrNull() ?: return

        try {
            val code = LanguageCode.valueOf(codeStr.uppercase())
            languageRepository.findByCode(code)?.let {
                user.selectedLanguages.add(it)
            }
        } catch (e: Exception) {

        }

        userRepository.save(user)
    }


    @Transactional
    fun updateUserLanguage(user: User, langCode: LanguageCode) {
        user.selectedLanguages.clear()
        languageRepository.findByCode(langCode)?.let {
            user.selectedLanguages.add(it)
        }
        userRepository.save(user)
    }

    @Transactional
    override fun updatePhoneNumber(user: User, phoneNumber: String) {

        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        val formattedNumber = if (cleanNumber.startsWith("+")) cleanNumber else "+$cleanNumber"

        user.phoneNumber = formattedNumber
        userRepository.save(user)
    }


    override fun findByTelegramId(telegramId: Long) = userRepository.findByTelegramId(telegramId)

    @Transactional
    override fun deleteUser(id: Long) {
        userRepository.trash(id)
    }

    @Transactional
    override fun promoteToOperator(telegramId: Long): String {
        val user = userRepository.findByTelegramId(telegramId) ?: return "Foydalanuvchi topilmadi!"
        if (user.role == UserRole.OPERATOR) return "Bu foydalanuvchi allaqachon operator."
        user.role = UserRole.OPERATOR
        userRepository.save(user)
        setupOperatorStatus(user)
        return "Foydalanuvchi muvaffaqiyatli operator qilindi!"
    }

    @Transactional
    override fun updateUserRole(telegramId: Long, newRole: UserRole): String {
        val user = userRepository.findByTelegramId(telegramId) ?: return "Xato: Foydalanuvchi topilmadi"
        user.role = newRole
        userRepository.save(user)
        if (newRole == UserRole.OPERATOR) setupOperatorStatus(user)
        return "Muvaffaqiyatli: ${user.firstName} endi $newRole rolida."
    }

    private fun setupOperatorStatus(user: User) {
        val existingStatus = operatorStatusRepository.findByOperator(user)
        if (existingStatus == null) {
            operatorStatusRepository.save(OperatorStatus(operator = user, status = OperatorState.OFFLINE))
        }
    }
}