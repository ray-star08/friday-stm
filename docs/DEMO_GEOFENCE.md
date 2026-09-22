# Geofence demo sekolah

Konfigurasi yang disetujui untuk demo SMK Negeri 1 Cimahi:

- Pusat: **-6.902144277968082, 107.53840454446247** (pin dari pemilik project).
- Radius: **180 meter** untuk Ta’lim, Senam, Larkam, dan validasi foto kamera.
- Ini lingkaran toleransi demo, **bukan batas pagar sekolah hasil survei**. Sebagian area di luar sekolah dapat masuk; luas sekolah saja tidak membuktikan semua sudut berada dalam radius.

## Sumber konfigurasi

- Kamera: `SCHOOL_LATITUDE`, `SCHOOL_LONGITUDE`, `MAX_RADIUS_METERS` di `util/Constants.kt`.
- Check-in kegiatan: dokumen `geofences` di Firestore; fase check-out tidak memakai target geofence kegiatan. Kamera tidak membaca konfigurasi ini secara dinamis, sehingga perubahan titik di kode memerlukan **build dan instal APK baru**.
- Seeder kegiatan aktif memakai konstanta kamera yang sama. Referensi Apel lama dipertahankan dan tidak digunakan oleh fase aktif.
- Dokumen database demo yang ditargetkan: `geofences/lapangan_utama` (`talim`), `geofences/senam`, `geofences/larkam`. Pertahankan ID/label/field lainnya; resolver memilih melalui `activity`, bukan asumsi ID.

**Jangan menjalankan `seedInitialData()` untuk pembaruan radius pada database yang sudah berisi data.** Seeder dapat menimpa rotasi/manual edits dan membuat dokumen Ta’lim tambahan dengan ID berbeda. Untuk migrasi demo ini, cadangkan dokumen yang ada, ubah hanya `radiusMeter` secara atomik dengan precondition `updateTime`, lalu baca ulang setiap target. Akun, role, rules, indeks, rotasi, dan riwayat tidak perlu diubah.

## Pengujian dan pemakaian

- Regression JVM memeriksa titik 175 m (diterima) dan 185 m (ditolak) di empat arah dari pin yang ditulis independen dari konstanta aplikasi; seed kegiatan aktif harus memberi keputusan yang sama.
- Tes instrumentasi tambahan menguji fungsi jarak Android asli di dalam/luar radius. Ini bukan simulasi izin lokasi atau pembuktian GPS fisik di sekolah.
- Setelah update APK, buka aplikasi saat online agar listener Firestore mendapat radius terbaru. Jangan hapus data aplikasi jika ada foto/antrean yang belum tersinkron.
- Gladi bersih dari beberapa titik nyata sekolah tetap diperlukan. Bila titik terjauh belum masuk, ukur jaraknya atau koreksi pusat, jangan langsung menaikkan radius tanpa batas.
- Batas hari/fase kegiatan, role, lokasi mock, autentikasi, dan kepemilikan evidence tetap berlaku. Geofence client bukan bukti anti-spoof GPS tingkat produksi.
