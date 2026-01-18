#!/usr/bin/env python3
"""
Progress Cost Line Chart Visualization
Compares optimization progress between two runs over time
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


def load_progress_data(filepath):
    """Load progress data from CSV file, extracting best_soft over time"""
    times = []
    costs = []
    
    with open(filepath, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            time_ms = int(row['time_ms'])
            best_soft = float(row['best_soft'])
            times.append(time_ms)
            costs.append(best_soft)
    
    return times, costs


def find_log_folders(project_dir):
    """Find all log folders"""
    import glob
    folders = []
    
    pattern = str(project_dir / "atils_log_one_run_*")
    for folder in sorted(glob.glob(pattern)):
        if os.path.isdir(folder):
            folders.append(folder)
    
    # Check for default folder
    default_folder = project_dir / "atils_log_one_run"
    if default_folder.exists() and default_folder.is_dir():
        folders.append(str(default_folder))
    
    return folders


def find_log_files(folder):
    """Find all log CSV files in a folder"""
    import glob
    files = []
    
    pattern = os.path.join(folder, "atils_log_*_atils.csv")
    for f in sorted(glob.glob(pattern)):
        files.append(f)
    
    return files


def select_files_interactive(project_dir):
    """Interactive file selection for comparison"""
    folders = find_log_folders(project_dir)
    
    if not folders:
        print("❌ No log folders found!")
        sys.exit(1)
    
    print("\n" + "="*60)
    print("📊 Progress Cost Comparison - File Selection")
    print("="*60)
    
    # Select first folder and file
    print("\n--- Select FIRST file ---")
    print("\nAvailable folders:\n")
    for i, folder in enumerate(folders, 1):
        print(f"  [{i}] {os.path.basename(folder)}")
    
    while True:
        try:
            choice = input("\n> Select folder (1-{}): ".format(len(folders)))
            idx = int(choice) - 1
            if 0 <= idx < len(folders):
                folder1 = folders[idx]
                break
            print("Invalid choice.")
        except ValueError:
            print("Please enter a valid number.")
        except KeyboardInterrupt:
            print("\nCancelled.")
            sys.exit(0)
    
    files1 = find_log_files(folder1)
    print(f"\nFiles in {os.path.basename(folder1)} ({len(files1)} files)")
    
    # Show instance options
    instances = sorted(set([os.path.basename(f).split('_')[2] for f in files1]))
    print("\nAvailable instances:", ', '.join(instances))
    
    instance = input("> Enter instance (e.g., i30): ").strip()
    
    # Find matching files for this instance
    instance_files = [f for f in files1 if f"_{instance}_" in f]
    if not instance_files:
        print(f"❌ No files found for instance {instance}")
        sys.exit(1)
    
    print(f"\nAvailable runs for {instance}:\n")
    for i, f in enumerate(instance_files, 1):
        print(f"  [{i}] {os.path.basename(f)}")
    
    while True:
        try:
            choice = input("\n> Select run (1-{}): ".format(len(instance_files)))
            idx = int(choice) - 1
            if 0 <= idx < len(instance_files):
                file1 = instance_files[idx]
                break
            print("Invalid choice.")
        except ValueError:
            print("Please enter a valid number.")
    
    # Select second file
    print("\n--- Select SECOND file ---")
    print("\nAvailable folders:\n")
    for i, folder in enumerate(folders, 1):
        print(f"  [{i}] {os.path.basename(folder)}")
    
    while True:
        try:
            choice = input("\n> Select folder (1-{}): ".format(len(folders)))
            idx = int(choice) - 1
            if 0 <= idx < len(folders):
                folder2 = folders[idx]
                break
            print("Invalid choice.")
        except ValueError:
            print("Please enter a valid number.")
    
    files2 = find_log_files(folder2)
    instance_files2 = [f for f in files2 if f"_{instance}_" in f]
    
    if not instance_files2:
        print(f"❌ No files found for instance {instance} in {os.path.basename(folder2)}")
        sys.exit(1)
    
    print(f"\nAvailable runs for {instance}:\n")
    for i, f in enumerate(instance_files2, 1):
        print(f"  [{i}] {os.path.basename(f)}")
    
    while True:
        try:
            choice = input("\n> Select run (1-{}): ".format(len(instance_files2)))
            idx = int(choice) - 1
            if 0 <= idx < len(instance_files2):
                file2 = instance_files2[idx]
                break
            print("Invalid choice.")
        except ValueError:
            print("Please enter a valid number.")
    
    return file1, file2, instance


def create_progress_chart(file1, file2, instance, output_path):
    """Create progress cost line chart"""
    
    print("\n📂 Loading data...")
    times1, costs1 = load_progress_data(file1)
    times2, costs2 = load_progress_data(file2)
    
    label1 = os.path.basename(os.path.dirname(file1)).replace("atils_log_one_run_", "")
    label2 = os.path.basename(os.path.dirname(file2)).replace("atils_log_one_run_", "")
    
    print(f"  ✓ Loaded {label1}: {len(times1)} data points")
    print(f"  ✓ Loaded {label2}: {len(times2)} data points")
    
    # Create figure with style matching reference
    fig, ax = plt.subplots(figsize=(14, 6))
    
    # Set background
    ax.set_facecolor('white')
    fig.patch.set_facecolor('white')
    
    # Plot lines with colors matching reference
    ax.plot(times1, costs1, color='#e66b60', linewidth=1.2, label=label1, alpha=0.9)
    ax.plot(times2, costs2, color='#58a8df', linewidth=1.2, label=label2, alpha=0.9)
    
    # Title and labels
    ax.set_title(
        f'Perbandingan Progress Cost: {label1} vs {label2} (Instance {instance})',
        fontsize=14,
        fontweight='bold',
        pad=15
    )
    ax.set_xlabel('Time (ms)', fontsize=12, fontweight='bold')
    ax.set_ylabel('Cost', fontsize=12, fontweight='bold')
    
    # Grid
    ax.yaxis.grid(True, linestyle='-', alpha=0.3, color='gray')
    ax.xaxis.grid(True, linestyle='-', alpha=0.3, color='gray')
    ax.set_axisbelow(True)
    
    # Format axes with thousands separator
    ax.get_yaxis().set_major_formatter(
        plt.FuncFormatter(lambda x, p: format(int(x), ','))
    )
    ax.get_xaxis().set_major_formatter(
        plt.FuncFormatter(lambda x, p: format(int(x), ','))
    )
    
    # Legend - positioned like reference (upper right)
    ax.legend(loc='upper right', fontsize=10, framealpha=0.9)
    
    # Add border around plot
    for spine in ax.spines.values():
        spine.set_visible(True)
        spine.set_color('black')
        spine.set_linewidth(1)
    
    # Tight layout
    plt.tight_layout()
    
    # Save figure
    plt.savefig(output_path, dpi=150, bbox_inches='tight', facecolor='white')
    print(f"\n✅ Progress chart saved to: {output_path}")
    
    # Close plot
    plt.close(fig)
    
    return output_path


def main():
    project_dir = get_project_dir()
    
    # Select files interactively
    file1, file2, instance = select_files_interactive(project_dir)
    
    print(f"\n✓ File 1: {os.path.basename(file1)}")
    print(f"✓ File 2: {os.path.basename(file2)}")
    
    # Create output directory
    output_dir = project_dir / "documents"
    output_dir.mkdir(exist_ok=True)
    
    # Generate output filename
    label1 = os.path.basename(os.path.dirname(file1)).replace("atils_log_one_run_", "")
    label2 = os.path.basename(os.path.dirname(file2)).replace("atils_log_one_run_", "")
    output_filename = f"progress_comparison_{instance}_{label1}_vs_{label2}.png"
    output_path = output_dir / output_filename
    
    # Create chart
    create_progress_chart(file1, file2, instance, str(output_path))


if __name__ == "__main__":
    main()
