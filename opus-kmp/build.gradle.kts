@file:Suppress("DEPRECATION")
@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import java.io.File
import java.util.Base64
import java.util.Properties
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
}

group = rootProject.group
version = rootProject.version

val jniDir = rootProject.projectDir.resolve("jni")
val opusDir = rootProject.projectDir.resolve("opus")
val hostOs = OperatingSystem.current()

// ==================== Android NDK resolution ====================
val androidApiLevel = 21
val pinnedAndroidNdkVersion = "27.0.12077973"

fun resolveAndroidSdkDir(): File? {
    listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").forEach { key ->
        System.getenv(key)?.takeIf { it.isNotBlank() }?.let {
            val dir = file(it)
            if (dir.isDirectory) return dir
        }
    }
    val localProperties = rootProject.file("local.properties")
    if (localProperties.isFile) {
        val properties = Properties().apply { localProperties.inputStream().use { load(it) } }
        properties.getProperty("sdk.dir")?.takeIf { it.isNotBlank() }?.let {
            val dir = file(it)
            if (dir.isDirectory) return dir
        }
    }
    return null
}

fun resolveAndroidNdkDir(): File? {
    listOf("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT", "NDK_HOME").forEach { key ->
        System.getenv(key)?.takeIf { it.isNotBlank() }?.let {
            val dir = file(it)
            if (dir.isDirectory) return dir
        }
    }
    val sdk = resolveAndroidSdkDir() ?: return null
    val ndkParent = sdk.resolve("ndk")
    if (!ndkParent.isDirectory) return null
    val pinned = ndkParent.resolve(pinnedAndroidNdkVersion)
    if (pinned.isDirectory) return pinned
    return ndkParent.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
}

val resolvedAndroidNdk = resolveAndroidNdkDir()
val androidNdkToolchain = resolvedAndroidNdk?.resolve("build/cmake/android.toolchain.cmake")

fun hasExecutable(name: String): Boolean =
    System.getenv("PATH")?.split(File.pathSeparator)
        ?.map { File(it, name) }
        ?.any { it.isFile && it.canExecute() } == true

fun canBuildNativeTarget(targetName: String): Boolean = when {
    hostOs.isMacOsX && targetName.startsWith("macos") -> true
    hostOs.isLinux && targetName == "linuxX64" -> true
    hostOs.isLinux && targetName == "linuxArm64" ->
        System.getProperty("os.arch").lowercase() in listOf("aarch64", "arm64") ||
            hasExecutable("aarch64-linux-gnu-gcc")

    hostOs.isLinux && targetName == "mingwX64" -> hasExecutable("x86_64-w64-mingw32-gcc")
    hostOs.isWindows && targetName == "mingwX64" -> true
    targetName.startsWith("androidNative") && androidNdkToolchain?.isFile == true -> true
    else -> false
}

val cmakeExecutable: String = rootProject.extra["cmakeExecutable"] as String

