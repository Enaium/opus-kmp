/*
 * Copyright (c) 2026 Enaium
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cn.enaium.opus

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Loads libopus_jni for the current OS/architecture.
 *
 * The main JVM artifact depends on one `opus-kmp-jni-jvm-<os>-<arch>` artifact
 * per supported platform, so a normal classpath always contains the right
 * library. Its bytes are extracted to a versioned temp directory and loaded
 * with `System.load`, because jars cannot be dlopen'ed directly.
 *
 * On Android the AAR ships the same library in `jniLibs`, so the system loader
 * finds it by name and extraction is skipped.
 */
internal object NativeLoader {

    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        loaded = true

        val osName = System.getProperty("os.name").orEmpty().lowercase()
        if (osName.contains("android")) {
            System.loadLibrary("opus_jni")
            return
        }

        val classifier = detectClassifier()
        val libFile = libFileName()
        val resource = "/cn/enaium/opus/native/$classifier/$libFile"
        val bytes = NativeLoader::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
        if (bytes == null) {
            // Android host tests and custom deployments put the library on
            // java.library.path instead of the classpath.
            System.loadLibrary("opus_jni")
            return
        }
        System.load(extract(bytes, libFile).absolutePath)
    }

    private fun extract(bytes: ByteArray, libFile: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = digest.joinToString("") { "%02x".format(it) }.take(16)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "opus-kmp-$hex")
        tempDir.mkdirs()
        val target = File(tempDir, libFile)
        if (!target.isFile || target.length().toInt() != bytes.size) {
            val tmp = File.createTempFile(libFile, ".part", tempDir)
            tmp.writeBytes(bytes)
            // Avoid java.nio.file on Android (API < 26) where the class is
            // absent; the temporary file is in the same directory, so the
            // rename is atomic on the same filesystem.
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) {
                    FileOutputStream(target).use { output -> output.write(bytes) }
                    tmp.delete()
                }
            }
        }
        return target
    }

    private fun detectClassifier(): String {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()
        val os = when {
            osName.contains("linux") -> "linux"
            osName.contains("mac") || osName.contains("darwin") || osName.contains("osx") -> "darwin"
            osName.contains("win") -> "windows"
            else -> error("Unsupported OS for opus-kmp JVM artifact: $osName")
        }
        val arch = when (osArch) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> error("Unsupported CPU architecture for opus-kmp JVM artifact: $osArch")
        }
        return "$os-$arch"
    }

    private fun libFileName(): String {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            // MinGW names DLLs without the Unix "lib" prefix.
            osName.contains("win") -> "opus_jni.dll"
            osName.contains("mac") || osName.contains("darwin") || osName.contains("osx") ->
                "libopus_jni.dylib"

            else -> "libopus_jni.so"
        }
    }
}
