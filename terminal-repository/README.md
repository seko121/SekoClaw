# Siko Environment package repository

This directory defines the artifact contract consumed by Siko Claw. It is not a
mirror of Termux, Debian, Ubuntu, or Alpine. Every executable must be compiled
for Android Bionic and the Siko prefix:

`/data/data/com.sikoclaw.app/files/terminal/usr`

The initial supported architecture is `arm64-v8a`. Packages built for another
ABI or another application prefix must never be published here.

## Published layout

```text
arm64-v8a/
  base-system.tar.zst
  index.json
  packages/
    <name>-<version>.tar.zst
```

`base-system.tar.zst` contains paths relative to the prefix, never absolute
paths. It must include executable `bin/bash`, `bin/pkg`, `bin/curl`, and
`bin/git`. The app verifies the archive SHA-256, blocks path traversal, stages
the extraction under `usr.tmp`, validates it, and only then activates it.

The base image should also contain the package database implementation used by
`pkg`, CA certificates, BusyBox/coreutils, grep, sed, awk, findutils, tar, gzip,
xz, unzip, OpenSSL, wget, nano, less, procps, diffutils, patch and make.

## Required build rules

- Set the package build system app name to `com.sikoclaw.app`.
- Set its prefix to `/data/data/com.sikoclaw.app/files/terminal/usr`.
- Use Android NDK/Bionic builds only.
- Reject files containing `/data/data/com.termux`, Debian/Ubuntu `/usr`, or an
  unexpected ELF architecture.
- Generate SHA-256 after the final archive is produced.
- Publish the bootstrap checksum separately and place its value in
  `SIKO_TERMINAL_BOOTSTRAP_SHA256` for the Android build.
- Serve the repository over HTTPS and set `SIKO_TERMINAL_REPOSITORY` to its root.

`pkg` is the stable public interface even if the repository later chooses a
patched apt/dpkg backend. A Linux distribution is installed only after the user
runs `pkg install proot` and explicitly chooses a distro.
