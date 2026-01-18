# Dokumentasi Algoritma IHTP (Integrated Healthcare Timetabling Problem)

## Ringkasan Problem

IHTP adalah masalah optimasi terintegrasi yang menggabungkan tiga subproblem NP-hard:
1. **Patient Admission Scheduling (PAS)** - menentukan hari admission dan room untuk setiap pasien
2. **Nurse-to-Room Assignment (NRA)** - mengassign nurse ke room pada setiap shift
3. **Surgical Case Planning (SCP)** - mengalokasikan operasi ke operating theater

### Keputusan yang Harus Dibuat Algoritma

1. **Admission date** untuk setiap patient (atau postponement untuk optional patients)
2. **Room assignment** untuk setiap admitted patient (tetap di 1 room selama stay)
3. **Nurse assignment** ke setiap occupied room untuk setiap shift
4. **Operating Theater (OT) assignment** untuk surgery setiap patient pada admission day

---

## Data Structures dan Indexing

### Time Model
- **Days**: 0 sampai D-1 (D = kelipatan 7, range 14-28 hari)
- **Shifts per day**: 3 shift (early, late, night)
- **Shift numbering**: 0-indexed sequential
  - Day 0: shift 0 (early), 1 (late), 2 (night)
  - Day 1: shift 3 (early), 4 (late), 5 (night)
  - Day d: shift 3d (early), 3d+1 (late), 3d+2 (night)
  - Total shifts dalam periode: 3×D

### Patient Types
1. **Occupants** (pasien existing di day-0)
   - Room dan admission sudah FIXED
   - Tidak gunakan OT (operasi sudah lewat)
   - Berkontribusi pada: H1, H7, S1 (room), S2, S3, S4 (nurse)

2. **Patients** (pasien baru yang perlu dijadwalkan)
   - **Mandatory**: HARUS diadmit dalam periode (H5 violation jika tidak)
   - **Optional**: boleh postpone ke periode berikutnya (S8 penalty)

### Workload dan Skill Arrays
⚠️ **CRITICAL**: Untuk patient dengan `length_of_stay = L`:
- Array length = **3 × L**
- Index 0 = early shift pada admission day
- Index (3L-1) = night shift pada discharge day
- Admission/discharge terjadi AFTER night shift, BEFORE early shift

---

## HARD CONSTRAINTS (Wajib Dipenuhi)

### H1: No Gender Mix in Room
**Definisi**: Pasien dengan gender berbeda TIDAK BOLEH berada dalam 1 room pada hari yang sama.

**Implementasi Check**:
```python
for day in range(days):
    for room in rooms:
        patients_in_room = get_patients_in_room(room, day)
        genders = {patient.gender for patient in patients_in_room}
        if len(genders) > 1:
            violations += 1  # HARD CONSTRAINT VIOLATION
```

**Strategi Repair**:
- Pindahkan patient ke room lain dengan gender yang compatible
- Perhatikan: patient tidak bisa pindah room mid-stay (fixed for entire stay)

---

### H2: Compatible Rooms
**Definisi**: Patient hanya boleh di-assign ke room yang TIDAK ada dalam `incompatible_room_ids` mereka.

**Implementasi Check**:
```python
for patient in scheduled_patients:
    if patient.room_id in patient.incompatible_room_ids:
        violations += 1  # HARD CONSTRAINT VIOLATION
```

**Strategi Assignment**:
```python
compatible_rooms = [r for r in rooms if r.id not in patient.incompatible_room_ids]
# Pilih dari compatible_rooms saja
```

---

### H3: Surgeon Overtime
**Definisi**: Total durasi operasi yang dilakukan surgeon pada hari d tidak boleh melebihi `max_surgery_time[d]`.

**Formula**:
\[
\sum_{\text{patient } p \text{ with } \text{surgeon}_p = s \text{ and } \text{admission}_p = d} \text{surgery\_duration}_p \leq \text{max\_surgery\_time}_s[d]
\]

