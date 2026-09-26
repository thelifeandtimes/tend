package io.omabit.tend

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/** Cookie-bearing account vault is encrypted with a non-exportable Android Keystore key. */
class AndroidCredentials(context: Context) : CredentialStore {
    private val prefs = context.getSharedPreferences("tend-vault", Context.MODE_PRIVATE)
    private val alias = "io.omabit.tend.sessions.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    override suspend fun read(): AccountVault =
        withContext(Dispatchers.IO) {
            val text = prefs.getString("encrypted", null) ?: return@withContext AccountVault()
            val bytes = Base64.decode(text, Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            Wire.decodeFromString<AccountVault>(
                cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString()
            )
        }

    override suspend fun write(vault: AccountVault): Unit =
        withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val bytes = cipher.iv + cipher.doFinal(Wire.encodeToString(vault).encodeToByteArray())
            check(
                prefs
                    .edit()
                    .putString("encrypted", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    .commit()
            ) {
                "Could not securely save ship sessions"
            }
        }
}

@Entity(tableName = "cache", primaryKeys = ["account", "key"])
data class CacheRow(val account: String, val key: String, val payload: String)

@Dao
abstract class CacheDao {
    @Query("SELECT * FROM cache WHERE account = :account")
    abstract suspend fun read(account: String): List<CacheRow>

    @Upsert abstract suspend fun upsert(rows: List<CacheRow>)

    @Query("DELETE FROM cache WHERE account = :account AND `key` = :key")
    abstract suspend fun removeRow(account: String, key: String)

    @Query("DELETE FROM cache WHERE account = :account")
    abstract suspend fun remove(account: String)

    @Transaction
    open suspend fun save(account: String, rows: List<CacheRow>, removed: List<String>) {
        upsert(rows)
        removed.forEach { removeRow(account, it) }
    }
}

@Database(entities = [CacheRow::class], version = 1, exportSchema = false)
abstract class TendDatabase : RoomDatabase() {
    abstract fun cache(): CacheDao
}

/** One row per canonical list, plus account metadata. Unchanged lists are not rewritten. */
class AndroidCache(context: Context) : CacheStore {
    private val lock = Mutex()
    private val dao =
        Room.databaseBuilder(context, TendDatabase::class.java, "tend-cache.db").build().cache()
    private val last = mutableMapOf<String, TendState>()

    override suspend fun read(account: String): TendState? =
        lock.withLock {
            val rows = dao.read(account)
            val meta = rows.firstOrNull { it.key == "meta" } ?: return@withLock null
            Wire.decodeFromString<TendState>(meta.payload)
                .copy(
                    lists =
                        rows
                            .filter { it.key.startsWith("list:") }
                            .map { Wire.decodeFromString<TaskList>(it.payload) }
                )
                .also { last[account] = it }
        }

    override suspend fun write(account: String, state: TendState): Unit =
        lock.withLock {
            val previous = last[account]
            val prior = previous?.lists?.associateBy { it.id }.orEmpty()
            val rows =
                mutableListOf(
                    CacheRow(account, "meta", Wire.encodeToString(state.copy(lists = emptyList())))
                )
            state.lists
                .filter { prior[it.id] != it }
                .forEach { rows += CacheRow(account, "list:${it.id}", Wire.encodeToString(it)) }
            val ids = state.lists.mapTo(mutableSetOf()) { it.id }
            val removed = prior.keys.filterNot { it in ids }.map { "list:$it" }
            dao.save(account, rows, removed)
            last[account] = state
        }

    override suspend fun remove(account: String): Unit =
        lock.withLock {
            dao.remove(account)
            last.remove(account)
            Unit
        }
}
