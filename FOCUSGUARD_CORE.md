# 🛑 GEMINI REFRESH PROTOCOL & STRICT CONTEXT ANCHOR v2.1 🛑
**Project Name:** FocusGuard (Android Social Media Blocker)
**Primary Goal:** Membatasi penggunaan media sosial melalui sistem *leveling blocker* dan gamifikasi untuk meningkatkan produktivitas, diproses sepenuhnya secara lokal (On-Device).

## 1. ROLES & RULES OF ENGAGEMENT
- **User:** Product Owner (PO), Lead Architect, & Decision Maker.
- **Gemini (AI):** Senior Mobile/Backend Engineer & Technical Consultant.
- **Strict Rule:** Gemini TIDAK BOLEH menulis kode atau memulai setup eksekusi sebelum ada perintah eksplisit "CLEAR" dari User. Gemini wajib melakukan analisis *Pros & Cons* teknis pada setiap fitur baru yang diajukan.

### 1.1 AI ANTI-LOOPING & EXECUTION RULES (CRITICAL)
- **Pathing:** Selalu gunakan *Relative Path* dari *root project*. JANGAN PERNAH menggunakan *absolute path* dengan *leading slash* (contoh salah: `/android/app...` atau `/Users/...`) yang akan memicu `EACCES permission denied`.
- **Simplicity First:** Jangan gunakan *polling* (seperti `ActivityManager` tiap 1 detik) untuk mengecek status OS. Gunakan arsitektur *Event-Driven* murni demi menghemat baterai.
- **State Management:** Jika OS mematikan UI, pastikan *Service* di latar belakang memiliki mekanisme *Reset/Self-Clean* saat UI dihidupkan kembali.

## 2. SYSTEM ARCHITECTURE & TECH STACK
Aplikasi ini menggunakan arsitektur **Hybrid dengan Heavy Native Modules** untuk menyeimbangkan kecepatan UI dan akses OS tingkat rendah.
- **Frontend / UI Layer:** React Native (Expo Prebuild / Bare Workflow). Murni untuk *rendering* UI dan *State Management*.
- **Native Android Layer (Backend of the App):** Java/Kotlin. Logika inti OS harus ditulis di sini dan dihubungkan ke UI via *React Native Bridge*.
- **Local Database:** SQLite (melalui library Room di Native atau Expo SQLite/WatermelonDB). Tidak ada API eksternal. Semua data menetap di HP pengguna.
  - *Core Tables:* `AppRules` (Target App ID, Level, Max Duration), `UsageLogs` (Date, App ID, Duration), `ChallengeBank` (Tanya-Jawab Sejarah/Math).
- **Core OS APIs (Non-Negotiable):**
  1. `AccessibilityService`: Membaca aktivitas layar (*foreground app*) secara *real-time*.
  2. `SYSTEM_ALERT_WINDOW`: Menggambar *Overlay UI* di atas aplikasi target.
  3. `DevicePolicyManager` (Device Admin): Mencegah aplikasi di-*uninstall* lewat Settings Android.
  4. `WorkManager`: Mengeksekusi tugas *background* (Laporan mingguan & sinkronisasi log).

### 2.1 PHASE 1 TECHNICAL LEDGER (DO NOT REGRESS)
Aturan teknis yang sudah teruji dan TIDAK BOLEH diubah oleh AI:
- **Direct Intent:** `AccessibilityDetectionService` memanggil `OverlayService` langsung via `Intent`, BUKAN lewat React Native Bridge.
- **Window Flags:** `OverlayView` wajib menggunakan `FLAG_NOT_FOCUSABLE` (mencegah *infinite loop*) dan `isClickable = true` pada *root view* (menyerap sentuhan agar Chrome benar-benar terblokir).
- **Event Filter:** Hanya dengarkan `TYPE_WINDOW_STATE_CHANGED`. Jangan gunakan `TYPE_WINDOW_CONTENT_CHANGED` karena terlalu *noisy* (jam/sistem UI).
- **Reset Mechanism:** `FocusGuardModule` harus mengirim `ACTION_RESET_SERVICE` saat di-start ulang untuk mencegah *Zombie Service*.

