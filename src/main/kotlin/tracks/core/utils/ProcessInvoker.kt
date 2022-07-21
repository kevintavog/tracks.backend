package tracks.core.utils

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors

data class ProcessResult(val output: String, val error: String, val exitCode: Int)

class ProcessInvoker private constructor(val outputAvailable: ((data: String) -> Unit)?) {
    companion object {
        fun run(path: String, arguments: List<String>): ProcessResult {
            val pi = ProcessInvoker(null)
            pi.start(path, arguments)
            return pi.waitForExit()
        }

        fun start(path: String, arguments: List<String>, available: (data: String) -> Unit): ProcessInvoker {
            val pi = ProcessInvoker(available)
            pi.start(path, arguments)
            return pi
        }
    }

    private var process: Process? = null
    private var stdoutReader: StreamReader? = null
    private var stderrReader: StreamReader? = null

    private fun start(path: String, arguments: List<String>) {
        process = ProcessBuilder(listOf(path) + arguments).start()
        stdoutReader = StreamReader(process!!.inputStream, outputAvailable)
        stderrReader = StreamReader(process!!.errorStream, null)
        Executors.newSingleThreadExecutor().submit(stdoutReader!!)
        Executors.newSingleThreadExecutor().submit(stderrReader!!)
    }

    fun writeToStdin(input: String) {
        process?.outputStream?.write(input.toByteArray(Charsets.UTF_8))
        process?.outputStream?.flush()
    }

    fun waitForExit(): ProcessResult {
        process?.waitFor()

        return ProcessResult(
            stdoutReader?.buffer.toString(),
            stderrReader?.buffer.toString(),
            process?.exitValue() ?: -1
        )
    }
}

class StreamReader(val stream: InputStream, val outputAvailable: ((data: String) -> Unit)?): Runnable {
    val buffer = StringBuilder()

    override fun run() {
        BufferedReader(InputStreamReader(stream)).lines().forEach {
            outputAvailable?.let { available ->
                available(it)
            } ?: run {
                buffer.append(it)
            }
        }
    }
}
