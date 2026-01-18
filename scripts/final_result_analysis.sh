#!/bin/bash

# Script untuk menganalisis hasil final dari 11 runs untuk setiap instance
# Output: CSV dengan Instance, Best Cost, Average Cost, dan Std Dev

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Fungsi untuk memilih folder yang akan dianalisis
select_log_folder() {
    local folders=()
    local folder_names=()

    echo "" >&2
    echo "Scanning for log folders..." >&2
    echo "" >&2

    # Cari semua folder dengan prefix atils_log_one_run_
    while IFS= read -r folder; do
        if [ -d "$folder" ]; then
            folders+=("$folder")
            folder_names+=("$(basename "$folder")")
        fi
    done < <(find "$PROJECT_DIR" -maxdepth 1 -type d -name "atils_log_one_run_*" | sort)

    # Jika ada folder tanpa suffix (default)
    if [ -d "$PROJECT_DIR/atils_log_one_run" ]; then
        folders+=("$PROJECT_DIR/atils_log_one_run")
        folder_names+=("atils_log_one_run")
    fi

    if [ ${#folders[@]} -eq 0 ]; then
        echo "No log folders found!" >&2
        exit 1
    fi

    # Gunakan fzf jika tersedia, jika tidak gunakan select
    if command -v fzf &> /dev/null; then
        echo "Select a folder:" >&2
        local selected=$(printf '%s\n' "${folder_names[@]}" | fzf --preview "echo 'Files: '" --preview-window=top:2)
        if [ -z "$selected" ]; then
            echo "No folder selected!" >&2
            exit 1
        fi

        # Cari folder yang dipilih
        for i in "${!folder_names[@]}"; do
            if [ "${folder_names[$i]}" = "$selected" ]; then
                SELECTED_LOG_DIR="${folders[$i]}"
                return
            fi
        done
    else
        # Fallback: gunakan select menu
        echo "Select a folder:" >&2
        PS3=$'\n> Your choice: '
        select folder_name in "${folder_names[@]}"; do
            if [ -z "$folder_name" ]; then
                echo "Invalid choice!" >&2
                exit 1
            fi

            # Cari folder yang dipilih
            for i in "${!folder_names[@]}"; do
                if [ "${folder_names[$i]}" = "$folder_name" ]; then
                    SELECTED_LOG_DIR="${folders[$i]}"
                    return
                fi
            done
        done
    fi
}

# Pilih folder
select_log_folder
LOG_DIR="$SELECTED_LOG_DIR"
FOLDER_NAME=$(basename "$LOG_DIR")

# Buat direktori documents jika belum ada
DOCS_DIR="$PROJECT_DIR/documents"
mkdir -p "$DOCS_DIR"

OUTPUT_FILE="$DOCS_DIR/final_results_analysis_${FOLDER_NAME}.csv"

# Fungsi untuk menghitung statistik
calculate_stats() {
    local instance=$1
    local costs=()
    local run_count=0
    
    # Membaca cost dari setiap run (1-11)
    for run in {1..11}; do
        file="${LOG_DIR}/atils_log_${instance}_ATILS_${run}_atils.csv"
        
        if [ -f "$file" ]; then
            # Ambil nilai best_soft dari baris terakhir (FINAL)
            cost=$(tail -1 "$file" | awk -F',' '{print $6}')
            
            # Hapus trailing .00 jika ada
            cost=$(echo "$cost" | sed 's/\.00$//')
            
            if [ ! -z "$cost" ]; then
                costs+=($cost)
                ((run_count++))
            fi
        fi
    done
    
    # Jika tidak ada data untuk instance ini, skip
    if [ ${#costs[@]} -eq 0 ]; then
        return
    fi
    
    # Hitung best cost (minimum)
    best_cost=${costs[0]}
    for cost in "${costs[@]}"; do
        if (( $(echo "$cost < $best_cost" | bc -l) )); then
            best_cost=$cost
        fi
    done
    
    # Hitung average
    sum=0
    for cost in "${costs[@]}"; do
        sum=$(echo "$sum + $cost" | bc -l)
    done
    avg_cost=$(echo "scale=2; $sum / ${#costs[@]}" | bc -l)
    
    # Hitung standard deviation
    sum_sq_diff=0
    for cost in "${costs[@]}"; do
        diff=$(echo "$cost - $avg_cost" | bc -l)
        sq_diff=$(echo "$diff * $diff" | bc -l)
        sum_sq_diff=$(echo "$sum_sq_diff + $sq_diff" | bc -l)
    done
    variance=$(echo "scale=2; $sum_sq_diff / ${#costs[@]}" | bc -l)
    std_dev=$(echo "scale=2; sqrt($variance)" | bc -l)
    
    # Format avg dan std untuk 2 decimal places
    avg_cost=$(printf "%.2f" $avg_cost)
    std_dev=$(printf "%.2f" $std_dev)
    
    # Output CSV row
    echo "${instance},${best_cost},${avg_cost},${std_dev},${run_count}"
}

echo ""
echo "Scanning for log folders..."
echo ""

# Buat header CSV
echo "Instance,Best_Cost,Average_Cost,Std_Dev,Run_Count" > "$OUTPUT_FILE"

# Process setiap instance dari i01 sampai i30
for i in {1..30}; do
    # Format dengan zero padding (01, 02, ..., 10, 11, ...)
    instance=$(printf "i%02d" $i)
    echo "Processing ${instance}..."
    calculate_stats "$instance" >> "$OUTPUT_FILE"
done

echo ""
echo "Analysis complete!"
echo "Results saved to: $OUTPUT_FILE"
echo ""
echo "Summary:"
wc -l "$OUTPUT_FILE"
echo ""
echo "First 5 rows:"
head -6 "$OUTPUT_FILE"
