pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "opus-kmp"

include(":opus-kmp")
include(":examples:visual")
include(":examples:visual:android-app")
project(":examples:visual:android-app").projectDir = file("examples/visual/android")

// Per-OS/arch JNI artifacts that ship the prebuilt libopus_jni shared library
// as a classpath resource. NativeLoader extracts the matching one at runtime.
listOf(
    "linux-x86_64",
    "linux-aarch64",
    "darwin-x86_64",
    "darwin-aarch64",
    "windows-x86_64",
).forEach { classifier ->
    val name = ":jni-jvm-$classifier"
    include(name)
    project(name).projectDir = file("jni/jvm/$classifier")
}
