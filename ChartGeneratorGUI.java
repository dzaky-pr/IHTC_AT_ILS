import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class ChartGeneratorGUI extends JFrame {

    // Configuration
    private static final int CHART_WIDTH = 1200;
    private static final int CHART_HEIGHT = 800;
    private EnhancedChartPanel chartPanel;
    private JLabel statusLabel;

    // UI for file selection
    private JComboBox<String> dirCombo;
    private JComboBox<String> fileCombo;
    private JComboBox<String> phaseCombo;
    private JLabel pathLabel;

    private File projectRoot;
    private File currentLogFile;
    private String currentPhaseFilter = "All Phases";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new ChartGeneratorGUI().setVisible(true);
        });
    }

    public ChartGeneratorGUI() {
        super("Log Chart Generator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 900);
        setLocationRelativeTo(null);

        // Assume current working directory is the project root
        projectRoot = new File(".");

        initUI();
        loadDirectories();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // --- Top Control Panel ---
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Log Selection"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Directory Selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        controlPanel.add(new JLabel("Folder (violation_*):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        dirCombo = new JComboBox<>();
        dirCombo.addActionListener(e -> onDirectoryChanged());
        controlPanel.add(dirCombo, gbc);

        // Row 1: File Selection
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        controlPanel.add(new JLabel("Log File:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        fileCombo = new JComboBox<>();
        fileCombo.addActionListener(e -> onFileChanged());
        controlPanel.add(fileCombo, gbc);

        // Row 2: Full Path Display
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        controlPanel.add(new JLabel("Full Path:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        pathLabel = new JLabel("-");
        pathLabel.setForeground(Color.GRAY);
        pathLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        controlPanel.add(pathLabel, gbc);

        // Row 3: Phase filter
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        controlPanel.add(new JLabel("Phase Filter:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        phaseCombo = new JComboBox<>();
        phaseCombo.addItem("All Phases");
        phaseCombo.addActionListener(e -> onPhaseChanged());
        controlPanel.add(phaseCombo, gbc);

        // Render Button (Right side of Row 0-1)
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        JButton refreshBtn = new JButton("Render Chart");
        refreshBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        refreshBtn.addActionListener(e -> renderChart());
        controlPanel.add(refreshBtn, gbc);

        // --- Center Chart Preview (Responsive) ---
        chartPanel = new EnhancedChartPanel();
        mainPanel.add(chartPanel, BorderLayout.CENTER);

        // --- Bottom Action Panel ---
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        statusLabel = new JLabel("Ready.");
        JButton saveBtn = new JButton("Save Chart Image (High Res)");
        saveBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        saveBtn.addActionListener(e -> saveChart());

        JPanel leftStatus = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftStatus.add(statusLabel);

        actionPanel.add(saveBtn);

        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.add(leftStatus, BorderLayout.WEST);
        bottomContainer.add(actionPanel, BorderLayout.EAST);

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(bottomContainer, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void loadDirectories() {
        dirCombo.removeAllItems();
        File[] dirs = projectRoot.listFiles((dir,
                name) -> (name.startsWith("violation_") || name.startsWith("violation") || name.startsWith("atils_log"))
                        && new File(dir, name).isDirectory());

        if (dirs != null) {
            Arrays.sort(dirs, Comparator.comparing(File::getName));
            for (File d : dirs) {
                dirCombo.addItem(d.getName());
            }
        }

        if (dirCombo.getItemCount() > 0) {
            dirCombo.setSelectedIndex(0);
        } else {
            pathLabel.setText("No 'violation_*' directories found in " + projectRoot.getAbsolutePath());
        }
    }

    private void onDirectoryChanged() {
        fileCombo.removeAllItems();
        String dirName = (String) dirCombo.getSelectedItem();
        if (dirName == null)
            return;

        File dir = new File(projectRoot, dirName);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".csv"));

        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                fileCombo.addItem(f.getName());
            }
        }

        if (fileCombo.getItemCount() > 0) {
            fileCombo.setSelectedIndex(0);
        }
    }

    private void onFileChanged() {
        String dirName = (String) dirCombo.getSelectedItem();
        String fileName = (String) fileCombo.getSelectedItem();

        if (dirName != null && fileName != null) {
            File dir = new File(projectRoot, dirName);
            currentLogFile = new File(dir, fileName);
            pathLabel.setText(currentLogFile.getAbsolutePath());
            loadPhaseOptions(currentLogFile);
            renderChart();
        } else {
            currentLogFile = null;
            pathLabel.setText("-");
        }
    }

    private void loadPhaseOptions(File logFile) {
        List<String> phases = new ArrayList<>();
        phases.add("All Phases");

        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String header = br.readLine();
            if (header == null)
                return;
            String[] cols = header.split(",");
            int phaseIdx = -1;
            for (int i = 0; i < cols.length; i++) {
                if (cols[i].trim().equalsIgnoreCase("phase")) {
                    phaseIdx = i;
                    break;
                }
            }

            if (phaseIdx == -1)
                return;

            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length > phaseIdx) {
                    String phaseVal = parts[phaseIdx].trim();
                    if (!phases.contains(phaseVal)) {
                        phases.add(phaseVal);
                    }
                }
            }
        } catch (IOException ignored) {
        }

        phaseCombo.removeAllItems();
        for (String p : phases) {
            phaseCombo.addItem(p);
        }
        phaseCombo.setSelectedIndex(0);
        currentPhaseFilter = "All Phases";
    }

    private void onPhaseChanged() {
        String selected = (String) phaseCombo.getSelectedItem();
        if (selected != null) {
            currentPhaseFilter = selected;
            renderChart();
        }
    }

    private void renderChart() {
        if (currentLogFile == null || !currentLogFile.exists()) {
            statusLabel.setText("Error: No valid file selected.");
            return;
        }

        chartPanel.reset();
        chartPanel.loadLog(currentLogFile.getAbsolutePath(), currentPhaseFilter);

        statusLabel.setText("Rendered: " + currentLogFile.getName());
        chartPanel.revalidate();
        chartPanel.repaint();
    }

    private void saveChart() {
        // [PERBAIKAN FITUR SAVE]
        // 1. Pastikan folder output ada
        File outputDir = new File("./charts_one_run");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(outputDir);

        String defaultName = "chart_output.png";
        if (currentLogFile != null) {
            defaultName = "chart_" + currentLogFile.getName().replace(".csv", "") + ".png";
        }
        fc.setSelectedFile(new File(defaultName));

        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dest = fc.getSelectedFile();
            if (!dest.getName().toLowerCase().endsWith(".png")) {
                dest = new File(dest.getParentFile(), dest.getName() + ".png");
            }

            try {
                chartPanel.saveImage(dest);
                statusLabel.setText("Saved to: " + dest.getName());
                JOptionPane.showMessageDialog(this, "Chart saved successfully!\nPath: " + dest.getAbsolutePath());
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =================================================================================
    // ENHANCED CHART PANEL
    // =================================================================================
    static class EnhancedChartPanel extends JPanel {
        private List<Integer> iterations = new ArrayList<>();
        private List<Double> values = new ArrayList<>();
        private List<Double> bestCosts = new ArrayList<>();
        private List<Double> thresholds = new ArrayList<>();

        private String chartTitle = ""; // Title dikosongkan defaultnya
        private Color lineColor = new Color(52, 152, 219);
        private String yLabel = "Value";
        private String currentPhase = "All Phases";
        private double thresholdScaleFactor = 1.0;
        private String currentCsvPath = "";

        public EnhancedChartPanel() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(Color.GRAY));
        }

        public void reset() {
            iterations.clear();
            values.clear();
            bestCosts.clear();
            thresholds.clear();
            repaint();
        }

        public void loadLog(String csvPath, String phaseFilter) {
            iterations.clear();
            values.clear();
            bestCosts.clear();
            thresholds.clear();
            currentPhase = phaseFilter;
            currentCsvPath = csvPath;

            int colIndexCurrent = -1;
            int colIndexBest = -1;
            int colIndexPhase = -1;
            int colIndexThreshold = -1;

            try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
                String line = br.readLine();
                if (line == null)
                    return;

                String[] headers = line.split(",");
                for (int i = 0; i < headers.length; i++) {
                    String h = headers[i].trim().toLowerCase();
                    if (h.contains("soft_cost")) {
                        colIndexCurrent = i;
                        // [REQUEST] Title dihapus nanti saat drawing, disini label internal saja
                        chartTitle = "Soft Cost Optimization";
                        yLabel = "Soft Cost";
                        lineColor = new Color(52, 152, 219);
                    } else if (h.contains("best_soft")) {
                        colIndexBest = i;
                    } else if (h.contains("hard_violations")) {
                        if (colIndexCurrent == -1) {
                            colIndexCurrent = i;
                            chartTitle = "Hard Violations";
                            yLabel = "Violations";
                            lineColor = new Color(231, 76, 60);
                        }
                    } else if (h.contains("best_hard")) {
                        if (colIndexBest == -1) {
                            colIndexBest = i;
                        }
                    } else if (h.equals("phase")) {
                        colIndexPhase = i;
                    } else if (h.equals("threshold")) {
                        colIndexThreshold = i;
                    }
                }

                if (colIndexCurrent == -1)
                    colIndexCurrent = 3;
                if (colIndexBest == -1)
                    colIndexBest = 5;

                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length > Math.max(colIndexCurrent, colIndexBest)) {
                        if (colIndexPhase != -1 && phaseFilter != null && !"All Phases".equals(phaseFilter)) {
                            String phaseVal = parts[colIndexPhase].trim();
                            if (!phaseFilter.equals(phaseVal))
                                continue;
                        }
                        try {
                            int iter = Integer.parseInt(parts[0].trim());
                            double val = Double.parseDouble(parts[colIndexCurrent].trim());
                            double best = Double.parseDouble(parts[colIndexBest].trim());
                            double threshold = 0.0;
                            if (colIndexThreshold != -1 && parts.length > colIndexThreshold) {
                                try {
                                    threshold = Double.parseDouble(parts[colIndexThreshold].trim());
                                } catch (NumberFormatException e) {
                                    threshold = 0.0;
                                }
                            }
                            iterations.add(iter);
                            values.add(val);
                            bestCosts.add(best);
                            thresholds.add(threshold);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (!values.isEmpty() && !thresholds.isEmpty()) {
                double minVal = Double.MAX_VALUE;
                double maxVal = -Double.MAX_VALUE;

                for (Double v : values) {
                    if (v < minVal)
                        minVal = v;
                    if (v > maxVal)
                        maxVal = v;
                }
                for (Double v : bestCosts) {
                    if (v < minVal)
                        minVal = v;
                    if (v > maxVal)
                        maxVal = v;
                }

                double maxThreshold = 0;
                for (Double t : thresholds) {
                    if (t > maxThreshold)
                        maxThreshold = t;
                }

                if (maxThreshold > 0) {
                    double costRange = maxVal - minVal;
                    thresholdScaleFactor = (costRange * 0.07) / maxThreshold;
                } else {
                    thresholdScaleFactor = 1.0;
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            drawChart((Graphics2D) g, getWidth(), getHeight());
        }

        private void drawChart(Graphics2D g2, int w, int h) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (iterations.isEmpty() || values.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.drawString("No data loaded.", w / 2 - 40, h / 2);
                return;
            }

            // Fill background
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);

            int paddingLeft = 80;
            int paddingRight = 30;
            int paddingTop = 60;
            int paddingBottom = 60;

            int chartW = w - paddingLeft - paddingRight;
            int chartH = h - paddingTop - paddingBottom;

            // --- Calculate Nice Axis Scales ---
            int minIter = iterations.get(0);
            int maxIter = iterations.get(iterations.size() - 1);
            AxisScale xScale = calculateNiceScale(minIter, maxIter, 10);

            double minVal = Double.MAX_VALUE;
            double maxVal = -Double.MAX_VALUE;
            for (Double v : values) {
                if (v < minVal)
                    minVal = v;
                if (v > maxVal)
                    maxVal = v;
            }
            for (Double v : bestCosts) {
                if (v < minVal)
                    minVal = v;
                if (v > maxVal)
                    maxVal = v;
            }
            double yRange = maxVal - minVal;
            if (yRange == 0)
                yRange = 10;
            AxisScale yScale = calculateNiceScale(Math.max(0, minVal - yRange * 0.05), maxVal + yRange * 0.05, 15);

            // [REQUEST] Title DIHAPUS. Kode di bawah ini dikomentari.
            /*
             * g2.setColor(Color.BLACK);
             * g2.setFont(new Font("SansSerif", Font.BOLD, 16));
             * FontMetrics fm = g2.getFontMetrics();
             * g2.drawString(chartTitle, (w - fm.stringWidth(chartTitle)) / 2, 35);
             */

            // Draw Y-Axis Grid & Labels
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();
            g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 3 }, 0));

            for (double tik = yScale.niceMin; tik <= yScale.niceMax
                    + 0.00001 * yScale.pTick; tik += yScale.tickSpacing) {
                double normalizedY = (tik - yScale.niceMin) / (yScale.niceMax - yScale.niceMin);
                int yPixel = (h - paddingBottom) - (int) (normalizedY * chartH);

                if (yPixel < paddingTop || yPixel > h - paddingBottom)
                    continue;

                g2.setColor(new Color(220, 220, 220));
                g2.drawLine(paddingLeft, yPixel, w - paddingRight, yPixel);

                g2.setColor(Color.BLACK);
                String label = formatNumber(tik);
                g2.drawString(label, paddingLeft - fm.stringWidth(label) - 8, yPixel + 4);
            }

            // Draw X-Axis Grid & Labels
            for (double tik = xScale.niceMin; tik <= xScale.niceMax
                    + 0.00001 * xScale.pTick; tik += xScale.tickSpacing) {
                double normalizedX = (tik - xScale.niceMin) / (xScale.niceMax - xScale.niceMin);
                int xPixel = paddingLeft + (int) (normalizedX * chartW);

                if (xPixel < paddingLeft || xPixel > w - paddingRight)
                    continue;

                g2.setColor(new Color(220, 220, 220));
                g2.drawLine(xPixel, paddingTop, xPixel, h - paddingBottom);

                g2.setColor(Color.BLACK);
                String label = formatNumber(tik);
                g2.drawString(label, xPixel - fm.stringWidth(label) / 2, h - paddingBottom + 20);
            }

            // Draw Axes Lines
            g2.setStroke(new BasicStroke(2));
            g2.setColor(Color.BLACK);
            g2.drawLine(paddingLeft, paddingTop, paddingLeft, h - paddingBottom);
            g2.drawLine(paddingLeft, h - paddingBottom, w - paddingRight, h - paddingBottom);

            // Axis Titles
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2.drawString("Iteration", w / 2 - 20, h - 15);
            g2.rotate(-Math.PI / 2);
            g2.drawString(yLabel, -(h / 2 + 20), 20);
            g2.rotate(Math.PI / 2);

            // Plot Current Cost
            g2.setColor(new Color(52, 152, 219, 150));
            g2.setStroke(new BasicStroke(1.5f));

            double xRange = xScale.niceMax - xScale.niceMin;
            double yRangeScale = yScale.niceMax - yScale.niceMin;
            if (xRange == 0)
                xRange = 1;
            if (yRangeScale == 0)
                yRangeScale = 1;

            int prevX = -1;
            int prevY = -1;

            for (int i = 0; i < iterations.size(); i++) {
                double it = iterations.get(i);
                double val = values.get(i);

                int px = paddingLeft + (int) (((it - xScale.niceMin) / xRange) * chartW);
                int py = (h - paddingBottom) - (int) (((val - yScale.niceMin) / yRangeScale) * chartH);

                px = Math.max(paddingLeft, Math.min(w - paddingRight, px));
                py = Math.max(paddingTop, Math.min(h - paddingBottom, py));

                if (i > 0) {
                    g2.drawLine(prevX, prevY, px, py);
                }
                prevX = px;
                prevY = py;
            }

            // Plot Nilai Ambang Batas
            if (!values.isEmpty() && !thresholds.isEmpty()) {
                g2.setColor(new Color(255, 165, 0));
                g2.setStroke(new BasicStroke(4.0f));

                List<Integer> envelopeIndices = new ArrayList<>();
                List<Double> envelopeValues = new ArrayList<>();

                int firstThresholdIdx = -1;
                double firstThreshold = 0;
                for (int i = 0; i < thresholds.size(); i++) {
                    if (thresholds.get(i) > 0.01) {
                        firstThresholdIdx = i;
                        firstThreshold = thresholds.get(i);
                        break;
                    }
                }

                if (firstThresholdIdx < 0) {
                    firstThresholdIdx = 0;
                    firstThreshold = thresholds.get(0);
                }

                int windowSize = 20;
                int windowStart = Math.max(0, firstThresholdIdx - windowSize);
                int windowEnd = Math.min(firstThresholdIdx + windowSize, values.size() - 1);

                int startIdx = firstThresholdIdx;
                double startVal = values.get(firstThresholdIdx);

                for (int j = windowStart; j <= windowEnd; j++) {
                    if (values.get(j) > startVal) {
                        startVal = values.get(j);
                        startIdx = j;
                    }
                }

                envelopeIndices.add(startIdx);
                envelopeValues.add(startVal);

                double prevThreshold = firstThreshold;
                double thresholdChangeTolerance = 0.05;

                for (int i = firstThresholdIdx + 1; i < thresholds.size(); i++) {
                    double currentThreshold = thresholds.get(i);
                    double change = Math.abs(currentThreshold - prevThreshold) / Math.max(prevThreshold, 1.0);

                    if (change > thresholdChangeTolerance) {
                        windowStart = Math.max(envelopeIndices.get(envelopeIndices.size() - 1), i - windowSize);
                        windowEnd = Math.min(i + windowSize, values.size() - 1);

                        int peakIdx = i;
                        double peakVal = values.get(i);

                        for (int j = windowStart; j <= windowEnd; j++) {
                            if (values.get(j) > peakVal) {
                                peakVal = values.get(j);
                                peakIdx = j;
                            }
                        }

                        double lastVal = envelopeValues.get(envelopeValues.size() - 1);
                        if (peakVal >= lastVal * 0.98) {
                            envelopeIndices.add(peakIdx);
                            envelopeValues.add(peakVal);
                        }

                        prevThreshold = currentThreshold;
                    }
                }

                envelopeIndices.add(values.size() - 1);
                envelopeValues.add(bestCosts.get(bestCosts.size() - 1));

                boolean hasChanges = true;
                int maxIterations = 50;
                int iteration = 0;

                while (hasChanges && iteration < maxIterations) {
                    hasChanges = false;
                    iteration++;

                    for (int segIdx = 0; segIdx < envelopeIndices.size() - 1; segIdx++) {
                        int idx0 = envelopeIndices.get(segIdx);
                        double val0 = envelopeValues.get(segIdx);
                        int idx1 = envelopeIndices.get(segIdx + 1);
                        double val1 = envelopeValues.get(segIdx + 1);

                        int violatingIdx = -1;
                        double maxViolation = 0;

                        for (int i = idx0 + 1; i < idx1; i++) {
                            double expectedY = val0 + (val1 - val0) * (i - idx0) / (idx1 - idx0);
                            double violation = values.get(i) - expectedY;

                            if (violation > maxViolation) {
                                maxViolation = violation;
                                violatingIdx = i;
                            }
                        }

                        if (violatingIdx >= 0 && maxViolation > 0.01) {
                            envelopeIndices.add(segIdx + 1, violatingIdx);
                            envelopeValues.add(segIdx + 1, values.get(violatingIdx));
                            hasChanges = true;
                            break;
                        }
                    }
                }

                prevX = -1;
                prevY = -1;

                for (int i = 0; i < envelopeIndices.size(); i++) {
                    int idx = envelopeIndices.get(i);
                    double it = iterations.get(idx);
                    double val = envelopeValues.get(i);

                    int px = paddingLeft + (int) (((it - xScale.niceMin) / xRange) * chartW);
                    int py = (h - paddingBottom) - (int) (((val - yScale.niceMin) / yRangeScale) * chartH);

                    px = Math.max(paddingLeft, Math.min(w - paddingRight, px));
                    py = Math.max(paddingTop, Math.min(h - paddingBottom, py));

                    if (i > 0) {
                        g2.drawLine(prevX, prevY, px, py);
                    }
                    prevX = px;
                    prevY = py;
                }
            }

            // Draw Legend
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            int legX = paddingLeft + 20;
            int legY = paddingTop + 20;

            // Current cost legend
            g2.setColor(new Color(52, 152, 219, 150));
            g2.fillRect(legX, legY, 20, 3);
            g2.setColor(Color.BLACK);
            g2.drawString("Current Cost", legX + 25, legY + 3);

            // Threshold legend
            legY += 20;
            g2.setColor(new Color(255, 165, 0));
            g2.setStroke(new BasicStroke(3.5f));
            g2.drawLine(legX, legY, legX + 20, legY);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Color.BLACK);
            g2.drawString("Nilai Ambang Batas (Threshold)", legX + 25, legY + 3);

            // [REQUEST] INFO BOX DIHAPUS. Kode statistik di bawah ini dikomentari.
            /*
             * g2.setFont(new Font("SansSerif", Font.BOLD, 11));
             * g2.setColor(new Color(0, 0, 0, 200));
             * 
             * if (!bestCosts.isEmpty() && bestCosts.size() > 1) {
             * double initialBest = bestCosts.get(0);
             * double finalBest = bestCosts.get(bestCosts.size() - 1);
             * double totalImprovement = ((initialBest - finalBest) / initialBest) * 100;
             * 
             * int acceptanceMoves = 0;
             * for (int i = 0; i < values.size(); i++) {
             * if (Math.abs(values.get(i) - bestCosts.get(i)) < 0.01) {
             * acceptanceMoves++;
             * }
             * }
             * double acceptanceRatio = (acceptanceMoves * 100.0) / values.size();
             * 
             * String info = String.format(
             * "Initial: %s | Final: %s | Improvement: %.2f%% | Acceptance: %.1f%% | Points: %d"
             * ,
             * formatNumber(initialBest), formatNumber(finalBest), totalImprovement,
             * acceptanceRatio,
             * values.size());
             * g2.drawString(info, paddingLeft + 10, h - 15);
             * } else {
             * String info = String.format("Min: %s | Max: %s | Points: %d",
             * formatNumber(minVal),
             * formatNumber(maxVal), values.size());
             * g2.drawString(info, w - paddingRight - 200, paddingTop - 10);
             * }
             */
        }

        public void saveImage(File file) {
            // [PERBAIKAN FITUR SAVE]: Menggunakan TYPE_INT_RGB agar tidak ada masalah
            // transparansi
            BufferedImage bi = new BufferedImage(CHART_WIDTH, CHART_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = bi.createGraphics();
            drawChart(g2, CHART_WIDTH, CHART_HEIGHT);
            g2.dispose();
            try {
                ImageIO.write(bi, "png", file);
            } catch (IOException e) {
                e.printStackTrace();
                // Throw supaya bisa ditangkap di GUI utama
                throw new RuntimeException(e);
            }
        }

        private String formatNumber(double d) {
            if (d == (long) d)
                return String.format("%,d", (long) d);
            else
                return String.format("%,.1f", d);
        }

        private AxisScale calculateNiceScale(double min, double max, int maxTicks) {
            double range = niceNum(max - min, false);
            double tickSpacing = niceNum(range / (maxTicks - 1), true);
            double niceMin = Math.floor(min / tickSpacing) * tickSpacing;
            double niceMax = Math.ceil(max / tickSpacing) * tickSpacing;
            return new AxisScale(niceMin, niceMax, tickSpacing, 0);
        }

        private double niceNum(double range, boolean round) {
            double exponent = Math.floor(Math.log10(range));
            double fraction = range / Math.pow(10, exponent);
            double niceFraction;

            if (round) {
                if (fraction < 1.5)
                    niceFraction = 1;
                else if (fraction < 3)
                    niceFraction = 2;
                else if (fraction < 7)
                    niceFraction = 5;
                else
                    niceFraction = 10;
            } else {
                if (fraction <= 1)
                    niceFraction = 1;
                else if (fraction <= 2)
                    niceFraction = 2;
                else if (fraction <= 5)
                    niceFraction = 5;
                else
                    niceFraction = 10;
            }

            return niceFraction * Math.pow(10, exponent);
        }

        static class AxisScale {
            double niceMin, niceMax, tickSpacing;
            double pTick;

            AxisScale(double min, double max, double spac, double p) {
                this.niceMin = min;
                this.niceMax = max;
                this.tickSpacing = spac;
                this.pTick = p;
            }
        }
    }
}