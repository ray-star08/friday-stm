# Friday STM — Technical Task Board

> **Aplikasi:** Absensi disiplin siswa untuk kegiatan Jumat pagi di SMK Negeri 1 Cimahi.
> **Stack:** Kotlin · Jetpack Compose · MVVM · Firebase (Auth, Firestore) · Cloudinary (selfie) · CameraX · Play Services Location · osmdroid.
> **Build:** `compileSdk`/`targetSdk` 37 · `minSdk` 29 · JVM 11 · AGP 9.3.2 · Kotlin 2.2.10 · Compose BOM 2026.02.01 · Firebase BOM 34.1.0.
> **Vibe produk:** Keamanan absensi ala Talenta · interaksi visual ala Strava · UI ramah ala Ruangguru.

## Alur Inti (State-Driven UI — hari Jumat)

| Fase | Waktu | Kegiatan | Aksi UI |
|------|-------|----------|---------|
| **1** | 06:30–08:00 | Pembiasaan Paralel (Ta'lim / Larkam / Senam) | UI difilter oleh **rotasi mingguan per grade** → hanya tampil target geofence + tombol kegiatan yang sesuai, plus selfie |
| **2** | 08:00–08:30 | Check-out sebelum KBM | Tombol **Check-out** wajib |

> Sebelum 06:30 → `BEFORE` (empty state hitung mundur); di luar hari Jumat → `NOT_FRIDAY`; setelah 08:30 → `DONE`.
>
> **Fase Apel (06:00–06:30) sudah dihapus.** Alur Jumat kini langsung dibuka di Pembiasaan pukul 06:30. Sisa artefak Apel (`ApelStamp`, `SelfiePhase.APEL`, `ActivityType.APEL`, field `attendance.apel`) dipertahankan **read-only** supaya dokumen absensi lama tetap terbaca — tidak ada fase yang menulis Apel baru.

**Prioritas:** 🔴 High (blocker/MVP) · 🟡 Medium (fungsional inti) · 🟢 Low (polish/nice-to-have)

**Status project saat ini:** MVP alur inti jalan (lihat Kesimpulan di bawah). Sisa pekerjaan = hardening, anti-cheat, dan tooling admin/guru.

---

## Milestone 1 — Project Setup & Architecture

### 1.1 Dependency & Gradle
- [v] 🔴 Buat project Firebase di console, download `google-services.json` ke `app/`
- [v] 🔴 Tambah plugin `com.google.gms.google-services` di `settings.gradle.kts` (pluginManagement) & apply di `app/build.gradle.kts`
- [v] 🔴 Tambah `firebase-bom` + `firebase-auth-ktx`, `firebase-firestore-ktx` ke `libs.versions.toml` dan `app/build.gradle.kts` — **CATATAN:** `firebase-storage` **tidak dipakai** (dihapus); selfie disimpan di Cloudinary.
- [v] 🔴 Tambah `androidx.navigation:navigation-compose`
- [v] 🔴 Tambah `androidx.lifecycle:lifecycle-viewmodel-compose` + `lifecycle-runtime-compose` (collectAsStateWithLifecycle)
- [v] 🔴 Tambah `kotlinx-coroutines-play-services` (await() untuk Task Firebase)
- [v] 🟡 Tambah `com.google.android.gms:play-services-location` (geofencing)
- [v] 🟡 Tambah CameraX: `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`
- [v] 🟡 Tambah `io.coil-kt:coil-compose` (load foto dari Storage)
- [v] 🟢 Tambah library peta: `maps-compose` (Google Maps) **atau** `osmdroid` (gratis, tanpa API key) — pilih salah satu untuk Mini-map

### 1.2 Struktur Paket (MVVM)
- [v] 🔴 Buat struktur paket di `com.gynda.fridaystm`:
  - `data/` → `model/`, `repository/`, `remote/` (Firestore/Storage source)
  - `domain/` → logika murni (phase engine, rotasi) yang bisa di-unit-test tanpa Android
  - `ui/` → `screen/`, `component/`, `theme/`, `navigation/`
  - `viewmodel/`
  - `util/` → `LocationUtil`, `TimeProvider`, `Constants`
- [v] 🟡 Buat abstraksi `TimeProvider` (interface) agar jam bisa di-mock saat testing fase

### 1.3 Navigation & App Shell
- [v] 🔴 Buat `AppNavHost` (NavHost) dengan route: `splash`, `login`, `home`, `history`, `profile`
- [v] 🔴 Buat sealed class / object `Routes` untuk type-safe navigation
- [v] 🟡 Redup `MainActivity` — ganti `Greeting` jadi `FridayStmApp()` yang memanggil NavHost
- [v] 🟢 Bottom navigation bar (Home · Riwayat · Profil) ala Ruangguru

### 1.4 Theme
- [v] 🟡 Definisikan color palette + typography ramah (rounded, warna cerah) di `ui/theme/`
- [v] 🟢 Dukungan light/dark mode konsisten

---

## Milestone 2 — Firebase Firestore Schema

### 2.1 Desain Koleksi
- [ ] 🔴 `users/{uid}` — profil siswa
  ```json
  {
    "uid": "abc123",
    "nis": "2024001",
    "nama": "Budi",
    "grade": 11,               // 10 | 11 | 12
    "kelas": "XI RPL 1",
    "role": "student",         // student | class_rep | instructor | admin
    "photoUrl": "..."
  }
  ```
- [ ] 🔴 `geofences/{id}` — titik & radius pagar digital
  ```json
  {
    "id": "lapangan_utama",
    "label": "Lapangan Utama",
    "activity": "talim",       // talim | larkam | senam  ("apel" legacy, read-only)
    "lat": -6.87,
    "lng": 107.54,
    "radiusMeter": 40
  }
  ```
- [ ] 🔴 `rotations/{scheduleId}` — jadwal rotasi mingguan (mapping grade → activity per pekan)
  ```json
  {
    "weekOfYear": 32,          // atau pola cyclic index
    "mapping": { "10": "senam", "11": "talim", "12": "larkam" }
  }
  ```
  > **Catatan desain:** Bisa disimpan sebagai pola siklus tetap (3 kegiatan × rotasi) alih-alih per pekan, supaya tidak perlu input manual tiap minggu. Lihat Milestone 3.2.
- [ ] 🔴 `attendance/{uid}_{dateYYYYMMDD}` — 1 dokumen per siswa per hari, menampung pembiasaan + checkout
  ```json
  {
    "uid": "abc123",
    "date": "2026-08-14",
    "grade": 11,
    "pembiasaan": { "activity": "talim", "checkedIn": true, "time": "06:45", "lat": -6.87, "lng": 107.54, "selfieUrl": "...", "valid": true },
    "checkout":   { "checkedOut": true, "time": "07:58" },
    "status": "complete"       // incomplete | complete | flagged
  }
  ```
  > **Kenapa 1 dokumen/hari?** Query riwayat & rekap guru jadi murah, dan `docId` deterministik (`uid_date`) mencegah duplikat check-in.
  > **Legacy:** dokumen lama bisa punya field `apel` — tetap dibaca (`ApelStamp`), tidak pernah ditulis lagi.

### 2.2 Data Class Kotlin
- [v] 🔴 Buat `User`, `Geofence`, `RotationSchedule`, `AttendanceRecord` + sub-model (`PembiasaanStamp`, `CheckoutStamp`) di `data/model/` — `ApelStamp` dipertahankan read-only untuk dokumen lama.
- [v] 🟡 Anotasi `@get:PropertyName` bila nama field beda dengan Kotlin, sediakan constructor kosong untuk deserialisasi Firestore

### 2.3 Security Rules & Seed Data
- [v] 🔴 Tulis Firestore Security Rules: siswa hanya boleh read/write dokumen `attendance` miliknya sendiri; `geofences` & `rotations` read-only untuk siswa
- [v] 🔴 Rules untuk koleksi kegiatan: `talim_summaries` (class rep, kelas sendiri), `senam_sessions` (instructor set / semua baca), `larkam_runs` (append-only milik sendiri); `isStaff()` = `instructor | admin`
- [v] 🔴 Validasi anti-manipulasi jam di rules `attendance`: `hasServerTime()` → `request.resource.data.updatedAt == request.time`, wajib pada `create` & `update`
- [v] 🟡 Seed data awal: geofences (4 titik) + rotasi fallback — `util/FirestoreSeeder.kt` → `seedInitialData()` (satu `batch.commit()`, idempoten, butuh login `role: "admin"`); langkah operasional di [`RELEASE.md`](RELEASE.md) §3
- [v] 🟢 ~~Rules Storage~~ — **tidak berlaku:** selfie di Cloudinary (unsigned preset), bukan Firebase Storage; `storage.rules` dihapus.

---

## Milestone 3 — Core Logic Implementation

### 3.1 Phase Engine (StateFlow)
- [v] 🔴 Buat `enum class FridayPhase { NOT_FRIDAY, BEFORE, PEMBIASAAN, CHECKOUT, DONE }`
- [v] 🔴 Fungsi murni `resolvePhase(now: LocalDateTime): FridayPhase` di `domain/` (unit-testable):
  - Bukan Jumat → `NOT_FRIDAY`
  - < 06:30 → `BEFORE`; 06:30–08:00 → `PEMBIASAAN`; 08:00–08:30 → `CHECKOUT`; ≥ 08:30 → `DONE`
- [v] 🔴 Di ViewModel: expose `StateFlow<FridayPhase>` yang auto-update. Pakai `flow { while(true){ emit(resolvePhase(now)); delay(...) } }` atau tick per menit, lalu `stateIn(viewModelScope)`
- [v] 🟡 UI meng-collect via `collectAsStateWithLifecycle()` → transisi `BEFORE` → `PEMBIASAAN` di 06:30 terjadi otomatis tanpa refresh manual
- [v] 🟢 Handle edge case: waktu device diubah user → validasi silang dengan `FieldValue.serverTimestamp()` saat menulis absen — field `updatedAt` di-stamp server pada tiap tulisan `attendance`, dan rules menolak nilai yang bukan `request.time`. Field `time` (`"HH:mm"`) tinggal teks tampilan, bukan bukti.

### 3.2 Logika Rotasi Mingguan
- [v] 🔴 Fungsi murni `activityForGrade(grade: Int, weekIndex: Int): Activity` di `domain/`:
  - 3 kegiatan (talim, larkam, senam) dirotasi siklik antar 3 grade
  - `weekIndex = weekOfYear % 3`; mapping = `(gradeSlot + weekIndex) % 3`
- [v] 🔴 Hitung `weekOfYear` dari tanggal (ISO week) via `TimeProvider`
- [v] 🟡 Fallback: bila ada dokumen `rotations` override di Firestore untuk pekan itu, pakai itu; kalau tidak, pakai rumus siklik — `RotationRepository.observeActiveSchedule(weekId)` membaca `rotations/{weekId}` → `rotations/default_schedule` → `null`; `HomeViewModel.activeActivityFor()` memakai `mapping[grade]` bila ada dan valid, selain itu `activityForGrade`. Dokumen hilang / offline / `PERMISSION_DENIED` / nilai rusak semuanya jatuh ke rumus (tak pernah `null`).
- [v] 🟡 Unit test tabel rotasi: pastikan tiap grade dapat 3 kegiatan berbeda dalam 3 pekan, tidak ada bentrok

### 3.3 ViewModel & Repository
- [v] 🔴 `HomeViewModel` menggabungkan `phase` + `user.grade` + `rotation` → `HomeUiState` (sealed/data class) yang menentukan tombol & geofence target aktif
- [v] 🔴 `AttendanceRepository`: `submitPembiasaan()`, `submitCheckout()`, `observeTodayRecord()`, `observeHistory()` (coroutines + Firestore snapshot listener) — `submitApel()` dihapus bersama fase Apel.
- [v] 🟡 `AuthRepository`: login (email/NIS), ambil profil user, sign out
- [v] 🟢 State loading/error/success terbungkus `Result` atau sealed `UiState`
  > **Catatan implementasi:** repositori dibuat sebagai *interface* (Firebase-backed impl) agar `HomeViewModel` bisa di-unit-test dengan fake. Ticking fase via `flow{}`+`delay`+`distinctUntilChanged`, digabung dengan profil & record lewat `combine`+`stateIn(WhileSubscribed)`. Gating tombol oleh radius geofence & selfie di Milestone 4.

---

## Milestone 4 — Geofencing & CameraX Integration ("Pagar Digital")

### 4.1 Permissions
- [v] 🔴 Tambah di `AndroidManifest.xml`: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `CAMERA`, `INTERNET`
- [x] 🔴 Runtime permission flow via `rememberLauncherForActivityResult` untuk lokasi + kamera sebelum check-in — lokasi di `HomeScreen.kt` (`RequestMultiplePermissions`), kamera di `CameraCaptureScreen.kt`.
- [x] 🟡 Handle "denied permanently" → arahkan ke Settings — `locationPermanentlyDenied` + `openAppSettings()` di `HomeScreen.kt`.

### 4.2 Validasi Geofence
- [v] 🔴 `LocationUtil.getCurrentLocation()` via `FusedLocationProviderClient` (high accuracy, coroutine `await()`)
- [v] 🔴 `isInsideGeofence(current, target, radius)` pakai `Location.distanceBetween()` (Haversine bawaan) → bandingkan dengan `radiusMeter`
- [v] 🔴 Tombol check-in **disabled** sampai user berada di dalam radius geofence yang sesuai fase & rotasi
- [v] 🟡 Tampilkan jarak realtime ke titik target ("Anda 25 m dari Lapangan Senam")
- [v] 🟢 Cek `isMockLocationEnabled` / `location.isFromMockProvider` untuk anti-fake-GPS — fix mock ditandai `LocationFix.isMock`; `HomeViewModel` mengunci layar ke `HomeUiState.Error(R.string.location_mock_detected)` (tanpa tombol aksi sama sekali) dan membuang `lastFix`, jadi selfie yang sedang berjalan pun tidak bisa menulis koordinat palsu.

### 4.3 CameraX Selfie
- [v] 🔴 `CameraPreview` composable (`PreviewView` via `AndroidView`) default kamera depan
- [v] 🔴 `ImageCapture` → simpan bitmap/temp file saat tombol capture ditekan
- [v] 🔴 Upload selfie ke **Cloudinary unsigned preset** (`selfies/{uid}/{date}_{phase}`) via OkHttp, ambil `secure_url`, simpan ke dokumen attendance — **CATATAN:** memakai Cloudinary (bukan Firebase Storage) sesuai kebijakan storage provider.
- [v] 🟡 Kompres foto sebelum upload (JPEG q70, hemat kuota)
- [ ] 🟢 Overlay watermark timestamp + lokasi pada foto (bukti kuat ala Talenta)

---

## Milestone 5 — UI/UX Compose

### 5.1 Komponen Reusable
- [v] 🔴 `BigActionButton` — tombol besar penuh warna (Check-in Kegiatan / Check-out), punya state: enabled, disabled (di luar geofence), loading, done
- [v] 🔴 `DynamicPhaseCard` — kartu yang berubah isi & warna mengikuti `FridayPhase` + kegiatan rotasi aktif
- [v] 🟡 `MiniMap` — peta kecil menampilkan posisi user + lingkaran radius geofence target — **CATATAN:** osmdroid (gratis, tanpa API key); `GeofenceMiniMap` + Circle overlay + marker realtime + tombol recenter.
- [v] 🟡 `StatusStepper` / progress indikator Pembiasaan → Checkout (ala Strava activity)
- [v] 🟢 `SelfiePreviewCard`, `CountdownTimer` (hitung mundur ke batas fase) — selfie card (AsyncImage + badge status + jam) & countdown tick per detik ke deadline fase.

### 5.2 Screens
- [v] 🔴 `LoginScreen` — auth NIS/email — **CATATAN:** email/password aktif; NIS→email lookup ditunda (butuh field `nis` queryable).
- [v] 🔴 `HomeScreen` — konsumsi `HomeUiState`, render kartu + tombol sesuai fase & rotasi
- [v] 🔴 `CameraCaptureScreen` — flow selfie sebelum submit
- [v] 🟡 `HistoryScreen` — riwayat absen (query koleksi attendance milik user)
- [v] 🟢 `ProfileScreen` — data siswa + logout

### 5.3 Polish & UX States
- [v] 🟡 Empty state (bukan hari Jumat / di luar jam kegiatan) dengan ilustrasi ramah — `DynamicPhaseCard` NOT_FRIDAY/BEFORE/DONE.
- [v] 🟡 Loading & error state di tiap aksi jaringan — `HomeUiState.Loading/Error` + `SubmitStatus`, Crossfade transisi.
- [v] 🟢 Animasi transisi antar fase, haptic feedback saat check-in sukses — `Crossfade` antar state Home. **⏳ SEBAGIAN:** haptic feedback menyusul.
- [v] 🟢 Accessibility: contentDescription, kontras warna memadai — `cd_*` string di kamera/map/selfie/countdown; palette M3 semantic.

---

## Cross-Cutting (jangan dilupakan)
- [ ] 🟡 Panel/tools admin untuk atur `geofences` & `rotations` (bisa manual via console dulu)
- [v] 🟡 Unit test untuk `domain/` (phase engine + rotasi) — logika paling kritikal, harus 100% teruji
- [ ] 🟢 Analytics/logging kejadian flagged (siswa di luar geofence, telat, tidak checkout)
- [ ] 🟢 Dashboard rekap untuk guru/wali kelas

---

## Kesimpulan & Pengembangan Selanjutnya

**Status:** MVP alur inti selesai — phase engine, rotasi, geofence gating, CameraX + upload Cloudinary, semua screen, dan unit test `domain/` sudah jalan. Yang tersisa adalah *hardening*, *anti-cheat*, dan *tooling admin/guru*.

### 🔴 Blocker sebelum dipakai lapangan
1. **Deploy Firestore rules terbaru** (2.3) — rules `talim_summaries`/`senam_sessions`/`larkam_runs` + `hasServerTime()` baru ditambahkan; tanpa deploy, ketiga fitur itu ditolak `PERMISSION_DENIED`. Langkah + 3 skenario Rules Playground: [`RELEASE.md`](RELEASE.md) §1–§2.
2. **Jalankan seeder** (2.3) — `seedInitialData()` sebagai user `admin`; tanpa `geofences` `HomeUiState` tidak punya target dan tombol check-in tak pernah aktif. Lihat [`RELEASE.md`](RELEASE.md) §3.

### 🟡 Integritas data (anti-manipulasi)
3. ~~**Override rotasi dari Firestore** (3.2)~~ — **selesai:** `RotationRepository` (`rotations/{weekId}` → `default_schedule` → rumus siklik) tersambung ke `HomeViewModel`. Untuk pekan spesial/libur, admin cukup membuat dokumen `rotations/2026-W40` berisi `mapping` grade→activity; tanpa dokumen apa pun, rumus siklik tetap jalan (termasuk saat offline).

### 🟢 Polish
- Watermark timestamp+lokasi pada selfie (4.3) — bukti lebih kuat.
- Haptic feedback saat check-in sukses (5.3).

### 🛠 Tahap berikutnya (sisi guru/admin — proyek terpisah)
- Panel admin atur `geofences` & `rotations` (mulai dari Firebase console).
- Analytics kejadian `flagged` (di luar geofence / telat / tak checkout).
- Dashboard rekap wali kelas.

**Urutan disarankan:** deploy rules → seed data → polish. Prosedur lengkap: [`RELEASE.md`](RELEASE.md) §4.

> ⚠️ Firestore Security Rules (2.3) mencakup 7 koleksi (`users`, `geofences`, `rotations`, `attendance`, `talim_summaries`, `senam_sessions`, `larkam_runs`) dengan default-deny untuk sisanya. **Verifikasi di Rules Playground sebelum production** ([`RELEASE.md`](RELEASE.md) §2): siswa tak bisa menulis `attendance` milik orang lain, id `talim_summaries` wajib `{date}_{kelas}`, `senam_sessions` hanya instructor/admin, `geofences`/`rotations` read-only. `role()`/`myKelas()` menambah 1–2 document read per evaluasi (biaya, bukan bug).
