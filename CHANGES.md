# Changes in this fork

This repository is a modified copy of [Hy4ri/hermes-mobile](https://github.com/Hy4ri/hermes-mobile)
(Apache License 2.0; first upstream commit `d6ff3de5`, 2026-06-16). It is not the official app and is
not affiliated with or endorsed by its authors. Official releases, the F-Droid listing and support
are upstream.

Under section 4(b) of the Apache License 2.0, this file states that the files below were modified
in this fork. All changes were made between 2026-10-17 and 2026-10-21 (commit messages
`batch-5A` to `batch-6`). `git log --author=anoscranel` lists every change.

Modified files:

- `.github/workflows/android.yml`
- `app/src/androidTest/java/com/m57/hermescontrol/ui/chat/components/ComposerInteractionTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/m57/hermescontrol/Navigation.kt`
- `app/src/main/java/com/m57/hermescontrol/data/remote/ProviderCatalogProbe.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/ChatScreen.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/ChatViewModel.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/MediaModelsViewModel.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/ModelManagerViewModel.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/ProbeViewModel.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/components/ChatComposer.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/components/ChatMediaLaunchers.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/components/ChatScrollController.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/components/ChatScrollFab.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/components/ComposerToolbar.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/components/ContextUsageChip.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/components/MediaModelsSheet.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/components/ModelManagerSheet.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/components/ProbeSheet.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/chat/components/SessionsSheet.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/common/HermesScaffold.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/model/components/ModelPickerDialog.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/sessions/SessionsScreen.kt`
- `app/src/main/java/com/m57/hermescontrol/ui/sessions/SessionsViewModel.kt`
- `app/src/main/res/values/strings.xml`

Summary of the changes: model-manager rework (provider collapse, per-key capability toggles,
probe sheet), sessions/history selection mode, composer layout fixes, camera and attachment
hardening, drawer and chat UI polish.
