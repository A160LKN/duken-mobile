package kz.duken.mobile.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

data class Session(val token: String, val email: String)

/** Токен и email вошедшего покупателя. Переживают перезапуск приложения. */
class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("token")
    private val emailKey = stringPreferencesKey("email")

    val session: Flow<Session?> = context.dataStore.data.map { prefs ->
        val token = prefs[tokenKey] ?: return@map null
        Session(token, prefs[emailKey].orEmpty())
    }

    suspend fun current(): Session? = session.first()

    suspend fun save(session: Session) {
        context.dataStore.edit {
            it[tokenKey] = session.token
            it[emailKey] = session.email
        }
    }

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(tokenKey)
            it.remove(emailKey)
        }
    }
}
