package uz.zero.appsupport

enum class UserRole {
    USER,
    ADMIN,
    OPERATOR
}

enum class LanguageCode {
    UZ,
    RU,
    EN
}

enum class OperatorState {
    OFFLINE,
    ONLINE,
    BUSY
}

enum class ChatStatus {
    ACTIVE,
    WAITING,
    ENDED,
    RATED
}

enum class MessageType {
    TEXT,
    PHOTO,
    VIDEO,
    VOICE,
    DOCUMENT,
    STICKER
}