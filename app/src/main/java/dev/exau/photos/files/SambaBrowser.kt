package dev.exau.photos.files

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.InputStream
import java.util.EnumSet
import java.util.Properties
import java.util.concurrent.TimeUnit

class SambaBrowser {
    private val lock = Any()

    fun list(share: SambaShare, relative: String): List<FileEntry> = synchronized(lock) {
        val errors = ArrayList<String>()
        runCatching { listSmbj(share, relative) }
            .onSuccess { return it }
            .onFailure {
                Log.w(TAG, "smbj failed for ${share.host}/${share.share}", it)
                errors += it.message ?: it.javaClass.simpleName
            }
        runCatching { listJcifs(share, relative) }
            .onSuccess { return it }
            .onFailure {
                Log.w(TAG, "jcifs failed for ${share.host}/${share.share}", it)
                errors += it.message ?: it.javaClass.simpleName
            }
        throw IllegalStateException(errors.lastOrNull() ?: "Could not connect")
    }

    fun openStream(share: SambaShare, relative: String): InputStream = synchronized(lock) {
        runCatching { openSmbj(share, relative) }
            .getOrElse { openJcifs(share, relative) }
    }

    fun readLimited(share: SambaShare, relative: String, maxBytes: Int = 25_000_000): ByteArray {
        openStream(share, relative).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(32_768)
            var total = 0
            while (total < maxBytes) {
                val n = input.read(buf, 0, minOf(buf.size, maxBytes - total))
                if (n <= 0) break
                out.write(buf, 0, n)
                total += n
            }
            return out.toByteArray()
        }
    }

    fun close() = Unit

    fun mutate(share: SambaShare, action: (SambaMutator) -> Unit) = synchronized(lock) {
        runCatching { mutateSmbj(share, action) }
            .getOrElse { first ->
                runCatching { mutateJcifs(share, action) }
                    .getOrElse { second ->
                        throw second
                    }
            }
    }

    private fun mutateSmbj(share: SambaShare, action: (SambaMutator) -> Unit) {
        val configs = listOf(smbjConfig(encrypt = false), smbjConfig(encrypt = true))
        var last: Throwable? = null
        for (config in configs) {
            val client = SMBClient(config)
            try {
                client.connect(share.host, share.port).use { conn ->
                    for (auth in authContexts(share)) {
                        try {
                            conn.authenticate(auth).use { session ->
                                val disk = session.connectShare(share.share) as? DiskShare
                                    ?: error("\"${share.share}\" is not a disk share")
                                disk.use {
                                    action(SmbjMutator(it))
                                    return
                                }
                            }
                        } catch (error: Exception) {
                            last = error
                            Log.w(TAG, "smbj write ${auth.username}@${share.host} failed", error)
                        }
                    }
                }
            } catch (error: Exception) {
                last = error
            } finally {
                runCatching { client.close() }
            }
        }
        throw last ?: IllegalStateException("Could not write with SMB2/3")
    }

    private fun mutateJcifs(share: SambaShare, action: (SambaMutator) -> Unit) {
        action(JcifsMutator(share, jcifsContext(share)))
    }

    private inner class SmbjMutator(private val disk: DiskShare) : SambaMutator {
        private val shareAll = EnumSet.of(
            SMB2ShareAccess.FILE_SHARE_READ,
            SMB2ShareAccess.FILE_SHARE_WRITE,
            SMB2ShareAccess.FILE_SHARE_DELETE,
        )

        override fun list(relative: String) = readSmbjList(disk, relative)

        override fun mkdir(relative: String) {
            disk.mkdir(smbPath(relative))
        }

        override fun delete(relative: String, directory: Boolean) {
            val path = smbPath(relative)
            if (directory) disk.rmdir(path, true) else disk.rm(path)
        }

        override fun rename(from: String, to: String) {
            disk.open(
                smbPath(from),
                EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ),
                null,
                shareAll,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { it.rename(smbPath(to)) }
        }

        override fun copyFile(from: String, to: String) {
            disk.openFile(
                smbPath(from),
                EnumSet.of(AccessMask.GENERIC_READ, AccessMask.FILE_READ_DATA),
                null,
                shareAll,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { src ->
                disk.openFile(
                    smbPath(to),
                    EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.FILE_WRITE_DATA),
                    null,
                    shareAll,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null,
                ).use { dest ->
                    src.inputStream.use { input ->
                        dest.outputStream.use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    private inner class JcifsMutator(
        private val share: SambaShare,
        private val ctx: CIFSContext,
    ) : SambaMutator {
        override fun list(relative: String): List<FileEntry> {
            val folder = SmbFile(jcifsUrl(share, relative, directory = true), ctx)
            val children = folder.listFiles() ?: emptyArray()
            return children.mapNotNull { child ->
                val name = child.name.trimEnd('/')
                if (name.isBlank() || name == "." || name == "..") return@mapNotNull null
                val dir = child.isDirectory
                FileEntry(
                    name = name,
                    relative = FileKinds.join(relative, name),
                    isDir = dir,
                    isImage = !dir && FileKinds.isImage(name),
                    isVideo = !dir && FileKinds.isVideo(name),
                    size = if (dir) 0L else runCatching { child.length() }.getOrDefault(0L),
                    lastModified = runCatching { child.lastModified() }.getOrDefault(0L),
                )
            }
        }

        override fun mkdir(relative: String) {
            SmbFile(jcifsUrl(share, relative, directory = true), ctx).mkdir()
        }

        override fun delete(relative: String, directory: Boolean) {
            val file = SmbFile(jcifsUrl(share, relative, directory = directory), ctx)
            if (directory && file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    val name = child.name.trimEnd('/')
                    if (name.isBlank() || name == "." || name == "..") return@forEach
                    delete(FileKinds.join(relative, name), child.isDirectory)
                }
            }
            file.delete()
        }

        override fun rename(from: String, to: String) {
            val src = SmbFile(jcifsUrl(share, from, directory = false), ctx)
            val dest = SmbFile(jcifsUrl(share, to, directory = false), ctx)
            src.renameTo(dest)
        }

        override fun copyFile(from: String, to: String) {
            val src = SmbFile(jcifsUrl(share, from, directory = false), ctx)
            val dest = SmbFile(jcifsUrl(share, to, directory = false), ctx)
            src.copyTo(dest)
        }
    }

    private fun listSmbj(share: SambaShare, relative: String): List<FileEntry> {
        val configs = listOf(smbjConfig(encrypt = false), smbjConfig(encrypt = true))
        var last: Throwable? = null
        for (config in configs) {
            val client = SMBClient(config)
            try {
                client.connect(share.host, share.port).use { conn ->
                    for (auth in authContexts(share)) {
                        try {
                            conn.authenticate(auth).use { session ->
                                val disk = session.connectShare(share.share) as? DiskShare
                                    ?: error("\"${share.share}\" is not a disk share")
                                disk.use {
                                    return readSmbjList(it, relative)
                                }
                            }
                        } catch (error: Exception) {
                            last = error
                            Log.w(TAG, "smbj auth ${auth.username}@${share.host} failed", error)
                        }
                    }
                }
            } catch (error: Exception) {
                last = error
                Log.w(TAG, "smbj connect ${share.host}:${share.port} failed", error)
            } finally {
                runCatching { client.close() }
            }
        }
        throw last ?: IllegalStateException("Could not connect with SMB2/3")
    }

    private fun readSmbjList(disk: DiskShare, relative: String): List<FileEntry> {
        val paths = listOf(smbPath(relative), "").distinct()
        var last: Throwable? = null
        for (path in paths) {
            try {
                return disk.list(path).mapNotNull { info ->
                    val name = info.fileName ?: return@mapNotNull null
                    if (name == "." || name == "..") return@mapNotNull null
                    val dir = isDirectory(info)
                    FileEntry(
                        name = name,
                        relative = FileKinds.join(relative, name),
                        isDir = dir,
                        isImage = !dir && FileKinds.isImage(name),
                        isVideo = !dir && FileKinds.isVideo(name),
                        size = info.endOfFile,
                        lastModified = info.lastWriteTime?.toEpochMillis() ?: 0L,
                    )
                }.sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
            } catch (error: Exception) {
                last = error
            }
        }
        throw last ?: IllegalStateException("Could not list that folder")
    }

    private fun openSmbj(share: SambaShare, relative: String): InputStream {
        val client = SMBClient(smbjConfig(encrypt = false))
        val conn = client.connect(share.host, share.port)
        val session = conn.authenticate(authContexts(share).first())
        val disk = session.connectShare(share.share) as DiskShare
        return disk.openFile(
            smbPath(relative),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            null,
        ).inputStream
    }

    private fun listJcifs(share: SambaShare, relative: String): List<FileEntry> {
        val ctx = jcifsContext(share)
        val url = jcifsUrl(share, relative, directory = true)
        val folder = SmbFile(url, ctx)
        val children = folder.listFiles() ?: emptyArray()
        return children.mapNotNull { child ->
            val name = child.name.trimEnd('/')
            if (name.isBlank() || name == "." || name == "..") return@mapNotNull null
            val dir = child.isDirectory
            FileEntry(
                name = name,
                relative = FileKinds.join(relative, name),
                isDir = dir,
                isImage = !dir && FileKinds.isImage(name),
                isVideo = !dir && FileKinds.isVideo(name),
                size = if (dir) 0L else runCatching { child.length() }.getOrDefault(0L),
                lastModified = runCatching { child.lastModified() }.getOrDefault(0L),
            )
        }.sortedWith(compareByDescending<FileEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    private fun openJcifs(share: SambaShare, relative: String): InputStream {
        val ctx = jcifsContext(share)
        return SmbFile(jcifsUrl(share, relative, directory = false), ctx).inputStream
    }

    private fun jcifsContext(share: SambaShare): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB1")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "20000")
            setProperty("jcifs.smb.client.soTimeout", "25000")
            setProperty("jcifs.smb.client.connTimeout", "12000")
            setProperty("jcifs.smb.client.dfs.disabled", "false")
        }
        val base = BaseContext(PropertyConfiguration(props))
        val user = share.username
        val auth = if (user.isBlank()) {
            NtlmPasswordAuthenticator("", "", "")
        } else {
            NtlmPasswordAuthenticator(share.domain, user, share.password)
        }
        return base.withCredentials(auth)
    }

    private fun jcifsUrl(share: SambaShare, relative: String, directory: Boolean): String {
        val port = if (share.port == 445) "" else ":${share.port}"
        val path = relative.trim('/').replace('\\', '/')
        val suffix = buildString {
            if (path.isNotBlank()) append(path)
            if (directory) append('/')
        }
        return "smb://${share.host}$port/${share.share}/$suffix"
    }

    private fun authContexts(share: SambaShare): List<AuthenticationContext> {
        val user = share.username
        val pass = share.password.toCharArray()
        val domain = share.domain
        val result = ArrayList<AuthenticationContext>()
        if (user.isBlank()) {
            result += AuthenticationContext.guest()
            result += AuthenticationContext.anonymous()
            result += AuthenticationContext("", CharArray(0), "")
        } else {
            result += AuthenticationContext(user, pass, domain)
            if (domain.isBlank()) {
                result += AuthenticationContext(user, pass, "WORKGROUP")
                result += AuthenticationContext(user, pass, share.host.substringBefore('.'))
            }
        }
        return result.distinctBy { "${it.username}|${it.domain}" }
    }

    private fun smbjConfig(encrypt: Boolean): SmbConfig =
        SmbConfig.builder()
            .withTimeout(20, TimeUnit.SECONDS)
            .withSoTimeout(25, TimeUnit.SECONDS)
            .withEncryptData(encrypt)
            .withSigningRequired(false)
            .withDfsEnabled(true)
            .withDialects(
                SMB2Dialect.SMB_2_1,
                SMB2Dialect.SMB_3_0,
                SMB2Dialect.SMB_3_0_2,
                SMB2Dialect.SMB_3_1_1,
            )
            .build()

    private fun smbPath(relative: String): String =
        relative.trim('/').replace('/', '\\')

    private fun isDirectory(info: com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation): Boolean {
        val value = try {
            info.javaClass.getMethod("getFileAttributes").invoke(info)
        } catch (_: Exception) {
            return false
        }
        return when (value) {
            is Set<*> -> value.contains(FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
            is Number -> value.toLong() and 0x10L != 0L
            else -> value.toString().contains("DIRECTORY")
        }
    }

    companion object {
        private const val TAG = "PhotosSamba"
    }
}

interface SambaMutator {
    fun list(relative: String): List<FileEntry>
    fun mkdir(relative: String)
    fun delete(relative: String, directory: Boolean)
    fun rename(from: String, to: String)
    fun copyFile(from: String, to: String)
}
