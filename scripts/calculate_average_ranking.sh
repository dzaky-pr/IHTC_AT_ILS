#!/bin/bash

# ============================================================================
# Script: Calculate Average Ranking for ATILS Dataset
# Description: Menghitung average ranking untuk setiap run dari berbagai eksperimen
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "============================================================================"
echo "CALCULATE AVERAGE RANKING - ATILS DATASET"
echo "============================================================================"
echo ""

# Set variables - relative to project root
OUTPUT_DIR="$PROJECT_ROOT/ranking_results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Find all folders matching pattern atils_log_one_run_* in project root
echo "🔍 Scanning for data folders with pattern: atils_log_one_run_*"
echo "   Location: $PROJECT_ROOT"
echo ""

DATA_FOLDERS=($(find "$PROJECT_ROOT" -maxdepth 1 -type d -name "atils_log_one_run_*" | sort))

if [ ${#DATA_FOLDERS[@]} -eq 0 ]; then
    echo "❌ Error: No folders found matching pattern 'atils_log_one_run_*'"
    echo "   Please make sure at least one folder exists in: $PROJECT_ROOT"
    exit 1
fi

echo "📁 Found ${#DATA_FOLDERS[@]} data folder(s):"
for folder in "${DATA_FOLDERS[@]}"; do
    folder_name=$(basename "$folder")
    file_count=$(find "$folder" -name "atils_log_*.csv" 2>/dev/null | wc -l | tr -d ' ')
    echo "   • $folder_name ($file_count CSV files)"
done
echo ""

# Ask user to select folder or use the one with most CSV files (likely the 11 run)
if [ ${#DATA_FOLDERS[@]} -eq 1 ]; then
    DATA_FOLDER="${DATA_FOLDERS[0]}"
    echo "✅ Using folder: $(basename "$DATA_FOLDER")"
else
    # Find folder with most CSV files (most likely the 11 runs)
    max_files=0
    selected_folder=""
    for folder in "${DATA_FOLDERS[@]}"; do
        file_count=$(find "$folder" -name "atils_log_*.csv" 2>/dev/null | wc -l | tr -d ' ')
        if [ "$file_count" -gt "$max_files" ]; then
            max_files=$file_count
            selected_folder="$folder"
        fi
    done
    DATA_FOLDER="$selected_folder"
    echo "✅ Auto-selected folder with most runs: $(basename "$DATA_FOLDER") ($max_files CSV files)"
    echo "   (Likely contains multiple runs per instance)"
fi
echo ""

# Count CSV files
FILE_COUNT=$(find "$DATA_FOLDER" -name "atils_log_*.csv" | wc -l | tr -d ' ')
echo "📊 Processing $FILE_COUNT CSV files from: $(basename "$DATA_FOLDER")"
echo ""

if [ "$FILE_COUNT" -eq 0 ]; then
    echo "❌ No CSV files found in $DATA_FOLDER"
    exit 1
fi

# Run Python script to calculate average ranking
echo "🔄 Processing data and calculating rankings..."
echo ""

# Use Python from virtual environment if available, otherwise use system python3
if [ -f "$PROJECT_ROOT/.venv/bin/python" ]; then
    PYTHON_CMD="$PROJECT_ROOT/.venv/bin/python"
elif [ -f "$PROJECT_ROOT/venv/bin/python" ]; then
    PYTHON_CMD="$PROJECT_ROOT/venv/bin/python"
else
    PYTHON_CMD="python3"
fi

$PYTHON_CMD - "$DATA_FOLDER" "$OUTPUT_DIR" << 'PYTHON_SCRIPT'
import pandas as pd
import numpy as np
import glob
import os
import sys
from datetime import datetime

# Get data folder and output directory from command line arguments
DATA_FOLDER = sys.argv[1] if len(sys.argv) > 1 else "atils_log_one_run_11_run_archive_3"
OUTPUT_DIR = sys.argv[2] if len(sys.argv) > 2 else "ranking_results"

print("="*100)
print("STEP 1: LOADING DATA FROM ALL RUNS")
print("="*100)
print(f"Data Folder: {DATA_FOLDER}\n")

# Get all CSV files
csv_files = sorted(glob.glob(f"{DATA_FOLDER}/atils_log_*.csv"))
print(f"Found {len(csv_files)} CSV files\n")

# Dictionary to store all costs: {instance: {run: final_cost}}
all_costs = {}

for csv_file in csv_files:
    filename = os.path.basename(csv_file)
    
    # Parse filename: atils_log_i01_ATILS_1_atils.csv
    # Extract instance (i01) and run number (1)
    parts = filename.replace('atils_log_', '').replace('_atils.csv', '').split('_')
    instance = parts[0]  # e.g., i01
    run_number = int(parts[2])  # e.g., 1
    
    # Read the CSV file
    try:
        df = pd.read_csv(csv_file)
        
        # Get the final cost (last row) - use soft_cost column
        if len(df) > 0:
            final_cost = df['soft_cost'].iloc[-1]
            
            # Initialize instance if not exists
            if instance not in all_costs:
                all_costs[instance] = {}
            
            # Store the final cost
            all_costs[instance][f'T{run_number}'] = final_cost
    except Exception as e:
        print(f"❌ Error reading {filename}: {e}")

print(f"✅ Extracted costs from {len(csv_files)} files")
print(f"✅ Total instances: {len(all_costs)}\n")

# Determine the number of runs dynamically
max_run = 0
for instance_costs in all_costs.values():
    for run_name in instance_costs.keys():
        run_num = int(run_name[1:])  # Extract number from T1, T2, etc.
        max_run = max(max_run, run_num)

print(f"🔢 Detected {max_run} runs (T1 to T{max_run})\n")

# Create DataFrame where each row is an instance and each column is a run
print("="*100)
print("STEP 2: CREATE COST MATRIX (Instances x Runs)")
print("="*100)

data_rows = []
for instance in sorted(all_costs.keys()):
    row = {'Instance': instance}
    for run_num in range(1, max_run + 1):
        run_name = f'T{run_num}'
        if run_name in all_costs[instance]:
            row[run_name] = float(all_costs[instance][run_name])
        else:
            row[run_name] = np.nan
    data_rows.append(row)

df_costs = pd.DataFrame(data_rows)
print(f"Matrix shape: {df_costs.shape}")
print(f"Columns: {df_costs.columns.tolist()}\n")

# Save costs matrix
costs_output = f"{OUTPUT_DIR}/atils_all_runs_costs.csv"
df_costs.to_csv(costs_output, index=False)
print(f"✅ Saved: {costs_output}\n")

# Calculate rankings
print("="*100)
print("STEP 3: CALCULATE RANKINGS FOR EACH INSTANCE")
print("="*100)

# Store rankings for each run across all instances
run_rankings = {f'T{i}': [] for i in range(1, max_run + 1)}

for idx, row in df_costs.iterrows():
    instance = row['Instance']
    costs = row[1:].values.astype(float)  # Ensure float type
    valid_costs = costs[~pd.isna(costs)]  # Use pd.isna instead of np.isnan
    
    if len(valid_costs) == 0:
        continue
    
    # Calculate ranking for each run
    for i, cost in enumerate(costs):
        run_name = f'T{i+1}'
        if not pd.isna(cost):  # Use pd.isna instead of np.isnan
            better_count = np.sum(valid_costs < cost)
            ranking = better_count + 1
            run_rankings[run_name].append(ranking)

print(f"✅ Rankings calculated for {len(df_costs)} instances\n")

# Calculate average ranking
print("="*100)
print("STEP 4: CALCULATE AVERAGE RANKING FOR EACH RUN")
print("="*100)

run_avg_rankings = []

for run, rankings in run_rankings.items():
    if len(rankings) > 0:
        avg_ranking = np.mean(rankings)
        median_ranking = np.median(rankings)
        best_ranking = np.min(rankings)
        worst_ranking = np.max(rankings)
        std_ranking = np.std(rankings)
        rank_1_count = sum(1 for r in rankings if r == 1)
        rank_top3_count = sum(1 for r in rankings if r <= 3)
        
        run_avg_rankings.append({
            'Run': run,
            'Avg Ranking': round(avg_ranking, 2),
            'Median Ranking': round(median_ranking, 1),
            'Best Ranking': int(best_ranking),
            'Worst Ranking': int(worst_ranking),
            'Std Dev': round(std_ranking, 2),
            'Total Instances': len(rankings),
            'Rank #1 Count': rank_1_count,
            'Top 3 Count': rank_top3_count
        })

# Create DataFrame and sort by average ranking
df_run_rankings = pd.DataFrame(run_avg_rankings)
df_run_rankings = df_run_rankings.sort_values('Avg Ranking')
df_run_rankings['Final Rank'] = range(1, len(df_run_rankings) + 1)

# Reorder columns
df_run_rankings = df_run_rankings[['Final Rank', 'Run', 'Avg Ranking', 'Median Ranking', 
                                     'Best Ranking', 'Worst Ranking', 'Std Dev', 
                                     'Total Instances', 'Rank #1 Count', 'Top 3 Count']]

print("\n" + "="*100)
print("AVERAGE RANKING - ALL RUNS (Sorted by Avg Ranking)")
print("="*100)
print(df_run_rankings.to_string(index=False))

# Save ranking table
ranking_output = f"{OUTPUT_DIR}/atils_run_average_ranking.csv"
df_run_rankings.to_csv(ranking_output, index=False)
print(f"\n✅ Saved: {ranking_output}")

# Highlight best run
best_run = df_run_rankings.iloc[0]
print("\n" + "="*100)
print("🏆 BEST PERFORMING RUN")
print("="*100)
print(f"   Run: {best_run['Run']}")
print(f"   Average Ranking: {best_run['Avg Ranking']}")
print(f"   Median Ranking: {best_run['Median Ranking']}")
print(f"   Times Ranked #1: {int(best_run['Rank #1 Count'])} / {int(best_run['Total Instances'])} ({best_run['Rank #1 Count']/best_run['Total Instances']*100:.1f}%)")
print(f"   Times in Top 3: {int(best_run['Top 3 Count'])} / {int(best_run['Total Instances'])} ({best_run['Top 3 Count']/best_run['Total Instances']*100:.1f}%)")
print(f"   Std Dev: {best_run['Std Dev']} (Lower = More Consistent)")
print("="*100)

# Generate text report
report_output = f"{OUTPUT_DIR}/atils_run_ranking_report.txt"
with open(report_output, 'w', encoding='utf-8') as f:
    f.write("="*100 + "\n")
    f.write(f"COMPREHENSIVE RUN RANKING REPORT - ATILS Dataset\n")
    f.write(f"Data Source: {DATA_FOLDER}\n")
    f.write(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
    f.write("="*100 + "\n\n")
    
    f.write("🏆 BEST PERFORMING RUN\n")
    f.write("-" * 80 + "\n")
    f.write(f"   Run: {best_run['Run']}\n")
    f.write(f"   Average Ranking: {best_run['Avg Ranking']}\n")
    f.write(f"   Median Ranking: {best_run['Median Ranking']}\n")
    f.write(f"   Best Ranking: #{int(best_run['Best Ranking'])}\n")
    f.write(f"   Worst Ranking: #{int(best_run['Worst Ranking'])}\n")
    f.write(f"   Std Dev: {best_run['Std Dev']}\n")
    f.write(f"   Times Ranked #1: {int(best_run['Rank #1 Count'])} / {int(best_run['Total Instances'])} ({best_run['Rank #1 Count']/best_run['Total Instances']*100:.1f}%)\n")
    f.write(f"   Times in Top 3: {int(best_run['Top 3 Count'])} / {int(best_run['Total Instances'])} ({best_run['Top 3 Count']/best_run['Total Instances']*100:.1f}%)\n\n")
    
    f.write("="*100 + "\n")
    f.write("AVERAGE RANKING - ALL RUNS\n")
    f.write("="*100 + "\n\n")
    f.write(df_run_rankings.to_string(index=False))
    f.write("\n\n")
    
    f.write("="*100 + "\n")
    f.write("ANALYSIS & INSIGHTS\n")
    f.write("="*100 + "\n\n")
    
    f.write("📊 Overall Statistics:\n")
    f.write(f"   • Total Runs Analyzed: {len(df_run_rankings)}\n")
    f.write(f"   • Total Instances: {int(df_run_rankings['Total Instances'].iloc[0])}\n")
    f.write(f"   • Best Average Ranking: {df_run_rankings['Avg Ranking'].min()}\n")
    f.write(f"   • Worst Average Ranking: {df_run_rankings['Avg Ranking'].max()}\n")
    f.write(f"   • Mean Average Ranking: {df_run_rankings['Avg Ranking'].mean():.2f}\n\n")
    
    f.write("🏅 Top 3 Runs:\n")
    for idx, row in df_run_rankings.head(3).iterrows():
        emoji = '🥇' if row['Final Rank'] == 1 else '🥈' if row['Final Rank'] == 2 else '🥉'
        f.write(f"   {emoji} #{int(row['Final Rank'])} - {row['Run']}: Avg Ranking = {row['Avg Ranking']}\n")
    
    f.write("\n📉 Bottom 3 Runs:\n")
    for idx, row in df_run_rankings.tail(3).iterrows():
        f.write(f"   • #{int(row['Final Rank'])} - {row['Run']}: Avg Ranking = {row['Avg Ranking']}\n")
    
    f.write("\n" + "="*100 + "\n")

print(f"✅ Saved: {report_output}")

print("\n" + "="*100)
print("✨ PROCESS COMPLETED SUCCESSFULLY!")
print("="*100)
print(f"\nData Source: {DATA_FOLDER}")
print(f"Generated files in '{OUTPUT_DIR}/':")
print(f"  📄 atils_run_average_ranking.csv - Average ranking table for all runs")
print(f"  📄 atils_all_runs_costs.csv - All costs (instances x runs)")
print(f"  📄 atils_run_ranking_report.txt - Comprehensive text report")
print("="*100)

PYTHON_SCRIPT

echo ""
echo "============================================================================"
echo "✅ Script execution completed!"
echo "============================================================================"
echo ""
echo "📂 Output files are located in: $OUTPUT_DIR/"
echo ""
echo "To view the results:"
echo "  cat $OUTPUT_DIR/atils_run_ranking_report.txt"
echo "  open $OUTPUT_DIR/atils_run_average_ranking.csv"
echo ""
