package com.tisbi.sportclub.repo

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.tisbi.sportclub.db.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Точка доступа к базе данных и репозиториям */
object Database {
    @SuppressLint("StaticFieldLeak")
    private lateinit var helper: DbHelper

    val users = UsersRepo()
    val clients = ClientsRepo()
    val trainers = TrainersRepo()
    val schedule = ScheduleRepo()
    val memberships = MembershipRepo()
    val payments = PaymentsRepo()
    val attendance = AttendanceRepo()

    fun init(context: Context) {
        if (!::helper.isInitialized) helper = DbHelper(context.applicationContext)
    }

    internal fun <T> read(block: (android.database.sqlite.SQLiteDatabase) -> T): T =
        block(helper.readableDatabase)

    internal fun <T> write(block: (android.database.sqlite.SQLiteDatabase) -> T): T =
        block(helper.writableDatabase)
}

// ----------------------------------------------------------------------
// Вспомогательные функции
// ----------------------------------------------------------------------

private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun todayStr(): String = LocalDate.now().format(DATE)
fun nowStr(): String = LocalDateTime.now().format(DATETIME)
fun parseDate(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it, DATE) }.getOrNull() }
fun parseDateTime(s: String?): LocalDateTime? =
    s?.let { runCatching { LocalDateTime.parse(it, DATETIME) }.getOrNull() }

fun Cursor.long(col: String): Long = getLong(getColumnIndexOrThrow(col))
fun Cursor.intOrNull(col: String): Int? = {
    val i = getColumnIndex(col); if (i >= 0 && !isNull(i)) getInt(i) else null
}()
fun Cursor.str(col: String): String? = {
    val i = getColumnIndex(col); if (i >= 0) getString(i) else null
}()
fun Cursor.double(col: String): Double? = {
    val i = getColumnIndex(col); if (i >= 0 && !isNull(i)) getDouble(i) else null
}()

/** Результат операции с сообщением об ошибке */
sealed class OpResult {
    data object Ok : OpResult()
    data class Error(val message: String) : OpResult()
}

// ----------------------------------------------------------------------
// Пользователи (авторизация)
// ----------------------------------------------------------------------

class UsersRepo {
    fun login(login: String, password: String): User? = Database.read { db ->
        db.rawQuery(
            "SELECT * FROM Users WHERE Login = ? AND PasswordHash = ?",
            arrayOf(login.trim(), DbHelper.sha256(password))
        ).use { c ->
            if (c.moveToFirst()) User(
                id = c.long("ID"),
                login = c.str("Login") ?: "",
                passwordHash = c.str("PasswordHash") ?: "",
                role = c.str("Role") ?: "",
                idClient = c.str("ID_Client")?.toLongOrNull(),
                idTrainer = c.str("ID_Trainer")?.toLongOrNull(),
                fullName = c.str("FullName") ?: ""
            ) else null
        }
    }

