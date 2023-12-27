import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.posix.*

data class KVStoreError(val errorMessage: String?): Exception(errorMessage)

class KVStore<K, V>(walMode: Boolean) {
    private val innerWalMode = walMode
    private val mutex = Mutex()
    val innerKvStore = mutableMapOf<K, V>()

    @OptIn(ExperimentalForeignApi::class)
    fun updateWAL(key: K, value: V) {
        memScoped {
            val filePath = "wal.log"
            val mode = "ab" //append mode
            val file = fopen(filePath, mode) ?: throw Error("File not found : $filePath")

            try {
                val data = "$key=$value\n".cstr

                if(fwrite(data, 1.toULong(), data.size.toULong(), file) != data.size.toULong()) {
                    throw Error("Failed to write WAL entry")
                }
                fflush(file)

                // sync to disk
                fsync(fileno(file))
            } finally {
                fclose(file)
            }
        }
    }

    suspend fun store(key: K, value: V): Result<Boolean> {
        mutex.withLock {
            try {
                if (innerWalMode) {
                    updateWAL(key, value)
                }
                val oldValue = innerKvStore[key]
                innerKvStore[key] = value
                if (oldValue == null) {
                    return Result.success(false)
                }
                return Result.success(true)
            } catch (e: Error) {
                return Result.failure(KVStoreError("Failed to store $key:$value"))
            }
        }
    }

    fun get(key: K): Result<V> {
        return innerKvStore[key] ?.let { value -> Result.success(value) }?: return Result.failure(KVStoreError("Value not found for $key"))
    }
}
