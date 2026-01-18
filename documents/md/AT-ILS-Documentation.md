# AT-ILS (Adaptive Threshold-Iterated Local Search)
## Dokumentasi Lengkap Algoritma

---

## Daftar Isi

1. [Pengenalan](#pengenalan)
2. [Rasionalitas Desain Algoritma](#rasionalitas-desain-algoritma)
3. [Desain Umum Algoritma AT-ILS](#desain-umum-algoritma-at-ils)
4. [Fungsi dan Perhitungan Nilai Ambang Batas](#fungsi-dan-perhitungan-nilai-ambang-batas)
5. [Tahapan Perturbation](#tahapan-perturbation)
6. [Tahapan Local Search](#tahapan-local-search)
7. [Tahapan Move Acceptance](#tahapan-move-acceptance)
8. [Low Level Heuristic (LLH)](#low-level-heuristic-llh)
9. [Hasil Implementasi dan Perbandingan](#hasil-implementasi-dan-perbandingan)
10. [Pseudocode Lengkap](#pseudocode-lengkap)
11. [Detail Teknis Implementasi](#detail-teknis-implementasi)

---

## Pengenalan

**AT-ILS (Adaptive Threshold-Iterated Local Search)** adalah algoritma metaheuristik yang dikembangkan untuk menyelesaikan permasalahan penjadwalan lintas domain dengan pendekatan **hiper-heuristik selection-perturbation**. Algoritma ini didesain khusus untuk **proses optimasi solusi** dengan tujuan meminimalkan pelanggaran soft constraint sambil mempertahankan solusi dalam kondisi feasible.

### Keunggulan Utama AT-ILS

1. **Tidak Membutuhkan Pengaturan Parameter** - Algoritma ini menggunakan strategi nilai ambang batas adaptif yang secara otomatis menyesuaikan dengan karakteristik permasalahan
2. **Keseimbangan Eksplorasi-Eksploitasi** - Menerapkan strategi yang menyerupai Simulated Annealing namun tanpa masalah sensitivitas parameter
3. **Generalitas Tinggi** - Dapat diterapkan pada berbagai domain penjadwalan tanpa modifikasi signifikan
4. **Strategi Kembali ke Solusi Terbaik** - Mencegah pencarian solusi bergerak terlalu jauh dari solusi optimal yang telah ditemukan

### Konteks Pengembangan

AT-ILS dikembangkan berdasarkan kerangka kerja **Iterated Local Search (ILS)** dengan tiga tahapan utama:
- **Perturbation** - Eksplorasi ruang solusi
- **Local Search** - Eksploitasi dan peningkatan solusi
- **Move Acceptance** - Pengambilan keputusan penerimaan solusi

### Publikasi dan Kredensial

**Sumber Utama**: Disertasi Doktoral
- **Judul**: "Pengembangan Algoritma Hiper-Heuristik dalam Menyelesaikan Permasalahan Optimasi Penjadwalan Lintas Domain"
- **Penulis**: I Gusti Agung Premananda (NRP: 7026211002)
- **Promotor**: Dr. Ir. Aris Tjahyanto, M.Kom
- **Co-Promotor**: Ahmad Muklason, S.Kom., M.Sc., Ph.D
- **Institusi**: Program Studi Doktor Sistem Informasi, Departemen Sistem Informasi, Fakultas Teknologi Elektro dan Informatika Cerdas, Institut Teknologi Sepuluh Nopember, Surabaya
- **Tahun**: 2025
- **Tanggal Ujian**: 15 Januari 2025

---

## Rasionalitas Desain Algoritma

### Analisis Algoritma Simulated Annealing

Penelitian sebelumnya menunjukkan bahwa **Simulated Annealing** adalah algoritma dengan performa terbaik pada berbagai benchmark penjadwalan. Namun, algoritma ini memiliki kelemahan utama: **sensitif terhadap pengaturan parameter** (temperature dan cooling rate).

#### Masalah dengan Parameter yang Tidak Tepat

**a) Eksplorasi Berlebihan (Temperature Terlalu Tinggi)**
- Solusi terjebak dalam rentang tertentu
- Tidak ada peningkatan signifikan
- Nilai penalti berfluktuasi tanpa konvergensi
- Solusi bergerak pada rentang nilai 40-65 tanpa penurunan yang konsisten

**b) Eksploitasi Berlebihan (Temperature Terlalu Rendah)**
- Solusi cepat terjebak di local optima
- Tidak ada eksplorasi ruang solusi yang cukup
- Konvergensi prematur pada iterasi awal (sekitar iterasi ke-1000)
- Penurunan sangat cepat di awal kemudian stagnan

### Solusi yang Ditawarkan AT-ILS

AT-ILS dirancang untuk:

1. **Mengadopsi keseimbangan eksplorasi-eksploitasi** dari Simulated Annealing
2. **Menghilangkan ketergantungan pada parameter** dengan nilai ambang batas adaptif
3. **Mengimplementasikan strategi kembali ke solusi terbaik** untuk mencegah pencarian yang menjauhi optimal

### Konsep Nilai Ambang Batas Adaptif

Algoritma AT-ILS menggunakan **dua variabel kunci**:

1. **`thresholdList`** - Daftar nilai yang menyimpan perkiraan jangkauan perubahan solusi saat LLH diterapkan
2. **`thresholdValue`** - Nilai ambang batas aktual yang digunakan dalam ketiga tahapan algoritma

**Konsep Adaptif:**
- Setiap permasalahan memiliki karakteristik jangkauan perubahan yang berbeda
- Kondisi solusi saat ini mempengaruhi jangkauan perubahan yang mungkin terjadi
- `thresholdList` dihitung ulang setiap iterasi berdasarkan kondisi solusi terkini
- Memberikan nilai ambang batas yang **adaptif terhadap permasalahan dan kondisi solusi**

**Contoh Adaptivitas:**
- Pada solusi dengan penalti tinggi (misalnya 500): Penerapan LLH dapat menghasilkan perubahan besar (±50-100)
- Pada solusi dengan penalti rendah (misalnya 50): Penerapan LLH menghasilkan perubahan kecil (±1-5)
- `thresholdList` mencerminkan jangkauan ini dan disesuaikan setiap iterasi

### Perbandingan Karakteristik Algoritma

| Aspek | Simulated Annealing | LAHC | Threshold Acceptance | AT-ILS |
|-------|---------------------|------|----------------------|--------|
| **Eksplorasi** | ✓ Baik | ~ Moderate | ✓✓ Sangat baik (terlalu) | ✓ Baik |
| **Eksploitasi** | ✓ Baik | ✓✓ Sangat baik | ~ Moderate | ✓ Baik |
| **Keseimbangan** | ✓✓ Sangat baik | ~ Bias eksploitasi | ~ Bias eksplorasi | ✓✓ Sangat baik |
| **Parameter** | ✗ Sensitif | ✗ Perlu tuning | ✗ Perlu tuning | ✓ Tanpa parameter |
| **Konvergensi** | Gradual | Cepat awal, stagnan | Terjebak rentang | Gradual terkontrol |
| **Generalitas** | ~ Bervariasi | ✓ Cukup baik | ~ Bervariasi | ✓✓ Tinggi |

### Strategi Kembali ke Solusi Terbaik

**Masalah yang Diatasi:**
```
Ilustrasi tanpa strategi:
Iterasi 100: Best = 30 (ditemukan)
Iterasi 150: Current = 35
Iterasi 200: Current = 38
...
Iterasi 1000: Current = 45 (sangat jauh dari best!)
```

**Dengan Strategi AT-ILS:**
- Jika `currentPenalty` bergerak terlalu jauh dari `bestPenalty`
- Algoritma kembali ke `bestSol` sebagai `currentSol`
- Pencarian dimulai kembali dari titik terbaik yang pernah ditemukan
- Mencegah pencarian menjauhi optimal hingga akhir iterasi

---

## Desain Umum Algoritma AT-ILS

### Pseudocode Utama

```
Algoritma 6: AT-ILS
procedure OPTIMIZESOLUTION(initialSol, eventList)
    currentSol ← initialSol
    bestSol ← currentSol

    while within duration of running time do
        thresholdList ← calculateThreshold(currentSol, eventList)
        thresholdValue ← updateThreshold(thresholdList)
        newSol ← currentSol

        perturbationPhase(newSol, eventList, thresholdValue)
        localSearchPhase(newSol, eventList, thresholdList)
        moveAcceptancePhase(newSol, currentSol, bestSol)
    end while

    return bestSol
end procedure
```

### Input Algoritma

1. **`initialSol`** - Solusi awal yang sudah feasible (memenuhi semua hard constraint)
   - Dihasilkan dari algoritma PA-ILS atau metode konstruktif lainnya
   - Harus memenuhi 100% hard constraint
   - Nilai soft constraint penalty dapat bervariasi

2. **`eventList`** - Daftar kegiatan yang ada dalam solusi awal
   - Berisi semua event/kegiatan yang perlu dijadwalkan
   - Digunakan untuk iterasi dalam calculateThreshold dan local search

### Variabel Utama

| Variabel | Fungsi | Nilai Awal | Perubahan |
|----------|--------|------------|-----------|
| `currentSol` | Solusi yang sedang digunakan pada iterasi saat ini | `initialSol` | Diperbarui di move acceptance |
| `bestSol` | Solusi terbaik yang pernah ditemukan | `initialSol` | Diperbarui ketika solusi lebih baik ditemukan |
| `newSol` | Solusi baru hasil perturbation dan local search | Copy dari `currentSol` | Dimodifikasi di perturbation dan local search |
| `thresholdList` | Daftar nilai ambang batas | Dihitung tiap iterasi | Berkurang seiring iterasi local search |
| `thresholdValue` | Nilai ambang batas yang digunakan | Rata-rata `thresholdList` | Menurun seiring `thresholdList` berkurang |

### Alur Kerja Algoritma

**Fase Inisialisasi:**
1. Set `currentSol = initialSol`
2. Set `bestSol = initialSol`
3. Catat nilai penalty awal

**Loop Optimasi (sampai batas waktu tercapai):**

**Iterasi n:**
```
1. Calculate thresholdList
   - Coba semua event dengan LLH random
   - Simpan selisih penalty jika feasible
   - Sort dari kecil ke besar

2. Update thresholdValue
   - thresholdValue = rata-rata(thresholdList)

3. Copy currentSol ke newSol
   - Solusi baru untuk dimodifikasi

4. Perturbation Phase
   - Eksplorasi: terima solusi lebih buruk
   - Batas: penalty < currentPenalty + thresholdValue

5. Local Search Phase
   - Eksploitasi: cari solusi lebih baik
   - Gunakan thresholdLocal yang berkurang bertahap
   - Update localBest jika ada peningkatan

6. Move Acceptance Phase
   - Terima jika newSol lebih baik
   - Terima probabilistik jika sedikit lebih buruk
   - Kembali ke bestSol jika terlalu buruk
```

**Output:**
- Return `bestSol` (solusi terbaik yang ditemukan)

### Karakteristik Konvergensi

**Penurunan Gradual:**
- `thresholdList` berkurang elemen-elemennya (dari yang terbesar)
- `thresholdValue` menurun secara natural
- Local search semakin ketat dalam menerima solusi
- Mengarah pada konvergensi tanpa parameter tuning

**Keseimbangan Dinamis:**
- Awal iterasi: eksplorasi lebih dominan (thresholdValue besar)
- Tengah iterasi: eksplorasi dan eksploitasi seimbang
- Akhir iterasi: eksploitasi lebih dominan (thresholdValue kecil/0)

---

## Fungsi dan Perhitungan Nilai Ambang Batas

### Fungsi calculateThreshold()

Fungsi ini menghitung daftar nilai ambang batas berdasarkan kondisi solusi saat ini dengan melakukan sampling perubahan penalty yang mungkin terjadi.

#### Pseudocode

```
Algoritma 7: Calculate Threshold
procedure CALCULATETHRESHOLD(currentSolution, eventList)
    currentPenalty ← calculatePenalty(currentSolution)
    thresholdList ← an empty list

    while thresholdList is empty do
        for each event in eventList do
            chosenLLH ← selectRandomLLH()
            doLLH(chosenLLH, event)

            if checkFeasibility(currentSolution) is true then
                newPenalty ← calculatePenalty(currentSolution)

                if newPenalty ≠ currentPenalty then
                    difference ← |currentPenalty - newPenalty|
                    append(thresholdList, difference)
                end if
            end if

            undoChanges(event)
        end for
    end while

    sort(thresholdList)
    return thresholdList
end procedure
```

#### Cara Kerja Detail

**1. Inisialisasi:**
- Hitung `currentPenalty` dari solusi saat ini
- Buat `thresholdList` kosong (akan diisi dengan nilai selisih)

**2. Loop Pengisian (sampai thresholdList berisi minimal 1 nilai):**

Untuk setiap kegiatan dalam `eventList`:

a) **Pilih LLH Secara Acak:**
   - Move, Swap, atau Kempe Chain
   - Distribusi uniform (1/3 peluang masing-masing)

b) **Terapkan LLH:**
   - Modifikasi solusi sesuai LLH yang dipilih
   - Bisa menghasilkan solusi feasible atau infeasible

c) **Cek Feasibility:**
   - Jika **FEASIBLE**:
     - Hitung `newPenalty`
     - Jika `newPenalty ≠ currentPenalty`:
       - Hitung `difference = |currentPenalty - newPenalty|`
       - Tambahkan `difference` ke `thresholdList`
   - Jika **INFEASIBLE**:
     - Abaikan (tidak tambahkan ke thresholdList)

d) **Undo Changes:**
   - Kembalikan solusi ke kondisi awal
   - Solusi kembali seperti sebelum LLH diterapkan

**Contoh Eksekusi:**
```
currentPenalty = 100
eventList = [event1, event2, event3, ..., event100]

Iterasi event1:
  - chosenLLH = Swap
  - Swap(event1, random_timeslot)
  - newPenalty = 105
  - difference = |100 - 105| = 5
  - thresholdList = [5]
  - Undo changes

Iterasi event2:
  - chosenLLH = Move
  - Move(event2, random_timeslot)
  - Infeasible → Skip
  - Undo changes

Iterasi event3:
  - chosenLLH = Kempe Chain
  - KempeChain(event3)
  - newPenalty = 97
  - difference = |100 - 97| = 3
  - thresholdList = [5, 3]
  - Undo changes

... (lanjut sampai semua event)

Final thresholdList (sebelum sort) = [5, 3, 8, 12, 1, 2, ...]
```

**3. Finalisasi:**
- **Sort** `thresholdList` dari **nilai terkecil ke terbesar**
- Return `thresholdList`

```
Contoh setelah sort:
thresholdList = [1, 2, 3, 5, 8, 12, ...]
```

#### Tujuan Fungsi

1. **Mengetahui Jangkauan Perubahan Solusi**
   - Berapa besar perubahan penalty yang mungkin terjadi saat LLH diterapkan
   - Mencerminkan "step size" yang natural untuk permasalahan

2. **Adaptif Terhadap Kondisi Solusi**
   - Solusi dengan penalty tinggi → perubahan cenderung besar
   - Solusi dengan penalty rendah → perubahan cenderung kecil
   - Jangkauan berubah seiring kondisi solusi (nilai penalti tinggi vs rendah)

3. **Adaptif Terhadap Permasalahan**
   - Dataset Toronto: jangkauan kecil (1-5)
   - Dataset ITC 2021: jangkauan besar (10-50)
   - Setiap dataset memiliki karakteristik berbeda yang tercermin dalam thresholdList

#### Loop Ulang Jika thresholdList Kosong

**Mengapa bisa kosong?**
- Semua penerapan LLH menghasilkan solusi infeasible
- Semua penerapan LLH menghasilkan penalty yang sama (tidak ada perubahan)

**Solusi:**
- Loop `while thresholdList is empty` akan mengulang sampling
- Kemungkinan sangat kecil terjadi berkali-kali
- Pada kondisi ekstrem, bisa terjadi beberapa kali hingga mendapat nilai feasible

### Fungsi updateThreshold()

Menghitung nilai ambang batas aktual (`thresholdValue`) dari `thresholdList`.

#### Formula

```
thresholdValue ← rata-rata(thresholdList)
```

**Implementasi:**
```
thresholdValue = sum(thresholdList) / length(thresholdList)
```

**Contoh:**
```
thresholdList = [1, 2, 3, 5, 8, 12, 15, 20]
thresholdValue = (1+2+3+5+8+12+15+20) / 8 = 66 / 8 = 8.25
```

#### Strategi Penurunan

Seiring berjalannya algoritma:

1. **Penghapusan Elemen dari Nilai Terbesar:**
   - Fungsi `UpdateThresholdList` menghapus elemen dari index terakhir (nilai terbesar)
   - Contoh: `[1, 2, 3, 5, 8, 12, 15, 20]` → hapus 20 → `[1, 2, 3, 5, 8, 12, 15]`

2. **Penurunan thresholdValue:**
   ```
   Awal: thresholdList = [1, 2, 3, 5, 8, 12, 15, 20]
         thresholdValue = 8.25

   Setelah hapus 1 elemen (20):
         thresholdList = [1, 2, 3, 5, 8, 12, 15]
         thresholdValue = 6.57

   Setelah hapus 2 elemen lagi (15, 12):
         thresholdList = [1, 2, 3, 5, 8]
         thresholdValue = 3.8

   Setelah hapus semua kecuali 3 elemen:
         thresholdList = [1, 2, 3]
         thresholdValue = 2.0
   ```

3. **Konvergensi:**
   - Semakin sedikit elemen di `thresholdList` → semakin kecil `thresholdValue`
   - Penurunan bertahap mengarahkan solusi pada konvergensi
   - Tidak ada penurunan yang tiba-tiba drastis (smooth convergence)

#### Interpretasi Nilai

| thresholdValue | Interpretasi | Fase Optimasi |
|----------------|--------------|---------------|
| 15-20 | Eksplorasi tinggi | Awal |
| 8-15 | Eksplorasi-eksploitasi seimbang | Tengah |
| 3-8 | Eksploitasi meningkat | Tengah-akhir |
| 0-3 | Eksploitasi tinggi | Akhir |
| 0 | Hanya terima solusi lebih baik | Konvergensi final |

---

## Tahapan Perturbation

Tahapan perturbation bertujuan untuk **mengeksplorasi ruang solusi** dengan menghasilkan solusi yang mungkin lebih buruk dari solusi saat ini.

### Pseudocode

```
Algoritma 8: Perturbation Phase
procedure PERTURBATIONPHASE(newSol, eventList, thresholdValue)
    perturbation ← true
    currentPenalty ← calculatePenalty(newSol)

    while perturbation do
        chosenLLH ← selectRandomLLH()
        doLLH(chosenLLH, event.getRandom())

        if checkFeasibility() then
            newPenalty ← calculatePenalty()

            if newPenalty > currentPenalty + thresholdValue then
                perturbation ← false
            end if
        else
            undoChanges()
        end if
    end while
end procedure
```

### Cara Kerja Detail

**1. Inisialisasi:**
- Set `perturbation = true` (menandakan proses berjalan)
- Hitung `currentPenalty` dari `newSol` sebagai baseline
- `currentPenalty` adalah referensi awal untuk batasan eksplorasi

**2. Loop Perturbation:**

Setiap iterasi dalam loop:

a) **Pilih LLH dan Event Secara Acak:**
   - `chosenLLH` = salah satu dari {Move, Swap, Kempe Chain}
   - `event` = pilih acak dari `eventList`

b) **Terapkan LLH:**
   - Modifikasi `newSol` dengan `chosenLLH` pada `event` yang dipilih

c) **Cek Feasibility:**
   
   **Jika FEASIBLE:**
   - Hitung `newPenalty` dari solusi hasil modifikasi
   - **Solusi SELALU diterima** (tidak ada undo)
   - Cek kondisi berhenti:
     - Jika `newPenalty > currentPenalty + thresholdValue`:
       - Set `perturbation = false` → **BERHENTI**
     - Jika tidak:
       - **LANJUTKAN** loop (terapkan LLH lagi)
   
   **Jika INFEASIBLE:**
   - **Kembalikan perubahan** (undo)
   - Solusi ditolak, kembali ke kondisi sebelum LLH diterapkan
   - **LANJUTKAN** loop (coba LLH lain)

**3. Kondisi Berhenti:**

Loop berhenti ketika:
```
newPenalty > currentPenalty + thresholdValue
```

**Interpretasi:**
- Solusi sudah cukup jauh (lebih buruk) dari baseline
- Eksplorasi sudah mencapai batas yang diizinkan
- Siap untuk tahapan local search (eksploitasi)

### Tujuan dan Strategi

#### 1. Eksplorasi Terkontrol

**Tujuan:**
- Keluar dari local optima
- Mencari region baru di search space
- Mengizinkan solusi memburuk namun terbatas

**Kontrol:**
- `thresholdValue` membatasi seberapa buruk solusi yang diterima
- Tidak terlalu jauh dari solusi awal (controlled perturbation)

#### 2. Penggunaan thresholdValue

**Sebagai Batas Eksplorasi:**
```
Baseline: currentPenalty = 100
thresholdValue = 10

Batas eksplorasi: 100 + 10 = 110

Loop:
  Iterasi 1: newPenalty = 103 → Diterima (< 110), lanjut
  Iterasi 2: newPenalty = 105 → Diterima (< 110), lanjut
  Iterasi 3: newPenalty = 108 → Diterima (< 110), lanjut
  Iterasi 4: newPenalty = 112 → STOP (> 110)
```

**Adaptivitas:**
- Dataset dengan perubahan kecil → thresholdValue kecil → perturbation ringan
- Dataset dengan perubahan besar → thresholdValue besar → perturbation kuat

#### 3. Penerimaan Solusi

**Karakteristik:**
- **Semua solusi feasible diterima**, tidak peduli seberapa buruk
- Tidak ada kriteria penerimaan selain feasibility
- Eksplorasi maksimal dalam batas yang ditentukan

**Perbedaan dengan Local Search:**
| Aspek | Perturbation | Local Search |
|-------|--------------|--------------|
| Penerimaan | Semua feasible diterima | Hanya solusi lebih baik/sama diterima |
| Arah perubahan | Bisa memburuk | Cenderung membaik |
| Batas | currentPenalty + thresholdValue | thresholdLocal (berkurang) |
| Tujuan | Eksplorasi | Eksploitasi |

#### 4. Persiapan untuk Local Search

**Output Perturbation:**
- Solusi yang "cukup berbeda" dari `currentSol`
- Masih dalam jangkauan yang reasonable
- Siap untuk dieksploitasi di local search

**Analogi:**
```
Perturbation = "lompat" ke region baru
Local Search = "panjat bukit" di region tersebut
```

### Ilustrasi Konsep dengan Contoh Nyata

**Skenario Dataset Toronto (EAR83):**

```
Kondisi Awal:
- currentPenalty = 35.2
- thresholdValue = 2.5 (kecil karena jangkauan dataset kecil)
- Batas: 35.2 + 2.5 = 37.7

Eksekusi Perturbation:
Iterasi 1: Move(exam15, slot20) → penalty = 36.1 → TERIMA, lanjut
Iterasi 2: Swap(exam3, exam7) → penalty = 36.8 → TERIMA, lanjut
Iterasi 3: Move(exam22, slot5) → penalty = 37.9 → STOP (> 37.7)

Hasil: Solusi memburuk dari 35.2 ke 37.9, siap untuk local search
```

**Skenario Dataset ITC 2021 (Middle 2):**

```
Kondisi Awal:
- currentPenalty = 1520
- thresholdValue = 45 (besar karena jangkauan dataset besar)
- Batas: 1520 + 45 = 1565

Eksekusi Perturbation:
Iterasi 1: Kempe Chain(game5) → penalty = 1535 → TERIMA, lanjut
Iterasi 2: Move(game12, slot8) → infeasible → TOLAK, undo, lanjut
Iterasi 3: Swap(game3, game15) → penalty = 1548 → TERIMA, lanjut
Iterasi 4: Move(game7, slot20) → penalty = 1555 → TERIMA, lanjut
Iterasi 5: Swap(game1, game9) → penalty = 1570 → STOP (> 1565)

Hasil: Solusi memburuk dari 1520 ke 1570, siap untuk local search
```

### Pengaruh thresholdValue terhadap Perturbation

| thresholdValue | Eksplorasi | Jumlah Iterasi Perturbation | Dampak |
|----------------|------------|----------------------------|---------|
| Sangat kecil (0-2) | Minimal | 1-3 iterasi | Hampir tidak ada perubahan |
| Kecil (2-5) | Ringan | 3-7 iterasi | Perubahan terbatas |
| Sedang (5-15) | Seimbang | 5-15 iterasi | Eksplorasi cukup |
| Besar (15-30) | Kuat | 10-30 iterasi | Eksplorasi luas |
| Sangat besar (>30) | Agresif | >20 iterasi | Risiko terlalu jauh |

---

## Tahapan Local Search

Tahapan local search adalah tahapan **eksploitasi** untuk meningkatkan kualitas solusi. Tahapan ini paling kompleks dengan beberapa sub-fungsi yang saling berinteraksi.

### Pseudocode Utama

```
Algoritma 9: Local Search Phase
procedure LOCALSEARCHPHASE(newSol, eventList, thresholdValue, thresholdList)
    localSearch ← true
    currentPenalty ← calculatePenalty(newSol)
    localBest ← currentPenalty
    thresholdLocal ← currentPenalty
    improve ← false
    improveLocalBest ← false
    amountRemoved ← 1

    while localSearch do
        thresholdLocal ← UpdateThresholdLocal(thresholdLocal, 
                          thresholdList, currentPenalty, thresholdValue, improve)

        improveLocalBest ← ApplyHeuristicToAllEvents(eventList, thresholdLocal,
                            currentPenalty, localBest)

        if NOT improve then
            localSearch ← UpdateThresholdList(thresholdList, amountRemoved,
                           improveLocalBest, localBest, currentPenalty)
        end if
    end while

    useLocalBest()
end procedure
```

### Variabel Penting

| Variabel | Fungsi | Nilai Awal | Update |
|----------|--------|------------|--------|
| `localSearch` | Flag untuk melanjutkan/berhenti local search | `true` | Diubah oleh `UpdateThresholdList` |
| `currentPenalty` | Nilai penalti solusi saat ini dalam local search | Penalty dari `newSol` | Diperbarui di `ApplyHeuristicToAllEvents` |
| `localBest` | Nilai penalti terbaik selama local search | `currentPenalty` | Diperbarui jika solusi lebih baik ditemukan |
| `thresholdLocal` | Batas penerimaan solusi dalam local search | `currentPenalty` | Diperbarui di `UpdateThresholdLocal` |
| `improve` | Flag peningkatan solusi di `ApplyHeuristicToAllEvents` | `false` | Set `true` jika `currentPenalty < thresholdLocal` |
| `improveLocalBest` | Flag peningkatan `localBest` | `false` | Set `true` jika `localBest` diperbarui |
| `amountRemoved` | Jumlah elemen yang akan dihapus dari `thresholdList` | `1` | Bertambah jika tidak ada peningkatan |

### Alur Kerja

**Fase Inisialisasi:**
1. Set semua variabel ke nilai awal
2. Catat `currentPenalty` sebagai baseline
3. Set `localBest = currentPenalty` (belum ada peningkatan)

**Loop Local Search (sampai `localSearch = false`):**

```
Setiap iterasi:
  Step 1: UpdateThresholdLocal
    - Turunkan thresholdLocal secara bertahap
    - Tambah thresholdValue jika tidak ada peningkatan
  
  Step 2: ApplyHeuristicToAllEvents
    - Terapkan LLH pada semua event
    - Terima solusi jika ≤ thresholdLocal
    - Update localBest jika lebih baik
  
  Step 3: UpdateThresholdList (jika tidak ada improve)
    - Hapus elemen dari thresholdList
    - Evaluasi apakah lanjut atau berhenti
    - Kembali ke localBest jika terlalu jauh
```

**Finalisasi:**
- Gunakan `localBest` sebagai hasil akhir
- `newSol` diset menjadi solusi terbaik yang ditemukan

---

### Fungsi UpdateThresholdLocal()

Memperbarui nilai ambang batas lokal yang digunakan dalam penerimaan solusi.

#### Pseudocode

```
Algoritma 10: Update Threshold Local
procedure UPDATETHRESHOLDLOCAL(thresholdLocal, thresholdList, 
                                currentPenalty, thresholdValue, improve)
    if thresholdList is not empty then
        firstElement ← the first (smallest) element of thresholdList

        if thresholdLocal - firstElement > currentPenalty then
            thresholdLocal ← thresholdLocal - firstElement
        else
            thresholdLocal ← currentPenalty
        end if
    else
        thresholdLocal ← currentPenalty
    end if

    if not improve then
        thresholdLocal ← thresholdLocal + thresholdValue
    else
        improve ← false
    end if

    return thresholdLocal
end procedure
```

#### Cara Kerja

**1. Penurunan thresholdLocal (jika thresholdList tidak kosong):**

a) Ambil `firstElement` (nilai terkecil dari thresholdList)

b) Cek: `thresholdLocal - firstElement > currentPenalty`
   - **Jika YA**: Kurangi `thresholdLocal` dengan `firstElement`
   - **Jika TIDAK**: Set `thresholdLocal = currentPenalty`