    /** Создание учётной записи клиента вместе с регистрацией (пароль = последние 4 цифры телефона) */
    fun createClientAccount(db: android.database.sqlite.SQLiteDatabase, clientId: Long, login: String, password: String, fio: String) {
        val cv = ContentValues().apply {
            put("Login", login)
            put("PasswordHash", DbHelper.sha256(password))
            put("Role", Roles.CLIENT)
            put("ID_Client", clientId)
            put("FullName", fio)
        }
        db.insertWithOnConflict("Users", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun loginExists(login: String): Boolean = Database.read { db ->
        db.rawQuery("SELECT COUNT(*) FROM Users WHERE Login = ?", arrayOf(login.trim()))
            .use { it.moveToFirst() && it.getInt(0) > 0 }
    }
}

// ----------------------------------------------------------------------
// Клиенты
// ----------------------------------------------------------------------

class ClientsRepo {
    fun all(search: String? = null): List<Client> = Database.read { db ->
        val where = if (!search.isNullOrBlank())
            "WHERE LastName LIKE ? OR FirstName LIKE ? OR Phone LIKE ? OR CardNumber LIKE ?"
        else ""
        val args = if (!search.isNullOrBlank()) arrayOf("%$search%", "%$search%", "%$search%", "%$search%") else arrayOf()
        db.rawQuery("SELECT * FROM Clients $where ORDER BY LastName", args).use { c ->
            val list = mutableListOf<Client>()
            while (c.moveToNext()) list += c.toClient()
            list
        }
    }

    fun byId(id: Long): Client? = Database.read { db ->
        db.rawQuery("SELECT * FROM Clients WHERE ID = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toClient() else null
        }
    }

    fun byCard(card: String): Client? = Database.read { db ->
        db.rawQuery("SELECT * FROM Clients WHERE CardNumber = ?", arrayOf(card.trim())).use { c ->
            if (c.moveToFirst()) c.toClient() else null
        }
    }

    /** Вставка; возвращает id новой записи */
    fun insert(c: Client): Long = Database.write { db ->
        val cv = c.toValues().apply { remove("ID") }
        db.insertOrThrow("Clients", null, cv)
    }

    fun update(c: Client) = Database.write { db ->
        db.update("Clients", c.toValues(), "ID = ?", arrayOf(c.id.toString()))
    }

    fun delete(id: Long) = Database.write { db ->
        db.delete("Clients", "ID = ?", arrayOf(id.toString()))
    }

    /** Генерация номера карты следующего клиента */
    fun nextCardNumber(): String = Database.read { db ->
        db.rawQuery("SELECT COUNT(*) FROM Clients", null).use { c ->
            if (c.moveToFirst()) "СК-%06d".format(c.getInt(0) + 1) else "СК-000001"
        }
    }

    private fun Cursor.toClient() = Client(
        id = long("ID"),
        lastName = str("LastName") ?: "",
        firstName = str("FirstName") ?: "",
        middleName = str("MiddleName"),
        gender = str("Gender"),
        birthDate = str("BirthDate"),
        phone = str("Phone") ?: "",
        email = str("Email"),
        cardNumber = str("CardNumber"),
        registrationDate = str("RegistrationDate")
    )

    private fun Client.toValues() = ContentValues().apply {
        put("ID", id)
        put("LastName", lastName)
        put("FirstName", firstName)
        put("MiddleName", middleName)
        put("Gender", gender)
        put("BirthDate", birthDate)
        put("Phone", phone)
        put("Email", email)
        put("CardNumber", cardNumber)
        put("RegistrationDate", registrationDate)
    }
}

// ----------------------------------------------------------------------
// Тренеры
// ----------------------------------------------------------------------

class TrainersRepo {
    fun all(search: String? = null): List<Trainer> = Database.read { db ->
        val where = if (!search.isNullOrBlank())
            "WHERE LastName LIKE ? OR FirstName LIKE ? OR Specialization LIKE ?"
        else ""
        val args = if (!search.isNullOrBlank()) arrayOf("%$search%", "%$search%", "%$search%") else arrayOf()
        db.rawQuery("SELECT * FROM Trainers $where ORDER BY LastName", args).use { c ->
            val list = mutableListOf<Trainer>()
            while (c.moveToNext()) list += c.toTrainer()
            list
        }
    }

    fun byId(id: Long): Trainer? = Database.read { db ->
        db.rawQuery("SELECT * FROM Trainers WHERE ID = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toTrainer() else null
        }
    }

    fun insert(t: Trainer): Long = Database.write { db ->
        val cv = t.toValues().apply { remove("ID") }
        db.insertOrThrow("Trainers", null, cv)
    }

    fun update(t: Trainer) = Database.write { db ->
        db.update("Trainers", t.toValues(), "ID = ?", arrayOf(t.id.toString()))
    }

    /** Удаление тренера; запрещено, если у него есть предстоящие занятия */
    fun delete(id: Long): OpResult = Database.write { db ->
        val future = db.rawQuery(
            "SELECT COUNT(*) FROM WorkoutSessions WHERE ID_Trainer = ? AND StartDateTime >= ?",
            arrayOf(id.toString(), nowStr())
        ).use { it.moveToFirst() && it.getInt(0) > 0 }
        if (future) {
            OpResult.Error("У тренера есть предстоящие занятия. Перенесите или удалите их перед удалением тренера.")
        } else {
            db.delete("Attendance", "ID_Session IN (SELECT ID FROM WorkoutSessions WHERE ID_Trainer = ?)",
                arrayOf(id.toString()))
            db.delete("WorkoutSessions", "ID_Trainer = ?", arrayOf(id.toString()))
            db.delete("Users", "ID_Trainer = ?", arrayOf(id.toString()))
            db.delete("Trainers", "ID = ?", arrayOf(id.toString()))
            OpResult.Ok
        }
    }

    private fun Cursor.toTrainer() = Trainer(
        id = long("ID"),
        lastName = str("LastName") ?: "",
        firstName = str("FirstName") ?: "",
        middleName = str("MiddleName"),
        phone = str("Phone"),
        specialization = str("Specialization"),
        hireDate = str("HireDate")
    )

    private fun Trainer.toValues() = ContentValues().apply {
        put("ID", id)
        put("LastName", lastName)
        put("FirstName", firstName)
        put("MiddleName", middleName)
        put("Phone", phone)
        put("Specialization", specialization)
        put("HireDate", hireDate)
    }
}

// ----------------------------------------------------------------------
// Расписание занятий
// ----------------------------------------------------------------------

class ScheduleRepo {
    /** Занятия с данными о типе, тренере и числе записавшихся */
    fun sessions(
        from: LocalDate? = null,
        to: LocalDate? = null,
        trainerId: Long? = null,
        typeId: Long? = null,
        hall: String? = null,
        includePast: Boolean = false
    ): List<SessionDetails> = Database.read { db ->
        val where = buildString {
            if (from != null) append(" AND datetime(s.StartDateTime) >= datetime(?)")
            if (to != null) append(" AND datetime(s.StartDateTime) < datetime(?, '+1 day')")
            if (trainerId != null) append(" AND s.ID_Trainer = ?")
            if (typeId != null) append(" AND s.ID_SessionType = ?")
            if (hall != null) append(" AND s.Hall = ?")
            if (!includePast) append(" AND datetime(s.StartDateTime) >= datetime('now', 'localtime')")
        }
        val query = "SELECT s.*, st.TypeName AS TypeName, t.LastName AS TLastName, t.FirstName AS TFirstName, t.MiddleName AS TMiddle " +
                "FROM WorkoutSessions s " +
                "LEFT JOIN SessionTypes st ON st.ID = s.ID_SessionType " +
                "LEFT JOIN Trainers t ON t.ID = s.ID_Trainer " +
                "WHERE 1=1 $where ORDER BY s.StartDateTime"
        val argList = mutableListOf<String>()
        if (from != null) argList += from.atStartOfDay().format(DATETIME)
        if (to != null) argList += "$to"
        if (trainerId != null) argList += trainerId.toString()
        if (typeId != null) argList += typeId.toString()
        if (hall != null) argList += hall
        db.rawQuery(query, argList.toTypedArray()).use { c ->
            val list = mutableListOf<SessionDetails>()
            while (c.moveToNext()) {
                val sessionId = c.long("ID")
                val booked = db.rawQuery(
                    "SELECT COUNT(*) FROM Attendance WHERE ID_Session = ? AND Status IN (?, ?)",
                    arrayOf(sessionId.toString(), AttendanceStatus.BOOKED, AttendanceStatus.VISITED)
                ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
                val trainer = Trainer(
                    c.long("ID_Trainer"), c.str("TLastName") ?: "", c.str("TFirstName") ?: "",
                    c.str("TMiddle"), null, null, null
                )
                list += SessionDetails(
                    session = WorkoutSession(
                        id = sessionId,
                        sessionName = c.str("SessionName") ?: "",
                        idSessionType = c.str("ID_SessionType")?.toLongOrNull(),
                        idTrainer = c.str("ID_Trainer")?.toLongOrNull(),
                        startDateTime = c.str("StartDateTime"),
                        duration = c.intOrNull("Duration") ?: 60,
                        maxParticipants = c.intOrNull("MaxParticipants") ?: 0,
                        hall = c.str("Hall")
                    ),
                    typeName = c.str("TypeName") ?: "",
                    trainerFio = trainer.fio,
                    bookedCount = booked
                )
            }
            list
        }
    }

    fun byId(id: Long): WorkoutSession? = Database.read { db ->
        db.rawQuery("SELECT * FROM WorkoutSessions WHERE ID = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) WorkoutSession(
                c.long("ID"), c.str("SessionName") ?: "", c.str("ID_SessionType")?.toLongOrNull(),
                c.str("ID_Trainer")?.toLongOrNull(), c.str("StartDateTime"),
                c.intOrNull("Duration") ?: 60, c.intOrNull("MaxParticipants") ?: 0, c.str("Hall")
            ) else null
        }
    }

    /** Проверка пересечений по времени для одного тренера и зала (ТЗ п. 4.2.3) */
    fun checkConflict(
        excludeId: Long?, trainerId: Long, hall: String, startDateTime: String, duration: Int
    ): String? = Database.read { db ->
        val query = "SELECT s.SessionName, s.Hall, s.ID_Trainer, t.LastName FROM WorkoutSessions s " +
                "LEFT JOIN Trainers t ON t.ID = s.ID_Trainer " +
                "WHERE s.ID <> ? AND (s.ID_Trainer = ? OR s.Hall = ?) " +
                "AND date(s.StartDateTime) = date(?) " +
                "AND datetime(?) < datetime(s.StartDateTime, '+' || s.Duration || ' minutes') " +
                "AND datetime(s.StartDateTime) < datetime(?, '+' || ? || ' minutes')"
        val args = arrayOf(
            (excludeId ?: -1).toString(), trainerId.toString(), hall, startDateTime,
            startDateTime, startDateTime, duration.toString()
        )
        db.rawQuery(query, args).use { c ->
            if (c.moveToFirst()) {
                val other = c.str("SessionName") ?: "занятие"
                if (c.long("ID_Trainer") == trainerId)
                    "Тренер уже занят в это время ($other)"
                else
                    "Зал пересекается с другим занятием ($other)"
            } else null
        }
    }

    fun insert(s: WorkoutSession, trainerId: Long): OpResult {
        val conflict = checkConflict(null, trainerId, s.hall ?: "", s.startDateTime ?: "", s.duration)
        if (conflict != null) return OpResult.Error(conflict)
        return try {
            Database.write { db ->
                db.insertOrThrow("WorkoutSessions", null, s.toValues().apply { remove("ID") })
            }
            OpResult.Ok
        } catch (e: Exception) {
            OpResult.Error(DbHelper.cleanError(e))
        }
    }

    fun update(s: WorkoutSession, trainerId: Long): OpResult {
        val conflict = checkConflict(s.id, trainerId, s.hall ?: "", s.startDateTime ?: "", s.duration)
        if (conflict != null) return OpResult.Error(conflict)
        return try {
            Database.write { db ->
                db.update("WorkoutSessions", s.toValues(), "ID = ?", arrayOf(s.id.toString()))
            }
            OpResult.Ok
        } catch (e: Exception) {
            OpResult.Error(DbHelper.cleanError(e))
        }
    }

    fun delete(id: Long) = Database.write { db ->
        db.delete("Attendance", "ID_Session = ?", arrayOf(id.toString()))
        db.delete("WorkoutSessions", "ID = ?", arrayOf(id.toString()))
    }

    private fun WorkoutSession.toValues() = ContentValues().apply {
        put("ID", id)
        put("SessionName", sessionName)
        put("ID_SessionType", idSessionType)
        put("ID_Trainer", idTrainer)
        put("StartDateTime", startDateTime)
        put("Duration", duration)
        put("MaxParticipants", maxParticipants)
        put("Hall", hall)
    }
}

// ----------------------------------------------------------------------
// Типы занятий и типы абонементов (справочники)
// ----------------------------------------------------------------------

class TypesRepo {
    fun sessionTypes(): List<SessionType> = Database.read { db ->
        db.rawQuery("SELECT * FROM SessionTypes ORDER BY TypeName", null).use { c ->
            val list = mutableListOf<SessionType>()
            while (c.moveToNext()) list += SessionType(c.long("ID"), c.str("TypeName") ?: "", c.double("Price"))
            list
        }
    }

    fun insertSessionType(name: String, price: Double): Long = Database.write { db ->
        db.insertOrThrow("SessionTypes", null, ContentValues().apply {
            put("TypeName", name); put("Price", price)
        })
    }

    fun deleteSessionType(id: Long): OpResult = Database.write { db ->
        val used = db.rawQuery("SELECT COUNT(*) FROM WorkoutSessions WHERE ID_SessionType = ?", arrayOf(id.toString()))
            .use { it.moveToFirst() && it.getInt(0) > 0 }
        if (used) OpResult.Error("Тип используется в расписании занятий")
        else { db.delete("SessionTypes", "ID = ?", arrayOf(id.toString())); OpResult.Ok }
    }

    fun membershipTypes(): List<MembershipType> = Database.read { db ->
        db.rawQuery("SELECT * FROM MembershipTypes ORDER BY Price", null).use { c ->
            val list = mutableListOf<MembershipType>()
            while (c.moveToNext()) list += MembershipType(
                c.long("ID"), c.str("TypeName") ?: "", c.intOrNull("Duration") ?: 0,
                c.double("Price") ?: 0.0, c.str("Description")
            )
            list
        }
    }

    fun membershipTypeById(id: Long): MembershipType? = Database.read { db ->
        db.rawQuery("SELECT * FROM MembershipTypes WHERE ID = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) MembershipType(
                c.long("ID"), c.str("TypeName") ?: "", c.intOrNull("Duration") ?: 0,
                c.double("Price") ?: 0.0, c.str("Description")
            ) else null
        }
    }

    fun insertMembershipType(t: MembershipType): Long = Database.write { db ->
        db.insertOrThrow("MembershipTypes", null, ContentValues().apply {
            put("TypeName", t.typeName); put("Duration", t.duration)
            put("Price", t.price); put("Description", t.description)
        })
    }

    fun updateMembershipType(t: MembershipType) = Database.write { db ->
        db.update("MembershipTypes", ContentValues().apply {
            put("TypeName", t.typeName); put("Duration", t.duration)
            put("Price", t.price); put("Description", t.description)
        }, "ID = ?", arrayOf(t.id.toString()))
    }

    fun deleteMembershipType(id: Long): OpResult = Database.write { db ->
        val used = db.rawQuery("SELECT COUNT(*) FROM Memberships WHERE ID_MembershipType = ?", arrayOf(id.toString()))
            .use { it.moveToFirst() && it.getInt(0) > 0 }
        if (used) OpResult.Error("Тип уже продавался — удаление невозможно")
        else { db.delete("MembershipTypes", "ID = ?", arrayOf(id.toString())); OpResult.Ok }
    }
}

// ----------------------------------------------------------------------
// Абонементы
// ----------------------------------------------------------------------

class MembershipRepo {
    private val types = TypesRepo()

