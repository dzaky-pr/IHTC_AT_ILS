import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * IHTP Launcher - Simple PA-ILS + AT-ILS Optimizer GUI
 */
public class IHTP_Launcher extends JFrame {

    private static final String TEST_DIR = "./ihtc2024_test_dataset";
    private static final String COMPETITION_DIR = "./ihtc2024_competition_instances";
    private static final String DEFAULT_JAR = "json-20250107.jar";

    // UI Components
    private JList<FileItem> instanceList;
    private JComboBox<FileItem> solutionCombo;
    private JTextField jarField, runtimeField, suffixField, bulkField;
    private JTextField solDirField, violDirField, chartDirField;
    private JTextArea consoleArea;
    private JButton runBtn, abortBtn, validateBtn;
    private ChartPanel chartPanel;
    private Timer chartTimer;

    // Runtime
    private SwingWorker<Integer, String> worker;
    private volatile Process currentProcess;
    private List<FileItem> allInstances = new ArrayList<>();
    private List<FileItem> allSolutions = new ArrayList<>();
    private String currentLogFile;

    static class FileItem {
        File file;
        String label;

        FileItem(File f, String l) {
            file = f;
            label = l;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    class ChartPanel extends JPanel {
        private List<Integer> iterations = new ArrayList<>();
        private List<Double> values = new ArrayList<>();
        private double minVal = Double.MAX_VALUE;
        private double maxVal = 0;

        public void reset() {
            iterations.clear();
            values.clear();
            minVal = Double.MAX_VALUE;
            maxVal = 0;
            repaint();
        }

        public void loadLog(String csvPath) {
            iterations.clear();
            values.clear();
            minVal = Double.MAX_VALUE;
            maxVal = 0;

            try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
                String line;
                int iter = 0;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length > 2) {
                        try {
                            double val = Double.parseDouble(parts[2].trim());
                            iterations.add(iter++);
                            values.add(val);
                            minVal = Math.min(minVal, val);
                            maxVal = Math.max(maxVal, val);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception e) {
            }

            if (minVal == Double.MAX_VALUE)
                minVal = 0;
            if (maxVal <= 0)
                maxVal = 10;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int padLeft = 70, padRight = 150, padTop = 50, padBottom = 70;
            int chartW = w - padLeft - padRight;
            int chartH = h - padTop - padBottom;

            // Title
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("Optimization Progress", w / 2 - 80, 25);

            // Draw axes
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(padLeft, padTop, padLeft, h - padBottom);
            g2.drawLine(padLeft, h - padBottom, w - padRight, h - padBottom);

            if (iterations.isEmpty()) {
                g2.drawString("(No data)", w / 2 - 30, h / 2);
                return;
            }

            // Calculate nice scales
            double range = maxVal - minVal;
            if (range <= 0)
                range = 1;

            int minIter = 0;
            int maxIter = iterations.get(iterations.size() - 1);
            double[] xScale = niceScale(minIter, maxIter, 8);
            double[] yScale = niceScale(Math.max(0, minVal - range * 0.05), maxVal + range * 0.05, 12);

            // Draw grid and axis labels with nice numbers
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 2 }, 0));

            // Y-axis grid
            for (double tik = yScale[0]; tik <= yScale[1] + 0.0001 * yScale[2]; tik += yScale[2]) {
                double norm = (tik - yScale[0]) / (yScale[1] - yScale[0]);
                int yPx = (h - padBottom) - (int) (norm * chartH);
                if (yPx < padTop || yPx > h - padBottom)
                    continue;
                g2.setColor(new Color(220, 220, 220));
                g2.drawLine(padLeft, yPx, w - padRight, yPx);
                g2.setColor(Color.BLACK);
                String label = formatNice(tik);
                g2.drawString(label, padLeft - g2.getFontMetrics().stringWidth(label) - 8, yPx + 4);
            }

            // X-axis grid
            for (double tik = xScale[0]; tik <= xScale[1] + 0.0001 * xScale[2]; tik += xScale[2]) {
                double norm = (tik - xScale[0]) / (xScale[1] - xScale[0]);
                int xPx = padLeft + (int) (norm * chartW);
                if (xPx < padLeft || xPx > w - padRight)
                    continue;
                g2.setColor(new Color(220, 220, 220));
                g2.drawLine(xPx, padTop, xPx, h - padBottom);
                g2.setColor(Color.BLACK);
                String label = formatNice(tik);
                g2.drawString(label, xPx - g2.getFontMetrics().stringWidth(label) / 2, h - padBottom + 18);
            }

