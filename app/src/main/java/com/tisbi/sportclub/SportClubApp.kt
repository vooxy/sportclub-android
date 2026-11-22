package com.tisbi.sportclub

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.tisbi.sportclub.db.User
import com.tisbi.sportclub.repo.Database

class SportClubApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Database.init(this)
    }

    companion object {
        /** Текущая сессия пользователя (persisted) */
        fun session(context: Context): SessionManager = SessionManager(context)
    }
}

/** Хранит текущего пользователя между запусками приложения */
class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun save(user: User) {
        prefs.edit()
            .putLong("id", user.id)
            .putString("login", user.login)
            .putString("role", user.role)
            .putString("name", user.fullName)
            .putLong("idClient", user.idClient ?: -1)
            .putLong("idTrainer", user.idTrainer ?: -1)
            .apply()
    }

    fun restore(): User? {
        val id = prefs.getLong("id", -1)
        if (id == -1L) return null
        return User(
            id = id,
            login = prefs.getString("login", "") ?: "",
            passwordHash = "",
            role = prefs.getString("role", "") ?: "",
            idClient = prefs.getLong("idClient", -1).takeIf { it > 0 },
            idTrainer = prefs.getLong("idTrainer", -1).takeIf { it > 0 },
            fullName = prefs.getString("name", "") ?: ""
        )
    }

    fun clear() = prefs.edit().clear().apply()
}
