package uz.zero.appsupport.services


import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.zero.appsupport.*
import uz.zero.appsupport.ChatRatingRepository
import uz.zero.appsupport.OperatorStatisticsRepository

@Service
class RatingService(
    private val chatRatingRepository: ChatRatingRepository,
    private val operatorStatisticsRepository: OperatorStatisticsRepository
) {

    @Transactional
    fun rateOperator(chat: Chat, score: Int, comment: String?) {

        val chatRating = ChatRating(
            session = chat,
            operator = chat.operator,
            user = chat.user,
            rating = score,
            comment = comment
        )
        chatRatingRepository.save(chatRating)


        val stats = operatorStatisticsRepository.findByOperator(chat.operator)
            ?: OperatorStatistics(operator = chat.operator)


        val currentCount = stats.ratingsCount
        val currentAverage = stats.averageRating

        val newCount = currentCount + 1
        val newAverage = ((currentAverage * currentCount) + score.toDouble()) / newCount

        stats.ratingsCount = newCount
        stats.averageRating = newAverage

        operatorStatisticsRepository.save(stats)
    }
}