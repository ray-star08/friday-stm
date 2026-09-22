# Tahap 2 — pembacaan kehadiran kompatibel

Basis: merge tahap 1 `72895e7b3f801141c4e1555accdd731a0dbfb3dd`.
Branch: `fix/attendance-read-model-stage-2`.

## Scope dan batas operasional

Tahap ini menyatukan **pembacaan** `attendance` dan `presensi_records`, bukan migrasi writer. Tidak ada perubahan versi stack, penghapusan data lama, deploy rules, akses data siswa produksi, atau konfigurasi Cloudinary produksi. Home dan penulis check-in/check-out tetap memakai kontrak tahap 1.

`AttendanceDay` adalah proyeksi kompatibilitas per **UID/tanggal sekolah**. Bukan dokumen Firestore baru dan tidak ditulis kembali. `AttendanceRecord` maupun stamp fase tidak boleh dipalsukan hanya agar selfie generik terlihat seperti check-in Jumat.

## Kontrak hasil baca

- **COMPLETE / Lengkap:** dokumen canonical menyatakan complete, pembiasaan checked-in/valid dan checkout checked-out.
- **PARTIAL / Sebagian:** pembiasaan valid atau Apel historis valid sudah tercatat, tetapi belum checkout.
- **NEEDS_REVIEW / Perlu ditinjau:** flagged, fase tidak valid, checkout tanpa check-in, status asing atau kombinasi status/fase yang tidak konsisten. Jangan ditampilkan sebagai alfa, izin, atau kehadiran lengkap.
- **LEGACY / Selfie lama:** hanya ada bukti generik `presensi_records`. Bukti tetap terlihat tetapi tidak memperoleh check-in, checkout, atau validasi buatan.

Canonical pada UID/tanggal sama menentukan lifecycle; selfie lama tidak boleh menghapus flagged atau menaikkan partial menjadi complete. Timestamp legacy lokal diperlakukan sebagai waktu sekolah; timestamp ber-offset dinormalisasi ke `Asia/Jakarta`. Satu UID/tanggal dihitung satu kali, memilih selfie lama paling awal secara deterministik jika terdapat duplikat. Identitas/tanggal rusak harus menghasilkan kegagalan data yang terlihat, bukan angka nol yang terlihat sah.

**Lengkap bukan verifikasi server atas kehadiran fisik.** Field `valid`, GPS dan jam tampilan belum menjadi attestation tepercaya. `serverTimestamp` membuktikan waktu penerimaan server saja. Jangan memakai keterlambatan atau angka ketidakhadiran sementara untuk sanksi.

## Konsistensi layar dan ekspor

- Riwayat menampilkan proyeksi gabungan beserta lifecycle serta bukti aslinya.
- Profil menghitung hari tercatat, bukan jumlah dokumen selfie. Angka izin di profil tetap jumlah **pengajuan**, berbeda dari izin disetujui pada rekap.
- Dashboard mempertahankan pembedaan lengkap, partial, legacy dan perlu tinjau. Total **Tercatat** bukan total **Lengkap**.
- Tanggal awal/akhir, label UI, nama file dan query memakai tanggal sekolah WIB; nilai tanggal UTC dari Material DatePicker dikonversi eksplisit.
- Jam canonical kosong/rusak dicatat sebagai **Jam tidak tersedia/tidak valid**, bukan dimasukkan kelompok sebelum 07:00. Hari tetap dihitung dalam lifecycle yang sesuai.
- Dialog hari campuran memperlihatkan bukti umum legacy beserta jam capture secara terpisah, tanpa mengisi jam atau bukti fase canonical.
- Ekspor menyertakan rincian lifecycle. Izin hanya APPROVED; kehadiran/record yang perlu ditinjau mendahului izin pada tanggal sama. Duplikat izin tidak menambah hari; konflik tipe izin/sakit diselesaikan secara deterministik dengan sakit didahulukan.
- Denominator masih tanggal yang teramati dalam data kelas, **bukan kalender akademik**. Label angka sisa adalah **Belum tercatat**, bukan alfa final. Kalender kegiatan, hari libur dan finalisasi sanksi belum ada.
- Kegagalan salah satu sumber tidak boleh menjadi sukses kosong. Riwayat/profil/reset akun tidak boleh memunculkan bukti akun sebelumnya; retry dashboard harus benar-benar membuka ulang stream.