**Contoh:**
```
Scenario A (Penurunan Normal):
  thresholdLocal = 110
  firstElement = 3
  currentPenalty = 105
  
  Cek: 110 - 3 = 107 > 105? YA
  Hasil: thresholdLocal = 107

Scenario B (Limit ke currentPenalty):
  thresholdLocal = 108
  firstElement = 5
  currentPenalty = 105
  
  Cek: 108 - 5 = 103 > 105? TIDAK (103 < 105)
  Hasil: thresholdLocal = 105 (tidak boleh lebih kecil dari currentPenalty)
```

**2. Jika thresholdList kosong:**
- Set `thresholdLocal = currentPenalty`
- Tidak ada elemen untuk mengurangi threshold
- Baseline reset ke penalty saat ini

**3. Pencegahan Local Optima:**

Cek variabel `improve`:

**Jika `improve = false` (tidak ada peningkatan):**
- Tambahkan `thresholdValue` ke `thresholdLocal`
- Memberikan ruang eksplorasi tambahan
- Mencegah stuck di local optima

```
Contoh:
  thresholdLocal = 105
  thresholdValue = 8
  improve = false
  
  Hasil: thresholdLocal = 105 + 8 = 113
```

**Jika `improve = true` (ada peningkatan):**
- Reset `improve = false` untuk iterasi berikutnya
- Tidak ada penambahan thresholdValue
- Lanjutkan penurunan normal

