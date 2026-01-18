import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class IHTP_Analyzer {

    private static double safeAverage(List<Integer> data) {
        if (data.isEmpty())
            return 0.0;
        return data.stream().mapToDouble(Integer::doubleValue).average().orElse(0.0);
    }

    private static double safeMedian(List<Integer> data) {
        if (data.isEmpty())
            return 0.0;
        Collections.sort(data);
        int n = data.size();
        if (n % 2 == 1)
            return data.get(n / 2);
        return (data.get(n / 2 - 1) + data.get(n / 2)) / 2.0;
    }

    private static String fmt(double val) {
        return String.format("%.2f", val);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java IHTP_Analyzer <instances_directory>");
            return;
        }

        File folder = new File(args[0]);
        // Filter file i01.json s/d iXX.json
        File[] listOfFiles = folder.listFiles((dir, name) -> name.matches("i\\d+\\.json"));

        if (listOfFiles == null || listOfFiles.length == 0) {
            System.out.println("No JSON files found in " + args[0]);
            return;
        }

        Arrays.sort(listOfFiles);

        // Header diperbarui: Ada kolom Beds untuk kapasitas total
        System.out.println("Instance | Days | Rooms | Beds | Nurs | Pats | Mand | Surg | OT  | Occu | P/Bed Ratio");
        System.out.println("-----------------------------------------------------------------------------------------");

        List<String> detailLines = new ArrayList<>();

        for (File file : listOfFiles) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
                JSONObject obj = new JSONObject(content);

                int days = obj.optInt("days", 0);

                JSONArray shiftTypesArr = obj.optJSONArray("shift_types");
                List<String> shiftTypes = new ArrayList<>();
                if (shiftTypesArr != null) {
                    for (int i = 0; i < shiftTypesArr.length(); i++) {
                        shiftTypes.add(shiftTypesArr.optString(i, ""));
                    }
                }
                int shiftCount = shiftTypes.isEmpty() ? 3 : shiftTypes.size();

                // --- 1. Rooms & Total Capacity (Beds) ---
                int numRooms = 0;
                int totalBeds = 0;
                if (obj.has("rooms")) {
                    JSONArray roomsArr = obj.getJSONArray("rooms");
                    numRooms = roomsArr.length();
                    for (int i = 0; i < numRooms; i++) {
                        totalBeds += roomsArr.getJSONObject(i).optInt("capacity", 1);
                    }
                }

                // --- 2. Nurses ---
                int numNurses = obj.has("nurses") ? obj.getJSONArray("nurses").length() : 0;
                Map<Integer, Integer> supplySkill = new HashMap<>();
                double[][] nurseCapacity = new double[days][shiftCount];
                if (obj.has("nurses")) {
                    JSONArray nurses = obj.getJSONArray("nurses");
                    for (int i = 0; i < nurses.length(); i++) {
                        JSONObject nurse = nurses.getJSONObject(i);
                        int skill = nurse.optInt("skill_level", 0);
                        supplySkill.put(skill, supplySkill.getOrDefault(skill, 0) + 1);

                        JSONArray shifts = nurse.optJSONArray("working_shifts");
                        if (shifts != null) {
                            for (int j = 0; j < shifts.length(); j++) {
                                JSONObject ws = shifts.getJSONObject(j);
                                int day = ws.optInt("day", -1);
                                String shiftName = ws.optString("shift", "");
                                int shiftIdx = shiftTypes.indexOf(shiftName);
                                if (shiftIdx < 0)
                                    shiftIdx = ws.optInt("shift_index", -1);
                                int cap = ws.optInt("max_load", 0);
                                if (day >= 0 && day < days && shiftIdx >= 0 && shiftIdx < shiftCount) {
                                    nurseCapacity[day][shiftIdx] += cap;
                                }
                            }
                        }
                    }
                }

                // --- 3. Patients & Mandatory ---
                int numPatients = 0;
                int mandatoryCount = 0;
                List<Integer> patientLos = new ArrayList<>();
                List<Integer> patientRelease = new ArrayList<>();
                List<Integer> patientDue = new ArrayList<>();
                List<Integer> backlogWindow = new ArrayList<>();
                Map<Integer, Integer> requiredSkill = new HashMap<>();
                double[] patientWorkloadByShift = new double[shiftCount];
                double totalPatientWorkload = 0.0;
                Map<String, Integer> surgeonDuration = new HashMap<>();
                double totalSurgeryDuration = 0.0;
                if (obj.has("patients")) {
                    JSONArray patients = obj.getJSONArray("patients");
                    numPatients = patients.length();
                    for (int i = 0; i < numPatients; i++) {
                        JSONObject p = patients.getJSONObject(i);
                        if (p.optBoolean("mandatory", false))
                            mandatoryCount++;
                        int los = p.optInt("length_of_stay", 0);
                        patientLos.add(los);
                        int rel = p.optInt("surgery_release_day", 0);
                        int due = p.optInt("surgery_due_day", rel);
                        patientRelease.add(rel);
                        patientDue.add(due);
                        backlogWindow.add(Math.max(0, due - rel));

                        JSONArray workloadArr = p.optJSONArray("workload_produced");
                        if (workloadArr != null) {
                            for (int k = 0; k < workloadArr.length(); k++) {
                                int shiftIdx = shiftCount == 0 ? 0 : k % shiftCount;
                                double val = workloadArr.optDouble(k, 0.0);
                                if (shiftIdx >= 0 && shiftIdx < shiftCount) {
                                    patientWorkloadByShift[shiftIdx] += val;
                                }
                                totalPatientWorkload += val;
                            }
                        }

                        JSONArray skillReq = p.optJSONArray("skill_level_required");
                        if (skillReq != null) {
                            for (int k = 0; k < skillReq.length(); k++) {
                                int lvl = skillReq.optInt(k, 0);
                                requiredSkill.put(lvl, requiredSkill.getOrDefault(lvl, 0) + 1);
                            }
                        }

                        String surgeon = p.optString("surgeon_id", "");
                        int duration = p.optInt("surgery_duration", 0);
                        totalSurgeryDuration += duration;
                        if (!surgeon.isEmpty()) {
                            surgeonDuration.put(surgeon, surgeonDuration.getOrDefault(surgeon, 0) + duration);
                        }
                    }
                }

                // --- 4. Surgeons ---
                int numSurgeons = obj.has("surgeons") ? obj.getJSONArray("surgeons").length() : 0;
                double[] otCapacityByDay = new double[days];
                if (obj.has("surgeons")) {
                    JSONArray surgeons = obj.getJSONArray("surgeons");
                    for (int i = 0; i < surgeons.length(); i++) {
                        JSONObject s = surgeons.getJSONObject(i);
                        JSONArray caps = s.optJSONArray("max_surgery_time");
                        if (caps != null) {
                            for (int d = 0; d < Math.min(days, caps.length()); d++) {
                                otCapacityByDay[d] += caps.optDouble(d, 0.0);
                            }
                        }
                    }
                }

                // --- 5. Operating Theaters ---
                int numTheaters = obj.has("operating_theaters") ? obj.getJSONArray("operating_theaters").length() : 0;
                if (obj.has("operating_theaters")) {
                    JSONArray ots = obj.getJSONArray("operating_theaters");
                    for (int i = 0; i < ots.length(); i++) {
                        JSONObject ot = ots.getJSONObject(i);
                        JSONArray avail = ot.optJSONArray("availability");
                        if (avail != null) {
                            for (int d = 0; d < Math.min(days, avail.length()); d++) {
                                otCapacityByDay[d] += avail.optDouble(d, 0.0);
                            }
                        }
                    }
                }

                // --- 6. Occupants ---
                int numOccupants = obj.has("occupants") ? obj.getJSONArray("occupants").length() : 0;
                double[][] occupantWorkload = new double[days][shiftCount];
                List<Integer> occupantLos = new ArrayList<>();
                if (obj.has("occupants")) {
                    JSONArray occ = obj.getJSONArray("occupants");
                    for (int i = 0; i < occ.length(); i++) {
                        JSONObject o = occ.getJSONObject(i);
                        int los = o.optInt("length_of_stay", 0);
                        occupantLos.add(los);
                        JSONArray workloadArr = o.optJSONArray("workload_produced");
                        if (workloadArr != null) {
                            for (int k = 0; k < workloadArr.length(); k++) {
                                int shiftIdx = shiftCount == 0 ? 0 : k % shiftCount;
                                int day = shiftCount == 0 ? 0 : k / shiftCount;
                                double val = workloadArr.optDouble(k, 0.0);
                                if (day >= 0 && day < days && shiftIdx >= 0 && shiftIdx < shiftCount) {
                                    occupantWorkload[day][shiftIdx] += val;
                                }
                            }
                        }

                        JSONArray skillReq = o.optJSONArray("skill_level_required");
                        if (skillReq != null) {
                            for (int k = 0; k < skillReq.length(); k++) {
                                int lvl = skillReq.optInt(k, 0);
                                requiredSkill.put(lvl, requiredSkill.getOrDefault(lvl, 0) + 1);
                            }
                        }
                    }
                }

                // --- 7. Ratio Calculation ---
                double loadRatio = (totalBeds > 0 && days > 0) ? (double) numPatients / (totalBeds * days) : 0;

                System.out.printf("%-8s | %-4d | %-5d | %-4d | %-4d | %-4d | %-4d | %-4d | %-3d | %-4d | %.2f%n",
                        file.getName().replace(".json", ""), days, numRooms, totalBeds, numNurses, numPatients,
                        mandatoryCount, numSurgeons, numTheaters, numOccupants, loadRatio);

                // --- Detail Metrics ---
                double totalBedDays = (double) totalBeds * days;
                double occupantsBedDays = occupantLos.stream().mapToDouble(Integer::doubleValue).sum();
                double patientsBedDays = patientLos.stream().mapToDouble(Integer::doubleValue).sum();

                double capacitySum = 0.0;
                double demandOccSum = 0.0;
                double minGap = Double.POSITIVE_INFINITY;
                double maxGap = Double.NEGATIVE_INFINITY;
                double gapAcc = 0.0;
                int gapCount = 0;
                for (int d = 0; d < days; d++) {
                    for (int s = 0; s < shiftCount; s++) {
                        double cap = nurseCapacity[d][s];
                        double dem = occupantWorkload[d][s];
                        capacitySum += cap;
                        demandOccSum += dem;
                        double gap = cap - dem;
                        minGap = Math.min(minGap, gap);
                        maxGap = Math.max(maxGap, gap);
                        gapAcc += gap;
                        gapCount++;
                    }
                }
                double avgGap = gapCount > 0 ? gapAcc / gapCount : 0.0;
                if (minGap == Double.POSITIVE_INFINITY)
                    minGap = 0.0;
                if (maxGap == Double.NEGATIVE_INFINITY)
                    maxGap = 0.0;

                double totalOtCap = 0.0;
                double otMin = Double.POSITIVE_INFINITY;
                double otMax = Double.NEGATIVE_INFINITY;
                for (double v : otCapacityByDay) {
                    totalOtCap += v;
                    otMin = Math.min(otMin, v);
                    otMax = Math.max(otMax, v);
                }
                if (otMin == Double.POSITIVE_INFINITY)
                    otMin = 0.0;
                if (otMax == Double.NEGATIVE_INFINITY)
                    otMax = 0.0;

                double patientsPerNurse = numNurses > 0 ? (double) numPatients / numNurses : 0.0;
                double patientsPerRoom = numRooms > 0 ? (double) numPatients / numRooms : 0.0;
                double patientsPerBedPerDay = totalBedDays > 0 ? (double) numPatients / totalBedDays : 0.0;

                double optionalCount = numPatients - mandatoryCount;
                double optionalLosSum = patientLos.stream().mapToDouble(Integer::doubleValue).sum();
                double freeBedDays = Math.max(0.0, totalBedDays - occupantsBedDays);
                double optionalRiskDays = Math.max(0.0, optionalLosSum - freeBedDays);

                double medianLosPatients = safeMedian(new ArrayList<>(patientLos));
                double avgLosPatients = safeAverage(new ArrayList<>(patientLos));
                double medianLosOccupants = safeMedian(new ArrayList<>(occupantLos));
                double avgLosOccupants = safeAverage(new ArrayList<>(occupantLos));

                // Use totalPatientWorkload to compute combined workload and gap
                double demandPatSum = totalPatientWorkload;
                double demandTotal = demandOccSum + demandPatSum;
                double gapTotal = capacitySum - demandTotal;

                int maxRequiredSkill = requiredSkill.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
                int maxSupplySkill = supplySkill.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

                detailLines.add("--- " + file.getName().replace(".json", "") + " ---");
                detailLines.add("Beds:" + totalBeds + " Rooms:" + numRooms + " Days:" + days + " Nurses:" + numNurses
                        + " Patients:" + numPatients + " Mand:" + mandatoryCount + " Occu:" + numOccupants);
                detailLines.add("Patients/Nurse:" + fmt(patientsPerNurse) + " Patients/Room:" + fmt(patientsPerRoom)
                        + " Patients/Bed/Day:" + fmt(patientsPerBedPerDay));
                detailLines.add("LOS Patients avg/med:" + fmt(avgLosPatients) + "/" + fmt(medianLosPatients)
                        + " | LOS Occ avg/med:" + fmt(avgLosOccupants) + "/" + fmt(medianLosOccupants));
                detailLines.add("Bed-days: total=" + fmt(totalBedDays) + " occupants=" + fmt(occupantsBedDays)
                        + " patients(potential)=" + fmt(patientsBedDays));
                detailLines.add("Workload vs capacity (occupants only): demand=" + fmt(demandOccSum) + " cap="
                        + fmt(capacitySum) + " gap min/avg/max=" + fmt(minGap) + "/" + fmt(avgGap) + "/" + fmt(maxGap));
                detailLines.add("Workload totals: occupants=" + fmt(demandOccSum) + " patients=" + fmt(demandPatSum)
                    + " total=" + fmt(demandTotal) + " capacity=" + fmt(capacitySum) + " total gap=" + fmt(gapTotal));
                detailLines.add("Skill supply:" + supplySkill + " | Skill demand (occ+pat):" + requiredSkill
                        + " | maxReq=" + maxRequiredSkill + " maxSup=" + maxSupplySkill);
                detailLines.add("Patient workload by shift:"
                        + Arrays.toString(Arrays.stream(patientWorkloadByShift).mapToObj(v -> fmt(v)).toArray()));
                detailLines.add("OT capacity total=" + fmt(totalOtCap) + " min/day=" + fmt(otMin) + " max/day="
                        + fmt(otMax) + " | Surgery total duration=" + fmt(totalSurgeryDuration));
                detailLines.add("Surgeon load map:" + surgeonDuration);
                if (!patientRelease.isEmpty()) {
                    detailLines.add("Release day avg=" + fmt(safeAverage(new ArrayList<>(patientRelease))) + " due avg="
                            + fmt(safeAverage(new ArrayList<>(patientDue))) + " window avg="
                            + fmt(safeAverage(new ArrayList<>(backlogWindow))));
                }
                detailLines.add("Optional patients=" + fmt(optionalCount) + " optional LOS sum=" + fmt(optionalLosSum)
                        + " free bed-days=" + fmt(freeBedDays) + " risk days (unalloc est)=" + fmt(optionalRiskDays));
                detailLines.add("----------------------------------------------");

            } catch (Exception e) {
                System.out.println("Error reading " + file.getName() + ": " + e.getMessage());
            }
        }

        System.out.println();
        System.out.println("Detail Metrics per Instance:");
        for (String line : detailLines) {
            System.out.println(line);
        }
    }
}