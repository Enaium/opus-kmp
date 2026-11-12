# opus-kmp

Kotlin Multiplatform bindings for [Opus](https://github.com/xiph/opus), the
low-latency interactive audio codec: encoding, decoding, packet inspection and
repacketization on JVM, Android, Kotlin/Native and the web.

`opus` is included as a Git submodule and compiled from source for every
target. One C ABI, [`jni/include/opus_kmp.h`](jni/include/opus_kmp.h), feeds
the JNI bridge, the Kotlin/Native cinterop and the WebAssembly build, so every
platform shares the same argument validation, buffer ownership and packet
semantics. The portable floating-point build with runtime CPU dispatch disabled
is used everywhere: one code path across x86, ARM, Android, MinGW and
WebAssembly.

Binding version: **1.5.2.1** (Opus 1.5.2).

## Supported targets

| Platform | Kotlin target | Native artifact |
|---|---|---|
| JVM | `jvm` | `opus-kmp-jni-jvm-{linux,darwin}-{x86_64,aarch64}`, `opus-kmp-jni-jvm-windows-x86_64` |
| Android | `android` | `opus-kmp` AAR with `libopus_jni.so` for every ABI |
| Android Native | `androidNativeArm64`, `androidNativeArm32`, `androidNativeX64`, `androidNativeX86` | static archive embedded in the cinterop klib |
| Linux | `linuxX64`, `linuxArm64` | static archive embedded in the cinterop klib |
| Windows | `mingwX64` | static archive embedded in the cinterop klib |
| macOS | `macosX64`, `macosArm64` | static archive embedded in the cinterop klib |
| Kotlin/JS | `js` | embedded WebAssembly module |
| Kotlin/Wasm | `wasmJs` | embedded WebAssembly module |

The web targets compile Opus and the C shim to a standalone `.wasm` module with
Emscripten, embed its bytes into the published klib and instantiate it
synchronously through a small JS bridge. No resource lookup or async
initialization is required at runtime.

The same module is also published to npm as `@enaium/opus-kmp` for plain
JavaScript/TypeScript web apps. Stage it with
`./gradlew :opus-kmp:assembleWasmNpm`; it contains `opus_wasm.wasm` and
`opus_kmp_glue.js`, whose `initOpusKmp()` installs the C shim on
`globalThis.__opusWeb`. Kotlin consumers do not need the npm package.

## Using the library

```kotlin
dependencies {
    implementation("cn.enaium.opus:opus-kmp:1.5.2.1")
}
```

The Android AAR and the JVM artifacts contain the native libraries. JVM
consumers automatically receive the per-OS/arch JNI artifacts and
`NativeLoader` extracts the matching one from the classpath.

### Encoding and decoding

Opus accepts 8, 12, 16, 24 and 48 kHz, one or two channels, and frame durations
of 2.5, 5, 10, 20, 40 or 60 ms. At 48 kHz a 20 ms frame is 960 samples per
channel.

```kotlin
import cn.enaium.opus.*

val encoder = OpusEncoder.open(48_000, channels = 1, OpusApplication.AUDIO)
val decoder = OpusDecoder.open(48_000, channels = 1)

try {
    encoder.bitrate = 32_000
    encoder.complexity = 8
    encoder.vbr = true
    encoder.inbandFec = true
    encoder.packetLossPercent = 10
    encoder.dtx = true

    while (hasPcm()) {
        val pcm = readFrame(encoder.frameSizeSamples(20.0))  // ShortArray, 16-bit
        val packet = encoder.encode(pcm)
        if (packet.isNotEmpty()) {
            sink.write(packet)
            speaker.write(decoder.decode(packet))
        }
    }

    // A lost packet: run packet-loss concealment instead of consuming input.
    speaker.write(decoder.decode(null, frameSize = 960))
} finally {
    encoder.close()
    decoder.close()
}
```

`encode` also accepts an `offset`, an explicit frame size and a packet-size
budget. The floating-point API uses `FloatArray` in `-1..1`:

```kotlin
val packet = encoder.encodeFloat(samples)   // FloatArray
val samples = decoder.decodeFloat(packet)   // FloatArray
```

`OpusDecoder.decode(null)` conceals a lost packet; `decodeFec(packet)` recovers
the previous frame from the in-band FEC of the following packet.

### Packet inspection

```kotlin
val bandwidth = OpusPacket.bandwidth(packet)                  // OpusBandwidth
val frames = OpusPacket.frameCount(packet)                    // 1..48
val samples = OpusPacket.sampleCount(packet, sampleRate)      // per channel
val hasFec = OpusPacket.hasLbrr(packet)
val frameBytes = OpusPacket.frames(packet)                    // List<ByteArray>
```

`OpusPacket.pad` and `OpusPacket.unpad` adjust padding without re-encoding.

### Repacketization

`OpusRepacketizer` combines and splits packets without decoding audio. All
input packets must use the same TOC configuration.

```kotlin
OpusRepacketizer().use { repacketizer ->
    repacketizer.cat(first)
    repacketizer.cat(second)
    val combined = repacketizer.out()          // one 40 ms packet
    val single = repacketizer.outRange(0, 1)   // the first 20 ms frame
}
```

### Soft clipping

```kotlin
val clipped = Opus.softClip(floats, frameSize = 960, channels = 1)
```

### Version and errors

```kotlin
println(Opus.version)                  // "libopus 1.5.2"
Opus.strerror(-4)                      // "Invalid packet"
// Every rejected call throws OpusException with the negative Opus code.
```

## Examples

`examples/visual` is a Kotlin Multiplatform Dear ImGui application (imgui-kmp +
SDL3). It drives an encoder/decoder pair and plots the input, the decoded output
and the packet-size history through ImPlot. The input source is either a
synthetic speech-like signal or a real microphone captured through audio-io-kmp
(Core Audio, ALSA, WASAPI or Android AudioRecord); mono capture can feed either
one or both Opus channels.

```bash
./gradlew :examples:visual:jvmRun
./gradlew :examples:visual:jvmRun --args="--channels 2"
./gradlew :examples:visual:jvmRun --args="--microphone"
# or a standalone native executable:
./gradlew :examples:visual:linkDebugExecutableMacosArm64
OPUS_KMP_CHANNELS=2 ./examples/visual/build/bin/macosArm64/debugExecutable/visual.kexe
OPUS_KMP_MICROPHONE=1 ./examples/visual/build/bin/macosArm64/debugExecutable/visual.kexe
```

The same UI runs on Android through a thin `SDLActivity` host. The Android app
module uses the pure-JVM Android path: the common ImGui loop runs on SDL's
dedicated thread while the AARs supply `libopus_jni.so`, `libimgui_jni.so` and
`libsdl_jni.so` for every ABI.

```bash
./gradlew :examples:visual:android-app:assembleDebug
adb install -r examples/visual/android/build/outputs/apk/debug/android-app-debug.apk
adb shell am start -n cn.enaium.opus.examples.visual.android/.MainActivity
```

The app runs in landscape, scales the ImGui font and control widths by the
display density, and asks for `RECORD_AUDIO` at startup. Choose `microphone` in
the source combo to switch the visualizer from the synthetic signal to the
device input. Intent extras make automated runs deterministic:

```bash
adb shell am start -n cn.enaium.opus.examples.visual.android/.MainActivity \
  --ei channels 2 --ez microphone true
```

Headless smoke run:

```bash
SDL_VIDEO_DRIVER=dummy OPUS_KMP_FRAMES=30 \
  ./examples/visual/build/bin/macosArm64/debugExecutable/visual.kexe
```

## Building and testing

```bash
# host targets
./gradlew :opus-kmp:jvmTest :opus-kmp:macosArm64Test
# web (needs emsdk on PATH or in ~/emsdk)
./gradlew :opus-kmp:jsNodeTest :opus-kmp:wasmJsNodeTest
# Android AAR and per-ABI JNI libraries (needs Android NDK 27)
./gradlew :opus-kmp:bundleAndroidMainAar :opus-kmp:buildAndroidJniLibs
# local Maven publication
./gradlew :opus-kmp:publishToMavenLocal
# npm package staging
./gradlew :opus-kmp:assembleWasmNpm
```

The library uses CMake for every native artifact. Configure it directly when
debugging a platform:

```bash
cmake -S jni -B build -DBUILD_JNI=ON -DOPUS_DIR=$PWD/opus
cmake --build build --target opus_jni
emcmake cmake -S jni -B build-wasm -DBUILD_WASM=ON -DOPUS_DIR=$PWD/opus
cmake --build build-wasm --target opus_wasm
```

## Publishing

`.github/workflows/ci.yml` publishes every host artifact to Maven Local, runs
the JVM/native/web tests and builds the visual example headlessly.
`.github/workflows/release.yml` releases to Maven Central from three runners
(macOS, Linux, Windows) and then publishes the npm package. It needs these
repository secrets:

| Secret | Purpose |
|---|---|
| `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` | Sonatype Central Portal user token |
| `SIGNING_KEY` / `SIGNING_KEY_ID` / `SIGNING_PASSWORD` | PGP signing key, base64-encoded binary ring |
| `NPM_PUBLISH_KEY` | npm token for `@enaium/opus-kmp` |

npm only accepts three-component semver. The fourth component of the binding
version is published as a prerelease suffix: `1.5.2.1` becomes npm `1.5.2-1`.

## License

This binding is MIT-licensed. Opus itself is BSD-3-Clause; see
[`opus/COPYING`](opus/COPYING).