// ==================== Kotlin Multiplatform ====================
kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            // JDK 24+ warns about System.load calling into native code.
            jvmArgs("--enable-native-access=ALL-UNNAMED")
            // Print each test before it runs: a JNI crash kills the worker
            // without producing a test result, so the last STARTED line is the
            // only pointer to the failing test.
            testLogging {
                events("started", "failed")
            }
        }
    }

    android {
        namespace = "cn.enaium.opus"
        compileSdk = 37
        minSdk = 21

        withHostTest {}

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
        }
    }

    compilerOptions {
        // OpusNative uses expect/actual classes for the opaque native handles.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Desktop and server Kotlin/Native targets.
    macosArm64()
    macosX64()
    linuxX64()
    linuxArm64()
    mingwX64()

    // Kotlin/Native Android targets (apps that link libmain.so).
    androidNativeArm64()
    androidNativeArm32()
    androidNativeX64()
    androidNativeX86()

    // Web targets. Opus is compiled to a standalone WebAssembly module by the
    // buildWebWasm task; the Kotlin side talks to it through a small
    // synchronous JS bridge.
    js {
        nodejs()
    }
    wasmJs {
        nodejs()
    }

    // Keep the cinterop name identical across every native target so the
    // shared nativeMain implementation can call it.
    targets.withType<KotlinNativeTarget>().configureEach {
        val targetName = name
        val canBuild = canBuildNativeTarget(targetName)
        compilations.getByName("main") {
            cinterops.create("opus_kmp") {
                defFile(project.file("src/nativeInterop/cinterop/opus_kmp.def"))
                includeDirs(
                    project.file("src/nativeInterop/cinterop"),
                    jniDir.resolve("include"),
                )
                if (canBuild) {
                    val outputDir = layout.buildDirectory.dir("native/$targetName").get().asFile
                    extraOpts(
                        "-libraryPath", outputDir.absolutePath,
                        "-staticLibrary", "libopus_kmp.a",
                    )
                }
            }
        }
    }

    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }

        getByName("commonTest") {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        getByName("jvmMain") {
            dependencies {
                // Bundle every JNI artifact so consumers get the right native
                // binary out of the box; NativeLoader picks one at runtime.
                runtimeOnly(project(":jni-jvm-linux-x86_64"))
                runtimeOnly(project(":jni-jvm-linux-aarch64"))
                runtimeOnly(project(":jni-jvm-darwin-x86_64"))
                runtimeOnly(project(":jni-jvm-darwin-aarch64"))
                runtimeOnly(project(":jni-jvm-windows-x86_64"))
            }
        }

        getByName("jvmTest") {
            dependencies {
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }

        getByName("androidMain") {
            // Share the JNI declarations and loader with the JVM target. The
            // AAR supplies libopus_jni.so through jniLibs, so on Android the
            // loader falls back to System.loadLibrary.
            kotlin.srcDir("src/jvmMain/kotlin")
        }

        // Shared intermediate source sets. The default hierarchy template is
        // disabled so every edge below is explicit and deterministic.
        val commonMain = getByName("commonMain")
        val commonTest = getByName("commonTest")
        val nativeMain = maybeCreate("nativeMain").apply { dependsOn(commonMain) }
        val nativeTest = maybeCreate("nativeTest").apply { dependsOn(commonTest) }
        val webMain = maybeCreate("webMain").apply { dependsOn(commonMain) }
        val webTest = maybeCreate("webTest").apply { dependsOn(commonTest) }

        getByName("jsMain").dependsOn(webMain)
        getByName("wasmJsMain").dependsOn(webMain)
        getByName("jsTest").dependsOn(webTest)
        getByName("wasmJsTest").dependsOn(webTest)
    }
}

// Every Kotlin/Native target shares nativeMain/nativeTest. Wiring the
// dependency (rather than copying the source directory into each target) keeps
// expect/actual resolution and cinterop commonization working.
kotlin.targets.withType<KotlinNativeTarget>().configureEach {
    compilations.getByName("main").defaultSourceSet.dependsOn(
        kotlin.sourceSets.getByName("nativeMain"),
    )
    compilations.getByName("test").defaultSourceSet.dependsOn(
        kotlin.sourceSets.getByName("nativeTest"),
    )
}

// ==================== WebAssembly ====================
// Opus and the C shim are compiled to one standalone .wasm module. Its bytes
// are embedded into a generated Kotlin source file, so the published js/wasmJs
// artifact is self-contained: no resource lookup, fetch or async init.
val wasmConfigureDir = layout.buildDirectory.dir("wasm-cmake")
val wasmOutput = wasmConfigureDir.map { it.file("opus_wasm.wasm") }
val generatedWebDir = layout.buildDirectory.dir("generated/opusWeb/kotlin")

fun resolveEmsdk(): File? {
    System.getenv("EMSDK")?.takeIf { it.isNotBlank() }?.let {
        val dir = File(it)
        if (dir.resolve("emsdk").isFile) return dir
    }
    val home = System.getProperty("user.home") ?: return null
    return listOf(File(home, "emsdk"), File("/opt/emsdk"), File("/usr/local/emsdk"))
        .firstOrNull { it.resolve("emsdk").isFile }
}

fun resolveEmscripten(command: String): String {
    val emsdk = resolveEmsdk() ?: return command
    return listOf(
        emsdk.resolve(command),
        emsdk.resolve("upstream/emscripten/$command"),
    ).firstOrNull { it.isFile && it.canExecute() }?.absolutePath ?: command
}

