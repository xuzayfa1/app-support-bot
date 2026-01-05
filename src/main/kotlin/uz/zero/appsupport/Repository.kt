package uz.zero.appsupport

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import java.util.*

@NoRepositoryBean
interface BaseRepository<T : BaseEntity> : JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    fun findByIdAndDeletedFalse(id: Long): T?
    fun trash(id: Long): T?
    fun trashList(ids: List<Long>): List<T?>
    fun findAllNotDeleted(): List<T>
    fun findAllNotDeletedForPageable(pageable: Pageable): Page<T>
    fun saveAndRefresh(t: T): T
}

class BaseRepositoryImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, Long>,
    private val entityManager: EntityManager
) : SimpleJpaRepository<T, Long>(entityInformation, entityManager), BaseRepository<T> {

    val isNotDeletedSpecification = Specification<T> { root, _, cb -> cb.equal(root.get<Boolean>("deleted"), false) }

    override fun findByIdAndDeletedFalse(id: Long) = findByIdOrNull(id)?.run { if (deleted) null else this }

    @Transactional
    override fun trash(id: Long): T? = findByIdOrNull(id)?.run {
        deleted = true
        save(this)
    }

    override fun findAllNotDeleted(): List<T> = findAll(isNotDeletedSpecification)
    override fun findAllNotDeletedForPageable(pageable: Pageable): Page<T> =
        findAll(isNotDeletedSpecification, pageable)

    override fun trashList(ids: List<Long>): List<T?> = ids.map { trash(it) }

    @Transactional
    override fun saveAndRefresh(t: T): T {
        val saved = save(t)
        entityManager.flush()
        entityManager.refresh(saved)
        return saved
    }
}


interface UserRepository : BaseRepository<User> {
    fun findByTelegramId(telegramId: Long): User?
    fun findByRoleAndDeletedFalse(role: UserRole): List<User>
}


interface LanguageRepository : BaseRepository<Language> {
    fun findByCode(code: LanguageCode): Language?
}


interface OperatorStatusRepository : BaseRepository<OperatorStatus> {

    @Query(
        """
    SELECT os FROM OperatorStatus os 
    JOIN os.operator u 
    JOIN OperatorLanguage ol ON ol.operator = u 
    WHERE os.status = 'ONLINE' 
    AND ol.language.code = :userLangCode
    AND os.deleted = false 
    AND u.deleted = false
    ORDER BY os.updatedAt ASC
    """
    )
    fun findAvailableOperator(@Param("userLangCode") userLangCode: LanguageCode): OperatorStatus?

    fun findByOperator(operator: User): OperatorStatus?
}


interface ChatRepository : BaseRepository<Chat> {

    
    fun findFirstByUserAndStatusInAndDeletedFalse(
        user: User,
        statuses: Collection<ChatStatus>
    ): Chat?

    
    fun findFirstByOperatorAndStatusInAndDeletedFalse(
        operator: User,
        statuses: Collection<ChatStatus>
    ): Chat?

    
    fun countByStatus(status: ChatStatus): Long

    
    fun existsByUserAndStatusInAndDeletedFalse(
        user: User,
        statuses: Collection<ChatStatus>
    ): Boolean

    
    @Query(
        """
        SELECT c FROM Chat c 
        WHERE (c.user = :person OR c.operator = :person) 
        AND c.status = 'ACTIVE' 
        AND c.deleted = false
    """
    )
    fun findActiveChatByParticipant(@Param("person") person: User): Optional<Chat>

    @Query("""
        SELECT c FROM Chat c 
        WHERE c.status = :status 
        AND c.language.code IN :languages 
        AND c.deleted = false 
        ORDER BY c.id ASC
    """)
    fun findFirstWaitingChatByLanguages(
        @Param("status") status: ChatStatus,
        @Param("languages") languages: Collection<LanguageCode>
    ): Optional<Chat>
}


interface WaitingUserRepository : BaseRepository<WaitingUser> {


    @Query(
        """
        SELECT w FROM WaitingUser w 
        JOIN w.language l 
        WHERE w.deleted = false 
        AND l.code IN :operatorLangs 
        ORDER BY w.joinedAt ASC
    """
    )
    fun findFirstInQueue(@Param("operatorLangs") operatorLangCodes: Collection<LanguageCode>): Optional<WaitingUser>


    fun existsByUserAndDeletedFalse(user: User): Boolean


    fun findByUserAndDeletedFalse(user: User): Optional<WaitingUser>


    fun findAllByDeletedFalseOrderByJoinedAtAsc(): List<WaitingUser>


    @Modifying
    @Query("UPDATE WaitingUser w SET w.deleted = true WHERE w.user = :user AND w.deleted = false")
    fun softDeleteByUser(@Param("user") user: User)
}


interface MessageRepository : BaseRepository<Message> {

    fun findAllBySenderAndSessionIsNull(sender: User): List<Message>
    fun findByOperatorMessageId(id: Long): Message?
    fun findByUserMessageId(id: Long): Message?
}

interface OperatorLanguageRepository : BaseRepository<OperatorLanguage> {

    fun findAllByOperator(operator: User): List<OperatorLanguage>
}


interface OperatorStatisticsRepository : BaseRepository<OperatorStatistics> {
    fun findByOperator(operator: User): OperatorStatistics?
    fun findAllByOrderByAverageRatingDesc(): List<OperatorStatistics>
}

interface ChatRatingRepository : BaseRepository<ChatRating> {


    fun findTop10ByOrderByRatedAtDesc(): List<ChatRating>
}
