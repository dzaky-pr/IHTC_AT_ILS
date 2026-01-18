#!/usr/bin/env python3
"""
Box Plot Visualization for Final Cost Distribution Across Runs per Instance
Visual style matches the reference images provided
"""

import os
import sys
import glob
import numpy as np
import matplotlib
matplotlib.use('Agg')  # Non-interactive backend
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from pathlib import Path


def get_project_dir():
    """Get project directory from script location"""
    script_dir = Path(__file__).parent.resolve()
    return script_dir.parent


def find_log_folders(project_dir):
    """Find all log folders with pattern atils_log_one_run_*"""
    folders = []
    
    # Find folders with prefix
    pattern = str(project_dir / "atils_log_one_run_*")
    for folder in sorted(glob.glob(pattern)):
        if os.path.isdir(folder):
            folders.append(folder)
    
    # Check for default folder without suffix
    default_folder = project_dir / "atils_log_one_run"
    if default_folder.exists() and default_folder.is_dir():
        folders.append(str(default_folder))
    
    return folders


def select_folder_interactive(folders):
    """Interactive folder selection in terminal"""
    if not folders:
        print("No log folders found!")
        sys.exit(1)
    
    print("\n" + "="*60)
    print("📊 Box Plot Visualization - Folder Selection")
    print("="*60)
    print("\nAvailable log folders:\n")
    
    for i, folder in enumerate(folders, 1):
        folder_name = os.path.basename(folder)
        print(f"  [{i}] {folder_name}")
    
    print()
    
    while True:
        try:
            choice = input("> Your choice (1-{}): ".format(len(folders)))
            idx = int(choice) - 1
            if 0 <= idx < len(folders):
                return folders[idx]
            else:
                print("Invalid choice. Please try again.")
        except ValueError:
            print("Please enter a valid number.")
        except KeyboardInterrupt:
            print("\nCancelled.")
            sys.exit(0)


def extract_final_costs(log_dir, instance, num_runs=11):
    """Extract final cost from each run for a given instance"""
    costs = []
    
    for run in range(1, num_runs + 1):
        filename = f"atils_log_{instance}_ATILS_{run}_atils.csv"
        filepath = os.path.join(log_dir, filename)
        
        if os.path.exists(filepath):
            try:
                with open(filepath, 'r') as f:
                    lines = f.readlines()
                    if len(lines) > 1:
                        # Get last line, extract best_soft (column 6, 0-indexed 5)
                        last_line = lines[-1].strip()
                        parts = last_line.split(',')
                        if len(parts) >= 6:
                            cost = float(parts[5])
                            costs.append(cost)
            except Exception as e:
                print(f"Warning: Error reading {filename}: {e}")
    
    return costs


def create_boxplot(log_dir, output_dir):
    """Create box plot visualization matching reference style"""
    folder_name = os.path.basename(log_dir)
    
    print(f"\n📈 Processing folder: {folder_name}")
    print("-" * 50)
    
    # Collect data for all instances
    instances = [f"i{i:02d}" for i in range(1, 31)]
    all_costs = []
    valid_instances = []
    
    for instance in instances:
        costs = extract_final_costs(log_dir, instance)
        if costs:
            all_costs.append(costs)
            valid_instances.append(instance)
            print(f"  ✓ {instance}: {len(costs)} runs, costs range [{min(costs):.0f} - {max(costs):.0f}]")
        else:
            print(f"  ✗ {instance}: No data found")
    
    if not all_costs:
        print("\n❌ No data found to visualize!")
        return None
    
    # Create figure with style matching reference
    fig, ax = plt.subplots(figsize=(16, 8))
    
    # Set background color
    ax.set_facecolor('white')
    fig.patch.set_facecolor('white')
    
    # Create box plot
    positions = list(range(1, len(valid_instances) + 1))
    
    # Box plot with specific styling matching reference
    bp = ax.boxplot(
        all_costs,
        positions=positions,
        widths=0.6,
        patch_artist=True,
        showmeans=True,
        meanprops=dict(
            marker='^',
            markerfacecolor='red',
            markeredgecolor='red',
            markersize=6
        ),
        medianprops=dict(
            color='blue',
            linewidth=1.5
        ),
        boxprops=dict(
            facecolor='white',
            edgecolor='black',
            linewidth=1
        ),
        whiskerprops=dict(
            color='black',
            linewidth=1
        ),
        capprops=dict(
            color='black',
            linewidth=1
        ),
        flierprops=dict(
            marker='D',  # Diamond marker for outliers
            markerfacecolor='red',
            markeredgecolor='red',
            markersize=5,
            alpha=0.8
        )
    )
    
    # Title and labels
    ax.set_title(
        'Final Cost Distribution Across 11 Runs per Instance',
        fontsize=14,
        fontweight='bold',
        pad=15
    )
    ax.set_xlabel('Instance', fontsize=12, fontweight='bold')
    ax.set_ylabel('Final Best Cost', fontsize=12, fontweight='bold')
    
    # X-axis ticks
    ax.set_xticks(positions)
    ax.set_xticklabels(valid_instances, rotation=45, ha='right', fontsize=9)
    
    # Grid - horizontal only, subtle
    ax.yaxis.grid(True, linestyle='-', alpha=0.3, color='gray')
    ax.set_axisbelow(True)
    
    # Format y-axis with thousands separator
    ax.get_yaxis().set_major_formatter(
        plt.FuncFormatter(lambda x, p: format(int(x), ','))
    )
    
    # Legend - matching reference style
    legend_elements = [
        mpatches.Patch(facecolor='white', edgecolor='black', label='IQR (Q1-Q3)'),
        plt.Line2D([0], [0], color='blue', linewidth=1.5, label='Median'),
        plt.Line2D([0], [0], marker='^', color='w', markerfacecolor='red',
                   markeredgecolor='red', markersize=8, label='Mean'),
        plt.Line2D([0], [0], marker='D', color='w', markerfacecolor='red',
                   markeredgecolor='red', markersize=6, label='Outliers')
    ]
    ax.legend(handles=legend_elements, loc='upper left', fontsize=9)
    
    # Tight layout
    plt.tight_layout()
    
    # Save figure
    output_filename = f"boxplot_{folder_name}.png"
    output_path = os.path.join(output_dir, output_filename)
    
    plt.savefig(output_path, dpi=150, bbox_inches='tight', facecolor='white')
    print(f"\n✅ Box plot saved to: {output_path}")
    
    # Close plot to free memory
    plt.close(fig)
    
    return output_path


def main():
    project_dir = get_project_dir()
    
    # Find log folders
    folders = find_log_folders(project_dir)
    
    if not folders:
        print("❌ No log folders found in project directory!")
        print(f"   Looking in: {project_dir}")
        sys.exit(1)
    
    # Select folder interactively
    selected_folder = select_folder_interactive(folders)
    print(f"\n✓ Selected: {os.path.basename(selected_folder)}")
    
    # Create output directory
    output_dir = project_dir / "documents"
    output_dir.mkdir(exist_ok=True)
    
    # Create box plot
    create_boxplot(selected_folder, str(output_dir))


if __name__ == "__main__":
    main()
