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
        return userRepository.findByTelegramId(telegramUser.id) ?: userRepository.save(
            User(
                telegramId = telegramUser.id,
                phoneNumber = "temp_${telegramUser.id}",
                password = "no_password",
                firstName = telegramUser.firstName,
                lastName = telegramUser.lastName,
                role = UserRole.USER,
                selectedLanguage = languageRepository.findByCode(LanguageCode.UZ)
            )
        )
    }

    @Transactional
    override fun saveUserLanguages(telegramId: Long, langCodes: Set<String>) {
        val user = userRepository.findByTelegramId(telegramId) ?: return

        val mainLangCode = langCodes.first()
        val language = languageRepository.findByCode(LanguageCode.valueOf(mainLangCode))
        user.selectedLanguage = language
        userRepository.save(user)


    }

    override fun findByTelegramId(telegramId: Long) = userRepository.findByTelegramId(telegramId)

    override fun deleteUser(id: Long) {
        userRepository.trash(id)
    }

    override fun updatePhoneNumber(user: User, phoneNumber: String) {
        user.phoneNumber = if (phoneNumber.startsWith("+")) phoneNumber else "+$phoneNumber"
        userRepository.save(user)
    }


    @Transactional
    override fun promoteToOperator(telegramId: Long): String {
        val user = userRepository.findByTelegramId(telegramId)
            ?: return "Foydalanuvchi topilmadi!"

        if (user.role == UserRole.OPERATOR) return "Bu foydalanuvchi allaqachon operator."


        user.role = UserRole.OPERATOR
        userRepository.save(user)


        val existingStatus = operatorStatusRepository.findByOperator(user)
        if (existingStatus == null) {
            operatorStatusRepository.save(OperatorStatus(operator = user, status = OperatorState.OFFLINE))
        }

        return "Foydalanuvchi muvaffaqiyatli operator qilindi!"
    }

    @Transactional
    override fun updateUserRole(telegramId: Long, newRole: UserRole): String {
        val user = userRepository.findByTelegramId(telegramId)
            ?: return "Xato: Foydalanuvchi topilmadi (ID: $telegramId)"

        user.role = newRole
        userRepository.save(user)

        if (newRole == UserRole.OPERATOR) {
            val existingStatus = operatorStatusRepository.findByOperator(user)
            if (existingStatus == null) {
                operatorStatusRepository.save(OperatorStatus(operator = user, status = OperatorState.OFFLINE))
            }
        }

        return "Muvaffaqiyatli: ${user.firstName} endi $newRole rolida."
    }
}