import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Clock
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fprintf
import kotlin.time.Duration

fun main(): Unit = runBlocking {
    val inputChannel = Channel<String>()
    val parsingChannel = Channel<LogLine?>()
    val fileName = "logfile.txt"
    startLogFileAppend(fileName, Duration.parse("1s"))
    //delay(5000)
    readLogLines(fileName, inputChannel)
    startParsingLogLines(inputChannel, parsingChannel)
    val countingMetricsJob = startCountingMetrics(parsingChannel)

    // Wait for the aggregation to complete
    countingMetricsJob.join()

    // Closing channels
    inputChannel.close()
    parsingChannel.close()
}

fun generateRandomLogLine(): String {
    val timestamp = Clock.System.now().toEpochMilliseconds()
    val level = LogLevel.values().random()
    val endpoint = Endpoint.values().random()
    val message = when (level) {
        LogLevel.INFO -> "Operation completed successfully."
        LogLevel.ERROR -> "Operation failed due to an error."
        LogLevel.WARN -> "Operation completed with a warning."
        LogLevel.DEBUG -> "Debug information."
        LogLevel.TRACE -> "Trace information"
    }
    return LogLine(level, endpoint, timestamp, message).let { logLine -> "${logLine.level} ${logLine.endpoint} ${logLine.timestamp} ${logLine.message}" }
}

fun CoroutineScope.startParsingLogLines(inputLineChannel: Channel<String>, output: Channel<LogLine?>) = launch {
    for (line in inputLineChannel) {
        output.send(stringToLogLine(line))
    }
}

fun stringToLogLine(logLineStr: String): LogLine? {
    val parts = logLineStr.split(" ", limit = 4)
    if (parts.size < 4) return null

    val timestamp = parts[2].toLong()
    val level = parts[0]
    val endpoint = parts[1]
    val message = parts[3]

    return LogLine(LogLevel.valueOf(level), Endpoint.valueOf(endpoint), timestamp, message)
}

fun CoroutineScope.startCountingMetrics(input: Channel<LogLine?>) = launch {
    val levelCount = mutableMapOf<LogLevel, Int>()
    val messageCount = mutableMapOf<String, Int>()
    for (line in input) {
        line?:continue
        levelCount[line.level] = levelCount[line.level]?.let { currentCount -> currentCount + 1 }?:0
        messageCount[line.message] = messageCount[line.message]?.let { currentCount -> currentCount + 1 }?:0
        println("Current counters $levelCount $messageCount")
    }
}

/**
 *
 */

@OptIn(ExperimentalForeignApi::class)
fun CoroutineScope.startLogFileAppend(fileName: String, duration: Duration) = launch {
    val startTime = Clock.System.now()
    fopen(fileName, "w")?.let { file ->
        while(startTime.minus(Clock.System.now()).absoluteValue < duration) {
            val logLine = generateRandomLogLine()
            fprintf(file, "$logLine\n")
        }
        fclose(file)
    } ?: println("Failed to open file for writing.")
}


@OptIn(ExperimentalForeignApi::class, DelicateCoroutinesApi::class)
fun CoroutineScope.readLogLines(fileName: String, inputChannel: Channel<String>) = launch {
    fopen(fileName, "r")?.let {
            file ->
        memScoped {
            val bufferLength = 4096
            val buffer = allocArray<ByteVar>(bufferLength)

            // Keep trying to read new lines
            while (!inputChannel.isClosedForSend) {
                if (fgets(buffer, bufferLength, file) != null) {
                    val line = buffer.toKString()
                    inputChannel.send(line)
                } else {
                    // No new line available, wait before trying again
                    delay(1000)
                }
            }
        }
        fclose(file)
    }
}

data class LogLine(val level: LogLevel, val endpoint: Endpoint, val timestamp: Long, val message: String)
enum class LogLevel {
    INFO,
    DEBUG,
    ERROR,
    WARN,
    TRACE
}

enum class Endpoint(val path: String) {
    UserLogin("/api/user/login"),
    UserDetails("/api/user/details"),
    PaymentProcess("/api/payment/process"),
    UserLogout("/api/user/logout"),
    AddToCart("/api/cart/add"),
    ItemDetails("/api/item/details"),
    PaymentCheckout("/api/payment/checkout"),
    UserUpdate("/api/user/update"),
    ContentLoad("/api/content/load"),
    SearchQuery("/api/search/query")
}