plugins {
    // AGP is on the buildscript classpath through the KMP library plugin
    // declaration in the root build; a second version request would fail.
    id("com.android.application")
}

// Thin Android host for the shared visual example. MainActivity extends
// org.libsdl.app.SDLActivity (packaged by sdl-kmp-android-jvm) and runs the
// commonMain ImGui loop on SDL's own thread; the AARs supply libsdl_jni.so,
// libimgui_jni.so and libopus_jni.so for every ABI.
android {
    namespace = "cn.enaium.opus.examples.visual.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "cn.enaium.opus.examples.visual.android"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":examples:visual"))
    // Direct dependencies make the Android variants and their jniLibs part of
    // the APK even if the example library's implementation dependencies are
    // not exposed transitively.
    implementation(project(":opus-kmp"))
    implementation(libs.sdl.kmp)
    implementation(libs.imgui.kmp)
}