    /** Список абонементов с данными клиента и типа */
    fun all(search: String? = null): List<MembershipDetails> = Database.read { db ->
        val where = if (!search.isNullOrBlank())
            "AND (c.LastName LIKE ? OR mt.TypeName LIKE ? OR m.Status LIKE ?)" else ""
        val args = if (!search.isNullOrBlank()) arrayOf("%$search%", "%$search%", "%$search%") else arrayOf()
        db.rawQuery(
            "SELECT m.*, c.LastName AS CLast, c.FirstName AS CFirst, c.MiddleName AS CMid, " +
                    "mt.TypeName AS MType, mt.Duration AS MDuration " +
                    "FROM Memberships m " +
                    "JOIN Clients c ON c.ID = m.ID_Client " +
                    "JOIN MembershipTypes mt ON mt.ID = m.ID_MembershipType " +
                    "WHERE 1=1 $where ORDER BY m.EndDate DESC", args
        ).use { c ->
            val list = mutableListOf<MembershipDetails>()
            while (c.moveToNext()) {
                val client = Client(
                    c.long("ID_Client"), c.str("CLast") ?: "", c.str("CFirst") ?: "",
                    c.str("CMid"), null, null, "", null, null, null
                )
                list += MembershipDetails(
                    membership = Membership(
                        c.long("ID"), c.long("ID_Client"), c.long("ID_MembershipType"),
                        c.double("PurchasePrice"), c.str("StartDate"), c.str("EndDate"), c.str("Status")
                    ),
                    clientFio = client.fio,
                    typeName = c.str("MType") ?: "",
                    duration = c.intOrNull("MDuration") ?: 0
                )
            }
            list
        }
    }