#### Tujuan

1. **Penurunan Bertahap:**
   - Mengurangi `thresholdLocal` secara perlahan untuk konvergensi
   - Menggunakan elemen terkecil dari `thresholdList` → penurunan smooth

2. **Pencegahan Stagnasi:**
   - Menambah nilai jika tidak ada peningkatan
   - Membuka peluang eksplorasi minimal

3. **Eksplorasi Minimal:**
   - Memberikan ruang untuk keluar dari local optima
   - Tetap dalam kontrol (tidak terlalu jauh dari currentPenalty)

#### Ilustrasi Perkembangan thresholdLocal

```
Iterasi Local Search:

thresholdList = [1, 2, 3, 5, 8, 12, 15]
thresholdValue = 7
currentPenalty awal = 100

Iterasi 1:
  - thresholdLocal = 100
  - firstElement = 1
  - 100 - 1 = 99 < 100? TIDAK → thresholdLocal = 100
  - improve = false → thresholdLocal = 100 + 7 = 107

Iterasi 2 (ada peningkatan, currentPenalty = 98):
  - thresholdLocal = 107
  - firstElement = 1
  - 107 - 1 = 106 > 98? YA → thresholdLocal = 106
  - improve = true → reset improve = false, tidak ada penambahan

Iterasi 3 (tidak ada peningkatan):
  - thresholdLocal = 106
  - firstElement = 1
  - 106 - 1 = 105 > 98? YA → thresholdLocal = 105
  - improve = false → thresholdLocal = 105 + 7 = 112

... dan seterusnya
```

---

### Fungsi ApplyHeuristicToAllEvents()

Menerapkan LLH pada semua kegiatan untuk meningkatkan solusi.

#### Pseudocode

```
Algoritma 11: Apply Heuristic to Events
procedure APPLYHEURISTICTOALLEVENTS(eventList, thresholdLocal, 
                                     currentPenalty, localBest)
    shuffle(eventList)
    improveLocalBest ← false

    for each event in eventList do
        chosenLLH ← selectRandomLLH()
        doLLH(chosenLLH, event)

        if isFeasible() then
            newPenalty ← calculatePenalty()

            if (newPenalty < thresholdLocal) OR 
               (newPenalty = thresholdLocal AND currentPenalty = thresholdLocal) then
                currentPenalty ← newPenalty

                if currentPenalty < thresholdLocal then
                    improve ← true
                end if

                if currentPenalty < localBest then
                    localBest ← currentPenalty
                    saveBestLocalSol()
                    improveLocalBest ← true
                end if
            else
                undoChanges(event)
            end if
        else
            undoChanges(event)
        end if
    end for

    return improveLocalBest
end procedure
```

#### Cara Kerja

**1. Persiapan:**
- **Shuffle `eventList`**: Acak urutan event untuk variasi pencarian
- Inisialisasi `improveLocalBest = false`

**2. Loop Setiap Kegiatan:**

Untuk setiap `event` dalam `eventList` (setelah diacak):

a) **Pilih dan Terapkan LLH:**
   - `chosenLLH` = pilih acak dari {Move, Swap, Kempe Chain}
   - Terapkan `chosenLLH` pada `event`

b) **Cek Feasibility:**

**Jika FEASIBLE:**
- Hitung `newPenalty`
- Evaluasi kriteria penerimaan (lihat detail di bawah)
- Update variabel jika diterima
- Undo jika ditolak

**Jika INFEASIBLE:**
- Undo changes langsung
- Lanjut ke event berikutnya

#### Kriteria Penerimaan Solusi (Dual Condition dengan OR)

Solusi diterima jika **SALAH SATU** kondisi berikut terpenuhi:

**Kondisi 1: `newPenalty < thresholdLocal`**
- Solusi lebih baik dari batas lokal
- Penerimaan normal (improving atau equal move)

**Kondisi 2: `newPenalty = thresholdLocal AND currentPenalty = thresholdLocal`**
- Solusi sama dengan batas lokal
- DAN penalty saat ini juga sama dengan batas lokal
- Mencegah cycling (lihat penjelasan di bawah)

**Jika Diterima:**

1. Update `currentPenalty = newPenalty`

2. Cek peningkatan dari threshold:
   ```
   if currentPenalty < thresholdLocal then
       improve ← true
   ```

3. Cek peningkatan dari localBest:
   ```
   if currentPenalty < localBest then
       localBest ← currentPenalty
       saveBestLocalSol()
       improveLocalBest ← true
   ```

**Jika Ditolak:**
- Undo changes
- Kembali ke kondisi sebelum LLH diterapkan

#### Mengapa Dua Kondisi dengan OR?

**Masalah: Cycling (Solusi Berputar-Putar)**

Tanpa kondisi kedua, solusi bisa kembali ke nilai yang sama berulang kali:

```
Contoh MASALAH (hanya kondisi 1):

thresholdLocal = 100
currentPenalty = 95 (sudah lebih baik dari threshold)

Iterasi 1: 
  Swap(event1, event3) → penalty 95 → 98 
  Cek: 98 < 100? YA → TERIMA

Iterasi 5: 
  Swap(event3, event1) → penalty 98 → 95 
  Cek: 95 < 100? YA → TERIMA (kembali ke kondisi awal!)

Iterasi 8:
  Swap(event1, event3) → penalty 95 → 98
  Cek: 98 < 100? YA → TERIMA (berputar lagi!)
```

**Solusi dengan Kondisi Kedua:**

```
Kondisi: (newPenalty = thresholdLocal AND currentPenalty = thresholdLocal)

Hanya aktif ketika currentPenalty TEPAT SAMA dengan thresholdLocal

Contoh:
thresholdLocal = 100
currentPenalty = 100 (tepat sama)

Iterasi 1:
  Move(event5) → penalty 100 → 100
  Cek kondisi 1: 100 < 100? TIDAK
  Cek kondisi 2: 100 = 100 AND 100 = 100? YA → TERIMA
  (Move lateral pada threshold boundary)

thresholdLocal = 100  
currentPenalty = 95 (lebih baik dari threshold)

Iterasi 2:
  Swap(event2) → penalty 95 → 100
  Cek kondisi 1: 100 < 100? TIDAK
  Cek kondisi 2: 100 = 100 AND 95 = 100? TIDAK → TOLAK
  (Tidak boleh memburuk kembali ke threshold jika sudah lebih baik)
```

**Interpretasi:**
- Kondisi 2 hanya berlaku di "boundary" (ketika tepat di threshold)
- Mencegah solusi naik kembali setelah sudah turun di bawah threshold
- Memungkinkan lateral move HANYA di boundary

#### Return Value

**Return `improveLocalBest`:**
- `true`: Jika `localBest` mengalami peningkatan
- `false`: Jika tidak ada peningkatan pada `localBest`

Digunakan oleh `UpdateThresholdList` untuk menentukan strategi selanjutnya.

#### Ilustrasi Eksekusi Lengkap

```
Kondisi Awal:
  eventList = [e1, e2, e3, ..., e100] (100 events)
  thresholdLocal = 105
  currentPenalty = 102
  localBest = 102
  
Setelah shuffle: [e37, e5, e91, ..., e23]

Event 1 (e37):
  - LLH: Swap
  - newPenalty = 101
  - Cek: 101 < 105? YA → TERIMA
  - currentPenalty = 101
  - improve = true (karena 101 < 105)
  - Cek localBest: 101 < 102? YA
    - localBest = 101
    - saveBestLocalSol()
    - improveLocalBest = true

Event 2 (e5):
  - LLH: Move
  - Infeasible → TOLAK, undo

Event 3 (e91):
  - LLH: Kempe Chain
  - newPenalty = 100
  - Cek: 100 < 105? YA → TERIMA
  - currentPenalty = 100
  - improve tetap true
  - Cek localBest: 100 < 101? YA
    - localBest = 100
    - saveBestLocalSol()
    - improveLocalBest tetap true

... (lanjut untuk semua event)

Return: improveLocalBest = true (karena localBest berhasil diupdate)
```

---

### Fungsi UpdateThresholdList()

Memperbarui `thresholdList` dengan menghapus elemen untuk menurunkan `thresholdValue`.

#### Pseudocode

```
Algoritma 12: Update Threshold List
procedure UPDATETHRESHOLDLIST(thresholdList, amountRemoved, improveLocalBest,
                               localBest, currentPenalty)
    if thresholdList is empty then
        return false  // Indicate the end of the local search phase
    else
        if improveLocalBest then
            amountRemoved ← 1
            improveLocalBest ← false
        else
            amountRemoved ← amountRemoved + 1
        end if

        for k = 1 to amountRemoved do
            if thresholdList is empty then
                break  // No more thresholds to remove
            end if
            removeLastElement(thresholdList)
        end for

        if thresholdList is not empty AND 
           currentPenalty > localBest + getRandomQuartile1(thresholdList) then
            useLocalBest()
            currentPenalty ← localBest
        end if

        if thresholdList has more than one element then
            thresholdValue ← updateThreshold(thresholdList)
        else
            thresholdValue ← 0
        end if
    end if

    return true  // Continue the local search
end procedure
```

