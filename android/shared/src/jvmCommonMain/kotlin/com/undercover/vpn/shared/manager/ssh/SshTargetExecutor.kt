package com.undercover.vpn.shared.manager.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.Base64

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
    val combined: String
        get() = when {
            stderr.isBlank() -> stdout
            stdout.isBlank() -> stderr
            else -> "$stdout\n$stderr".trim()
        }
}

class SshTargetExecutor(
    val host: String,
    val port: Int = 22,
    val user: String = "root",
    val password: String? = null,
    val keyPath: String? = null,
    val keyContent: String? = null,
    val passphrase: String? = null
) : Closeable {

    private val jsch = JSch()
    private var session: Session? = null

    val isConnected: Boolean
        get() = session?.isConnected == true

    /**
     * Connects to remote SSH host.
     */
    suspend fun connect(timeoutMs: Int = 15000): Unit = withContext(Dispatchers.IO) {
        if (isConnected) return@withContext

        // Configure authentication
        if (!keyContent.isNullOrBlank()) {
            val prvBytes = keyContent.toByteArray(Charsets.UTF_8)
            val passBytes = passphrase?.toByteArray(Charsets.UTF_8)
            jsch.addIdentity("in_memory_key", prvBytes, null, passBytes)
        } else if (!keyPath.isNullOrBlank()) {
            if (!passphrase.isNullOrBlank()) {
                jsch.addIdentity(keyPath, passphrase.toByteArray(Charsets.UTF_8))
            } else {
                jsch.addIdentity(keyPath)
            }
        }

        val sess = jsch.getSession(user, host, if (port > 0) port else 22)
        if (!password.isNullOrBlank()) {
            sess.setPassword(password)
        }

        // Host key checking: Trust on first use / accept
        sess.setConfig("StrictHostKeyChecking", "no")
        sess.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
        sess.timeout = timeoutMs

        sess.connect(timeoutMs)
        session = sess
    }

    /**
     * Standard OpenSSH SHA256 base64 fingerprint without padding.
     * Matches Go's ssh.FingerprintSHA256 (e.g. SHA256:abc...)
     */
    fun hostKeyFingerprint(): String {
        val sess = session ?: throw IOException("Not connected to SSH host")
        val hostKeyB64 = sess.hostKey?.key ?: return ""
        val rawBytes = Base64.getDecoder().decode(hostKeyB64)
        val digest = MessageDigest.getInstance("SHA-256").digest(rawBytes)
        val b64 = Base64.getEncoder().encodeToString(digest).trimEnd('=')
        return "SHA256:$b64"
    }

    /**
     * Executes a command on the remote host and returns exitCode, stdout and stderr.
     */
    suspend fun execute(command: String, timeoutMs: Int = 30000): CommandResult = withContext(Dispatchers.IO) {
        val sess = session ?: throw IOException("Not connected to SSH host")
        val channel = sess.openChannel("exec") as ChannelExec
        channel.setCommand(command)

        val stdoutStream = ByteArrayOutputStream()
        val stderrStream = ByteArrayOutputStream()
        channel.setOutputStream(stdoutStream)
        channel.setErrStream(stderrStream)

        channel.connect(timeoutMs)

        val deadline = System.currentTimeMillis() + timeoutMs
        while (!channel.isClosed) {
            if (System.currentTimeMillis() > deadline) {
                channel.disconnect()
                throw IOException("Command timed out after ${timeoutMs}ms: $command")
            }
            Thread.sleep(25)
        }

        val exitCode = channel.exitStatus
        channel.disconnect()

        CommandResult(
            exitCode = exitCode,
            stdout = stdoutStream.toString(Charsets.UTF_8.name()),
            stderr = stderrStream.toString(Charsets.UTF_8.name())
        )
    }

    /**
     * Executes a command and returns combined stdout/stderr. Throws on error if required.
     */
    suspend fun executeCombined(command: String, timeoutMs: Int = 30000): String {
        val res = execute(command, timeoutMs)
        return res.combined
    }

    /**
     * Reads remote file content via base64 encoding over exec channel.
     */
    suspend fun readFile(remotePath: String): ByteArray = withContext(Dispatchers.IO) {
        val res = execute("base64 '$remotePath'")
        if (!res.isSuccess) {
            throw IOException("Failed to read remote file $remotePath: ${res.combined}")
        }
        val cleaned = res.stdout.replace("\n", "").replace("\r", "").trim()
        try {
            Base64.getDecoder().decode(cleaned)
        } catch (e: Exception) {
            throw IOException("Failed to decode base64 for $remotePath: ${e.message}", e)
        }
    }

    /**
     * Writes remote file content with specific octal permissions (e.g. 0600) via base64 decoding.
     */
    suspend fun writeFile(remotePath: String, data: ByteArray, perm: Int = 384 /* 0600 octal */): Unit = withContext(Dispatchers.IO) {
        val dir = if (remotePath.contains("/")) remotePath.substringBeforeLast("/") else "/"
        val mkDirRes = execute("mkdir -p '$dir' && chmod 0700 '$dir'")
        if (!mkDirRes.isSuccess) {
            throw IOException("Failed to create directory $dir: ${mkDirRes.combined}")
        }

        val b64 = Base64.getEncoder().encodeToString(data)
        val octalPerm = String.format("%04o", perm)
        val cmd = "install -m $octalPerm /dev/null '$remotePath' && echo '$b64' | base64 -d > '$remotePath'"
        val writeRes = execute(cmd)
        if (!writeRes.isSuccess) {
            throw IOException("Failed to write remote file $remotePath: ${writeRes.combined}")
        }
    }

    /**
     * Streams output line by line from a long-running remote command.
     */
    fun executeStream(command: String): Flow<String> = flow {
        val sess = session ?: throw IOException("Not connected to SSH host")
        val channel = sess.openChannel("exec") as ChannelExec
        channel.setCommand(command)

        val inputStream = channel.inputStream
        channel.connect()

        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        reader.useLines { lines ->
            lines.forEach { line ->
                emit(line)
            }
        }

        while (!channel.isClosed) {
            Thread.sleep(50)
        }
        channel.disconnect()
    }.flowOn(Dispatchers.IO)

    override fun close() {
        try {
            session?.disconnect()
        } catch (_: Exception) {
        } finally {
            session = null
        }
    }
}
