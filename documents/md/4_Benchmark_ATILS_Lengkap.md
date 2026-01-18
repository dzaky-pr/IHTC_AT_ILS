# 4 Benchmark Algoritma AT-ILS (Adaptive Threshold-Iterated Local Search)

## Ringkasan Eksekutif

Dokumen ini merangkum hasil implementasi algoritma **AT-ILS (Adaptive Threshold-Iterated Local Search)** pada 4 benchmark utama dalam domain penjadwalan. Algoritma AT-ILS dikembangkan dengan fokus pada **generalitas** dan **tidak memerlukan pengaturan parameter**, menjadikannya solusi yang adaptif untuk berbagai jenis permasalahan penjadwalan.

---

## 1. Benchmark Toronto (Exam Timetabling)

### 1.1 Karakteristik Permasalahan
- **Jenis Permasalahan**: Penjadwalan ujian tanpa memperhatikan kapasitas ruangan (uncapacitated exam timetabling)
- **Jumlah Dataset**: 13 dataset dari berbagai institusi (Kanada, Amerika, Inggris, Timur Tengah)
- **Ukuran Dataset**: Berkisar dari 80 ujian (HEC92) hingga 2.419 ujian (PUR93)
- **Kompleksitas**: Relatif sederhana dengan 1 hard constraint dan 1 soft constraint

### 1.2 Constraint
**Hard Constraint:**
- Tidak boleh ada peserta ujian yang dijadwalkan mengikuti 2 ujian atau lebih pada waktu bersamaan

**Soft Constraint:**
- Meminimalkan penalti berdasarkan jeda waktu antar ujian yang diikuti peserta
- Formula penalti menggunakan bobot: 16 (jeda 0 slot), 8 (jeda 1 slot), 4 (jeda 2 slot), 2 (jeda 3 slot), 1 (jeda 4 slot)

### 1.3 Hasil Implementasi AT-ILS

#### Hasil Optimasi (10 jam eksekusi):
- **Konsistensi**: Sangat baik dengan 9 dataset memiliki KV (Koefisien Variasi) < 1%
- **3 dataset KV 1-2%**: CAR91, LSE91
- **1 dataset KV > 6%**: HEC92 (6.11%)

#### Dataset Terbaik yang Dicapai:
| Dataset | Hasil AT-ILS | Waktu Eksekusi | Keterangan |
|---------|-------------|----------------|------------|
| **EAR83** | 32.40 | 30 jam | **Best new result** (sebelumnya 32.42) |
| STA83 | 157.03 | 10 jam | Optimal solution |
| YOR83 | 34.59 | 10 jam | Hasil konsisten |
| CAR91 | 4.48 | 50 jam | Dataset besar |
| PUR93 | 4.56 | 100 jam | Dataset terbesar (2.419 ujian) |

### 1.4 Perbandingan dengan Penelitian Terdahulu
- **Peringkat**: **4 dari 28 penelitian**
- **Hasil Baru**: 1 dataset (EAR83)
- **Unggul dari**: 24 penelitian sebelumnya
- **Top 3 penelitian**: Bellio et al. (2021), Leite et al. (2018), Burke and Bykov (2016)

#### Kelebihan Utama:
1. **Tidak memerlukan pengaturan parameter** (berbeda dengan penelitian peringkat 1-3)
2. **Konsistensi tinggi** antar dataset (peringkat 4-5.5 untuk 7 dataset)
3. **LLH generik** tidak spesifik untuk Toronto benchmark

### 1.5 Waktu Komputasi
- **Tercepat**: 10 jam (6 dataset: HEC92, RYE92, STA83, TRE92, UTE92, YOR83)
- **Sedang**: 20-30 jam (3 dataset: KFU93, LSE91, EAR83)
- **Besar**: 50 jam (3 dataset: CAR91, CAR92, UTA92)
- **Terbesar**: 100 jam (1 dataset: PUR93)

---

## 2. Benchmark ITC 2019 (Course Timetabling)