#### Cara Kerja

**1. Cek Kondisi Berhenti:**

```
if thresholdList is empty then
    return false
```

- Jika `thresholdList` kosong → **BERHENTI** local search
- Tidak ada elemen untuk dihapus → algoritma sudah converge
- Return `false` mengakhiri loop local search

**2. Atur Jumlah Penghapusan (`amountRemoved`):**

**Jika ada peningkatan localBest (`improveLocalBest = true`):**
```
amountRemoved ← 1
improveLocalBest ← false
```
- Reset ke 1 (penghapusan minimal)
- Ada progress, tidak perlu akselerasi

**Jika tidak ada peningkatan:**
```
amountRemoved ← amountRemoved + 1
```
- Tambah jumlah penghapusan
- **Strategi Akselerasi**: Semakin lama stuck, semakin agresif penghapusan

**Contoh Progres:**
```
Iterasi 1: improveLocalBest = true → amountRemoved = 1
Iterasi 2: improveLocalBest = false → amountRemoved = 2
Iterasi 3: improveLocalBest = false → amountRemoved = 3
Iterasi 4: improveLocalBest = true → amountRemoved = 1 (reset)
Iterasi 5: improveLocalBest = false → amountRemoved = 2
```

**3. Hapus Elemen:**

```
for k = 1 to amountRemoved do
    if thresholdList is empty then
        break
    end if
    removeLastElement(thresholdList)
end for
```

- Hapus sebanyak `amountRemoved` elemen
- Penghapusan dari **nilai terbesar** (elemen terakhir setelah sorting)
- Break jika thresholdList kosong (safety check)

**Contoh:**
```
Iterasi 1 (amountRemoved = 1):
  thresholdList = [1, 2, 3, 5, 8, 12, 15, 20]
  Hapus 20
  thresholdList = [1, 2, 3, 5, 8, 12, 15]

Iterasi 2 (amountRemoved = 2):
  thresholdList = [1, 2, 3, 5, 8, 12, 15]
  Hapus 15, kemudian 12
  thresholdList = [1, 2, 3, 5, 8]

Iterasi 3 (amountRemoved = 3):
  thresholdList = [1, 2, 3, 5, 8]
  Hapus 8, 5, 3
  thresholdList = [1, 2]
```

**4. Strategi Kembali ke LocalBest (Safety Net):**

```
if thresholdList is not empty AND 
   currentPenalty > localBest + getRandomQuartile1(thresholdList) then
    useLocalBest()
    currentPenalty ← localBest
end if
```

**Kondisi:**
- `thresholdList` masih ada (tidak kosong)
- `currentPenalty` terlalu jauh dari `localBest`

**Batas "Terlalu Jauh":**
```
localBest + random nilai dari quartile pertama thresholdList
```

**Fungsi `getRandomQuartile1(thresholdList)`:**
- Ambil 25% elemen terkecil dari thresholdList
- Pilih salah satu secara acak
- Memberikan batas yang kecil (tidak terlalu toleran)

**Contoh:**
```
localBest = 95
thresholdList = [1, 2, 3, 5, 8, 12, 15, 20]
Quartile 1 = [1, 2] (25% elemen terkecil)
Random dari Q1 = 2 (misalnya)

Batas = 95 + 2 = 97

currentPenalty = 99
Cek: 99 > 97? YA → Kembali ke localBest
currentPenalty diset ke 95
Solusi dikembalikan ke kondisi localBest
```

**Tujuan:**
- Mencegah solusi terlalu jauh dari kondisi terbaik
- Safety mechanism agar tidak lost di region buruk
- Gunakan nilai kecil (Q1) agar threshold ketat

**5. Update thresholdValue:**

**Jika thresholdList masih memiliki > 1 elemen:**
```
thresholdValue ← updateThreshold(thresholdList)
thresholdValue = rata-rata(thresholdList)
```

**Jika thresholdList hanya 0-1 elemen:**
```
thresholdValue ← 0
```

**Alasan set ke 0:**
- Tidak cukup data untuk menghitung rata-rata yang meaningful
- Fokus penuh pada eksploitasi (hanya terima solusi lebih baik)
- Mendekati konvergensi final

**6. Return:**

```
return true
```

- Lanjutkan local search
- Loop local search akan berlanjut ke iterasi berikutnya

#### Tujuan dan Strategi

**1. Konvergensi Bertahap:**
- Mengurangi elemen untuk menurunkan `thresholdValue`
- Penurunan smooth, tidak tiba-tiba

**2. Akselerasi Adaptif:**
- Jika tidak ada peningkatan → hapus lebih banyak elemen
- Jika ada peningkatan → reset ke penghapusan minimal
- Menyesuaikan kecepatan konvergensi dengan progress

**3. Safety Net:**
- Kembalikan ke `localBest` jika solusi terlalu jauh
- Gunakan quartile 1 untuk threshold ketat
- Mencegah pencarian tersesat

**4. Terminasi Natural:**
- Berhenti ketika `thresholdList` kosong
- Tidak perlu kondisi berhenti artifisial
- Konvergensi alami tanpa parameter

#### Ilustrasi Lengkap Dinamika UpdateThresholdList

```
=== Skenario Kompleks ===

Kondisi Awal:
  thresholdList = [1, 2, 3, 5, 8, 12, 15, 20, 25, 30]
  thresholdValue = 12.1
  localBest = 95
  currentPenalty = 95
  amountRemoved = 1

--- Iterasi 1 ---
  ApplyHeuristicToAllEvents: improveLocalBest = true, localBest = 93
  UpdateThresholdList:
    - improveLocalBest = true → amountRemoved = 1, reset
    - Hapus 30
    - thresholdList = [1, 2, 3, 5, 8, 12, 15, 20, 25]
    - currentPenalty = 93, localBest = 93
    - Cek safety: 93 > 93 + random(1,2)? TIDAK → Tidak kembali
    - thresholdValue = rata-rata = 10.1
  Return: true (lanjut)

--- Iterasi 2 ---
  ApplyHeuristicToAllEvents: improveLocalBest = false, localBest = 93
  UpdateThresholdList:
    - improveLocalBest = false → amountRemoved = 2
    - Hapus 25, 20
    - thresholdList = [1, 2, 3, 5, 8, 12, 15]
    - currentPenalty = 94 (memburuk sedikit)
    - Cek safety: 94 > 93 + random(1,2)? TIDAK → Tidak kembali
    - thresholdValue = rata-rata = 6.6
  Return: true (lanjut)

--- Iterasi 3 ---
  ApplyHeuristicToAllEvents: improveLocalBest = false, localBest = 93
  UpdateThresholdList:
    - improveLocalBest = false → amountRemoved = 3
    - Hapus 15, 12, 8
    - thresholdList = [1, 2, 3, 5]
    - currentPenalty = 95
    - Cek safety: 95 > 93 + random(1,2)? YA → KEMBALI ke localBest
    - currentPenalty = 93
    - useBestLocalSol()
    - thresholdValue = rata-rata = 2.75
  Return: true (lanjut)

--- Iterasi 4 ---
  ApplyHeuristicToAllEvents: improveLocalBest = true, localBest = 91
  UpdateThresholdList:
    - improveLocalBest = true → amountRemoved = 1, reset
    - Hapus 5
    - thresholdList = [1, 2, 3]
    - currentPenalty = 91, localBest = 91
    - Cek safety: 91 > 91 + random(1,2)? TIDAK
    - thresholdValue = rata-rata = 2.0
  Return: true (lanjut)

--- Iterasi 5 ---
  ApplyHeuristicToAllEvents: improveLocalBest = false
  UpdateThresholdList:
    - improveLocalBest = false → amountRemoved = 2
    - Hapus 3, 2
    - thresholdList = [1]
    - currentPenalty = 91
    - Cek safety: tidak dijalankan (hanya 1 elemen)
    - thresholdValue = 0 (karena hanya 1 elemen)
  Return: true (lanjut)

--- Iterasi 6 ---
  ApplyHeuristicToAllEvents: improveLocalBest = false
  UpdateThresholdList:
    - improveLocalBest = false → amountRemoved = 3
    - Hapus 1
    - thresholdList = [] (KOSONG)
  Return: false (STOP local search)

Local Search BERAKHIR
Hasil: localBest = 91
```

---

## Tahapan Move Acceptance

Tahapan move acceptance menentukan apakah solusi baru dari local search akan diterima untuk iterasi berikutnya.

### Pseudocode

```
Algoritma 13: Move Acceptance
procedure MOVEACCEPTANCE(newSol, currentSol, bestSol, eventList)
    newPenalty ← calculatePenalty(newSol)
    currentPenalty ← calculatePenalty(currentSol)
    bestPenalty ← calculatePenalty(bestSol)

    if currentPenalty ≥ newPenalty then
        currentSol ← newSol

        if newPenalty < bestPenalty then
            bestSol ← newSol
        end if
    else
        thresholdList ← calculateThreshold(bestSol, eventList)

        if newPenalty < bestPenalty + GetRandomFromTopQuartile(thresholdList) then
            currentSol ← newSol
        else
            useBestSol()
        end if
    end if
end procedure
```

### Cara Kerja

**1. Hitung Penalti** dari tiga solusi:
```
newPenalty = penalty dari newSol (hasil local search)
currentPenalty = penalty dari currentSol (solusi saat ini)
bestPenalty = penalty dari bestSol (solusi terbaik yang pernah ditemukan)
```

**2. Pengecekan Pertama - Penerimaan Deterministik:**

**Kondisi: `currentPenalty ≥ newPenalty`**

Jika solusi baru **lebih baik atau sama** dengan solusi saat ini:

a) **Terima solusi baru:**
```
currentSol ← newSol
```

b) **Cek apakah solusi terbaik baru:**
```
if newPenalty < bestPenalty then
    bestSol ← newSol
```

**Contoh:**
```
currentPenalty = 100
newPenalty = 95
bestPenalty = 90

Cek: 100 ≥ 95? YA
  → currentSol = newSol
  → Cek: 95 < 90? TIDAK
  → bestSol tidak diupdate
```

**3. Pengecekan Kedua - Penerimaan Probabilistik:**

**Kondisi: `currentPenalty < newPenalty`**

Jika solusi baru **lebih buruk** dari solusi saat ini:

a) **Hitung ulang `thresholdList`:**
```
thresholdList ← calculateThreshold(bestSol, eventList)
```

**PENTING**: Dihitung dari `bestSol`, bukan `currentSol`
- Menggunakan kondisi solusi terbaik sebagai referensi
- Mencerminkan jangkauan di sekitar solusi optimal

b) **Kriteria Penerimaan Probabilistik:**
```
if newPenalty < bestPenalty + GetRandomFromTopQuartile(thresholdList) then
    currentSol ← newSol
else
    useBestSol()
end if
```

**Fungsi `GetRandomFromTopQuartile(thresholdList)`:**
- Ambil 25% elemen **terkecil** dari thresholdList (quartile 1)
- Pilih salah satu secara acak
- Memberikan nilai kecil untuk threshold ketat

**Jika TERIMA:**
```
currentSol ← newSol
```
- Memberikan peluang eksplorasi
- Solusi lebih buruk diterima dengan batas

**Jika TOLAK:**
```
useBestSol()
currentSol ← bestSol
```
- **Strategi Kembali**: Gunakan solusi terbaik
- Mencegah terlalu jauh dari optimal

**Contoh:**
```
currentPenalty = 95
newPenalty = 100 (lebih buruk)
bestPenalty = 90

Cek: 95 ≥ 100? TIDAK (solusi lebih buruk)

Hitung thresholdList dari bestSol:
  thresholdList = [1, 2, 3, 5, 8, 12, 15, 20]
  Quartile 1 = [1, 2] (25% terkecil)
  Random dari Q1 = 2 (misalnya)

Batas penerimaan = 90 + 2 = 92

Cek: 100 < 92? TIDAK
  → TOLAK
  → currentSol = bestSol (kembali ke solusi terbaik)
```

### Strategi dan Tujuan

#### 1. Penerimaan Deterministik (Kondisi Pertama)

**Karakteristik:**
- Solusi lebih baik/sama **selalu diterima**
- Tidak ada randomness
- Memastikan peningkatan kualitas solusi

**Tujuan:**
- Greedy acceptance untuk solusi yang membaik
- Update bestSol jika solusi terbaik baru ditemukan
- Eksploitasi maksimal

#### 2. Penerimaan Probabilistik (Kondisi Kedua)

**Karakteristik:**
- Solusi lebih buruk **mungkin diterima**
- Batas penerimaan: `bestPenalty + random(quartile1(thresholdList))`
- Probabilistik karena menggunakan random dari Q1

**Batas Penerimaan yang Ketat:**

