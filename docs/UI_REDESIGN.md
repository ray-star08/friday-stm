# Friday STM — UI native v2

## Arah tampilan
- Identitas forest-green dengan permukaan netral terang, dark mode dengan token yang berpasangan; tanpa font jaringan/dependency baru.
- Login: identitas sekolah, heading, form outlined, satu tombol utama, teks bantuan admin. Scroll dan IME padding tetap tersedia.
- Beranda: salam/kelas, kartu fase vertikal, progres/check-out, lalu menu siswa sekunder (selfie, riwayat presensi, izin). Data dan callback lama dipertahankan.
- Riwayat: heading, kartu kronologi outlined, bukti/status dan dialog detail yang sama.
- Profil: identitas, statistik dari state, informasi akun, tombol keluar outlined dengan konfirmasi.
- Guru: header, filter, statistik, persetujuan/ekspor, daftar siswa berada dalam satu LazyColumn agar tetap terjangkau saat teks membesar.
- Bottom navigation menggunakan surface netral dan indikator Material 3.

## Bukti lokal
`RedesignUiTest` memakai state sintetis tanpa akun Firebase atau data siswa asli.
- API 29, layar 720×1280, density 320 (360dp lebar).
- 10 test lulus pada ukuran teks normal; 10 test lulus lagi pada font scale 1,5×.
- 10 test lulus lagi dengan layar utama dark/font 1,5×; login selalu diuji eksplisit light dan dark.
- Callback submit, check-out, menu izin guru, bottom navigation, logout dengan konfirmasi serta disabled form kosong diuji.
- Screenshot diambil dari Compose root dan diperiksa: login light/dark, beranda, riwayat, profil dan dashboard guru. Screenshot bukan seluruh aplikasi: status/navigation bar sistem tidak termasuk.
- Header riwayat sekarang berada dalam LazyColumn: regresi viewport 160dp/font 2× berhasil mereproduksi area list 0dp sebelum fix, lalu lulus setelah fix. Pada font besar sebagian konten berada di bawah viewport; scroll ke tombol dan logout diuji. Koordinat riwayat dapat membungkus ke baris kedua.
- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` lulus. Lint: 0 error, 74 warning baseline (tidak ditambah).

## Reproduksi
```sh
sh gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gynda.fridaystm.ui.RedesignUiTest
sh gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gynda.fridaystm.ui.RedesignUiTest \
  -Pandroid.testInstrumentationRunnerArguments.darkTheme=true \
  -Pandroid.testInstrumentationRunnerArguments.fontScale=1.5 \
  -Pandroid.testInstrumentationRunnerArguments.evidenceSuffix=dark-large
```
Screenshot tersimpan di external files aplikasi, subfolder `ui-evidence`.
Untuk mesin kecil, build APK saat emulator berhenti lalu install/jalankan instrumentation melalui adb. **Exit 0 adb tidak membuktikan test lulus**: periksa `OK (10 tests)`, bukan hanya exit shell.

## Batas
Belum mengganti seluruh layar pendukung (kamera/peta/form detail) atau memverifikasi semua ukuran layar, TalkBack, keyboard nyata dan perangkat fisik. Tidak mengubah kontrak koleksi Firebase, geofence, jadwal, kalkulasi statistik atau otorisasi. Build ini masih debug; login/upload produksi memerlukan konfigurasi dan uji staging sah. Jangan deploy rules atau menyebut APK siap operasional hanya karena UI tests lulus.
