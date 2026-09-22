# Revisi tahap 3 — kamera dan antrean capture

Status: **implementasi, review, dan verifikasi lokal selesai; bukan persetujuan rollout produksi**. Branch `fix/capture-offline-stage-3`, base `251fce8e5bbf79addfe7e76d96fb75ec9efe8218` (PR #2 telah merge).

## Batas kontrak

- Pembiasaan dan checkout resmi tetap berada pada `attendance`. Foto presensi umum merupakan bukti tambahan/legacy, bukan pembuktian bahwa semua fase selesai. Tidak ada migrasi/backfill produksi.
- Antrean capture menyimpan jenis kegiatan dan metadata immutable, agar Larkam tidak berubah menjadi presensi umum saat offline. Hasil `Uploaded` berarti bukti remote terkonfirmasi; `QueuedOffline` bukan klaim absen tersinkron.
- Identitas pemilik harus tetap sama di titik antre/upload/simpan. Pembatalan coroutine diteruskan. ID capture tetap untuk retry, berbeda untuk capture baru.
- Rules versioned `capture_<UUID>` tetap append-only. Recovery membaca hasil commit yang identik, bukan mengizinkan update. Pengecekan dokumen belum ada dibatasi GET untuk akun siswa/class rep berprofil; tidak mengizinkan membaca isi dokumen milik siswa lain atau list tanpa filter.
- JPEG yang gagal didecode tidak boleh diganti dengan gambar buatan. Foto pending tidak boleh hanya bergantung pada cache yang dapat dievikt Android.

## Implementasi

- Kamera tidak lagi menggunakan fallback bitmap abu-abu. Decode JPEG, rotasi sensor dan watermark berjalan melalui adapter di luar ViewModel; callback ditutup pada sukses, gagal, sibuk, dan cancellation. Validasi akun/role, Jumat 06:30–08:30 WIB, lokasi/mock, dan perubahan akun dilakukan sebelum submit.
- Intent Larkam disimpan pada ViewModel pelacak yang terikat back stack, memakai route kamera terpisah dengan capture ID. Owner, hari sekolah, durasi yang mengecualikan waktu pause, jarak dan rute dibekukan setelah selesai. Kamera umum tidak membaca singleton Larkam. Intent yang hilang saat process death ditolak; pengguna harus memulai ulang sesi yang belum masuk outbox.
- Semua `submitCapture` menyimpan JPEG privat + Room sebelum jaringan. Worker memakai jenis capture yang tersimpan dan checkpoint URL. Error sementara dijadwalkan ulang; bukti terminal tetap terlihat dengan pesan perlu penanganan.
- Room 1→2 menambah field tanpa menghapus baris. Capture ID lama diturunkan deterministik; metadata Larkam yang tidak ada tidak dikarang. Foto lama tetap dibaca dari direktori cache jika belum dievikt.
- Konfirmasi Firestore menolak snapshot cached/pending-writes meskipun dibaca dengan `Source.SERVER`. Acknowledgement dibatasi waktunya, dan cancellation setelah INSERT tidak menghapus foto yang sudah memiliki baris.
- Kontrak lama `uploadOrQueue`/`submitPresensi` tetap tersedia untuk kompatibilitas, tetapi bukan jalur kamera produksi; caller baru harus memakai `CaptureSubmissionRepository`.

## Cloudinary

Transport upload selfie/presensi/izin divalidasi bersama: URL harus HTTPS dari `res.cloudinary.com` pada cloud yang dikonfigurasi; HTTP 408/429/5xx adalah error sementara; detail mentah response tidak ditampilkan sebagai error. Pembatalan membatalkan request.

Konfigurasi tetap dari `local.properties`, Gradle `-P`, atau environment. Cloud name/preset unsigned bersifat client-visible, tetapi bukan alasan menambahkan secret. **API secret tidak boleh masuk APK/repository.**

Pengujian transport menggunakan response fixture lokal melalui interceptor HTTP, tanpa mengirim foto siswa. Uji koneksi nyata sebelumnya membuktikan akses upload, bukan pengaturan privasi atau otorisasi pemilik media.

## Batas produksi / rollout

1. Perubahan rules lokal harus direview dan dideploy secara terpisah sebelum aplikasi baru didistribusikan. Belum ada deploy produksi dari pekerjaan ini. Rules lama menolak GET dokumen capture yang belum ada; UI harus menampilkan kegagalan dan menjaga antrean, bukan diam-diam kehilangan capture.
2. Foto unsigned Cloudinary yang dites masih memakai delivery URL publik. Restriksi ukuran/format/folder pada preset harus dikelola di console; bukti sensitif memerlukan desain akses media terlindungi. Firestore rules tidak melindungi URL Cloudinary. **Belum dinyatakan aman untuk foto siswa/surat izin produksi.**
3. Tidak ada transaksi atomik lintas Cloudinary dan Firestore. Checkpoint URL + ID dokumen mengurangi duplikasi retry; crash persis sesudah media diterima sebelum checkpoint tersimpan tetap bisa meninggalkan orphan. Tidak mengklaim exactly-once lintas layanan.
4. Receipt time server bukan bukti bahwa GPS/jam capture tidak dimanipulasi. Validasi client dan schema bukan pengganti backend attestation.
5. Metadata Larkam yang tidak pernah tersimpan pada antrean versi lama tidak bisa dipulihkan secara jujur. Migrasi wajib menjaga bukti lama tanpa menyintesis detail.
6. Tes lokal/emulator memakai fixture sintetis. Kamera/GPS OEM, process-kill dan jaringan di HP lapangan serta data geofence/rotasi sekolah memerlukan uji staging tersendiri.
7. Cleanup cancellation tidak menjadikan filesystem dan SQLite atomik. Process death persis di antara simpan/hapus file dan transaksi Room masih bisa meninggalkan file yatim; belum ada rekonsiliasi orphan saat startup.
8. Follow-up non-blocking review: hentikan tracking secara eksplisit saat sesi kedaluwarsa; perluas tes navigasi/recreation kamera, stream pending-write SDK nyata, dan pembatalan HTTP yang sedang berjalan. Timeout acknowledgement writer tidak sama dengan deadline keseluruhan upload media.

## Verifikasi

Hasil lokal untuk penyerahan tahap 3. Review independen kamera serta perbaikan outbox terakhir lolos; status CI remote harus diperiksa pada PR, bukan disimpulkan dari hasil lokal:

**Review independen kamera dan perbaikan konkurensi terakhir lolos:** worker sekarang memeriksa status terminal dari baris terbaru setelah memperoleh mutex, bukan hanya snapshot sebelum menunggu. Regression terkoordinasi mereproduksi penulisan kedua yang keliru (RED expected 1, actual 2); pemeriksaan status di dalam mutex membuatnya GREEN, sambil mempertahankan baris dan JPEG. Fix sebelumnya sudah dikonfirmasi reviewer: exception khusus menjaga retry Alice→Bob→Alice tanpa upload kedua; foto ulang Larkam memakai ID foto baru (terpisah dari ID handoff); stopwatch memakai sumber waktu monotonic independen dari demo phase clock. Masing-masing memiliki assertion RED→GREEN. Tiga temuan audit awal (pending-write overlay, cancellation sesudah INSERT, dan await tanpa deadline) juga memiliki fix/regression GREEN. Pengujian repository memakai seam deterministik, bukan manipulasi stream Firebase produksi.

Perbaikan rereview outbox: perubahan owner diklasifikasikan ulang juga saat GET/readback melempar exception; denial owner yang tidak berubah tetap terminal. Path foto dipertahankan sebelum handoff dispatcher bisa membuang return value; cleanup setelah DELETE memeriksa ulang referensi Room dalam `NonCancellable`. Assertion RED terverifikasi pada `s3-owner-exception-red2`, `s3-photo-handoff-red`, dan `s3-photo-delete-red`; full suite GREEN mencakup negative control DELETE gagal yang harus tetap menjaga foto.

- **261 unit tests**, tanpa failure/error/skipped (`s3-stale-status-full-unit`). Termasuk regression respons GPS terlambat setelah finish: assertion gagal pada `s3-larkam-cancel-red`, lalu full suite lulus setelah guard cancellation.
- **108 rules tests** dalam 5 suite, seluruhnya lulus di emulator proyek demo (`s3-reviewed-rules`). Tidak ada deploy produksi.
- **30 tes native per konfigurasi**: normal, font 1.5×, dan dark/font 1.5× (`s3-delivery-normal`, `s3-delivery-large`, `s3-delivery-dark-large`). Termasuk migrasi SQLite/Room 1→2, file foto sesudah cache eviction, status terminal dan retry, regresi riwayat/dashboard/report serta viewport sempit.
- Sesudah fix konkurensi terakhir, APK dibangun ulang dan seluruh **30 tes native pada masing-masing konfigurasi** kembali lulus; bukti PDF dari tiap run diverifikasi ulang. Unit regression baru memakai barrier coroutine tanpa sleep, bukan simulasi berdasarkan urutan panggilan berurutan.
- PDF native dibuka/render di Android dan diverifikasi parser: **35 baris, 3 halaman**, semua sel/lifecycle cocok pada ketiga konfigurasi. **5 tes verifier** termasuk negative controls lulus.
- **Lint tanpa error, 72 warning lama**, tidak ada issue baru setelah pembandingan ID/pesan/path terhadap artifact CI tahap 2. Dua warning lama dihapus.
- APK debug dan androidTest dibangun ulang (`s3-delivery-apks`); signature APK debug v2 valid. Build gabungan awal kehabisan metaspace, bukan regression Kotlin; run terpisah dengan batas metaspace 512 MiB lulus tanpa mengubah versi stack.

Pemeriksaan visual Home normal dan dark/font 1.5× berhasil. Teks/banner dark terbaca tanpa overlap; sisi bawah tombol checkout berada di batas crop tangkapan. Assertion interaksi native tetap terpisah dari bukti visual; ini bukan review manual lengkap seluruh navigasi.

Log RED/GREEN berada di `/root/.hermes/cache/scratch/friday-stm-build-review/logs/`, bukti sintetis di `/root/.hermes/cache/scratch/friday-stage3-evidence/`. CI tahap sebelumnya bukan bukti CI tahap ini.