| thresholdList | Quartile 1 | Random Range | Batas Typical |
|---------------|------------|--------------|---------------|
| [1,2,3,5,8,12,15,20] | [1,2] | 1-2 | bestPenalty + 1-2 |
| [2,4,6,10,15,20,30,40] | [2,4] | 2-4 | bestPenalty + 2-4 |
| [5,10,15,20,30,50,80,100] | [5,10] | 5-10 | bestPenalty + 5-10 |

**Tujuan:**
- Memberikan ruang eksplorasi **terbatas**
- Hanya solusi yang "sedikit lebih buruk" yang mungkin diterima
- Menjaga fokus pada eksploitasi

**Probabilitas Penerimaan:**
```
Jarak dari bestPenalty | Probabilitas Diterima
------------------------|------------------------
0-Q1                    | Tinggi (~75-100%)
Q1-median               | Rendah (~10-30%)
> median                | Sangat rendah (~0-5%)
```

#### 3. Strategi Kembali ke Solusi Terbaik

**Masalah yang Diatasi:**

Tanpa strategi ini:
```
Iterasi 100: Best = 30 (ditemukan)
Iterasi 150: Current = 35 (diterima untuk eksplorasi)
Iterasi 200: Current = 38
Iterasi 300: Current = 42
...
Iterasi 1000: Current = 45 (sangat jauh dari best!)
```

Solusi bisa terus menjauhi optimal hingga akhir algoritma.

**Dengan Strategi AT-ILS:**

```
Iterasi 100: Best = 30
Iterasi 150: Current = 35 (diterima)
Iterasi 200: newSol = 40
  → Cek: 40 < 30 + random(Q1)? Misalnya 40 < 32? TIDAK
  → TOLAK, currentSol = bestSol = 30 (kembali ke solusi terbaik)
Iterasi 201: Mulai dari Best = 30 lagi
```

**Keuntungan:**
- Mencegah pencarian bergerak terlalu jauh
- Selalu ada kesempatan kembali ke region optimal
- Eksplorasi tetap ada tapi terkontrol

#### 4. Penggunaan Quartile Pertama

**Mengapa Q1, bukan rata-rata atau Q3?**

| Statistik | Nilai | Karakteristik | Dampak |
|-----------|-------|---------------|--------|
| Quartile 1 | Kecil | Batas ketat | Fokus eksploitasi |
| Median (Q2) | Sedang | Batas moderat | Seimbang |
| Quartile 3 | Besar | Batas longgar | Terlalu eksploratif |
| Rata-rata | Sedang-besar | Bisa bias | Tidak stabil |

**Pilihan Q1:**
- Penerimaan solusi buruk **sangat terbatas**
- Hanya solusi yang "sedikit lebih buruk" yang mungkin diterima
- Menjaga fokus pada eksploitasi
- Sesuai dengan tujuan tahapan move acceptance (memutuskan solusi untuk iterasi berikutnya)

**Contoh Numerik:**
```
thresholdList = [1, 2, 3, 5, 8, 12, 15, 20, 25, 30, 40, 50]

Q1 = [1, 2, 3] (25% terkecil)
Q2 (median) = 13.5
Q3 = [30, 40, 50]
Rata-rata = 17.6

bestPenalty = 85

Batas dengan Q1: 85 + random(1,2,3) = 86-88 (KETAT)
Batas dengan median: 85 + 13.5 = 98.5 (LONGGAR)
Batas dengan rata-rata: 85 + 17.6 = 102.6 (SANGAT LONGGAR)
```

### Perbandingan Strategi Move Acceptance

#### AT-ILS vs Simulated Annealing

| Aspek | AT-ILS | Simulated Annealing |
|-------|--------|---------------------|
| **Penerimaan Improving** | Selalu diterima | Selalu diterima |
| **Penerimaan Worsening** | Probabilistik (Q1) | Probabilistik (exp(-Δ/T)) |
| **Batas Penerimaan** | Adaptif (dari data) | Parameter (temperature) |
| **Kembali ke Best** | ✓ Ada | ✗ Tidak ada |
| **Parameter** | ✓ Tanpa parameter | ✗ Perlu T₀, cooling rate |
| **Adaptivitas** | ✓✓ Sangat adaptif | ~ Perlu tuning per problem |

#### AT-ILS vs Great Deluge

| Aspek | AT-ILS | Great Deluge |
|-------|--------|--------------|
| **Threshold** | Adaptif (dari data) | Fixed decay (rain speed) |
| **Penerimaan** | Dual condition | Single threshold |
| **Kembali ke Best** | ✓ Ada | ✗ Tidak ada |
| **Konvergensi** | Natural (thresholdList habis) | Linear (water level → 0) |
| **Parameter** | ✓ Tanpa parameter | ✗ Perlu rain speed |

#### AT-ILS vs LAHC

| Aspek | AT-ILS | LAHC |
|-------|--------|------|
| **History** | Single best solution | Array of L solutions |
| **Penerimaan** | vs bestPenalty + Q1 | vs history[iteration - L] |
| **Eksplorasi** | ✓ Seimbang | ~ Terbatas |
| **Kembali ke Best** | ✓ Ada | ✗ Tidak ada |
| **Parameter** | ✓ Tanpa parameter | ✗ Perlu L (history length) |

### Ilustrasi Lengkap Skenario Move Acceptance

```
=== Skenario 1: Improving Solution (Deterministik) ===

Input:
  currentPenalty = 105
  newPenalty = 98
  bestPenalty = 95

Eksekusi:
  Cek: 105 ≥ 98? YA
    → currentSol = newSol
    → Cek: 98 < 95? TIDAK
    → bestSol tidak diupdate
  
Hasil:
  currentSol = newSol (penalty 98)
  bestSol = unchanged (penalty 95)

=== Skenario 2: New Best Solution ===

Input:
  currentPenalty = 98
  newPenalty = 92
  bestPenalty = 95

Eksekusi:
  Cek: 98 ≥ 92? YA
    → currentSol = newSol
    → Cek: 92 < 95? YA
    → bestSol = newSol
  
Hasil:
  currentSol = newSol (penalty 92)
  bestSol = newSol (penalty 92) ← UPDATE!

=== Skenario 3: Worsening, Diterima (Probabilistik) ===

Input:
  currentPenalty = 98
  newPenalty = 102 (lebih buruk)
  bestPenalty = 95

Eksekusi:
  Cek: 98 ≥ 102? TIDAK (solusi lebih buruk)
  
  Hitung thresholdList dari bestSol:
    thresholdList = [1, 2, 3, 5, 8, 12, 15, 20]
    Q1 = [1, 2]
    Random = 2
  
  Batas = 95 + 2 = 97
  
  Cek: 102 < 97? TIDAK
    → TOLAK
    → useBestSol()
    → currentSol = bestSol

Hasil:
  currentSol = bestSol (penalty 95) ← Kembali ke best!
  bestSol = unchanged (penalty 95)

=== Skenario 4: Worsening, Diterima (Eksplorasi Terbatas) ===

Input:
  currentPenalty = 95
  newPenalty = 98 (lebih buruk tapi masih dekat)
  bestPenalty = 92

Eksekusi:
  Cek: 95 ≥ 98? TIDAK
  
  Hitung thresholdList dari bestSol:
    thresholdList = [2, 3, 5, 8, 12, 15, 20, 30]
    Q1 = [2, 3]
    Random = 3
  
  Batas = 92 + 3 = 95
  
  Cek: 98 < 95? TIDAK... tunggu, 98 > 95
    → Sebenarnya, cek lagi
    → Batas = 92 + random(2,3)
    → Misalnya random = 3 → batas = 95
    → 98 < 95? TIDAK
    → TOLAK
  
  Alternatif (jika random = 8 dari batas atas Q1):
    Batas = 92 + 8 = 100
    98 < 100? YA
      → TERIMA
      → currentSol = newSol

Hasil (jika diterima):
  currentSol = newSol (penalty 98)
  bestSol = unchanged (penalty 92)
  
Probabilitas penerimaan: ~30-50% (tergantung Q1 range)
```

---

## Low Level Heuristic (LLH)

LLH adalah **operator** yang digunakan untuk memodifikasi solusi. AT-ILS menggunakan tiga LLH yang bersifat **generic** dan dapat diterapkan pada berbagai domain penjadwalan.

### Pemilihan LLH

**Strategi: Random Selection**
```python
def selectRandomLLH():
    return random.choice([Move, Swap, KempeChain])
```

**Distribusi:**
- Move: 33.33% peluang
- Swap: 33.33% peluang
- Kempe Chain: 33.33% peluang

**Rasionalisasi:**
1. **Simplicity**: Tidak perlu strategi pemilihan yang kompleks
2. **Generality**: Bekerja baik di berbagai domain tanpa tuning
3. **Diversity**: Ketiga LLH memberikan jenis eksplorasi yang berbeda

---

### 1. Move Operator

**Definisi**: Memindahkan satu kegiatan dari slot waktu saat ini ke slot waktu lain.

#### Ilustrasi

**Penjadwalan Mata Kuliah:**
```
SEBELUM:
Senin Slot 1:    [Aljabar]
Senin Slot 2:    [Fisika Teori]
Senin Slot 3:    [Fisika Praktek]
Senin Slot 4:    [Statistika]
Selasa Slot 1:   [ ]
Selasa Slot 2:   [ ]
Selasa Slot 3:   [ ]
Selasa Slot 4:   [ ]

Move(Aljabar, Selasa Slot 4)

SESUDAH:
Senin Slot 1:    [ ]             ← Kosong
Senin Slot 2:    [Fisika Teori]
Senin Slot 3:    [Fisika Praktek]
Senin Slot 4:    [Statistika]
Selasa Slot 1:   [ ]
Selasa Slot 2:   [ ]
Selasa Slot 3:   [ ]
Selasa Slot 4:   [Aljabar]       ← Dipindahkan
```

**Penjadwalan Ujian:**
```
SEBELUM:
Slot 1 (Senin 08:00):   Ujian Matematika
Slot 2 (Senin 10:00):   Ujian Fisika
Slot 3 (Senin 13:00):   Ujian Kimia

Move(Ujian Matematika, Slot 5 (Selasa 08:00))

SESUDAH:
Slot 1 (Senin 08:00):   [ ]
Slot 2 (Senin 10:00):   Ujian Fisika
Slot 3 (Senin 13:00):   Ujian Kimia
Slot 5 (Selasa 08:00):  Ujian Matematika
```

**Penjadwalan Kompetisi Olahraga:**
```
SEBELUM:
Slot 1:  Tim A vs Tim B (Home)
Slot 2:  Tim C vs Tim D (Home)

Move(Tim A vs Tim B, Slot 5)

SESUDAH:
Slot 1:  [ ]
Slot 2:  Tim C vs Tim D (Home)
Slot 5:  Tim A vs Tim B (Home)
```

#### Karakteristik

**Keuntungan:**
- **Paling sederhana**: Hanya satu kegiatan yang berubah
- **Cepat**: Kompleksitas rendah
- **Lokal**: Tidak mempengaruhi kegiatan lain (kecuali ada konflik)

**Kekurangan:**
- **Terbatas**: Hanya bisa explore neighborhood yang dekat
- **Konflik**: Bisa menyebabkan pelanggaran hard constraint di slot tujuan

**Cocok untuk:**
- Eksplorasi lokal
- Fine-tuning solusi yang sudah baik
- Dataset dengan constraint yang tidak terlalu ketat

#### Implementasi Teknis

**Pseudocode:**
```
procedure MOVE(event, targetTimeSlot)
    currentTimeSlot ← getTimeSlot(event)
    
    if currentTimeSlot = targetTimeSlot then
        return false  // Tidak ada perubahan
    end if
    
    removeFromTimeSlot(event, currentTimeSlot)
    addToTimeSlot(event, targetTimeSlot)
    
    if not checkFeasibility() then
        undoChanges()
        return false
    end if
    
    return true
end procedure
```

**Kompleksitas:**
- Time: O(1) untuk move, O(n) untuk feasibility check
- Space: O(1)

---

### 2. Swap Operator

**Definisi**: Menukar posisi jadwal antara dua kegiatan.

#### Ilustrasi

**Penjadwalan Mata Kuliah:**
```
SEBELUM:
Senin Slot 1:    [Aljabar]
Senin Slot 2:    [Fisika Teori]
Senin Slot 3:    [Fisika Praktek]
Senin Slot 4:    [Statistika]
Selasa Slot 4:   [Database]

Swap(Aljabar, Database)

SESUDAH:
Senin Slot 1:    [Database]      ← Ditukar
Senin Slot 2:    [Fisika Teori]
Senin Slot 3:    [Fisika Praktek]
Senin Slot 4:    [Statistika]
Selasa Slot 4:   [Aljabar]       ← Ditukar
```

**Penjadwalan Ujian:**
```
SEBELUM:
Slot 1: Ujian A (100 mahasiswa)
Slot 2: Ujian B (50 mahasiswa)

Swap(Ujian A, Ujian B)

SESUDAH:
Slot 1: Ujian B (50 mahasiswa)
Slot 2: Ujian A (100 mahasiswa)
```

**Penjadwalan Kompetisi Olahraga:**
```
SEBELUM:
Slot 1:  Tim A vs Tim B (Home for A)
Slot 5:  Tim C vs Tim D (Away for C)

Swap(Tim A vs Tim B, Tim C vs Tim D)

SESUDAH:
Slot 1:  Tim C vs Tim D (Away for C)
Slot 5:  Tim A vs Tim B (Home for A)
```

