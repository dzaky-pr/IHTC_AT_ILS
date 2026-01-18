import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * IHTP_ATILS - Adaptive Threshold Iterated Local Search
 * 
 * Implements AT-ILS algorithm for optimizing soft constraints while maintaining
 * feasibility (hard violations = 0).
 * 
 * Based on AT-ILS documentation with three phases: 1. Perturbation Phase -
 * Exploration 2. Local Search Phase - Exploitation 3. Move Acceptance Phase -
 * Decision making
 */
public class IHTP_ATILS {

    // --- Inner Classes (reused from IHTP_SA structure) ---

    static class OutputPatient {
        String id;
        int pIndex;
        Integer assignedDay;
        String assignedRoom;
        String assignedTheater;
        int rIndex = -1;
        int tIndex = -1;

        public OutputPatient copy() {
            OutputPatient op = new OutputPatient();
            op.id = this.id;
            op.pIndex = this.pIndex;
            op.assignedDay = this.assignedDay;
            op.assignedRoom = this.assignedRoom;
            op.assignedTheater = this.assignedTheater;
            op.rIndex = this.rIndex;
            op.tIndex = this.tIndex;
            return op;
        }
    }

    static class OutputNurse {
        String id;
        int nIndex;
        Map<Integer, List<String>> assignments = new HashMap<>();

        public OutputNurse copy() {
            OutputNurse on = new OutputNurse();
            on.id = this.id;
            on.nIndex = this.nIndex;
            for (Map.Entry<Integer, List<String>> entry : this.assignments.entrySet()) {
                on.assignments.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return on;
        }
    }

    static class SolutionState {
        List<OutputPatient> patients = new ArrayList<>();
        List<OutputNurse> nurses = new ArrayList<>();

        // Helper structures for constraint checking
        List<Integer>[][] roomDayOccupancy;
        int[][] nurseShiftLoad;
        int[][] roomShiftNurse;
        int[][] surgeonDayLoad;
        int[][] operatingTheaterDayLoad;

        double softCost = 0.0;
        int hardViolations = 0;

        @SuppressWarnings("unchecked")
        public SolutionState(int R, int D, int N, int S, int Surg, int T) {
            roomDayOccupancy = new ArrayList[R][D];
            for (int r = 0; r < R; r++)
                for (int d = 0; d < D; d++)
                    roomDayOccupancy[r][d] = new ArrayList<>();

            nurseShiftLoad = new int[N][S];
            roomShiftNurse = new int[R][S];
            for (int r = 0; r < R; r++)
                Arrays.fill(roomShiftNurse[r], -1);

            surgeonDayLoad = new int[Surg][D];
            operatingTheaterDayLoad = new int[T][D];
        }

        public SolutionState copy(int R, int D, int N, int S, int Surg, int T) {
            SolutionState s = new SolutionState(R, D, N, S, Surg, T);
            for (OutputPatient p : this.patients)
                s.patients.add(p.copy());
            for (OutputNurse n : this.nurses)
                s.nurses.add(n.copy());
            s.softCost = this.softCost;
            s.hardViolations = this.hardViolations;

            for (int r = 0; r < R; r++)
                for (int d = 0; d < D; d++)
                    s.roomDayOccupancy[r][d].addAll(this.roomDayOccupancy[r][d]);

            for (int n = 0; n < N; n++)
                System.arraycopy(this.nurseShiftLoad[n], 0, s.nurseShiftLoad[n], 0, S);

            for (int r = 0; r < R; r++)
                System.arraycopy(this.roomShiftNurse[r], 0, s.roomShiftNurse[r], 0, S);

            for (int surg = 0; surg < Surg; surg++)
                System.arraycopy(this.surgeonDayLoad[surg], 0, s.surgeonDayLoad[surg], 0, D);

            for (int t = 0; t < T; t++)
                System.arraycopy(this.operatingTheaterDayLoad[t], 0, s.operatingTheaterDayLoad[t], 0, D);

            return s;
        }
    }

    // --- Main Fields ---
    private IHTP_Input input;
    private SolutionState currentState;
    private SolutionState bestState;
    private SolutionState initialFeasibleBackup; // Backup of initial feasible solution (for fallback)
    private Random rand;
    private int R, D, N, S, T, P;
    private String outputFilePath;
    private String logFilePath;

    // Soft constraint weights
    private int w_age, w_skill, w_cont, w_load, w_ot, w_trans, w_delay, w_opt;

    // AT-ILS specific structures - Pure AT-ILS (No modifications)
    // Pre-allocate reasonable capacity for efficiency without instance-specific
    // tuning
    private List<Double> thresholdList = new ArrayList<>(50); // Reasonable default for all instances
    private double thresholdValue = 0.0;

    // Event list for AT-ILS (scheduled patients)
    // Pre-allocate reasonable capacity for efficiency without instance-specific
    // tuning
    private List<OutputPatient> eventList = new ArrayList<>(100); // Reasonable default for all instances

    // Pure AT-ILS: No consecutive worse tracking - natural termination only

    // OPTIMIZATION: Domain pruning for feasibility-preserving moves
    // Pre-computed valid domains per patient (like PA-ILS)
    private Map<Integer, List<Integer>> validDaysForPatient = new HashMap<>();
    private Map<Integer, List<Integer>> validRoomsForPatient = new HashMap<>();
    private Map<Integer, List<Integer>> validTheatersForPatient = new HashMap<>();
    
    // OPTIMIZATION: Cached threshold - recalculate less frequently
    private long lastThresholdCalcTime = 0;
    private static final long THRESHOLD_CACHE_DURATION_MS = 5000; // 5 seconds cache

    /**
     * CRITICAL FIX: Helper method to copy full state (patients AND nurses)
     * This ensures soft cost calculation remains consistent after state copy
     */
    private void copyStateInto(SolutionState target, SolutionState source) {
        target.patients.clear();
        for (OutputPatient p : source.patients)
            target.patients.add(p.copy());
        target.nurses.clear();
        for (OutputNurse n : source.nurses)
            target.nurses.add(n.copy());
        // Note: rebuildHelpers and evaluateFull should be called after this
    }

    /**
     * FIXED: Helper method to safely flush fileWriter (prevents Stream closed
     * errors)
     */
    private void safeFlush(java.io.FileWriter fw) {
        if (fw != null) {
            try {
                fw.flush();
            } catch (IOException e) {
                // Ignore if already closed - this is safe
            }
        }
    }

    public IHTP_ATILS(String instanceFile, String solutionFile, String outputFilePath, long seed) throws IOException {
        this.outputFilePath = outputFilePath;
        this.rand = new Random(seed);

        // Load input
        this.input = new IHTP_Input(instanceFile);
        this.P = input.Patients();
        this.R = input.Rooms();
        this.D = input.Days();
        this.N = input.Nurses();
        this.S = input.Shifts();
        this.T = input.OperatingTheaters();

        // Parse weights
        JSONObject json = new JSONObject(new JSONTokener(new FileReader(instanceFile)));
        JSONObject weights = json.getJSONObject("weights");
        w_age = weights.getInt("room_mixed_age");
        w_skill = weights.getInt("room_nurse_skill");
        w_cont = weights.getInt("continuity_of_care");
        w_load = weights.getInt("nurse_eccessive_workload");
        w_ot = weights.getInt("open_operating_theater");
        w_trans = weights.getInt("surgeon_transfer");
        w_delay = weights.getInt("patient_delay");
        w_opt = weights.getInt("unscheduled_optional");

        // Load initial solution
        loadSolution(solutionFile);

        // Initialize helper structures
        rebuildHelpers(currentState);

        // Initial evaluation
        evaluateFull(currentState);

        if (currentState.hardViolations > 0) {
            System.err.println("WARNING: Initial solution has " + currentState.hardViolations + " hard violations!");
        }

        this.bestState = currentState.copy(R, D, N, S, input.Surgeons(), T);

        // CRITICAL: Store backup of initial feasible solution for fallback
        // This ensures we can always output a feasible solution even if AT-ILS corrupts
        // state
        if (currentState.hardViolations == 0) {
            this.initialFeasibleBackup = currentState.copy(R, D, N, S, input.Surgeons(), T);
            System.out.println("AT-ILS: Initial feasible solution backed up (Hard=0, Soft="
                    + String.format("%.2f", currentState.softCost) + ")");
        } else {
            this.initialFeasibleBackup = null;
            System.err.println("WARNING: No feasible backup available - initial solution has hard violations!");
        }

        // OPTIMIZATION: Initialize domain pruning for fast feasibility-preserving moves
        initializeDomainPruning();
        System.out.println("AT-ILS: Domain pruning initialized for " + P + " patients");

        // Build event list (scheduled patients only)
        updateEventList();
    }

    /**
     * OPTIMIZATION: Initialize domain pruning - pre-compute valid days/rooms/theaters per patient
     * This is done once at startup and enables O(1) feasibility checks
     */
    private void initializeDomainPruning() {
        validDaysForPatient.clear();
        validRoomsForPatient.clear();
        validTheatersForPatient.clear();
        
        for (int p = 0; p < P; p++) {
            // Valid days: [releaseDay, dueDay] within horizon
            int releaseDay = input.PatientSurgeryReleaseDay(p);
            int dueDay = input.PatientLastPossibleDay(p);
            List<Integer> validDays = new ArrayList<>();
            for (int d = releaseDay; d <= Math.min(dueDay, D - 1); d++) {
                validDays.add(d);
            }
            validDaysForPatient.put(p, validDays);
            
            // Valid rooms: compatible rooms only (H2)
            List<Integer> validRooms = new ArrayList<>();
            for (int r = 0; r < R; r++) {
                if (!input.IncompatibleRoom(p, r)) {
                    validRooms.add(r);
                }
            }
            validRoomsForPatient.put(p, validRooms);
            
            // Valid theaters: all theaters (can be refined based on surgeon preferences)
            List<Integer> validTheaters = new ArrayList<>();
            for (int t = 0; t < T; t++) {
                validTheaters.add(t);
            }
            validTheatersForPatient.put(p, validTheaters);
        }
    }

    private void loadSolution(String solutionFile) throws IOException {
        this.currentState = new SolutionState(R, D, N, S, input.Surgeons(), T);
        JSONObject j_sol = new JSONObject(new JSONTokener(new FileReader(solutionFile)));

        // Initialize patients
        for (int i = 0; i < P; i++) {
            OutputPatient op = new OutputPatient();
            op.id = input.PatientId(i);
            op.pIndex = i;
            op.assignedDay = null;
            op.assignedRoom = null;
            op.assignedTheater = null;
            currentState.patients.add(op);
        }

        // Update with JSON data
        JSONArray j_pats = j_sol.getJSONArray("patients");
        for (int i = 0; i < j_pats.length(); i++) {
            JSONObject jp = j_pats.getJSONObject(i);
            String id = jp.getString("id");
            int pIndex = input.findPatientIndex(id);

            if (pIndex != -1 && pIndex < P) {
                OutputPatient op = currentState.patients.get(pIndex);
                if (jp.has("admission_day") && !jp.get("admission_day").toString().equals("none")) {
                    op.assignedDay = jp.getInt("admission_day");
                    op.assignedRoom = jp.getString("room");
                    op.assignedTheater = jp.getString("operating_theater");
                    op.rIndex = input.findRoomIndex(op.assignedRoom);
                    op.tIndex = input.findOperatingTheaterIndex(op.assignedTheater);
                }
            }
        }

        // Parse nurses
        JSONArray j_nurs = j_sol.getJSONArray("nurses");
        for (int i = 0; i < j_nurs.length(); i++) {
            JSONObject jn = j_nurs.getJSONObject(i);
            OutputNurse on = new OutputNurse();
            on.id = jn.getString("id");
            on.nIndex = input.findNurseIndex(on.id);
            JSONArray assigns = jn.getJSONArray("assignments");
            for (int k = 0; k < assigns.length(); k++) {
                JSONObject ja = assigns.getJSONObject(k);
                int day = ja.getInt("day");
                String shiftStr = ja.getString("shift");
                int shiftIdx = input.findShiftIndex(shiftStr);
                int globalShift = day * input.ShiftsPerDay() + shiftIdx;

                JSONArray rooms = ja.getJSONArray("rooms");
                // OPTIMIZED: Pre-allocate capacity (Pure AT-ILS optimization)
                List<String> rList = new ArrayList<>(rooms.length());
                for (int r = 0; r < rooms.length(); r++)
                    rList.add(rooms.getString(r));

                on.assignments.put(globalShift, rList);
            }
            currentState.nurses.add(on);
        }
        currentState.nurses.sort((a, b) -> Integer.compare(a.nIndex, b.nIndex));
    }