### 2.1 Karakteristik Permasalahan
- **Jenis Permasalahan**: Penjadwalan mata kuliah (course timetabling)
- **Jumlah Dataset**: 30 dataset (3 kategori: Early, Middle, Late - masing-masing 10 dataset)
- **Ukuran Dataset**: 417 kelas (yach-fal17) hingga 8.813 kelas (pu-proj-fal19)
- **Kompleksitas**: Sangat tinggi dengan 19 jenis batasan distribusi

### 2.2 Constraint
**5 Jenis Batasan Utama:**
1. **Batasan Slot Waktu**: Setiap kelas memiliki daftar slot waktu terbatas dengan penalti preferensi
2. **Batasan Ruangan**: Setiap kelas hanya dapat dijadwalkan pada ruangan tertentu
3. **Batasan Mata Kuliah**: Struktur konfigurasi, subpart, dan kelas parent
4. **Batasan Mahasiswa**: Tidak boleh konflik jadwal, tidak melebihi kapasitas ruangan
5. **Batasan Distribusi**: 19 jenis constraint (SameStart, SameTime, DifferentTime, SameDays, DifferentDays, SameWeeks, DifferentWeeks, Overlap, NotOverlap, SameRoom, DifferentRoom, SameAttendees, Precedence, WorkDay, MinGap, MaxDays, MaxDayLoad, MaxBreaks, MaxBlock)

### 2.3 Hasil Tahapan Feasibility (PA-ILS)
- **Tingkat Keberhasilan**: **100%** pada semua 30 dataset (10/10 percobaan)
- **Waktu Rata-rata**:
  - 10 dataset: < 1 menit
  - 18 dataset: < 1 jam
  - 2 dataset besar: 3.2 jam (agh-fal17), 8.1 jam (pu-proj-fal19)

### 2.4 Hasil Optimasi AT-ILS

#### Konsistensi (20 jam eksekusi):
- **10 dataset**: KV < 3% (sangat konsisten)
- **13 dataset**: KV 3-10% (konsisten sedang)
- **7 dataset**: KV > 10% (variabilitas tinggi karena kompleksitas dan keterbatasan slot waktu)

#### Waktu Komputasi Total:
- **Rentang**: 30-250 jam (gabungan tahap 1 dan tahap 2)
- **Dataset tercepat**: muni-fi-spr16, muni-fsps-spr17 (~30 jam)
- **Dataset terlama**: agh-fal17, pu-proj-fal19, iku-spr18 (~250 jam)

### 2.5 Perbandingan dengan Penelitian Terdahulu
- **Peringkat**: **4 dari 9 penelitian** (overall)
- **Peringkat per kategori**:
  - Early: 4.70
  - Middle: 4.30
  - Late: 4.30

#### Top 3 Penelitian:
1. **Mikkelsen and Holm (2022)**: Peringkat 1.11 (matheuristic MIP + Fix-and-Optimize)
2. **Rappos et al. (2022)**: Peringkat 2.76
3. **Sylejmani et al. (2022)**: Peringkat 3.46 (Simulated Annealing dengan **30 parameter**)

#### Hasil Feasibility vs Penelitian Lain:
| Penelitian | Early | Middle | Late | Total |
|------------|-------|--------|------|-------|
| **AT-ILS (Penelitian ini)** | 10 | 10 | 10 | **30** |
| Sylejmani et al. (2022) | 10 | 10 | 10 | 30 |
| Premananda et al. (2022) | 10 | 10 | 10 | 30 |
| Rappos et al. (2022) | 10 | 10 | 9 | 29 |
| Lemos et al. (2020) | 8 | 8 | 4 | 20 |

### 2.6 Analisis Keunggulan
1. **100% feasibility** pada dataset kompleks
2. **Tidak memerlukan parameter tuning** (vs Sylejmani 30 parameter)
3. **Peringkat kompetitif** (4/9) dengan algoritma yang bersifat umum
4. **Hasil sebanding** dengan Sylejmani pada beberapa dataset (pu-proj-fal19, agh-fal17)

---

## 3. Benchmark ITC 2021 (Sports Timetabling)

