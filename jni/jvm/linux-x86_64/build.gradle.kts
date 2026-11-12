/*
 * Prebuilt JNI shared library for opus-kmp on linux/x86_64.
 *
 * Ships libopus_jni.so as a classpath resource at
 * /cn/enaium/opus/native/linux-x86_64/, which NativeLoader (in :opus-kmp's
 * jvmMain) extracts and System.load()s at runtime.
 */
import org.gradle.internal.os.OperatingSystem

plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

group = rootProject.group
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

val jniOs = "linux"
val jniArch = "x86_64"
val classifier = "$jniOs-$jniArch"
val libFile = "libopus_jni.so"
val resourceDir = "cn/enaium/opus/native/$classifier"

val canBuildHere = OperatingSystem.current().isLinux

val cmakeExecutable: String = rootProject.extra["cmakeExecutable"] as String

val nativeOutputDir = layout.buildDirectory.dir("jni-native/$classifier")
val cmakeBuildDir = layout.buildDirectory.dir("cmake-jni/$classifier")

val configureJniLibrary = tasks.register<Exec>("configureJniLibrary") {
    group = "build"
    description = "cmake-configures opus_jni for $classifier."
    onlyIf { canBuildHere }
    val outDir = nativeOutputDir.get().asFile
    val buildDir = cmakeBuildDir.get().asFile
    doFirst {
        outDir.mkdirs()
        buildDir.mkdirs()
    }
    workingDir = buildDir
    val javaHome = System.getProperty("java.home") ?: System.getenv("JAVA_HOME") ?: ""
    val jniInclude = if (javaHome.isNotEmpty()) "$javaHome/include" else ""
    commandLine(
        cmakeExecutable,
        rootProject.file("jni").absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DBUILD_JNI=ON",
        "-DOPUS_DIR=${rootProject.file("opus").absolutePath}",
        "-DJNI_INCLUDE_DIR=$jniInclude",
        "-DJNI_INCLUDE_DIR_PLATFORM=$jniInclude/linux",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${outDir.absolutePath}",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${outDir.absolutePath}",
    )
}

val buildJniLibrary = tasks.register<Exec>("buildJniLibrary") {
    group = "build"
    description = "Builds $libFile for $classifier."
    onlyIf { canBuildHere }
    dependsOn(configureJniLibrary)
    workingDir = cmakeBuildDir.get().asFile
    commandLine(cmakeExecutable, "--build", ".", "--target", "opus_jni", "--config", "Release")
    inputs.files(
        rootProject.file("jni/CMakeLists.txt"),
        rootProject.file("jni/config.h.in"),
        rootProject.file("jni/opus_kmp.c"),
        rootProject.file("jni/include/opus_kmp.h"),
        rootProject.file("jni/include/opus_kmp_all.h"),
        rootProject.file("jni/opus_jni.c"),
    )
    inputs.dir(rootProject.file("opus/src"))
    inputs.dir(rootProject.file("opus/silk"))
    inputs.dir(rootProject.file("opus/celt"))
    inputs.dir(rootProject.file("opus/include"))
    outputs.file(nativeOutputDir.map { it.file(libFile) })
}

tasks.named<Copy>("processResources") {
    dependsOn(buildJniLibrary)
    from(nativeOutputDir.map { it.file(libFile) }) {
        into(resourceDir)
    }
}

val hasSigningKey = rootProject.extra["hasSigningKey"] as Boolean

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    if (hasSigningKey) signAllPublications()
    coordinates(
        groupId = rootProject.group.toString(),
        artifactId = "opus-kmp-jni-jvm-$classifier",
        version = rootProject.version.toString(),
    )
    pom {
        name.set("opus-kmp-jni-jvm-$classifier")
        description.set(
            "Prebuilt JNI shared library for opus-kmp on $jniOs/$jniArch. " +
                "Loaded automatically by NativeLoader; not intended to be depended on directly.",
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