**Implementasi Check**:
```python
for surgeon in surgeons:
    for day in range(days):
        total_time = sum(
            patient.surgery_duration 
            for patient in scheduled_patients 
            if patient.surgeon_id == surgeon.id and patient.admission_day == day
        )
        if total_time > surgeon.max_surgery_time[day]:
            violations += 1  # HARD CONSTRAINT VIOLATION
```

**⚠️ Important**: `max_surgery_time[d] = 0` berarti surgeon UNAVAILABLE pada hari tersebut.

**Strategi Repair**:
- Reschedule patient ke hari lain dalam [release_day, due_day]
- Pastikan surgeon available (max_surgery_time > 0)

---

### H4: Operating Theater Overtime
**Definisi**: Total durasi operasi di OT pada hari d tidak boleh melebihi `availability[d]`.

**Formula**:
\[
\sum_{\text{patient } p \text{ with } \text{OT}_p = t \text{ and } \text{admission}_p = d} \text{surgery\_duration}_p \leq \text{availability}_t[d]
\]

**Implementasi Check**:
```python
for ot in operating_theaters:
    for day in range(days):
        total_time = sum(
            patient.surgery_duration 
            for patient in scheduled_patients 
            if patient.operating_theater == ot.id and patient.admission_day == day
        )
        if total_time > ot.availability[day]:
            violations += 1  # HARD CONSTRAINT VIOLATION
```

**⚠️ Important**: 
- `availability[d] = 0` berarti OT CLOSED pada hari tersebut
- Open scheduling policy: surgeon bisa operasi di OT mana saja

---

### H5: Mandatory Patients
**Definisi**: Semua mandatory patients HARUS diadmit dalam scheduling period.

**Implementasi Check**:
```python
for patient in patients:
    if patient.mandatory and not patient.is_scheduled:
        violations += 1  # HARD CONSTRAINT VIOLATION
```

**Strategi**:
- Prioritaskan mandatory patients dalam assignment
- Optional patients bisa postpone jika capacity tidak cukup

---

### H6: Admission Day Window
**Definisi**: Patient hanya bisa diadmit dalam window yang valid.

**Rules**:
- **Mandatory**: `surgery_release_day ≤ admission_day ≤ surgery_due_day`
- **Optional**: `admission_day ≥ surgery_release_day` (no due date)

**⚠️ CRITICAL**: Field `surgery_due_day` HANYA ADA untuk mandatory patients!

**Implementasi Check**:
```python
for patient in scheduled_patients:
    if patient.admission_day < patient.surgery_release_day:
        violations += 1  # HARD CONSTRAINT VIOLATION

    if patient.mandatory:  # due_day exists
        if patient.admission_day > patient.surgery_due_day:
            violations += 1  # HARD CONSTRAINT VIOLATION
```

---

### H7: Room Capacity
**Definisi**: Jumlah pasien dalam room pada hari tertentu tidak boleh melebihi capacity.

**Formula**:
\[
|\{p : \text{patient } p \text{ in room } r \text{ on day } d\}| \leq \text{capacity}_r
\]

**Implementasi Check**:
```python
for room in rooms:
    for day in range(days):
        # Hitung occupants + patients yang sedang stay
        count = 0

        # Occupants yang masih ada
        for occupant in occupants:
            if occupant.room_id == room.id and day < occupant.length_of_stay:
                count += 1

        # Patients yang dijadwalkan
        for patient in scheduled_patients:
            if patient.room_id == room.id:
                if patient.admission_day <= day < patient.admission_day + patient.length_of_stay:
                    count += 1

        if count > room.capacity:
            violations += 1  # HARD CONSTRAINT VIOLATION
```

---

### H8: Nurse Roster Constraints (Implicit)
**Definisi**: Setiap room yang occupied HARUS ada nurse yang assigned pada shift tersebut.

**Rules**:
- Nurse hanya bisa bekerja pada shift yang ada di roster (`working_shifts`)
- Nurse maksimum 1 shift per hari (sudah dijamin oleh roster)
- Nurse bisa assigned ke multiple rooms dalam 1 shift
- Setiap room hanya bisa punya 1 nurse per shift