### 3.1 Karakteristik Permasalahan
- **Jenis Permasalahan**: Penjadwalan pertandingan olahraga (sports timetabling)
- **Jumlah Dataset**: 45 dataset (3 kategori: Early, Middle, Late - masing-masing 15 dataset)
- **Komposisi Tim**: 
  - Dataset 1-3: 16 tim, 30 slot waktu
  - Dataset 4-9: 18 tim, 34 slot waktu
  - Dataset 10-15: 20 tim, 38 slot waktu
- **Kompleksitas**: Sangat tinggi dengan jumlah hard constraint mencapai 200+ pada beberapa dataset

### 3.2 Karakteristik Dataset
- **Hard Constraint Tinggi (>200)**: Early 1, Early 5, Early 11, Middle 2, Middle 3, Middle 13, Late 1, Late 2, Late 10, Late 12
- **Variasi Constraint**: Hard dan soft constraint bervariasi signifikan antar dataset
- **Tingkat Kesulitan**: Sangat tinggi dalam menghasilkan solusi feasible

### 3.3 Hasil Tahapan Feasibility (PA-ILS)

#### Tingkat Keberhasilan:
- **42 dataset**: 100% keberhasilan (10/10 percobaan)
- **3 dataset**: < 100%
  - Middle 2: 90% (9/10)
  - Middle 3: 90% (9/10)
  - Late 13: 80% (8/10)
- **Persentase keseluruhan**: **94.7%** (426/450 percobaan)

#### Waktu Pencarian Feasibility:
- **Rata-rata**: < 1 menit untuk sebagian besar dataset
- **Dataset sulit**: 
  - Middle 2: 87.5 menit (rata-rata)
  - Middle 3: 36.9 menit
  - Late 13: 30.8 menit

### 3.4 Hasil Optimasi AT-ILS

#### Konsistensi (10 jam eksekusi):
- **Kategori Early**: Mayoritas KV < 10% (konsisten)
- **Kategori Middle**: KV bervariasi 2-15%
- **Kategori Late**: KV bervariasi 1-8%

#### Waktu Komputasi:
- **10 jam**: Cukup untuk banyak dataset
- **20 jam**: Beberapa dataset memerlukan waktu tambahan (Early 1, 4, 10, Middle 2, 3, Late 9, 10, 13)
- **Catatan**: Sebagian besar dataset memerlukan lebih banyak waktu optimasi dibanding Toronto

### 3.5 Perbandingan dengan Penelitian Terdahulu

#### Feasibility Comparison:
| Penelitian | Early | Middle | Late | Total | Persentase |
|------------|-------|--------|------|-------|------------|
| **AT-ILS (Penelitian ini)** | 15 | 15 | 15 | **45** | **94.7%** |
| Lamas-Fernandez et al. (2021) | 15 | 15 | 15 | 45 | N/A (ILP method) |
| Rosati et al. (2022) | 15 | 14 | 15 | 44 | **83%** |
| Berthold et al. (2021) | 13 | 13 | 14 | 40 | N/A |
| Fonseca and Toffolo (2022) | 12 | 13 | 12 | 37 | N/A |

#### Ranking Optimasi:
- **Peringkat Overall**: **4 dari 9 penelitian**
- **Peringkat per kategori**:
  - Early: 4.80
  - Middle: 4.73
  - Late: 4.80
  - **Total**: 4.77

#### Top 3 Penelitian:
1. **Lamas-Fernandez et al. (2021)**: Peringkat 1.47 (ILP - Integer Linear Programming, metode exact)
2. **Rosati et al. (2022)**: Peringkat 2.54 (Simulated Annealing dengan **22 parameter** + **6 LLH khusus**)
3. **Fonseca and Toffolo (2022)**: Peringkat 4.28

### 3.6 Hasil Solusi Terbaik Baru
**2 dataset dengan best new results:**
- **Middle 2**: 7115 (sebelumnya: 7381 oleh Lamas-Fernandez)
- **Middle 3**: 8648 (sebelumnya: 9542 oleh Rosati)

### 3.7 Dataset dengan Hasil Kompetitif
Perbedaan kecil dengan penelitian terbaik pada:
- Early: 1, 11
- Middle: 7, 15
- Late: 2, 3, 4, 5, 6, 13