            // Plot line
            double xRange = xScale[1] - xScale[0];
            double yRange2 = yScale[1] - yScale[0];
            if (xRange == 0)
                xRange = 1;
            if (yRange2 == 0)
                yRange2 = 1;

            g2.setColor(new Color(231, 76, 60));
            g2.setStroke(new BasicStroke(2.5f));
            int prevX = -1, prevY = -1;
            for (int i = 0; i < iterations.size(); i++) {
                int px = padLeft + (int) (((iterations.get(i) - xScale[0]) / xRange) * chartW);
                int py = (h - padBottom) - (int) (((values.get(i) - yScale[0]) / yRange2) * chartH);
                px = Math.max(padLeft, Math.min(w - padRight, px));
                py = Math.max(padTop, Math.min(h - padBottom, py));
                if (i > 0 && prevX >= 0)
                    g2.drawLine(prevX, prevY, px, py);
                prevX = px;
                prevY = py;
            }

            // Enhanced Axis Labels with nice numbers
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));

            // X-axis label with range
            String xAxisLabel = String.format("Iteration [%s to %s]", formatNice(xScale[0]), formatNice(xScale[1]));
            int xLabelWidth = g2.getFontMetrics().stringWidth(xAxisLabel);
            g2.setColor(Color.BLACK);
            g2.drawString(xAxisLabel, padLeft + (chartW - xLabelWidth) / 2, h - 48);

            // Y-axis label with range
            String yAxisLabel = String.format("Soft Cost [%s to %s]", formatNice(yScale[0]), formatNice(yScale[1]));
            g2.rotate(-Math.PI / 2);
            int yLabelWidth = g2.getFontMetrics().stringWidth(yAxisLabel);
            g2.drawString(yAxisLabel, -(h / 2 + yLabelWidth / 2), 15);
            g2.rotate(Math.PI / 2);

            // Scale info box
            g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
            String scaleInfo = String.format("X-Tick: %s | Y-Tick: %s", formatNice(xScale[2]), formatNice(yScale[2]));
            int infoWidth = g2.getFontMetrics().stringWidth(scaleInfo);
            g2.setColor(new Color(240, 240, 240));
            g2.fillRect(w - padRight + 5, padTop + 5, infoWidth + 12, 18);
            g2.setColor(new Color(100, 100, 100));
            g2.drawRect(w - padRight + 5, padTop + 5, infoWidth + 12, 18);
            g2.setColor(Color.BLACK);
            g2.drawString(scaleInfo, w - padRight + 10, padTop + 17);
        }

        private double[] niceScale(double min, double max, int maxTicks) {
            double range = niceNum(max - min, false);
            double tick = niceNum(range / (maxTicks - 1), true);
            double niceMin = Math.floor(min / tick) * tick;
            double niceMax = Math.ceil(max / tick) * tick;
            return new double[] { niceMin, niceMax, tick };
        }

        private double niceNum(double range, boolean round) {
            double exp = Math.floor(Math.log10(range));
            double frac = range / Math.pow(10, exp);
            double nice;
            if (round) {
                if (frac < 1.5)
                    nice = 1;
                else if (frac < 3)
                    nice = 2;
                else if (frac < 7)
                    nice = 5;
                else
                    nice = 10;
            } else {
                if (frac <= 1)
                    nice = 1;
                else if (frac <= 2)
                    nice = 2;
                else if (frac <= 5)
                    nice = 5;
                else
                    nice = 10;
            }
            return nice * Math.pow(10, exp);
        }

        private String formatNice(double d) {
            if (d == (long) d)
                return String.format("%,d", (long) d);
            return String.format("%,.1f", d);
        }
    }

    public IHTP_Launcher() {
        super("IHTP Launcher - PA-ILS + AT-ILS");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(buildUI());
        pack();
        setSize(1100, 850);
        setLocationRelativeTo(null);

        loadData();
        chartTimer = new Timer(1000, e -> refreshChart());
    }

    private Container buildUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        // === CONFIG PANEL ===
        JPanel config = new JPanel(new GridBagLayout());
        config.setBorder(BorderFactory.createTitledBorder("Configuration"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: JSON JAR
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 0;
        config.add(new JLabel("JSON JAR:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        jarField = new JTextField(findJar(), 30);
        config.add(jarField, gc);
        gc.gridx = 2;
        gc.weightx = 0;
        JButton browseJar = new JButton("Browse");
        browseJar.addActionListener(e -> browseFile(jarField));
        config.add(browseJar, gc);

        // Row 1: Instance (Multi-select List)
        gc.gridy = 1;
        gc.gridx = 0;
        gc.weightx = 0;
        config.add(new JLabel("Instances (Multi-select):"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.gridwidth = 2;
        gc.weighty = 0.3;
        gc.fill = GridBagConstraints.BOTH;
        instanceList = new JList<>();
        instanceList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        instanceList.setVisibleRowCount(4);
        JScrollPane instanceScroll = new JScrollPane(instanceList);
        config.add(instanceScroll, gc);
        gc.gridwidth = 1;
        gc.weighty = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Row 2: Solution (for validation)
        gc.gridy = 2;
        gc.gridx = 0;
        gc.weightx = 0;
        config.add(new JLabel("Solution:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        solutionCombo = new JComboBox<>();
        config.add(solutionCombo, gc);
        gc.gridx = 2;
        gc.weightx = 0;
        JButton browseSol = new JButton("Browse");
        browseSol.addActionListener(e -> browseSolutionFile());
        config.add(browseSol, gc);

        // Row 3: Runtime, Bulk, and Suffix
        gc.gridy = 3;
        gc.gridx = 0;
        gc.weightx = 0;
        config.add(new JLabel("Runtime (min):"), gc);
        gc.gridx = 1;
        gc.weightx = 0.2;
        runtimeField = new JTextField("10", 8);
        config.add(runtimeField, gc);

        gc.gridx = 2;
        gc.weightx = 0;
        JPanel bulkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        bulkPanel.add(new JLabel("Bulk:"));
        bulkField = new JTextField("1", 5);
        bulkPanel.add(bulkField);
        bulkPanel.add(new JLabel("Suffix:"));
        suffixField = new JTextField("_ATILS", 8);
        bulkPanel.add(suffixField);
        config.add(bulkPanel, gc);

        // Row 4: Solution Directory
        gc.gridy = 4;
        gc.gridx = 0;
        gc.weightx = 0;
        config.add(new JLabel("Solution Dir:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        solDirField = new JTextField("solutions_one_run", 30);
        config.add(solDirField, gc);
        gc.gridx = 2;
        gc.weightx = 0;
        JButton browseSolDir = new JButton("Browse");
        browseSolDir.addActionListener(e -> browseDirectory(solDirField));
        config.add(browseSolDir, gc);

        // Row 5: Violation Directory
        gc.gridy = 5;
        gc.gridx = 0;
        gc.weightx = 0;
        config.add(new JLabel("Violation Dir:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        violDirField = new JTextField("violation_log_one_run", 30);
        config.add(violDirField, gc);
        gc.gridx = 2;
        gc.weightx = 0;
        JButton browseViolDir = new JButton("Browse");
        browseViolDir.addActionListener(e -> browseDirectory(violDirField));
        config.add(browseViolDir, gc);

        // Row 6: Chart Directory
        gc.gridy = 6;
        gc.gridx = 0;
        gc.weightx = 0;
        config.add(new JLabel("Chart Dir:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        chartDirField = new JTextField("charts_one_run", 30);
        config.add(chartDirField, gc);
        gc.gridx = 2;
        gc.weightx = 0;
        JButton browseChartDir = new JButton("Browse");
        browseChartDir.addActionListener(e -> browseDirectory(chartDirField));
        config.add(browseChartDir, gc);

        // Row 7: Buttons
        gc.gridy = 7;
        gc.gridx = 0;
        gc.gridwidth = 3;
        gc.weightx = 1;
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        runBtn = new JButton("▶ Run");
        runBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        runBtn.setBackground(new Color(46, 204, 113));
        runBtn.setForeground(Color.WHITE);
        runBtn.setOpaque(true);
        runBtn.setBorderPainted(false);
        runBtn.addActionListener(e -> runOptimizer());

        abortBtn = new JButton("■ Abort");
        abortBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        abortBtn.setBackground(new Color(231, 76, 60));
        abortBtn.setForeground(Color.WHITE);
        abortBtn.setOpaque(true);
        abortBtn.setBorderPainted(false);
        abortBtn.setEnabled(false);
        abortBtn.addActionListener(e -> abortProcess());

        validateBtn = new JButton("✓ Validate");
        validateBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        validateBtn.setBackground(new Color(52, 152, 219));
        validateBtn.setForeground(Color.WHITE);
        validateBtn.setOpaque(true);
        validateBtn.setBorderPainted(false);
        validateBtn.addActionListener(e -> validateSolution());

        JButton refreshBtn = new JButton("↻ Refresh");
        refreshBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        refreshBtn.addActionListener(e -> loadData());

        btnPanel.add(runBtn);
        btnPanel.add(abortBtn);
        btnPanel.add(validateBtn);
        btnPanel.add(refreshBtn);
        config.add(btnPanel, gc);

        // === CONSOLE + CHART ===
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.6);

        consoleArea = new JTextArea(10, 80);
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        split.setTopComponent(new JScrollPane(consoleArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED));

        chartPanel = new ChartPanel();
        chartPanel.setPreferredSize(new Dimension(800, 250));
        JPanel chartWrap = new JPanel(new BorderLayout());
        chartWrap.setBorder(BorderFactory.createTitledBorder("Optimization Progress"));
        chartWrap.add(chartPanel, BorderLayout.CENTER);
        split.setBottomComponent(chartWrap);

        root.add(config, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private void loadData() {
        loadInstances();
        loadSolutions();
    }

    private void loadInstances() {
        allInstances.clear();
        loadDir(TEST_DIR, "[test]", allInstances);
        loadDir(COMPETITION_DIR, "[comp]", allInstances);

        DefaultListModel<FileItem> model = new DefaultListModel<>();
        for (FileItem item : allInstances) {
            model.addElement(item);
        }
        instanceList.setModel(model);
    }

    private void loadSolutions() {
        allSolutions.clear();
        String solDir = solDirField.getText().trim();
        loadDir(solDir, "[sol]", allSolutions);
        loadDir("./solutions", "[sol]", allSolutions);

        solutionCombo.removeAllItems();
        for (FileItem item : allSolutions) {
            solutionCombo.addItem(item);
        }
    }

    private void loadDir(String path, String tag, List<FileItem> target) {
        File dir = new File(path);
        if (!dir.isDirectory())
            return;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null)
            return;
        Arrays.sort(files);
        for (File f : files)
            target.add(new FileItem(f, tag + " " + f.getName()));
    }

    private void browseSolutionFile() {
        JFileChooser chooser = new JFileChooser(".");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            FileItem item = new FileItem(f, "[custom] " + f.getName());
            solutionCombo.addItem(item);
            solutionCombo.setSelectedItem(item);
        }
    }

    private void runOptimizer() {
        int[] selectedIndices = instanceList.getSelectedIndices();
        if (selectedIndices.length == 0) {
            error("Pilih minimal 1 instance terlebih dahulu");
            return;
        }

        String jar = jarField.getText().trim();
        if (!new File(jar).isFile()) {
            error("JAR tidak ditemukan: " + jar);
            return;
        }

        // Validate and get runtime
        int runtimeMinutes;
        try {
            runtimeMinutes = Integer.parseInt(runtimeField.getText().trim());
            if (runtimeMinutes <= 0) {
                error("Runtime harus lebih dari 0");
                return;
            }
        } catch (Exception e) {
            error("Runtime harus berupa angka");
            return;
        }

        // Validate and get bulk
        int bulkRuns;
        try {
            bulkRuns = Integer.parseInt(bulkField.getText().trim());
            if (bulkRuns <= 0) {
                error("Bulk harus lebih dari 0");
                return;
            }
        } catch (Exception e) {
            error("Bulk harus berupa angka");
            return;
        }

        consoleArea.setText("");
        chartPanel.reset();
        setRunState(true);

        String classpath = "bin" + File.pathSeparator + "." + File.pathSeparator + jar;
        String baseSuffix = suffixField.getText().trim();
        String solDir = solDirField.getText().trim();
        String violDir = violDirField.getText().trim();

        // Build list of instances to run
        List<FileItem> instancesToRun = new ArrayList<>();
        DefaultListModel<FileItem> model = (DefaultListModel<FileItem>) instanceList.getModel();
        for (int idx : selectedIndices) {
            instancesToRun.add(model.getElementAt(idx));
        }

        worker = new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() {
                int totalRuns = instancesToRun.size() * bulkRuns;
                int currentRun = 0;

                for (FileItem item : instancesToRun) {
                    for (int bulk = 1; bulk <= bulkRuns; bulk++) {
                        currentRun++;

                        // Create suffix: baseSuffix + _bulkNumber
                        String finalSuffix = baseSuffix + "_" + bulk;

                        String baseName = item.file.getName().replaceAll("\\.json$", "");
                        String logPath = violDir + File.separator + "violation_log_" + baseName + finalSuffix + ".csv";
                        String solPath = solDir + File.separator + "solution_" + baseName + finalSuffix + ".json";

                        new File(violDir).mkdirs();
                        new File(solDir).mkdirs();

                        publish("\n" + "=".repeat(80) + "\n");
                        publish(String.format("Run %d/%d: %s (Bulk %d/%d)\n", currentRun, totalRuns, item.label, bulk,
                                bulkRuns));
                        publish("=".repeat(80) + "\n");

                        List<String> cmd = new ArrayList<>();
                        cmd.add("java");
                        cmd.add("-cp");
                        cmd.add(classpath);
                        cmd.add("IHTP_Solution");
                        cmd.add(item.file.getAbsolutePath());
                        cmd.add(String.valueOf(runtimeMinutes));
                        cmd.add(logPath);
                        cmd.add(solPath);

                        try {
                            ProcessBuilder pb = new ProcessBuilder(cmd);
                            pb.redirectErrorStream(true);
                            Process p = pb.start();
                            currentProcess = p;

                            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                                String line;
                                while ((line = r.readLine()) != null) {
                                    publish(line + "\n");
                                    currentLogFile = logPath;
                                }
                            }

                            int code = p.waitFor();
                            currentProcess = null;
                            publish("\nStatus: " + (code == 0 ? "✓ SUCCESS" : "✗ FAILED") + "\n");

                            // Auto-add solution to dropdown if successful
                            if (code == 0 && new File(solPath).exists()) {
                                SwingUtilities.invokeLater(() -> {
                                    File solFile = new File(solPath);
                                    FileItem solItem = new FileItem(solFile, "[new] " + solFile.getName());
                                    boolean exists = false;
                                    for (int i = 0; i < solutionCombo.getItemCount(); i++) {
                                        FileItem existing = solutionCombo.getItemAt(i);
                                        if (existing.file.getAbsolutePath().equals(solFile.getAbsolutePath())) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        solutionCombo.addItem(solItem);
                                        solutionCombo.setSelectedItem(solItem);
                                    }
                                });
                            }

                            // Auto-save chart to chartDir after successful optimization
                            String chartDir = chartDirField.getText().trim();
                            if (code == 0 && new File(logPath).exists() && !chartDir.isEmpty()) {
                                try {
                                    new File(chartDir).mkdirs();
                                    String chartPath = chartDir + File.separator + "chart_" + baseName + finalSuffix
                                            + ".png";
                                    saveChartToFile(logPath, chartPath);
                                    publish("📊 Chart saved: " + chartPath + "\n");
                                } catch (Exception chartEx) {
                                    publish("⚠ Chart save failed: " + chartEx.getMessage() + "\n");
                                }
                            }
                        } catch (Exception ex) {
                            publish("ERROR: " + ex.getMessage() + "\n");
                        }
                    }
                }

                publish("\n" + "=".repeat(80) + "\n");
                publish("ALL RUNS COMPLETED!\n");
                publish("=".repeat(80) + "\n");
                return 0;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks)
                    consoleArea.append(s);
                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                setRunState(false);
                stopChart();
            }
        };

        startChart();
        worker.execute();
    }

    private void validateSolution() {
        FileItem selectedInstance = instanceList.getSelectedValue();
        FileItem selectedSolution = (FileItem) solutionCombo.getSelectedItem();

        if (selectedInstance == null) {
            error("Pilih instance terlebih dahulu");
            return;
        }

        if (selectedSolution == null) {
            error("Pilih solution terlebih dahulu");
            return;
        }

        String jar = jarField.getText().trim();
        if (!new File(jar).isFile()) {
            error("JAR tidak ditemukan: " + jar);
            return;
        }

        consoleArea.setText("");
        setRunState(true);

        String classpath = "bin" + File.pathSeparator + "." + File.pathSeparator + jar;

        worker = new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() {
                publish("=== Validating Solution ===\n");
                publish("Instance: " + selectedInstance.label + "\n");
                publish("Solution: " + selectedSolution.label + "\n\n");

                List<String> cmd = new ArrayList<>();
                cmd.add("java");
                cmd.add("-cp");
                cmd.add(classpath);
                cmd.add("IHTP_Validator");
                cmd.add(selectedInstance.file.getAbsolutePath());
                cmd.add(selectedSolution.file.getAbsolutePath());

                try {
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    currentProcess = p;

                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            publish(line + "\n");
                        }
                    }

                    int code = p.waitFor();
                    currentProcess = null;
                    publish("\nValidation " + (code == 0 ? "✓ PASSED" : "✗ FAILED") + "\n");
                } catch (Exception ex) {
                    publish("ERROR: " + ex.getMessage() + "\n");
                }
                return 0;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks)
                    consoleArea.append(s);
                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                setRunState(false);
            }
        };

        worker.execute();
    }

    private void startChart() {
        if (chartTimer != null)
            chartTimer.start();
    }

    private void stopChart() {
        if (chartTimer != null)
            chartTimer.stop();
    }

    private void refreshChart() {
        if (currentLogFile != null && new File(currentLogFile).exists()) {
            chartPanel.loadLog(currentLogFile);
        }
    }

    private void abortProcess() {
        if (currentProcess != null) {
            try {
                currentProcess.destroyForcibly();
                currentProcess = null;
            } catch (Exception ignored) {
            }
        }
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        stopChart();
        setRunState(false);
    }

    private void setRunState(boolean running) {
        runBtn.setEnabled(!running);
        abortBtn.setEnabled(running);
        validateBtn.setEnabled(!running);
        instanceList.setEnabled(!running);
        solutionCombo.setEnabled(!running);
        solDirField.setEnabled(!running);
        violDirField.setEnabled(!running);
        chartDirField.setEnabled(!running);
        runtimeField.setEnabled(!running);
        bulkField.setEnabled(!running);
    }

    private void browseFile(JTextField field) {
        JFileChooser chooser = new JFileChooser(".");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JAR files", "jar"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void browseDirectory(JTextField field) {
        JFileChooser chooser = new JFileChooser(".");
        chooser.setDialogTitle("Pilih Direktori");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private String findJar() {
        File f = new File(DEFAULT_JAR);
        if (f.exists())
            return f.getAbsolutePath();
        return DEFAULT_JAR;
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Save optimization chart to file with nice axis formatting
     */
    private void saveChartToFile(String csvPath, String outputPath) throws Exception {
        List<Integer> iterations = new ArrayList<>();
        List<Double> softCosts = new ArrayList<>();
        List<Double> bestCosts = new ArrayList<>();
        List<Double> thresholds = new ArrayList<>();

        // Parse CSV log file
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String header = br.readLine();
            if (header == null)
                return;

            String[] cols = header.split(",");
            int colSoft = -1, colBest = -1, colThreshold = -1;
            for (int i = 0; i < cols.length; i++) {
                String h = cols[i].trim().toLowerCase();
                if (h.contains("soft_cost"))
                    colSoft = i;
                else if (h.contains("best_soft"))
                    colBest = i;
                else if (h.equals("threshold"))
                    colThreshold = i;
            }

            if (colSoft == -1)
                colSoft = 3;
            if (colBest == -1)
                colBest = 5;

            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                try {
                    int iter = Integer.parseInt(parts[0].trim());
                    double soft = Double.parseDouble(parts[colSoft].trim());
                    double best = Double.parseDouble(parts[colBest].trim());
                    double thresh = 0.0;
                    if (colThreshold >= 0 && parts.length > colThreshold) {
                        try {
                            thresh = Double.parseDouble(parts[colThreshold].trim());
                        } catch (Exception ignored) {
                        }
                    }
                    iterations.add(iter);
                    softCosts.add(soft);
                    bestCosts.add(best);
                    thresholds.add(thresh);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (iterations.isEmpty())
            return;

        // Create chart image (1200x800 for thesis quality)
        int w = 1200, h = 800;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);

        int padLeft = 80, padRight = 30, padTop = 60, padBottom = 60;
        int chartW = w - padLeft - padRight;
        int chartH = h - padTop - padBottom;

        // Calculate nice scales
        int minIter = iterations.get(0);
        int maxIter = iterations.get(iterations.size() - 1);
        double[] xScale = niceScale(minIter, maxIter, 10);

        double minVal = Double.MAX_VALUE, maxVal = -Double.MAX_VALUE;
        for (Double v : softCosts) {
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
        double range = maxVal - minVal;
        if (range == 0)
            range = 10;
        double[] yScale = niceScale(Math.max(0, minVal - range * 0.05), maxVal + range * 0.05, 15);

        // Title
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        String title = "AT-ILS Optimization Progress";
        g2.drawString(title, (w - g2.getFontMetrics().stringWidth(title)) / 2, 35);

        // Draw grid and axes
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 3 }, 0));

        // Y-axis grid
        for (double tik = yScale[0]; tik <= yScale[1] + 0.0001 * yScale[2]; tik += yScale[2]) {
            double norm = (tik - yScale[0]) / (yScale[1] - yScale[0]);
            int yPx = (h - padBottom) - (int) (norm * chartH);
            if (yPx < padTop || yPx > h - padBottom)
                continue;
            g2.setColor(new Color(220, 220, 220));
            g2.drawLine(padLeft, yPx, w - padRight, yPx);
            g2.setColor(Color.BLACK);
            String label = formatNice(tik);
            g2.drawString(label, padLeft - g2.getFontMetrics().stringWidth(label) - 8, yPx + 4);
        }

        // X-axis grid
        for (double tik = xScale[0]; tik <= xScale[1] + 0.0001 * xScale[2]; tik += xScale[2]) {
            double norm = (tik - xScale[0]) / (xScale[1] - xScale[0]);
            int xPx = padLeft + (int) (norm * chartW);
            if (xPx < padLeft || xPx > w - padRight)
                continue;
            g2.setColor(new Color(220, 220, 220));
            g2.drawLine(xPx, padTop, xPx, h - padBottom);
            g2.setColor(Color.BLACK);
            String label = formatNice(tik);
            g2.drawString(label, xPx - g2.getFontMetrics().stringWidth(label) / 2, h - padBottom + 20);
        }

        // Axes
        g2.setStroke(new BasicStroke(2));
        g2.setColor(Color.BLACK);
        g2.drawLine(padLeft, padTop, padLeft, h - padBottom);
        g2.drawLine(padLeft, h - padBottom, w - padRight, h - padBottom);

        // Enhanced Axis Labels with Nice Numbers and Ranges
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));

        // X-axis label with range
        String xAxisLabel = String.format("Iteration [%s to %s]", formatNice(xScale[0]), formatNice(xScale[1]));
        int xLabelWidth = g2.getFontMetrics().stringWidth(xAxisLabel);
        g2.drawString(xAxisLabel, padLeft + (chartW - xLabelWidth) / 2, h - 25);

        // Y-axis label with range (rotated)
        String yAxisLabel = String.format("Soft Cost [%s to %s]", formatNice(yScale[0]), formatNice(yScale[1]));
        g2.rotate(-Math.PI / 2);
        int yLabelWidth = g2.getFontMetrics().stringWidth(yAxisLabel);
        g2.drawString(yAxisLabel, -(h / 2 + yLabelWidth / 2), 15);
        g2.rotate(Math.PI / 2);

        // Add axis scale info box
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        String scaleInfo = String.format("X-Tick: %s | Y-Tick: %s", formatNice(xScale[2]), formatNice(yScale[2]));
        int infoWidth = g2.getFontMetrics().stringWidth(scaleInfo);
        g2.setColor(new Color(240, 240, 240));
        g2.fillRect(w - padRight - infoWidth - 20, padTop + 5, infoWidth + 15, 20);
        g2.setColor(new Color(100, 100, 100));
        g2.drawRect(w - padRight - infoWidth - 20, padTop + 5, infoWidth + 15, 20);
        g2.drawString(scaleInfo, w - padRight - infoWidth - 15, padTop + 18);

        double xRange = xScale[1] - xScale[0];
        double yRange2 = yScale[1] - yScale[0];
        if (xRange == 0)
            xRange = 1;
        if (yRange2 == 0)
            yRange2 = 1;

        // Plot current cost (thin blue)
        g2.setColor(new Color(52, 152, 219, 150));
        g2.setStroke(new BasicStroke(1.5f));
        int prevX = -1, prevY = -1;
        for (int i = 0; i < iterations.size(); i++) {
            int px = padLeft + (int) (((iterations.get(i) - xScale[0]) / xRange) * chartW);
            int py = (h - padBottom) - (int) (((softCosts.get(i) - yScale[0]) / yRange2) * chartH);
            px = Math.max(padLeft, Math.min(w - padRight, px));
            py = Math.max(padTop, Math.min(h - padBottom, py));
            if (i > 0)
                g2.drawLine(prevX, prevY, px, py);
            prevX = px;
            prevY = py;
        }

        // Plot best cost (bold red)
        g2.setColor(new Color(231, 76, 60));
        g2.setStroke(new BasicStroke(3f));
        prevX = -1;
        prevY = -1;
        for (int i = 0; i < iterations.size(); i++) {
            int px = padLeft + (int) (((iterations.get(i) - xScale[0]) / xRange) * chartW);
            int py = (h - padBottom) - (int) (((bestCosts.get(i) - yScale[0]) / yRange2) * chartH);
            px = Math.max(padLeft, Math.min(w - padRight, px));
            py = Math.max(padTop, Math.min(h - padBottom, py));
            if (i > 0)
                g2.drawLine(prevX, prevY, px, py);
            prevX = px;
            prevY = py;
        }

        // Plot threshold (dashed orange)
        boolean hasThreshold = false;
        for (Double t : thresholds)
            if (t > 0) {
                hasThreshold = true;
                break;
            }
        if (hasThreshold) {
            g2.setColor(new Color(255, 165, 0));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f,
                    new float[] { 5.0f, 5.0f }, 0.0f));
            prevX = -1;
            prevY = -1;
            for (int i = 0; i < iterations.size(); i++) {
                if (thresholds.get(i) <= 0)
                    continue;
                int px = padLeft + (int) (((iterations.get(i) - xScale[0]) / xRange) * chartW);
                int py = (h - padBottom) - (int) (((thresholds.get(i) - yScale[0]) / yRange2) * chartH);
                px = Math.max(padLeft, Math.min(w - padRight, px));
                py = Math.max(padTop, Math.min(h - padBottom, py));
                if (prevX >= 0)
                    g2.drawLine(prevX, prevY, px, py);
                prevX = px;
                prevY = py;
            }
        }

        // Legend
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        int legX = padLeft + 20, legY = padTop + 20;
        g2.setColor(new Color(52, 152, 219, 150));
        g2.fillRect(legX, legY, 20, 3);
        g2.setColor(Color.BLACK);
        g2.drawString("Current Cost", legX + 25, legY + 3);
        legY += 20;
        g2.setColor(new Color(231, 76, 60));
        g2.setStroke(new BasicStroke(3f));
        g2.drawLine(legX, legY, legX + 20, legY);
        g2.setColor(Color.BLACK);
        g2.drawString("Best Cost", legX + 25, legY + 3);
        if (hasThreshold) {
            legY += 20;
            g2.setColor(new Color(255, 165, 0));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f,
                    new float[] { 5.0f, 5.0f }, 0.0f));
            g2.drawLine(legX, legY, legX + 20, legY);
            g2.setColor(Color.BLACK);
            g2.drawString("Threshold", legX + 25, legY + 3);
        }

        // Stats info
        double initialBest = bestCosts.get(0);
        double finalBest = bestCosts.get(bestCosts.size() - 1);
        double improvement = initialBest > 0 ? ((initialBest - finalBest) / initialBest) * 100 : 0;
        String info = String.format("Initial: %s | Final: %s | Improvement: %.2f%% | Points: %d",
                formatNice(initialBest), formatNice(finalBest), improvement, iterations.size());
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(Color.BLACK);
        g2.drawString(info, padLeft + 10, h - 15);

        g2.dispose();
        javax.imageio.ImageIO.write(img, "png", new File(outputPath));
    }

    private double[] niceScale(double min, double max, int maxTicks) {
        double range = niceNum(max - min, false);
        double tick = niceNum(range / (maxTicks - 1), true);
        double niceMin = Math.floor(min / tick) * tick;
        double niceMax = Math.ceil(max / tick) * tick;
        return new double[] { niceMin, niceMax, tick };
    }

    private double niceNum(double range, boolean round) {
        double exp = Math.floor(Math.log10(range));
        double frac = range / Math.pow(10, exp);
        double nice;
        if (round) {
            if (frac < 1.5)
                nice = 1;
            else if (frac < 3)
                nice = 2;
            else if (frac < 7)
                nice = 5;
            else
                nice = 10;
        } else {
            if (frac <= 1)
                nice = 1;
            else if (frac <= 2)
                nice = 2;
            else if (frac <= 5)
                nice = 5;
            else
                nice = 10;
        }
        return nice * Math.pow(10, exp);
    }

    private String formatNice(double d) {
        if (d == (long) d)
            return String.format("%,d", (long) d);
        return String.format("%,.1f", d);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new IHTP_Launcher().setVisible(true));
    }
}
