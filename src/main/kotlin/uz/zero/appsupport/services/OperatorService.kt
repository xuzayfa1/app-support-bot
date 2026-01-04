package uz.zero.appsupport.services

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uz.zero.appsupport.*
import org.springframework.context.annotation.Lazy
import java.util.Date

interface OperatorService {
    fun goOnline(operator: User)
    fun goOffline(operator: User)
    fun updateOperatorLanguages(operator: User, langCodes: Set<String>)
}

@Service
class OperatorServiceImpl(
    private val operatorStatusRepository: OperatorStatusRepository,
    private val languageRepository: LanguageRepository,
    private val operatorLanguageRepository: OperatorLanguageRepository,
) : OperatorService {

    @Transactional
    override fun goOnline(user: User) {
        val status = operatorStatusRepository.findByOperator(user) ?: OperatorStatus(operator = user)
        status.status = OperatorState.ONLINE
        status.modifiedDate = Date()
        operatorStatusRepository.save(status)
    }

    @Transactional
    override fun goOffline(operator: User) {
        operatorStatusRepository.findByOperator(operator)?.let {
            it.status = OperatorState.OFFLINE
            operatorStatusRepository.save(it)
        }
    }


    @Transactional
    override fun updateOperatorLanguages(operator: User, langCodes: Set<String>) {

        langCodes.forEach { code ->
            val language = languageRepository.findByCode(LanguageCode.valueOf(code))
            if (language != null) {
                operatorLanguageRepository.save(OperatorLanguage(operator, language))
            }
        }
    }
}