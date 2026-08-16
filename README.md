# Files

A local-first Android app for photos and files. Nothing is uploaded unless you turn on optional Immich backup.

Launcher name is **Files**. Package is `dev.exau.photos` so existing installs keep updating; changing it would look like a different app.

## What it does

Two tabs:

**Photos** — albums from this phone (MediaStore), Liked, and Bin. Full-screen viewer with zoom, in-app video (no loop), crop / rotate / flip / filters / draw / resize. Edits save a **copy** under `Pictures/Files/`. Share strips GPS and other EXIF from photos.

**Files** — internal storage, SD / USB, and Samba shares. Copy, move, rename, zip / unzip, hidden files, sort, starred folders, storage scan, text editor, PIN lock. Samba passwords are stored in encrypted prefs when the device keystore works.

Immich is optional and sits under Settings. Backup is Wi‑Fi (or Ethernet) only, not on metered networks, and skips files already on the server. Opening a photo is not a share-to-Immich action.

## Requirements

- Android 8.0 (API 26) or newer
- All files access if you want the Files tab to browse the whole phone
- Same LAN as your NAS for Samba (SMB on port 445)
- Immich server only if you use backup

## Build

```bash
./gradlew assembleDebug
```

APK:

`app/build/outputs/apk/debug/app-debug.apk`

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Privacy

- No ads, no analytics, no Google Photos
- Photos and files stay on the device (and on your NAS if you add Samba)
- Immich is your server, not a cloud account in this app
- Samba secrets use EncryptedSharedPreferences when possible

## Current version

1.36