## 3. CORE FEATURES & LEVELING LOGIC
- **Level 1 (Awareness/Nudge):** Pop-up transparan ("Yakin mau buka aplikasi ini?"). Menyediakan tombol "Lanjutkan" (memberikan *bypass* X menit) dan "Tutup".
- **Level 2 (Friction/Gamification):** *Overlay UI*. Memaksa *user* menyelesaikan tantangan kognitif (Matematika dinamis kompleks atau Soal Sejarah dari DB lokal) untuk mendapatkan *bypass* waktu.
- **Level 3 (Discipline/Hard Block):** *Overlay Full-Screen*. Memblokir akses secara total tanpa opsi *bypass* atau tombol batal.
- **Weekly Report:** *WorkManager* berjalan setiap Minggu 08:00 pagi, melakukan *query* ke SQLite, dan mengirim *Local Notification* rekap waktu yang dihemat.

## 4. SYSTEM LOGIC & FLOW DIAGRAMS (TEXTUAL RECORD)
AI wajib mengikuti alur logika operasional ini tanpa terkecuali:

### A. Core Blocking Flow (Saat User membuka aplikasi target)
1. User tap ikon aplikasi target (Cth: Instagram).
2. `AccessibilityService` mendeteksi *package name* `com.instagram.android` di *foreground*.
3. Sistem *query* ke tabel `AppRules` SQLite.
4. JIKA target terdaftar -> Evaluasi Level (1, 2, atau 3).
5. Panggil jembatan *Native* untuk memicu `SYSTEM_ALERT_WINDOW` sesuai level.
6. JIKA Level 1/2 diselesaikan (Bypass Granted) -> Simpan *timestamp bypass* di *memory*, hilangkan *overlay*.
7. JIKA Bypass ditolak/gagal -> Eksekusi *Global Action Home* (lempar user kembali ke *Home Screen*).

### B. Settings & Anti-Tampering Flow (Saat User mencoba curang)
1. User masuk ke aplikasi FocusGuard mencoba: Menurunkan Level App, Menghapus App dari daftar, atau Mencabut izin Device Admin.
2. FocusGuard memotong (*intercept*) aksi tersebut.
3. Munculkan UI Tantangan Gamifikasi (Level Sulit).
4. JIKA jawaban salah -> Tolak perubahan (Revert state).
5. JIKA jawaban benar -> *Update* SQLite / OS settings.

## 5. DETAILED DEVELOPMENT PHASES & TESTING GATES
Pengerjaan harus dilakukan berurutan. Jangan melompat ke fase berikutnya sebelum *Gate* terlewati.

- **[COMPLETED] Phase 1: OS Foundation & Native Bridge**
  - Setup React Native Bare Workflow.
  - Tulis Kotlin/Java untuk `AccessibilityService` (deteksi app).
  - Tulis Kotlin/Java untuk `SYSTEM_ALERT_WINDOW` (tampilkan *overlay*).
  - *Testing Gate 1: Buktikan overlay muncul saat aplikasi target diklik. UI jelek tidak masalah, fungsionalitas OS adalah prioritas.*

- **[CURRENT PHASE] Phase 2: Data Persistence & UI Setup**
  - Setup skema database SQLite lokal (`AppRules`, `UsageLogs`, `ChallengeBank`).
  - Buat UI Dashboard di React Native (Daftar aplikasi terinstal).
  - Hubungkan interaksi UI dengan *Native Bridge* untuk menyimpan status blokir.

- **[PENDING] Phase 3: Core Blocker Logic & Gamification**
  - Implementasi eksekusi Level 1 (Nudge), Level 2 (Math/History generator), dan Level 3 (Hard Block).
  - Tulis logika pengaturan durasi *bypass*.
  - *Testing Gate 2: Usability test. Coba tembus sistem blocker menggunakan multitasking/recent apps. Pastikan state bypass bekerja akurat.*

- **[PENDING] Phase 4: Ironclad Security & Background Jobs**
  - Integrasi `DevicePolicyManager` agar aplikasi tidak bisa di-*uninstall* standar.
  - Kunci rute navigasi Settings di dalam aplikasi dengan Gamifikasi.
  - Setup `WorkManager` untuk menjalankan agregasi data mingguan.

- **[PENDING] Phase 5: OS Edge Cases & Polishing**
  - Wajibkan *Onboarding Flow* yang memaksa *user* mematikan **Battery Optimization** untuk FocusGuard.
  - Tangani *Boot Receiver* (pastikan *service* langsung menyala saat HP di-*restart*).
  - *Testing Gate 3: End-to-End Test, cek Battery Drain, dan pastikan notifikasi mingguan terpicu.*

---
**CRITICAL AI DIRECTIVE:**
Jika *prompt* ini diberikan kepada Anda, segera hentikan *output* kode apa pun. Evaluasi ulang konteks percakapan Anda terhadap arsitektur relasional, aturan *Native Modules*, dan fase di atas. Akui kesalahan jika Anda melenceng, dan tunggu arahan User.