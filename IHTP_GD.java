import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

public class IHTP_GD {

    // --- Configuration ---
    private long maxRuntimeSeconds = 600; // Instance variable, default 600

    // Modified Great Deluge Parameters (Kahar & Kendall, 2015)
    private static final int STAGNATION_LIMIT = 5; // W = 5
    private static final long ESTIMATED_MAX_ITERATIONS = 10_000_000; // For decay calculation

    // --- Main Entry Point ---

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java IHTP_GD <input.json> <solution.json> [output_solution.json]");
            return;
        }

        String inputFile = args[0];
        String initialSolutionFile = args[1];
        String outputSolutionFile = (args.length >= 3) ? args[2] : "best_solution_gd.json";

        // Default 10 min if running from main
        runGD(inputFile, initialSolutionFile, outputSolutionFile, 10);
    }

    /**
     * Method called from IHTP_Optimizer launcher Runs GD optimization with
     * violation logging support
     */
    public static void runGDFromLauncher(String inputFile, int timeLimitMinutes, String violationLogFile,
            String outputSolutionFile, String initialSolutionFile) {
        try {
            System.out.println("=== IHTP_GD Optimizer (Launcher Mode) ===");
            System.out.println("Input: " + inputFile);
            System.out.println("Runtime: " + timeLimitMinutes + " min");
            System.out.println("Output: " + outputSolutionFile);

            IHTP_Input input = new IHTP_Input(inputFile);

            List<IHTP_Solution.OutputPatient> initialPatients;
            List<IHTP_Solution.OutputNurse> initialNurses;

            if (initialSolutionFile != null && new java.io.File(initialSolutionFile).exists()) {
                System.out.println("Loading initial solution: " + initialSolutionFile);
                String jsonContent = Files.readString(Paths.get(initialSolutionFile));
                JSONObject jsonSol = new JSONObject(jsonContent);

                initialPatients = parsePatients(jsonSol, input);
                initialNurses = parseNurses(jsonSol, input);
            } else {
                System.out.println("No initial solution provided, starting from empty solution");
                initialPatients = new ArrayList<>();
                initialNurses = new ArrayList<>();

                // Initialize empty patients
                for (int i = 0; i < input.Patients(); i++) {
                    IHTP_Solution.OutputPatient op = new IHTP_Solution.OutputPatient();
                    op.id = input.PatientId(i);
                    initialPatients.add(op);
                }

                // Initialize empty nurses
                for (int i = 0; i < input.Nurses(); i++) {
                    IHTP_Solution.OutputNurse on = new IHTP_Solution.OutputNurse();
                    on.id = input.NurseId(i);
                    initialNurses.add(on);
                }
            }

            // Run optimization
            System.out.println("Starting Adaptive Great Deluge optimization...");
            System.out.println("Violation Log: " + violationLogFile);
            IHTP_GD optimizer = new IHTP_GD(input);
            optimizer.maxRuntimeSeconds = timeLimitMinutes * 60L;
            optimizer.run(initialPatients, initialNurses, outputSolutionFile, violationLogFile);

            System.out.println("=== Optimization Complete ===");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<IHTP_Solution.OutputPatient> parsePatients(JSONObject jsonSol, IHTP_Input input) {
        List<IHTP_Solution.OutputPatient> patients = new ArrayList<>();
        JSONArray patArr = jsonSol.getJSONArray("patients");
        for (int i = 0; i < patArr.length(); i++) {
            JSONObject p = patArr.getJSONObject(i);
            IHTP_Solution.OutputPatient op = new IHTP_Solution.OutputPatient();
            op.id = p.getString("id");
            if (p.has("admission_day") && !p.get("admission_day").toString().equals("none")) {
                op.assignedDay = p.getInt("admission_day");
                op.assignedRoom = p.getString("room");
                op.assignedTheater = p.getString("operating_theater");
            }
            patients.add(op);
        }
        return patients;
    }

    private static List<IHTP_Solution.OutputNurse> parseNurses(JSONObject jsonSol, IHTP_Input input) {
        List<IHTP_Solution.OutputNurse> nurses = new ArrayList<>();
        JSONArray nurseArr = jsonSol.getJSONArray("nurses");
        for (int i = 0; i < nurseArr.length(); i++) {
            JSONObject n = nurseArr.getJSONObject(i);
            IHTP_Solution.OutputNurse on = new IHTP_Solution.OutputNurse();
            on.id = n.getString("id");
            JSONArray assigns = n.getJSONArray("assignments");
            for (int j = 0; j < assigns.length(); j++) {
                JSONObject a = assigns.getJSONObject(j);
                int day = a.getInt("day");
                String shiftStr = a.getString("shift");
                int shiftIdx = input.findShiftIndex(shiftStr);

                int globalShift = day * input.ShiftsPerDay() + shiftIdx;

                on.workingShifts.add(globalShift);
                List<String> rooms = new ArrayList<>();
                JSONArray rArr = a.getJSONArray("rooms");
                for (int k = 0; k < rArr.length(); k++) {
                    rooms.add(rArr.getString(k));
                }
                on.workingRooms.add(rooms);
            }
            nurses.add(on);
        }
        return nurses;
    }

    public static void runGD(String inputFile, String initialSolutionFile, String outputSolutionFile,
            int timeLimitMinutes) {
        try {
            System.out.println("Loading input and initial solution...");
            IHTP_Input input = new IHTP_Input(inputFile);

            // Load initial solution JSON
            String jsonContent = Files.readString(Paths.get(initialSolutionFile));
            JSONObject jsonSol = new JSONObject(jsonContent);

            // Parse initial solution
            List<IHTP_Solution.OutputPatient> initialPatients = new ArrayList<>();
            JSONArray patArr = jsonSol.getJSONArray("patients");
            for (int i = 0; i < patArr.length(); i++) {
                JSONObject p = patArr.getJSONObject(i);
                IHTP_Solution.OutputPatient op = new IHTP_Solution.OutputPatient();
                op.id = p.getString("id");
                if (p.has("admission_day") && !p.get("admission_day").toString().equals("none")) {
                    op.assignedDay = p.getInt("admission_day");
                    op.assignedRoom = p.getString("room");
                    op.assignedTheater = p.getString("operating_theater");
                }
                initialPatients.add(op);
            }

            List<IHTP_Solution.OutputNurse> initialNurses = new ArrayList<>();
            JSONArray nurseArr = jsonSol.getJSONArray("nurses");
            for (int i = 0; i < nurseArr.length(); i++) {
                JSONObject n = nurseArr.getJSONObject(i);
                IHTP_Solution.OutputNurse on = new IHTP_Solution.OutputNurse();
                on.id = n.getString("id");
                JSONArray assigns = n.getJSONArray("assignments");
                for (int j = 0; j < assigns.length(); j++) {
                    JSONObject a = assigns.getJSONObject(j);
                    int day = a.getInt("day");
                    String shiftStr = a.getString("shift");
                    int shiftIdx = input.findShiftIndex(shiftStr);

                    int globalShift = day * input.ShiftsPerDay() + shiftIdx;

                    on.workingShifts.add(globalShift);
                    List<String> rooms = new ArrayList<>();
                    JSONArray rArr = a.getJSONArray("rooms");
                    for (int k = 0; k < rArr.length(); k++) {
                        rooms.add(rArr.getString(k));
                    }
                    on.workingRooms.add(rooms);
                }
                initialNurses.add(on);
            }

            // Run optimization
            System.out.println("Starting Adaptive Great Deluge optimization (Improvisation)...");
            System.out.println("Time Limit: " + timeLimitMinutes + " min");
            IHTP_GD optimizer = new IHTP_GD(input);
            optimizer.maxRuntimeSeconds = timeLimitMinutes * 60;
            optimizer.run(initialPatients, initialNurses, outputSolutionFile, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final IHTP_Input input;

    public IHTP_GD(IHTP_Input input) {
        this.input = input;
    }

    public void run(List<IHTP_Solution.OutputPatient> initialPatients, List<IHTP_Solution.OutputNurse> initialNurses,
            String outputFile, String violationLogFile) throws Exception {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + maxRuntimeSeconds * 1000;

        // Current Solution holds the SINGLE state we will operate on
        GD_Solution currentSol = new GD_Solution(input, initialPatients, initialNurses);
        long currentCost = currentSol.calculateTotalSoftCost();

        System.out.println("Initial Soft Cost: " + currentCost);

        // Setup CSV logging
        String csvHeader = "iteration,time_sec,current_cost,best_cost,boundary,phase";
        long logInterval = 1000; // Log every 1000 iterations
        List<String> logBuffer = new ArrayList<>();

        // Create parent directory if needed and write header
        if (violationLogFile != null) {
            java.io.File logFile = new java.io.File(violationLogFile);
            if (logFile.getParentFile() != null && !logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            writeHeader(violationLogFile, csvHeader);
        }

        // Best solution found
        long bestCost = currentCost;
        GD_Solution bestSol = currentSol.deepCopy();

        // 1.1 Adaptive Boundary Control Initialization
        double B = currentCost + (currentCost * 0.03);

        // 1.4 Desired Value Initialization (D)
        double D = currentCost;

        long totalIterations = ESTIMATED_MAX_ITERATIONS;
        long iteration = 0;
        int stagnationCounter = 0;

        // Initial Decay Calculation
        if (currentCost <= D) {
            D = 0.8 * currentCost;
        }
        double baseDecayRate = (currentCost - D) / (double) totalIterations;

        System.out.println("Init B: " + String.format("%.2f", B) + " D: " + String.format("%.2f", D) + " Decay: "
                + String.format("%.6f", baseDecayRate));

        // --- 1.2 Multi-Phase Search Control Setup ---
        // Phases: Exploration (0-30%), Balanced (30-80%), Intensification (80-100%)
        final double PHASE_EXPLORE_END = 0.3;
        final double PHASE_BALANCED_END = 0.8;

        while (System.currentTimeMillis() < endTime && B > 0) {
            iteration++;
            long currentTime = System.currentTimeMillis();
            double timeRatio = (double) (currentTime - startTime) / (endTime - startTime);
            long remainingIterations = totalIterations - iteration;
            if (remainingIterations <= 0)
                remainingIterations = 1;

            // CSV Logging every logInterval iterations
            if (violationLogFile != null && iteration % logInterval == 0) {
                double elapsedSec = (currentTime - startTime) / 1000.0;
                String phase = (timeRatio < PHASE_EXPLORE_END) ? "EXPLORE"
                        : (timeRatio < PHASE_BALANCED_END) ? "BALANCED" : "INTENSIFY";
                String logEntry = String.format(java.util.Locale.US, "%d,%.2f,%d,%d,%.2f,%s", iteration, elapsedSec,
                        currentCost, bestCost, B, phase);
                logBuffer.add(logEntry);

                // Append to file immediately for live visualization
                appendLogEntries(violationLogFile, logBuffer, logBuffer.size() - 1);
            }

            // --- Phase-Dependent Decay ---
            double decayMultiplier = 1.0;
            String phaseName = "BALANCED";

            if (timeRatio < PHASE_EXPLORE_END) {
                decayMultiplier = 0.5; // Slower decay for Exploration
                phaseName = "EXPLORE";
            } else if (timeRatio > PHASE_BALANCED_END) {
                decayMultiplier = 1.5; // Faster decay for Intensification
                phaseName = "INTENSE";
            }

            // 1.4 Dynamic Desired Value Update (Check periodically or on improvement)
            if (currentCost <= D) {
                D = 0.8 * currentCost;
                // Recalculate Base Decay
                baseDecayRate = (currentCost - D) / (double) remainingIterations;
            }

            double currentDecay = baseDecayRate * decayMultiplier;

            // --- 1.1 Adaptive Neighborhood Selection ---
            int selectedMoveType = currentSol.selectAdaptiveMoveType();
            GD_Solution.Move move = currentSol.createMoveByType(selectedMoveType);

            if (move == null)
                continue;

            // 2. Apply Move
            move.apply();

            // 3. Check Feasibility
            boolean feasible = !currentSol.hasHardViolations();
            boolean accepted = false;
            boolean globalImprovement = false;

            if (!feasible) {
                move.undo(); // Reject
                stagnationCounter++;
                currentSol.updateMoveStats(selectedMoveType, false, false, false);
            } else {
                long newCost = currentSol.calculateTotalSoftCost();

                // 2. Acceptance Rule (Deterministic GD)
                if (newCost <= currentCost || newCost <= B) {
                    // Accept
                    accepted = true;
                    boolean improved = (newCost < currentCost);
                    currentCost = newCost;

                    // Update Best
                    if (newCost < bestCost) {
                        bestCost = newCost;
                        bestSol = currentSol.deepCopy();
                        globalImprovement = true;

                        // Log significant partial progress
                        if (iteration % 5000 == 0 || globalImprovement) {
                            System.out.println("Phase: " + phaseName + " | New Best: " + bestCost + " (Iter: "
                                    + iteration + ", B: " + String.format("%.2f", B) + ")");
                        }

                        saveSolution(bestSol, outputFile);
                        stagnationCounter = 0;
                    } else if (improved) {
                        stagnationCounter = 0;
                    } else {
                        // Accepted but no improvement (lateral/worse but <= B)
                        stagnationCounter++;
                    }

                } else {
                    // Reject
                    move.undo();
                    stagnationCounter++;
                }

                // Feedback for Adaptive Selection
                currentSol.updateMoveStats(selectedMoveType, accepted, true, globalImprovement);
            }

            // --- 1.3 Improved Stagnation Handling ---
            // Dynamic Stagnation Threshold dependent on Phase
            int dynamicLimit = STAGNATION_LIMIT;
            if (timeRatio < PHASE_EXPLORE_END)
                dynamicLimit = STAGNATION_LIMIT * 3; // Tolerate more stagnation early

            if (stagnationCounter >= dynamicLimit) {
                // Adaptive Recovery Mechanism
                // Rollback + Perturb
                currentSol = bestSol.deepCopy();
                currentSol.perturbState(3); // Small perturbation
                currentCost = currentSol.calculateTotalSoftCost();

                // Reset Boundary partially
                B = currentCost + (currentCost * 0.02);

                // Recalculate Decay
                baseDecayRate = (currentCost - D) / (double) remainingIterations;
                if (baseDecayRate < 0)
                    baseDecayRate = 0;

                stagnationCounter = 0;
            }

            // Boundary Update
            B -= currentDecay;
            if (B < 0)
                B = 0;
        }

        // --- 1.4 Lightweight Intensification (Late Search) ---
        System.out.println("Starting Lightweight Intensification (Greedy Hill-Climbing)...");
        // Run a short deterministic greedy phase on the BEST solution found
        currentSol = bestSol.deepCopy();
        currentCost = bestCost;
        long intenseEndTime = System.currentTimeMillis() + 5000; // 5 seconds max
        int intenseIter = 0;

        while (System.currentTimeMillis() < intenseEndTime) {
            intenseIter++;
            // Simple greedy: Try random moves, accept ONLY if strict improvement
            GD_Solution.Move move = currentSol.pickRandomMove(); // Use random for speed
            if (move == null)
                continue;
            move.apply();
            if (!currentSol.hasHardViolations()) {
                long newCost = currentSol.calculateTotalSoftCost();
                if (newCost < bestCost) {
                    bestCost = newCost;
                    bestSol = currentSol.deepCopy();
                    currentCost = newCost;
                    System.out.println(" [Intensify] Improved to: " + bestCost);
                    saveSolution(bestSol, outputFile);
                } else {
                    move.undo();
                }
            } else {
                move.undo();
            }
        }

        System.out.println("Optimization Finished.");
        System.out.println("Total Iterations: " + iteration);
        System.out.println("Intensification Iterations: " + intenseIter);
        System.out.println("Best Cost Found: " + bestCost);

        // Final CSV log entry
        if (violationLogFile != null) {
            double finalElapsedSec = (System.currentTimeMillis() - startTime) / 1000.0;
            String finalEntry = String.format(java.util.Locale.US, "%d,%.2f,%d,%d,%.2f,%s", iteration + intenseIter,
                    finalElapsedSec, bestCost, bestCost, 0.0, "FINAL");
            logBuffer.add(finalEntry);
            appendLogEntries(violationLogFile, logBuffer, logBuffer.size() - 1);
        }

        saveSolution(bestSol, outputFile);
    }

    /**
     * Write CSV header to file (overwrite mode)
     */
    private void writeHeader(String path, String header) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(path, false))) {
            pw.println(header);
        } catch (Exception e) {
            System.err.println("Error writing CSV header: " + e.getMessage());
        }
    }

    /**
     * Append log entries to CSV file
     */
    private void appendLogEntries(String path, List<String> buffer, int fromIndex) {
        if (fromIndex >= buffer.size())
            return;
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(path, true))) {
            for (int i = fromIndex; i < buffer.size(); i++) {
                pw.println(buffer.get(i));
            }
        } catch (Exception e) {
            System.err.println("Error appending to CSV: " + e.getMessage());
        }
    }

    private void saveSolution(GD_Solution sol, String path) {
        try {
            JSONObject root = new JSONObject();

            JSONArray pArr = new JSONArray();
            for (IHTP_Solution.OutputPatient p : sol.patients) {
                JSONObject obj = new JSONObject();
                obj.put("id", p.id);
                if (p.assignedDay == null) {
                    obj.put("admission_day", "none");
                } else {
                    obj.put("admission_day", p.assignedDay);
                    obj.put("room", p.assignedRoom);
                    obj.put("operating_theater", p.assignedTheater);
                }
                pArr.put(obj);
            }
            root.put("patients", pArr);

            JSONArray nArr = new JSONArray();
            for (IHTP_Solution.OutputNurse n : sol.nurses) {
                JSONObject obj = new JSONObject();
                obj.put("id", n.id);
                JSONArray assigns = new JSONArray();
                for (int i = 0; i < n.workingShifts.size(); i++) {
                    int globalShift = n.workingShifts.get(i);
                    int day = globalShift / input.ShiftsPerDay();
                    String shiftStr = input.ShiftName(globalShift % input.ShiftsPerDay());
                    JSONObject aObj = new JSONObject();
                    aObj.put("day", day);
                    aObj.put("shift", shiftStr);
                    // n.workingRooms is List<List<String>>
                    aObj.put("rooms", new JSONArray(n.workingRooms.get(i)));
                    assigns.put(aObj);
                }
                obj.put("assignments", assigns);
                nArr.put(obj);
            }
            root.put("nurses", nArr);

            Files.writeString(Paths.get(path), root.toString(2));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Inner Class: Solution State & Logic ---

    class GD_Solution {
        IHTP_Input input;
        List<IHTP_Solution.OutputPatient> patients;
        List<IHTP_Solution.OutputNurse> nurses;
        Random rnd = new Random();

        // State for cost calculation
        private int[] admissionDay;
        private int[] assignedRoomIndex;
        private int[] assignedTheaterIndex;
        private int[][] roomShiftNurse;
        private List<Integer>[][] nurseShiftRoomList;
        private int[][] nurseShiftLoad;
        private List<Integer>[][] roomDayPatientList;
        private int[][] roomDayAPatients;
        private int[][] roomDayBPatients;
        private List<Integer>[][] operatingTheaterDayPatientList;
        private int[][] operatingTheaterDayLoad;
        private int[][] surgeonDayLoad;
        private int[][][] surgeonDayTheaterCount;
        private List<Integer>[] patientShiftNurse; // For continuity of care (Patient + Occupant)

        private boolean debugMode = false;

        // Constructor for initial load
        public GD_Solution(IHTP_Input input, List<IHTP_Solution.OutputPatient> patients,
                List<IHTP_Solution.OutputNurse> nurses) {
            this.input = input;
            this.patients = patients;
            this.nurses = nurses;
            buildState();
        }

        // Private constructor for deep copy efficiency
        private GD_Solution(IHTP_Input input, List<IHTP_Solution.OutputPatient> patients,
                List<IHTP_Solution.OutputNurse> nurses, boolean dummy) {
            this.input = input;
            this.patients = patients;
            this.nurses = nurses;
            // State will be populated by deepCopy
        }

        public GD_Solution deepCopy() {
            // Deep copy lists
            List<IHTP_Solution.OutputPatient> pCopy = new ArrayList<>();
            for (IHTP_Solution.OutputPatient p : patients) {
                IHTP_Solution.OutputPatient np = new IHTP_Solution.OutputPatient();
                np.id = p.id;
                np.assignedDay = p.assignedDay;
                np.assignedRoom = p.assignedRoom;
                np.assignedTheater = p.assignedTheater;
                pCopy.add(np);
            }
            List<IHTP_Solution.OutputNurse> nCopy = new ArrayList<>();
            for (IHTP_Solution.OutputNurse n : nurses) {
                IHTP_Solution.OutputNurse nn = new IHTP_Solution.OutputNurse();
                nn.id = n.id;
                nn.workingShifts = new ArrayList<>(n.workingShifts);
                nn.workingRooms = new ArrayList<>();
                for (List<String> rList : n.workingRooms) {
                    nn.workingRooms.add(new ArrayList<>(rList));
                }
                nCopy.add(nn);
            }

            GD_Solution copy = new GD_Solution(input, pCopy, nCopy, true);

            // Deep copy arrays for state
            copy.admissionDay = Arrays.copyOf(this.admissionDay, this.admissionDay.length);
            copy.assignedRoomIndex = Arrays.copyOf(this.assignedRoomIndex, this.assignedRoomIndex.length);
            copy.assignedTheaterIndex = Arrays.copyOf(this.assignedTheaterIndex, this.assignedTheaterIndex.length);

            // Matrices
            copy.roomShiftNurse = copyMatrix(this.roomShiftNurse);
            copy.nurseShiftLoad = copyMatrix(this.nurseShiftLoad);
            copy.roomDayAPatients = copyMatrix(this.roomDayAPatients);
            copy.roomDayBPatients = copyMatrix(this.roomDayBPatients);
            copy.operatingTheaterDayLoad = copyMatrix(this.operatingTheaterDayLoad);
            copy.surgeonDayLoad = copyMatrix(this.surgeonDayLoad);

            // 3D Matrix
            copy.surgeonDayTheaterCount = new int[input.Surgeons()][input.Days()][input.OperatingTheaters()];
            for (int i = 0; i < input.Surgeons(); i++) {
                for (int j = 0; j < input.Days(); j++) {
                    System.arraycopy(this.surgeonDayTheaterCount[i][j], 0, copy.surgeonDayTheaterCount[i][j], 0,
                            this.surgeonDayTheaterCount[i][j].length);
                }
            }

            // List arrays (Deep copy of lists within arrays)
            copy.nurseShiftRoomList = copyListMatrix(this.nurseShiftRoomList);
            copy.roomDayPatientList = copyListMatrix(this.roomDayPatientList);
            copy.operatingTheaterDayPatientList = copyListMatrix(this.operatingTheaterDayPatientList);
            copy.patientShiftNurse = copyListArray(this.patientShiftNurse);

            return copy;
        }

        private int[][] copyMatrix(int[][] src) {
            int[][] dest = new int[src.length][];
            for (int i = 0; i < src.length; i++) {
                dest[i] = Arrays.copyOf(src[i], src[i].length);
            }
            return dest;
        }

        @SuppressWarnings("unchecked")
        private List<Integer>[][] copyListMatrix(List<Integer>[][] src) {
            List<Integer>[][] dest = new List[src.length][src[0].length];
            for (int i = 0; i < src.length; i++) {
                for (int j = 0; j < src[i].length; j++) {
                    dest[i][j] = new ArrayList<>(src[i][j]);
                }
            }
            return dest;
        }

        @SuppressWarnings("unchecked")
        private List<Integer>[] copyListArray(List<Integer>[] src) {
            List<Integer>[] dest = new List[src.length];
            for (int i = 0; i < src.length; i++) {
                dest[i] = new ArrayList<>(src[i]);
            }
            return dest;
        }

        // --- Hard Constraint Checks ---
        public boolean hasHardViolations() {
            return getValidator().getTotalViolations() > 0;
        }

        private IHTP_Validator getValidator() {
            JSONObject root = new JSONObject();
            JSONArray pArr = new JSONArray();
            for (IHTP_Solution.OutputPatient p : patients) {
                JSONObject obj = new JSONObject();
                obj.put("id", p.id);
                if (p.assignedDay == null)
                    obj.put("admission_day", "none");
                else {
                    obj.put("admission_day", p.assignedDay);
                    obj.put("room", p.assignedRoom);
                    obj.put("operating_theater", p.assignedTheater);
                }
                pArr.put(obj);
            }
            root.put("patients", pArr);

            JSONArray nArr = new JSONArray();
            for (IHTP_Solution.OutputNurse n : nurses) {
                JSONObject obj = new JSONObject();
                obj.put("id", n.id);
                JSONArray assigns = new JSONArray();
                for (int i = 0; i < n.workingShifts.size(); i++) {
                    int globalShift = n.workingShifts.get(i);
                    int day = globalShift / input.ShiftsPerDay();
                    String shiftStr = input.ShiftName(globalShift % input.ShiftsPerDay());
                    JSONObject aObj = new JSONObject();
                    aObj.put("day", day);
                    aObj.put("shift", shiftStr);
                    // n.workingRooms is List<List<String>>
                    aObj.put("rooms", new JSONArray(n.workingRooms.get(i)));
                    assigns.put(aObj);
                }
                obj.put("assignments", assigns);
                nArr.put(obj);
            }
            root.put("nurses", nArr);

            IHTP_Validator val = new IHTP_Validator(input, root, false);
            if (debugMode) {
                long internal = calculateCostInternal(false);
                long external = val.getTotalCost();
                if (internal != external) {
                    System.out.println(
                            "Debug Verify: Int=" + internal + " Ext=" + external + " Diff=" + (internal - external));
                }
            }
            return val;
        }

        // --- State Building ---
        @SuppressWarnings("unchecked")
        private void buildState() {
            int P = input.Patients();
            int R = input.Rooms();
            int S = input.Shifts();
            int D = input.Days();
            int T = input.OperatingTheaters();
            int N = input.Nurses();
            int Sur = input.Surgeons();
            int Occ = input.Occupants();

            admissionDay = new int[P];
            Arrays.fill(admissionDay, -1);
            assignedRoomIndex = new int[P];
            Arrays.fill(assignedRoomIndex, -1);
            assignedTheaterIndex = new int[P];
            Arrays.fill(assignedTheaterIndex, -1);

            roomShiftNurse = new int[R][S];
            for (int[] row : roomShiftNurse)
                Arrays.fill(row, -1);

            nurseShiftRoomList = new List[N][S];
            nurseShiftLoad = new int[N][S];
            for (int n = 0; n < N; n++) {
                for (int s = 0; s < S; s++) {
                    nurseShiftRoomList[n][s] = new ArrayList<>();
                }
            }

            roomDayPatientList = new List[R][D];
            roomDayAPatients = new int[R][D];
            roomDayBPatients = new int[R][D];
            for (int r = 0; r < R; r++) {
                for (int d = 0; d < D; d++) {
                    roomDayPatientList[r][d] = new ArrayList<>();
                }
            }

            operatingTheaterDayPatientList = new List[T][D];
            operatingTheaterDayLoad = new int[T][D];
            for (int t = 0; t < T; t++) {
                for (int d = 0; d < D; d++) {
                    operatingTheaterDayPatientList[t][d] = new ArrayList<>();
                }
            }

            surgeonDayLoad = new int[Sur][D];
            surgeonDayTheaterCount = new int[Sur][D][T];

            patientShiftNurse = new List[P + Occ];
            for (int i = 0; i < P + Occ; i++) {
                int len = (i < P) ? input.PatientLengthOfStay(i) : input.OccupantLengthOfStay(i - P);
                patientShiftNurse[i] = new ArrayList<>();
                for (int k = 0; k < len * input.ShiftsPerDay(); k++)
                    patientShiftNurse[i].add(-1);
            }

            // CRITICAL FIX: Load Occupants FIRST so Nurses see them!
            updateWithOccupantsInfo();

            for (IHTP_Solution.OutputPatient p : patients) {
                if (p.assignedDay == null)
                    continue;
                int pIdx = input.patientIndexById.get(p.id);
                int ad = p.assignedDay;
                int rIdx = input.roomIndexById.get(p.assignedRoom);
                int tIdx = input.findOperatingTheaterIndex(p.assignedTheater);
                addPatientToState(pIdx, ad, rIdx, tIdx);
            }

            for (IHTP_Solution.OutputNurse n : nurses) {
                int nIdx = input.findNurseIndex(n.id);
                for (int i = 0; i < n.workingShifts.size(); i++) {
                    int globalShift = n.workingShifts.get(i);
                    List<String> rooms = n.workingRooms.get(i);
                    for (String rName : rooms) {
                        int rIdx = input.roomIndexById.get(rName);
                        addNurseAssignmentToState(nIdx, globalShift, rIdx);
                    }
                }
            }
        }

        private void updateWithOccupantsInfo() {
            int offset = input.Patients();
            for (int i = 0; i < input.Occupants(); i++) {
                int glob = i + offset;
                int r = input.OccupantRoom(i);
                for (int d = 0; d < input.OccupantLengthOfStay(i); d++) {
                    if (d >= input.Days())
                        break;
                    roomDayPatientList[r][d].add(glob);
                    if (input.OccupantGender(i) == IHTP_Input.Gender.A)
                        roomDayAPatients[r][d]++;
                    else
                        roomDayBPatients[r][d]++;
                }
            }
        }

        // --- State Helper Methods ---

        private void addPatientToState(int p, int ad, int r, int t) {
            admissionDay[p] = ad;
            assignedRoomIndex[p] = r;
            assignedTheaterIndex[p] = t;

            int len = input.PatientLengthOfStay(p);
            int length = Math.min(input.Days(), ad + len);

            for (int d = ad; d < length; d++) {
                roomDayPatientList[r][d].add(p);
                if (input.PatientGender(p) == IHTP_Input.Gender.A)
                    roomDayAPatients[r][d]++;
                else
                    roomDayBPatients[r][d]++;

                for (int s = d * input.ShiftsPerDay(); s < (d + 1) * input.ShiftsPerDay(); s++) {
                    int n = roomShiftNurse[r][s];
                    if (n != -1) {
                        int rel = s - ad * input.ShiftsPerDay();
                        if (rel < len * input.ShiftsPerDay()) {
                            patientShiftNurse[p].set(rel, n);
                            nurseShiftLoad[n][s] += input.PatientWorkloadProduced(p, rel);
                        }
                    }
                }
            }

            operatingTheaterDayPatientList[t][ad].add(p);
            operatingTheaterDayLoad[t][ad] += input.PatientSurgeryDuration(p);
            int surg = input.PatientSurgeon(p);
            surgeonDayLoad[surg][ad] += input.PatientSurgeryDuration(p);
            surgeonDayTheaterCount[surg][ad][t]++;
        }

        private void removePatientFromState(int p, int ad, int r, int t) {
            if (ad == -1)
                return;

            int len = input.PatientLengthOfStay(p);
            int length = Math.min(input.Days(), ad + len);

            for (int d = ad; d < length; d++) {
                roomDayPatientList[r][d].remove((Integer) p);
                if (input.PatientGender(p) == IHTP_Input.Gender.A)
                    roomDayAPatients[r][d]--;
                else
                    roomDayBPatients[r][d]--;

                for (int s = d * input.ShiftsPerDay(); s < (d + 1) * input.ShiftsPerDay(); s++) {
                    int n = roomShiftNurse[r][s];
                    if (n != -1) {
                        int rel = s - ad * input.ShiftsPerDay();
                        if (rel < len * input.ShiftsPerDay()) {
                            patientShiftNurse[p].set(rel, -1);
                            nurseShiftLoad[n][s] -= input.PatientWorkloadProduced(p, rel);
                        }
                    }
                }
            }

            operatingTheaterDayPatientList[t][ad].remove((Integer) p);
            operatingTheaterDayLoad[t][ad] -= input.PatientSurgeryDuration(p);
            int surg = input.PatientSurgeon(p);
            surgeonDayLoad[surg][ad] -= input.PatientSurgeryDuration(p);
            surgeonDayTheaterCount[surg][ad][t]--;

            admissionDay[p] = -1;
            assignedRoomIndex[p] = -1;
            assignedTheaterIndex[p] = -1;
        }

        private void addNurseAssignmentToState(int n, int s, int r) {
            roomShiftNurse[r][s] = n;
            nurseShiftRoomList[n][s].add(r);

            int day = s / input.ShiftsPerDay();
            for (int p : roomDayPatientList[r][day]) {
                if (p < input.Patients()) {
                    if (admissionDay[p] != -1) {
                        int rel = s - admissionDay[p] * input.ShiftsPerDay();
                        if (rel >= 0 && rel < input.PatientLengthOfStay(p) * input.ShiftsPerDay()) {
                            nurseShiftLoad[n][s] += input.PatientWorkloadProduced(p, rel);
                            patientShiftNurse[p].set(rel, n);
                        }
                    }
                } else {
                    int occ = p - input.Patients();
                    nurseShiftLoad[n][s] += input.OccupantWorkloadProduced(occ, s);
                    if (s < patientShiftNurse[p].size())
                        patientShiftNurse[p].set(s, n);
                }
            }
        }

        private void removeNurseAssignmentFromState(int n, int s, int r) {
            if (roomShiftNurse[r][s] == n) {
                roomShiftNurse[r][s] = -1;
            }
            nurseShiftRoomList[n][s].remove((Integer) r);

            int day = s / input.ShiftsPerDay();
            for (int p : roomDayPatientList[r][day]) {
                if (p < input.Patients()) {
                    if (admissionDay[p] != -1) {
                        int rel = s - admissionDay[p] * input.ShiftsPerDay();
                        if (rel >= 0 && rel < input.PatientLengthOfStay(p) * input.ShiftsPerDay()) {
                            nurseShiftLoad[n][s] -= input.PatientWorkloadProduced(p, rel);
                            patientShiftNurse[p].set(rel, -1);
                        }
                    }
                } else {
                    int occ = p - input.Patients();
                    nurseShiftLoad[n][s] -= input.OccupantWorkloadProduced(occ, s);
                    if (s < patientShiftNurse[p].size())
                        patientShiftNurse[p].set(s, -1);
                }
            }
        }

        // --- Cost Calculation (Identical to verification, relies on state) ---

        public long calculateTotalSoftCost() {
            return calculateCostInternal(false);
        }

        private long calculateCostInternal(boolean print) {
            long c1 = 0, c2 = 0, c3 = 0, c4 = 0, c5 = 0, c6 = 0, c7 = 0, c8 = 0;
            // Calculations...
            for (int r = 0; r < input.Rooms(); r++) {
                for (int d = 0; d < input.Days(); d++) {
                    if (roomDayPatientList[r][d].isEmpty())
                        continue;
                    int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                    for (int p : roomDayPatientList[r][d]) {
                        int age = (p < input.Patients()) ? input.PatientAgeGroup(p)
                                : input.OccupantAgeGroup(p - input.Patients());
                        if (age < min)
                            min = age;
                        if (age > max)
                            max = age;
                    }
                    if (max > min)
                        c1 += (long) (max - min) * input.weights[0];
                }
            }
            for (int r = 0; r < input.Rooms(); r++) {
                for (int s = 0; s < input.Shifts(); s++) {
                    int n = roomShiftNurse[r][s];
                    if (n == -1)
                        continue;
                    int day = s / input.ShiftsPerDay();
                    for (int p : roomDayPatientList[r][day]) {
                        if (p < input.Patients()) {
                            int rel = s - admissionDay[p] * input.ShiftsPerDay();
                            if (rel >= 0 && input.PatientSkillLevelRequired(p, rel) > input.NurseSkillLevel(n)) {
                                c2 += (long) (input.PatientSkillLevelRequired(p, rel) - input.NurseSkillLevel(n))
                                        * input.weights[1];
                            }
                        } else {
                            int occ = p - input.Patients();
                            if (input.OccupantSkillLevelRequired(occ, s) > input.NurseSkillLevel(n)) {
                                c2 += (long) (input.OccupantSkillLevelRequired(occ, s) - input.NurseSkillLevel(n))
                                        * input.weights[1];
                            }
                        }
                    }
                }
            }
            for (int p = 0; p < input.Patients() + input.Occupants(); p++) {
                if (p < input.Patients() && admissionDay[p] == -1)
                    continue;
                boolean[] seen = new boolean[input.Nurses()];
                int distinct = 0;
                if (p < patientShiftNurse.length) {
                    for (int n : patientShiftNurse[p]) {
                        if (n != -1 && !seen[n]) {
                            seen[n] = true;
                            distinct++;
                        }
                    }
                }
                if (distinct > 0)
                    c3 += (long) distinct * input.weights[2];
            }
            for (int n = 0; n < input.Nurses(); n++) {
                for (int i = 0; i < input.NurseWorkingShifts(n); i++) {
                    int s = input.NurseWorkingShift(n, i);
                    if (nurseShiftLoad[n][s] > input.NurseMaxLoad(n, s)) {
                        c4 += (long) (nurseShiftLoad[n][s] - input.NurseMaxLoad(n, s)) * input.weights[3];
                    }
                }
            }
            for (int t = 0; t < input.OperatingTheaters(); t++) {
                for (int d = 0; d < input.Days(); d++) {
                    if (!operatingTheaterDayPatientList[t][d].isEmpty())
                        c5 += (long) input.weights[4];
                }
            }
            for (int s = 0; s < input.Surgeons(); s++) {
                for (int d = 0; d < input.Days(); d++) {
                    int count = 0;
                    for (int t = 0; t < input.OperatingTheaters(); t++) {
                        if (surgeonDayTheaterCount[s][d][t] > 0)
                            count++;
                    }
                    if (count > 1)
                        c6 += (long) (count - 1) * input.weights[5];
                }
            }
            for (int p = 0; p < input.Patients(); p++) {
                if (admissionDay[p] != -1) {
                    if (admissionDay[p] > input.PatientSurgeryReleaseDay(p))
                        c7 += (long) (admissionDay[p] - input.PatientSurgeryReleaseDay(p)) * input.weights[6];
                } else {
                    if (!input.PatientMandatory(p))
                        c8 += (long) input.weights[7];
                }
            }
            long total = c1 + c2 + c3 + c4 + c5 + c6 + c7 + c8;
            if (print)
                System.out.println("TOTAL: " + total);
            return total;
        }

        // --- ABSTRACT MOVE CLASS ---

        abstract class Move {
            abstract void apply();

            abstract void undo();
        }

        // --- ADAPTIVE MOVE SELECTION ---

        // Weights for 8 move types
        private double[] moveWeights = new double[] { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0 };
        private final double MIN_WEIGHT = 0.1;

        public int selectAdaptiveMoveType() {
            // Roulette Wheel Selection
            double totalWeight = 0;
            for (double w : moveWeights)
                totalWeight += w;

            double r = rnd.nextDouble() * totalWeight;
            double count = 0;
            for (int i = 0; i < moveWeights.length; i++) {
                count += moveWeights[i];
                if (count >= r)
                    return i;
            }
            return moveWeights.length - 1; // Fallback
        }

        public void updateMoveStats(int moveType, boolean accepted, boolean valid, boolean newBest) {
            // Reward/Penalty
            if (!valid) {
                // Invalid moves (Hard Constraint violations) penalty
                moveWeights[moveType] *= 0.95;
            } else if (newBest) {
                // Strong Reward
                moveWeights[moveType] += 2.0;
            } else if (accepted) {
                // Moderate Reward
                moveWeights[moveType] += 0.5;
            } else {
                // Rejected (but valid) penalty
                moveWeights[moveType] *= 0.98;
            }
            // Clamp min weight
            if (moveWeights[moveType] < MIN_WEIGHT)
                moveWeights[moveType] = MIN_WEIGHT;
        }

        public void perturbState(int moves) {
            for (int i = 0; i < moves; i++) {
                Move m = pickRandomMove();
                if (m != null) {
                    m.apply();
                    if (hasHardViolations()) {
                        m.undo();
                    }
                }
            }
        }

        // --- Move Selection Factory ---
        public Move pickRandomMove() {
            int moveType = rnd.nextInt(8);
            return createMoveByType(moveType);
        }

        public Move createMoveByType(int moveType) {
            switch (moveType) {
            case 0:
                return createMoveSetPatient();
            case 1:
                return createMoveRemovePatient();
            case 2:
                return createMoveSwapPatients();
            case 3:
                return createMoveKickPatient();
            case 4:
                return createMoveRemovePatient(); // Reuse for weight slot 4
            case 5:
                return createMoveSetNurse();
            case 6:
                return createMoveSwapNurseRoomsSingle();
            case 7:
                return createMoveSwapNurseRoomsAll();
            }
            return null;
        }

        // --- IMPLEMENTATION OF MOVES ---

        // 1. SetPatient
        private Move createMoveSetPatient() {
            if (patients.isEmpty())
                return null;
            IHTP_Solution.OutputPatient p = patients.get(rnd.nextInt(patients.size()));
            int pIdx = input.patientIndexById.get(p.id);

            // Generate NEW assignment
            int release = input.PatientSurgeryReleaseDay(pIdx);
            int due = input.PatientLastPossibleDay(pIdx);
            int newDay = release + rnd.nextInt(due - release + 1);
            String newRoom = input.RoomId(rnd.nextInt(input.Rooms()));
            String newTheater = input.OperatingTheaterId(rnd.nextInt(input.OperatingTheaters()));

            return new MoveSetPatient(p, newDay, newRoom, newTheater);
        }

        class MoveSetPatient extends Move {
            IHTP_Solution.OutputPatient p;
            int oldDay, newDay;
            String oldRoom, newRoom;
            String oldT, newT;
            int pIdx, oldRIdx, oldTIdx, newRIdx, newTIdx;

            MoveSetPatient(IHTP_Solution.OutputPatient p, int d, String r, String t) {
                this.p = p;
                this.newDay = d;
                this.newRoom = r;
                this.newT = t;
                this.pIdx = input.patientIndexById.get(p.id);
                this.oldDay = (p.assignedDay == null) ? -1 : p.assignedDay;
                this.oldRoom = p.assignedRoom;
                this.oldT = p.assignedTheater;
                if (oldDay != -1) {
                    this.oldRIdx = input.findRoomIndex(oldRoom);
                    this.oldTIdx = input.findOperatingTheaterIndex(oldT);
                }
                this.newRIdx = input.findRoomIndex(newRoom);
                this.newTIdx = input.findOperatingTheaterIndex(newT);
            }

            @Override
            void apply() {
                if (oldDay != -1)
                    removePatientFromState(pIdx, oldDay, oldRIdx, oldTIdx);
                p.assignedDay = newDay;
                p.assignedRoom = newRoom;
                p.assignedTheater = newT;
                addPatientToState(pIdx, newDay, newRIdx, newTIdx);
            }

            @Override
            void undo() {
                removePatientFromState(pIdx, newDay, newRIdx, newTIdx);
                p.assignedDay = (oldDay == -1) ? null : oldDay;
                p.assignedRoom = oldRoom;
                p.assignedTheater = oldT;
                if (oldDay != -1)
                    addPatientToState(pIdx, oldDay, oldRIdx, oldTIdx);
            }
        }

        // 2. RemovePatient
        private Move createMoveRemovePatient() {
            List<IHTP_Solution.OutputPatient> victims = new ArrayList<>();
            for (IHTP_Solution.OutputPatient p : patients) {
                if (!input.PatientMandatory(input.patientIndexById.get(p.id)) && p.assignedDay != null)
                    victims.add(p);
            }
            if (victims.isEmpty())
                return null;
            return new MoveRemovePatient(victims.get(rnd.nextInt(victims.size())));
        }

        class MoveRemovePatient extends Move {
            IHTP_Solution.OutputPatient p;
            int oldDay;
            String oldRoom, oldT;
            int pIdx, oldRIdx, oldTIdx;

            MoveRemovePatient(IHTP_Solution.OutputPatient p) {
                this.p = p;
                this.pIdx = input.patientIndexById.get(p.id);
                this.oldDay = p.assignedDay;
                this.oldRoom = p.assignedRoom;
                this.oldT = p.assignedTheater;
                this.oldRIdx = input.findRoomIndex(oldRoom);
                this.oldTIdx = input.findOperatingTheaterIndex(oldT);
            }

            @Override
            void apply() {
                removePatientFromState(pIdx, oldDay, oldRIdx, oldTIdx);
                p.assignedDay = null;
                p.assignedRoom = null;
                p.assignedTheater = null;
            }

            @Override
            void undo() {
                p.assignedDay = oldDay;
                p.assignedRoom = oldRoom;
                p.assignedTheater = oldT;
                addPatientToState(pIdx, oldDay, oldRIdx, oldTIdx);
            }
        }

        // 3. SwapPatients
        private Move createMoveSwapPatients() {
            List<IHTP_Solution.OutputPatient> sched = new ArrayList<>();
            for (IHTP_Solution.OutputPatient p : patients)
                if (p.assignedDay != null)
                    sched.add(p);
            if (sched.size() < 2)
                return null;
            IHTP_Solution.OutputPatient p1 = sched.get(rnd.nextInt(sched.size()));
            IHTP_Solution.OutputPatient p2 = sched.get(rnd.nextInt(sched.size()));
            if (p1 == p2)
                return null;
            return new MoveSwapPatients(p1, p2);
        }

        class MoveSwapPatients extends Move {
            IHTP_Solution.OutputPatient p1, p2;
            int idx1, idx2;
            int d1, d2;
            String r1, r2, t1, t2;
            int rIdx1, rIdx2, tIdx1, tIdx2;

            MoveSwapPatients(IHTP_Solution.OutputPatient p1, IHTP_Solution.OutputPatient p2) {
                this.p1 = p1;
                this.p2 = p2;
                idx1 = input.patientIndexById.get(p1.id);
                idx2 = input.patientIndexById.get(p2.id);
                d1 = p1.assignedDay;
                r1 = p1.assignedRoom;
                t1 = p1.assignedTheater;
                d2 = p2.assignedDay;
                r2 = p2.assignedRoom;
                t2 = p2.assignedTheater;
                rIdx1 = input.findRoomIndex(r1);
                tIdx1 = input.findOperatingTheaterIndex(t1);
                rIdx2 = input.findRoomIndex(r2);
                tIdx2 = input.findOperatingTheaterIndex(t2);
            }

            @Override
            void apply() {
                removePatientFromState(idx1, d1, rIdx1, tIdx1);
                removePatientFromState(idx2, d2, rIdx2, tIdx2);
                p1.assignedDay = d2;
                p1.assignedRoom = r2;
                p1.assignedTheater = t2;
                p2.assignedDay = d1;
                p2.assignedRoom = r1;
                p2.assignedTheater = t1;
                addPatientToState(idx1, d2, rIdx2, tIdx2);
                addPatientToState(idx2, d1, rIdx1, tIdx1);
            }

            @Override
            void undo() {
                removePatientFromState(idx1, d2, rIdx2, tIdx2);
                removePatientFromState(idx2, d1, rIdx1, tIdx1);
                p1.assignedDay = d1;
                p1.assignedRoom = r1;
                p1.assignedTheater = t1;
                p2.assignedDay = d2;
                p2.assignedRoom = r2;
                p2.assignedTheater = t2;
                addPatientToState(idx1, d1, rIdx1, tIdx1);
                addPatientToState(idx2, d2, rIdx2, tIdx2);
            }
        }

        // 4. KickPatient
        private Move createMoveKickPatient() {
            List<IHTP_Solution.OutputPatient> unsch = new ArrayList<>();
            for (IHTP_Solution.OutputPatient p : patients)
                if (p.assignedDay == null)
                    unsch.add(p);
            if (unsch.isEmpty())
                return null;

            IHTP_Solution.OutputPatient kicker = unsch.get(rnd.nextInt(unsch.size()));
            int kIdx = input.patientIndexById.get(kicker.id);

            int rel = input.PatientSurgeryReleaseDay(kIdx);
            int due = input.PatientLastPossibleDay(kIdx);
            int day = rel + rnd.nextInt(due - rel + 1);
            String room = input.RoomId(rnd.nextInt(input.Rooms()));
            String theater = input.OperatingTheaterId(rnd.nextInt(input.OperatingTheaters()));

            // Identify conflicts
            List<IHTP_Solution.OutputPatient> victims = new ArrayList<>();
            int kEnd = day + input.PatientLengthOfStay(kIdx);

            for (IHTP_Solution.OutputPatient other : patients) {
                if (other != kicker && other.assignedDay != null && other.assignedRoom.equals(room)) {
                    int oStart = other.assignedDay;
                    int oEnd = oStart + input.PatientLengthOfStay(input.patientIndexById.get(other.id));
                    if (day < oEnd && oStart < kEnd) {
                        victims.add(other);
                    }
                }
            }

            return new MoveKickPatient(kicker, day, room, theater, victims);
        }

        class MoveKickPatient extends Move {
            IHTP_Solution.OutputPatient kicker;
            int newDay;
            String newRoom, newT;
            List<IHTP_Solution.OutputPatient> victims;
            // Saved state for victims
            List<Integer> vDays = new ArrayList<>();
            List<String> vRooms = new ArrayList<>(), vTheaters = new ArrayList<>();

            MoveKickPatient(IHTP_Solution.OutputPatient k, int d, String r, String t,
                    List<IHTP_Solution.OutputPatient> v) {
                kicker = k;
                newDay = d;
                newRoom = r;
                newT = t;
                victims = v;
                for (IHTP_Solution.OutputPatient vic : victims) {
                    vDays.add(vic.assignedDay);
                    vRooms.add(vic.assignedRoom);
                    vTheaters.add(vic.assignedTheater);
                }
            }

            @Override
            void apply() {
                for (IHTP_Solution.OutputPatient v : victims) {
                    removePatientFromState(input.patientIndexById.get(v.id), v.assignedDay,
                            input.findRoomIndex(v.assignedRoom), input.findOperatingTheaterIndex(v.assignedTheater));
                    v.assignedDay = null;
                    v.assignedRoom = null;
                    v.assignedTheater = null;
                }

                // Add kicker
                kicker.assignedDay = newDay;
                kicker.assignedRoom = newRoom;
                kicker.assignedTheater = newT;
                addPatientToState(input.patientIndexById.get(kicker.id), newDay, input.findRoomIndex(newRoom),
                        input.findOperatingTheaterIndex(newT));
            }

            @Override
            void undo() {
                removePatientFromState(input.patientIndexById.get(kicker.id), newDay, input.findRoomIndex(newRoom),
                        input.findOperatingTheaterIndex(newT));
                kicker.assignedDay = null;
                kicker.assignedRoom = null;
                kicker.assignedTheater = null;

                // Restore victims
                for (int i = 0; i < victims.size(); i++) {
                    IHTP_Solution.OutputPatient v = victims.get(i);
                    v.assignedDay = vDays.get(i);
                    v.assignedRoom = vRooms.get(i);
                    v.assignedTheater = vTheaters.get(i);
                    addPatientToState(input.patientIndexById.get(v.id), v.assignedDay,
                            input.findRoomIndex(v.assignedRoom), input.findOperatingTheaterIndex(v.assignedTheater));
                }
            }
        }

        // 5. SetNurse
        private Move createMoveSetNurse() {
            if (nurses.size() < 2)
                return null;
            IHTP_Solution.OutputNurse n1 = nurses.get(rnd.nextInt(nurses.size()));
            IHTP_Solution.OutputNurse n2 = nurses.get(rnd.nextInt(nurses.size()));
            if (n1 == n2)
                return null;

            if (n1.workingShifts.isEmpty())
                return null;
            int sIdx1 = rnd.nextInt(n1.workingShifts.size());
            int s = n1.workingShifts.get(sIdx1);
            int sIdx2 = n2.workingShifts.indexOf(s);
            if (sIdx2 == -1)
                return null;

            List<String> rList1 = n1.workingRooms.get(sIdx1);
            List<String> rList2 = n2.workingRooms.get(sIdx2);

            String r1 = rList1.isEmpty() ? null : rList1.get(rnd.nextInt(rList1.size()));
            String r2 = rList2.isEmpty() ? null : rList2.get(rnd.nextInt(rList2.size()));

            if (r1 == null && r2 == null)
                return null;

            return new MoveSetNurse(n1, n2, s, sIdx1, sIdx2, r1, r2, rList1, rList2);
        }

        class MoveSetNurse extends Move {
            IHTP_Solution.OutputNurse n1, n2;
            int s, sIdx1, sIdx2;
            String r1, r2;
            List<String> rList1, rList2;
            int n1ID, n2ID, r1Idx, r2Idx;

            MoveSetNurse(IHTP_Solution.OutputNurse n1, IHTP_Solution.OutputNurse n2, int s, int sIdx1, int sIdx2,
                    String r1, String r2, List<String> rl1, List<String> rl2) {
                this.n1 = n1;
                this.n2 = n2;
                this.s = s;
                this.sIdx1 = sIdx1;
                this.sIdx2 = sIdx2;
                this.r1 = r1;
                this.r2 = r2;
                this.rList1 = rl1;
                this.rList2 = rl2;

                n1ID = input.findNurseIndex(n1.id);
                n2ID = input.findNurseIndex(n2.id);
                if (r1 != null)
                    r1Idx = input.findRoomIndex(r1);
                if (r2 != null)
                    r2Idx = input.findRoomIndex(r2);
            }

            @Override
            void apply() {
                if (r1 != null) {
                    removeNurseAssignmentFromState(n1ID, s, r1Idx);
                    rList1.remove(r1);
                    addNurseAssignmentToState(n2ID, s, r1Idx);
                    rList2.add(r1);
                }
                if (r2 != null) {
                    removeNurseAssignmentFromState(n2ID, s, r2Idx);
                    rList2.remove(r2);
                    addNurseAssignmentToState(n1ID, s, r2Idx);
                    rList1.add(r2);
                }
            }

            @Override
            void undo() {
                if (r1 != null) {
                    removeNurseAssignmentFromState(n2ID, s, r1Idx);
                    rList2.remove(r1);
                    addNurseAssignmentToState(n1ID, s, r1Idx);
                    rList1.add(r1);
                }
                if (r2 != null) {
                    removeNurseAssignmentFromState(n1ID, s, r2Idx);
                    rList1.remove(r2);
                    addNurseAssignmentToState(n2ID, s, r2Idx);
                    rList2.add(r2);
                }
            }
        }

        // 6. SwapNurseRoomsSingle
        private Move createMoveSwapNurseRoomsSingle() {
            if (nurses.isEmpty())
                return null;
            IHTP_Solution.OutputNurse n = nurses.get(rnd.nextInt(nurses.size()));
            if (n.workingShifts.isEmpty())
                return null;
            int sIdx = rnd.nextInt(n.workingShifts.size());
            int s = n.workingShifts.get(sIdx);
            List<String> rooms = n.workingRooms.get(sIdx);
            if (rooms.isEmpty())
                return null;

            int rListIdx = rnd.nextInt(rooms.size());
            String oldRoom = rooms.get(rListIdx);
            String newRoom = input.RoomId(rnd.nextInt(input.Rooms()));

            if (oldRoom.equals(newRoom) || rooms.contains(newRoom))
                return null;

            if (roomShiftNurse[input.findRoomIndex(newRoom)][s] != -1)
                return null;

            return new MoveSwapNurseRoomsSingle(n, s, sIdx, rListIdx, oldRoom, newRoom);
        }

        class MoveSwapNurseRoomsSingle extends Move {
            IHTP_Solution.OutputNurse n;
            int s, sIdx, rListIdx;
            String oldRoom, newRoom;
            int nIdx, oldRIdx, newRIdx;

            MoveSwapNurseRoomsSingle(IHTP_Solution.OutputNurse n, int s, int sIdx, int rListIdx, String oldRoom,
                    String newRoom) {
                this.n = n;
                this.s = s;
                this.sIdx = sIdx;
                this.rListIdx = rListIdx;
                this.oldRoom = oldRoom;
                this.newRoom = newRoom;
                nIdx = input.findNurseIndex(n.id);
                oldRIdx = input.findRoomIndex(oldRoom);
                newRIdx = input.findRoomIndex(newRoom);
            }

            @Override
            void apply() {
                removeNurseAssignmentFromState(nIdx, s, oldRIdx);
                n.workingRooms.get(sIdx).set(rListIdx, newRoom);
                addNurseAssignmentToState(nIdx, s, newRIdx);
            }

            @Override
            void undo() {
                removeNurseAssignmentFromState(nIdx, s, newRIdx);
                n.workingRooms.get(sIdx).set(rListIdx, oldRoom);
                addNurseAssignmentToState(nIdx, s, oldRIdx);
            }
        }

        // 7. SwapNurseRoomsAll
        private Move createMoveSwapNurseRoomsAll() {
            if (nurses.size() < 2)
                return null;
            int n1Idx = rnd.nextInt(nurses.size());
            int n2Idx = rnd.nextInt(nurses.size());
            if (n1Idx == n2Idx)
                return null;
            return new MoveSwapNurseRoomsAll(nurses.get(n1Idx), nurses.get(n2Idx));
        }

        class MoveSwapNurseRoomsAll extends Move {
            IHTP_Solution.OutputNurse n1, n2;
            int n1ID, n2ID;
            List<Integer> s1, s2;
            List<List<String>> r1, r2;

            MoveSwapNurseRoomsAll(IHTP_Solution.OutputNurse n1, IHTP_Solution.OutputNurse n2) {
                this.n1 = n1;
                this.n2 = n2;
                n1ID = input.findNurseIndex(n1.id);
                n2ID = input.findNurseIndex(n2.id);
                s1 = n1.workingShifts;
                r1 = n1.workingRooms;
                s2 = n2.workingShifts;
                r2 = n2.workingRooms;
            }

            @Override
            void apply() {
                for (int i = 0; i < s1.size(); i++) {
                    int s = s1.get(i);
                    for (String r : r1.get(i))
                        removeNurseAssignmentFromState(n1ID, s, input.findRoomIndex(r));
                }
                for (int i = 0; i < s2.size(); i++) {
                    int s = s2.get(i);
                    for (String r : r2.get(i))
                        removeNurseAssignmentFromState(n2ID, s, input.findRoomIndex(r));
                }

                n1.workingShifts = s2;
                n1.workingRooms = r2;
                n2.workingShifts = s1;
                n2.workingRooms = r1;

                for (int i = 0; i < n1.workingShifts.size(); i++) {
                    int s = n1.workingShifts.get(i);
                    for (String r : n1.workingRooms.get(i))
                        addNurseAssignmentToState(n1ID, s, input.findRoomIndex(r));
                }
                for (int i = 0; i < n2.workingShifts.size(); i++) {
                    int s = n2.workingShifts.get(i);
                    for (String r : n2.workingRooms.get(i))
                        addNurseAssignmentToState(n2ID, s, input.findRoomIndex(r));
                }
            }

            @Override
            void undo() {
                for (int i = 0; i < n1.workingShifts.size(); i++) {
                    int s = n1.workingShifts.get(i);
                    for (String r : n1.workingRooms.get(i))
                        removeNurseAssignmentFromState(n1ID, s, input.findRoomIndex(r));
                }
                for (int i = 0; i < n2.workingShifts.size(); i++) {
                    int s = n2.workingShifts.get(i);
                    for (String r : n2.workingRooms.get(i))
                        removeNurseAssignmentFromState(n2ID, s, input.findRoomIndex(r));
                }

                n1.workingShifts = s1;
                n1.workingRooms = r1;
                n2.workingShifts = s2;
                n2.workingRooms = r2;

                for (int i = 0; i < n1.workingShifts.size(); i++) {
                    int s = n1.workingShifts.get(i);
                    for (String r : n1.workingRooms.get(i))
                        addNurseAssignmentToState(n1ID, s, input.findRoomIndex(r));
                }
                for (int i = 0; i < n2.workingShifts.size(); i++) {
                    int s = n2.workingShifts.get(i);
                    for (String r : n2.workingRooms.get(i))
                        addNurseAssignmentToState(n2ID, s, input.findRoomIndex(r));
                }
            }
        }
    }
}