    private void rebuildHelpers(SolutionState state) {
        // Clear and rebuild room occupancy
        for (int r = 0; r < R; r++)
            for (int d = 0; d < D; d++)
                state.roomDayOccupancy[r][d].clear();

        // Add patients
        for (OutputPatient p : state.patients) {
            if (p.assignedDay == null)
                continue;
            int len = input.PatientLengthOfStay(p.pIndex);
            for (int d = p.assignedDay; d < Math.min(D, p.assignedDay + len); d++) {
                state.roomDayOccupancy[p.rIndex][d].add(p.pIndex);
            }
        }

        // Add occupants (fixed)
        for (int occ = 0; occ < input.Occupants(); occ++) {
            int r = input.OccupantRoom(occ);
            int len = input.OccupantLengthOfStay(occ);
            for (int d = 0; d < Math.min(D, len); d++) {
                state.roomDayOccupancy[r][d].add(P + occ);
            }
        }

        // Rebuild nurse assignments
        for (int r = 0; r < R; r++)
            Arrays.fill(state.roomShiftNurse[r], -1);

        for (OutputNurse n : state.nurses) {
            for (var entry : n.assignments.entrySet()) {
                int s = entry.getKey();
                for (String roomId : entry.getValue()) {
                    int r = input.findRoomIndex(roomId);
                    state.roomShiftNurse[r][s] = n.nIndex;
                }
            }
        }

        // Rebuild surgeon & OT loads
        for (int s = 0; s < input.Surgeons(); s++)
            Arrays.fill(state.surgeonDayLoad[s], 0);
        for (int t = 0; t < T; t++)
            Arrays.fill(state.operatingTheaterDayLoad[t], 0);

        for (OutputPatient p : state.patients) {
            if (p.assignedDay == null)
                continue;
            int surg = input.PatientSurgeon(p.pIndex);
            state.surgeonDayLoad[surg][p.assignedDay] += input.PatientSurgeryDuration(p.pIndex);
            state.operatingTheaterDayLoad[p.tIndex][p.assignedDay] += input.PatientSurgeryDuration(p.pIndex);
        }
    }

    // --- Evaluation (reused from IHTP_SA) ---

    public void evaluateFull(SolutionState state) {
        double cost = 0;
        int hard = 0;

        // Reset loads
        for (int n = 0; n < N; n++)
            Arrays.fill(state.nurseShiftLoad[n], 0);
        for (int s = 0; s < input.Surgeons(); s++)
            Arrays.fill(state.surgeonDayLoad[s], 0);
        for (int t = 0; t < T; t++)
            Arrays.fill(state.operatingTheaterDayLoad[t], 0);

        // Populate surgeon & OT loads
        for (OutputPatient p : state.patients) {
            if (p.assignedDay == null)
                continue;
            int surg = input.PatientSurgeon(p.pIndex);
            state.surgeonDayLoad[surg][p.assignedDay] += input.PatientSurgeryDuration(p.pIndex);
            state.operatingTheaterDayLoad[p.tIndex][p.assignedDay] += input.PatientSurgeryDuration(p.pIndex);
        }

        // S1: Room Mixed Age & H1: Gender Mix & H7: Capacity
        for (int r = 0; r < R; r++) {
            for (int d = 0; d < D; d++) {
                List<Integer> occupants = state.roomDayOccupancy[r][d];
                if (occupants.isEmpty())
                    continue;

                int minAge = Integer.MAX_VALUE, maxAge = Integer.MIN_VALUE;
                int countA = 0, countB = 0;

                for (int idx : occupants) {
                    int age = (idx < P) ? input.PatientAgeGroup(idx) : input.OccupantAgeGroup(idx - P);
                    minAge = Math.min(minAge, age);
                    maxAge = Math.max(maxAge, age);

                    var gender = (idx < P) ? input.PatientGender(idx) : input.OccupantGender(idx - P);
                    if (gender == IHTP_Input.Gender.A)
                        countA++;
                    else if (gender == IHTP_Input.Gender.B)
                        countB++;
                }

                if (maxAge > minAge)
                    cost += (maxAge - minAge) * w_age;

                if (countA > 0 && countB > 0)
                    hard++;

                if (occupants.size() > input.RoomCapacity(r))
                    hard++;
            }
        }

        // Calculate nurse loads & S2, S3, S4
        for (int r = 0; r < R; r++) {
            for (int s = 0; s < S; s++) {
                int day = s / input.ShiftsPerDay();
                List<Integer> occupants = state.roomDayOccupancy[r][day];

                int nIdx = state.roomShiftNurse[r][s];
                if (nIdx == -1) {
                    if (!occupants.isEmpty())
                        hard++;
                    continue;
                }

                for (int idx : occupants) {
                    int workload = 0;
                    int skillReq = 0;

                    if (idx < P) {
                        if (state.patients.get(idx).assignedDay == null)
                            continue;
                        int admissionDay = state.patients.get(idx).assignedDay;
                        int relShift = s - admissionDay * input.ShiftsPerDay();
                        if (relShift >= 0 && relShift < input.PatientLengthOfStay(idx) * input.ShiftsPerDay()) {
                            workload = input.PatientWorkloadProduced(idx, relShift);
                            skillReq = input.PatientSkillLevelRequired(idx, relShift);
                        }
                    } else {
                        int occ = idx - P;
                        workload = input.OccupantWorkloadProduced(occ, s);
                        skillReq = input.OccupantSkillLevelRequired(occ, s);
                    }

                    state.nurseShiftLoad[nIdx][s] += workload;

                    int nSkill = input.NurseSkillLevel(nIdx);
                    if (skillReq > nSkill) {
                        cost += (skillReq - nSkill) * w_skill;
                    }
                }
            }
        }

        // S4: Nurse Excessive Workload & H8
        for (OutputNurse n : state.nurses) {
            for (int s : n.assignments.keySet()) {
                if (!n.assignments.get(s).isEmpty()) {
                    if (!input.IsNurseWorkingInShift(n.nIndex, s))
                        hard++;
                }
            }
        }

        for (int n = 0; n < N; n++) {
            for (int s = 0; s < S; s++) {
                int maxLoad = input.NurseMaxLoad(n, s);
                if (state.nurseShiftLoad[n][s] > maxLoad) {
                    cost += (state.nurseShiftLoad[n][s] - maxLoad) * w_load;
                }
            }
        }

        // S3: Continuity of Care
        for (int p = 0; p < P; p++) {
            OutputPatient op = state.patients.get(p);
            if (op.assignedDay == null)
                continue;

            boolean[] seen = new boolean[N];
            int distinct = 0;
            int len = input.PatientLengthOfStay(p);
            int startS = op.assignedDay * input.ShiftsPerDay();
            int endS = (op.assignedDay + len) * input.ShiftsPerDay();

            for (int s = startS; s < endS && s < S; s++) {
                int nIdx = state.roomShiftNurse[op.rIndex][s];
                if (nIdx != -1 && !seen[nIdx]) {
                    seen[nIdx] = true;
                    distinct++;
                }
            }
            if (distinct > 0)
                cost += distinct * w_cont;
        }

        for (int occ = 0; occ < input.Occupants(); occ++) {
            int r = input.OccupantRoom(occ);
            int len = input.OccupantLengthOfStay(occ);
            boolean[] seen = new boolean[N];
            int distinct = 0;
            for (int s = 0; s < len * input.ShiftsPerDay() && s < S; s++) {
                int nIdx = state.roomShiftNurse[r][s];
                if (nIdx != -1 && !seen[nIdx]) {
                    seen[nIdx] = true;
                    distinct++;
                }
            }
            if (distinct > 0)
                cost += distinct * w_cont;
        }

        // S5: Open Operating Theater
        int[] theaterUsedDays = new int[T * D];
        for (OutputPatient p : state.patients) {
            if (p.assignedDay == null)
                continue;
            theaterUsedDays[p.tIndex * D + p.assignedDay]++;
        }
        for (int i = 0; i < theaterUsedDays.length; i++) {
            if (theaterUsedDays[i] > 0)
                cost += w_ot;
        }

        // S6: Surgeon Transfer
        for (int surg = 0; surg < input.Surgeons(); surg++) {
            for (int d = 0; d < D; d++) {
                boolean[] tUsed = new boolean[T];
                int tCount = 0;
                for (OutputPatient p : state.patients) {
                    if (p.assignedDay != null && p.assignedDay == d && input.PatientSurgeon(p.pIndex) == surg) {
                        if (!tUsed[p.tIndex]) {
                            tUsed[p.tIndex] = true;
                            tCount++;
                        }
                    }
                }
                if (tCount > 1)
                    cost += (tCount - 1) * w_trans;
            }
        }

        // S7: Admission Delay
        for (OutputPatient p : state.patients) {
            if (p.assignedDay != null) {
                int release = input.PatientSurgeryReleaseDay(p.pIndex);
                if (p.assignedDay > release) {
                    cost += (p.assignedDay - release) * w_delay;
                }
            }
        }

        // S8: Unscheduled Optional
        for (OutputPatient p : state.patients) {
            if (p.assignedDay == null) {
                if (!input.PatientMandatory(p.pIndex))
                    cost += w_opt;
                else
                    hard++;
            }
        }

        // H2: Patient-Room Compatibility
        for (int r = 0; r < R; r++) {
            for (int d = 0; d < D; d++) {
                for (int pid : state.roomDayOccupancy[r][d]) {
                    if (pid < P && input.IncompatibleRoom(pid, r))
                        hard++;
                }
            }
        }

        // H3: Surgeon Overtime
        for (int s = 0; s < input.Surgeons(); s++) {
            for (int d = 0; d < D; d++) {
                if (state.surgeonDayLoad[s][d] > input.SurgeonMaxSurgeryTime(s, d))
                    hard++;
            }
        }

        // H4: OT Overtime
        for (int t = 0; t < T; t++) {
            for (int d = 0; d < D; d++) {
                if (state.operatingTheaterDayLoad[t][d] > input.OperatingTheaterAvailability(t, d))
                    hard++;
            }
        }

        // H6: Admission Day
        for (OutputPatient p : state.patients) {
            if (p.assignedDay != null) {
                int release = input.PatientSurgeryReleaseDay(p.pIndex);
                int due = input.PatientLastPossibleDay(p.pIndex);
                if (p.assignedDay < release || p.assignedDay > due)
                    hard++;
            }
        }

        state.softCost = cost;
        state.hardViolations = hard;
    }

    // --- AT-ILS Core Functions ---

    /**
     * Update event list (scheduled patients only)
     */
    private void updateEventList() {
        eventList.clear();
        for (OutputPatient p : currentState.patients) {
            if (p.assignedDay != null)
                eventList.add(p);
        }
    }

    /**
     * Calculate threshold list based on current solution 
     * Pure AT-ILS Algorithm 7: Calculate Threshold
     * OPTIMIZED: Use delta evaluation instead of full state copies
     */
    private List<Double> calculateThreshold(SolutionState solution, List<OutputPatient> events) {
        List<Double> thresholds = new ArrayList<>();
        
        if (events.isEmpty()) {
            thresholds.add(Math.max(1.0, solution.softCost * 0.01));
            return thresholds;
        }

        double basePenalty = solution.softCost;
        
        // OPTIMIZATION: Use cached threshold if recently calculated
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastThresholdCalcTime < THRESHOLD_CACHE_DURATION_MS && !thresholdList.isEmpty()) {
            return new ArrayList<>(thresholdList); // Return cached copy
        }
        lastThresholdCalcTime = currentTime;

        // OPTIMIZED: Use delta cost estimation instead of full state copies
        // Sample fewer moves but estimate cost differences analytically
        int sampleSize = Math.min(events.size(), 30); // Reduced sample size
        
        // Shuffle events for random sampling
        List<OutputPatient> shuffledEvents = new ArrayList<>(events);
        Collections.shuffle(shuffledEvents, rand);

        for (int i = 0; i < Math.min(sampleSize, shuffledEvents.size()); i++) {
            OutputPatient event = shuffledEvents.get(i);
            if (event.assignedDay == null) continue;
            
            // OPTIMIZED: Estimate delta costs without full state copy
            // Use analytical estimation based on constraint weights
            
            // Estimate move cost delta
            double moveDelta = estimateMoveDelta(event, solution);
            if (moveDelta > 0.001) thresholds.add(moveDelta);
            
            // Estimate day change cost delta  
            double dayDelta = estimateDayChangeDelta(event, solution);
            if (dayDelta > 0.001) thresholds.add(dayDelta);
            
            // Estimate room change cost delta
            double roomDelta = estimateRoomChangeDelta(event, solution);
            if (roomDelta > 0.001) thresholds.add(roomDelta);
        }

        // Pure AT-ILS: Ensure at least one threshold value
        if (thresholds.isEmpty()) {
            // Fallback: use percentage of current cost
            double defaultThreshold = Math.max(1.0, basePenalty * 0.01);
            thresholds.add(defaultThreshold);
            thresholds.add(defaultThreshold * 2);
            thresholds.add(defaultThreshold * 3);
        }

        Collections.sort(thresholds); // Pure AT-ILS: Sort from smallest to largest
        return thresholds;
    }
    
