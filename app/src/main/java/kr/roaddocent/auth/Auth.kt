package kr.roaddocent.auth

import android.content.Context
import java.security.MessageDigest

/** Who is signed in. */
data class User(val id: String, val name: String, val provider: String /* local | google */, val email: String = "")

/**
 * Sign-in for the contest build: local test accounts (hashed) plus Google Sign-In through Credential Manager when a
 * Web client ID is configured. Production swaps this for the Pleos account / OEM identity.
 */
class AuthStore(context: Context) {
    private val sp = context.getSharedPreferences("roaddocent_auth", Context.MODE_PRIVATE)

    /** Test accounts for reviewers. Passwords are stored as SHA-256; the README lists the plain ones. */
    private val accounts = mapOf(
        "tester" to Triple(sha("pleos2026!"), "테스터", "tester@roaddocent.kr"),
        "judge" to Triple(sha("hyundai2026"), "심사위원", "judge@roaddocent.kr"),
        "family" to Triple(sha("family2026"), "가족", "family@roaddocent.kr")
    )

    var user: User?
        get() = sp.getString("id", null)?.let { User(it, sp.getString("name", it) ?: it, sp.getString("provider", "local") ?: "local", sp.getString("email", "") ?: "") }
        private set(v) { sp.edit().apply { if (v == null) clear() else putString("id", v.id).putString("name", v.name).putString("provider", v.provider).putString("email", v.email) }.apply() }

    val termsAcceptedAt: Long get() = sp.getLong("terms_at", 0L)
    val termsVersion: String get() = sp.getString("terms_v", "") ?: ""

    fun signInLocal(id: String, password: String): Result<User> {
        val a = accounts[id.trim().lowercase()] ?: return Result.failure(IllegalArgumentException("no such account"))
        if (a.first != sha(password)) return Result.failure(IllegalArgumentException("wrong password"))
        return Result.success(User(id.trim().lowercase(), a.second, "local", a.third).also { user = it })
    }

    fun signInGoogle(email: String, name: String, subject: String): User = User("google:$subject", name.ifBlank { email }, "google", email).also { user = it }

    fun acceptTerms(version: String) = sp.edit().putLong("terms_at", System.currentTimeMillis()).putString("terms_v", version).apply()
    fun signOut() { user = null }

    companion object {
        const val TERMS_VERSION = "2026-10-26"
        fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