fun Exec.configureEmscripten() {
    val emsdk = resolveEmsdk() ?: return
    environment("EMSDK", emsdk.absolutePath)
    environment("EM_CONFIG", emsdk.resolve(".emscripten").absolutePath)

    // Emscripten needs Python 3.10+, while a Gradle daemon started from the
    // desktop can inherit an older /usr/bin/python3 (3.9 on macOS). Prefer a
    // Homebrew interpreter when one is installed; CI images already have a new
    // enough python3 on PATH.
    val python = System.getenv("EMSDK_PYTHON")?.takeIf { it.isNotBlank() }
        ?: listOf("/opt/homebrew/bin/python3", "/usr/local/bin/python3")
            .map(::File)
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    if (python != null) environment("EMSDK_PYTHON", python)

    val extraPath = buildList {
        add(emsdk.resolve("upstream/emscripten").absolutePath)
        File(python ?: "").parent?.let { add(it) }
    }
    environment(
        "PATH",
        extraPath.joinToString(File.pathSeparator) +
            File.pathSeparator + (System.getenv("PATH") ?: ""),
    )
}

val configureWebWasm = tasks.register<Exec>("configureWebWasm") {
    group = "build"
    description = "Configures the Emscripten build of Opus."
    val buildDir = wasmConfigureDir.get().asFile
    workingDir = buildDir
    configureEmscripten()
    commandLine(
        resolveEmscripten("emcmake"), "cmake",
        jniDir.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DBUILD_JNI=OFF",
        "-DBUILD_WASM=ON",
        "-DOPUS_DIR=${opusDir.absolutePath}",
    )
    doFirst {
        buildDir.mkdirs()
    }
    inputs.files(
        rootProject.file("jni/CMakeLists.txt"),
        rootProject.file("jni/config.h.in"),
        rootProject.file("jni/opus_kmp.c"),
        rootProject.file("jni/include/opus_kmp.h"),
        rootProject.file("jni/include/opus_kmp_all.h"),
        rootProject.file("jni/opus_wasm_entry.c"),
        opusDir.resolve("cmake/OpusFunctions.cmake"),
    )
    inputs.dir(opusDir.resolve("src"))
    inputs.dir(opusDir.resolve("silk"))
    inputs.dir(opusDir.resolve("celt"))
    inputs.dir(opusDir.resolve("include"))
    outputs.dir(wasmConfigureDir)
}

val buildWebWasm = tasks.register<Exec>("buildWebWasm") {
    group = "build"
    description = "Compiles Opus and the KMP shim to a standalone WebAssembly module."
    dependsOn(configureWebWasm)
    val buildDir = wasmConfigureDir.get().asFile
    workingDir = buildDir
    configureEmscripten()
    commandLine(cmakeExecutable, "--build", ".", "--target", "opus_wasm", "--config", "Release")
    inputs.files(
        rootProject.file("jni/CMakeLists.txt"),
        rootProject.file("jni/config.h.in"),
        rootProject.file("jni/opus_kmp.c"),
        rootProject.file("jni/include/opus_kmp.h"),
        rootProject.file("jni/include/opus_kmp_all.h"),
        rootProject.file("jni/opus_wasm_entry.c"),
        opusDir.resolve("cmake/OpusFunctions.cmake"),
    )
    inputs.dir(opusDir.resolve("src"))
    inputs.dir(opusDir.resolve("silk"))
    inputs.dir(opusDir.resolve("celt"))
    inputs.dir(opusDir.resolve("include"))
    outputs.file(wasmOutput)
}

val generateWebWasmData = tasks.register<DefaultTask>("generateWebWasmData") {
    group = "build"
    description = "Embeds the compiled WebAssembly module into a Kotlin source file."
    dependsOn(buildWebWasm)
    val wasmBytes = wasmOutput
    val outputDir = generatedWebDir
    inputs.file(wasmBytes)
    outputs.dir(outputDir)
    doLast {
        val bytes = wasmBytes.get().asFile.readBytes()
        val base64 = Base64.getEncoder().encodeToString(bytes)
        val chunkSize = 4096
        val chunks = base64.chunked(chunkSize)
        val file = outputDir.get().asFile.resolve("cn/enaium/opus/OpusWasmData.kt")
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("// Generated by :opus-kmp:generateWebWasmData. Do not edit.")
                appendLine("package cn.enaium.opus")
                appendLine()
                appendLine("internal const val OPUS_WASM_BASE64_SIZE = ${base64.length}")
                appendLine("internal val OPUS_WASM_BASE64: String by lazy {")
                appendLine("    buildString(OPUS_WASM_BASE64_SIZE) {")
                chunks.forEachIndexed { index, _ ->
                    appendLine("        append(WASM_CHUNK_$index)")
                }
                appendLine("    }")
                appendLine("}")
                chunks.forEachIndexed { index, chunk ->
                    appendLine("private const val WASM_CHUNK_$index = \"$chunk\"")
                }
            },
        )
    }
}

