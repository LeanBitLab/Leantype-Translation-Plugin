# LeanType Translation Plugin

This is a dynamic plugin APK for **LeanType** keyboard that enables inline text translation support.

## How it works

LeanType keyboard is a free and open-source (FOSS) project licensed under GPLv3. To enable real-time inline text translation without adding extra overhead or external network dependencies directly into the main keyboard codebase, this plugin isolates the translation provider logic into a separate, dynamically loaded APK.

At runtime, LeanType loads this plugin dynamically via `DexClassLoader` when enabled by the user in settings.

## Building the APK

To build the APK, run the following Gradle task:

```bash
./gradlew assembleRelease
```

The compiled APK will be generated at:
`app/build/outputs/apk/release/translation_plugin.apk`

## Installation

1. Copy the built `translation_plugin.apk` to your Android device.
2. In LeanType Settings, go to **Libraries** > **Load translation plugin**.
3. Select the APK using the file picker.

## License

Licensed under the [GNU General Public License v3.0](LICENSE).