    fun byId(id: Long): Membership? = Database.read { db ->
        db.rawQuery("SELECT * FROM Memberships WHERE ID = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) Membership(
                c.long("ID"), c.long("ID_Client"), c.long("ID_MembershipType"),
                c.double("PurchasePrice"), c.str("StartDate"), c.str("EndDate"), c.str("Status")
            ) else null
        }
    }

    /** Действующий абонемент клиента (статус «Активен», срок не истёк) */
    fun activeMembership(clientId: Long): Membership? = Database.read { db ->
        db.rawQuery(
            "SELECT * FROM Memberships WHERE ID_Client = ? AND Status = ? AND EndDate >= ? " +
                    "ORDER BY EndDate DESC LIMIT 1",
            arrayOf(clientId.toString(), MembershipStatus.ACTIVE, todayStr())
        ).use { c ->
            if (c.moveToFirst()) Membership(
                c.long("ID"), c.long("ID_Client"), c.long("ID_MembershipType"),
                c.double("PurchasePrice"), c.str("StartDate"), c.str("EndDate"), c.str("Status")
            ) else null
        }
    }

    /** Все абонементы клиента (для карточки и профиля) */
    fun forClient(clientId: Long): List<ClientMembership> = Database.read { db ->
        db.rawQuery(
            "SELECT m.*, mt.TypeName AS MType FROM Memberships m " +
                    "JOIN MembershipTypes mt ON mt.ID = m.ID_MembershipType " +
                    "WHERE m.ID_Client = ? ORDER BY m.StartDate DESC",
            arrayOf(clientId.toString())
        ).use { c ->
            val list = mutableListOf<ClientMembership>()
            while (c.moveToNext()) {
                val m = Membership(
                    c.long("ID"), c.long("ID_Client"), c.long("ID_MembershipType"),
                    c.double("PurchasePrice"), c.str("StartDate"), c.str("EndDate"), c.str("Status")
                )
                val daysLeft = parseDate(m.endDate)?.toEpochDay()?.minus(LocalDate.now().toEpochDay()) ?: -1
                list += ClientMembership(m, c.str("MType") ?: "", daysLeft)
            }
            list
        }
    }