kotlin.sourceSets.getByName("webMain").kotlin.srcDir(generatedWebDir)

tasks.matching {
    // Every task that reads webMain (compilation, source jars, publication
    // sources) must see the generated wasm data file.
    it.name.startsWith("compileKotlinJs") ||
        it.name.startsWith("compileKotlinWasmJs") ||
        it.name.startsWith("compileTestKotlinJs") ||
        it.name.startsWith("compileTestKotlinWasmJs") ||
        it.name == "jsSourcesJar" ||
        it.name == "wasmJsSourcesJar" ||
        it.name.contains("WebMain") ||
        it.name == "kotlinMultiplatformSourcesJar" ||
        it.name == "sourcesJar" ||
        it.name == "metadataSourcesJar"
}.configureEach {
    dependsOn(generateWebWasmData)
}

// ==================== Native static libraries ====================
fun registerNativeBuildTasks(targetName: String, cmakeFlags: List<String> = emptyList()) {
    val cmakeBuildDir = layout.buildDirectory.dir("cmake-native/$targetName").get().asFile
    val outputDir = layout.buildDirectory.dir("native/$targetName").get().asFile
    val canBuild = canBuildNativeTarget(targetName)

    val configureTask = tasks.register<Exec>("configureNative_$targetName") {
        onlyIf { canBuild }
        doFirst {
            cmakeBuildDir.mkdirs()
            outputDir.mkdirs()
        }
        workingDir = cmakeBuildDir
        commandLine(
            listOf(
                cmakeExecutable, jniDir.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release",
                "-DBUILD_JNI=OFF",
                "-DOPUS_DIR=${opusDir.absolutePath}",
                "-DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=${outputDir.absolutePath}",
            ) + cmakeFlags,
        )
    }

    val staticLibrary = File(outputDir, "libopus_kmp.a")
    val buildTask = tasks.register<Exec>("buildNative_$targetName") {
        onlyIf { canBuild }
        dependsOn(configureTask)
        workingDir = cmakeBuildDir
        commandLine(cmakeExecutable, "--build", ".", "--target", "opus_kmp", "--config", "Release")
        // No outputs: cmake's own dependency tracking decides what to recompile,
        // and Gradle must always let it run after a source change.
    }

    tasks.matching {
        it.name.startsWith("cinteropOpus_kmp") &&
            it.name.endsWith(targetName.replaceFirstChar { c -> c.uppercase() })
    }.configureEach {
        dependsOn(buildTask)
        // cinterop copies the archive into the klib; without this input a
        // rebuilt library would keep linking the stale copy.
        inputs.file(staticLibrary)
    }
}

if (hostOs.isMacOsX) {
    registerNativeBuildTasks("macosArm64", listOf("-DCMAKE_OSX_ARCHITECTURES=arm64"))
    registerNativeBuildTasks("macosX64", listOf("-DCMAKE_OSX_ARCHITECTURES=x86_64"))
} else if (hostOs.isLinux) {
    registerNativeBuildTasks("linuxX64")
    val arm64Flags = if (System.getProperty("os.arch").lowercase() in listOf("aarch64", "arm64")) {
        emptyList()
    } else {
        listOf(
            "-DCMAKE_SYSTEM_NAME=Linux",
            "-DCMAKE_SYSTEM_PROCESSOR=aarch64",
            "-DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc",
        )
    }
    registerNativeBuildTasks("linuxArm64", arm64Flags + listOf("-DCMAKE_C_FLAGS=-mno-outline-atomics"))
    registerNativeBuildTasks("mingwX64", listOf(
        "-DCMAKE_SYSTEM_NAME=Windows",
        "-DCMAKE_SYSTEM_PROCESSOR=x86_64",
        "-DCMAKE_C_COMPILER=x86_64-w64-mingw32-gcc",
    ))
} else if (hostOs.isWindows) {
    // CMake would pick the Visual Studio generator on a stock Windows runner,
    // producing an MSVC archive the MinGW-based Kotlin/Native link cannot use.
    registerNativeBuildTasks("mingwX64", listOf(
        "-G", "MinGW Makefiles",
        "-DCMAKE_C_COMPILER=x86_64-w64-mingw32-gcc",
    ))
}

