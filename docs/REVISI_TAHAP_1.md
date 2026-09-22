# Revisi tahap 1 — keamanan dan baseline

Branch: `fix/attendance-hardening-stage-1`. Basis audit: `7008c655b85c66c752699d0f171c29e8a15e3416`.

## Batas perubahan

Tahap ini bukan rewrite, migrasi data, atau rilis produksi. Stack/versi Android tetap. CI hanya memverifikasi kode; **tidak ada deploy Firebase, signing release, upload data siswa, atau migrasi otomatis**.

### Kontrak Firestore yang diuji

- `presensi` dan `presensi_records`: siswa membaca hanya miliknya; guru/admin tetap dapat query rekap. Create wajib pemilik yang sedang login, role student/class_rep, nama/kelas sesuai profil sekolah, dan `createdAt` server timestamp. Update/delete bukti tetap ditolak.
- `users`: identitas sekolah diprovisikan admin. Siswa hanya dapat mengubah `photoUrl` dan `fcmToken`; bukan UID/NIS/nama/grade/kelas/role. Admin awal harus dibuat melalui kanal tepercaya (Admin SDK/console), bukan self-registration.
- `attendance`: dokumen baru harus mulai dengan pembiasaan, bukan checkout/complete. Checkout hanya melanjutkan check-in yang sudah tersimpan. UID/tanggal/bukti check-in tidak dapat ditimpa; completed/flagged tidak dapat ditulis ulang. Nested `serverTime` dan `updatedAt` wajib server timestamp. Dokumen legacy apel tetap terbaca dan bisa dilanjutkan ketika slot pembiasaan masih kosong.

### Perubahan Android yang sudah diuji lokal

- Tombol dan handler checkout menolak record yang belum check-in, fase/role yang salah, dan pengiriman ulang checkout. Tampilan legacy yang sudah checked-out tetap dikenali.
- Dashboard dan rekap hanya menghitung izin `APPROVED`. Presensi ganda siswa/hari dihitung sekali memakai timestamp valid paling awal, bukan urutan dokumen.
- Capture harus milik UID yang login. Drain offline melewati baris akun lain dan berhenti saat identitas berganti; identitas diperiksa lagi setelah upload sebelum menulis Firestore. Baris/foto akun lama dipertahankan. Banner menggunakan query jumlah per UID. Saat beranda dibuat atau pemilik login kembali dengan antrean miliknya, sinkronisasi dijadwalkan otomatis satu kali per sesi; bukan polling antrean akun lain. Auth-resume memakai APPEND_OR_REPLACE pada chain WorkManager yang sama agar tidak hilang di belakang upload lama; capture/manual tetap KEEP. Regresi diuji memakai database WorkManager asli in-memory, bukan hanya counter fake.
- Pengujian dashboard memakai dependency auth palsu, bukan Firebase nyata. Fixture lokasi valid memakai koordinat sekolah yang dikonfigurasi; batas radius produksi tidak dilonggarkan.
- Lint error pengambilan string resource di Compose diperbaiki tanpa suppression.

Verifikasi lokal gabungan: **167 unit test, 0 gagal/0 skip; lint 0 error dan 74 warning lama; assemble debug berhasil**. Tambahan UI diuji pada emulator API 29, 360dp: 10 test normal + 10 eksekusi ulang dark/font 1,5× lulus. CI remote, perangkat fisik dan integrasi staging adalah gerbang terpisah. Pemeriksaan akun bersifat titik-periksa, bukan transaksi atomik dengan logout; upload jaringan yang sudah berjalan tidak dapat ditarik kembali. Kebijakan cancellation dan idempotency penuh tetap tahap lanjutan.

### Kompatibilitas yang harus diperhatikan operator

1. Inventarisasi profil sekolah di staging sebelum menerapkan rules: `uid` harus cocok dengan path, role harus wire value `student|class_rep|instructor|admin`, dan nama/kelas/grade harus benar. Tidak ada pembuatan profil otomatis oleh siswa.
2. Antrean lama dengan nama/kelas yang sudah berbeda dari profil saat ini akan ditolak rules. Jangan mengganti pemilik, menghapus antrean, atau memalsukan profil agar upload lolos. Tinjau bukti dan lakukan rekonsiliasi terkontrol melalui tooling admin (belum dibuat pada tahap ini).
3. Check-in/checkout ganda ditolak untuk menjaga bukti; jangan menganggap retry write pada dokumen complete sebagai koreksi. Perbaikan data historis harus melalui tooling tepercaya dengan audit log.
4. `createdAt`/`serverTime` adalah waktu penerimaan server. **Bukan** bukti kebenaran waktu pengambilan foto atau GPS.

## Menjalankan verifikasi

Prasyarat: Java 21, Node 22, Android SDK platform 37 dan build-tools 36.0.0. Pakai Gradle wrapper yang sudah dipin dan `sh gradlew` (tidak perlu mengubah executable bit).

```sh
npm ci --ignore-scripts --no-audit --no-fund
npm run test:rules
sh gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --continue
```

`npm run test:rules` menggunakan `demo-friday-stm-test` dan `firebase.emulators.json`, bukan `.firebaserc` produksi. Semua fixture sintetis. Jangan mengganti project ID dengan proyek produksi. Suite Larkam lama tetap ikut berjalan, bersama regresi ownership, profil dan transisi attendance.

CI `.github/workflows/verify.yml` menjalankan Android dan emulator secara terpisah. Reports disimpan sebagai artifact 7 hari. Token hanya `contents: read`, actions dipin ke commit, dan tidak ada production secrets yang diperlukan. APK dari job ini tidak dipublikasikan sebagai rilis.

## Gerbang sebelum pilot (belum terverifikasi pada tahap ini)

- Staging Firebase terpisah, profil/rotasi/geofence berasal dari data sekolah yang sah.
- Cloudinary cloud name/upload preset dikonfigurasi lokal; bukan kredensial yang dikarang atau di-commit. Build tanpa konfigurasi upload bukan APK siap operasional.
- Uji perangkat: login, permission ditolak, lokasi nyata/mock, selfie, kehilangan jaringan, kill/restart, pergantian akun, antrean milik akun lama, dan notifikasi.
- Uji SDK Android terhadap rules staging, termasuk serialisasi `@ServerTimestamp` nested stamp dan penolakan operasi ilegal. Suite JS membuktikan kontrak payload/rules, bukan menggantikan uji perangkat tersebut.
- Persetujuan operator untuk deployment rules yang terkoordinasi dengan versi aplikasi. Jangan deploy rules otomatis dari PR ini.

## Pekerjaan tahap berikutnya

- Satukan sumber data `attendance` dan `presensi_records` beserta adapter/migrasi; tahap ini belum membuat dashboard membaca alur Jumat yang saat ini menulis koleksi berbeda.
- Idempotency key end-to-end, serialisasi worker/manual sync, retensi file foto yang tahan cleanup, retry classification/backoff, dan rekonsiliasi antrean lama.
- Kalender akademik/scheduled-day denominator dan kebijakan konflik izin vs hadir.
- Validasi tanggal/fase berdasarkan waktu tepercaya serta kebijakan offline terlambat; validasi payload lengkap dan bukti lokasi tetap perlu hardening.
- School/staff scoping yang lebih sempit, privacy/retensi foto di Cloudinary, tooling koreksi admin, staging dan pilot satu kelas.

Hasil lokal dan CI harus dibaca dari output eksekusi/PR, bukan disimpulkan dari keberadaan workflow atau checklist.
