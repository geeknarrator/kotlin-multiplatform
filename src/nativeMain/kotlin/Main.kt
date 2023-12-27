import kotlinx.cinterop.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.posix.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

fun main() = runBlocking {
    val kvStore = KVStore<String, Int>(false)
    val keyList = listOf("kv", "geek", "narrator", "youtube", "subscribe", "like", "comment", "share")
    measureTimeMillis {
         coroutineScope {
            for (i in 1..1000000) {
                val key = keyList.random()
                launch {
                    kvStore.store(key, i)
                }
            }
        }
    }.let { time -> println("Took $time ms, final store state ${kvStore.innerKvStore}") }
}

@OptIn(ExperimentalForeignApi::class)
fun createRandomIntsFile(fileName: String, totalInts: Int) {
    fopen(fileName, "w")?.let { file ->
        repeat(totalInts) {
            val number = Random.nextInt(0, 10000)
            fprintf(file, "$number\n")
        }
        fclose(file)
    } ?: println("Failed to open file for writing.")
}

@OptIn(ExperimentalForeignApi::class)
fun readChunks(fileName: String, chunkSize: Int) {
    fopen(fileName, "r")?.let { file ->
        memScoped {
            val buffer = allocArray<ByteVar>(chunkSize)
            while(true) {
                val bytesRead = fread(buffer, 1.toULong(), chunkSize.toULong(), file)
                if(bytesRead == 0UL) break
               // println("Buffer ${buffer.toKString()}")
            }
        }
        fclose(file)
    }
}


@OptIn(ExperimentalForeignApi::class)
fun readLines(fileName: String) {
    fopen(fileName, "r")?.let {
        file ->
            memScoped {
                val bufferLength = 4096
                val buffer = allocArray<ByteVar>(bufferLength)
                while(true) {
                    fgets(buffer, bufferLength, file)?.toKString() ?: break
                    //println(line.trimEnd())
                }
            }
        fclose(file)
    }
}