    /**
     * Оформление нового абонемента с автоматическим платежом.
     * По ТЗ п. 4.2.2: система проверяет отсутствие другого действующего абонемента.
     */
    fun purchase(clientId: Long, typeId: Long, method: String): OpResult {
        if (activeMembership(clientId) != null) {
            return OpResult.Error("У клиента уже есть действующий абонемент. Используйте продление.")
        }
        val type = types.membershipTypeById(typeId) ?: return OpResult.Error("Тип абонемента не найден")
        return Database.write { db ->
            db.beginTransaction()
            try {
                val start = todayStr()
                val end = LocalDate.now().plusDays(type.duration.toLong()).format(DATE)
                val membershipId = db.insertOrThrow("Memberships", null, ContentValues().apply {
                    put("ID_Client", clientId)
                    put("ID_MembershipType", typeId)
                    put("PurchasePrice", type.price)
                    put("StartDate", start)
                    put("EndDate", end)
                    put("Status", MembershipStatus.ACTIVE)
                })
                db.insertOrThrow("Payments", null, ContentValues().apply {
                    put("ID_Membership", membershipId)
                    put("PaymentDate", nowStr())
                    put("Amount", type.price)
                    put("PaymentMethod", method)
                    put("Status", PaymentStatus.PAID)
                })
                db.setTransactionSuccessful()
                OpResult.Ok
            } catch (e: Exception) {
                OpResult.Error(DbHelper.cleanError(e))
            } finally {
                db.endTransaction()
            }
        }
    }