// ==================== Android JNI shared libraries ====================
val androidJniAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

androidNdkToolchain?.takeIf { it.isFile }?.let { toolchain ->
    listOf(
        "androidNativeArm64" to "arm64-v8a",
        "androidNativeArm32" to "armeabi-v7a",
        "androidNativeX64" to "x86_64",
        "androidNativeX86" to "x86",
    ).forEach { (targetName, abi) ->
        registerNativeBuildTasks(targetName, listOf(
            "-DCMAKE_TOOLCHAIN_FILE=${toolchain.absolutePath}",
            "-DANDROID_ABI=$abi",
            "-DANDROID_PLATFORM=android-$androidApiLevel",
            // Kotlin/Native's Android runtime predates the NDK's outlined
            // atomics helpers; emit inline LL/SC sequences instead.
            "-DCMAKE_C_FLAGS=-mno-outline-atomics",
        ))
    }
}

val androidJniLibsDir = layout.buildDirectory.dir("jniLibs")
val buildAndroidJniLibs = tasks.register<DefaultTask>("buildAndroidJniLibs") {
    group = "build"
    description = "Builds libopus_jni.so for all Android ABIs."
}

androidJniAbis.forEach { abi ->
    val outputDir = layout.buildDirectory.dir("jniLibs/$abi")
    val cmakeBuildDir = layout.buildDirectory.dir("cmake-android-$abi").get().asFile

    val configureTask = tasks.register<Exec>("configureAndroidJni_$abi") {
        onlyIf { androidNdkToolchain?.isFile == true }
        doFirst {
            cmakeBuildDir.mkdirs()
            outputDir.get().asFile.mkdirs()
        }
        workingDir = cmakeBuildDir
        commandLine(
            listOf(
                cmakeExecutable, jniDir.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release",
                "-DBUILD_JNI=ON",
                "-DOPUS_DIR=${opusDir.absolutePath}",
                "-DCMAKE_TOOLCHAIN_FILE=${androidNdkToolchain?.absolutePath ?: ""}",
                "-DANDROID_ABI=$abi",
                "-DANDROID_PLATFORM=android-$androidApiLevel",
                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${outputDir.get().asFile.absolutePath}",
            ),
        )
    }

    val buildTask = tasks.register<Exec>("buildAndroidJni_$abi") {
        onlyIf { androidNdkToolchain?.isFile == true }
        dependsOn(configureTask)
        workingDir = cmakeBuildDir
        commandLine(cmakeExecutable, "--build", ".", "--target", "opus_jni", "--config", "Release")
    }

    buildAndroidJniLibs.configure { dependsOn(buildTask) }
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addStaticSourceDirectory(androidJniLibsDir.get().asFile.absolutePath)
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.contains("JniLibFolders") }
    .configureEach { dependsOn(buildAndroidJniLibs) }

// ==================== Android host tests ====================
// Android host-test targets (unit tests on the JVM) call System.loadLibrary
// directly, so they need a flat directory containing libopus_jni.{so,dylib}.
// Reuse the host's :jni-jvm-* subproject build output.
val hostJniProjectName = run {
    val arch = System.getProperty("os.arch").lowercase()
    val archClassifier = when (arch) {
        "amd64", "x86_64", "x64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        else -> null
    }
    val osClassifier = when {
        hostOs.isLinux -> "linux"
        hostOs.isMacOsX -> "darwin"
        hostOs.isWindows -> "windows"
        else -> null
    }
    if (osClassifier != null && archClassifier != null) ":jni-jvm-$osClassifier-$archClassifier" else null
}

if (hostJniProjectName != null) {
    val hostJniProject = project(hostJniProjectName)
    val hostNativeDir = hostJniProject.layout.buildDirectory.dir(
        "jni-native/${hostJniProjectName.removePrefix(":jni-jvm-")}",
    )
    tasks.withType<Test>().configureEach {
        if (name.contains("AndroidHostTest", ignoreCase = true)) {
            dependsOn("$hostJniProjectName:buildJniLibrary")
            systemProperty("java.library.path", hostNativeDir.get().asFile.absolutePath)
        }
    }
}