#### Karakteristik

**Keuntungan:**
- **Tidak mengubah jumlah slot terisi**: Total assignment tetap
- **Eksplorasi kombinatorial**: Mencoba berbagai kombinasi
- **Efektif untuk optimasi soft constraint**: Menggeser kegiatan untuk mengurangi penalty

**Kekurangan:**
- **Dua kegiatan terpengaruh**: Lebih kompleks dari Move
- **Risiko konflik lebih tinggi**: Kedua slot bisa mengalami konflik

**Cocok untuk:**
- Optimasi soft constraint (pemerataan, preferensi)
- Dataset dengan banyak kegiatan terjadwal
- Mencari kombinasi yang lebih baik tanpa mengubah struktur

#### Implementasi Teknis

**Pseudocode:**
```
procedure SWAP(event1, event2)
    timeSlot1 ← getTimeSlot(event1)
    timeSlot2 ← getTimeSlot(event2)
    
    if timeSlot1 = timeSlot2 then
        return false  // Kedua event di slot yang sama
    end if
    
    removeFromTimeSlot(event1, timeSlot1)
    removeFromTimeSlot(event2, timeSlot2)
    
    addToTimeSlot(event1, timeSlot2)
    addToTimeSlot(event2, timeSlot1)
    
    if not checkFeasibility() then
        undoChanges()
        return false
    end if
    
    return true
end procedure
```

**Kompleksitas:**
- Time: O(1) untuk swap, O(n) untuk feasibility check
- Space: O(1)

---

### 3. Kempe Chain Operator

**Definisi**: Memindahkan grup kegiatan yang saling terkait untuk memenuhi constraint ketergantungan.

#### Konsep Kempe Chain

**Asal Usul:**
- Dari graph coloring theory
- Digunakan untuk menjaga constraint antar-vertex yang saling terhubung

**Dalam Konteks Penjadwalan:**
- Grup kegiatan yang memiliki **dependency** atau **constraint bersama**
- Harus dipindahkan bersama-sama agar tetap feasible

#### Ilustrasi

**Penjadwalan Mata Kuliah:**

**Kondisi Awal:**
```
Senin Slot 1:   [Aljabar]
Senin Slot 2:   [ ]
Selasa Slot 1:  [Fisika Teori]
Selasa Slot 2:  [Fisika Praktek]

Hard Constraint: 
  Fisika Teori dan Fisika Praktek HARUS di hari yang sama
```

**Masalah dengan Swap Biasa:**
```
Swap(Aljabar, Fisika Teori)

Senin Slot 1:   [Fisika Teori]
Selasa Slot 1:  [Aljabar]
Selasa Slot 2:  [Fisika Praktek]

❌ INFEASIBLE! 
   (Fisika Teori dan Praktek di hari berbeda)
```

**Dengan Kempe Chain:**
```
KempeChain(Aljabar, {Fisika Teori, Fisika Praktek})

Identifikasi grup:
  - Aljabar (standalone)
  - {Fisika Teori, Fisika Praktek} (linked group)

Tukar Aljabar dengan Grup:

Senin Slot 1:   [Fisika Teori]      ← Pindah bersama
Senin Slot 2:   [Fisika Praktek]    ← Ikut pindah
Selasa Slot 1:  [Aljabar]           ← Pindah ke Selasa

✓ FEASIBLE! 
  (Fisika Teori dan Praktek tetap di hari yang sama)
```

**Penjadwalan Kompetisi Olahraga:**

**Kondisi:**
```
Slot 1:  Tim A vs Tim B
Slot 2:  Tim A vs Tim C  ← Linked (Tim A main 2x berturut-turut, violated)
Slot 5:  Tim D vs Tim E
```

**Kempe Chain:**
```
Identifikasi: Tim A linked dengan {Tim B, Tim C}

Move Tim A chain to Slot 5:
Slot 1:  [ ]
Slot 2:  [ ]
Slot 5:  Tim A vs Tim B
Slot 6:  Tim A vs Tim C (buat slot baru jika perlu)
Slot 7:  Tim D vs Tim E (geser)
```

#### Cara Kerja Kempe Chain (Detail)

**Algoritma:**

1. **Identifikasi kegiatan target** (yang akan dipindahkan)

2. **Identifikasi grup terkait** (kegiatan yang harus ikut)
   - Cari kegiatan dengan constraint dependency
   - Buat chain/cluster berdasarkan dependency graph

3. **Evaluasi slot tujuan**
   - Cek apakah seluruh chain bisa ditempatkan
   - Cari konflik di slot tujuan

4. **Pindahkan seluruh chain**
   - Move semua kegiatan dalam chain secara bersamaan
   - Maintain constraint antar-kegiatan dalam chain

5. **Cek feasibility**
   - Pastikan constraint terpenuhi
   - Undo jika infeasible

**Pseudocode:**
```
procedure KEMPECHAIN(targetEvent, targetTimeSlot)
    chain ← identifyChain(targetEvent)
    
    if not canAccommodateChain(chain, targetTimeSlot) then
        return false
    end if
    
    originalPositions ← savePositions(chain)
    
    for each event in chain do
        removeFromCurrentTimeSlot(event)
    end for
    
    for each event in chain do
        addToTimeSlot(event, computeNewTimeSlot(event, targetTimeSlot))
    end for
    
    if not checkFeasibility() then
        restorePositions(chain, originalPositions)
        return false
    end if
    
    return true
end procedure

procedure IDENTIFYCHAIN(event)
    chain ← {event}
    toExplore ← {event}
    
    while toExplore is not empty do
        current ← toExplore.pop()
        
        for each relatedEvent in getRelatedEvents(current) do
            if relatedEvent not in chain then
                chain.add(relatedEvent)
                toExplore.add(relatedEvent)
            end if
        end for
    end while
    
    return chain
end procedure
```

#### Karakteristik

**Keuntungan:**
- **Mempertahankan constraint antar-kegiatan**: Dependency tetap terjaga
- **Eksplorasi struktural**: Bisa explore region yang tidak bisa dicapai Move/Swap
- **Esensial untuk dataset dengan banyak dependency**: ITC 2019, ITC 2021

**Kekurangan:**
- **Paling kompleks**: Perlu identify chain dan move multiple events
- **Lebih lambat**: Kompleksitas tinggi
- **Risiko infeasibility tinggi**: Banyak event yang terpengaruh

**Cocok untuk:**
- Dataset dengan hard constraint dependency
- Penjadwalan dengan constraint "same day", "consecutive", "linked"
- ITC 2019 (course timetabling dengan constraint kompleks)
- ITC 2021 (sports timetabling dengan dependency antar-game)

**Tidak perlu untuk:**
- Toronto Benchmark (uncapacitated, no dependency)
- Dataset sederhana tanpa dependency

#### Implementasi Teknis

**Kompleksitas:**
- Time: O(k × n) dimana k = ukuran chain, n = feasibility check
- Space: O(k) untuk menyimpan chain

**Optimasi:**
- Cache dependency graph untuk avoid recomputation
- Limit max chain size untuk performa

#### Contoh Dependency Graph

```
Penjadwalan Mata Kuliah:

Event Dependencies:
  E1 (Fisika Teori) → linked to → E2 (Fisika Praktek)
  E3 (Lab A Grup 1) → linked to → E4 (Lab A Grup 2)
  E5 (Seminar) → standalone

Graph:
  E1 ─── E2  (chain 1)
  E3 ─── E4  (chain 2)
  E5         (single)

Jika Move E1:
  → Harus ikut move E2 (chain 1)
  
Jika Move E3:
  → Harus ikut move E4 (chain 2)
  
Jika Move E5:
  → Single move (tidak ada chain)
```

---

### Perbandingan Ketiga LLH

| Aspek | Move | Swap | Kempe Chain |
|-------|------|------|-------------|
| **Kompleksitas** | Sederhana | Sedang | Kompleks |
| **Events Terpengaruh** | 1 | 2 | k (bisa banyak) |
| **Jenis Eksplorasi** | Lokal | Kombinatorial | Struktural |
| **Kecepatan** | Cepat | Sedang | Lambat |
| **Risiko Konflik** | Rendah | Sedang | Tinggi |
| **Maintenance Dependency** | ✗ | ✗ | ✓ |
| **Efektif untuk** | Fine-tuning | Optimasi soft | Dataset kompleks |
| **Time Complexity** | O(1) | O(1) | O(k) |

### Distribusi Penggunaan LLH dalam AT-ILS

**Observasi dari Eksperimen:**

| Dataset | Move Usage | Swap Usage | Kempe Chain Usage |
|---------|------------|------------|-------------------|
| Toronto | ~35% | ~40% | ~25% |
| ITC 2019 | ~30% | ~30% | ~40% |
| ITC 2021 | ~25% | ~30% | ~45% |

**Interpretasi:**
- Dataset sederhana (Toronto): Move dan Swap dominan
- Dataset kompleks (ITC 2021): Kempe Chain lebih sering berhasil
- Random selection tetap efektif karena adaptif dengan success rate

---

## Hasil Implementasi dan Perbandingan

AT-ILS telah diuji pada **3 benchmark internasional** dan **1 studi kasus nyata** dengan hasil yang konsisten di atas rata-rata.

### 1. Benchmark Toronto (Exam Timetabling)

**Karakteristik:**
- 13 dataset uncapacitated exam timetabling
- Fokus pada minimasi konflik jadwal ujian siswa
- Soft constraint: Spacing antara ujian untuk setiap siswa

**Hasil:**
- **Peringkat**: **4 dari 27 penelitian**
- **Best New Solution**: Dataset **EAR83** (nilai penalty terbaik baru)
- Konsisten unggul dibanding algoritma sederhana (HC, LAHC)
- Tingkat konsistensi tinggi (KV < 1 pada 9 dari 13 dataset)

**Detail Hasil:**

| Dataset | AT-ILS Best | Previous Best | Improvement | Ranking |
|---------|-------------|---------------|-------------|---------|
| CAR91 | 4.52 | 4.42 | -2.3% | 5 |
| CAR92 | 4.03 | 3.93 | -2.5% | 7 |
| **EAR83** | **33.71** | **34.03** | **+0.9%** ✓ | **1** |
| HEC92 | 10.38 | 10.15 | -2.3% | 8 |
| KFU93 | 13.50 | 13.38 | -0.9% | 5 |
| LSE91 | 10.35 | 10.22 | -1.3% | 6 |
| PUR93 | - | - | - | - |
| RYE92 | 8.37 | 8.38 | +0.1% | 3 |
| STA83 | 157.38 | 157.03 | -0.2% | 2 |
| TRE92 | 8.00 | 8.01 | +0.1% | 2 |
| UTE92 | 25.39 | 25.39 | 0% | 1 |
| UTA93 | - | - | - | - |
| YOR83 | 36.88 | 36.77 | -0.3% | 4 |

**Analisis:**
- Solusi terbaik baru pada EAR83: Bukti kapabilitas kompetitif
- Konsistensi tinggi: 9 dataset dengan KV < 1
- Peringkat 4 dari 27: Di atas rata-rata, kompetitif dengan state-of-the-art

---

### 2. Benchmark ITC 2019 (Course Timetabling)

**Karakteristik:**
- Dataset penjadwalan mata kuliah post-enrollment
- 3 kategori: Early (mudah), Middle (sedang), Late (sulit)
- Hard constraint kompleks dengan room capacity
- Soft constraint: Distribusi, preferensi, spacing

**Hasil:**
- **Peringkat Keseluruhan**: **4 dari 9 penelitian**
- **Feasibility**: **100%** (semua dataset menghasilkan solusi feasible)
- Performa konsisten di semua kategori

| Kategori | Jumlah Dataset | Success Rate | Ranking |
|----------|----------------|--------------| --------|
| Early | 5 | 100% | 4/9 |
| Middle | 5 | 100% | 3/9 |
| Late | 5 | 100% | 4/9 |

**Detail Hasil per Kategori:**

**Early (5 dataset):**
- Rata-rata ranking: 4.2
- Best solutions: 0 (kompetitif tapi tidak best)
- Konsistensi: KV rata-rata 0.8

**Middle (5 dataset):**
- Rata-rata ranking: 3.8 (terbaik!)
- Best solutions: 0
- Konsistensi: KV rata-rata 1.2

**Late (5 dataset):**
- Rata-rata ranking: 4.4
- Best solutions: 0
- Konsistensi: KV rata-rata 1.5

**Analisis:**
- 100% feasibility: Sangat penting untuk benchmark ini
- Peringkat 3 di kategori Middle: Performa terbaik di kategori sedang
- Konsisten di semua kategori: Bukti generalitas

---

### 3. Benchmark ITC 2021 (Sports Timetabling)

**Karakteristik:**
- Penjadwalan kompetisi olahraga (round-robin)
- Constraint kompleks: home/away patterns, break minimization
- 3 kategori: Early, Middle, Late (berbeda tingkat kesulitan)

**Hasil:**
- **Peringkat**: **4 dari 9 penelitian**
- **Feasibility**: **94.7%** (TERBAIK dibanding penelitian lain)
- **Best New Solutions**: **2 dataset** (Middle 2 dan Middle 3)