    /** Продление абонемента (ТЗ п. 4.2.2): дата окончания += длительность типа, статус → «Активен» */
    fun renew(membershipId: Long, method: String): OpResult {
        val m = byId(membershipId) ?: return OpResult.Error("Абонемент не найден")
        val type = types.membershipTypeById(m.idMembershipType) ?: return OpResult.Error("Тип абонемента не найден")
        return Database.write { db ->
            db.beginTransaction()
            try {
                val base = maxOf(parseDate(m.endDate) ?: LocalDate.now(), LocalDate.now())
                val newEnd = base.plusDays(type.duration.toLong()).format(DATE)
                db.update("Memberships", ContentValues().apply {
                    put("EndDate", newEnd)
                    put("Status", MembershipStatus.ACTIVE)
                }, "ID = ?", arrayOf(membershipId.toString()))
                db.insertOrThrow("Payments", null, ContentValues().apply {
                    put("ID_Membership", membershipId)
                    put("PaymentDate", nowStr())
                    put("Amount", type.price)
                    put("PaymentMethod", method)
                    put("Status", PaymentStatus.PAID)
                })
                db.setTransactionSuccessful()
                OpResult.Ok
            } catch (e: Exception) {
                OpResult.Error(DbHelper.cleanError(e))
            } finally {
                db.endTransaction()
            }
        }
    }

    /** Заморозка: статус «Заморожен», дата окончания увеличивается на число дней заморозки */
    fun freeze(membershipId: Long, days: Int): OpResult {
        val m = byId(membershipId) ?: return OpResult.Error("Абонемент не найден")
        if (days <= 0) return OpResult.Error("Укажите положительное число дней")
        return Database.write { db ->
            val base = maxOf(parseDate(m.endDate) ?: LocalDate.now(), LocalDate.now())
            val newEnd = base.plusDays(days.toLong()).format(DATE)
            db.update("Memberships", ContentValues().apply {
                put("Status", MembershipStatus.FROZEN)
                put("EndDate", newEnd)
            }, "ID = ?", arrayOf(membershipId.toString()))
            OpResult.Ok
        }
    }

    fun setStatus(membershipId: Long, status: String) = Database.write { db ->
        db.update("Memberships", ContentValues().apply { put("Status", status) }, "ID = ?", arrayOf(membershipId.toString()))
    }

    fun delete(id: Long) = Database.write { db ->
        db.delete("Payments", "ID_Membership = ?", arrayOf(id.toString()))
        db.delete("Memberships", "ID = ?", arrayOf(id.toString()))
    }
}

// ----------------------------------------------------------------------
// Платежи и отчётность
// ----------------------------------------------------------------------

class PaymentsRepo {
    fun all(from: LocalDate? = null, to: LocalDate? = null): List<PaymentDetails> = Database.read { db ->
        val where = buildString {
            if (from != null) append(" AND date(p.PaymentDate) >= date(?)")
            if (to != null) append(" AND date(p.PaymentDate) <= date(?)")
        }
        val args = mutableListOf<String>()
        if (from != null) args += from.toString()
        if (to != null) args += to.toString()
        db.rawQuery(
            "SELECT p.*, c.LastName AS CLast, c.FirstName AS CFirst, c.MiddleName AS CMid, mt.TypeName AS MType " +
                    "FROM Payments p " +
                    "LEFT JOIN Memberships m ON m.ID = p.ID_Membership " +
                    "LEFT JOIN Clients c ON c.ID = m.ID_Client " +
                    "LEFT JOIN MembershipTypes mt ON mt.ID = m.ID_MembershipType " +
                    "WHERE 1=1 $where ORDER BY p.PaymentDate DESC", args.toTypedArray()
        ).use { c ->
            val list = mutableListOf<PaymentDetails>()
            while (c.moveToNext()) {
                val client = Client(
                    -1, c.str("CLast") ?: "", c.str("CFirst") ?: "", c.str("CMid"),
                    null, null, "", null, null, null
                )
                list += PaymentDetails(
                    payment = Payment(
                        c.long("ID"), c.str("ID_Membership")?.toLongOrNull(), c.str("PaymentDate"),
                        c.double("Amount"), c.str("PaymentMethod"), c.str("Status")
                    ),
                    clientFio = client.fio.ifBlank { "—" },
                    membershipTypeName = c.str("MType") ?: ""
                )
            }
            list
        }
    }

    fun delete(id: Long) = Database.write { db ->
        db.delete("Payments", "ID = ?", arrayOf(id.toString()))
    }