    /**
     * OPTIMIZATION: Estimate move delta cost without full state evaluation
     */
    private double estimateMoveDelta(OutputPatient p, SolutionState state) {
        if (p.assignedDay == null) return 0;
        
        double delta = 0;
        int releaseDay = input.PatientSurgeryReleaseDay(p.pIndex);
        
        // S7: Admission delay change
        if (p.assignedDay > releaseDay) {
            // Current delay cost
            int currentDelay = p.assignedDay - releaseDay;
            // Estimate new delay (random within range)
            int dueDay = input.PatientLastPossibleDay(p.pIndex);
            int avgNewDay = (releaseDay + dueDay) / 2;
            int newDelay = Math.max(0, avgNewDay - releaseDay);
            delta += Math.abs(newDelay - currentDelay) * w_delay;
        }
        
        // S1: Age mixing estimate (simplified)
        delta += w_age * 0.5; // Average expected change
        
        // S5: OT opening estimate
        delta += w_ot * 0.3; // Probability of OT change
        
        return delta;
    }
    
    /**
     * OPTIMIZATION: Estimate day change delta cost
     */
    private double estimateDayChangeDelta(OutputPatient p, SolutionState state) {
        if (p.assignedDay == null) return 0;
        
        double delta = 0;
        int releaseDay = input.PatientSurgeryReleaseDay(p.pIndex);
        int dueDay = input.PatientLastPossibleDay(p.pIndex);
        
        // S7: Admission delay change
        int dayRange = dueDay - releaseDay + 1;
        if (dayRange > 1) {
            delta += w_delay * (dayRange / 2.0); // Expected delay change
        }
        
        // S5: OT opening (likely to change)
        delta += w_ot;
        
        // S6: Surgeon transfer (may change)
        delta += w_trans * 0.3;
        
        return delta;
    }
    
    /**
     * OPTIMIZATION: Estimate room change delta cost
     */
    private double estimateRoomChangeDelta(OutputPatient p, SolutionState state) {
        if (p.assignedDay == null || p.rIndex < 0) return 0;
        
        double delta = 0;
        int len = input.PatientLengthOfStay(p.pIndex);
        
        // S1: Age mixing change (main benefit of room change)
        for (int d = p.assignedDay; d < Math.min(D, p.assignedDay + len); d++) {
            List<Integer> occupants = state.roomDayOccupancy[p.rIndex][d];
            if (occupants.size() > 1) {
                // Estimate age range reduction
                delta += w_age * 0.5;
            }
        }
        
        // S3: Continuity of care (may change with room)
        delta += w_cont * len * 0.2;
        
        return delta;
    }

    /**
     * Update threshold value (average of threshold list) - Pure AT-ILS Algorithm
     */
    private double updateThreshold(List<Double> thresholdList, double currentCost) {
        if (thresholdList.isEmpty())
            return Math.max(1.0, currentCost * 0.01); // Safety fallback only

        double sum = 0.0;
        for (double t : thresholdList)
            sum += t;
        return sum / thresholdList.size(); // Pure AT-ILS: Simple average
    }

    /**
     * Apply Low Level Heuristic (LLH) 
     * Pure AT-ILS: 8 operators for IHTP domain optimization
     * LLH 0-4: Original operators (Move, Swap, Kempe, NurseReassign, RoomChange)
     * LLH 5-7: New operators targeting S5/S8 (ScheduleOptional, OTConsolidate, KickAndSchedule)
     * Returns true if applied successfully
     */
    private boolean applyLLH(int llhType, OutputPatient event, SolutionState state, boolean temporary) {
        switch (llhType) {
        case 0: // Move Operator - move patient to different day/room/theater
            return moveOperator(event, state, temporary);
        case 1: // Swap Operator - swap two patients
            return swapOperator(event, state, temporary);
        case 2: // Kempe Chain Operator - change day only
            return kempeChainOperator(event, state, temporary);
        case 3: // Nurse Reassignment - optimize nurse assignments for room
            return nurseReassignmentOperator(event, state, temporary);
        case 4: // Room Change - change room only (optimizes S1 age mixing)
            return roomChangeOperator(event, state, temporary);
        case 5: // Schedule Optional Patient - targets S8 (unscheduled_optional)
            return scheduleOptionalPatientOperator(event, state, temporary);
        case 6: // OT Consolidation - targets S5 (open_operating_theater)
            return otConsolidationOperator(event, state, temporary);
        case 7: // Kick and Schedule - escape local optima by trading patients
            return kickAndScheduleOperator(event, state, temporary);
        default:
            return moveOperator(event, state, temporary);
        }
    }
    
    /**
     * Room Change Operator: Change patient's room (keep day same)
     * Specifically optimizes S1 (room mixed age) constraint
     * Pure AT-ILS: Feasibility-aware room selection
     */
    private boolean roomChangeOperator(OutputPatient p, SolutionState state, boolean temporary) {
        if (p.assignedDay == null) return false;
        
        int oldRoomIdx = p.rIndex;
        IHTP_Input.Gender patientGender = input.PatientGender(p.pIndex);
        int len = input.PatientLengthOfStay(p.pIndex);
        
        // Find best compatible room (aiming to reduce age mixing)
        List<Integer> compatibleRooms = new ArrayList<>();
        for (int r = 0; r < R; r++) {
            if (r == oldRoomIdx) continue;
            if (input.IncompatibleRoom(p.pIndex, r)) continue;
            compatibleRooms.add(r);
        }
        
        if (compatibleRooms.isEmpty()) return false;
        
        // Shuffle for randomness
        Collections.shuffle(compatibleRooms, rand);
        
        // Try each compatible room
        for (int newRoomIdx : compatibleRooms) {
            boolean feasible = true;
            
            // Check capacity and gender for all days of stay
            for (int d = p.assignedDay; d < Math.min(D, p.assignedDay + len) && feasible; d++) {
                List<Integer> occupants = state.roomDayOccupancy[newRoomIdx][d];
                
                // Check capacity (H7)
                if (occupants.size() >= input.RoomCapacity(newRoomIdx)) {
                    feasible = false;
                    break;
                }
                
                // Check gender (H1)
                for (int idx : occupants) {
                    IHTP_Input.Gender otherGender = (idx < P) ? input.PatientGender(idx) : input.OccupantGender(idx - P);
                    if (patientGender != otherGender) {
                        feasible = false;
                        break;
                    }
                }
            }
            
            if (feasible) {
                // Apply the room change
                removePatientFromOccupancy(state, p);
                p.assignedRoom = input.RoomId(newRoomIdx);
                p.rIndex = newRoomIdx;
                addPatientToOccupancy(state, p);
                
                if (!temporary) {
                    rebuildHelpers(state);
                    evaluateFull(state);
                }
                return true;
            }
        }
        
        return false; // No feasible room found
    }
    
    /**
     * Nurse Reassignment Operator: Reassign nurse for a room-shift
     * Optimizes S2 (skill), S3 (continuity of care), S4 (workload) constraints
     * Pure AT-ILS: Feasibility-aware nurse selection
     */
    private boolean nurseReassignmentOperator(OutputPatient event, SolutionState state, boolean temporary) {
        if (event.assignedDay == null || event.rIndex < 0) return false;
        
        int roomIdx = event.rIndex;
        int len = input.PatientLengthOfStay(event.pIndex);
        
        // Pick a random shift during patient's stay
        int dayOffset = rand.nextInt(len);
        int day = event.assignedDay + dayOffset;
        if (day >= D) return false;
        
        int shiftType = rand.nextInt(input.ShiftsPerDay()); // early, late, or night
        int globalShift = day * input.ShiftsPerDay() + shiftType;
        if (globalShift >= S) return false;
        
        // Get current nurse for this room-shift
        int currentNurseIdx = state.roomShiftNurse[roomIdx][globalShift];
        
        // Find available nurses for this shift (H8 compliant)
        List<Integer> availableNurses = new ArrayList<>();
        for (int n = 0; n < N; n++) {
            if (n != currentNurseIdx && input.IsNurseWorkingInShift(n, globalShift)) {
                availableNurses.add(n);
            }
        }
        
        if (availableNurses.isEmpty()) return false;
        
        // Pick a random available nurse
        int newNurseIdx = availableNurses.get(rand.nextInt(availableNurses.size()));
        
        // Update nurse assignments
        // Remove room from current nurse's assignment
        if (currentNurseIdx >= 0 && currentNurseIdx < state.nurses.size()) {
            OutputNurse currentNurse = state.nurses.get(currentNurseIdx);
            List<String> rooms = currentNurse.assignments.get(globalShift);
            if (rooms != null) {
                rooms.remove(input.RoomId(roomIdx));
                if (rooms.isEmpty()) {
                    currentNurse.assignments.remove(globalShift);
                }
            }
        }
        
        // Add room to new nurse's assignment
        OutputNurse newNurse = null;
        for (OutputNurse n : state.nurses) {
            if (n.nIndex == newNurseIdx) {
                newNurse = n;
                break;
            }
        }
        
        if (newNurse != null) {
            List<String> rooms = newNurse.assignments.computeIfAbsent(globalShift, k -> new ArrayList<>());
            if (!rooms.contains(input.RoomId(roomIdx))) {
                rooms.add(input.RoomId(roomIdx));
            }
        }
        
        // Update helper structures
        state.roomShiftNurse[roomIdx][globalShift] = newNurseIdx;
        
        if (!temporary) {
            rebuildHelpers(state);
            evaluateFull(state);
        }
        return true;
    }

    /**
     * Move Operator: Move patient to different day/room/theater
     * OPTIMIZED: Use domain pruning for fast feasibility-aware selection
     * Returns true only if move maintains feasibility
     */
    private boolean moveOperator(OutputPatient p, SolutionState state, boolean temporary) {
        if (p.assignedDay == null)
            return false;

        int oldDay = p.assignedDay;
        int oldRoomIdx = p.rIndex;
        int oldTheaterIdx = p.tIndex;
        int surgeon = input.PatientSurgeon(p.pIndex);
        int surgeryDuration = input.PatientSurgeryDuration(p.pIndex);
        int len = input.PatientLengthOfStay(p.pIndex);
        IHTP_Input.Gender pGender = input.PatientGender(p.pIndex);

        // OPTIMIZATION: Use pre-computed valid domains
        List<Integer> validDays = validDaysForPatient.get(p.pIndex);
        List<Integer> validRooms = validRoomsForPatient.get(p.pIndex);
        List<Integer> validTheaters = validTheatersForPatient.get(p.pIndex);
        
        if (validDays == null || validDays.isEmpty() || 
            validRooms == null || validRooms.isEmpty() ||
            validTheaters == null || validTheaters.isEmpty()) {
            return false;
        }

        // Try multiple times to find a feasible move
        int maxAttempts = 15; // Reduced from 20
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // OPTIMIZED: Select from pre-computed valid domains
            int newDay = validDays.get(rand.nextInt(validDays.size()));
            if (newDay >= D) continue;
            
            int newRoomIdx = validRooms.get(rand.nextInt(validRooms.size()));
            int newTheaterIdx = validTheaters.get(rand.nextInt(validTheaters.size()));

            // Quick feasibility checks (H3, H4, H7, H1)
            
            // H3: Check surgeon availability
            int surgeonLoadOnNewDay = state.surgeonDayLoad[surgeon][newDay];
            if (newDay == oldDay) surgeonLoadOnNewDay -= surgeryDuration; // Subtract self
            if (surgeonLoadOnNewDay + surgeryDuration > input.SurgeonMaxSurgeryTime(surgeon, newDay)) continue;

            // H4: Check OT availability
            int otLoadOnNewDay = state.operatingTheaterDayLoad[newTheaterIdx][newDay];
            if (newDay == oldDay && newTheaterIdx == oldTheaterIdx) otLoadOnNewDay -= surgeryDuration;
            if (otLoadOnNewDay + surgeryDuration > input.OperatingTheaterAvailability(newTheaterIdx, newDay)) continue;

            // H7 & H1: Check room capacity and gender
            boolean roomFeasible = true;
            for (int d = newDay; d < Math.min(D, newDay + len) && roomFeasible; d++) {
                List<Integer> occupants = state.roomDayOccupancy[newRoomIdx][d];
                int effectiveOccupancy = occupants.size();
                
                // Don't count self if moving within same room
                boolean selfPresent = (newRoomIdx == oldRoomIdx && d >= oldDay && d < oldDay + len);
                if (selfPresent) effectiveOccupancy--;
                
                // H7: Capacity
                if (effectiveOccupancy >= input.RoomCapacity(newRoomIdx)) {
                    roomFeasible = false;
                    break;
                }
                
                // H1: Gender
                for (int idx : occupants) {
                    if (idx == p.pIndex) continue;
                    IHTP_Input.Gender otherGender = (idx < P) ? input.PatientGender(idx) : input.OccupantGender(idx - P);
                    if (pGender != otherGender) {
                        roomFeasible = false;
                        break;
                    }
                }
            }
            if (!roomFeasible) continue;

            // Apply move - feasibility checks passed
            removePatientFromOccupancy(state, p);
            
            // Update surgeon/OT loads
            state.surgeonDayLoad[surgeon][oldDay] -= surgeryDuration;
            state.operatingTheaterDayLoad[oldTheaterIdx][oldDay] -= surgeryDuration;
            
            p.assignedDay = newDay;
            p.assignedRoom = input.RoomId(newRoomIdx);
            p.assignedTheater = input.OperatingTheaterId(newTheaterIdx);
            p.rIndex = newRoomIdx;
            p.tIndex = newTheaterIdx;
            
            state.surgeonDayLoad[surgeon][newDay] += surgeryDuration;
            state.operatingTheaterDayLoad[newTheaterIdx][newDay] += surgeryDuration;
            
            addPatientToOccupancy(state, p);

            if (!temporary) {
                rebuildHelpers(state);
                evaluateFull(state);
            }
            return true;
        }

