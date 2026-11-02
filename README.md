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

Butuh **JDK 21** (sama seperti CI). Emulator Firestore tidak jalan di Java 8.

Suite yang dijalankan `jest.config.mjs`: `larkam.rules.test.js` di root, plus
`tests/rules/*.test.js` (attendance read-model, security, transitions, capture
outbox). Port emulator `8085` dari `firebase.emulators.json` — kalau bentrok,
matikan dulu instance lain.

## Docs

- `docs/DEMO_GEOFENCE.md` — titik pusat, radius, dan batasan demo
- `docs/REVISI_TAHAP_*.md`, `docs/UI_REDESIGN.md` — riwayat perubahan
- `RELEASE.md` — catatan rilis