    /** Финансовый отчёт за период: общая сумма, разбивка по способам оплаты и типам абонементов */
    data class Report(
        val total: Double,
        val count: Int,
        val byMethod: Map<String, Double>,
        val byType: Map<String, Double>
    )

    fun report(from: LocalDate, to: LocalDate): Report {
        val list = all(from, to).filter { it.payment.status == PaymentStatus.PAID }
        return Report(
            total = list.sumOf { it.payment.amount ?: 0.0 },
            count = list.size,
            byMethod = list.groupBy { it.payment.paymentMethod ?: "—" }
                .mapValues { e -> e.value.sumOf { it.payment.amount ?: 0.0 } },
            byType = list.groupBy { it.membershipTypeName.ifBlank { "—" } }
                .mapValues { e -> e.value.sumOf { it.payment.amount ?: 0.0 } }
        )
    }
}

// ----------------------------------------------------------------------
// Посещения и запись на занятия
// ----------------------------------------------------------------------

class AttendanceRepo {
    /** Записи клиентов на занятие (для тренера) */
    fun forSession(sessionId: Long): List<AttendanceDetails> = Database.read { db ->
        db.rawQuery(
            "SELECT a.*, c.LastName AS CLast, c.FirstName AS CFirst, c.MiddleName AS CMid, " +
                    "s.SessionName AS SName, s.StartDateTime AS SStart, s.Hall AS SHall " +
                    "FROM Attendance a JOIN Clients c ON c.ID = a.ID_Client " +
                    "LEFT JOIN WorkoutSessions s ON s.ID = a.ID_Session " +
                    "WHERE a.ID_Session = ? ORDER BY c.LastName", arrayOf(sessionId.toString())
        ).use { c -> c.collect() }
    }

    /** Записи клиента: предстоящие (bookingOnly=true) или вся история */
    fun forClient(clientId: Long, upcomingOnly: Boolean = false): List<AttendanceDetails> = Database.read { db ->
        val where = if (upcomingOnly)
            "AND a.Status = ? AND datetime(a.VisitDateTime) >= datetime('now','localtime')"
        else ""
        val args = if (upcomingOnly) arrayOf(clientId.toString(), AttendanceStatus.BOOKED) else arrayOf(clientId.toString())
        db.rawQuery(
            "SELECT a.*, c.LastName AS CLast, c.FirstName AS CFirst, c.MiddleName AS CMid, " +
                    "s.SessionName AS SName, s.StartDateTime AS SStart, s.Hall AS SHall, t.LastName AS TLast, t.FirstName AS TFirst, t.MiddleName AS TMid " +
                    "FROM Attendance a JOIN Clients c ON c.ID = a.ID_Client " +
                    "LEFT JOIN WorkoutSessions s ON s.ID = a.ID_Session " +
                    "LEFT JOIN Trainers t ON t.ID = s.ID_Trainer " +
                    "WHERE a.ID_Client = ? $where ORDER BY a.VisitDateTime DESC", args
        ).use { c -> c.collect(withTrainer = true) }
    }

    private fun Cursor.collect(withTrainer: Boolean = false): List<AttendanceDetails> {
        val list = mutableListOf<AttendanceDetails>()
        while (moveToNext()) {
            val client = Client(
                -1, str("CLast") ?: "", str("CFirst") ?: "", str("CMid"), null, null, "", null, null, null
            )
            val trainerFio = if (withTrainer) {
                val t = Trainer(-1, str("TLast") ?: "", str("TFirst") ?: "", str("TMid"), null, null, null)
                t.fio
            } else null
            list += AttendanceDetails(
                attendance = Attendance(
                    long("ID"), long("ID_Client"), str("ID_Session")?.toLongOrNull(),
                    str("VisitDateTime"), (intOrNull("Paid") ?: 0) == 1, str("Status") ?: ""
                ),
                clientFio = client.fio,
                sessionName = str("SName"),
                trainerFio = trainerFio,
                hall = str("SHall"),
                startDateTime = str("SStart")
            )
        }
        return list
    }

