package com.sikoclaw.app.linux.kai

import android.os.Build
import com.sikoclaw.app.utils.KVUtils

enum class LinuxCompatibilityMode(val label: String, val description: String) {
    AUTOMATIC("Automatic", "Retry once without seccomp when the device restricts PRoot"),
    STANDARD("Standard", "Use normal PRoot behavior"),
    NO_SECCOMP("No seccomp", "Always set PROOT_NO_SECCOMP=1 for restrictive emulators"),
    DISABLED("Disabled", "Do not start the Linux sandbox");

    companion object {
        fun current(): LinuxCompatibilityMode = runCatching {
            valueOf(KVUtils.getString("LINUX_COMPATIBILITY_MODE", AUTOMATIC.name))
        }.getOrDefault(AUTOMATIC)

        fun emulatorLikely(): Boolean {
            val fingerprint = Build.FINGERPRINT.lowercase()
            val model = Build.MODEL.lowercase()
            return fingerprint.contains("generic") || fingerprint.contains("emulator") ||
                model.contains("sdk") || model.contains("emulator") || model.contains("virtual")
        }
    }
}

object LinuxCompatibilitySettings {
    fun mode(): LinuxCompatibilityMode = LinuxCompatibilityMode.current()
    fun setMode(mode: LinuxCompatibilityMode) = KVUtils.putString("LINUX_COMPATIBILITY_MODE", mode.name)
}
