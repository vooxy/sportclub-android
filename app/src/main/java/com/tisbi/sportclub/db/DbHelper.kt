package com.tisbi.sportclub.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * База данных SQLite ИС «Спортивный клуб».
 *
 * Схема повторяет реляционную модель из пояснительной записки (8 таблиц)
 * с доработками по результатам анализа ТЗ:
 *  - WorkoutSessions.Hall — зал занятия (контроль пересечений по залу);
 *  - Clients.CardNumber — номер клиентской карты для турникета;
 *  - Users — учётные записи и роли для авторизации;
 *  - Attendance.Status — «Записан / Пришёл / Отменил»;
 *  - Attendance.ID_Session допускает NULL — свободное посещение по карте.
 *
 * Целостность данных обеспечивается тремя механизмами:
 *  1) внешние ключи (PRAGMA foreign_keys = ON);
 *  2) транзакции в репозиториях (оформление и продление абонемента);
 *  3) триггеры SQLite — последний рубеж защиты (см. createTriggers).
 */
class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "sport_club.db"
        const val DB_VERSION = 2

        fun sha256(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        /** Чистое сообщение об ошибке из исключения SQLite (без служебного префикса) */
        fun cleanError(e: Exception): String {
            val msg = e.message ?: "Операция отклонена базой данных"
            return msg.substringAfter(":", msg).trim().ifEmpty { msg }
        }
    }

    /** Контроль ссылочной целостности внешними ключами */
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createTables(db)
        seed(db)
        createTriggers(db) // триггеры создаются после начального заполнения
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        val tables = listOf(
            "Users", "Attendance", "Payments", "Memberships", "WorkoutSessions",
            "SessionTypes", "MembershipTypes", "Trainers", "Clients"
        )
        tables.forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        onCreate(db)
    }

    private fun createTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE Clients (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                LastName TEXT NOT NULL,
                FirstName TEXT NOT NULL,
                MiddleName TEXT,
                Gender TEXT,
                BirthDate TEXT,
                Phone TEXT NOT NULL,
                Email TEXT,
                CardNumber TEXT UNIQUE,
                RegistrationDate TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE Trainers (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                LastName TEXT NOT NULL,
                FirstName TEXT NOT NULL,
                MiddleName TEXT,
                Phone TEXT,
                Specialization TEXT,
                HireDate TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE MembershipTypes (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                TypeName TEXT NOT NULL,
                Duration INTEGER NOT NULL,
                Price REAL NOT NULL,
                Description TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE Memberships (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                ID_Client INTEGER NOT NULL REFERENCES Clients(ID),
                ID_MembershipType INTEGER NOT NULL REFERENCES MembershipTypes(ID),
                PurchasePrice REAL,
                StartDate TEXT,
                EndDate TEXT,
                Status TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE Payments (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                ID_Membership INTEGER REFERENCES Memberships(ID),
                PaymentDate TEXT,
                Amount REAL,
                PaymentMethod TEXT,
                Status TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE SessionTypes (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                TypeName TEXT NOT NULL,
                Price REAL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE WorkoutSessions (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                SessionName TEXT NOT NULL,
                ID_SessionType INTEGER REFERENCES SessionTypes(ID),
                ID_Trainer INTEGER REFERENCES Trainers(ID),
                StartDateTime TEXT,
                Duration INTEGER,
                MaxParticipants INTEGER,
                Hall TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE Attendance (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                ID_Client INTEGER NOT NULL REFERENCES Clients(ID),
                ID_Session INTEGER REFERENCES WorkoutSessions(ID),
                VisitDateTime TEXT,
                Paid INTEGER,
                Status TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE Users (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                Login TEXT NOT NULL UNIQUE,
                PasswordHash TEXT NOT NULL,
                Role TEXT NOT NULL,
                ID_Client INTEGER REFERENCES Clients(ID),
                ID_Trainer INTEGER REFERENCES Trainers(ID),
                FullName TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_clients_lastname ON Clients(LastName)")
        db.execSQL("CREATE INDEX idx_sessions_start ON WorkoutSessions(StartDateTime)")
        db.execSQL("CREATE INDEX idx_attendance_client ON Attendance(ID_Client)")
    }

    // ------------------------------------------------------------------
    // Триггеры контроля целостности (создаются после начального заполнения)
    // ------------------------------------------------------------------

    private fun createTriggers(db: SQLiteDatabase) {
        // 1. Запрет повторной активной записи клиента на то же занятие
        db.execSQL(
            """
            CREATE TRIGGER trg_attendance_unique
            BEFORE INSERT ON Attendance
            FOR EACH ROW WHEN NEW.ID_Session IS NOT NULL AND NEW.Status = 'Записан'
            BEGIN
                SELECT CASE WHEN EXISTS (
                    SELECT 1 FROM Attendance a
                    WHERE a.ID_Session = NEW.ID_Session
                      AND a.ID_Client = NEW.ID_Client
                      AND a.Status IN ('Записан', 'Пришёл')
                ) THEN RAISE(ABORT, 'Клиент уже записан на это занятие') END;
            END
            """.trimIndent()
        )
        // 2. Запрет записи на занятие без свободных мест
        db.execSQL(
            """
            CREATE TRIGGER trg_attendance_capacity
            BEFORE INSERT ON Attendance
            FOR EACH ROW WHEN NEW.ID_Session IS NOT NULL AND NEW.Status = 'Записан'
            BEGIN
                SELECT CASE WHEN (
                    SELECT COUNT(*) FROM Attendance a
                    WHERE a.ID_Session = NEW.ID_Session
                      AND a.Status IN ('Записан', 'Пришёл')
                ) >= COALESCE((
                    SELECT MaxParticipants FROM WorkoutSessions WHERE ID = NEW.ID_Session
                ), 0) THEN RAISE(ABORT, 'Свободных мест на занятии нет') END;
            END
            """.trimIndent()
        )
        // 3. При записи на занятие у клиента должен быть действующий абонемент
        db.execSQL(
            """
            CREATE TRIGGER trg_attendance_membership
            BEFORE INSERT ON Attendance
            FOR EACH ROW WHEN NEW.ID_Session IS NOT NULL AND NEW.Status = 'Записан'
            BEGIN
                SELECT CASE WHEN NOT EXISTS (
                    SELECT 1 FROM Memberships m
                    WHERE m.ID_Client = NEW.ID_Client
                      AND m.Status = 'Активен'
                      AND m.EndDate >= date(NEW.VisitDateTime)
                ) THEN RAISE(ABORT, 'Запись возможна только при действующем абонементе') END;
            END
            """.trimIndent()
        )
        // 4. Не более одного действующего абонемента у клиента
        db.execSQL(
            """
            CREATE TRIGGER trg_membership_single_active
            BEFORE INSERT ON Memberships
            FOR EACH ROW WHEN NEW.Status = 'Активен'
            BEGIN
                SELECT CASE WHEN EXISTS (
                    SELECT 1 FROM Memberships m
                    WHERE m.ID_Client = NEW.ID_Client
                      AND m.Status = 'Активен'
                      AND m.EndDate >= NEW.StartDate
                ) THEN RAISE(ABORT, 'У клиента уже есть действующий абонемент') END;
            END
            """.trimIndent()
        )
        // 5. Дата окончания абонемента должна быть позже даты начала
        db.execSQL(
            """
            CREATE TRIGGER trg_membership_dates
            BEFORE INSERT ON Memberships
            FOR EACH ROW WHEN NEW.EndDate <= NEW.StartDate
            BEGIN
                SELECT RAISE(ABORT, 'Дата окончания абонемента должна быть позже даты начала');
            END
            """.trimIndent()
        )
        // 6. Автоматический перевод истёкшего абонемента в статус «Неактивен»
        db.execSQL(
            """
            CREATE TRIGGER trg_membership_autoclose
            AFTER UPDATE OF EndDate, Status ON Memberships
            FOR EACH ROW
            WHEN NEW.Status = 'Активен' AND NEW.EndDate < date('now')
            BEGIN
                UPDATE Memberships SET Status = 'Неактивен' WHERE ID = NEW.ID;
            END
            """.trimIndent()
        )
        // 7. Сумма платежа не может быть отрицательной
        db.execSQL(
            """
            CREATE TRIGGER trg_payment_positive
            BEFORE INSERT ON Payments
            FOR EACH ROW WHEN NEW.Amount < 0
            BEGIN
                SELECT RAISE(ABORT, 'Сумма платежа не может быть отрицательной');
            END
            """.trimIndent()
        )
        // 8. Запрет удаления клиента, у которого есть платежи
        db.execSQL(
            """
            CREATE TRIGGER trg_client_delete_guard
            BEFORE DELETE ON Clients
            FOR EACH ROW WHEN EXISTS (
                SELECT 1 FROM Payments p
                JOIN Memberships m ON m.ID = p.ID_Membership
                WHERE m.ID_Client = OLD.ID
            )
            BEGIN
                SELECT RAISE(ABORT, 'У клиента есть платежи — удаление запрещено');
            END
            """.trimIndent()
        )
        // 9. Новое занятие не может назначаться в прошедшее время
        db.execSQL(
            """
            CREATE TRIGGER trg_session_past
            BEFORE INSERT ON WorkoutSessions
            FOR EACH ROW WHEN NEW.StartDateTime <= datetime('now', 'localtime')
            BEGIN
                SELECT RAISE(ABORT, 'Занятие нельзя назначать в прошедшее время');
            END
            """.trimIndent()
        )
        // 10. Длительность занятия — от 30 до 300 минут
        db.execSQL(
            """
            CREATE TRIGGER trg_session_duration
            BEFORE INSERT ON WorkoutSessions
            FOR EACH ROW WHEN NEW.Duration < 30 OR NEW.Duration > 300
            BEGIN
                SELECT RAISE(ABORT, 'Длительность занятия должна быть от 30 до 300 минут');
            END
            """.trimIndent()
        )
    }

    // ------------------------------------------------------------------
    // Контрольные (демонстрационные) данные: не менее 10 записей в таблицах
    // ------------------------------------------------------------------

    private fun seed(db: SQLiteDatabase) {
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val today = LocalDate.now()
        val pwd = sha256("1234")

        fun d(days: Long): String = today.plusDays(days).format(date)
        fun dt(days: Long, hour: Int, minute: Int = 0): String =
            LocalDateTime.of(today.plusDays(days), LocalTime.of(hour, minute)).format(dateTime)

        fun sql(statement: String) = db.execSQL(statement)

        // Тренеры (10)
        sql("INSERT INTO Trainers (ID, LastName, FirstName, MiddleName, Phone, Specialization, HireDate) VALUES " +
                "(1,'Иванова','Анна','Сергеевна','+7 (917) 123-45-67','Йога','${d(-900)}')," +
                "(2,'Петров','Дмитрий','Александрович','+7 (917) 234-56-78','Кроссфит','${d(-720)}')," +
                "(3,'Смирнова','Елена','Викторовна','+7 (917) 345-67-89','Пилатес','${d(-500)}')," +
                "(4,'Гареев','Руслан','Маратович','+7 (917) 456-78-90','Бокс','${d(-365)}')," +
                "(5,'Козлов','Максим','Игоревич','+7 (917) 567-89-01','Плавание','${d(-800)}')," +
                "(6,'Ахметзянова','Гульнара','Рафиковна','+7 (917) 678-90-12','Фитнес','${d(-450)}')," +
                "(7,'Ситников','Андрей','Олегович','+7 (917) 789-01-23','Тренажёрный зал','${d(-300)}')," +
                "(8,'Якимова','Наталья','Петровна','+7 (917) 890-12-34','Растяжка','${d(-600)}')," +
                "(9,'Валеев','Тимур','Азатович','+7 (917) 901-23-45','Смешанные единоборства','${d(-200)}')," +
                "(10,'Романова','Екатерина','Сергеевна','+7 (917) 012-34-56','Аэробика','${d(-150)}')")

        // Клиенты (12)
        sql("INSERT INTO Clients (ID, LastName, FirstName, MiddleName, Gender, BirthDate, Phone, Email, CardNumber, RegistrationDate) VALUES " +
                "(1,'Салахов','Карим','Фанисович','м','2004-05-12','+7 (987) 654-32-10','salakhov@mail.ru','СК-000001','${d(-120)}')," +
                "(2,'Кузнецова','Мария','Андреевна','ж','1998-11-03','+7 (987) 111-22-33','kuznetsova@gmail.com','СК-000002','${d(-90)}')," +
                "(3,'Ахметов','Тимур','Ринатович','м','1995-02-27','+7 (987) 222-33-44','ahmetov@yandex.ru','СК-000003','${d(-200)}')," +
                "(4,'Васильева','Ольга','Ивановна','ж','2000-07-15','+7 (987) 333-44-55','vasilyeva@rambler.ru','СК-000004','${d(-60)}')," +
                "(5,'Николаев','Артём','Павлович','м','1992-09-08','+7 (987) 444-55-66','nikolaev@mail.ru','СК-000005','${d(-400)}')," +
                "(6,'Гимадиева','Алия','Наилевна','ж','2003-03-21','+7 (987) 555-66-77','gimadieva@gmail.com','СК-000006','${d(-10)}')," +
                "(7,'Гарипов','Ильдар','Ринатович','м','1990-12-05','+7 (987) 666-77-88','garipov@yandex.ru','СК-000007','${d(-320)}')," +
                "(8,'Сафина','Регина','Марсовна','ж','1997-06-18','+7 (987) 777-88-99','safina@mail.ru','СК-000008','${d(-75)}')," +
                "(9,'Морозов','Егор','Дмитриевич','м','2001-01-30','+7 (987) 888-99-00','morozov@gmail.com','СК-000009','${d(-45)}')," +
                "(10,'Латыпова','Дина','Азатовна','ж','1999-08-22','+7 (987) 999-00-11','latypova@rambler.ru','СК-000010','${d(-30)}')," +
                "(11,'Хайруллин','Алан','Ринатович','м','1996-04-14','+7 (987) 000-11-22','khairullin@yandex.ru','СК-000011','${d(-250)}')," +
                "(12,'Ветрова','Ксения','Павловна','ж','2002-10-09','+7 (987) 121-23-34','vetrova@mail.ru','СК-000012','${d(-15)}')")

        // Типы абонементов (10)
        sql("INSERT INTO MembershipTypes (ID, TypeName, Duration, Price, Description) VALUES " +
                "(1,'Безлимит на месяц',30,3500,'Посещение любых групповых занятий без ограничений')," +
                "(2,'8 занятий',30,2800,'8 посещений групповых занятий в течение месяца')," +
                "(3,'12 занятий',30,3800,'12 посещений групповых занятий в течение месяца')," +
                "(4,'Годовой безлимит',365,25000,'Безлимитный доступ ко всем занятиям на год')," +
                "(5,'Разовое посещение',1,500,'Одно посещение любого группового занятия')," +
                "(6,'Студенческий',30,2000,'Безлимит на месяц при предъявлении студенческого билета')," +
                "(7,'Безлимит на 3 месяца',90,9000,'Безлимитный доступ на три месяца')," +
                "(8,'24 занятия',60,6500,'24 посещения в течение двух месяцев')," +
                "(9,'Дневной безлимит',30,2500,'Безлимит по будням до 17:00')," +
                "(10,'Семейный на месяц',30,5000,'Безлимит для клиента и одного члена семьи')")

        // Типы занятий (10)
        sql("INSERT INTO SessionTypes (ID, TypeName, Price) VALUES " +
                "(1,'Йога',500),(2,'Кроссфит',600),(3,'Пилатес',500),(4,'Бокс',700),(5,'Фитнес',500)," +
                "(6,'Плавание',600),(7,'Тренажёрный зал',550),(8,'Растяжка',450)," +
                "(9,'Смешанные единоборства',750),(10,'Аэробика',500)")

        // Занятия: шаблон на три недели (прошлая неделя для истории, текущая и следующая)
        val rows = mutableListOf<String>()
        var id = 1L
        fun addSession(
            name: String, typeId: Long, trainerId: Long,
            dayOffset: Long, hour: Int, minute: Int, duration: Int, max: Int, hall: String
        ) {
            rows.add(
                "($id,'$name',$typeId,$trainerId,'${dt(dayOffset, hour, minute)}',$duration,$max,'$hall')"
            )
            id++
        }
        for (week in -1..1) {
            val monday = today.plusDays((1 - today.dayOfWeek.value).toLong() + week * 7)
            fun day(dow: Int) = monday.plusDays((dow - 1).toLong()).let {
                it.toEpochDay() - today.toEpochDay()
            }
            addSession("Утренняя йога", 1, 1, day(1), 10, 0, 60, 15, "Зал №1")
            addSession("Йога для начинающих", 1, 1, day(2), 19, 0, 60, 15, "Зал №1")
            addSession("Кроссфит", 2, 2, day(1), 18, 0, 60, 12, "Зал №2")
            addSession("Пилатес", 3, 3, day(2), 12, 0, 50, 10, "Зал №1")
            addSession("Кроссфит", 2, 2, day(3), 18, 0, 60, 12, "Зал №2")
            addSession("Утренняя йога", 1, 1, day(3), 10, 0, 60, 15, "Зал №1")
            addSession("Бокс: группа новичков", 4, 4, day(2), 18, 0, 90, 8, "Зал единоборств")
            addSession("Утренняя йога", 1, 1, day(5), 10, 0, 60, 15, "Зал №1")
            addSession("Кроссфит", 2, 2, day(5), 18, 0, 60, 12, "Зал №2")
            addSession("Пилатес", 3, 3, day(4), 12, 0, 50, 10, "Зал №1")
            addSession("Бокс: группа новичков", 4, 4, day(4), 18, 0, 90, 8, "Зал единоборств")
            addSession("Фитнес-программа", 5, 6, day(6), 11, 0, 60, 20, "Зал №2")
            addSession("Плавание: техника", 6, 5, day(6), 9, 0, 60, 10, "Бассейн")
            addSession("Силовая тренировка", 7, 7, day(3), 20, 0, 90, 12, "Тренажёрный зал")
            addSession("Растяжка", 8, 8, day(5), 19, 0, 45, 14, "Зал №1")
        }
        sql("INSERT INTO WorkoutSessions (ID, SessionName, ID_SessionType, ID_Trainer, StartDateTime, Duration, MaxParticipants, Hall) VALUES " +
                rows.joinToString(","))

        // Абонементы (12)
        sql("INSERT INTO Memberships (ID, ID_Client, ID_MembershipType, PurchasePrice, StartDate, EndDate, Status) VALUES " +
                "(1,1,1,3500,'${d(-5)}','${d(25)}','${MembershipStatus.ACTIVE}')," +
                "(2,2,2,2800,'${d(-10)}','${d(20)}','${MembershipStatus.ACTIVE}')," +
                "(3,3,1,3500,'${d(-40)}','${d(-10)}','${MembershipStatus.INACTIVE}')," +
                "(4,4,3,3800,'${d(-8)}','${d(52)}','${MembershipStatus.FROZEN}')," +
                "(5,5,4,25000,'${d(-100)}','${d(265)}','${MembershipStatus.ACTIVE}')," +
                "(6,7,7,9000,'${d(-60)}','${d(30)}','${MembershipStatus.ACTIVE}')," +
                "(7,8,6,2000,'${d(-25)}','${d(5)}','${MembershipStatus.ACTIVE}')," +
                "(8,9,5,500,'${d(-15)}','${d(-14)}','${MembershipStatus.CLOSED}')," +
                "(9,10,9,2500,'${d(-28)}','${d(12)}','${MembershipStatus.ACTIVE}')," +
                "(10,11,8,6500,'${d(-50)}','${d(10)}','${MembershipStatus.ACTIVE}')," +
                "(11,2,5,500,'${d(-90)}','${d(-89)}','${MembershipStatus.CLOSED}')," +
                "(12,7,5,500,'${d(-150)}','${d(-149)}','${MembershipStatus.CLOSED}')")

        // Платежи (12)
        sql("INSERT INTO Payments (ID, ID_Membership, PaymentDate, Amount, PaymentMethod, Status) VALUES " +
                "(1,1,'${d(-5)} 12:30',3500,'${PaymentMethod.ONLINE}','${PaymentStatus.PAID}')," +
                "(2,2,'${d(-10)} 18:05',2800,'${PaymentMethod.CARD}','${PaymentStatus.PAID}')," +
                "(3,3,'${d(-40)} 11:00',3500,'${PaymentMethod.CASH}','${PaymentStatus.PAID}')," +
                "(4,4,'${d(-8)} 14:20',3800,'${PaymentMethod.CARD}','${PaymentStatus.PAID}')," +
                "(5,5,'${d(-100)} 10:15',25000,'${PaymentMethod.ONLINE}','${PaymentStatus.PAID}')," +
                "(6,6,'${d(-60)} 16:40',9000,'${PaymentMethod.CARD}','${PaymentStatus.PAID}')," +
                "(7,7,'${d(-25)} 09:50',2000,'${PaymentMethod.CASH}','${PaymentStatus.PAID}')," +
                "(8,8,'${d(-15)} 13:10',500,'${PaymentMethod.CASH}','${PaymentStatus.PAID}')," +
                "(9,9,'${d(-28)} 19:30',2500,'${PaymentMethod.ONLINE}','${PaymentStatus.PAID}')," +
                "(10,10,'${d(-50)} 17:25',6500,'${PaymentMethod.CARD}','${PaymentStatus.PAID}')," +
                "(11,11,'${d(-90)} 12:00',500,'${PaymentMethod.CASH}','${PaymentStatus.PAID}')," +
                "(12,12,'${d(-150)} 15:45',500,'${PaymentMethod.CASH}','${PaymentStatus.PAID}')")

        // Посещения (14): прошедшие занятия, будущие записи и свободные посещения
        val nowStr = LocalDateTime.now().format(dateTime)
        fun lastPast(name: String): Long? = db.rawQuery(
            "SELECT ID FROM WorkoutSessions WHERE SessionName = ? AND StartDateTime < ? ORDER BY StartDateTime DESC LIMIT 1",
            arrayOf(name, nowStr)
        ).use { if (it.moveToFirst()) it.getLong(0) else null }

        fun nextFuture(name: String, skip: Int): Long? = db.rawQuery(
            "SELECT ID FROM WorkoutSessions WHERE SessionName = ? AND StartDateTime > ? ORDER BY StartDateTime ASC LIMIT ?, 1",
            arrayOf(name, nowStr, skip.toString())
        ).use { if (it.moveToFirst()) it.getLong(0) else null }

        val attRows = mutableListOf<String>()
        var attId = 1L
        fun visit(clientId: Long, sessionId: Long?, when_: String, status: String) {
            attRows.add("($attId,$clientId,${sessionId ?: "NULL"},'$when_',1,'$status')")
            attId++
        }
        lastPast("Утренняя йога")?.let {
            visit(1, it, dt(-4, 10, 0), AttendanceStatus.VISITED)
            visit(2, it, dt(-4, 10, 0), AttendanceStatus.VISITED)
            visit(5, it, dt(-4, 10, 0), AttendanceStatus.VISITED)
        }
        lastPast("Кроссфит")?.let {
            visit(1, it, dt(-2, 18, 0), AttendanceStatus.VISITED)
            visit(7, it, dt(-2, 18, 0), AttendanceStatus.VISITED)
        }
        lastPast("Бокс: группа новичков")?.let {
            visit(4, it, dt(-3, 18, 0), AttendanceStatus.CANCELLED)
            visit(9, it, dt(-3, 18, 0), AttendanceStatus.VISITED)
        }
        lastPast("Пилатес")?.let {
            visit(10, it, dt(-3, 12, 0), AttendanceStatus.VISITED)
            visit(8, it, dt(-3, 12, 0), AttendanceStatus.VISITED)
        }
        lastPast("Плавание: техника")?.let {
            visit(11, it, dt(-5, 9, 0), AttendanceStatus.VISITED)
        }
        // Свободные посещения по карте через турникет (без занятия)
        visit(5, null, dt(-1, 9, 30), AttendanceStatus.VISITED)
        visit(1, null, dt(-2, 8, 15), AttendanceStatus.VISITED)
        // Будущие записи
        nextFuture("Утренняя йога", 0)?.let {
            visit(1, it, dt(2, 10, 0), AttendanceStatus.BOOKED)
            visit(2, it, dt(2, 10, 0), AttendanceStatus.BOOKED)
        }
        nextFuture("Утренняя йога", 1)?.let {
            visit(1, it, dt(4, 10, 0), AttendanceStatus.BOOKED)
        }
        nextFuture("Кроссфит", 0)?.let {
            visit(2, it, dt(1, 18, 0), AttendanceStatus.BOOKED)
        }
        nextFuture("Растяжка", 0)?.let {
            visit(10, it, dt(3, 19, 0), AttendanceStatus.BOOKED)
        }
        sql("INSERT INTO Attendance (ID, ID_Client, ID_Session, VisitDateTime, Paid, Status) VALUES " +
                attRows.joinToString(","))

        // Пользователи (24): администратор, бухгалтерия, 10 тренеров и 12 клиентов.
        // Пароль всех учётных записей: 1234 (хранится в виде хеша SHA-256).
        val userRows = mutableListOf(
            "(1,'admin','$pwd','${Roles.ADMIN}',NULL,NULL,'Администратор клуба')",
            "(2,'buh','$pwd','${Roles.ACCOUNTANT}',NULL,NULL,'Бухгалтерия')"
        )
        for (i in 1..10) {
            val t = db.rawQuery("SELECT LastName, FirstName, MiddleName FROM Trainers WHERE ID = ?", arrayOf(i.toString()))
                .use { c ->
                    if (c.moveToFirst()) Triple(c.getString(0), c.getString(1), c.getString(2)) else null
                } ?: continue
            val fio = "${t.first} ${t.second} ${t.third}"
            userRows.add("(${i + 2},'trainer$i','$pwd','${Roles.TRAINER}',NULL,$i,'$fio')")
        }
        for (i in 1..12) {
            val c1 = db.rawQuery("SELECT LastName, FirstName, MiddleName FROM Clients WHERE ID = ?", arrayOf(i.toString()))
                .use { c ->
                    if (c.moveToFirst()) Triple(c.getString(0), c.getString(1), c.getString(2)) else null
                } ?: continue
            val fio = "${c1.first} ${c1.second} ${c1.third}"
            userRows.add("(${i + 12},'client$i','$pwd','${Roles.CLIENT}',$i,NULL,'$fio')")
        }
        sql("INSERT INTO Users (ID, Login, PasswordHash, Role, ID_Client, ID_Trainer, FullName) VALUES " +
                userRows.joinToString(","))
    }
}
