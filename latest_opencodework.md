# SipCall - Current State

## What We've Done

### Analysis Complete
- **Project**: Android/Kotlin SIP calling app using PJSIP (`com.github.VoiSmart:pjsip-android:2.5.0`), Start.io SDK, Firebase, Room, Compose
- **Bug**: Signed release arm64 APK crashes on device with native SIGABRT in `libpjsua2.so`. Debug and universal debug builds work fine.

### Root Cause
The crash is a **native assertion** in PJSIP's `pj_mutex_lock` when `mutex` is NULL. This happens because:
1. **PJSIP functions called from unregistered threads**: `SipEngine.init()` calls `Endpoint()` constructor, `libCreate()`, and `libRegisterThread()` from a `Dispatchers.IO` coroutine thread that is NOT registered with PJSIP.
2. **Race condition**: Both `IPDialApplication.onCreate()` (line 22) and `SipService.onCreate()` (line 164) call `SipEngine.init()` concurrently from `Dispatchers.IO` coroutines without synchronization. The `if (endpoint != null) return` guard is not thread-safe.
3. **`libCreate()` silently fails on unregistered threads**: `pjsua_create()` doesn't properly initialize internal state (including the Endpoint's `mutex` field) when called from an unregistered thread. The SWIG binding may throw on error, but the race condition corrupts the singleton global state.
4. **`libRegisterThread()` calls `pj_mutex_lock(this->mutex)`**: When `mutex` is NULL (because `libCreate()` didn't init it), the assertion fires and aborts the process.

### Crash Pattern (from tombstones)
```
Abort message: '../src/pj/os_core_unix.c:1278: pj_status_t pj_mutex_lock(pj_mutex_t *): assertion "mutex" failed'
backtrace:
  #02 libpjsua2.so (pj_mutex_lock+124)
  #03 libpjsua2.so (pj::Endpoint::libRegisterThread(...)+640)
  #09 com.ipdial.service.SipEngine.init+194
  #11 ip.invokeSuspend+376  (coroutine on Dispatchers.IO)
  #24 art::Thread::CreateCallback  (thread name: "DefaultDispatch" = truncated "DefaultDispatcher-worker-*")
```

### Changes Made So Far
1. **`app/src/main/java/com/ipdial/service/SipEngine.kt`**: Added `@Synchronized` to `init()` and attempted main-thread Endpoint creation via `Handler(Looper.getMainLooper()).post` + `CountDownLatch`. **This did NOT fix the crash** — `mutex` is still NULL in `libRegisterThread`.

### Current State
- APK builds and installs successfully
- App still crashes on launch with the same `pj_mutex_lock` assertion
- The fix needs a more fundamental approach: **call `pj_thread_register()` BEFORE creating the Endpoint**

## What's Still Needed

### Fix Approach (choose one)

**Option A: Custom JNI wrapper (requires NDK)**
- Create `app/src/main/cpp/pjsip_register.cpp` with a JNI function calling `pj_thread_register()`
- Create `CMakeLists.txt` to build it
- Update `build.gradle` to include externalNativeBuild
- Call the JNI function before `Endpoint()` constructor in `init()`

**Option B: Install NDK first**
- NDK not currently installed
- Install via `sdkmanager` or Android Studio

**Option C: Alternative approach without NDK**
- Use a prebuilt wrapper .so or
- Find another way to register the PJSIP thread

### Files to Modify
- `app/src/main/java/com/ipdial/service/SipEngine.kt` — already modified, needs further changes
- `app/src/main/cpp/pjsip_register.cpp` — new (needs NDK)
- `app/src/main/cpp/CMakeLists.txt` — new (needs NDK)
- `app/build.gradle` — needs externalNativeBuild block

### Build & Test Commands
```bash
# Install NDK components
sdkmanager "ndk;27.0.12077973" "cmake;3.22.1"

# Build
./gradlew clean assembleRelease

# Install
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk

# Launch & monitor
adb logcat -c && adb shell monkey -p com.ipdial -c android.intent.category.LAUNCHER 1 && sleep 3 && adb logcat -d | grep -E "SipEngine|SipService|CRASH|FATAL|SIGABRT|assert|libc" | tail -30
```

### Device Info
- OnePlus device, arm64-v8a
- Android version (API level from build.gradle: 37 = Android 14)
- PJSIP native library: `libpjsua2.so` (arm64-v8a)
