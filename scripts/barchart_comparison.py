#!/usr/bin/env python3
"""
Bar Chart Visualization for Algorithm Comparison
Compares two datasets across instances, split into 3 charts (per 10 instances)
Visual style matches the reference image provided
"""

import os
import sys
import csv
import numpy as np
import matplotlib
matplotlib.use('Agg')  # Non-interactive backend
import matplotlib.pyplot as plt
from pathlib import Path


def get_project_dir():
    """Get project directory from script location"""
    script_dir = Path(__file__).parent.resolve()
    return script_dir.parent


def load_csv_data(filepath):
    """Load data from CSV file"""
    data = {}
    with open(filepath, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            instance = row['Instance']
            data[instance] = {
                'best_cost': float(row['Best_Cost']),
                'average_cost': float(row['Average_Cost']),
                'std_dev': float(row['Std_Dev']),
                'run_count': int(row['Run_Count'])
            }
    return data


def find_csv_files(project_dir):
    """Find all final_results_analysis CSV files"""
    docs_dir = project_dir / "documents"
    files = []
    
    for f in sorted(docs_dir.glob("final_results_analysis_*.csv")):
        files.append(str(f))
    
    return files


def select_files_interactive(files):
    """Interactive file selection in terminal"""
    if len(files) < 2:
        print("❌ Need at least 2 CSV files to compare!")
        sys.exit(1)
    
    print("\n" + "="*60)
    print("📊 Bar Chart Comparison - File Selection")
    print("="*60)
    print("\nAvailable CSV files:\n")
    
    for i, f in enumerate(files, 1):
        filename = os.path.basename(f)
        print(f"  [{i}] {filename}")
    
    print()
    
    # Select first file
    while True:
        try:
            choice1 = input("> Select FIRST file (1-{}): ".format(len(files)))
            idx1 = int(choice1) - 1
            if 0 <= idx1 < len(files):
                break
            else:
                print("Invalid choice. Please try again.")
        except ValueError:
            print("Please enter a valid number.")
        except KeyboardInterrupt:
            print("\nCancelled.")
            sys.exit(0)
    
    # Select second file
    while True:
        try:
            choice2 = input("> Select SECOND file (1-{}): ".format(len(files)))
            idx2 = int(choice2) - 1
            if 0 <= idx2 < len(files) and idx2 != idx1:
                break
            elif idx2 == idx1:
                print("Please select a different file.")
            else:
                print("Invalid choice. Please try again.")
        except ValueError:
            print("Please enter a valid number.")
        except KeyboardInterrupt:
            print("\nCancelled.")
            sys.exit(0)
    
    return files[idx1], files[idx2]


def get_label_from_filename(filepath):
    """Extract a short label from filename"""
    filename = os.path.basename(filepath)
    # Remove prefix and suffix
    label = filename.replace("final_results_analysis_", "").replace(".csv", "")
    # Shorten the label
    label = label.replace("atils_log_one_run_", "")
    return label


def create_comparison_barchart(data1, data2, label1, label2, instances, title_suffix, output_path):
    """Create grouped bar chart comparing two datasets"""
    
    # Extract data for the specified instances
    costs1 = []
    costs2 = []
    valid_instances = []
    
    for inst in instances:
        if inst in data1 and inst in data2:
            costs1.append(data1[inst]['best_cost'])
            costs2.append(data2[inst]['best_cost'])
            valid_instances.append(inst)
    
    if not valid_instances:
        print(f"  ⚠️  No valid data for {title_suffix}")
        return None
    
    # Create figure with style matching reference
    fig, ax = plt.subplots(figsize=(12, 6))
    
    # Set background
    ax.set_facecolor('white')
    fig.patch.set_facecolor('white')
    
    # Bar settings
    x = np.arange(len(valid_instances))
    width = 0.35
    
    # Create bars with colors matching reference (blue and coral/salmon)
    bars1 = ax.bar(x - width/2, costs1, width, label=label1, 
                   color='#58a8df', edgecolor='#58a8df', linewidth=0.5)
    bars2 = ax.bar(x + width/2, costs2, width, label=label2,
                   color='#e66b60', edgecolor='#e66b60', linewidth=0.5)
    
    # Title and labels
    ax.set_title(
        f'Perbandingan Algoritma: {title_suffix}',
        fontsize=14,
        fontweight='bold',
        pad=15
    )
    ax.set_xlabel('Instance', fontsize=12, fontweight='bold')
    ax.set_ylabel('Cost', fontsize=12, fontweight='bold')
    
    # X-axis ticks
    ax.set_xticks(x)
    ax.set_xticklabels(valid_instances, fontsize=10)
    
    # Grid - horizontal only
    ax.yaxis.grid(True, linestyle='-', alpha=0.3, color='gray')
    ax.set_axisbelow(True)
    
    # Format y-axis with thousands separator
    ax.get_yaxis().set_major_formatter(
        plt.FuncFormatter(lambda x, p: format(int(x), ','))
    )
    
    # Legend - positioned like reference (upper left inside box)
    ax.legend(loc='upper left', fontsize=10, framealpha=0.9)
    
    # Add border around plot
    for spine in ax.spines.values():
        spine.set_visible(True)
        spine.set_color('black')
        spine.set_linewidth(1)
    
    # Tight layout
    plt.tight_layout()
    
    # Save figure
    plt.savefig(output_path, dpi=150, bbox_inches='tight', facecolor='white')
    print(f"  ✅ Saved: {os.path.basename(output_path)}")
    
    # Close plot
    plt.close(fig)
    
    return output_path


def main():
    project_dir = get_project_dir()
    
    # Find CSV files
    files = find_csv_files(project_dir)
    
    if not files:
        print("❌ No CSV files found in documents directory!")
        sys.exit(1)
    
    # Select files interactively
    file1, file2 = select_files_interactive(files)
    
    # Load data
    print("\n📂 Loading data...")
    data1 = load_csv_data(file1)
    data2 = load_csv_data(file2)
    
    label1 = get_label_from_filename(file1)
    label2 = get_label_from_filename(file2)
    
    print(f"  ✓ Loaded {label1}: {len(data1)} instances")
    print(f"  ✓ Loaded {label2}: {len(data2)} instances")
    
    # Define instance groups
    instance_groups = [
        ([f"i{i:02d}" for i in range(1, 11)], "Small (i01-i10)"),
        ([f"i{i:02d}" for i in range(11, 21)], "Medium (i11-i20)"),
        ([f"i{i:02d}" for i in range(21, 31)], "Large (i21-i30)")
    ]
    
    # Create output directory
    output_dir = project_dir / "documents"
    output_dir.mkdir(exist_ok=True)
    
    print("\n📈 Creating bar charts...")
    
    # Create charts for each group
    for instances, title_suffix in instance_groups:
        # Create filename based on instance range
        range_str = title_suffix.split('(')[1].replace(')', '').replace('-', '_to_')
        output_filename = f"barchart_comparison_{range_str}.png"
        output_path = output_dir / output_filename
        
        create_comparison_barchart(
            data1, data2, label1, label2,
            instances, title_suffix, str(output_path)
        )
    
    print("\n✅ All bar charts created successfully!")


if __name__ == "__main__":
    main()
