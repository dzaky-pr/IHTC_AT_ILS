#!/bin/bash

# Pastikan berada di root directory project (yg ada IHTP_Input.java)
# Jika dijalankan dari root (./scripts/analyze_instances.sh)
if [ -f "IHTP_Input.java" ]; then
    ROOT_DIR="."
# Jika dijalankan dari dalam scripts (./analyze_instances.sh)
elif [ -f "../IHTP_Input.java" ]; then
    ROOT_DIR=".."
    cd ..
else
    echo "Error: Harap jalankan dari root directory project."
    exit 1
fi

echo "=== Compiling IHTP Analyzer ==="

# Choose analyzer source (prefer root, then scripts/)
if [ -f "IHTP_Analyzer.java" ]; then
    SRC="IHTP_Analyzer.java"
elif [ -f "scripts/IHTP_Analyzer.java" ]; then
    SRC="scripts/IHTP_Analyzer.java"
elif [ -f "scripts/IHTP_Analyzer.java.orig" ]; then
    SRC="scripts/IHTP_Analyzer.java.orig"
else
    echo "Error: Analyzer source not found (IHTP_Analyzer.java)."
    exit 1
fi

# Prepare documents dir and log file
DOCUMENTS_DIR="$ROOT_DIR/documents"
mkdir -p "$DOCUMENTS_DIR"
LOG="$DOCUMENTS_DIR/analyze_instances_result_$(date +%Y%m%d_%H%M%S).txt"

# Compile into bin/ so we can run from project root
(cd "$ROOT_DIR" && javac -cp ".:json-20250107.jar" -d bin "$SRC")

if [ $? -eq 0 ]; then
    echo "=== Running Analysis on ihtc2024_competition_instances ==="
    (cd "$ROOT_DIR" && java -cp "bin:json-20250107.jar" IHTP_Analyzer ihtc2024_competition_instances | tee "$LOG")
    echo ""
    echo "Analysis saved to: $LOG"
else
    echo "Compilation Failed!"
fi