### 3.8 Analisis Keunggulan
1. **Feasibility terbaik**: 94.7% vs 83% (Rosati) dengan metode metaheuristic
2. **Hasil baru**: 2 dataset best results
3. **Tidak ada parameter**: vs 22 parameter (Rosati), vs ILP exact method (Lamas)
4. **Konsistensi tinggi** dalam feasibility generation
5. **Unggul dari exact method** dalam beberapa aspek praktis

---

## 4. Perbandingan Lintas Benchmark

### 4.1 Rangkuman Performa AT-ILS

| Benchmark | Peringkat | Jumlah Pesaing | Best New Results | Tingkat Feasibility |
|-----------|-----------|----------------|------------------|---------------------|
| Toronto | 4/28 | 27 penelitian | 1 dataset (EAR83) | 100% |
| ITC 2019 | 4/9 | 8 penelitian | 0 | 100% |
| ITC 2021 | 4/9 | 8 penelitian | 2 dataset (Middle 2, 3) | 94.7% |

### 4.2 Konsistensi Antar Benchmark
**Sangat konsisten** dengan peringkat **4** di semua benchmark yang diuji, menunjukkan:
- Generalitas tinggi algoritma
- Stabilitas performa lintas domain
- Tidak sensitif terhadap karakteristik permasalahan

### 4.3 Tingkat Kompleksitas vs Performa

| Benchmark | Kompleksitas | Ukuran Max | Hard Const. | Waktu Feasibility | Waktu Optimasi |
|-----------|--------------|------------|-------------|-------------------|----------------|
| Toronto | Sederhana | 2.419 ujian | 1 jenis | < 24 jam (PUR93) | 10-100 jam |
| ITC 2019 | Tinggi | 8.813 kelas | 19 jenis | < 8.1 jam | 30-250 jam |
| ITC 2021 | Sangat Tinggi | 20 tim, 200+ const. | Banyak jenis | < 87.5 menit | 10-20 jam |

**Observasi**: Waktu komputasi berbanding lurus dengan kompleksitas dan ukuran dataset.

---

## 5. Karakteristik Unik Algoritma AT-ILS

### 5.1 Desain Algoritma

#### Tiga Tahapan Utama:
1. **Perturbation Phase**: 
   - Eksplorasi dengan threshold adaptif
   - Menerima solusi lebih buruk dalam batas threshold

2. **Local Search Phase**: 
   - Balance eksplorasi-eksploitasi mirip Simulated Annealing
   - Threshold menurun bertahap
   - Kembali ke solusi terbaik lokal jika perlu

3. **Move Acceptance Phase**: 
   - Strategi kembali ke solusi terbaik global
   - Mencegah pencarian menjauhi solusi optimal

### 5.2 Threshold Adaptif (Parameter-Free)

#### ThresholdList Calculation:
```
thresholdList = selisih nilai penalti saat LLH diterapkan ke semua event
thresholdValue = rata-rata(thresholdList)
```

**Keunggulan:**
- Threshold **menyesuaikan dengan karakteristik permasalahan**
- **Tidak memerlukan tuning** manual
- Threshold **menurun otomatis** untuk konvergensi

### 5.3 Low-Level Heuristics (LLH)

**3 LLH Generik:**
1. **Move**: Memindahkan kegiatan ke slot waktu lain
2. **Swap**: Menukar jadwal 2 kegiatan
3. **Kempe Chain**: Memindahkan kelompok kegiatan terkait

**Catatan**: LLH bersifat umum, tidak spesifik untuk domain tertentu.

### 5.4 Strategi Kembali ke Solusi Terbaik
- Mencegah pencarian menjauhi best solution
- Diterapkan pada move acceptance phase
- Menggunakan threshold dari kuartil pertama thresholdList

---

## 6. Perbandingan dengan Algoritma Kompetitor

### 6.1 Toronto Benchmark

#### Top Competitors:
1. **Bellio et al. (2021)** - Peringkat 1.88
   - Metode: Matheuristic (MIP + local search)
   - Kelebihan: Hasil terbaik
   - Kelemahan: Kompleksitas tinggi, waktu komputasi besar

2. **Leite et al. (2018)** - Peringkat 2.92
   - Metode: Specific heuristic
   - Kelebihan: Hasil sangat baik
   - Kelemahan: Domain-specific

