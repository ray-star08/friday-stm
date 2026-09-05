# RELEASE.md — Prosedur Rilis Friday STM

Panduan operasional untuk melepas perubahan backend (Firestore Rules + reference
data). Urutan di bawah **wajib berurutan**: rules dulu, baru seed, baru APK.

| Artefak | Sumber | Cara rilis |
|---|---|---|
| Firestore Security Rules | `firestore.rules` | `firebase deploy --only firestore:rules` |
| Reference data (`geofences`, `rotations`) | `util/FirestoreSeeder.kt` | jalankan `seedInitialData()` sebagai user `admin` |
| Aplikasi | modul `app` | `./gradlew app:assembleRelease` |

Project Firebase: `friday-stm-ef55e` (lihat `app/google-services.json`).

---

## 1. Deploy Firestore Rules

Prasyarat sekali saja:

```bash
npm install -g firebase-tools
firebase login
firebase use friday-stm-ef55e
```

Repo ini belum punya `firebase.json`. Buat sekali di root (aman, hanya konfigurasi
path — tidak menyentuh data):

```json
{
  "firestore": {
    "rules": "firestore.rules"
  }
}
```

Lalu:

```bash
# 1. Lihat rules yang AKTIF sekarang — simpan sebagai cadangan sebelum menimpa.
firebase firestore:rules:get > firestore.rules.deployed.bak

# 2. Deploy.
firebase deploy --only firestore:rules
```

`deploy` **mengganti** rules yang aktif di project — tidak ada merge, tidak ada
undo otomatis. Cadangan di langkah 1 adalah satu-satunya jalan kembali. Jalankan
verifikasi §2 **sebelum** deploy ke project produksi.

Alternatif tanpa CLI: Firebase Console → Firestore Database → Rules → tempel isi
`firestore.rules` → **Publish**.

## 2. Verifikasi di Rules Playground

Console → Firestore Database → **Rules** → tab **Rules playground**. Untuk setiap
skenario: isi *Simulation type*, *Location* (path dokumen), *Authenticated* +
`uid`, lalu **Run**. Semua skenario di bawah **harus DENIED**.

Playground **tidak** menjalankan `get()`/`exists()` terhadap data nyata secara
sempurna untuk semua kasus — pastikan dokumen `users/{uid}` yang dipakai memang
ada dengan `role` yang sesuai, karena `role()` dan `myKelas()` membacanya.

### Skenario A — Siswa A menulis `attendance` milik Siswa B → DENIED

| Field | Nilai |
|---|---|
| Simulation type | `update` |
| Location | `/attendance/uidB_2026-09-11` |
| Authenticated | ✅, Firebase UID = `uidA` |

Payload:

```json
{ "uid": "uidB", "status": "complete" }
```

Ditolak oleh `resource.data.uid == uid()` pada `allow update` — dokumen milik
`uidB`, penelepon `uidA`. Coba juga varian `"uid": "uidA"` (mencoba membajak
dokumen dengan mengklaim kepemilikan): tetap DENIED, karena `resource.data.uid`
di dokumen tersimpan tetap `uidB`.

Kontrol positif (harus ALLOWED): `create` di `/attendance/uidA_2026-09-11`
sebagai `uidA` dengan payload `{ "uid": "uidA", "date": "2026-09-11", "updatedAt": <request.time> }`.

> Catatan Playground: field `updatedAt` tidak bisa diisi `serverTimestamp()` dari
> form. Pilih *timestamp* dan set nilainya **sama dengan** waktu simulasi, atau
> uji jalur ini dari perangkat sungguhan — aturan `hasServerTime()`
> (`request.resource.data.updatedAt == request.time`) hanya lolos untuk
> `FieldValue.serverTimestamp()` yang dikirim SDK.

### Skenario B — ID `talim_summaries` tidak sesuai `{date}_{kelas}` → DENIED

| Field | Nilai |
|---|---|
| Simulation type | `create` |
| Location | `/talim_summaries/rangkuman1` |
| Authenticated | ✅, UID = `uidRep` (profilnya `role: "class_rep"`, `kelas: "XI RPL 1"`) |

Payload:

```json
{
  "date": "2026-09-11",
  "kelas": "XI RPL 1",
  "submittedByUid": "uidRep",
  "penceramah": "Ustaz Fulan",
  "tema": "Kejujuran",
  "ringkasan": "..."
}
```

Ditolak oleh `docId == request.resource.data.date + '_' + request.resource.data.kelas`
— id `rangkuman1` bukan `2026-09-11_XI RPL 1`. Isi payload valid; **hanya id yang
salah**, itulah yang diuji.

Kontrol positif: ulangi dengan Location `/talim_summaries/2026-09-11_XI RPL 1`
→ ALLOWED.

### Skenario C — Siswa biasa menulis `senam_sessions` → DENIED

| Field | Nilai |
|---|---|
| Simulation type | `update` (atau `create`) |
| Location | `/senam_sessions/2026-W37` |
| Authenticated | ✅, UID = `uidSiswa` (profilnya `role: "student"`) |

Payload:

```json
{ "weekId": "2026-W37", "videoId": "dQw4w9WgXcQ", "setByUid": "uidSiswa" }
```

