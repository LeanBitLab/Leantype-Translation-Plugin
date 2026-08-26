# LeanType Translation Plugin

A high-performance dynamic plugin for the [LeanType Keyboard](https://github.com/LeanBitLab/LeanType) enabling real-time **On-Device Offline ML Kit Translation**.

---

## ✨ Features

- **🛡️ On-Device Offline Translation (ML Kit)**: Zero-network on-device neural translation using lightweight (~30 MB) language models.
- **📦 On-Demand Model Management**: Download, manage, and delete 59+ language models directly from LeanType settings.
- **🔄 Universal Compatibility**: Fully supported across **all 4 LeanType flavors** (`Standard`, `Standard Full`, `Offline`, and `Offline Lite`).
- **🔌 Dynamic Isolated Loading**: Loaded dynamically via `PluginClassLoader` with isolated native libraries and zero runtime footprint when inactive.

---

> [!NOTE]
> Do **not** install this APK directly into Android OS as a standard app. It is a dynamic plugin library loaded internally by LeanType.

---

## 🛠️ How It Works

LeanType loads this plugin dynamically at runtime when translation is invoked. The plugin adheres to **LeanType Translation Interface v2**, providing synchronous and asynchronous translation bridges, model availability checks, and lifecycle management.

---

## 📥 Installation & Setup

### Option 1: In-App Downloader (Online Flavors)
1. In LeanType, open **Settings → Translation** (or **Settings → Plugins**).
2. Tap **Download Plugin** to automatically fetch and verify the latest release APK.
3. Tap **Offline Translation Models** to download desired on-device language packs.

### Option 2: Manual Loading (Offline & Offline Lite Flavors)
1. Download `translation_plugin.apk` from the [Latest Releases](https://github.com/LeanBitLab/Leantype-Translation-Plugin/releases/latest).
2. In LeanType, open **Settings → Translation** (or **Settings → Plugins**).
3. Tap **Load translation plugin** and select the `.apk` file.
4. Tap **Offline Translation Models** to download language models via browser or import models.

---

## 🏗️ Building From Source

Build the signed release APK with Gradle:

```bash
./gradlew :app:assembleRelease
```

The output APK will be generated at:
```
app/build/outputs/apk/release/translation_plugin.apk
```

---

## 📄 License

Licensed under the [GNU General Public License v3.0](LICENSE).