// ==================== wasm: npm package ====================
// The standalone WebAssembly module and the JS glue that owns its instance ship
// as @enaium/opus-kmp for web apps that are not written in Kotlin. Kotlin/JS
// and Kotlin/Wasm consumers use the Maven artifact instead: there the same glue
// and module bytes are embedded into the klib, so no asset lookup is needed.
//
// npm requires semver. A four-component binding version (1.5.2.1) carries this
// library's own revision in the fourth component, so it becomes the prerelease
// suffix (1.5.2-1). Versions that are already valid semver, such as
// 1.0.1-fixed.1.5.2, are published unchanged.
val fourPartVersion = Regex("""^(\d+)\.(\d+)\.(\d+)\.(\d+)$""")
val npmPackageVersion: String by lazy {
    val version = rootProject.version.toString()
    val match = fourPartVersion.matchEntire(version) ?: return@lazy version
    val (major, minor, patch, revision) = match.destructured
    "$major.$minor.$patch-$revision"
}

val wasmNpmDir = layout.buildDirectory.dir("npm").get().asFile
val wasmGlueSource = project.file("src/webMain/kotlin/cn/enaium/opus/OpusWeb.kt")

tasks.register("assembleWasmNpm") {
    group = "build"
    description = "Stages the @enaium/opus-kmp npm package in build/npm."
    dependsOn(buildWebWasm)
    inputs.file(wasmGlueSource)
    inputs.file(wasmOutput)
    inputs.property("npmPackageVersion", npmPackageVersion)
    outputs.dir(wasmNpmDir)
    doLast {
        wasmNpmDir.deleteRecursively()
        wasmNpmDir.mkdirs()
        wasmOutput.get().asFile.copyTo(wasmNpmDir.resolve("opus_wasm.wasm"), overwrite = true)

        val source = wasmGlueSource.readText()
        val marker = "internal const val OPUS_WEB_INIT_JS: String = \"\"\""
        val start = source.indexOf(marker)
        check(start >= 0) { "OPUS_WEB_INIT_JS is missing from ${wasmGlueSource.name}" }
        val bodyStart = start + marker.length
        val end = source.indexOf("\"\"\"", bodyStart)
        check(end >= 0) { "OPUS_WEB_INIT_JS is not terminated in ${wasmGlueSource.name}" }
        val initBody = source.substring(bodyStart, end).trim()
        check(initBody.startsWith("(base64)")) {
            "OPUS_WEB_INIT_JS no longer starts with the expected initializer"
        }

        wasmNpmDir.resolve("opus_kmp_glue.js").writeText(
            """
            // Generated by :opus-kmp:assembleWasmNpm. Do not edit.
            //
            // The initializer below is the same JS glue the Kotlin/JS and
            // Kotlin/Wasm targets embed; it owns the WebAssembly instance and
            // exposes the C shim on globalThis.__opusWeb.

            const opusInit = $initBody;

            function toBase64(bytes) {
              let text = '';
              const chunk = 0x8000;
              for (let i = 0; i < bytes.length; i += chunk) {
                text += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
              }
              return btoa(text);
            }

            /** Initializes from an already-encoded WebAssembly module. */
            export function initOpusKmpFromBase64(base64) {
              opusInit(base64);
              return globalThis.__opusWeb;
            }

            /**
             * Fetches the module (browser) or accepts raw bytes (Node, bundlers)
             * and initializes it. Pass a Uint8Array/ArrayBuffer when fetch cannot
             * read the source, for example a file read with readFile in Node.
             */
            export async function initOpusKmp(
              source = new URL('./opus_wasm.wasm', import.meta.url),
            ) {
              let bytes;
              if (source instanceof Uint8Array) {
                bytes = source;
              } else if (source instanceof ArrayBuffer) {
                bytes = new Uint8Array(source);
              } else {
                const response = await fetch(source);
                if (!response.ok) {
                  throw new Error(
                    'failed to fetch the Opus WebAssembly module: ' + response.status,
                  );
                }
                bytes = new Uint8Array(await response.arrayBuffer());
              }
              return initOpusKmpFromBase64(toBase64(bytes));
            }

            /** The C shim installed on globalThis by the most recent init. */
            export function getOpusWeb() {
              return globalThis.__opusWeb;
            }

            export default initOpusKmp;
            """.trimIndent() + "\n",
        )

        wasmNpmDir.resolve("README.md").writeText(
            """
            # @enaium/opus-kmp

            Standalone WebAssembly build of Opus, with the same JS glue used by
            the `opus-kmp` Kotlin/JS and Kotlin/Wasm targets.

            ```js
            import { initOpusKmp } from '@enaium/opus-kmp';

            await initOpusKmp();            // browser: fetch('./opus_wasm.wasm')
            const opus = globalThis.__opusWeb;
            console.log(opus.versionString());
            ```

            In Node, read the wasm file yourself because `fetch` does not support
            `file:` URLs:

            ```js
            import { readFile } from 'node:fs/promises';
            import { initOpusKmp } from '@enaium/opus-kmp';

            const bytes = await readFile(new URL('@enaium/opus-kmp/opus_wasm.wasm', import.meta.url));
            await initOpusKmp(bytes);
            ```

            `initOpusKmpFromBase64(base64)` is available when the module is
            already encoded. The lower-level API is the C shim installed on
            `globalThis.__opusWeb`; the Kotlin API lives in the
            `cn.enaium.opus:opus-kmp` Maven artifact.

            License: MIT.
            """.trimIndent() + "\n",
        )

        wasmNpmDir.resolve("package.json").writeText(
            """
            {
              "name": "@enaium/opus-kmp",
              "version": "$npmPackageVersion",
              "description": "Standalone Opus WebAssembly module and JS loader for opus-kmp",
              "license": "MIT",
              "type": "module",
              "exports": {
                ".": "./opus_kmp_glue.js",
                "./opus_wasm.wasm": "./opus_wasm.wasm",
                "./package.json": "./package.json"
              },
              "files": ["opus_kmp_glue.js", "opus_wasm.wasm", "README.md"],
              "homepage": "https://github.com/Enaium/opus-kmp",
              "repository": {
                "type": "git",
                "url": "git+https://github.com/Enaium/opus-kmp.git"
              }
            }
            """.trimIndent() + "\n",
        )
    }
}