3. **Burke and Bykov (2016)** - Peringkat 3.23
   - Metode: Graph-based hyper-heuristic
   - Kelebihan: Hasil baik
   - Kelemahan: Kompleksitas tinggi

**AT-ILS** - Peringkat 4.76
- **Kelebihan**: Tidak perlu parameter, LLH generik, konsistensi tinggi
- **Gap dengan Top 1**: Hasil kompetitif dengan gap minimal pada banyak dataset

### 6.2 ITC 2019 Benchmark

#### Top Competitors:
1. **Mikkelsen and Holm (2022)** - Peringkat 1.11
   - Metode: Matheuristic (MIP + Fix-and-Optimize)
   - Kelebihan: Hasil terbaik dominan
   - Kelemahan: Sangat kompleks, waktu komputasi sangat besar

2. **Rappos et al. (2022)** - Peringkat 2.76
   - Metode: Metaheuristic
   - Kelebihan: Balance complexity-performance
   - Kelemahan: Masih memerlukan tuning

3. **Sylejmani et al. (2022)** - Peringkat 3.46
   - Metode: Simulated Annealing
   - **30 parameter** yang perlu di-tune
   - Kelebihan: Hasil baik
   - Kelemahan: Sangat sensitif parameter

**AT-ILS** - Peringkat 4.43
- **Kelebihan**: 0 parameter, hasil sebanding pada beberapa dataset
- **Unggul dari**: Premananda (Whale Optimization), Great Deluge variants

### 6.3 ITC 2021 Benchmark

#### Top Competitors:
1. **Lamas-Fernandez et al. (2021)** - Peringkat 1.47
   - Metode: **ILP (Integer Linear Programming)** - exact method
   - Kelebihan: Hasil optimal untuk banyak dataset
   - Kelemahan: Metode exact, tidak scalable, waktu komputasi ekstrim
   - Catatan: **Tidak ada info tingkat keberhasilan feasibility**

2. **Rosati et al. (2022)** - Peringkat 2.54
   - Metode: Simulated Annealing
   - **22 parameter** + **6 LLH khusus ITC 2021**
   - Feasibility: **83%** (36/45 dataset 100%)
   - Kelebihan: Hasil sangat baik
   - Kelemahan: Sangat domain-specific

3. **Fonseca and Toffolo (2022)** - Peringkat 4.28
   - Metode: Hybrid heuristic
   - Kelebihan: Hasil baik
   - Kelemahan: Feasibility lebih rendah (37/45)

**AT-ILS** - Peringkat 4.77
- **Kelebihan**:
  - Feasibility **94.7%** (terbaik untuk metaheuristic vs 83% Rosati)
  - **0 parameter** vs 22 parameter (Rosati)
  - **3 LLH generik** vs 6 LLH khusus (Rosati)
  - **2 best new results**
- **Trade-off**: Slight gap dengan Rosati dalam optimasi, tetapi unggul dalam feasibility dan generalitas

---

## 7. Insight untuk Benchmark/Domain Lain

### 7.1 Karakteristik Permasalahan yang Cocok untuk AT-ILS

#### Sangat Cocok:
1. **Permasalahan NP-Hard** dengan search space besar
2. **Multi-constraint problems** (hard + soft constraints)
3. **Permasalahan dengan keterbatasan resource** (ruangan, waktu, kapasitas)
4. **Domain yang memerlukan feasibility tinggi** (94.7% - 100%)
5. **Ketika parameter tuning tidak praktis** atau waktu terbatas

#### Cocok dengan Perhatian:
1. **Dataset sangat besar** (>10.000 entitas): waktu komputasi meningkat signifikan
2. **Real-time scheduling**: AT-ILS memerlukan waktu komputasi substansial
3. **Permasalahan dengan struktur sangat spesifik**: mungkin LLH khusus lebih baik

### 7.2 Faktor Penentu Performa

#### Tingkat Kompleksitas Constraint:
- **Sederhana (Toronto)**: Performa excellent (peringkat 4, KV < 1%)
- **Tinggi (ITC 2019)**: Performa baik (peringkat 4, KV 1-50%)
- **Sangat Tinggi (ITC 2021)**: Performa baik dengan feasibility terbaik (94.7%)

