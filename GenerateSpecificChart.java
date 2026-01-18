import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

public class GenerateSpecificChart {

    public static void main(String[] args) {
        // Configuration
        String logPath = "/Users/dzakyrifai/Downloads/coding/ta-dzaky/TimeTabling_Optimization_NPA_PRA/violation_log_one_run/logs_i24_ATILS_1.csv";
        String outputPath = "/Users/dzakyrifai/Downloads/coding/ta-dzaky/TimeTabling_Optimization_NPA_PRA/charts_one_run/chart_i24_ATILS_1_ATILS_new.png";

        System.out.println("Processing Log: " + logPath);
        System.out.println("Target Output: " + outputPath);

        // Instantiate Panel
        LiveChartPanel chartPanel = new LiveChartPanel();

        // Larger size for thesis clarity (1200x600)
        chartPanel.setSize(1200, 600);

        // Load Data (using logic for AT-ILS / Soft Cost)
        chartPanel.loadMode2Log(logPath);

        // Save Image
        chartPanel.saveImage(new File(outputPath));

        System.out.println("Done.");
    }

    // Copied from IHTP_Launcher_V2.java
    static class LiveChartPanel extends JPanel {
        private List<Integer> iterations1 = new ArrayList<>(); // PA-ILS iterations
        private List<Double> values1 = new ArrayList<>(); // Hard violations
        private List<Integer> iterations2 = new ArrayList<>(); // AT-ILS iterations
        private List<Double> values2 = new ArrayList<>(); // Soft costs (current)
        private List<Double> bestCosts = new ArrayList<>(); // Best cost so far
        private double minVal = Double.MAX_VALUE;
        private double maxVal = 0;
        private String chartTitle = "Optimization Progress";

        private Color lineColor1 = new Color(231, 76, 60); // Red for hard violations
        @SuppressWarnings("unused")
        private Color lineColor2 = new Color(52, 152, 219); // Blue for soft cost
        private boolean dualMode = false; // True for Mode 1 (PA-ILS + AT-ILS)

