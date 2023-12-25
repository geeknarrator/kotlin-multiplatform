import kotlinx.cinterop.*
import platform.posix.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

fun main() {

    val fileName = "big.txt"
    measureTimeMillis {
        fileName.let { fileName ->
            readChunks(fileName, 1000)
        }
    }.let { nano -> println("Total time with chunk size 1000 $nano ms") }

    measureTimeMillis {
        fileName.let { fileName ->
            readChunks(fileName, 100)
        }
    }.let { nano -> println("Total time with chunk size 100 $nano ms") }

    measureTimeMillis {
        fileName.let { fileName ->
            readChunks(fileName, 100000)
        }
    }.let { nano -> println("Total time with chunk size 10000 $nano ms") }
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
                println("Buffer ${buffer.toKString()}")
            }
        }
        fclose(file)
    }
}