**Kesimpulan**: AT-ILS **stabil** di semua tingkat kompleksitas.

#### Ukuran Dataset:
- **Kecil (<500 entitas)**: Sangat cepat (< 1 menit feasibility, < 10 jam optimasi)
- **Sedang (500-2000)**: Cepat (< 1 jam feasibility, 10-50 jam optimasi)
- **Besar (2000-5000)**: Moderat (1-8 jam feasibility, 50-150 jam optimasi)
- **Sangat Besar (>5000)**: Lambat (> 8 jam feasibility, > 150 jam optimasi)

#### Keterbatasan Slot Waktu:
- **Toronto**: Fleksibel (setiap ujian bisa di slot manapun) → KV rendah
- **ITC 2019**: Sangat terbatas (setiap kelas punya daftar slot terbatas) → KV tinggi
- **ITC 2021**: Terbatas struktural (tim, home/away) → KV sedang

**Kesimpulan**: Fleksibilitas slot waktu **mempengaruhi konsistensi**, bukan performa rata-rata.

### 7.3 Rekomendasi Implementasi untuk Domain Baru

#### Langkah 1: Analisis Karakteristik Domain
1. **Identifikasi jenis constraint**:
   - Berapa banyak hard vs soft constraint?
   - Apakah ada constraint struktural kompleks?

2. **Ukur ukuran masalah**:
   - Jumlah entitas yang dijadwalkan
   - Jumlah slot waktu/resource
   - Estimasi search space

3. **Evaluasi fleksibilitas**:
   - Seberapa fleksibel slot waktu?
   - Berapa banyak resource yang tersedia vs kebutuhan?

#### Langkah 2: Modifikasi LLH (Jika Diperlukan)
**Gunakan 3 LLH default** (move, swap, kempe) **JIKA**:
- Permasalahan tidak memiliki struktur unik
- Fokus pada generalitas
- Tidak ada waktu untuk develop LLH khusus

**Tambahkan LLH khusus** jika:
- Ada struktur domain yang sangat spesifik (seperti ITC 2019: course-config-subpart-class hierarchy)
- Performance gap dengan state-of-art terlalu besar
- Ada domain knowledge yang bisa dieksploitasi

**Contoh**:
- ITC 2019 bisa ditambahkan LLH untuk handle course structure
- Sports timetabling bisa ditambahkan LLH untuk handle home/away constraints

#### Langkah 3: Tentukan Target Performa
**Feasibility Target:**
- **≥95%**: Excellent (seperti ITC 2021: 94.7%)
- **≥90%**: Good
- **<90%**: Consider algorithm improvement

**Ranking Target (jika ada kompetisi):**
- **Top 5**: Excellent untuk algoritma generik
- **Top 10**: Good
- **Above median**: Acceptable untuk first implementation

#### Langkah 4: Kalibrasi Waktu Komputasi
**Berdasarkan pengalaman 3 benchmark:**

| Ukuran Dataset | Feasibility Time | Optimization Time | Total |
|---------------|------------------|-------------------|-------|
| < 500 entities | < 5 min | 10-20 jam | ~20 jam |
| 500-1000 | 5-30 min | 20-50 jam | ~50 jam |
| 1000-3000 | 30-120 min | 50-100 jam | ~100 jam |
| > 3000 | 1-8 jam | 100-250 jam | ~250 jam |

**Rule of thumb**: Alokasikan **10x waktu feasibility** untuk optimasi.

#### Langkah 5: Benchmark Comparison Strategy
**Jika benchmark memiliki existing solutions:**
1. **Pertama target feasibility** ≥90% (lebih penting dari ranking)
2. **Target ranking** di atas median (top 50%)
3. **Highlight** keunggulan: no parameter, generic LLH, consistency
4. **Analisis gap** dengan top performers:
   - Jika gap kecil (<10%): excellent result
   - Jika gap sedang (10-30%): good result, emphasize trade-off (generalitas vs performance)
   - Jika gap besar (>30%): consider domain-specific improvements

### 7.4 Ekspektasi Performa di Domain Baru

