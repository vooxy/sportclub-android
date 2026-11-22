package com.tisbi.sportclub.db

/** Роли пользователей системы (ТЗ п. 3.1, 4.2) */
object Roles {
    const val ADMIN = "Администратор"
    const val TRAINER = "Тренер"
    const val ACCOUNTANT = "Бухгалтер"
    const val CLIENT = "Клиент"
}

/** Статусы абонемента (ТЗ п. 4.2.2) */
object MembershipStatus {
    const val ACTIVE = "Активен"
    const val FROZEN = "Заморожен"
    const val INACTIVE = "Неактивен"
    const val CLOSED = "Закрыт"
}

/** Статусы платежа (ТЗ п. 4.2.4) */
object PaymentStatus {
    const val PAID = "Оплачен"
    const val PENDING = "Ожидает"
    const val REFUNDED = "Возврат"
}

/** Способы оплаты (ТЗ п. 4.2.4) */
object PaymentMethod {
    const val CASH = "Наличные"
    const val CARD = "Банковская карта"
    const val ONLINE = "Онлайн-перевод"
}

/** Статус записи на занятие в таблице Посещения (доработка схемы) */
object AttendanceStatus {
    const val BOOKED = "Записан"
    const val VISITED = "Пришёл"
    const val CANCELLED = "Отменил"
}

data class User(
    val id: Long,
    val login: String,
    val passwordHash: String,
    val role: String,
    val idClient: Long?,
    val idTrainer: Long?,
    val fullName: String
)

data class Client(
    val id: Long,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val gender: String?,
    val birthDate: String?,
    val phone: String,
    val email: String?,
    val cardNumber: String?,
    val registrationDate: String?
) {
    val fio: String get() = buildString {
        append(lastName)
        append(" ")
        append(firstName)
        if (!middleName.isNullOrBlank()) {
            append(" ")
            append(middleName.substring(0, 1))
            append(".")
        }
    }
    val fioFull: String get() = listOfNotNull(lastName, firstName, middleName).joinToString(" ")
}

data class Trainer(
    val id: Long,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val phone: String?,
    val specialization: String?,
    val hireDate: String?
) {
    val fio: String get() = buildString {
        append(lastName)
        append(" ")
        append(firstName)
        if (!middleName.isNullOrBlank()) {
            append(" ")
            append(middleName.substring(0, 1))
            append(".")
        }
    }
}

data class SessionType(
    val id: Long,
    val typeName: String,
    val price: Double?
)

data class MembershipType(
    val id: Long,
    val typeName: String,
    val duration: Int,
    val price: Double,
    val description: String?
)

data class Membership(
    val id: Long,
    val idClient: Long,
    val idMembershipType: Long,
    val purchasePrice: Double?,
    val startDate: String?,
    val endDate: String?,
    val status: String?
)

data class Payment(
    val id: Long,
    val idMembership: Long?,
    val paymentDate: String?,
    val amount: Double?,
    val paymentMethod: String?,
    val status: String?
)

data class WorkoutSession(
    val id: Long,
    val sessionName: String,
    val idSessionType: Long?,
    val idTrainer: Long?,
    val startDateTime: String?,
    val duration: Int,
    val maxParticipants: Int,
    val hall: String?
)

data class Attendance(
    val id: Long,
    val idClient: Long,
    val idSession: Long?,
    val visitDateTime: String?,
    val paid: Boolean,
    val status: String
)

/** Составные представления для UI */

data class SessionDetails(
    val session: WorkoutSession,
    val typeName: String,
    val trainerFio: String,
    val bookedCount: Int
)

data class MembershipDetails(
    val membership: Membership,
    val clientFio: String,
    val typeName: String,
    val duration: Int
)

data class AttendanceDetails(
    val attendance: Attendance,
    val clientFio: String,
    val sessionName: String?,
    val trainerFio: String?,
    val hall: String?,
    val startDateTime: String?
)

data class PaymentDetails(
    val payment: Payment,
    val clientFio: String,
    val membershipTypeName: String
)

/** Абонемент клиента с вычисляемыми характеристиками */
data class ClientMembership(
    val membership: Membership,
    val typeName: String,
    val daysLeft: Long
)