**Implementasi Check**:
```python
for day in range(days):
    for shift_type in ["early", "late", "night"]:
        shift_idx = day * 3 + shift_type_to_index(shift_type)

        for room in rooms:
            if is_room_occupied(room, day):
                assigned_nurse = get_assigned_nurse(room, shift_idx)

                if assigned_nurse is None:
                    violations += 1  # HARD: UncoveredRoom
                else:
                    # Check nurse available in this shift
                    if not nurse_works_in_shift(assigned_nurse, day, shift_type):
                        violations += 1  # HARD: NursePresence
```

---

## SOFT CONSTRAINTS (Minimize Penalties)

### S1: Room Mixed Age
**Tujuan**: Minimize perbedaan age_group dalam room per hari.

**Penalty Calculation**:
```python
penalty = 0
for day in range(days):
    for room in rooms:
        patients_in_room = get_patients_in_room(room, day)
        if len(patients_in_room) > 1:
            age_groups = [patient.age_group for patient in patients_in_room]
            age_indices = [age_groups_list.index(ag) for ag in age_groups]
            max_diff = max(age_indices) - min(age_indices)
            penalty += max_diff

total_cost = weights["room_mixed_age"] * penalty
```

**Formula**:
\[
\text{Cost} = w_1 \times \sum_{d=0}^{D-1} \sum_{r \in \text{rooms}} \max(\text{age\_indices}_r[d]) - \min(\text{age\_indices}_r[d])
\]

**Strategi Minimization**:
- Kelompokkan patients dengan age_group yang sama/dekat di room yang sama
- Contoh: `["infant", "youth", "adult", "elderly"]` → infant + elderly = penalty 3

---

### S2: Room Nurse Skill
**Tujuan**: Nurse skill level harus memenuhi requirement pasien.

**Penalty per Shift**:
```python
penalty = max(0, required_skill - nurse_skill)
```

**Total Cost Calculation**:
```python
total_penalty = 0

# Untuk setiap patient (termasuk occupants)
for patient in all_patients:
    admission = patient.admission_day if hasattr(patient, 'admission_day') else 0

    for shift_offset in range(3 * patient.length_of_stay):
        day = admission + shift_offset // 3
        shift_in_day = shift_offset % 3  # 0=early, 1=late, 2=night
        shift_idx = day * 3 + shift_in_day

        required_skill = patient.skill_level_required[shift_offset]
        assigned_nurse = get_nurse_for_patient_room(patient.room_id, shift_idx)

        if assigned_nurse:
            skill_deficit = max(0, required_skill - assigned_nurse.skill_level)
            total_penalty += skill_deficit

total_cost = weights["room_nurse_skill"] * total_penalty
```

**⚠️ Important**: 
- Nurse dengan skill LEBIH TINGGI bisa handle patient (no penalty)
- Hierarchical: skill 0 (lowest) sampai L-1 (highest)

---

### S3: Continuity of Care
**Tujuan**: Minimize jumlah nurse berbeda yang merawat 1 patient.

**Formula**:
\[
\text{Cost} = w_3 \times \sum_{p \in \text{all\_patients}} |\{n : \text{nurse } n \text{ assigned to patient } p\}|
\]

**Implementasi**:
```python
total_distinct_nurses = 0

for patient in all_patients:
    nurses_for_patient = set()
    admission = patient.admission_day if hasattr(patient, 'admission_day') else 0

    for shift_offset in range(3 * patient.length_of_stay):
        day = admission + shift_offset // 3
        shift_in_day = shift_offset % 3
        shift_idx = day * 3 + shift_in_day

        nurse = get_nurse_for_patient_room(patient.room_id, shift_idx)
        if nurse:
            nurses_for_patient.add(nurse.id)

    total_distinct_nurses += len(nurses_for_patient)

total_cost = weights["continuity_of_care"] * total_distinct_nurses
```

**⚠️ Minimum Theoretical Cost**: 
- Setiap patient butuh minimum 3 nurse (early, late, night)
- Karena roster: max 1 shift/day per nurse
- Minimum total = 3 × jumlah_admitted_patients