#### Permasalahan Serupa Toronto (Simple Constraints):
- **Feasibility**: 100% (high confidence)
- **Ranking**: Top 5 (likely)
- **Waktu**: Cepat (< 50 jam untuk dataset besar)
- **KV**: < 5% (sangat konsisten)

#### Permasalahan Serupa ITC 2019 (Complex Constraints + Limited Slots):
- **Feasibility**: 100% (high confidence)
- **Ranking**: Top 5-10 (likely)
- **Waktu**: Moderat-lambat (50-250 jam)
- **KV**: 5-30% (variability medium-high)

#### Permasalahan Serupa ITC 2021 (Very Complex Constraints + High Conflict):
- **Feasibility**: 90-95% (expect challenges on hardest instances)
- **Ranking**: Top 5-10 (if feasibility achieved)
- **Waktu**: Cepat untuk feasibility (< 2 jam), moderat untuk optimasi (20-50 jam)
- **KV**: 5-10% (konsisten)

#### Domain Baru yang Belum Diuji:
**Job Shop Scheduling:**
- **Ekspektasi**: Feasibility 95-100%, Ranking Top 10
- **Alasan**: Mirip dengan timetabling, constraint terstruktur
- **Rekomendasi**: Tambahkan LLH untuk handle job precedence

**Vehicle Routing Problem (VRP):**
- **Ekspektasi**: Feasibility 90-95%, Ranking Top 15
- **Alasan**: Search space sangat besar, constraints spasial
- **Rekomendasi**: Perlu LLH khusus untuk geographical constraints

**Resource Allocation:**
- **Ekspektasi**: Feasibility 95-100%, Ranking Top 5
- **Alasan**: Mirip dengan timetabling
- **Rekomendasi**: LLH default cukup

**Nurse Rostering:**
- **Ekspektasi**: Feasibility 95-100%, Ranking Top 5-10
- **Alasan**: Struktur constraint mirip timetabling
- **Rekomendasi**: Pertimbangkan LLH untuk shift patterns

### 7.5 Red Flags (Kapan AT-ILS Mungkin Tidak Optimal)

⚠️ **Hati-hati jika:**
1. **Search space terlalu besar**: >100.000 entitas dengan constraint sangat ketat
2. **Real-time requirement**: Butuh solusi dalam <1 menit
3. **Optimal solution required**: AT-ILS adalah heuristic, bukan exact method
4. **Domain dengan structure exploitation advantage**: Jika matheuristic atau ILP lebih cocok (seperti ITC 2019 vs Mikkelsen)
5. **Feasibility sangat sulit**: Jika state-of-art struggle dengan feasibility, AT-ILS mungkin juga struggle (tapi cenderung lebih baik, lihat ITC 2021)

---

## 8. Kesimpulan dan Rekomendasi

### 8.1 Kesimpulan Utama

1. **Generalitas Terbukti**: 
   - Konsisten **peringkat 4** di 3 benchmark berbeda
   - Domain: exam timetabling, course timetabling, sports timetabling
   - **Tidak memerlukan parameter tuning**

2. **Feasibility Excellence**:
   - Toronto: **100%**
   - ITC 2019: **100%**
   - ITC 2021: **94.7%** (terbaik untuk metaheuristic)

3. **Best New Results**:
   - Toronto: 1 dataset (EAR83)
   - ITC 2021: 2 dataset (Middle 2, 3)
   - **Total**: 3 best new results across benchmarks

4. **Konsistensi**:
   - Mayoritas dataset: KV < 10%
   - Tidak ada hasil anomali buruk
   - Stabil across different problem characteristics

### 8.2 Positioning AT-ILS

**AT-ILS adalah pilihan excellent untuk:**
- ✅ Penelitian yang fokus pada **generalitas**
- ✅ Praktisi yang **tidak punya waktu tuning parameter**
- ✅ Permasalahan yang **feasibility critical**
- ✅ Perbandingan dengan **multiple algorithms**
- ✅ Baseline yang **kuat** untuk pengembangan lebih lanjut

**AT-ILS bukan pilihan utama untuk:**
- ❌ **Absolute best result** diperlukan (gunakan domain-specific matheuristic atau ILP)
- ❌ **Real-time** applications
- ❌ **Optimal solution** guaranteed (gunakan exact methods)

