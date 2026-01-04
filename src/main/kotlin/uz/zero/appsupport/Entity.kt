package uz.zero.appsupport

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table
import jakarta.persistence.Temporal
import jakarta.persistence.TemporalType
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.util.Date

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
class BaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @CreatedDate @Temporal(TemporalType.TIMESTAMP) var createdDate: Date? = null,
    @LastModifiedDate @Temporal(TemporalType.TIMESTAMP) var modifiedDate: Date? = null,
    @CreatedBy var createdBy: Long? = null,
    @LastModifiedBy var lastModifiedBy: Long? = null,
    @Column(nullable = false) @ColumnDefault(value = "false") var deleted: Boolean = false
)

@Entity
@Table(name = "users")
class User(

    @Column(nullable = false, unique = true)
    var telegramId: Long,
    @Column(nullable = false, unique = true)
    var phoneNumber: String,
    @Column(nullable = false)
    var password: String,
    var firstName: String? = null,
    var lastName: String? = null,

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    var role: UserRole,

    @ManyToOne
    var selectedLanguage: Language? = null
) : BaseEntity()


@Entity
class Language(
    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false, unique = true)
    var code: LanguageCode,
    @Column(nullable = false)
    var name: String
) : BaseEntity()

@Entity
class OperatorLanguage(
    @ManyToOne
    var operator: User,
    @ManyToOne
    var language: Language

) : BaseEntity()

@Entity
@Table(name = "operator_status")
class OperatorStatus(

    @ManyToOne
    var operator: User,

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    var status: OperatorState = OperatorState.OFFLINE
) : BaseEntity()


@Entity
class Chat(
    @ManyToOne
    var user: User,

    @ManyToOne
    var operator: User,

    @ManyToOne
    var language: Language,

    @Enumerated(value = EnumType.STRING)
    var status: ChatStatus = ChatStatus.ACTIVE,

    var startedAt: Date? = null,
    var endedAt: Date? = null

) : BaseEntity()


@Entity
class Message(
    @ManyToOne
    var session: Chat,
    @ManyToOne
    var sender: User,
    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    var messageType: MessageType,
    @Column(columnDefinition = "TEXT")
    var content: String? = null,

    var userMessageId: Long? = null,
    var operatorMessageId: Long? = null,

    var telegramMessageId: Long? = null,
    var telegramFileId: String? = null,
    var telegramFileUniqueId: String? = null,
    @ManyToOne
    var replyTo: Message? = null,
    var isEdited: Boolean = false,
    var editedAt: Date? = null

) : BaseEntity()

@Entity
class WaitingUser(
    @ManyToOne
    @JoinColumn(name = "user_id")
    val user: User,
    @ManyToOne
    var language: Language,
    var joinedAt: Date = Date()

) : BaseEntity()


@Entity
class ChatRating(
    @ManyToOne
    var session: Chat,
    @ManyToOne
    var operator: User,
    @ManyToOne
    var user: User,
    @Column(nullable = false)
    var rating: Int,
    var comment: String? = null,
    var ratedAt: Date = Date()

) : BaseEntity()


@Entity
class OperatorStatistics(
    @ManyToOne(fetch = FetchType.EAGER)
    var operator: User,
    var averageRating: Double = 0.0,
    var ratingsCount: Long = 0,
) : BaseEntity()
