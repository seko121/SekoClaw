# Kai Linux Sandbox port to OctoBot

## Scope

This port reuses Kai's production Alpine/PRoot runtime, persistent Bash shell, package manager logic, file access layer and sandbox ViewModels. Siko-specific code is limited to Android wiring, Siko's visual system, agent tool registration, and file import/export/copy/move adapters required by Siko's product requirements.

## Source mapping

| Kai source | OctoBot destination | Adaptation |
|---|---|---|
| `composeApp/src/androidMain/.../sandbox/LinuxSandboxManager.kt` | `app/src/main/java/com/sikoclaw/app/linux/kai/LinuxSandboxManager.kt` | Package/import changes; removed Kai conversation repository injection while preserving shell/runtime behavior. |
| `composeApp/src/androidMain/.../sandbox/RootfsDownloader.kt` | `app/src/main/java/com/sikoclaw/app/linux/kai/RootfsDownloader.kt` | Package change only. Alpine 3.22.5 and Kai mirror fallback retained. |
| `composeApp/src/androidMain/.../sandbox/ProotExecutor.kt` | `app/src/main/java/com/sikoclaw/app/linux/kai/ProotExecutor.kt` | Package change only. |
| `composeApp/src/androidMain/.../sandbox/PersistentSandboxShell.kt` | `app/src/main/java/com/sikoclaw/app/linux/kai/PersistentSandboxShell.kt` | Package/import changes only. Persistent Bash process retained. |
| `composeApp/src/androidMain/.../sandbox/SessionShell.kt` | `app/src/main/java/com/sikoclaw/app/linux/kai/SessionShell.kt` | Package/import changes only. |
| `composeApp/src/androidMain/.../sandbox/SandboxFiles.kt` | `app/src/main/java/com/sikoclaw/app/linux/kai/SandboxFiles.kt` | Package change only. |
| `composeApp/src/androidMain/.../sandbox/SandboxState.kt` | `app/src/main/java/com/sikoclaw/app/linux/kai/SandboxState.kt` | Package change only. |
| `composeApp/src/jvmShared/.../sandbox/SshConfigManager.kt` | `app/src/main/java/com/sikoclaw/app/linux/kai/SshConfigManager.kt` | Package change only. |
| `composeApp/src/commonMain/.../SandboxController.kt` | `app/src/main/java/com/sikoclaw/app/linux/SandboxController.kt` | Converted KMP contract to Android interface; added secure Siko file copy/move/import/export operations. |
| `composeApp/src/androidMain/.../SandboxController.android.kt` | `app/src/main/java/com/sikoclaw/app/linux/SikoSandboxController.kt` | Replaced Koin with Siko application singleton; added ContentResolver import/export adapter. |
| `composeApp/src/commonMain/.../SandboxSessionViewModel.kt` | `app/src/main/java/com/sikoclaw/app/linux/ui/SandboxSessionViewModel.kt` | Removed Kai conversation-following dependency; retained persistent live session and added in-session command history controls. |
| `composeApp/src/commonMain/.../SandboxFileBrowserViewModel.kt` | `app/src/main/java/com/sikoclaw/app/linux/ui/SandboxFileBrowserViewModel.kt` | Android resources converted to Siko strings; behavior retained. |
| `composeApp/src/commonMain/.../SandboxPackagesViewModel.kt` | `app/src/main/java/com/sikoclaw/app/linux/ui/SandboxPackagesViewModel.kt` | KMP resource strings converted to Siko strings; apk logic retained. |
| Kai `TerminalSheet.kt`, `SandboxTabsContent.kt`, `SandboxFileBrowserScreen.kt`, `SandboxPackagesScreen.kt`, `SandboxSettings.kt` | `app/src/main/java/com/sikoclaw/app/ui/settings/LinuxSandboxActivity.kt` | Kai interaction logic composed into Siko's AppScreenScaffold, colors, cards, typography and safe insets. |
| Kai `ShellCommandTool.kt` behavior | `app/src/main/java/com/sikoclaw/app/tool/impl/LinuxSandboxTool.kt` | Adapted to Siko `BaseTool`; blocks agent commands unless installed, ready and enabled. |
| `androidApp/src/main/jniLibs/<ABI>/libproot*.so`, `libtalloc.so` | `app/src/main/jniLibs/<ABI>/` | Copied without modification for arm64-v8a, armeabi-v7a and x86_64. Release remains arm64-v8a per Siko configuration. |

## Build and Android integration

- Added Ktor client core and OkHttp engine used by Kai rootfs downloads.
- Added Kotlin immutable collections used by the copied package ViewModel.
- Added `LinuxSandboxActivity` with `adjustResize` in the manifest.
- Added FileProvider path for the exported sandbox home.
- Added R8 keep rules for the Linux package and Ktor warning rule.
- Registered `linux_shell` in Siko's tool registry.
- Added Linux Sandbox to Settings with install, progress, cancel, enable/disable, basic packages, size, status and uninstall controls.

## Licensing

- Kai is Apache-2.0. Its full license is bundled as `assets/licenses/KAI_LICENSE_APACHE_2_0.txt`.
- Kai attribution is bundled in `assets/licenses/KAI_LINUX_SANDBOX_NOTICE.txt` and shown in Settings > About.
- Kai's third-party notice is bundled as `assets/licenses/KAI_THIRD_PARTY_LICENSES.md`.
- PRoot binaries are GPL-2.0 and executed as a separate process, matching Kai's distribution model.
- talloc is LGPL-3.0 and remains a PRoot runtime dependency.
- No file was excluded for a license restriction.

## Verification snapshot (2026-07-19)

- `:app:compileDebugKotlin`: PASS.
- `:app:testDebugUnitTest`: PASS.
- `:app:assembleRelease`: PASS.
- Clean UI launch and install screen: PASS on Android emulator.
- Rootfs mirror download and extraction: PASS on the available x86_64 emulator.
- PRoot `apk update`: BLOCKED on the available Android 16KB-page x86_64 emulator (`signal 11`). This emulator reports page size 16384. It is not an arm64-v8a runtime acceptance result.
- Required on-device acceptance remains: run the produced arm64 release on a normal arm64-v8a phone, then execute the terminal/files/packages test sequence. The application surfaces the real PRoot error and does not report a false Ready state.

## Runtime acceptance checklist for arm64 phone

1. Install Alpine and wait for Ready.
2. Install Basic Packages.
3. Run `echo "Hello from OctoBot"` and `pwd`.
4. Run `cd /root && touch siko-test.txt`; confirm it appears in Files.
5. Edit and reopen the file.
6. Search/install/remove a package in Packages.
7. Restart the app and confirm the rootfs and home remain.
8. Uninstall and reinstall the sandbox.