---

### S4: Nurse Excessive Workload
**Tujuan**: Total workload nurse per shift tidak melebihi `max_load`.

**Penalty per Shift**:
```python
penalty = max(0, total_workload - max_load)
```

**Total Cost Calculation**:
```python
total_penalty = 0

for nurse in nurses:
    for work_shift in nurse.working_shifts:
        day = work_shift.day
        shift_type = work_shift.shift  # "early", "late", "night"
        shift_idx = day * 3 + shift_type_to_index(shift_type)
        max_load = work_shift.max_load

        # Hitung total workload dari semua room yang assigned ke nurse
        total_workload = 0
        assigned_rooms = get_rooms_assigned_to_nurse(nurse.id, shift_idx)

        for room in assigned_rooms:
            for patient in get_patients_in_room(room, day):
                # Tentukan shift offset patient
                if hasattr(patient, 'admission_day'):
                    days_since_admission = day - patient.admission_day
                else:  # occupant
                    days_since_admission = day

                shift_offset = days_since_admission * 3 + shift_type_to_index(shift_type)

                if 0 <= shift_offset < len(patient.workload_produced):
                    total_workload += patient.workload_produced[shift_offset]

        excess = max(0, total_workload - max_load)
        total_penalty += excess

total_cost = weights["nurse_eccessive_workload"] * total_penalty
```

**⚠️ Important**: `max_load` berbeda per shift karena auxiliary activities.

---

### S5: Open Operating Theater
**Tujuan**: Minimize jumlah OT yang dibuka per hari.

**Definisi "Open"**: OT dianggap open jika ada minimal 1 patient assigned pada hari tersebut.

**Formula**:
\[
\text{Cost} = w_5 \times \sum_{d=0}^{D-1} |\{t : \exists p \text{ with } \text{OT}_p = t \text{ and } \text{admission}_p = d\}|
\]

**Implementasi**:
```python
total_open_ots = 0

for day in range(days):
    open_ots = set()
    for patient in scheduled_patients:
        if patient.admission_day == day:
            open_ots.add(patient.operating_theater)

    total_open_ots += len(open_ots)

total_cost = weights["open_operating_theater"] * total_open_ots
```

**Strategi Minimization**:
- Konsolidasikan operasi ke fewer OTs per day
- Trade-off dengan H4 (OT capacity constraint)

---

### S6: Surgeon Transfer
**Tujuan**: Minimize perpindahan OT oleh surgeon dalam 1 hari kerja.

**Formula**:
\[
\text{Cost} = w_6 \times \sum_{s \in \text{surgeons}} \sum_{d=0}^{D-1} \max(0, |\text{OTs}_s[d]| - 1)
\]

Di mana \(\text{OTs}_s[d]\) adalah set OT yang digunakan surgeon s pada day d.

**Implementasi**:
```python
total_transfers = 0

for surgeon in surgeons:
    for day in range(days):
        ots_used = set()
        for patient in scheduled_patients:
            if patient.surgeon_id == surgeon.id and patient.admission_day == day:
                ots_used.add(patient.operating_theater)

        # Jika surgeon menggunakan lebih dari 1 OT, hitung transfers
        if len(ots_used) > 1:
            total_transfers += len(ots_used) - 1

total_cost = weights["surgeon_transfer"] * total_transfers
```

**Strategi Minimization**:
- Assign semua patient dari surgeon yang sama ke 1 OT per day
- Trade-off dengan S5 (open OT) dan H4 (OT capacity)

---

### S7: Patient Delay
**Tujuan**: Minimize delay antara release date dan admission date.

**Delay Calculation**:
```python
delay = admission_day - surgery_release_day
```

**Formula**:
\[
\text{Cost} = w_7 \times \sum_{p \in \text{scheduled\_patients}} (\text{admission}_p - \text{release}_p)
\]

**Implementasi**:
```python
total_delay = 0

for patient in scheduled_patients:
    delay = patient.admission_day - patient.surgery_release_day
    total_delay += delay

total_cost = weights["patient_delay"] * total_delay
```

