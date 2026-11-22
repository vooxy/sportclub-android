package com.tisbi.sportclub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tisbi.sportclub.db.User
import com.tisbi.sportclub.ui.accountant.AccountantScreen
import com.tisbi.sportclub.ui.admin.AdminScreen
import com.tisbi.sportclub.ui.client.ClientScreen
import com.tisbi.sportclub.ui.login.LoginScreen
import com.tisbi.sportclub.ui.theme.SportClubTheme
import com.tisbi.sportclub.ui.trainer.TrainerScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SportClubTheme {
                var user by remember { mutableStateOf(SessionManager(this).restore()) }
                if (user == null) {
                    LoginScreen(onLogin = { user = it })
                } else {
                    val current = user!!
                    RoleHost(user = current, onLogout = { user = null })
                }
            }
        }
    }
}

@Composable
private fun RoleHost(user: User, onLogout: () -> Unit) {
    when (user.role) {
        com.tisbi.sportclub.db.Roles.ADMIN -> AdminScreen(user, onLogout)
        com.tisbi.sportclub.db.Roles.TRAINER -> TrainerScreen(user, onLogout)
        com.tisbi.sportclub.db.Roles.ACCOUNTANT -> AccountantScreen(user, onLogout)
        else -> ClientScreen(user, onLogout)
    }
}