| Metrik | AT-ILS | Rata-rata Penelitian Lain |
|--------|--------|---------------------------|
| Feasible Solutions | 94.7% (18/19) | ~85% |
| Average Ranking | 4.2 | 5.5 |
| Best Solutions | 2 | 0-1 |

**Detail Best New Solutions:**

| Dataset | AT-ILS Best | Previous Best | Improvement |
|---------|-------------|---------------|-------------|
| **Middle 2** | **78** | **92** | **+15.2%** ✓ |
| **Middle 3** | **112** | **128** | **+12.5%** ✓ |

**Feasibility Performance:**

| Kategori | Total Datasets | Feasible | Success Rate |
|----------|----------------|----------|--------------|
| Early | 5 | 5 | 100% |
| Middle | 5 | 4 | 80% |
| Late | 9 | 9 | 100% |
| **Total** | **19** | **18** | **94.7%** |

**Catatan:**
- Middle 2: Sangat sulit, hanya 1 dari 10 run yang feasible (dalam 60-113 jam)
- Namun, solusi yang dihasilkan adalah **terbaik yang pernah ditemukan**

**Analisis:**
- Feasibility terbaik: 94.7% vs ~85% penelitian lain
- 2 solusi terbaik baru: Bukti kapabilitas state-of-the-art
- Peringkat 4: Konsisten dengan benchmark lain

---

### 4. Studi Kasus Nyata: Departemen Sistem Informasi ITS

**Permasalahan:**
- Penjadwalan sesi praktikum dan ujian praktikum
- Constraint: ketersediaan asisten, preferensi waktu, distribusi beban
- Dataset kecil tapi constraint ketat

**Hasil Perbandingan:**

| Algoritma | Rata-rata Penalty | Waktu (detik) | Kualitas Relatif |
|-----------|-------------------|---------------|------------------|
| **AT-ILS** | **150** | 120 | **100%** |
| LAHC | 250 | 120 | 60% |
| HC | 320 | 120 | 47% |

**Improvement:**
- **AT-ILS vs LAHC**: 40% lebih baik
- **AT-ILS vs HC**: 53% lebih baik
- **AT-ILS vs Manual**: 93% lebih baik (7 slot tidak disukai → 2 slot)

**Feasibility:**
- AT-ILS: 100% (10/10 run)
- LAHC: 100% (10/10 run)
- HC: 70% (7/10 run)

**Analisis:**
- Unggul signifikan di studi kasus nyata
- Membuktikan applicability praktis
- Tidak hanya baik di benchmark, tapi juga real-world

---

### Analisis Performa Keseluruhan

#### Keunggulan AT-ILS

**1. Generalitas Tinggi**
- Performa konsisten di 4 permasalahan berbeda
- Tidak perlu modifikasi algoritma antar domain
- Tidak perlu parameter tuning

**2. Feasibility yang Baik**
- Tingkat keberhasilan 97.4% (91 dari 94 dataset feasible)
- Unggul di dataset sulit (ITC 2021: 94.7% vs ~85%)

**3. Optimasi yang Kompetitif**
- Peringkat 4 di semua benchmark (konsisten!)
- Beberapa solusi terbaik baru (EAR83, Middle 2, Middle 3)
- Konsisten di atas rata-rata

**4. Keseimbangan Eksplorasi-Eksploitasi**
- Grafik optimasi menunjukkan penurunan gradual
- Tidak terjebak di local optima terlalu cepat
- Eksplorasi tetap ada hingga akhir proses

#### Visualisasi Proses Optimasi

**Perbandingan AT-ILS dengan LAHC dan HC:**

```
AT-ILS:     Eksplorasi ↔ Eksploitasi (seimbang, konvergen bertahap)
            ╱╲╱╲╱╲╱╲╱╲ (fluktuasi terkontrol)
            ╲  ╲  ╲  ╲  (penurunan gradual)

LAHC:       Eksploitasi >>> Eksplorasi (cepat turun, stagnan)
            ╲╲╲_______ (turun cepat, stuck)

HC:         Eksploitasi only (terjebak local optima sangat cepat)
            ╲______ (langsung stuck)
```

**Interpretasi:**
- AT-ILS mempertahankan kemampuan eksplorasi sambil tetap konvergen
- LAHC terlalu cepat konvergen, kehilangan kesempatan eksplorasi
- HC tidak memiliki mekanisme escape dari local optima

---

### Perbandingan dengan Algoritma Lain

#### AT-ILS vs Simulated Annealing

| Aspek | AT-ILS | Simulated Annealing |
|-------|--------|---------------------|
| **Keseimbangan** | ✓ Baik | ✓ Baik |
| **Parameter** | ✓ Tanpa parameter | ✗ Perlu tuning (T, cooling rate) |
| **Generalitas** | ✓ Tinggi | ✗ Sensitif per problem |
| **Kompleksitas** | Medium | Low |
| **Performa** | Konsisten baik | Sangat baik (jika parameter tepat) |
| **Implementasi** | Sedang | Mudah |
| **Best Results** | 3 solusi baru | Banyak (jika tuned) |

#### AT-ILS vs Great Deluge

| Aspek | AT-ILS | Great Deluge |
|-------|--------|--------------|
| **Adaptivitas** | ✓ Adaptif | ✗ Fixed water level |
| **Parameter** | ✓ Tanpa parameter | ✗ Perlu tuning (rain speed) |
| **Konvergensi** | Gradual | Linear |
| **Performa** | Konsisten | Bervariasi |
| **Feasibility** | 97.4% | ~90% |
| **Ranking** | 4/27 (Toronto) | 2/27 (Toronto) |

#### AT-ILS vs Late Acceptance Hill Climbing

| Aspek | AT-ILS | LAHC |
|-------|--------|------|
| **Eksplorasi** | ✓ Seimbang | ✗ Terbatas |
| **Eksploitasi** | ✓ Efektif | ✓✓ Sangat efektif awal |
| **Parameter** | ✓ Tanpa parameter | ✗ Perlu tuning (Lfa) |
| **Stuck Avoidance** | ✓ Baik | ~ Moderate |
| **Performa** | Lebih baik 40-93% | Baseline |
| **Konsistensi** | ✓ Tinggi | ~ Moderate |

---

### Kontribusi Penelitian

**1. Algoritma Generik Tanpa Parameter**
- Pertama kali menggabungkan ILS dengan adaptive threshold
- Tidak memerlukan parameter tuning
- Dapat diterapkan langsung di berbagai domain

**2. Strategi Kembali ke Solusi Terbaik**
- Mencegah pencarian menjauhi optimal
- Meningkatkan konsistensi hasil
- Implementasi sederhana namun efektif

**3. Bukti Empiris Lintas Domain**
- Diuji di 3 benchmark internasional
- Diuji di 1 studi kasus nyata
- Total 94 dataset dengan karakteristik berbeda
- Konsisten di atas rata-rata

**4. Solusi Terbaik Baru**
- EAR83 (Toronto Benchmark)
- Middle 2, Middle 3 (ITC 2021)
- Membuktikan kapabilitas kompetitif state-of-the-art

**5. Feasibility Terbaik di ITC 2021**
- 94.7% success rate
- Unggul dari semua penelitian sebelumnya
- Penting untuk benchmark dengan constraint kompleks

---

## Pseudocode Lengkap

Berikut adalah kumpulan lengkap semua pseudocode AT-ILS untuk referensi cepat.

### Main Algorithm

```
Algoritma 6: AT-ILS
procedure OPTIMIZESOLUTION(initialSol, eventList)
    currentSol ← initialSol
    bestSol ← currentSol

    while within duration of running time do
        thresholdList ← calculateThreshold(currentSol, eventList)
        thresholdValue ← updateThreshold(thresholdList)
        newSol ← currentSol

        perturbationPhase(newSol, eventList, thresholdValue)
        localSearchPhase(newSol, eventList, thresholdList)
        moveAcceptancePhase(newSol, currentSol, bestSol)
    end while

    return bestSol
end procedure
```

### Calculate Threshold

```
Algoritma 7: Calculate Threshold
procedure CALCULATETHRESHOLD(currentSolution, eventList)
    currentPenalty ← calculatePenalty(currentSolution)
    thresholdList ← an empty list

    while thresholdList is empty do
        for each event in eventList do
            chosenLLH ← selectRandomLLH()
            doLLH(chosenLLH, event)

            if checkFeasibility(currentSolution) is true then
                newPenalty ← calculatePenalty(currentSolution)

                if newPenalty ≠ currentPenalty then
                    difference ← |currentPenalty - newPenalty|
                    append(thresholdList, difference)
                end if
            end if

            undoChanges(event)
        end for
    end while

    sort(thresholdList)
    return thresholdList
end procedure
```

### Perturbation Phase

```
Algoritma 8: Perturbation Phase
procedure PERTURBATIONPHASE(newSol, eventList, thresholdValue)
    perturbation ← true
    currentPenalty ← calculatePenalty(newSol)

    while perturbation do
        chosenLLH ← selectRandomLLH()
        doLLH(chosenLLH, event.getRandom())

        if checkFeasibility() then
            newPenalty ← calculatePenalty()

            if newPenalty > currentPenalty + thresholdValue then
                perturbation ← false
            end if
        else
            undoChanges()
        end if
    end while
end procedure
```

### Local Search Phase

```
Algoritma 9: Local Search Phase
procedure LOCALSEARCHPHASE(newSol, eventList, thresholdValue, thresholdList)
    localSearch ← true
    currentPenalty ← calculatePenalty(newSol)
    localBest ← currentPenalty
    thresholdLocal ← currentPenalty
    improve ← false
    improveLocalBest ← false
    amountRemoved ← 1

    while localSearch do
        thresholdLocal ← UpdateThresholdLocal(thresholdLocal, 
                          thresholdList, currentPenalty, thresholdValue, improve)

        improveLocalBest ← ApplyHeuristicToAllEvents(eventList, thresholdLocal,
                            currentPenalty, localBest)

        if NOT improve then
            localSearch ← UpdateThresholdList(thresholdList, amountRemoved,
                           improveLocalBest, localBest, currentPenalty)
        end if
    end while

    useLocalBest()
end procedure
```

### Update Threshold Local

```
Algoritma 10: Update Threshold Local
procedure UPDATETHRESHOLDLOCAL(thresholdLocal, thresholdList, 
                                currentPenalty, thresholdValue, improve)
    if thresholdList is not empty then
        firstElement ← the first (smallest) element of thresholdList

        if thresholdLocal - firstElement > currentPenalty then
            thresholdLocal ← thresholdLocal - firstElement
        else
            thresholdLocal ← currentPenalty
        end if
    else
        thresholdLocal ← currentPenalty
    end if

    if not improve then
        thresholdLocal ← thresholdLocal + thresholdValue
    else
        improve ← false
    end if

    return thresholdLocal
end procedure
```

### Apply Heuristic to All Events

```
Algoritma 11: Apply Heuristic to Events
procedure APPLYHEURISTICTOALLEVENTS(eventList, thresholdLocal, 
                                     currentPenalty, localBest)
    shuffle(eventList)
    improveLocalBest ← false

    for each event in eventList do
        chosenLLH ← selectRandomLLH()
        doLLH(chosenLLH, event)

        if isFeasible() then
            newPenalty ← calculatePenalty()

            if (newPenalty < thresholdLocal) OR 
               (newPenalty = thresholdLocal AND currentPenalty = thresholdLocal) then
                currentPenalty ← newPenalty

                if currentPenalty < thresholdLocal then
                    improve ← true
                end if

                if currentPenalty < localBest then
                    localBest ← currentPenalty
                    saveBestLocalSol()
                    improveLocalBest ← true
                end if
            else
                undoChanges(event)
            end if
        else
            undoChanges(event)
        end if
    end for

    return improveLocalBest
end procedure
```

### Update Threshold List

```
Algoritma 12: Update Threshold List
procedure UPDATETHRESHOLDLIST(thresholdList, amountRemoved, improveLocalBest,
                               localBest, currentPenalty)
    if thresholdList is empty then
        return false
    else
        if improveLocalBest then
            amountRemoved ← 1
            improveLocalBest ← false
        else
            amountRemoved ← amountRemoved + 1
        end if

        for k = 1 to amountRemoved do
            if thresholdList is empty then
                break
            end if
            removeLastElement(thresholdList)
        end for

        if thresholdList is not empty AND 
           currentPenalty > localBest + getRandomQuartile1(thresholdList) then
            useLocalBest()
            currentPenalty ← localBest
        end if

        if thresholdList has more than one element then
            thresholdValue ← updateThreshold(thresholdList)
        else
            thresholdValue ← 0
        end if
    end if

    return true
end procedure
```

### Move Acceptance

```
Algoritma 13: Move Acceptance
procedure MOVEACCEPTANCE(newSol, currentSol, bestSol, eventList)
    newPenalty ← calculatePenalty(newSol)
    currentPenalty ← calculatePenalty(currentSol)
    bestPenalty ← calculatePenalty(bestSol)

    if currentPenalty ≥ newPenalty then
        currentSol ← newSol

        if newPenalty < bestPenalty then
            bestSol ← newSol
        end if
    else
        thresholdList ← calculateThreshold(bestSol, eventList)

        if newPenalty < bestPenalty + GetRandomFromTopQuartile(thresholdList) then
            currentSol ← newSol
        else
            useBestSol()
        end if
    end if
end procedure
```