**Strategi Minimization**:
- Admit patients as early as possible (mendekati release_day)
- Trade-off dengan resource availability (room, OT, surgeon, nurse)

---

### S8: Unscheduled Optional Patients
**Tujuan**: Minimize jumlah optional patients yang tidak dijadwalkan.

**Formula**:
\[
\text{Cost} = w_8 \times |\{p : p \text{ is optional and not scheduled}\}|
\]

**Implementasi**:
```python
unscheduled_count = 0

for patient in patients:
    if not patient.mandatory and not patient.is_scheduled:
        unscheduled_count += 1

total_cost = weights["unscheduled_optional"] * unscheduled_count
```

**⚠️ Important**: 
- Hanya OPTIONAL patients yang bisa unscheduled (tidak violation H5)
- Biasanya weight paling besar (misal 350) untuk encourage scheduling

---

## OBJECTIVE FUNCTION

**Total Cost**:
\[
\text{Total Cost} = \sum_{i=1}^{8} w_i \times \text{penalty}_i
\]

Di mana:
- \(w_1\) = `room_mixed_age`
- \(w_2\) = `room_nurse_skill`
- \(w_3\) = `continuity_of_care`
- \(w_4\) = `nurse_eccessive_workload`
- \(w_5\) = `open_operating_theater`
- \(w_6\) = `surgeon_transfer`
- \(w_7\) = `patient_delay`
- \(w_8\) = `unscheduled_optional`

**Goal**: Minimize Total Cost subject to ALL hard constraints satisfied.

---

## STRATEGI ALGORITMA

### 1. Initial Solution Construction

**Urutan Assignment**:
```python
# Phase 1: Schedule Mandatory Patients (prioritas tinggi)
for patient in sorted(mandatory_patients, key=lambda p: p.surgery_due_day):
    admission_day = find_best_admission_day(patient)
    room = find_compatible_room(patient, admission_day)
    ot = find_available_ot(patient, admission_day)
    assign_patient(patient, admission_day, room, ot)

# Phase 2: Schedule Optional Patients (jika masih ada capacity)
for patient in optional_patients:
    try:
        admission_day = find_best_admission_day(patient)
        room = find_compatible_room(patient, admission_day)
        ot = find_available_ot(patient, admission_day)
        assign_patient(patient, admission_day, room, ot)
    except NoFeasibleSolution:
        patient.is_scheduled = False  # S8 penalty

# Phase 3: Assign Nurses to Rooms
for day in range(days):
    for shift_type in ["early", "late", "night"]:
        assign_nurses_to_rooms(day, shift_type)
```

### 2. Feasibility Checks

**Pre-Assignment Checks**:
```python
def can_assign_patient(patient, admission_day, room, ot):
    # H2: Room compatibility
    if room.id in patient.incompatible_room_ids:
        return False

    # H6: Admission window
    if admission_day < patient.surgery_release_day:
        return False
    if patient.mandatory and admission_day > patient.surgery_due_day:
        return False

    # H7: Room capacity (for all days of stay)
    for day in range(admission_day, admission_day + patient.length_of_stay):
        if get_room_occupancy(room, day) >= room.capacity:
            return False

    # H1: Gender mix (for all days of stay)
    for day in range(admission_day, admission_day + patient.length_of_stay):
        room_genders = get_room_genders(room, day)
        if room_genders and patient.gender not in room_genders:
            return False

    # H3: Surgeon availability and capacity
    surgeon = get_surgeon(patient.surgeon_id)
    if surgeon.max_surgery_time[admission_day] == 0:
        return False
    current_time = get_surgeon_daily_time(surgeon, admission_day)
    if current_time + patient.surgery_duration > surgeon.max_surgery_time[admission_day]:
        return False

    # H4: OT availability and capacity
    if ot.availability[admission_day] == 0:
        return False
    current_time = get_ot_daily_time(ot, admission_day)
    if current_time + patient.surgery_duration > ot.availability[admission_day]:
        return False

    return True
```

### 3. Neighborhood Operators (untuk Local Search/Metaheuristics)