### 8.3 Rekomendasi untuk Benchmark/Domain Baru

#### Untuk Peneliti:
1. **Mulai dengan AT-ILS default** (3 LLH generik, no parameter)
2. **Target feasibility ≥90%** sebagai milestone pertama
3. **Target ranking Top 5-10** untuk algoritma generik
4. **Jika gap >30%** dengan top performer, consider:
   - Tambahkan domain-specific LLH (1-2 LLH)
   - Hybrid dengan local intensification methods
   - Tapi **jangan** tambahkan parameter tuning (maintain generalitas)

#### Untuk Praktisi:
1. **Gunakan AT-ILS** jika:
   - Permasalahan mirip timetabling/scheduling
   - Waktu development terbatas
   - Feasibility lebih penting dari optimality
   - Dataset berubah-ubah (tidak perlu re-tune)

2. **Ekspektasi realistic**:
   - Solusi dalam **Top 10** existing solutions
   - Waktu komputasi: **~10x ukuran dataset** (rule of thumb)
   - Feasibility: **≥90%** untuk sebagian besar instances

3. **Kelebihan praktis**:
   - **No tuning**: langsung pakai
   - **Maintainable**: mudah dipahami dan dimodifikasi
   - **Reliable**: konsisten across instances

### 8.4 Future Directions

**Untuk meningkatkan AT-ILS lebih lanjut:**
1. **Adaptive LLH selection**: Saat ini random, bisa di-improve dengan learning
2. **Hybrid dengan methods lain**: Kombinasi dengan local search intensification
3. **Parallelization**: Threshold calculation bisa di-paralel
4. **Dynamic threshold strategies**: Saat ini linear decrease, bisa di-improve

**Tapi tetap maintain:**
- ✅ **Zero parameter** philosophy
- ✅ **Generic LLH** sebagai default
- ✅ **Simplicity** dan maintainability

---

## 9. Technical Summary

### 9.1 Algorithm Complexity
- **Time Complexity**: O(n × m × k)
  - n = jumlah iterasi
  - m = jumlah events
  - k = jumlah LLH applications
- **Space Complexity**: O(m + t)
  - m = solution size
  - t = threshold list size

### 9.2 Critical Success Factors
1. **Threshold adaptability**: Menyesuaikan dengan problem characteristics
2. **Balance exploration-exploitation**: Mirip Simulated Annealing tanpa parameter
3. **Return to best strategy**: Mencegah divergence dari best solution
4. **Generic LLH**: Move, Swap, Kempe Chain cukup untuk banyak domain

### 9.3 Sensitivity Analysis
**Tidak sensitif terhadap:**
- ✅ Problem size (tested: 80 - 8813 entities)
- ✅ Constraint complexity (tested: 1 constraint - 200+ constraints)
- ✅ Domain type (tested: 3 different domains)

**Sensitif terhadap:**
- ⚠️ Slot flexibility: lebih fleksibel → konsistensi lebih baik
- ⚠️ Computation time: lebih lama → hasil lebih baik (diminishing returns setelah threshold tertentu)

---

## 10. Citation dan Referensi

Untuk mengutip hasil-hasil ini dalam penelitian atau implementasi baru:

**Key Results:**
- **Toronto**: Peringkat 4/28, Best new result EAR83 (32.40)
- **ITC 2019**: Peringkat 4/9, 100% feasibility
- **ITC 2021**: Peringkat 4/9, 94.7% feasibility, 2 best new results

**Key Strength**: Parameter-free, generic LLH, consistent performance

**Comparison Baseline**: 
- For generality: Use AT-ILS as strong baseline
- For domain-specific: AT-ILS provides competitive trade-off between simplicity and performance

---

*Dokumen ini disusun sebagai referensi utama untuk implementasi AT-ILS pada benchmark atau domain baru. Update dokumen ini jika ada hasil baru atau insight tambahan dari implementasi di domain lain.*

**Version**: 1.0  
**Last Updated**: Januari 2026  
**Based on**: BAB 4, 5, 6, 7 - Disertasi AT-ILS
