import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        mainRun {
            mainClass = "cn.enaium.opus.examples.visual.Main_jvmKt"
        }
    }

    // The Android path runs the same commonMain UI inside SDLActivity; the
    // example app module hosts the activity and the AARs supply the JNI
    // libraries (opus_kmp, sdl_jni, imgui_jni).
    android {
        namespace = "cn.enaium.opus.examples.visual"
        compileSdk = 37
        minSdk = 24

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
        }
    }

    // The same UI source builds into a standalone executable on every desktop
    // target imgui-kmp and sdl-kmp publish klibs for.
    macosArm64 {
        binaries.executable()
    }

    macosX64 {
        binaries.executable()
    }

    linuxX64 {
        binaries.executable()
    }

    mingwX64 {
        binaries.executable()
    }

    compilerOptions {
        // The library and this example use expect/actual classes.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // This repository disables the automatic hierarchy template in
    // gradle.properties, so materialize the shared native source set here.
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":opus-kmp"))
                // Dear ImGui/ImPlot and the SDL3 window they are driven from.
                implementation(libs.imgui.kmp)
                implementation(libs.sdl.kmp)
                // Real microphone capture for the "microphone" source.
                implementation(libs.audio.io.kmp)
            }
        }
    }
}

// SDL has to own the first thread on macOS. `jvmRun` passes the flag; an IDE
// run configuration has to add it itself (the JVM entry point explains that).
tasks.withType<JavaExec>().configureEach {
    if (OperatingSystem.current().isMacOsX) {
        jvmArgs("--enable-native-access=ALL-UNNAMED", "-XstartOnFirstThread")
    }
}
