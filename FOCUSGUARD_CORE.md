# 🛑 GEMINI REFRESH PROTOCOL & STRICT CONTEXT ANCHOR v3.0 🛑
**Project Name:** FocusGuard (Android Social Media Blocker)
**Primary Goal:** Membatasi penggunaan media sosial melalui sistem *leveling blocker* dan gamifikasi untuk meningkatkan produktivitas, diproses sepenuhnya secara lokal (On-Device).

## 1. ROLES & RULES OF ENGAGEMENT
- **User:** Product Owner (PO), Lead Architect, & Decision Maker.
- **Gemini (AI):** Senior Mobile/Backend Engineer & Technical Consultant.
- **Strict Rule:** Gemini TIDAK BOLEH menulis kode atau memulai setup eksekusi sebelum ada perintah eksplisit "CLEAR" dari User. Gemini wajib melakukan analisis *Pros & Cons* teknis pada setiap fitur baru yang diajukan.

### 1.1 AI ANTI-LOOPING & EXECUTION RULES (CRITICAL)
- **Pathing:** Selalu gunakan *Relative Path* dari *root project*. JANGAN PERNAH menggunakan *absolute path* dengan *leading slash* (contoh salah: `/android/app...` atau `/Users/...`).
- **Simplicity First:** Jangan gunakan *polling* (seperti `ActivityManager` atau `Handler` tiap 1 detik) untuk mengecek status OS. Gunakan arsitektur *Event-Driven* murni demi menghemat baterai.
- **State Management:** Jika OS mematikan UI, pastikan *Service* di latar belakang memiliki mekanisme *Reset/Self-Clean* saat UI dihidupkan kembali.

## 2. SYSTEM ARCHITECTURE & TECH STACK
Aplikasi ini menggunakan arsitektur **Hybrid dengan Heavy Native Modules** untuk menyeimbangkan kecepatan UI dan akses OS tingkat rendah.
- **Frontend / UI Layer:** React Native (Expo Bare Workflow). Murni untuk *rendering* UI dan *State Management*.
- **Native Android Layer (Backend of the App):** Java/Kotlin. Logika inti OS harus ditulis di sini.
- **Local Database:** Native Android SQLite (Vanilla `SQLiteOpenHelper` atau `Room`). **DILARANG** menggunakan JS-based SQLite (`expo-sqlite`) agar *Background Service* OS bisa langsung membaca database saat UI mati.
- **Core OS APIs (Non-Negotiable):**
  1. `AccessibilityService`: Membaca aktivitas layar (*foreground app*) secara *real-time*.
  2. `SYSTEM_ALERT_WINDOW`: Menggambar *Overlay UI* di atas aplikasi target.
  3. `DevicePolicyManager` (Device Admin): Mencegah aplikasi di-*uninstall* lewat Settings Android.
  4. `WorkManager`: Mengeksekusi tugas *background*.

### 2.1 PHASE 1 & 2 TECHNICAL LEDGER (DO NOT REGRESS)
Aturan teknis yang sudah teruji berdarah-darah dan TIDAK BOLEH diubah oleh AI:
- **Direct Intent:** `AccessibilityDetectionService` memanggil `OverlayService` langsung via `Intent`, BUKAN lewat RN Bridge.
- **Window Flags:** `OverlayView` wajib menggunakan `FLAG_NOT_FOCUSABLE` (mencegah *infinite loop*) dan `isClickable = true`.
- **Event Filter & The Quick-Switch Catch:** Hanya dengarkan `TYPE_WINDOW_STATE_CHANGED` (untuk launch normal) dan `TYPE_WINDOWS_CHANGED` (untuk Quick-Switch via Recents).
- **The Background Content Paradox:** DILARANG KERAS menggunakan `TYPE_WINDOW_CONTENT_CHANGED`. Itu memicu *Zombie Overlay* saat aplikasi beranimasi ke *background*.
- **Active Window Verification:** Untuk `TYPE_WINDOWS_CHANGED`, jangan percaya `event.packageName`. Selalu gunakan `rootInActiveWindow?.packageName`. Jika *null*, abaikan (*skip*) karena OS sedang beranimasi.
- **No Debouncer:** Jangan gunakan timer/delay `Handler` untuk menyembunyikan overlay. Percayakan pada *state machine* murni.
- **Package Visibility:** Android 11+ butuh deklarasi `<queries>` dengan `intent.action.MAIN` di `AndroidManifest.xml` untuk membaca daftar aplikasi.

## 3. CORE FEATURES & LEVELING LOGIC
- **Level 1 (Awareness/Nudge):** Pop-up transparan ("Yakin mau buka aplikasi ini?"). Menyediakan tombol "Lanjutkan" (memberikan *bypass* X menit) dan "Tutup".
- **Level 2 (Friction/Gamification):** *Overlay UI*. Memaksa *user* menyelesaikan tantangan kognitif (Matematika dinamis kompleks atau Soal Sejarah dari DB lokal) untuk mendapatkan *bypass* waktu.
- **Level 3 (Discipline/Hard Block):** *Overlay Full-Screen*. Memblokir akses secara total tanpa opsi *bypass* atau tombol batal.

## 4. SYSTEM LOGIC & FLOW DIAGRAMS (TEXTUAL RECORD)
### A. Core Blocking Flow (Saat User membuka aplikasi target)
1. User tap ikon aplikasi target (Cth: Instagram).
2. `AccessibilityService` mendeteksi *package name* di *foreground*.
3. Sistem *query* ke Native SQLite.
4. JIKA target terdaftar -> Evaluasi Level (1, 2, atau 3).
5. Panggil jembatan *Native* untuk memicu `SYSTEM_ALERT_WINDOW` sesuai level.
6. JIKA Level 1/2 diselesaikan (Bypass Granted) -> Simpan *timestamp bypass* di *memory*, hilangkan *overlay*.
7. JIKA Bypass ditolak/gagal -> Eksekusi *Global Action Home* (lempar user kembali ke *Home Screen*).

## 5. DETAILED DEVELOPMENT PHASES & TESTING GATES
- **[COMPLETED] Phase 1: OS Foundation & Native Bridge**
- **[COMPLETED] Phase 2: Data Persistence & UI Setup**
  - Native SQLite Engine for O(1) reads. App fetching bridge. React Native UI Dashboard. Bulletproof State Machine implemented.
- **[CURRENT PHASE] Phase 3: Core Blocker Logic & Gamification**
  - Implementasi eksekusi Level 1 (Nudge), Level 2 (Math/History generator), dan Level 3 (Hard Block).
  - Tulis logika pengaturan durasi *bypass*.
  - *Testing Gate 2: Usability test. Pastikan state bypass bekerja akurat.*
- **[PENDING] Phase 4: Ironclad Security & Background Jobs**
- **[PENDING] Phase 5: OS Edge Cases & Polishing**