#!/bin/bash
# Chart Generator for AT-ILS Optimization Logs
# 
# Features:
# - Load CSV logs from violation_* or atils_log_* directories
# - Phase filtering (All Phases, PERTURBATION, LOCAL_SEARCH, ACCEPTANCE, etc.)
# - Displays: Current Cost (blue), Nilai Ambang Batas/Threshold (orange)
# - Nice number formatting for X-axis (iteration) and Y-axis (cost)
# - Interactive chart visualization
#
# Usage: ./create_chart.sh

# Move to project root
cd "$(dirname "$0")/.." || exit

# Ensure bin directory exists
mkdir -p bin

# Compile
echo "Compiling ChartGeneratorGUI..."
javac -d bin ChartGeneratorGUI.java

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful!"
    echo ""
    echo "Launching Chart Generator GUI..."
    echo ""
    echo "Legend:"
    echo "  Blue line           → Current Cost (iterasi saat ini)"
    echo "  Orange line         → Nilai Ambang Batas (Threshold)"
    echo ""
    echo "Tips:"
    echo "  • Garis orange menunjukkan threshold adaptif untuk local search"
    echo "  • Garis biru menunjukkan cost yang diterima pada setiap iterasi"
    echo "  • Pilih fase untuk filter visualisasi"
    echo "  • Tombol Save exports chart sebagai PNG (1200x800)"
    echo ""
    
    java -cp bin ChartGeneratorGUI
else
    echo "✗ Compilation failed!"
    exit 1
fi