        public LiveChartPanel() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(Color.GRAY));
        }

        public void reset() {
            iterations1.clear();
            values1.clear();
            iterations2.clear();
            values2.clear();
            bestCosts.clear();
            minVal = Double.MAX_VALUE;
            maxVal = 0;
            repaint();
        }

        public void setDualMode(boolean dual) {
            this.dualMode = dual;
            if (dual) {
                chartTitle = "Mode 1: PA-ILS (Hard) → AT-ILS (Soft)";
            } else {
                chartTitle = "Mode 2: Soft Cost Optimization";
            }
        }

        // Load log for Mode 1 (PA-ILS UNIFIED format:
        // iteration,time_ms,hard_violations,soft_cost,best_hard,best_soft,status)
        public void loadMode1Log(String csvPath) {
            iterations1.clear();
            values1.clear();
            minVal = Double.MAX_VALUE;
            maxVal = 0;

            try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
                String line;
                boolean header = true;

                while ((line = br.readLine()) != null) {
                    if (header) {
                        header = false;
                        continue;
                    }

                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        int iter = Integer.parseInt(parts[0].trim());
                        // Unified format: col 2 = hard_violations (for PA-ILS chart)
                        double hardViolations = Double.parseDouble(parts[2].trim());

                        iterations1.add(iter);
                        values1.add(hardViolations);

                        // Update min/max tracking
                        if (hardViolations > maxVal)
                            maxVal = hardViolations;
                        if (hardViolations < minVal)
                            minVal = hardViolations;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            // Handle edge case: all values are 0 or minVal never set
            if (minVal == Double.MAX_VALUE)
                minVal = 0;
            if (maxVal <= 0)
                maxVal = 10; // Default range when no violations
            repaint();
        }

        // Load log for Mode 2 (AT-ILS UNIFIED format:
        // iteration,time_ms,hard_violations,soft_cost,best_hard,best_soft,status)
        public void loadMode2Log(String csvPath) {
            iterations2.clear();
            values2.clear();
            bestCosts.clear();
            minVal = Double.MAX_VALUE;
            maxVal = 0;

            try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
                String line;
                boolean header = true;
                while ((line = br.readLine()) != null) {
                    if (header) {
                        header = false;
                        continue;
                    }
                    String[] parts = line.split(",");
                    if (parts.length >= 6) {
                        int iter = Integer.parseInt(parts[0].trim());
                        // Unified format: col 3 = soft_cost (current), col 5 = best_soft
                        double softCost = Double.parseDouble(parts[3].trim());
                        double bestSoft = Double.parseDouble(parts[5].trim());

                        iterations2.add(iter);
                        values2.add(softCost);
                        bestCosts.add(bestSoft);

                        // Track overall min/max for scaling
                        if (softCost > maxVal)
                            maxVal = softCost;
                        if (softCost < minVal)
                            minVal = softCost;
                        if (bestSoft < minVal)
                            minVal = bestSoft;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            // Handle edge case: minVal never set
            if (minVal == Double.MAX_VALUE)
                minVal = 0;
            if (maxVal <= 0)
                maxVal = 1000; // Default range
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int padding = 70;
            int chartW = w - padding * 2;
            int chartH = h - padding * 2;

            // Draw title
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString(chartTitle, w / 2 - 100, 20);

            // Draw axes
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1));
            g2.drawLine(padding, padding, padding, h - padding);
            g2.drawLine(padding, h - padding, w - padding, h - padding);

            boolean hasData1 = !iterations1.isEmpty() && !values1.isEmpty();
            boolean hasData2 = !iterations2.isEmpty() && !values2.isEmpty();

            if (!hasData1 && !hasData2) {
                g2.setColor(Color.GRAY);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.drawString("Waiting for data... Run optimization to see chart.", w / 2 - 150, h / 2);
                return;
            }

            // Calculate range with safety checks
            double range = maxVal - minVal;
            if (range <= 0)
                range = Math.max(maxVal, 10); // Fallback range
            if (Double.isInfinite(range) || Double.isNaN(range))
                range = 10;
            double displayMin = Math.max(0, minVal - range * 0.1);
            double displayMax = maxVal + range * 0.1;
            double displayRange = displayMax - displayMin;
            if (displayRange <= 0)
                displayRange = 10; // Safety fallback

            // Draw Y axis labels
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (int i = 0; i <= 5; i++) {
                int y = h - padding - (i * chartH / 5);
                double val = displayMin + (displayRange * i / 5);
                g2.setColor(new Color(230, 230, 230));
                g2.drawLine(padding + 1, y, w - padding, y);
                g2.setColor(Color.BLACK);
                g2.drawString(String.format("%.0f", val), 5, y + 4);
            }

            // Draw legend
            int legendX = w - padding - 200;
            int legendY = padding + 10;
            if (hasData1) {
                g2.setColor(lineColor1);
                g2.fillRect(legendX, legendY, 15, 10);
                g2.setColor(Color.BLACK);
                g2.drawString("Hard Violations", legendX + 20, legendY + 10);
                legendY += 18;
            }
            if (hasData2) {
                // Current cost (thin blue)
                g2.setColor(new Color(52, 152, 219, 150)); // Semi-transparent
                g2.fillRect(legendX, legendY, 15, 10);
                g2.setColor(Color.BLACK);
                g2.drawString("Current Soft Cost", legendX + 20, legendY + 10);
                legendY += 18;
                // Best cost (bold red)
                g2.setColor(new Color(231, 76, 60));
                g2.setStroke(new BasicStroke(3));
                g2.drawLine(legendX, legendY + 5, legendX + 15, legendY + 5);
                g2.setStroke(new BasicStroke(1));
                g2.setColor(Color.BLACK);
                g2.drawString("Best Soft Cost", legendX + 20, legendY + 10);
            }

            // Find max iteration for X scale
            int maxIter = 1;
            if (hasData1)
                maxIter = Math.max(maxIter, iterations1.get(iterations1.size() - 1));
            if (hasData2) {
                int offset = dualMode && hasData1 ? iterations1.get(iterations1.size() - 1) : 0;
                maxIter = Math.max(maxIter, offset + iterations2.get(iterations2.size() - 1));
            }

            // Draw X axis labels (10 points for better resolution)
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.drawString("Iteration", w / 2 - 20, h - 10);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (int i = 0; i <= 10; i++) {
                int x = padding + (i * chartW / 10);
                int val = maxIter * i / 10;
                g2.setColor(new Color(230, 230, 230));
                g2.drawLine(x, padding, x, h - padding - 1);
                g2.setColor(Color.BLACK);
                g2.drawString(String.valueOf(val), x - 10, h - padding + 15);
            }

            // Draw PA-ILS data (hard violations from values1)
            // For separate PA-ILS panel, draw when hasData1 is true
            // For combined dual mode, draw when dualMode && hasData1
            if (hasData1) {
                g2.setColor(lineColor1);
                g2.setStroke(new BasicStroke(2));
                for (int i = 1; i < iterations1.size(); i++) {
                    int x1 = padding + (int) ((iterations1.get(i - 1) * (double) chartW) / maxIter);
                    int x2 = padding + (int) ((iterations1.get(i) * (double) chartW) / maxIter);
                    int y1 = h - padding - (int) (((values1.get(i - 1) - displayMin) / displayRange) * chartH);
                    int y2 = h - padding - (int) (((values1.get(i) - displayMin) / displayRange) * chartH);
                    y1 = Math.max(padding, Math.min(h - padding, y1));
                    y2 = Math.max(padding, Math.min(h - padding, y2));
                    g2.drawLine(x1, y1, x2, y2);
                }
            }

            // Draw AT-ILS data (dual-line: current + best cost)
            if (hasData2) {
                int offset = dualMode && hasData1 ? iterations1.get(iterations1.size() - 1) : 0;

                // Draw current cost (thin blue line with transparency)
                g2.setColor(new Color(52, 152, 219, 150));
                g2.setStroke(new BasicStroke(1.5f));
                for (int i = 1; i < iterations2.size(); i++) {
                    int x1 = padding + (int) (((offset + iterations2.get(i - 1)) * (double) chartW) / maxIter);
                    int x2 = padding + (int) (((offset + iterations2.get(i)) * (double) chartW) / maxIter);
                    int y1 = h - padding - (int) (((values2.get(i - 1) - displayMin) / displayRange) * chartH);
                    int y2 = h - padding - (int) (((values2.get(i) - displayMin) / displayRange) * chartH);
                    y1 = Math.max(padding, Math.min(h - padding, y1));
                    y2 = Math.max(padding, Math.min(h - padding, y2));
                    g2.drawLine(x1, y1, x2, y2);
                }

                // Draw best cost (bold red line)
                g2.setColor(new Color(231, 76, 60));
                g2.setStroke(new BasicStroke(3f));
                for (int i = 1; i < bestCosts.size(); i++) {
                    int x1 = padding + (int) (((offset + iterations2.get(i - 1)) * (double) chartW) / maxIter);
                    int x2 = padding + (int) (((offset + iterations2.get(i)) * (double) chartW) / maxIter);
                    int y1 = h - padding - (int) (((bestCosts.get(i - 1) - displayMin) / displayRange) * chartH);
                    int y2 = h - padding - (int) (((bestCosts.get(i) - displayMin) / displayRange) * chartH);
                    y1 = Math.max(padding, Math.min(h - padding, y1));
                    y2 = Math.max(padding, Math.min(h - padding, y2));
                    g2.drawLine(x1, y1, x2, y2);

                    // Add marker when best improves significantly (>1% improvement)
                    if (i > 0 && bestCosts.get(i - 1) > 0) {
                        double improvement = (bestCosts.get(i - 1) - bestCosts.get(i)) / bestCosts.get(i - 1);
                        if (improvement > 0.01) { // >1% improvement
                            g2.setColor(new Color(46, 204, 113)); // Green marker
                            g2.fillOval(x2 - 4, y2 - 4, 8, 8);
                            g2.setColor(new Color(231, 76, 60));

                            // Add annotation for large improvements (>5%)
                            if (improvement > 0.05) {
                                g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                                g2.setColor(new Color(46, 204, 113));
                                String annot = String.format("-%.1f%%", improvement * 100);
                                g2.drawString(annot, x2 + 5, y2 - 5);
                                g2.setColor(new Color(231, 76, 60));
                                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                            }
                        }
                    }
                }
            }

            // Draw current values
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            int infoY = h - 25;
            if (hasData1 && !values1.isEmpty()) {
                double minHard = values1.get(0);
                for (double v : values1)
                    if (v < minHard)
                        minHard = v;
                g2.setColor(lineColor1);
                g2.drawString("Best Hard: " + String.format("%.0f", minHard), padding + 10, infoY);
                g2.drawString("Iterations: " + values1.size(), padding + 180, infoY);
            }
            if (hasData2 && !values2.isEmpty() && !bestCosts.isEmpty()) {
                double finalCurrent = values2.get(values2.size() - 1);
                double finalBest = bestCosts.get(bestCosts.size() - 1);
                double initialBest = bestCosts.get(0);
                double totalImprovement = ((initialBest - finalBest) / initialBest) * 100;

                g2.setColor(new Color(52, 152, 219));
                g2.drawString("Current: " + String.format("%.0f", finalCurrent), padding + 10, infoY);
                g2.setColor(new Color(231, 76, 60));
                g2.drawString("Best: " + String.format("%.0f", finalBest), padding + 180, infoY);
                g2.setColor(new Color(46, 204, 113));
                g2.drawString("Total Improvement: " + String.format("%.2f%%", totalImprovement), padding + 350, infoY);
                g2.setColor(Color.BLACK);
                g2.drawString("Iterations: " + values2.size(), padding + 580, infoY);
            }
        }

        public void saveImage(File file) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                w = 800;
                h = 280; // Default size if not visible
                setSize(w, h);
            }
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            paint(g2);
            g2.dispose();
            try {
                ImageIO.write(bi, "png", file);
            } catch (IOException e) {
                System.err.println("Error saving chart: " + e.getMessage());
            }
        }
    }
}
