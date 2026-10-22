# Hermes Android UI

A robust Android UI for the Hermes agent: a native Jetpack Compose app that runs the Hermes
controller front end on Android — sessions, model manager, connected providers, and local control
of a Hermes deployment.

## Status

Early, under active work. The app screens, gesture model and model manager are built; the backend
contract is still being settled. Expect paths or APIs to change between commits.

## Build

```sh
./gradlew assembleDebug
```

Debug builds are signed with the committed debug keystore (`app/debug.keystore`, debug use only).

## Legal

This repository is a modified version of [Hy4ri/hermes-mobile](https://github.com/Hy4ri/hermes-mobile),
used under the [Apache License 2.0](LICENSE). It is not the official app and is not affiliated with
its authors. Modified files are listed in [CHANGES.md](CHANGES.md).