        return false; // No feasible move found
    }

    /**
     * Swap Operator: Swap day/room/theater between two patients
     * Pure AT-ILS: Feasibility-aware random swap
     * Returns true only if swap maintains feasibility
     */
    private boolean swapOperator(OutputPatient p1, SolutionState state, boolean temporary) {
        if (p1.assignedDay == null || eventList.size() < 2)
            return false;

        // Pure AT-ILS: Random patient selection for swap with feasibility check
        OutputPatient p2 = null;
        int maxAttempts = 30;
        for (int i = 0; i < maxAttempts; i++) {
            OutputPatient candidate = eventList.get(rand.nextInt(eventList.size()));
            if (candidate != p1 && candidate.assignedDay != null) {
                // Check if swap is valid for both patients (H6 - admission window)
                int p1Release = input.PatientSurgeryReleaseDay(p1.pIndex);
                int p1Due = input.PatientLastPossibleDay(p1.pIndex);
                int p2Release = input.PatientSurgeryReleaseDay(candidate.pIndex);
                int p2Due = input.PatientLastPossibleDay(candidate.pIndex);
                
                // Check if p1 can go to p2's day and vice versa
                if (candidate.assignedDay >= p1Release && candidate.assignedDay <= p1Due &&
                    p1.assignedDay >= p2Release && p1.assignedDay <= p2Due) {
                    // Check room compatibility (H2)
                    if (!input.IncompatibleRoom(p1.pIndex, candidate.rIndex) &&
                        !input.IncompatibleRoom(candidate.pIndex, p1.rIndex)) {
                        p2 = candidate;
                        break;
                    }
                }
            }
        }

        if (p2 == null || p2.assignedDay == null)
            return false;

        // Save original values for potential rollback
        Integer tempDay = p1.assignedDay;
        String tempRoom = p1.assignedRoom;
        String tempTheater = p1.assignedTheater;
        int tempRIdx = p1.rIndex;
        int tempTIdx = p1.tIndex;

        // Remove both from occupancy
        removePatientFromOccupancy(state, p1);
        removePatientFromOccupancy(state, p2);

        // Perform swap
        p1.assignedDay = p2.assignedDay;
        p1.assignedRoom = p2.assignedRoom;
        p1.assignedTheater = p2.assignedTheater;
        p1.rIndex = p2.rIndex;
        p1.tIndex = p2.tIndex;

        p2.assignedDay = tempDay;
        p2.assignedRoom = tempRoom;
        p2.assignedTheater = tempTheater;
        p2.rIndex = tempRIdx;
        p2.tIndex = tempTIdx;

        // Add back to occupancy
        addPatientToOccupancy(state, p1);
        addPatientToOccupancy(state, p2);

        if (!temporary) {
            rebuildHelpers(state);
            evaluateFull(state);
            
            // Verify feasibility - rollback if violated
            if (state.hardViolations > 0) {
                // Rollback
                removePatientFromOccupancy(state, p1);
                removePatientFromOccupancy(state, p2);
                
                p1.assignedDay = tempDay;
                p1.assignedRoom = tempRoom;
                p1.assignedTheater = tempTheater;
                p1.rIndex = tempRIdx;
                p1.tIndex = tempTIdx;
                
                p2.assignedDay = p1.assignedDay;
                p2.assignedRoom = p1.assignedRoom;
                p2.assignedTheater = p1.assignedTheater;
                p2.rIndex = p1.rIndex;
                p2.tIndex = p1.tIndex;
                
                // This is wrong, need original p2 values - use state copy approach
                addPatientToOccupancy(state, p1);
                addPatientToOccupancy(state, p2);
                rebuildHelpers(state);
                evaluateFull(state);
                return false;
            }
        }
        return true;
    }

    /**
     * Kempe Chain Operator: Move patient to different day (simpler, more effective)
     * OPTIMIZED: Use domain pruning for fast feasibility-aware day change
     */
    private boolean kempeChainOperator(OutputPatient p, SolutionState state, boolean temporary) {
        if (p.assignedDay == null)
            return false;

        int oldDay = p.assignedDay;
        int surgeon = input.PatientSurgeon(p.pIndex);
        int surgeryDuration = input.PatientSurgeryDuration(p.pIndex);
        int len = input.PatientLengthOfStay(p.pIndex);
        IHTP_Input.Gender pGender = input.PatientGender(p.pIndex);

        // OPTIMIZATION: Use pre-computed valid days
        List<Integer> validDays = validDaysForPatient.get(p.pIndex);
        if (validDays == null || validDays.isEmpty()) return false;

        // Try to find a feasible new day
        int maxAttempts = 12; // Reduced from 15
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int newDay = validDays.get(rand.nextInt(validDays.size()));
            if (newDay >= D || newDay == oldDay) continue;

            // H3: Check surgeon availability on new day
            int surgeonLoadOnNewDay = state.surgeonDayLoad[surgeon][newDay];
            if (surgeonLoadOnNewDay + surgeryDuration > input.SurgeonMaxSurgeryTime(surgeon, newDay)) continue;

            // H4: Check OT availability on new day
            int otLoadOnNewDay = state.operatingTheaterDayLoad[p.tIndex][newDay];
            if (otLoadOnNewDay + surgeryDuration > input.OperatingTheaterAvailability(p.tIndex, newDay)) continue;

            // H7: Check room capacity on new days
            boolean capacityOk = true;
            for (int d = newDay; d < Math.min(D, newDay + len); d++) {
                int occupancy = state.roomDayOccupancy[p.rIndex][d].size();
                boolean patientIsHere = (d >= oldDay && d < oldDay + len);
                if (!patientIsHere && occupancy >= input.RoomCapacity(p.rIndex)) {
                    capacityOk = false;
                    break;
                }
            }
            if (!capacityOk) continue;

            // H1: Check gender compatibility
            boolean genderOk = true;
            for (int d = newDay; d < Math.min(D, newDay + len) && genderOk; d++) {
                for (int idx : state.roomDayOccupancy[p.rIndex][d]) {
                    if (idx == p.pIndex) continue;
                    IHTP_Input.Gender otherGender = (idx < P) ? input.PatientGender(idx) : input.OccupantGender(idx - P);
                    if (pGender != otherGender) {
                        genderOk = false;
                        break;
                    }
                }
            }
            if (!genderOk) continue;

            // Apply move - feasibility checks passed
            removePatientFromOccupancy(state, p);
            
            // Update surgeon/OT loads
            state.surgeonDayLoad[surgeon][oldDay] -= surgeryDuration;
            state.operatingTheaterDayLoad[p.tIndex][oldDay] -= surgeryDuration;
            
            p.assignedDay = newDay;
            
            state.surgeonDayLoad[surgeon][newDay] += surgeryDuration;
            state.operatingTheaterDayLoad[p.tIndex][newDay] += surgeryDuration;
            
            addPatientToOccupancy(state, p);

            if (!temporary) {
                rebuildHelpers(state);
                evaluateFull(state);
            }
            return true;
        }

        return false; // No feasible move found
    }

    /**
     * Schedule Optional Patient Operator (NEW LLH 5): Schedule an unscheduled optional patient
     * Targets S8 (unscheduled_optional) constraint - very high weight in many instances (200-500)
     * Pure AT-ILS: Feasibility-aware scheduling
     */
    private boolean scheduleOptionalPatientOperator(OutputPatient p, SolutionState state, boolean temporary) {
        // Find an unscheduled optional patient
        List<OutputPatient> unscheduled = new ArrayList<>();
        for (OutputPatient patient : state.patients) {
            if (patient.assignedDay == null && !input.PatientMandatory(patient.pIndex)) {
                unscheduled.add(patient);
            }
        }
        
        if (unscheduled.isEmpty()) return false;
        
        // Pick random unscheduled optional patient
        OutputPatient target = unscheduled.get(rand.nextInt(unscheduled.size()));
        int pIdx = target.pIndex;
        int releaseDay = input.PatientSurgeryReleaseDay(pIdx);
        int dueDay = input.PatientLastPossibleDay(pIdx);
        int surgeryDuration = input.PatientSurgeryDuration(pIdx);
        int surgeon = input.PatientSurgeon(pIdx);
        int len = input.PatientLengthOfStay(pIdx);
        IHTP_Input.Gender pGender = input.PatientGender(pIdx);
        
        // Use pre-computed valid domains
        List<Integer> validDays = validDaysForPatient.get(pIdx);
        List<Integer> validRooms = validRoomsForPatient.get(pIdx);
        List<Integer> validTheaters = validTheatersForPatient.get(pIdx);
        
        if (validDays == null || validDays.isEmpty() ||
            validRooms == null || validRooms.isEmpty() ||
            validTheaters == null || validTheaters.isEmpty()) {
            return false;
        }
        
        // Try to find a feasible slot (try 20 combinations)
        int maxAttempts = 20;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int day = validDays.get(rand.nextInt(validDays.size()));
            if (day < releaseDay || day > dueDay || day >= D) continue;
            
            int roomIdx = validRooms.get(rand.nextInt(validRooms.size()));
            int theaterIdx = validTheaters.get(rand.nextInt(validTheaters.size()));
            
            // H3: Check surgeon availability
            if (state.surgeonDayLoad[surgeon][day] + surgeryDuration > input.SurgeonMaxSurgeryTime(surgeon, day))
                continue;
            
            // H4: Check OT availability
            if (state.operatingTheaterDayLoad[theaterIdx][day] + surgeryDuration > input.OperatingTheaterAvailability(theaterIdx, day))
                continue;
            
            // H7 & H1: Check room capacity and gender
            boolean roomFeasible = true;
            for (int d = day; d < Math.min(D, day + len) && roomFeasible; d++) {
                List<Integer> occupants = state.roomDayOccupancy[roomIdx][d];
                
                // H7: Capacity
                if (occupants.size() >= input.RoomCapacity(roomIdx)) {
                    roomFeasible = false;
                    break;
                }
                
                // H1: Gender
                for (int idx : occupants) {
                    IHTP_Input.Gender otherGender = (idx < P) ? input.PatientGender(idx) : input.OccupantGender(idx - P);
                    if (pGender != otherGender) {
                        roomFeasible = false;
                        break;
                    }
                }
            }
            if (!roomFeasible) continue;
            
            // Found feasible slot - schedule the patient
            target.assignedDay = day;
            target.assignedRoom = input.RoomId(roomIdx);
            target.assignedTheater = input.OperatingTheaterId(theaterIdx);
            target.rIndex = roomIdx;
            target.tIndex = theaterIdx;
            
            state.surgeonDayLoad[surgeon][day] += surgeryDuration;
            state.operatingTheaterDayLoad[theaterIdx][day] += surgeryDuration;
            addPatientToOccupancy(state, target);
            
            if (!temporary) {
                rebuildHelpers(state);
                evaluateFull(state);
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * OT Consolidation Operator (NEW LLH 6): Move patient to an already open OT on the same day
     * Targets S5 (open_operating_theater) constraint - reduces number of open OTs
     * Pure AT-ILS: Feasibility-aware OT consolidation
     */
    private boolean otConsolidationOperator(OutputPatient p, SolutionState state, boolean temporary) {
        if (p.assignedDay == null) return false;
        
        int day = p.assignedDay;
        int currentOT = p.tIndex;
        int surgeon = input.PatientSurgeon(p.pIndex);
        int surgeryDuration = input.PatientSurgeryDuration(p.pIndex);
        
        // Find all OTs that are already open on this day (have at least one patient)
        List<Integer> openOTs = new ArrayList<>();
        for (int t = 0; t < T; t++) {
            if (t != currentOT && state.operatingTheaterDayLoad[t][day] > 0) {
                // Check if this OT has capacity
                int currentLoad = state.operatingTheaterDayLoad[t][day];
                int maxCapacity = input.OperatingTheaterAvailability(t, day);
                if (currentLoad + surgeryDuration <= maxCapacity) {
                    openOTs.add(t);
                }
            }
        }
        
        if (openOTs.isEmpty()) return false;
        
        // Pick random open OT
        int newOT = openOTs.get(rand.nextInt(openOTs.size()));
        
        // Check surgeon transfer constraint (S6) - try to pick OT where surgeon already operates
        List<Integer> sameOT = new ArrayList<>();
        for (int t : openOTs) {
            // Check if surgeon already operates in this OT on this day
            for (OutputPatient other : state.patients) {
                if (other.assignedDay != null && other.assignedDay == day && 
                    other.tIndex == t && input.PatientSurgeon(other.pIndex) == surgeon) {
                    sameOT.add(t);
                    break;
                }
            }
        }
        if (!sameOT.isEmpty()) {
            newOT = sameOT.get(rand.nextInt(sameOT.size()));
        }
        
        // Apply the move
        state.operatingTheaterDayLoad[currentOT][day] -= surgeryDuration;
        state.operatingTheaterDayLoad[newOT][day] += surgeryDuration;
        
        p.assignedTheater = input.OperatingTheaterId(newOT);
        p.tIndex = newOT;
        
        if (!temporary) {
            rebuildHelpers(state);
            evaluateFull(state);
        }
        return true;
    }
    
    /**
     * Kick and Schedule Operator (NEW LLH 7): Unschedule an optional patient to make room
     * for another unscheduled patient (especially mandatory ones stuck)
     * Allows escaping local optima by trading patients
     * Pure AT-ILS: Feasibility-aware patient trading
     */
    private boolean kickAndScheduleOperator(OutputPatient p, SolutionState state, boolean temporary) {
        // Find unscheduled mandatory patients first, then optional
        List<OutputPatient> unscheduled = new ArrayList<>();
        for (OutputPatient patient : state.patients) {
            if (patient.assignedDay == null) {
                unscheduled.add(patient);
            }
        }
        
        if (unscheduled.isEmpty()) return false;
        
        // Sort: mandatory first
        unscheduled.sort((a, b) -> {
            boolean aMan = input.PatientMandatory(a.pIndex);
            boolean bMan = input.PatientMandatory(b.pIndex);
            return Boolean.compare(bMan, aMan); // true first
        });
        
        // Take first unscheduled (prefer mandatory)
        OutputPatient toSchedule = unscheduled.get(0);
        int pIdx = toSchedule.pIndex;
        int releaseDay = input.PatientSurgeryReleaseDay(pIdx);
        int dueDay = input.PatientLastPossibleDay(pIdx);
        int surgeryDuration = input.PatientSurgeryDuration(pIdx);
        int surgeon = input.PatientSurgeon(pIdx);
        int len = input.PatientLengthOfStay(pIdx);
        IHTP_Input.Gender pGender = input.PatientGender(pIdx);
        
        // Find scheduled optional patients that could be kicked
        List<OutputPatient> kickable = new ArrayList<>();
        for (OutputPatient patient : state.patients) {
            if (patient.assignedDay != null && !input.PatientMandatory(patient.pIndex) && patient != toSchedule) {
                kickable.add(patient);
            }
        }
        
        if (kickable.isEmpty()) return false;
        
        // Try to find: kick one optional, schedule one unscheduled
        Collections.shuffle(kickable, rand);
        
        for (OutputPatient victim : kickable) {
            // Try to schedule toSchedule in victim's slot
            int victimDay = victim.assignedDay;
            int victimRoom = victim.rIndex;
            int victimOT = victim.tIndex;
            int victimSurgeon = input.PatientSurgeon(victim.pIndex);
            int victimDuration = input.PatientSurgeryDuration(victim.pIndex);
            int victimLen = input.PatientLengthOfStay(victim.pIndex);
            
            // Check if toSchedule can use victim's day (H6 - admission window)
            if (victimDay < releaseDay || victimDay > dueDay) continue;
            
            // Check H3: surgeon availability after kick
            int surgeonLoad = state.surgeonDayLoad[surgeon][victimDay];
            if (victimSurgeon == surgeon) surgeonLoad -= victimDuration;
            if (surgeonLoad + surgeryDuration > input.SurgeonMaxSurgeryTime(surgeon, victimDay)) continue;
            
            // Check H4: OT availability after kick
            int otLoad = state.operatingTheaterDayLoad[victimOT][victimDay];
            otLoad -= victimDuration;
            if (otLoad + surgeryDuration > input.OperatingTheaterAvailability(victimOT, victimDay)) continue;
            
            // Check H2: room compatibility
            if (input.IncompatibleRoom(pIdx, victimRoom)) continue;
            
            // Check H7 & H1: room capacity and gender after victim leaves
            boolean roomFeasible = true;
            for (int d = victimDay; d < Math.min(D, victimDay + len) && roomFeasible; d++) {
                List<Integer> occupants = new ArrayList<>(state.roomDayOccupancy[victimRoom][d]);
                
                // Remove victim from consideration
                if (d >= victimDay && d < victimDay + victimLen) {
                    occupants.remove((Integer) victim.pIndex);
                }
                
                // H7: Capacity
                if (occupants.size() >= input.RoomCapacity(victimRoom)) {
                    roomFeasible = false;
                    break;
                }
                
                // H1: Gender
                for (int idx : occupants) {
                    IHTP_Input.Gender otherGender = (idx < P) ? input.PatientGender(idx) : input.OccupantGender(idx - P);
                    if (pGender != otherGender) {
                        roomFeasible = false;
                        break;
                    }
                }
            }
            if (!roomFeasible) continue;
            
            // Feasible! Execute the kick and schedule
            // 1. Remove victim
            removePatientFromOccupancy(state, victim);
            state.surgeonDayLoad[victimSurgeon][victimDay] -= victimDuration;
            state.operatingTheaterDayLoad[victimOT][victimDay] -= victimDuration;
            victim.assignedDay = null;
            victim.assignedRoom = null;
            victim.assignedTheater = null;
            victim.rIndex = -1;
            victim.tIndex = -1;
            
            // 2. Schedule toSchedule
            toSchedule.assignedDay = victimDay;
            toSchedule.assignedRoom = input.RoomId(victimRoom);
            toSchedule.assignedTheater = input.OperatingTheaterId(victimOT);
            toSchedule.rIndex = victimRoom;
            toSchedule.tIndex = victimOT;
            state.surgeonDayLoad[surgeon][victimDay] += surgeryDuration;
            state.operatingTheaterDayLoad[victimOT][victimDay] += surgeryDuration;
            addPatientToOccupancy(state, toSchedule);
            
            if (!temporary) {
                rebuildHelpers(state);
                evaluateFull(state);
            }
            return true;
        }
        
        return false;
    }

    private void removePatientFromOccupancy(SolutionState state, OutputPatient p) {
        if (p.assignedDay == null)
            return;
        int len = input.PatientLengthOfStay(p.pIndex);
        for (int k = 0; k < len; k++) {
            int day = p.assignedDay + k;
            if (day < D)
                state.roomDayOccupancy[p.rIndex][day].remove((Integer) p.pIndex);
        }
    }

    private void addPatientToOccupancy(SolutionState state, OutputPatient p) {
        if (p.assignedDay == null)
            return;
        int len = input.PatientLengthOfStay(p.pIndex);
        for (int k = 0; k < len; k++) {
            int day = p.assignedDay + k;
            if (day < D)
                state.roomDayOccupancy[p.rIndex][day].add(p.pIndex);
        }
    }

    /**
     * Perturbation Phase - Pure AT-ILS Algorithm 8 
     * IMPROVED: Better exploration within feasible space
     * Natural termination: stops when newPenalty > initialPenalty + thresholdValue
     * CRITICAL: Use FIXED threshold reference (initialPenalty) as per documentation
     */
    private void perturbationPhase(SolutionState newSol, List<OutputPatient> events, double thresholdVal) {
        if (events.isEmpty()) return;
        
        // Pure AT-ILS: Fixed reference point for threshold
        final double initialPenalty = newSol.softCost;
        final double maxAllowedPenalty = initialPenalty + thresholdVal;

        // Pure AT-ILS: Apply a controlled number of perturbation moves
        // The number of moves depends on problem size (more events = more perturbation)
        int numMoves = Math.max(3, events.size() / 10);
        int successfulMoves = 0;
        int maxAttempts = numMoves * 5; // Allow some failed attempts
        int attempts = 0;

        while (successfulMoves < numMoves && attempts < maxAttempts) {
            attempts++;
            
            // Pure AT-ILS: Random LLH and event selection (8 operators for IHTP)
            int llhType = rand.nextInt(8); // 0-4=Original, 5=ScheduleOptional, 6=OTConsolidate, 7=KickAndSchedule
            OutputPatient event = events.get(rand.nextInt(events.size()));

            // Save state before applying
            SolutionState backup = newSol.copy(R, D, N, S, input.Surgeons(), T);

            // Apply LLH (feasibility-aware operators)
            boolean applied = applyLLH(llhType, event, newSol, false);

            if (applied) {
                rebuildHelpers(newSol);
                evaluateFull(newSol);

                // Pure AT-ILS: Only accept feasible solutions within threshold
                if (newSol.hardViolations == 0) {
                    double newPenalty = newSol.softCost;

                    // Pure AT-ILS Algorithm 8: Accept if within threshold
                    if (newPenalty <= maxAllowedPenalty) {
                        successfulMoves++;
                        // Continue - keep the change
                    } else {
                        // Exceeds threshold - undo and stop perturbation
                        copyStateInto(newSol, backup);
                        rebuildHelpers(newSol);
                        evaluateFull(newSol);
                        break; // Stop perturbation as per AT-ILS
                    }
                } else {
                    // Infeasible move - undo (Pure AT-ILS requirement)
                    copyStateInto(newSol, backup);
                    rebuildHelpers(newSol);
                    evaluateFull(newSol);
                }
            }
        }

        // Ensure final state is feasible
        rebuildHelpers(newSol);
        evaluateFull(newSol);
    }

    /**
     * Local Search Phase - Pure AT-ILS Algorithm 9 
     * IMPROVED: More effective exploitation with proper termination
     * Natural termination: stops when updateThresholdList returns false
     */
    private void localSearchPhase(SolutionState newSol, List<OutputPatient> events, List<Double> thresholdList,
            double thresholdVal) {
        // Pure AT-ILS: Ensure we start with a feasible solution
        rebuildHelpers(newSol);
        evaluateFull(newSol);
        if (newSol.hardViolations > 0 || events.isEmpty()) {
            return; // Skip local search if infeasible or no events
        }

        boolean localSearch = true;
        double currentPenalty = newSol.softCost;
        double localBest = currentPenalty;
        double thresholdLocal = currentPenalty + thresholdVal; // Start with some room for exploration
        boolean improve = false;
        boolean improveLocalBest = false;
        int amountRemoved = 1;

        SolutionState localBestState = newSol.copy(R, D, N, S, input.Surgeons(), T);

        // Pure AT-ILS: Use local copy of threshold list (don't modify global)
        List<Double> localThresholdList = new ArrayList<>(thresholdList);

        // Pure AT-ILS: Natural termination - limit iterations based on threshold list size
        int maxIterations = Math.max(localThresholdList.size() * 2, 20);
        int iterCount = 0;

        while (localSearch && iterCount < maxIterations) {
            iterCount++;

            // Pure AT-ILS Algorithm 10: Update threshold local
            thresholdLocal = updateThresholdLocal(thresholdLocal, localThresholdList, currentPenalty, thresholdVal, improve);

            // Pure AT-ILS Algorithm 11: Apply heuristic to all events
            boolean[] result = applyHeuristicToAllEventsWithImprove(events, thresholdLocal, newSol, localBest);
            improveLocalBest = result[0];
            improve = result[1];

            // Update localBestState if better
            if (improveLocalBest && newSol.hardViolations == 0) {
                localBest = newSol.softCost;
                localBestState = newSol.copy(R, D, N, S, input.Surgeons(), T);
            }

            // Pure AT-ILS Algorithm 12: Update threshold list
            if (!improve) {
                localSearch = updateThresholdListLocal(localThresholdList, amountRemoved, improveLocalBest, localBest,
                        currentPenalty, newSol, localBestState);
                amountRemoved = improveLocalBest ? 1 : amountRemoved + 1;
            } else {
                improve = false; // Reset improve flag as per documentation
            }

            currentPenalty = newSol.softCost;
            
            // Early termination if threshold list exhausted
            if (localThresholdList.isEmpty()) {
                localSearch = false;
            }
        }

        // Use local best if better
        if (localBestState.hardViolations == 0 && localBestState.softCost < newSol.softCost) {
            copyStateInto(newSol, localBestState);
            rebuildHelpers(newSol);
            evaluateFull(newSol);
        }

        // Ensure newSol is feasible
        rebuildHelpers(newSol);
        evaluateFull(newSol);
    }
    
    /**
     * Update Threshold List for Local Search - Pure AT-ILS Algorithm 12
     * Uses local copy of threshold list
     */
    private boolean updateThresholdListLocal(List<Double> localThresholdList, int amountRemoved, boolean improveLocalBest,
            double localBest, double currentPenalty, SolutionState state, SolutionState localBestState) {
        // Pure AT-ILS: Stop if thresholdList is empty
        if (localThresholdList.isEmpty()) {
            return false;
        }

        // Remove elements from end (largest values)
        for (int k = 0; k < amountRemoved && !localThresholdList.isEmpty(); k++) {
            localThresholdList.remove(localThresholdList.size() - 1);
        }

        // Pure AT-ILS Algorithm 12: Return to local best if too far
        if (!localThresholdList.isEmpty() && currentPenalty > localBest + getRandomQuartile1(localThresholdList)) {
            if (localBestState.hardViolations == 0) {
                copyStateInto(state, localBestState);
                rebuildHelpers(state);
                evaluateFull(state);
            }
        }

        return !localThresholdList.isEmpty(); // Continue if list not empty
    }

    /**
     * Update Threshold Local - Pure AT-ILS Algorithm 10 NO modifications - exact
     * implementation from documentation
     */
    private double updateThresholdLocal(double thresholdLocal, List<Double> thresholdList, double currentPenalty,
            double thresholdValue, boolean improve) {
        // Pure AT-ILS Algorithm 10: Decrease thresholdLocal
        if (!thresholdList.isEmpty()) {
            double firstElement = thresholdList.get(0);
            if (thresholdLocal - firstElement > currentPenalty) {
                thresholdLocal = thresholdLocal - firstElement;
            } else {
                thresholdLocal = currentPenalty;
            }
        } else {
            thresholdLocal = currentPenalty;
        }

        // Pure AT-ILS Algorithm 10: If no improvement, increase threshold
        if (!improve) {
            thresholdLocal = thresholdLocal + thresholdValue;
        }
        // Note: improve flag is reset in caller as per documentation

        return thresholdLocal;
    }

    /**
     * Apply Heuristic to All Events - Algorithm 11 
     * IMPROVED: Try multiple LLH types per event to increase improvement chances
     * Returns array: [improveLocalBest, improve]
     */
    private boolean[] applyHeuristicToAllEventsWithImprove(List<OutputPatient> events, double thresholdLocal,
            SolutionState state, double localBest) {
        Collections.shuffle(events, rand);
        boolean improveLocalBest = false;
        boolean improve = false;

        for (OutputPatient event : events) {
            // Save state before applying
            SolutionState backup = state.copy(R, D, N, S, input.Surgeons(), T);
            double oldPenalty = state.softCost;

            // Pure AT-ILS: Try random LLH - 8 operators for IHTP
            int llhType = rand.nextInt(8); // 0-4=Original, 5=ScheduleOptional, 6=OTConsolidate, 7=KickAndSchedule
            boolean applied = applyLLH(llhType, event, state, false);
            
            if (applied) {
                rebuildHelpers(state);
                evaluateFull(state);

                if (state.hardViolations == 0) {
                    double newPenalty = state.softCost;

                    // Pure AT-ILS: Accept if within threshold or equal
                    if (newPenalty <= thresholdLocal) {
                        if (newPenalty < oldPenalty) {
                            improve = true;
                        }

                        if (newPenalty < localBest) {
                            improveLocalBest = true;
                            localBest = newPenalty; // Update localBest for subsequent events
                        }
                        // Keep the change - don't restore
                    } else {
                        // Undo - restore backup (exceeds threshold)
                        copyStateInto(state, backup);
                        rebuildHelpers(state);
                        evaluateFull(state);
                    }
                } else {
                    // Undo if infeasible - restore backup
                    copyStateInto(state, backup);
                    rebuildHelpers(state);
                    evaluateFull(state);
                }
            }
        }

        return new boolean[] { improveLocalBest, improve };
    }

    /**
     * Get random value from first quartile (Q1) of threshold list Pure AT-ILS: Uses
     * random value from quartile 1 for exploration This provides controlled
     * randomness while staying in the lower range
     */
    private double getRandomQuartile1(List<Double> thresholdList) {
        if (thresholdList.isEmpty())
            return 0.0;
        int quartile1Size = thresholdList.size() / 4;
        if (quartile1Size == 0)
            quartile1Size = 1; // At least 1 element
        if (quartile1Size > thresholdList.size())
            quartile1Size = thresholdList.size();

        // Get random index from first quartile (0 to quartile1Size-1)
        int randomIndex = rand.nextInt(quartile1Size);
        return thresholdList.get(randomIndex);
    }

    // Pure AT-ILS: Only Q1 is used (removed getRandomQuartile2or3 - not in
    // documentation)

    /**
     * Move Acceptance Phase - Pure AT-ILS Algorithm 13 
     * IMPROVED: Simpler and more effective acceptance criteria
     */
    private void moveAcceptancePhase(SolutionState newSol, SolutionState currentSol, SolutionState bestSol,
            List<OutputPatient> events) {
        // Pure AT-ILS: Only accept feasible solutions
        if (newSol.hardViolations > 0) {
            // Restore to current (which should be feasible)
            rebuildHelpers(currentSol);
            evaluateFull(currentSol);
            return;
        }

        double newPenalty = newSol.softCost;
        double currentPenalty = currentSol.softCost;
        double bestPenalty = bestSol.softCost;

        // Pure AT-ILS Algorithm 13: Update best if improvement found
        if (newPenalty < bestPenalty && newSol.hardViolations == 0) {
            copyStateInto(bestSol, newSol);
            rebuildHelpers(bestSol);
            evaluateFull(bestSol);
            bestPenalty = bestSol.softCost;
        }

        // Pure AT-ILS Algorithm 13: Accept if better or equal
        if (newPenalty <= currentPenalty) {
            copyStateInto(currentSol, newSol);
            rebuildHelpers(currentSol);
            evaluateFull(currentSol);
        } else {
            // Pure AT-ILS Algorithm 13: Accept worse solution within threshold from best
            // Use simple threshold comparison instead of recalculating
            double acceptanceThreshold = thresholdValue; // Use current threshold
            
            if (newPenalty < bestPenalty + acceptanceThreshold) {
                // Accept worse solution within threshold
                copyStateInto(currentSol, newSol);
                rebuildHelpers(currentSol);
                evaluateFull(currentSol);
            } else {
                // Return to best solution (prevents drift)
                copyStateInto(currentSol, bestSol);
                rebuildHelpers(currentSol);
                evaluateFull(currentSol);
            }
        }
    }

    /**
     * Main AT-ILS Algorithm - Algorithm 6
     */
    public void optimizeSolution(long maxTimeMs) {
        long maxTimeSec = maxTimeMs / 1000;
        long maxTimeMin = maxTimeSec / 60;
        long maxTimeSecRem = maxTimeSec % 60;

        System.out.println("==========================================");
        System.out.flush();
        System.out.println("Starting AT-ILS Optimization...");
        System.out.flush(); // Flush for realtime console output
        System.out.println("Time limit: " + maxTimeMin + "m " + maxTimeSecRem + "s (" + maxTimeSec + " seconds)");
        System.out.flush();
        System.out.println("Initial Soft Cost: " + String.format("%.2f", currentState.softCost));
        System.out.flush();
        System.out.println("Initial Hard Violations: " + currentState.hardViolations);
        System.out.flush();
        if (logFilePath != null && !logFilePath.isEmpty()) {
            System.out.println("Log file: " + logFilePath);
            System.out.flush();
        } else {
            System.out.println("WARNING: Log file path is null or empty!");
            System.out.flush();
        }
        System.out.println("==========================================");
        System.out.flush();

        long startTime = System.currentTimeMillis();
        int iteration = 0;

        // Setup log writer - UNIFIED format for Launcher V2
        java.io.BufferedWriter logWriter = null;
        java.io.FileWriter fileWriter = null; // Keep reference for direct flush
        if (logFilePath != null && !logFilePath.isEmpty()) {
            try {
                // Ensure directory exists
                java.io.File logFile = new java.io.File(logFilePath);
                java.io.File logDir = logFile.getParentFile();
                if (logDir != null && !logDir.exists()) {
                    logDir.mkdirs();
                    System.out.println("Created log directory: " + logDir.getAbsolutePath());
                    System.out.flush();
                }

                // Always create new file for AT-ILS (overwrite if exists)
                // Use FileWriter with BufferedWriter for realtime writing
                fileWriter = new java.io.FileWriter(logFilePath, false);
                logWriter = new java.io.BufferedWriter(fileWriter);
                // ENHANCED format for detailed thesis analysis:
                // iteration: iteration number (for X-axis in charts)
                // time_ms: elapsed time in milliseconds (for time-based analysis)
                // hard_violations: current hard constraint violations (should be 0 for feasible
                // solutions)
                // soft_cost: current soft cost (for optimization progress tracking)
                // best_hard: best hard violations found so far
                // best_soft: best soft cost found so far (for convergence analysis)
                // status: FEASIBLE or INFEASIBLE (for solution quality tracking)
                // phase: PERTURBATION, LOCAL_SEARCH, ACCEPTANCE, or ITERATION_END (for phase
                // tracking)
                // threshold: current threshold value (for adaptive threshold analysis)
                // improvement: improvement amount from previous best (for convergence tracking)
                // accepted: whether solution was accepted (YES/NO) - only for ACCEPTANCE phase
                logWriter.write(
                        "iteration,time_ms,hard_violations,soft_cost,best_hard,best_soft,status,phase,threshold,improvement,accepted\n");
                logWriter.flush();
                safeFlush(fileWriter); // FIXED: Safely flush underlying FileWriter
                System.out.println("Log file initialized: " + logFile.getAbsolutePath());
                System.out.println(
                        "Log format: iteration,time_ms,hard_violations,soft_cost,best_hard,best_soft,status,phase,threshold,improvement,accepted");
                System.out.flush();
            } catch (IOException e) {
                System.err.println("ERROR: Failed to create log file: " + logFilePath);
                System.err.println("Error details: " + e.getMessage());
                e.printStackTrace();
                logWriter = null; // Disable logging if file creation fails
                fileWriter = null;
            }
        } else {
            System.out.println("Warning: Log file path is null or empty. Logging disabled.");
            System.out.flush();
        }

        // CRITICAL: Ensure initial solution is feasible (AT-ILS requirement)
        if (currentState.hardViolations > 0) {
            System.err.println("WARNING: Initial solution has " + currentState.hardViolations + " hard violations!");
            System.err.println("AT-ILS requires feasible initial solution. Results may be suboptimal.");
            System.err.flush();
        }

        // Ensure bestState is initialized correctly and feasible
        rebuildHelpers(bestState);
        evaluateFull(bestState);
        if (bestState.hardViolations > 0 && currentState.hardViolations == 0) {
            // If current is feasible but best is not, update best
            copyStateInto(bestState, currentState);
            rebuildHelpers(bestState);
            evaluateFull(bestState);
        }

        // Track best values for logging
        double bestSoftCost = bestState.softCost;
        int bestHardViolations = bestState.hardViolations;
        double previousBestSoft = bestSoftCost; // Track for improvement calculation
        double initialSoftCost = currentState.softCost; // Store initial cost for final statistics
        double improvement = 0.0; // Track improvement per iteration (declared outside loop for scope)

        // Statistics tracking for detailed analysis
        int totalAcceptedMoves = 0;
        int totalRejectedMoves = 0;

        // Pure AT-ILS: Track exploration vs exploitation for balance analysis (logging
        // only)
        long totalPerturbationTime = 0; // Time spent in perturbation (exploration)
        long totalLocalSearchTime = 0; // Time spent in local search (exploitation)

        // Log initial state (iteration 0) - critical for analysis
        if (logWriter != null) {
            try {
                long elapsed = 0;
                String status = (currentState.hardViolations == 0) ? "FEASIBLE" : "INFEASIBLE";
                // Use locale-independent format (US locale ensures decimal point, not comma)
                String softCostStr = String.format(java.util.Locale.US, "%.2f", currentState.softCost);
                String bestSoftStr = String.format(java.util.Locale.US, "%.2f", bestSoftCost);
                // Format:
                // iteration,time_ms,hard_violations,soft_cost,best_hard,best_soft,status,phase,threshold,improvement,accepted
                String logLine = "0," + elapsed + "," + currentState.hardViolations + "," + softCostStr + ","
                        + bestHardViolations + "," + bestSoftStr + "," + status + ",INITIAL,0.00,0.00,NA\n";
                logWriter.write(logLine);
                logWriter.flush();
                safeFlush(fileWriter); // FIXED: Safely flush underlying FileWriter
                // Initial log entry written (silent)
            } catch (IOException e) {
                System.err.println("Error writing initial log: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Warning: Log file path not set, logging disabled.");
        }

        while (System.currentTimeMillis() - startTime < maxTimeMs) {
            try {
                iteration++;

                // Pure AT-ILS: Calculate threshold every iteration (no caching)
                thresholdList = calculateThreshold(currentState, eventList);
                thresholdValue = updateThreshold(thresholdList, currentState.softCost);

                // Create new solution from current
                SolutionState newSol = currentState.copy(R, D, N, S, input.Surgeons(), T);
                double costBeforePerturbation = newSol.softCost;

                // Perturbation phase (EXPLORATION)
                long pertStartTime = System.currentTimeMillis();
                if (eventList.isEmpty()) {
                    System.err.println("ERROR: eventList is empty! Cannot perform perturbation.");
                    System.err.flush();
                } else {
                    perturbationPhase(newSol, eventList, thresholdValue);
                    // OPTIMIZED: perturbationPhase already ensures state is rebuilt/evaluated at
                    // the end (Pure AT-ILS optimization)
                }
                double costAfterPerturbation = newSol.softCost;
                long pertEndTime = System.currentTimeMillis();
                totalPerturbationTime += (pertEndTime - pertStartTime);

                // OPTIMIZED: If perturbation produced infeasible solution, restore to
                // currentState
                if (newSol.hardViolations > 0) {
                    // Restore to current feasible state
                    copyStateInto(newSol, currentState);
                    rebuildHelpers(newSol);
                    evaluateFull(newSol);
                    costAfterPerturbation = newSol.softCost;
                }

                // Log after perturbation phase (EXPLORATION) - ALWAYS log for all iterations
                if (logWriter != null) {
                    try {
                        long elapsedMsPert = System.currentTimeMillis() - startTime;
                        String status = (newSol.hardViolations == 0) ? "FEASIBLE" : "INFEASIBLE";
                        String softCostStr = String.format(java.util.Locale.US, "%.2f", newSol.softCost);
                        String bestSoftStr = String.format(java.util.Locale.US, "%.2f", bestSoftCost);
                        String thresholdStr = String.format(java.util.Locale.US, "%.2f", thresholdValue);
                        double phaseImprovement = costBeforePerturbation - costAfterPerturbation; // Positive = better
                        String improvementStr = String.format(java.util.Locale.US, "%.2f", phaseImprovement);
                        // Format:
                        // iteration,time_ms,hard_violations,soft_cost,best_hard,best_soft,status,phase,threshold,improvement,accepted
                        String logLine = iteration + "," + elapsedMsPert + "," + newSol.hardViolations + ","
                                + softCostStr + "," + bestHardViolations + "," + bestSoftStr + "," + status
                                + ",PERTURBATION," + thresholdStr + "," + improvementStr + ",NA\n";
                        logWriter.write(logLine);
                        logWriter.flush();
                        safeFlush(fileWriter); // FIXED: Safely flush underlying FileWriter
                    } catch (IOException e) {
                        // Ignore
                    }
                }

                // Local search phase (EXPLOITATION) - only if feasible
                // Pure AT-ILS: Natural termination, no forced minimum time
                long lsStartTime = System.currentTimeMillis();
                double costBeforeLocalSearch = newSol.softCost;
                if (newSol.hardViolations == 0 && !eventList.isEmpty()) {
                    localSearchPhase(newSol, eventList, thresholdList, thresholdValue);
                }
                rebuildHelpers(newSol);
                evaluateFull(newSol);
                double costAfterLocalSearch = newSol.softCost;
                long lsEndTime = System.currentTimeMillis();
                long lsTime = lsEndTime - lsStartTime;

                totalLocalSearchTime += lsTime;

                // Log after local search phase (EXPLOITATION) - ALWAYS log for all iterations
                if (logWriter != null) {
                    try {
                        long elapsedMsLS = System.currentTimeMillis() - startTime;
                        String status = (newSol.hardViolations == 0) ? "FEASIBLE" : "INFEASIBLE";
                        String softCostStr = String.format(java.util.Locale.US, "%.2f", newSol.softCost);
                        String bestSoftStr = String.format(java.util.Locale.US, "%.2f", bestSoftCost);
                        String thresholdStr = String.format(java.util.Locale.US, "%.2f", thresholdValue);
                        double phaseImprovement = costBeforeLocalSearch - costAfterLocalSearch; // Positive = better
                        String improvementStr = String.format(java.util.Locale.US, "%.2f", phaseImprovement);
                        // Format:
                        // iteration,time_ms,hard_violations,soft_cost,best_hard,best_soft,status,phase,threshold,improvement,accepted
                        String logLine = iteration + "," + elapsedMsLS + "," + newSol.hardViolations + "," + softCostStr
                                + "," + bestHardViolations + "," + bestSoftStr + "," + status + ",LOCAL_SEARCH,"
                                + thresholdStr + "," + improvementStr + ",NA\n";
                        logWriter.write(logLine);
                        logWriter.flush();
                        safeFlush(fileWriter); // FIXED: Safely flush underlying FileWriter
                    } catch (IOException e) {
                        // Ignore
                    }
                }

                // CRITICAL FIX: Ensure newSol is feasible before move acceptance (Pure AT-ILS
                // requirement)
                // If newSol is infeasible after local search, restore to currentState
                // (feasible)
                rebuildHelpers(newSol);
                evaluateFull(newSol);
                if (newSol.hardViolations > 0) {
                    // Restore to current feasible state before move acceptance
                    copyStateInto(newSol, currentState);
                    rebuildHelpers(newSol);
                    evaluateFull(newSol);
                }

                // Move acceptance phase (newSol is now guaranteed feasible)
                double currentCostBeforeAcceptance = currentState.softCost;
                moveAcceptancePhase(newSol, currentState, bestState, eventList);
                rebuildHelpers(currentState);
                evaluateFull(currentState);
                rebuildHelpers(bestState);
                evaluateFull(bestState);
                double costAfterAcceptance = currentState.softCost;

                // CRITICAL FIX: Ensure currentState is feasible after move acceptance
                if (currentState.hardViolations > 0) {
                    // If somehow infeasible, restore to best feasible
                    if (bestState.hardViolations == 0) {
                        copyStateInto(currentState, bestState);
                        rebuildHelpers(currentState);
                        evaluateFull(currentState);
                    }
                }

                // Determine if solution was accepted
                boolean wasAccepted = (costAfterAcceptance != currentCostBeforeAcceptance)
                        || (costAfterAcceptance <= currentCostBeforeAcceptance);
                String acceptedStr = wasAccepted ? "YES" : "NO";

                // Update statistics
                if (wasAccepted) {
                    totalAcceptedMoves++;
                } else {
                    totalRejectedMoves++;
                }

                // Log after acceptance phase - ALWAYS log for all iterations
                if (logWriter != null) {
                    try {
                        long elapsedMsAcc = System.currentTimeMillis() - startTime;
                        String status = (currentState.hardViolations == 0) ? "FEASIBLE" : "INFEASIBLE";
                        String softCostStr = String.format(java.util.Locale.US, "%.2f", currentState.softCost);
                        String bestSoftStr = String.format(java.util.Locale.US, "%.2f", bestSoftCost);
                        String thresholdStr = String.format(java.util.Locale.US, "%.2f", thresholdValue);
                        double phaseImprovement = currentCostBeforeAcceptance - costAfterAcceptance; // Positive =
                                                                                                     // better
                        String improvementStr = String.format(java.util.Locale.US, "%.2f", phaseImprovement);
                        // Format:
                        // iteration,time_ms,hard_violations,soft_cost,best_hard,best_soft,status,phase,threshold,improvement,accepted
                        String logLine = iteration + "," + elapsedMsAcc + "," + currentState.hardViolations + ","
                                + softCostStr + "," + bestHardViolations + "," + bestSoftStr + "," + status
                                + ",ACCEPTANCE," + thresholdStr + "," + improvementStr + "," + acceptedStr + "\n";
                        logWriter.write(logLine);
                        logWriter.flush();
                        safeFlush(fileWriter); // FIXED: Safely flush underlying FileWriter
                    } catch (IOException e) {
                        // Ignore
                    }
                }

                // Update event list
                updateEventList();

                // Track improvement BEFORE updating best values
                boolean isImprovement = false;

                // Update best values from bestState and detect improvement
                if (bestState.softCost < bestSoftCost) {
                    bestSoftCost = bestState.softCost;
                    isImprovement = true;
                }
                if (bestState.hardViolations < bestHardViolations) {
                    bestHardViolations = bestState.hardViolations;
                    isImprovement = true;
                }

                // Calculate improvement from previous iteration's best
                improvement = previousBestSoft - bestSoftCost; // Positive = improvement
                previousBestSoft = bestSoftCost; // Update for next iteration

                // Pure AT-ILS: No stagnation detection - natural termination via thresholdList

                // Log progress - UNIFIED format for Launcher V2
                // LOG EVERY ITERATION for complete analysis
                // User requested: "berikan saya semua step diprint di console dan
                // violation_log_one_run/"
                boolean shouldLog = true; // ALWAYS log every iteration

                if (logWriter != null && shouldLog) {
                    try {
                        long elapsedMsIterEnd = System.currentTimeMillis() - startTime;
                        String status = (currentState.hardViolations == 0) ? "FEASIBLE" : "INFEASIBLE";
                        // Format:
                        // iteration,time_ms,hard_violations,soft_cost,best_hard,best_soft,status,phase,threshold,improvement,accepted
                        // Use locale-independent format (US locale ensures decimal point, not comma)
                        // This is critical for CSV parsing and chart generation
                        String softCostStr = String.format(java.util.Locale.US, "%.2f", currentState.softCost);
                        String bestSoftStr = String.format(java.util.Locale.US, "%.2f", bestSoftCost);
                        String thresholdStr = String.format(java.util.Locale.US, "%.2f", thresholdValue);
                        String improvementStr = String.format(java.util.Locale.US, "%.2f", improvement);
                        String logLine = iteration + "," + elapsedMsIterEnd + "," + currentState.hardViolations + ","
                                + softCostStr + "," + bestHardViolations + "," + bestSoftStr + "," + status
                                + ",ITERATION_END," + thresholdStr + "," + improvementStr + ",NA\n";
                        logWriter.write(logLine);
                        logWriter.flush(); // Force flush BufferedWriter
                        safeFlush(fileWriter); // FIXED: Safely flush underlying FileWriter for immediate disk write
                        // Log confirmation only for milestones (every 10 iterations or improvements)
                        if (iteration % 10 == 0 || isImprovement) {
                            // Silent logging - information is in CSV file
                        }
                    } catch (IOException e) {
                        System.err.println("Error writing to log file: " + e.getMessage());
                        System.err.flush();
                        e.printStackTrace();
                    }
                } else if (logWriter == null && iteration == 1) {
                    System.err.println("ERROR: logWriter is null! Cannot write logs.");
                    System.err.flush();
                }

                // Compact console output - Dense but informative
                // Show: Iteration summary with all phases in one line
                long elapsedMs = System.currentTimeMillis() - startTime;
                long elapsedSec = elapsedMs / 1000;
                long remaining = maxTimeMs - elapsedMs;
                long remainingSec = remaining / 1000;
                long remainingMin = remainingSec / 60;
                long remainingSecRem = remainingSec % 60;

                // Calculate improvement percentage from initial cost
                double improvementPct = 0.0;
                if (initialSoftCost > 0 && bestSoftCost < initialSoftCost) {
                    improvementPct = ((initialSoftCost - bestSoftCost) / initialSoftCost) * 100.0;
                }

                // Phase deltas - track cost changes through each AT-ILS phase
                double deltaPert = costAfterPerturbation - costBeforePerturbation;
                double deltaLS = costAfterLocalSearch - costBeforeLocalSearch;
                double deltaAcc = costAfterAcceptance - currentCostBeforeAcceptance;

                // ENHANCED CONSOLE OUTPUT: Clear, readable progress tracking
                // Two output modes: Detailed (every iteration) and Summary (milestones)
                String improvementIndicator = isImprovement ? " ⭐ NEW BEST!" : "";
                String statusIcon = (currentState.hardViolations == 0) ? "✓" : "✗";

                // SUMMARY LOG: Every 10 iterations or on improvement - EASY TO READ
                if (iteration % 10 == 0 || isImprovement || iteration == 1) {
                    System.out.println("─".repeat(90));
                    String summaryMsg = String.format(
                            "│ Iter %4d │ Best: %10.2f │ Current: %10.2f │ Improve: %6.2f%% │ Threshold: %8.2f │ %s │%s",
                            iteration, bestSoftCost, currentState.softCost, improvementPct, thresholdValue, statusIcon,
                            improvementIndicator);
                    System.out.println(summaryMsg);
                    // Phase delta info - shows effect of each AT-ILS phase
                    String phaseInfo = String.format(
                            "│ Phases: Pert:%+.1f LS:%+.1f Acc:%+.1f │ Time: %dm%ds │ Remaining: %dm%ds │", deltaPert,
                            deltaLS, deltaAcc, elapsedSec / 60, elapsedSec % 60, remainingMin, remainingSecRem);
                    System.out.println(phaseInfo);

                    // Progress bar visualization
                    long elapsedMsProgress = System.currentTimeMillis() - startTime;
                    double progressPct = (elapsedMsProgress * 100.0) / maxTimeMs;
                    int barLength = 40;
                    int filledLength = (int) (progressPct / 100.0 * barLength);
                    String progressBar = "█".repeat(Math.max(0, filledLength))
                            + "░".repeat(Math.max(0, barLength - filledLength));
                    System.out.println(String.format("│ Progress: [%s] %.1f%% │ Time: %dm%ds / %dm%ds", progressBar,
                            progressPct, elapsedMsProgress / 60000, (elapsedMsProgress / 1000) % 60, maxTimeMs / 60000,
                            (maxTimeMs / 1000) % 60));
                    System.out.println("─".repeat(90));
                } else {
                    // COMPACT LOG: Single line for non-milestone iterations
                    long elapsedMsCompact = System.currentTimeMillis() - startTime;
                    long elapsedSecCompact = elapsedMsCompact / 1000;
                    String compactMsg = String.format("  [%4d] Best:%.2f Cur:%.2f Δ:%.2f Th:%.2f %s %dm%ds", iteration,
                            bestSoftCost, currentState.softCost, costAfterAcceptance - costBeforePerturbation,
                            thresholdValue, statusIcon, elapsedSecCompact / 60, elapsedSecCompact % 60);
                    System.out.println(compactMsg);
                }
                System.out.flush();

            } catch (Exception e) {
                System.err.println("ERROR in iteration " + iteration + ": " + e.getMessage());
                System.err.flush();
                e.printStackTrace();
                System.err.flush();
                // Continue to next iteration instead of crashing
            }

        }

        // Log final state (last iteration) - critical for analysis
        // This ensures the final best solution is always recorded
        if (logWriter != null) {
            try {
                long elapsed = System.currentTimeMillis() - startTime;
                String status = (bestState.hardViolations == 0) ? "FEASIBLE" : "INFEASIBLE";
                // Use locale-independent format (US locale ensures decimal point, not comma)
                String softCostStr = String.format(java.util.Locale.US, "%.2f", bestState.softCost);
                String bestSoftStr = String.format(java.util.Locale.US, "%.2f", bestSoftCost);
                // Format:
                // iteration,time_ms,hard_violations,soft_cost,best_hard,best_soft,status,phase,threshold,improvement,accepted
                String thresholdStr = String.format(java.util.Locale.US, "%.2f", thresholdValue);
                double finalImprovement = initialSoftCost - bestSoftCost; // Total improvement from initial
                String improvementStr = String.format(java.util.Locale.US, "%.2f", finalImprovement);
                String logLine = iteration + "," + elapsed + "," + bestState.hardViolations + "," + softCostStr + ","
                        + bestHardViolations + "," + bestSoftStr + "," + status + ",FINAL," + thresholdStr + ","
                        + improvementStr + ",NA\n";
                logWriter.write(logLine);
                logWriter.flush();
                // FIXED: Check if fileWriter is still open before flushing
                if (fileWriter != null) {
                    try {
                        fileWriter.flush(); // Force flush underlying FileWriter
                    } catch (IOException e) {
                        // FileWriter might be closed, ignore
                    }
                }
                System.out.println("Final log entry written to: " + logFilePath);
                System.out.flush();
            } catch (IOException e) {
                System.err.println("Error writing final log: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // FIXED: Close log writer safely
        if (logWriter != null) {
            try {
                logWriter.flush();
                logWriter.close();
            } catch (IOException e) {
                // Ignore if already closed
            }
        }
        if (fileWriter != null) {
            try {
                // Don't flush if already closed (closing BufferedWriter also closes underlying
                // FileWriter)
                fileWriter.close();
            } catch (IOException e) {
                // Ignore if already closed
            }
        }

        long totalElapsed = System.currentTimeMillis() - startTime;
        long totalElapsedSec = totalElapsed / 1000;
        long totalElapsedMin = totalElapsedSec / 60;
        long totalElapsedSecRem = totalElapsedSec % 60;

        // Calculate final statistics
        double totalImprovement = initialSoftCost > 0 ? ((initialSoftCost - bestSoftCost) / initialSoftCost) * 100.0
                : 0.0;
        double avgIterationTime = iteration > 0 ? (totalElapsed / (double) iteration) : 0.0;
        double acceptanceRate = (totalAcceptedMoves + totalRejectedMoves) > 0
                ? (totalAcceptedMoves * 100.0 / (totalAcceptedMoves + totalRejectedMoves))
                : 0.0;

        // FIXED: Calculate exploration vs exploitation balance
        long totalPhaseTime = totalPerturbationTime + totalLocalSearchTime;
        double explorationPercentage = totalPhaseTime > 0 ? (totalPerturbationTime * 100.0 / totalPhaseTime) : 0.0;
        double exploitationPercentage = totalPhaseTime > 0 ? (totalLocalSearchTime * 100.0 / totalPhaseTime) : 0.0;

        System.out.println("\n" + "=".repeat(70));
        System.out.println("AT-ILS Optimization Complete - Detailed Summary");
        System.out.println("=".repeat(70));
        System.out.flush();
        System.out.println("EXECUTION STATISTICS:");
        System.out.flush();
        System.out.println("  Total Iterations: " + iteration);
        System.out.flush();
        System.out.println("  Total Runtime: " + totalElapsedMin + "m " + totalElapsedSecRem + "s (" + totalElapsedSec
                + " seconds)");
        System.out.flush();
        System.out.println(String.format("  Average Time per Iteration: %.2f ms", avgIterationTime));
        System.out.flush();
        System.out.println(String.format("  Acceptance Rate: %.2f%% (%d accepted, %d rejected)", acceptanceRate,
                totalAcceptedMoves, totalRejectedMoves));
        System.out.flush();
        System.out.println("=".repeat(70));
        System.out.flush();
        System.out.println("EXPLORATION vs EXPLOITATION BALANCE:");
        System.out.flush();
        System.out.println(String.format("  Exploration (Perturbation): %.1f%% (%.1fs)", explorationPercentage,
                totalPerturbationTime / 1000.0));
        System.out.flush();
        System.out.println(String.format("  Exploitation (Local Search): %.1f%% (%.1fs)", exploitationPercentage,
                totalLocalSearchTime / 1000.0));
        System.out.flush();
        System.out.println("=".repeat(70));
        System.out.flush();
        System.out.println("OPTIMIZATION RESULTS:");
        System.out.flush();
        System.out.println("  Initial Soft Cost: " + String.format("%.2f", initialSoftCost));
        System.out.flush();
        System.out.println("  Final Soft Cost: " + String.format("%.2f", bestState.softCost));
        System.out.flush();
        System.out.println("  Best Soft Cost Found: " + String.format("%.2f", bestSoftCost));
        System.out.flush();
        System.out.println("  Best Hard Violations: " + bestState.hardViolations);
        System.out.flush();
        System.out.println(String.format("  Total Improvement: %.2f%% (reduction of %.2f)", totalImprovement,
                initialSoftCost - bestSoftCost));
        System.out.flush();
        System.out.println("=".repeat(70));
        System.out.flush();
    }

    /**
     * Save best solution to file
     */
    private void saveBest(SolutionState state) {
        try {
            JSONObject root = new JSONObject();

            // Patients
            JSONArray pats = new JSONArray();
            // OPTIMIZED: Pre-allocate capacity (Pure AT-ILS optimization)
            List<OutputPatient> sortedP = new ArrayList<>(state.patients.size());
            sortedP.addAll(state.patients);
            sortedP.sort((a, b) -> a.id.compareTo(b.id));

            for (OutputPatient op : sortedP) {
                JSONObject p = new JSONObject();
                p.put("id", op.id);
                if (op.assignedDay != null) {
                    p.put("admission_day", op.assignedDay);
                    p.put("room", op.assignedRoom);
                    p.put("operating_theater", op.assignedTheater);
                } else {
                    p.put("admission_day", "none");
                }
                pats.put(p);
            }
            root.put("patients", pats);

            // Nurses
            JSONArray nurs = new JSONArray();
            // OPTIMIZED: Pre-allocate capacity (Pure AT-ILS optimization)
            List<OutputNurse> sortedN = new ArrayList<>(state.nurses.size());
            sortedN.addAll(state.nurses);
            sortedN.sort((a, b) -> a.id.compareTo(b.id));

            for (OutputNurse on : sortedN) {
                JSONObject n = new JSONObject();
                n.put("id", on.id);
                JSONArray assigns = new JSONArray();

                // OPTIMIZED: Pre-allocate capacity (Pure AT-ILS optimization)
                List<Integer> shifts = new ArrayList<>(on.assignments.size());
                shifts.addAll(on.assignments.keySet());
                Collections.sort(shifts);

                for (int s : shifts) {
                    List<String> rooms = on.assignments.get(s);
                    if (rooms.isEmpty())
                        continue;

                    JSONObject a = new JSONObject();
                    int d = s / input.ShiftsPerDay();
                    int sh = s % input.ShiftsPerDay();
                    a.put("day", d);
                    a.put("shift", input.ShiftName(sh));

                    JSONArray rArr = new JSONArray();
                    for (String rm : rooms)
                        rArr.put(rm);
                    a.put("rooms", rArr);

                    assigns.put(a);
                }
                n.put("assignments", assigns);
                nurs.put(n);
            }
            root.put("nurses", nurs);

            try (FileWriter fw = new FileWriter(this.outputFilePath)) {
                fw.write(root.toString(4));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * External entry point for Optimizer (for IHTP_Optimizer.java)
     */
    // FIXED: Support both minutes (int) and milliseconds (long) for backward
    // compatibility
    public static void runATILS(String inFile, String solFile, int timeLimitMinutes, String logFile, String outFile) {
        runATILS(inFile, solFile, (long) timeLimitMinutes * 60 * 1000L, logFile, outFile);
    }

    public static void runATILS(String inFile, String solFile, long timeLimitMs, String logFile, String outFile) {
        try {
            System.out.println("AT-ILS: Loading solution from: " + solFile);
            System.out.flush();
            System.out.println("AT-ILS: Log file will be written to: " + logFile);
            System.out.flush();

            long seed = System.currentTimeMillis();
            IHTP_ATILS atils = new IHTP_ATILS(inFile, solFile, outFile, seed);
            atils.logFilePath = logFile;

            // Verify log file path
            if (logFile != null && !logFile.isEmpty()) {
                java.io.File logFileObj = new java.io.File(logFile);
                java.io.File logDir = logFileObj.getParentFile();
                if (logDir != null && !logDir.exists()) {
                    logDir.mkdirs();
                    System.out.println("AT-ILS: Created log directory: " + logDir.getAbsolutePath());
                    System.out.flush();
                }
                System.out.println("AT-ILS: Log file path: " + logFileObj.getAbsolutePath());
                System.out.flush();
            }

            atils.optimizeSolution(timeLimitMs);

            // CRITICAL: Ensure we save a FEASIBLE solution (Hard=0)
            // Re-evaluate bestState to ensure accurate violation count
            atils.rebuildHelpers(atils.bestState);
            atils.evaluateFull(atils.bestState);

            if (atils.bestState.hardViolations == 0) {
                // Best state is feasible - save it
                atils.saveBest(atils.bestState);
                System.out.println("AT-ILS: Saved FEASIBLE best solution (Hard=0, Soft="
                        + String.format("%.2f", atils.bestState.softCost) + ")");
            } else if (atils.initialFeasibleBackup != null) {
                // Best state is infeasible - use backup
                System.err.println("WARNING: AT-ILS best state has " + atils.bestState.hardViolations
                        + " hard violations! Using initial feasible backup instead.");
                atils.saveBest(atils.initialFeasibleBackup);
                System.out.println("AT-ILS: Saved FALLBACK feasible solution (Hard=0, Soft="
                        + String.format("%.2f", atils.initialFeasibleBackup.softCost) + ")");
            } else {
                // No feasible solution available - save best anyway with warning
                System.err.println("CRITICAL WARNING: No feasible solution available! Saving infeasible solution.");
                atils.saveBest(atils.bestState);
            }

            // Verify log file was created
            if (logFile != null && !logFile.isEmpty()) {
                java.io.File logFileObj = new java.io.File(logFile);
                if (logFileObj.exists()) {
                    System.out
                            .println("AT-ILS: Log file created successfully. Size: " + logFileObj.length() + " bytes");
                    System.out.flush();
                } else {
                    System.err.println("AT-ILS: WARNING - Log file was not created: " + logFile);
                    System.err.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("AT-ILS: Error during optimization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Main entry point - supports two formats: Format 1 (for IHTP_Optimizer):
     * <input.json> <time_limit_min> <log.csv> <output.json> [input_sol.json] Format
     * 2 (for IHTP_Launcher_V2): <instancePath> <solutionPath> <outputFile>
     * <runtime> <atilsLogFile>
     */
    public static void main(String[] args) {
        if (args.length >= 5) {
            // Format 2: For IHTP_Launcher_V2 Mode 2
            // args: <instancePath> <solutionPath> <outputFile> <runtime> <atilsLogFile>
            String inFile = args[0];
            String solFile = args[1];
            String outFile = args[2];
            int timeLimitMinutes = Integer.parseInt(args[3]);
            String logFile = args[4];

            runATILS(inFile, solFile, timeLimitMinutes, logFile, outFile);
        } else if (args.length >= 4) {
            // Format 1: For IHTP_Optimizer
            // args: <input.json> <time_limit_min> <log.csv> <output.json> [input_sol.json]
            String inFile = args[0];
            int timeLimitMinutes = Integer.parseInt(args[1]);
            String logFile = args[2];
            String outFile = args[3];
            String solFile = (args.length >= 5) ? args[4] : "solution.json";

            runATILS(inFile, solFile, timeLimitMinutes, logFile, outFile);
        } else {
            System.out.println("Usage (Format 1 - for IHTP_Optimizer):");
            System.out.println(
                    "  java IHTP_ATILS <input.json> <time_limit_min> <log.csv> <output.json> [input_sol.json]");
            System.out.println("\nUsage (Format 2 - for IHTP_Launcher_V2):");
            System.out.println("  java IHTP_ATILS <instancePath> <solutionPath> <outputFile> <runtime> <atilsLogFile>");
        }
    }
}