tasks.register<Exec>("publishWasmNpm") {
    group = "publishing"
    description = "Publishes @enaium/opus-kmp to npm (token: npm.publish.key)."
    dependsOn("assembleWasmNpm")
    workingDir = wasmNpmDir
    // The token lives in ~/.gradle/gradle.properties, next to the Maven Central
    // and signing credentials; CI writes it there from NPM_PUBLISH_KEY.
    // -Pnpm.publish.key=<token> overrides it for one-off runs.
    val npmKey = providers.gradleProperty("npm.publish.key")
    doFirst {
        val key = npmKey.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException(
                "npm.publish.key is not set: add it to ~/.gradle/gradle.properties " +
                    "or pass -Pnpm.publish.key=<token>",
            )
        // npm expands NODE_AUTH_TOKEN from the environment, so the token never
        // lands in package.json or the tarball.
        environment("NODE_AUTH_TOKEN", key)
        wasmNpmDir.resolve(".npmrc").writeText(
            "//registry.npmjs.org/:_authToken=\${NODE_AUTH_TOKEN}\n",
        )
    }
    commandLine("npm", "publish", "--access", "public", "--tag", "latest")
}

// ==================== Publishing ====================
val hasSigningKey = rootProject.extra["hasSigningKey"] as Boolean

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    if (hasSigningKey) signAllPublications()

    coordinates(
        groupId = group.toString(),
        artifactId = "opus-kmp",
        version = null,
    )

    pom {
        name.set("opus-kmp")
        description.set(
            "Kotlin Multiplatform bindings for Opus: low-latency speech and audio encoding, decoding, packet inspection and repacketization on JVM, Android, Kotlin/Native and the web.",
        )
        url.set("https://github.com/Enaium/opus-kmp")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("Enaium")
            }
        }

        scm {
            url.set("https://github.com/Enaium/opus-kmp")
            connection.set("scm:git:git@github.com:Enaium/opus-kmp.git")
            developerConnection.set("scm:git:git@github.com:Enaium/opus-kmp.git")
        }

        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/Enaium/opus-kmp/issues")
        }
    }
}