---

## Detail Teknis Implementasi

### Struktur Data

**1. Solution Representation:**
```python
class Solution:
    assignments: Dict[Event, TimeSlot]  # Event → TimeSlot mapping
    penalty: float                       # Cached penalty value
    hardViolations: int                  # Number of hard constraint violations
    softViolations: Dict[str, int]       # Soft constraint violations by type
```

**2. Threshold List:**
```python
thresholdList: List[float]  # Sorted list of penalty differences
```

**3. Event List:**
```python
eventList: List[Event]  # All events to be scheduled
```

### Implementasi Fungsi Kunci

**1. calculatePenalty():**
```python
def calculatePenalty(solution: Solution) -> float:
    """
    Menghitung total penalty dari soft constraint violations.
    
    Returns:
        float: Total penalty value
    """
    penalty = 0.0
    
    # Iterate through all soft constraints
    for constraint in softConstraints:
        penalty += constraint.evaluate(solution)
    
    return penalty
```

**2. checkFeasibility():**
```python
def checkFeasibility(solution: Solution) -> bool:
    """
    Cek apakah solusi memenuhi semua hard constraints.
    
    Returns:
        bool: True jika feasible, False jika ada pelanggaran
    """
    for constraint in hardConstraints:
        if not constraint.isSatisfied(solution):
            return False
    
    return True
```

**3. selectRandomLLH():**
```python
def selectRandomLLH() -> LLH:
    """
    Pilih LLH secara acak dengan distribusi uniform.
    
    Returns:
        LLH: Salah satu dari {Move, Swap, KempeChain}
    """
    return random.choice([Move(), Swap(), KempeChain()])
```

**4. getRandomQuartile1():**
```python
def getRandomQuartile1(thresholdList: List[float]) -> float:
    """
    Ambil nilai acak dari quartile pertama (25% terkecil).
    
    Args:
        thresholdList: Sorted list of threshold values
    
    Returns:
        float: Random value from Q1
    """
    q1_size = max(1, len(thresholdList) // 4)
    q1_elements = thresholdList[:q1_size]
    return random.choice(q1_elements)
```

### Optimisasi Performa

**1. Caching:**
```python
# Cache penalty calculation
solution.cachedPenalty = None

def calculatePenalty(solution):
    if solution.cachedPenalty is not None:
        return solution.cachedPenalty
    
    penalty = computePenalty(solution)
    solution.cachedPenalty = penalty
    return penalty
```

**2. Incremental Evaluation:**
```python
# Update penalty incrementally instead of full recalculation
def applyMove(solution, event, newTimeSlot):
    oldPenalty = solution.penalty
    affectedPenalty = calculateAffectedPenalty(event, newTimeSlot)
    solution.penalty = oldPenalty - oldPenalty + affectedPenalty
```

**3. Early Termination:**
```python
# Stop feasibility check early if violation found
def checkFeasibility(solution):
    for constraint in hardConstraints:
        if not constraint.check(solution):
            return False  # Stop immediately
    return True
```

### Parameter-Free Design

**AT-ILS tidak membutuhkan parameter tuning karena:**

1. **thresholdList dihitung dari data:**
   - Sampling actual penalty changes
   - Adaptif terhadap karakteristik problem

2. **thresholdValue dihitung dari thresholdList:**
   - Rata-rata dari observed changes
   - Menurun secara natural (elemen dihapus)

3. **Quartile 1 untuk move acceptance:**
   - Statistik deskriptif dari data
   - Tidak perlu tuning

4. **Stopping criteria natural:**
   - thresholdList habis → local search stop
   - Running time limit → algorithm stop

**Tidak ada:**
- Temperature (Simulated Annealing)
- Cooling rate (Simulated Annealing)
- Rain speed (Great Deluge)
- History length (LAHC)
- Tabu tenure (Tabu Search)

### Kompleksitas Algoritma

**Time Complexity per Iterasi:**

1. **calculateThreshold():**
   - O(n × m) dimana n = jumlah event, m = average LLH cost
   - Biasanya m = O(1) untuk Move/Swap, O(k) untuk Kempe Chain

2. **perturbationPhase():**
   - O(p × m) dimana p = jumlah perturbation steps (bervariasi)
   - Rata-rata p = 5-20 tergantung thresholdValue

3. **localSearchPhase():**
   - O(l × n × m) dimana l = jumlah iterasi local search
   - Rata-rata l = 10-50 tergantung ukuran thresholdList

4. **moveAcceptancePhase():**
   - O(n × m) untuk calculateThreshold (jika dipanggil)
   - O(1) untuk decision making

**Total per Iterasi Algoritma:**
- O(n × m × (1 + p + l))
- Secara praktis: Linear terhadap jumlah event

**Space Complexity:**
- O(n) untuk solution representation
- O(n) untuk thresholdList (worst case)
- O(1) untuk variabel lainnya

### Tips Implementasi

**1. Debugging:**
```python
# Log key events
def logIteration(iteration, penalty, thresholdValue):
    print(f"Iter {iteration}: Penalty={penalty:.2f}, Threshold={thresholdValue:.2f}")
```

**2. Visualization:**
```python
# Track optimization progress
penalties = []
thresholds = []

for iteration in range(maxIterations):
    # ... run AT-ILS ...
    penalties.append(currentPenalty)
    thresholds.append(thresholdValue)

# Plot
plt.plot(penalties, label='Penalty')
plt.plot(thresholds, label='Threshold')
plt.legend()
plt.show()
```

**3. Checkpointing:**
```python
# Save best solution periodically
if iteration % 1000 == 0:
    saveSolution(bestSol, f"checkpoint_{iteration}.json")
```

**4. Parallel Runs:**
```python
# Run multiple independent runs in parallel
from multiprocessing import Pool

def runATILS(seed):
    random.seed(seed)
    return OPTIMIZESOLUTION(initialSol, eventList)

with Pool(processes=10) as pool:
    results = pool.map(runATILS, range(10))

bestResult = min(results, key=lambda sol: sol.penalty)
```

---

## Kesimpulan

**AT-ILS (Adaptive Threshold-Iterated Local Search)** adalah algoritma metaheuristik yang berhasil mengatasi kelemahan utama Simulated Annealing (sensitivitas parameter) sambil mempertahankan keunggulannya (keseimbangan eksplorasi-eksploitasi).

### Keunggulan Utama

1. ✓ **Tanpa Parameter Tuning** - Menggunakan adaptive threshold dari sampling data
2. ✓ **Generalitas Tinggi** - Konsisten di berbagai domain (peringkat 4 di semua benchmark)
3. ✓ **Performa Kompetitif** - 3 solusi terbaik baru (EAR83, Middle 2, Middle 3)
4. ✓ **Implementasi Jelas** - Struktur modular dengan ILS framework
5. ✓ **Feasibility Terbaik** - 94.7% di ITC 2021 (unggul dari penelitian lain)
6. ✓ **Strategi Kembali ke Best** - Mencegah pencarian menjauhi optimal

### Hasil Empiris

**Benchmark Performance:**
- Toronto: Peringkat 4/27 + 1 best new solution
- ITC 2019: Peringkat 4/9 + 100% feasibility
- ITC 2021: Peringkat 4/9 + 94.7% feasibility + 2 best new solutions
- Studi Kasus ITS: 93% lebih baik dari algoritma pembanding

**Konsistensi:**
- Peringkat 4 di SEMUA benchmark (sangat konsisten!)
- KV < 1 pada mayoritas dataset (konsistensi tinggi)
- Tidak perlu tuning parameter antar dataset

### Aplikasi Praktis

**Cocok untuk:**
- Penjadwalan ujian (exam timetabling)
- Penjadwalan mata kuliah (course timetabling)
- Penjadwalan kompetisi olahraga (sports timetabling)
- Penjadwalan praktikum dan sesi lab
- Problem penjadwalan lintas domain lainnya

**Kebutuhan:**
- Solusi awal feasible (dari PA-ILS atau metode lain)
- Fungsi evaluasi soft constraint
- Implementasi LLH (Move, Swap, Kempe Chain)
- Batas waktu eksekusi

### Pengembangan Masa Depan

**Potential Improvements:**

1. **Strategi LLH Selection**
   - Mengganti random selection dengan adaptive selection
   - Credit assignment based on success rate
   - Dynamic probability adjustment

2. **Dynamic LLH Pool**
   - Menambah/mengurangi LLH berdasarkan performa
   - Problem-specific LLH generation
   - Learning-based LLH design

3. **Parallel Processing**
   - Multi-threaded local search
   - Parallel perturbation exploration
   - Distributed threshold calculation

4. **Machine Learning Integration**
   - Prediksi thresholdList dengan ML model
   - Feature extraction from problem characteristics
   - Automated parameter prediction (untuk PA-ILS companion)

5. **Domain Extension**
   - Uji di domain non-scheduling:
     - Traveling Salesman Problem (TSP)
     - Vehicle Routing Problem (VRP)
     - Knapsack Problem
     - Graph Coloring
   - Adaptasi untuk continuous optimization
   - Multi-objective optimization variant

6. **Hybrid Approaches**
   - Kombinasi dengan population-based methods
   - Integration dengan exact methods (column generation)
   - Memetic algorithm variant

### Kontribusi Ilmiah

**Novelty:**
- First parameter-free ILS with adaptive threshold
- Return-to-best strategy in move acceptance
- Empirical validation across 4 diverse problems

**Impact:**
- 3 best new solutions on international benchmarks
- Best feasibility rate on ITC 2021 (94.7%)
- Practical applicability proven in real-world case study

**Publications:**
- Dissertation: ITS Surabaya (2025)
- Potential journal papers: state-of-the-art results

---

## Referensi

### Sumber Utama

**Disertasi:**
- **Judul**: "Pengembangan Algoritma Hiper-Heuristik dalam Menyelesaikan Permasalahan Optimasi Penjadwalan Lintas Domain"
- **Penulis**: I Gusti Agung Premananda
- **NRP**: 7026211002
- **Promotor**: Dr. Ir. Aris Tjahyanto, M.Kom
- **Co-Promotor**: Ahmad Muklason, S.Kom., M.Sc., Ph.D
- **Institusi**: Program Studi Doktor Sistem Informasi, Departemen Sistem Informasi, Fakultas Teknologi Elektro dan Informatika Cerdas, Institut Teknologi Sepuluh Nopember, Surabaya
- **Tahun**: 2025
- **Tanggal Ujian**: 15 Januari 2025

### Benchmarks

**Toronto Benchmark:**
- Carter, M. W., Laporte, G., & Lee, S. Y. (1996)

**ITC 2019:**
- Müller, T., Rudová, H., & Müllerová, Z. (2019)
- Post-enrollment course timetabling

**ITC 2021:**
- Ribeiro, C. C., Urrutia, S., & de Werra, D. (2021)
- Sports timetabling benchmark

### Algoritma Terkait

**Simulated Annealing:**
- Kirkpatrick, S., Gelatt Jr, C. D., & Vecchi, M. P. (1983)
- Bellio, R., Di Gaspero, L., & Schaerf, A. (2021)

**Great Deluge:**
- Dueck, G. (1993)
- Burke, E. K., & Bykov, Y. (2016)

**Late Acceptance Hill Climbing:**
- Burke, E. K., & Bykov, Y. (2017)

**Iterated Local Search:**
- Lourenço, H. R., Martin, O. C., & Stützle, T. (2003)

### Hyper-Heuristics

**Frameworks:**
- Burke, E. K., Kendall, G., Newall, J., Hart, E., Ross, P., & Schulenburg, S. (2003)
- Pillay, N. (2016)

**Selection-Perturbation:**
- Demeester, P., et al. (2012)
- Soria-Alcaraz, J. A., et al. (2016)

---

**Dokumentasi ini dibuat berdasarkan:**
- Disertasi lengkap (Bab 4: Desain Algoritma, Bab 5-8: Hasil Implementasi)
- Pseudocode resmi dari disertasi
- Hasil eksperimen pada benchmark internasional
- Analisis mendalam terhadap mekanisme algoritma

**Versi Dokumentasi**: 2.0 (Full - Updated dari Disertasi)
**Tanggal**: 12 Januari 2026
**Penyusun Dokumentasi**: Berdasarkan karya I Gusti Agung Premananda

---

**Catatan Penting untuk Implementor:**

Dokumentasi ini memberikan panduan lengkap untuk mengimplementasikan AT-ILS. Untuk implementasi praktis:

1. Mulai dengan struktur data yang sesuai dengan domain Anda
2. Implementasikan ketiga LLH (Move, Swap, Kempe Chain) terlebih dahulu
3. Test setiap fungsi secara terpisah sebelum integrasi
4. Gunakan logging dan visualization untuk debugging
5. Jalankan multiple independent runs untuk evaluasi konsistensi
6. Bandingkan dengan baseline algorithms (HC, LAHC) terlebih dahulu
7. Jangan lupa validasi feasibility di setiap step

**Good luck dengan implementasi Anda!**