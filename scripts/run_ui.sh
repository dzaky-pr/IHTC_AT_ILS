#!/bin/bash

# Script untuk menjalankan IHTP Launcher dengan UI
# Simple PA-ILS + AT-ILS Optimizer GUI

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Warna untuk output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  IHTP Launcher - PA-ILS + AT-ILS${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 1. Buat folder yang diperlukan
echo -e "${YELLOW}[1/3] Preparing directories...${NC}"
mkdir -p "$PROJECT_ROOT/bin"
mkdir -p "$PROJECT_ROOT/solutions_one_run"
mkdir -p "$PROJECT_ROOT/violation_log_one_run"
echo -e "${GREEN}âœ“ Directories ready${NC}"
echo ""

# 2. Compile Java files
echo -e "${YELLOW}[2/3] Compiling Java files...${NC}"
echo "Compiling: IHTP_Input.java"
echo "Compiling: IHTP_Preprocess.java"
echo "Compiling: PA_ILS.java"
echo "Compiling: IHTP_Validator.java"
echo "Compiling: IHTP_Solution.java"
echo "Compiling: IHTP_ATILS.java"
echo "Compiling: IHTP_Launcher.java"

cd "$PROJECT_ROOT"
javac -cp ".:json-20250107.jar" -d bin \
    IHTP_Input.java \
    IHTP_Preprocess.java \
    PA_ILS.java \
    IHTP_Validator.java \
    IHTP_Solution.java \
    IHTP_ATILS.java \
    IHTP_Launcher.java

if [ $? -eq 0 ]; then
    echo -e "${GREEN}âœ“ Compilation successful${NC}"
else
    echo -e "${RED}âœ— Compilation failed!${NC}"
    exit 1
fi
echo ""

# 3. Jalankan UI
echo -e "${YELLOW}[3/3] Launching GUI...${NC}"
echo -e "${GREEN}âœ“ Starting IHTP Launcher${NC}"
echo ""
echo -e "${BLUE}========================================${NC}"
echo ""

cd "$PROJECT_ROOT"
java -cp "bin:json-20250107.jar" IHTP_Launcher

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}GUI closed. Thank you!${NC}"
echo -e "${BLUE}========================================${NC}"
