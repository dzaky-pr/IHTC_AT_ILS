#!/bin/bash

# Konfigurasi Direktori Relative dari lokasi script (scripts/)
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$BASE_DIR")"
SOLUTIONS_DIR="$ROOT_DIR/solutions"
OPTIMIZED_DIR="$ROOT_DIR/solutions_one_run"
INSTANCE_DIR="$ROOT_DIR/ihtc2024_competition_instances"
BEST_COST_FILE="$ROOT_DIR/documents/best_cost_first_all.txt"
SECOND_COST_FILE="$ROOT_DIR/documents/best_cost_second_place.txt"
THIRD_COST_FILE="$ROOT_DIR/documents/best_cost_third_place.txt"
CLASSPATH="bin:json-20250107.jar"

# Fungsi untuk menampilkan opsi folder yang tersedia
show_available_folders() {
    echo "=============================================================================================="
    echo " Folder yang tersedia:"
    echo "=============================================================================================="
    local i=1
    declare -a folders
    
    # List semua folder dengan prefix atils_log_one_run
    for folder in "$ROOT_DIR"/atils_log_one_run*; do
        if [ -d "$folder" ]; then
            folders+=("$folder")
            local folder_name=$(basename "$folder")
            echo "  $i) $folder_name"
            ((i++))
        fi
    done
    
    # Jika tidak ada folder, exit
    if [ ${#folders[@]} -eq 0 ]; then
        echo "  ERROR: Tidak ada folder dengan prefix 'atils_log_one_run' ditemukan!"
        exit 1
    fi
    
    echo "=============================================================================================="
    echo ""
    
    # Simpan array untuk digunakan di fungsi caller
    echo "${folders[@]}"
}

# Tentukan LOG_DIR berdasarkan argument atau pilihan user
select_log_folder() {
    local choice=$1
    declare -a folders=()
    
    # List semua folder dengan prefix atils_log_one_run
    for folder in "$ROOT_DIR"/atils_log_one_run*; do
        if [ -d "$folder" ]; then
            folders+=("$folder")
        fi
    done
    
    # Jika tidak ada folder, exit
    if [ ${#folders[@]} -eq 0 ]; then
        echo "ERROR: Tidak ada folder dengan prefix 'atils_log_one_run' ditemukan!"
        exit 1
    fi
    
    # Jika ada argument, gunakan itu
    if [ -n "$choice" ]; then
        # Jika argument adalah angka
        if [[ "$choice" =~ ^[0-9]+$ ]]; then
            # Array dimulai dari 0, tetapi pilihan user dimulai dari 1
            local idx=$((choice - 1))
            if [ $idx -lt 0 ] || [ $idx -ge ${#folders[@]} ]; then
                echo "ERROR: Pilihan nomor tidak valid!"
                show_available_folders
                exit 1
            fi
            LOG_DIR="${folders[$idx]}"
        else
            # Coba match dengan nama folder
            for folder in "${folders[@]}"; do
                if [[ "$(basename "$folder")" == *"$choice"* ]]; then
                    LOG_DIR="$folder"
                    break
                fi
            done
            
            if [ -z "$LOG_DIR" ]; then
                echo "ERROR: Folder dengan suffix '$choice' tidak ditemukan!"
                show_available_folders
                exit 1
            fi
        fi
    else
        # Jika hanya 1 folder, gunakan itu; jika lebih, minta user memilih
        if [ ${#folders[@]} -eq 1 ]; then
            LOG_DIR="${folders[0]}"
        else
            show_available_folders
            read -p "Pilih nomor folder (1-${#folders[@]}): " user_choice
            
            if ! [[ "$user_choice" =~ ^[0-9]+$ ]] || [ "$user_choice" -lt 1 ] || [ "$user_choice" -gt ${#folders[@]} ]; then
                echo "ERROR: Pilihan tidak valid!"
                exit 1
            fi
            
            local idx=$((user_choice - 1))
            LOG_DIR="${folders[$idx]}"
        fi
    fi
    
    echo "Menggunakan folder: $(basename "$LOG_DIR")"
    echo ""
}

# Parse argument CLI
select_log_folder "$1"

# Fungsi untuk mendapatkan best known cost
get_best_cost() {
    local instance=$1
    if [[ -f "$BEST_COST_FILE" ]]; then
        grep "^$instance " "$BEST_COST_FILE" | awk '{print $2}' | tr -d '\r' | tr -d ' '
    fi
}

# Fungsi untuk mendapatkan second best cost
get_second_cost() {
    local instance=$1
    if [[ -f "$SECOND_COST_FILE" ]]; then
        grep "^$instance " "$SECOND_COST_FILE" | awk '{print $2}' | tr -d '\r' | tr -d ' '
    fi
}

# Fungsi untuk mendapatkan third best cost
get_third_cost() {
    local instance=$1
    if [[ -f "$THIRD_COST_FILE" ]]; then
        grep "^$instance " "$THIRD_COST_FILE" | awk '{print $2}' | tr -d '\r' | tr -d ' '
    fi
}

# Ambil initial cost dari log AT-ILS (baris INITIAL)
get_initial_cost_from_log() {
    local dataset=$1
    local log_file
    log_file=$(ls "$LOG_DIR/atils_log_${dataset}_"*_atils.csv 2>/dev/null | sort | tail -n 1)
    if [[ -n "$log_file" ]]; then
        # Kolom ke-4 = soft_cost dari baris INITIAL (iteration=0)
        grep ",INITIAL," "$log_file" | head -n 1 | awk -F',' '{gsub(/"/,"",$4); print int($4)}'
    fi
}

# Ambil best_soft dari log AT-ILS terbaru untuk dataset terkait
get_best_soft_from_log() {
    local dataset=$1
    local log_file
    log_file=$(ls "$LOG_DIR/atils_log_${dataset}_"*_atils.csv 2>/dev/null | sort | tail -n 1)
    if [[ -n "$log_file" ]]; then
        # Kolom ke-6 = best_soft; gunakan baris terakhir (FINAL atau ITERATION_END terakhir)
        tail -n 1 "$log_file" | awk -F',' '{gsub(/"/,"",$6); print int($6)}'
    fi
}

# Prepare documents dir and log file
DOCUMENTS_DIR="$ROOT_DIR/documents"
mkdir -p "$DOCUMENTS_DIR"
LOG="$DOCUMENTS_DIR/analyze_solutions_$(date +%Y%m%d_%H%M%S).txt"
exec > >(tee "$LOG") 2>&1

echo ""
echo "=============================================================================================="
echo " ANALISIS PERBANDINGAN COST: Solutions vs Solutions_One_Run vs Best Known"
echo "=============================================================================================="
echo " Usage: ./analyze_solutions.sh [nomor|suffix]"
echo "   - Tanpa argument: pilih dari list yang tersedia"
echo "   - Dengan nomor: ./analyze_solutions.sh 1 (untuk pilihan pertama)"
echo "   - Dengan suffix: ./analyze_solutions.sh i04_i24 (untuk atils_log_one_run_i04_i24)"
echo "=============================================================================================="
printf "%-5s %-8s %-11s %-11s %-11s %-11s %-11s %-11s %-9s %-10s %-10s %-10s\n" "No" "Dataset" "Initial" "Optimized" "First Best" "Second Best" "Third Best" "Improvement" "Imp %" "Gap1 %" "Gap2 %" "Gap3 %"
echo "----------------------------------------------------------------------------------------------"

total_init=0
total_opt=0
total_best=0
total_second=0
total_third=0
total_imp=0
count=0

# Fungsi untuk mendapatkan cost dari validator
get_cost() {
    local inst=$1
    local sol=$2
    # Jalankan validator dan grep output Total cost
    # Gunakan cd ke ROOT_DIR agar classpath terbaca dengan benar
    output=$(cd "$ROOT_DIR" && java -cp "$CLASSPATH" IHTP_Validator "$inst" "$sol" 2>&1)
    cost=$(echo "$output" | grep "Total cost =" | awk -F'=' '{print $2}' | tr -d ' ')
    echo "$cost"
}

# Loop untuk dataset i01 sampai i30
for i in {1..30}; do
    # Format angka jadi 2 digit (01, 02, ..., 30)
    id=$(printf "%02d" $i)
    dataset="i$id"
    
    inst_file="$INSTANCE_DIR/$dataset.json"
    
    # Cari file optimized terbaru (ambil yang terakhir secara alfabetikal/tanggal)
    # Pola: solution_i01_*.json
    opt_file=$(ls "$OPTIMIZED_DIR/solution_${dataset}_"*.json 2>/dev/null | sort | tail -n 1)

    # Cek kelengkapan file (hanya perlu instance dan optimized file)
    if [[ -f "$inst_file" && -f "$opt_file" ]]; then
        # Ambil cost initial dari log AT-ILS (baris INITIAL)
        cost_init=$(get_initial_cost_from_log "$dataset")
        if [[ -z "$cost_init" ]]; then
            cost_init="-"
        fi
        
        # Ambil cost optimized dari log AT-ILS (best_soft)
        cost_opt=$(get_best_soft_from_log "$dataset")
        if [[ -z "$cost_opt" ]]; then
            # Fallback: validate the solution file
            cost_opt=$(get_cost "$inst_file" "$opt_file")
        fi
        if [[ -z "$cost_opt" ]]; then
            cost_opt="-"
        fi
        
        # Get best known cost
        best_cost=$(get_best_cost "$dataset")
        if [[ -z "$best_cost" ]]; then
            best_cost="-"
        fi
        
        # Get second best cost
        second_cost=$(get_second_cost "$dataset")
        if [[ -z "$second_cost" ]]; then
            second_cost="-"
        fi
        
        # Get third best cost
        third_cost=$(get_third_cost "$dataset")
        if [[ -z "$third_cost" ]]; then
            third_cost="-"
        fi
        
        # Validasi output angka
        if [[ "$cost_init" =~ ^[0-9]+$ ]] && [[ "$cost_opt" =~ ^[0-9]+$ ]]; then
            imp=$((cost_init - cost_opt))
            
            # Hitung persentase improvement (pakai awk untuk float)
            if [ "$cost_init" -ne 0 ]; then
                imp_pct=$(awk "BEGIN {printf \"%.2f\", ($imp / $cost_init) * 100}")
            else
                imp_pct="0.00"
            fi
            
            # Hitung gap to best
            if [[ "$best_cost" =~ ^[0-9]+$ ]]; then
                if [ "$best_cost" -ne 0 ]; then
                    gap=$((cost_opt - best_cost))
                    gap_pct=$(awk "BEGIN {printf \"%.2f\", ($gap / $best_cost) * 100}")
                else
                    gap_pct="0.00"
                fi
            else
                gap_pct="-"
            fi
            
            # Hitung gap to second
            if [[ "$second_cost" =~ ^[0-9]+$ ]]; then
                if [ "$second_cost" -ne 0 ]; then
                    gap2=$((cost_opt - second_cost))
                    gap2_pct=$(awk "BEGIN {printf \"%.2f\", ($gap2 / $second_cost) * 100}")
                else
                    gap2_pct="0.00"
                fi
            else
                gap2_pct="-"
            fi
            
            # Hitung gap to third
            if [[ "$third_cost" =~ ^[0-9]+$ ]]; then
                if [ "$third_cost" -ne 0 ]; then
                    gap3=$((cost_opt - third_cost))
                    gap3_pct=$(awk "BEGIN {printf \"%.2f\", ($gap3 / $third_cost) * 100}")
                else
                    gap3_pct="0.00"
                fi
            else
                gap3_pct="-"
            fi
            
            # Print baris tabel
            printf "%-5s %-8s %-11d %-11d %-11s %-11s %-11s %-11d %-8s%% %-9s%% %-9s%% %-9s%%\n" "$i" "$dataset" "$cost_init" "$cost_opt" "$best_cost" "$second_cost" "$third_cost" "$imp" "$imp_pct" "$gap_pct" "$gap2_pct" "$gap3_pct"
            
            # Akumulasi total
            total_init=$((total_init + cost_init))
            total_opt=$((total_opt + cost_opt))
            total_imp=$((total_imp + imp))
            if [[ "$best_cost" =~ ^[0-9]+$ ]]; then
                total_best=$((total_best + best_cost))
            fi
            if [[ "$second_cost" =~ ^[0-9]+$ ]]; then
                total_second=$((total_second + second_cost))
            fi
            if [[ "$third_cost" =~ ^[0-9]+$ ]]; then
                total_third=$((total_third + third_cost))
            fi
            ((count++))
        else
            printf "%-5s %-8s %-11s %-11s %-11s %-11s %-11s %-11s %-9s %-10s %-10s %-10s\n" "$i" "$dataset" "ERROR" "ERROR" "$best_cost" "$second_cost" "$third_cost" "-" "-" "-" "-" "-"
        fi
    else
        # Skip silent atau print info jika mau
        # echo "Skipping $dataset (Files not found)"
        continue
    fi
done

echo "----------------------------------------------------------------------------------------------"

# Final Stats
if [ $total_init -ne 0 ]; then
    avg_imp_pct=$(awk "BEGIN {printf \"%.2f\", ($total_imp / $total_init) * 100}")
else
    avg_imp_pct="0.00"
fi

if [ $total_best -ne 0 ]; then
    total_gap=$((total_opt - total_best))
    avg_gap_pct=$(awk "BEGIN {printf \"%.2f\", ($total_gap / $total_best) * 100}")
else
    avg_gap_pct="-"
fi

if [ $total_second -ne 0 ]; then
    total_gap2=$((total_opt - total_second))
    avg_gap2_pct=$(awk "BEGIN {printf \"%.2f\", ($total_gap2 / $total_second) * 100}")
else
    avg_gap2_pct="-"
fi

if [ $total_third -ne 0 ]; then
    total_gap3=$((total_opt - total_third))
    avg_gap3_pct=$(awk "BEGIN {printf \"%.2f\", ($total_gap3 / $total_third) * 100}")
else
    avg_gap3_pct="-"
fi

printf "%-5s %-8s %-11d %-11d %-11d %-11d %-11d %-11d %-8s%% %-9s%% %-9s%% %-9s%%\n" "TOT" "ALL ($count)" "$total_init" "$total_opt" "$total_best" "$total_second" "$total_third" "$total_imp" "$avg_imp_pct" "$avg_gap_pct" "$avg_gap2_pct" "$avg_gap3_pct"
echo "=============================================================================================="
