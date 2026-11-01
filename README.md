# friday-stm

Aplikasi absensi sekolah berbasis Android (Kotlin + Firebase) dengan validasi
lokasi (geofence) dan foto kamera untuk kegiatan Ta'lim, Senam, dan Larkam.

## Stack

- Kotlin, `minSdk 29`, `compileSdk 37`, package `com.gynda.fridaystm`
- Firebase Auth + Firestore; aturan keamanan di `firestore.rules`
- Konfigurasi Firebase (`app/google-services.json`) ikut di repo untuk demo

## Build

```bash
./gradlew assembleDebug
```

## Test aturan Firestore

Test rules jalan di Firebase Emulator, bukan project asli.

```bash
npm install   # Node >= 22
npm run test:rules
```

Suite ada di `larkam.rules.test.js`. Java diperlukan untuk emulator.

## Docs

- `docs/DEMO_GEOFENCE.md` — titik pusat, radius, dan batasan demo
- `docs/REVISI_TAHAP_*.md`, `docs/UI_REDESIGN.md` — riwayat perubahan
- `RELEASE.md` — catatan rilis