Ditolak: `allow create, update` hanya lolos untuk `isAdmin()` atau
`isInstructor()`, dan `role()` membaca `users/uidSiswa.role` = `student`.
`read` untuk uid yang sama tetap ALLOWED — video senam memang reference data.

### Ringkasan yang harus dicek

- [ ] A: `update` attendance milik orang lain → **Denied**
- [ ] A': `create` attendance sendiri dengan `updatedAt` = waktu server → **Allowed**
- [ ] B: id `talim_summaries` salah format → **Denied**
- [ ] B': id `{date}_{kelas}` benar, `class_rep` kelas sendiri → **Allowed**
- [ ] C: `student` menulis `senam_sessions` → **Denied**
- [ ] C': `student` membaca `senam_sessions` → **Allowed**
- [ ] `geofences` / `rotations`: `read` ALLOWED untuk siswa, `write` **Denied**
- [ ] `larkam_runs`: `update`/`delete` **Denied** untuk siapa pun (append-only)

Biaya: `role()` dan `myKelas()` masing-masing 1 document read per evaluasi. Ini
biaya, bukan bug — koleksi yang memakainya sengaja dibatasi.

## 3. Seed reference data

`geofences` dan `rotations` di-`write` hanya oleh `isAdmin()`, jadi seeder harus
dijalankan sambil login sebagai user yang dokumen `users/{uid}`-nya bernilai
`role: "admin"`.

```kotlin
// Panggil sekali dari entry point debug (tombol sementara / debug Activity),
// bukan dari alur pengguna.
val result = seedInitialData()   // util/FirestoreSeeder.kt
```

Isi yang ditulis — satu `batch.commit()`, atomik:

| Koleksi / doc id | label | activity | lat, lng | radiusMeter |
|---|---|---|---|---|
| `geofences/apel` | Lapangan Utama | `apel` | -6.87321, 107.54223 | 50 |
| `geofences/talim` | Masjid Al-Ikhlas | `talim` | -6.87350, 107.54210 | 40 |
| `geofences/larkam` | Area Lari Kampung | `larkam` | -6.87300, 107.54280 | 150 |
| `geofences/senam` | Lapangan Basket | `senam` | -6.87290, 107.54250 | 40 |
| `rotations/default_schedule` | — | mapping grade→activity (`weekOfYear: 0`) | — | — |

Idempoten: semua tulisan memakai `set()` ke id deterministik, jadi menjalankan
ulang memulihkan nilai baseline — **dan menimpa penyuntingan manual di console
pada field yang sama**. Sesuaikan koordinat di `SEED_GEOFENCES` bila titik
lapangan berubah, jangan sunting di console lalu lupa.

Verifikasi setelah seed: Console → Firestore → `geofences` berisi 4 dokumen,
`rotations/default_schedule` ada. Tanpa ini `HomeUiState.Ready.geofenceTarget`
selalu `null` dan tombol check-in tidak pernah bisa aktif.

### 3.1 Override rotasi untuk pekan spesial (opsional)

Rotasi normal dihitung dari rumus siklik di `domain/activityForGrade` — **tidak
perlu input mingguan**. Untuk libur atau pekan spesial, buat satu dokumen
`rotations/{weekId}` dengan id berformat ISO `yyyy-Www` (mis. `2026-W40`):

```json
{ "weekOfYear": 40, "mapping": { "10": "senam", "11": "senam", "12": "talim" } }
```

`HomeViewModel` memilih dengan urutan: `rotations/{weekId}` →
`rotations/default_schedule` → rumus siklik. Ini per-grade: grade yang tidak ada
di `mapping` (atau bernilai selain `talim` / `larkam` / `senam` — termasuk
`apel`) tetap memakai rumus, jadi override sebagian aman.

Hapus dokumen itu setelah pekannya lewat, atau tinggalkan — id-nya sudah
terikat pekan. Offline / `PERMISSION_DENIED` juga jatuh ke rumus, jadi tidak ada
skenario di mana siswa kehilangan kegiatan karena dokumen ini.

## 4. Urutan rilis

1. `firebase firestore:rules:get > firestore.rules.deployed.bak` — cadangkan.
2. Verifikasi 3 skenario §2 di Rules Playground.
3. `firebase deploy --only firestore:rules`.
4. Jalankan `seedInitialData()` sebagai admin, cek 5 dokumen di console.
5. `./gradlew app:testDebugUnitTest` — harus BUILD SUCCESSFUL.
6. `./gradlew app:assembleRelease`, lalu distribusikan APK/AAB.

Uji asap di perangkat sungguhan sesudahnya: hari Jumat 06:30–08:00, check-in
sekali sampai `attendance` terbentuk. Kalau `PERMISSION_DENIED` — langkah 3
belum jalan. Kalau tombol check-in tak pernah aktif — langkah 4 belum jalan.

## 5. Rollback

Rules: `firebase deploy --only firestore:rules` memakai file cadangan dari
langkah 1 (salin balik ke `firestore.rules` dulu). Reference data: `set()`
idempoten, jalankan ulang seeder. Dokumen `attendance` **tidak** bisa di-rollback
dari client — `allow delete: if false`; koreksi lewat console.