#### Operator 1: Reschedule Patient
```python
def reschedule_patient(patient):
    # Ubah admission_day dalam valid window
    old_day = patient.admission_day
    new_day = random.choice(range(patient.surgery_release_day, 
                                   patient.surgery_due_day + 1 if patient.mandatory 
                                   else days - patient.length_of_stay + 1))

    if can_assign_patient(patient, new_day, patient.room_id, patient.operating_theater):
        patient.admission_day = new_day
        return True
    return False
```

#### Operator 2: Change Room
```python
def change_room(patient):
    # Pindahkan patient ke room lain
    old_room = patient.room_id
    compatible_rooms = [r for r in rooms if r.id not in patient.incompatible_room_ids]
    new_room = random.choice(compatible_rooms)

    if can_assign_patient(patient, patient.admission_day, new_room, patient.operating_theater):
        patient.room_id = new_room
        return True
    return False
```

#### Operator 3: Change OT
```python
def change_ot(patient):
    # Pindahkan operasi ke OT lain
    old_ot = patient.operating_theater
    new_ot = random.choice(operating_theaters)

    if can_assign_patient(patient, patient.admission_day, patient.room_id, new_ot):
        patient.operating_theater = new_ot
        return True
    return False
```

#### Operator 4: Swap Patients
```python
def swap_patients(patient1, patient2):
    # Tukar admission day dan/atau room
    # Berguna untuk mengurangi resource conflicts
    temp_day = patient1.admission_day
    temp_room = patient1.room_id

    if (can_assign_patient(patient1, patient2.admission_day, patient2.room_id, patient1.operating_theater) and
        can_assign_patient(patient2, temp_day, temp_room, patient2.operating_theater)):

        patient1.admission_day = patient2.admission_day
        patient1.room_id = patient2.room_id
        patient2.admission_day = temp_day
        patient2.room_id = temp_room
        return True
    return False
```

#### Operator 5: Reassign Nurse
```python
def reassign_nurse(room, shift_idx):
    # Ubah nurse assignment untuk room tertentu pada shift tertentu
    day = shift_idx // 3
    shift_type_idx = shift_idx % 3
    shift_type = ["early", "late", "night"][shift_type_idx]

    # Cari nurse yang available pada shift ini
    available_nurses = [
        n for n in nurses 
        if any(ws.day == day and ws.shift == shift_type for ws in n.working_shifts)
    ]

    new_nurse = random.choice(available_nurses)
    assign_nurse_to_room(new_nurse, room, shift_idx)
    return True
```

### 4. Delta Evaluation (Incremental Cost Calculation)

Untuk efisiensi, hitung perubahan cost secara incremental:

```python
def delta_cost_reschedule(patient, new_day):
    old_cost = calculate_patient_cost(patient)

    # Simulate change
    old_day = patient.admission_day
    patient.admission_day = new_day

    new_cost = calculate_patient_cost(patient)

    # Restore
    patient.admission_day = old_day

    return new_cost - old_cost
```

### 5. Constraint Handling Strategies

**Penalty Method untuk Hard Constraints**:
```python
def objective_with_penalties(solution):
    soft_cost = calculate_soft_constraints_cost(solution)
    hard_violations = count_hard_constraint_violations(solution)

    # Big penalty untuk hard constraint violations
    PENALTY_MULTIPLIER = 1000000

    return soft_cost + PENALTY_MULTIPLIER * hard_violations
```

**Repair Heuristics**:
```python
def repair_solution(solution):
    # 1. Fix H5: Schedule all mandatory patients
    for patient in unscheduled_mandatory:
        force_assign_patient(patient)

    # 2. Fix H7: Resolve room capacity violations
    while has_room_capacity_violation():
        resolve_capacity_conflict()

    # 3. Fix H1: Resolve gender mix
    while has_gender_mix():
        move_conflicting_patient()

    # 4. Fix H3, H4: Resolve overtime
    while has_overtime_violation():
        reschedule_or_reassign_surgery()

    # 5. Fix H8: Ensure nurse coverage
    for room, shift in uncovered_rooms:
        assign_available_nurse(room, shift)

    return solution
```