## Query dan kompatibilitas deployment

Query siswa wajib dibatasi UID/userId di server; rules Firestore bukan filter. Staff membaca canonical dengan batas tanggal dan menyaring menggunakan roster kelas saat ini karena dokumen `attendance` lama tidak mempunyai snapshot kelas. Legacy dan izin dibaca melalui UID anggota kelas saat ini, bukan `studentClass`/`kelas` yang tersimpan pada bukti lama: siswa pindah masuk tetap membawa bukti historisnya, siswa pindah keluar tidak ikut. Larkam ekspor juga memakai query UID sebelum deserialisasi agar dokumen rusak kelas lain tidak mematikan ekspor. Fan-out saat ini satu query per UID; ini bukan snapshot transaksi atomik seluruh koleksi.

**Batas historis:** rekap mengikuti keanggotaan kelas saat ini, bukan keanggotaan pada waktu bukti dibuat. Tahap ini tidak mengarang histori perpindahan kelas. Data canonical dengan tanggal invalid mungkin tidak terambil oleh query rentang tanggal staff; inventaris/migrasi admin harus meninjau data historis secara terpisah sebelum pilot.

Ekspor satu kali perlu pembacaan server agar kegagalan jaringan tidak terlihat sebagai ekspor kosong sah. Listener dapat memakai cache Firestore; tampilan tersebut bukan tanda verifikasi online ataupun acknowledgment upload final.

## Verifikasi

Suite tambahan `tests/rules/attendance-read-model.test.js` menguji query kedua sumber: owner positif/negatif, anonymous, larangan query staff bagi siswa, query rentang tanggal staff + join roster, dan kedua sumber kosong sah. Ini menguji bentuk query/rules dengan JS SDK, **bukan pengganti uji serialisasi SDK Android/staging**.

Hasil lokal setelah perbaikan review pada 22 September 2026:

- 216 unit test: 0 failure, 0 error, 0 skipped. Termasuk loading saat perubahan roster, query UID sebelum deserialisasi, perpindahan kelas, cancellation, jam tidak tersedia, dan zona tanggal.
- 72 test rules dalam 4 suite: lulus pada emulator proyek `demo-friday-stm-test`.
- `testDebugUnitTest`, `lintDebug`, `assembleDebug`, dan `assembleDebugAndroidTest`: sukses. Lint 0 error / 74 warning baseline, tanpa warning baru.
- API 29 / 360dp: **26 instrumentation test** lulus pada normal, font 1,5×, dan dark + font 1,5×. Termasuk dialog/reset akun, lifecycle, error vs empty, viewport history pendek, mixed-source proof, Material date picker dan PDF unknown-time.
- PDF dibuat oleh Android asli, dibuka/render dengan `PdfRenderer`, kemudian dibaca ulang dengan parser independen: 35 siswa tepat sekali, 3 halaman, rincian lifecycle utuh pada seluruh baris. Tidak ada fallback teks yang menyamar sebagai PDF; kegagalan native diteruskan sebagai error.
- Pemeriksa PDF independen `tests/pdf/verify_native_pdf.py` mencocokkan semua sel dan rincian lifecycle ke manifest input; 5 negative control mendeteksi omission/duplikasi/perubahan data.
- Signature APK debug terverifikasi (v2). Screenshot memakai data sintetis dan hanya membuktikan content composable yang diuji, bukan login/backend produksi atau seluruh navigation shell.

Review independen ulang lulus: kelima blocker selesai, tidak ada security concern atau logic error baru. Saran non-blocking: tambah variasi delay izin/Larkam pada regression perubahan roster. Log, XML dan screenshot disimpan sebagai evidence di luar source tree. Status CI remote dicatat dalam PR; hasil lokal ini bukan klaim deployment/staging telah lulus.

## Sesudah tahap ini

1. Satukan jalur writer/selfie dan kebijakan fase/geofence dengan rencana compatibility rollout.
2. Durable outbox, idempotency event/upload dan penanganan commit berhasil tetapi acknowledgment hilang.
3. Kalender resmi, keanggotaan kelas historis dan kebijakan konflik/partial/offline yang disetujui sekolah.
4. Validasi server, scope assignment staff, signed/private media serta retensi foto.
5. Uji staging/perangkat nyata dan pilot terkontrol; migrasi hanya setelah dry-run, rekonsiliasi dan rollback disiapkan.
