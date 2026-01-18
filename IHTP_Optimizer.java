/**
 * IHTP_Optimizer - Simple Wrapper for PA-ILS + AT-ILS
 * 
 * This is a simple wrapper that calls IHTP_Solution.solve() and
 * IHTP_ATILS.runATILS() It does NOT modify the logic of PA-ILS or AT-ILS
 * algorithms.
 * 
 * Usage: java IHTP_Optimizer <input.json> <time_limit_min> <pails_log.csv>
 * <atils_log.csv> <output.json>
 */
public class IHTP_Optimizer {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println(
                    "Usage: java IHTP_Optimizer <input.json> <time_limit_min> <pails_log.csv> <atils_log.csv> <output.json>");
            System.exit(1);
        }

        String inputFile = args[0];
        int timeLimitMin = Integer.parseInt(args[1]);
        String pailsLogCsv = args[2];
        String atilsLogCsv = args[3];
        String outputJson = args[4];

        System.out.println("=================================================");
        System.out.println("IHTP Optimizer - PA-ILS + AT-ILS");
        System.out.println("=================================================");
        System.out.println("Input file: " + inputFile);
        System.out.println("Time limit: " + timeLimitMin + " minutes");
        System.out.println("PA-ILS log: " + pailsLogCsv);
        System.out.println("AT-ILS log: " + atilsLogCsv);
        System.out.println("Output file: " + outputJson);
        System.out.println("=================================================");
        System.out.flush();

        // Track total time
        long totalStartTime = System.currentTimeMillis();
        long totalTimeLimitMs = timeLimitMin * 60 * 1000L;

        // Create temp file for PA-ILS solution (will be used by AT-ILS)
        String tempFeasibleSolution = outputJson.replace(".json", "_feasible_temp.json");

        // PHASE 1: Find Feasible Solution (Hard Violations = 0) using IHTP_Solution
        System.out.println("\n[Phase 1] Constructing Feasible Solution (PA-ILS)...");
        System.out.flush();
        System.out.println("          Time limit: " + timeLimitMin + " minutes (" + (timeLimitMin * 60) + " seconds)");
        System.out.flush();
        System.out.println("          PA-ILS log file: " + pailsLogCsv);
        System.out.flush();

        long phase1StartTime = System.currentTimeMillis();
        boolean foundFeasible = IHTP_Solution.solve(inputFile, timeLimitMin, pailsLogCsv, tempFeasibleSolution);
        long phase1ElapsedMs = System.currentTimeMillis() - phase1StartTime;
        long phase1ElapsedSec = phase1ElapsedMs / 1000;
        long phase1ElapsedMin = phase1ElapsedSec / 60;
        long phase1ElapsedSecRem = phase1ElapsedSec % 60;

        if (foundFeasible) {
            System.out.println("\n[Phase 1] SUCCESS: Feasible solution found.");
            System.out.flush();
            System.out.println("          PA-ILS runtime: " + phase1ElapsedMin + "m " + phase1ElapsedSecRem + "s ("
                    + phase1ElapsedSec + " seconds)");
            System.out.flush();

            // Calculate remaining time for AT-ILS
            long elapsedMs = System.currentTimeMillis() - totalStartTime;
            long remainingMs = totalTimeLimitMs - elapsedMs;
            long remainingSec = remainingMs / 1000;
            long remainingMin = remainingSec / 60;
            long remainingSecRem = remainingSec % 60;

            if (remainingMs > 0) {
                System.out.println("          Remaining time for AT-ILS: " + remainingMin + "m " + remainingSecRem
                        + "s (" + remainingSec + " seconds)");
                System.out.flush();
                System.out.println("          Transitioning to AT-ILS (Adaptive Threshold Iterated Local Search).");
                System.out.flush();

                // PHASE 2: Optimize Soft Constraints using IHTP_ATILS Logic
                System.out.println("\n[Phase 2] Running AT-ILS (Adaptive Threshold Iterated Local Search)...");
                System.out.flush();
                System.out.println("          AT-ILS log file: " + atilsLogCsv);
                System.out.flush();

                // FIXED: Use remainingMs directly to avoid rounding down (preserves full time)
                // Minimum 1 minute to ensure AT-ILS has time to optimize
                long remainingMsForATILS = Math.max(60000, remainingMs); // Minimum 1 minute in ms
                long remainingSecForATILS = remainingMsForATILS / 1000;
                long remainingMinForATILS = remainingSecForATILS / 60;
                long remainingSecRemForATILS = remainingSecForATILS % 60;
                System.out.println("          AT-ILS time limit: " + remainingMinForATILS + "m "
                        + remainingSecRemForATILS + "s (" + remainingSecForATILS + " seconds)");
                System.out.flush();

                // Use the feasible solution from PA-ILS (already saved to tempFeasibleSolution)
                // FIXED: Pass milliseconds directly to avoid precision loss from minute
                // conversion
                // Convert remainingMs to minutes for runATILS (which accepts int minutes)
                int remainingTimeMin = Math.max(1, (int) (remainingMsForATILS / (60 * 1000L)));
                IHTP_ATILS.runATILS(inputFile, tempFeasibleSolution, remainingTimeMin, atilsLogCsv, outputJson);

                long phase2ElapsedMs = System.currentTimeMillis() - phase1StartTime - phase1ElapsedMs;
                long phase2ElapsedSec = phase2ElapsedMs / 1000;
                long phase2ElapsedMin = phase2ElapsedSec / 60;
                long phase2ElapsedSecRem = phase2ElapsedSec % 60;
                System.out.println("          AT-ILS runtime: " + phase2ElapsedMin + "m " + phase2ElapsedSecRem + "s ("
                        + phase2ElapsedSec + " seconds)");
                System.out.flush();
            } else {
                System.out.println("          WARNING: No time remaining for AT-ILS optimization.");
                System.out.flush();
                System.out.println("          Using PA-ILS solution as final output.");
                System.out.flush();
            }
        } else {
            System.err.println("\n[Phase 1] FAILED: Could not find a 0-violation solution within time limit.");
            System.err.flush();
            System.err.println("          Using best PA-ILS solution found (may have hard violations).");
            System.err.flush();
        }

        System.out.println("\n[Phase 2] Optimization Complete.");
        System.out.flush();

        long totalElapsed = System.currentTimeMillis() - totalStartTime;
        long totalElapsedSec = totalElapsed / 1000;
        long totalElapsedMin = totalElapsedSec / 60;
        long totalElapsedSecRem = totalElapsedSec % 60;
        System.out.println("Total runtime: " + totalElapsedMin + "m " + totalElapsedSecRem + "s (" + totalElapsedSec
                + " seconds)");
        System.out.flush();
    }
}