    /**
     * Запись клиента на занятие (ТЗ п. 4.2.3): проверка активного абонемента,
     * свободных мест и повторной записи.
     */
    fun book(clientId: Long, sessionId: Long): OpResult {
        val membershipRepo = MembershipRepo()
        if (membershipRepo.activeMembership(clientId) == null) {
            return OpResult.Error("Запись возможна только при действующем абонементе. Оформите или продлите абонемент.")
        }
        return Database.write { db ->
            val session = db.rawQuery("SELECT * FROM WorkoutSessions WHERE ID = ?", arrayOf(sessionId.toString()))
                .use { c ->
                    if (c.moveToFirst()) WorkoutSession(
                        c.long("ID"), c.str("SessionName") ?: "", null, null, c.str("StartDateTime"),
                        c.intOrNull("Duration") ?: 60, c.intOrNull("MaxParticipants") ?: 0, c.str("Hall")
                    ) else null
                } ?: return@write OpResult.Error("Занятие не найдено")

            val booked = db.rawQuery(
                "SELECT COUNT(*) FROM Attendance WHERE ID_Session = ? AND Status IN (?, ?)",
                arrayOf(sessionId.toString(), AttendanceStatus.BOOKED, AttendanceStatus.VISITED)
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (booked >= session.maxParticipants) {
                return@write OpResult.Error("Свободных мест нет (${session.maxParticipants} из ${session.maxParticipants} занято)")
            }

            val already = db.rawQuery(
                "SELECT COUNT(*) FROM Attendance WHERE ID_Session = ? AND ID_Client = ? AND Status = ?",
                arrayOf(sessionId.toString(), clientId.toString(), AttendanceStatus.BOOKED)
            ).use { it.moveToFirst() && it.getInt(0) > 0 }
            if (already) return@write OpResult.Error("Вы уже записаны на это занятие")

            try {
                db.insertOrThrow("Attendance", null, ContentValues().apply {
                    put("ID_Client", clientId)
                    put("ID_Session", sessionId)
                    put("VisitDateTime", session.startDateTime)
                    put("Paid", 1)
                    put("Status", AttendanceStatus.BOOKED)
                })
                OpResult.Ok
            } catch (e: Exception) {
                OpResult.Error(DbHelper.cleanError(e))
            }
        }
    }

    /** Отмена записи клиентом */
    fun cancelBooking(attendanceId: Long) = Database.write { db ->
        db.update("Attendance", ContentValues().apply { put("Status", AttendanceStatus.CANCELLED) },
            "ID = ?", arrayOf(attendanceId.toString()))
    }

    /** Отметка посещения тренером (ТЗ п. 4.2.5) */
    fun markVisited(attendanceId: Long, visited: Boolean) = Database.write { db ->
        db.update("Attendance", ContentValues().apply {
            put("Status", if (visited) AttendanceStatus.VISITED else AttendanceStatus.BOOKED)
        }, "ID = ?", arrayOf(attendanceId.toString()))
    }

    /**
     * Регистрация прохода через турникет по карте (ТЗ п. 4.2.5):
     * клиент ищется по номеру карты, проверяется абонемент,
     * создаётся запись свободного посещения без занятия.
     */
    fun registerGymEntry(cardNumber: String): OpResult = Database.write { db ->
        val client = db.rawQuery("SELECT * FROM Clients WHERE CardNumber = ?", arrayOf(cardNumber.trim()))
            .use { c ->
                if (c.moveToFirst()) Client(
                    c.long("ID"), c.str("LastName") ?: "", c.str("FirstName") ?: "", c.str("MiddleName"),
                    null, null, "", null, null, null
                ) else null
            } ?: return@write OpResult.Error("Карта $cardNumber не найдена")

        val active = db.rawQuery(
            "SELECT COUNT(*) FROM Memberships WHERE ID_Client = ? AND Status = ? AND EndDate >= ?",
            arrayOf(client.id.toString(), MembershipStatus.ACTIVE, todayStr())
        ).use { it.moveToFirst() && it.getInt(0) > 0 }

        if (!active) {
            return@write OpResult.Error("Отказано: ${client.fio} — нет действующего абонемента")
        }

        try {
            db.insertOrThrow("Attendance", null, ContentValues().apply {
                put("ID_Client", client.id)
                putNull("ID_Session")
                put("VisitDateTime", nowStr())
                put("Paid", 1)
                put("Status", AttendanceStatus.VISITED)
            })
            OpResult.Ok
        } catch (e: Exception) {
            OpResult.Error(DbHelper.cleanError(e))
        }
    }

    /** Журнал проходов через турникет (свободные посещения) */
    fun accessLog(limit: Int = 30): List<AttendanceDetails> = Database.read { db ->
        db.rawQuery(
            "SELECT a.*, c.LastName AS CLast, c.FirstName AS CFirst, c.MiddleName AS CMid, " +
                    "s.SessionName AS SName, s.StartDateTime AS SStart, s.Hall AS SHall " +
                    "FROM Attendance a JOIN Clients c ON c.ID = a.ID_Client " +
                    "LEFT JOIN WorkoutSessions s ON s.ID = a.ID_Session " +
                    "WHERE a.ID_Session IS NULL ORDER BY a.VisitDateTime DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            val list = mutableListOf<AttendanceDetails>()
            while (c.moveToNext()) {
                val client = Client(
                    -1, c.str("CLast") ?: "", c.str("CFirst") ?: "", c.str("CMid"),
                    null, null, "", null, null, null
                )
                list += AttendanceDetails(
                    Attendance(
                        c.long("ID"), c.long("ID_Client"), null, c.str("VisitDateTime"),
                        (c.intOrNull("Paid") ?: 0) == 1, c.str("Status") ?: ""
                    ),
                    client.fio, null, null, null, null
                )
            }
            list
        }
    }
}
