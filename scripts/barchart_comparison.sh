#!/bin/bash

# Script untuk membuat bar chart perbandingan dari dua dataset
# Similar to boxplot_visualization.sh

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Check if Python 3 is available
if ! command -v python3 &> /dev/null; then
    echo "Error: Python 3 is required but not installed."
    exit 1
fi

# Run the Python visualization script
python3 "$SCRIPT_DIR/barchart_comparison.py"
