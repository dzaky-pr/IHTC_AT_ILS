#!/bin/bash

# Script untuk membuat box plot visualisasi dari hasil runs
# Sama seperti final_result_analysis.sh tapi untuk visualisasi box plot

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Check if Python 3 is available
if ! command -v python3 &> /dev/null; then
    echo "Error: Python 3 is required but not installed."
    exit 1
fi

# Check if matplotlib is installed
python3 -c "import matplotlib" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "Installing required Python packages..."
    pip3 install matplotlib numpy
fi

# Run the Python visualization script
python3 "$SCRIPT_DIR/boxplot_visualization.py"