---

## TIPS IMPLEMENTASI

### 1. Data Structure Design

```python
class Solution:
    def __init__(self):
        self.patient_assignments = {}  # patient_id -> {day, room, ot}
        self.nurse_assignments = {}    # (nurse_id, shift_idx) -> [room_ids]
        self.room_occupancy = {}       # (room_id, day) -> [patient_ids]
        self.ot_schedule = {}          # (ot_id, day) -> [patient_ids]
        self.surgeon_schedule = {}     # (surgeon_id, day) -> [patient_ids]
```

### 2. Caching dan Memoization

Cache hasil perhitungan yang sering digunakan:
```python
# Cache room occupancy per day
self.room_occupancy_cache = {}

# Cache surgeon daily workload
self.surgeon_workload_cache = {}

# Invalidate cache saat ada perubahan
def update_patient_assignment(self, patient, new_day, new_room):
    # Clear affected cache entries
    self.invalidate_cache(patient, new_day, new_room)
```

### 3. Prioritization

**Urutan prioritas dalam construction**:
1. Mandatory patients dengan due_day paling awal
2. Patients dengan window sempit (due_day - release_day kecil)
3. Patients dengan surgery_duration besar (resource-intensive)
4. Optional patients

### 4. Decomposition

Pecah problem menjadi subproblem yang lebih kecil:
- **Stage 1**: Patient admission scheduling (days + rooms)
- **Stage 2**: OT assignment
- **Stage 3**: Nurse assignment
- **Stage 4**: Local improvement

### 5. Validation

Selalu validate solution sebelum dan sesudah operator:
```python
def validate_solution(solution):
    assert count_hard_violations(solution) == 0, "Hard constraints violated!"
    assert all_mandatory_scheduled(solution), "Mandatory patient not scheduled!"
    assert all_rooms_covered(solution), "Uncovered room exists!"
```

---

## METAHEURISTIC CONSIDERATIONS

### Tabu Search
- **Tabu list**: Simpan (patient_id, old_value, new_value) sebagai tabu
- **Tenure**: 7-14 iterations (adaptive based on problem size)
- **Aspiration criterion**: Accept jika best solution found

### Simulated Annealing
- **Initial temperature**: \(T_0\) = average cost delta from random moves
- **Cooling schedule**: \(T_{k+1} = \alpha \times T_k\) dengan \(\alpha\) = 0.95-0.99
- **Acceptance probability**: \(P = e^{-\Delta / T}\)

### Genetic Algorithm
- **Encoding**: Direct representation (admission_day, room_id, ot_id per patient)
- **Crossover**: Partial solutions exchange (misalnya: day assignment dari parent1, room dari parent2)
- **Mutation**: Random reschedule/reassign operators

### Variable Neighborhood Search (VNS)
- **Neighborhoods**:
  1. N1: Reschedule 1 patient
  2. N2: Change room for 1 patient
  3. N3: Swap 2 patients
  4. N4: Reschedule + change room
  5. N5: Reassign nurses for 1 day

---

## DEBUGGING CHECKLIST

- [ ] Semua mandatory patients scheduled?
- [ ] Admission days dalam valid window [release, due]?
- [ ] Tidak ada gender mix dalam room?
- [ ] Room capacity tidak dilanggar setiap hari?
- [ ] Tidak ada incompatible room assignment?
- [ ] Surgeon max_surgery_time tidak exceeded?
- [ ] OT availability tidak exceeded?
- [ ] Setiap occupied room punya nurse assigned?
- [ ] Nurse hanya assigned pada shift di roster mereka?
- [ ] Workload array indexing correct (3 × LOS)?
- [ ] Occupants di-handle dengan benar?
- [ ] Soft constraint calculations mengikuti formula?

---

## REFERENSI

- Problem specification: IHTC 2024 Competition
- Validator: Gunakan official C++ validator untuk verifikasi
- Website: https://ihtc2024.github.io

---

**Last Updated**: January 2026
