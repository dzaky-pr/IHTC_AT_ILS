#!/usr/bin/env python3
"""
Dual Progress Chart - Comparing Hill Climbing vs ATILS
Creates two stacked subplots showing optimization progress over time
"""

import os
import sys
import csv
import numpy as np
import matplotlib
matplotlib.use('Agg')  # Non-interactive backend
import matplotlib.pyplot as plt
from pathlib import Path


def load_hill_climbing_data(filepath):
    """Load Hill Climbing data - time in seconds, best_cost"""
    times = []
    costs = []
    
    with open(filepath, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            time_sec = float(row['time_sec'])
            best_cost = float(row['best_cost'])
            times.append(time_sec)
            costs.append(best_cost)
    
    return times, costs


def load_atils_data(filepath):
    """Load ATILS data - time in ms (convert to seconds), best_soft"""
    times = []
    costs = []
    
    with open(filepath, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            time_ms = float(row['time_ms'])
            time_sec = time_ms / 1000.0  # Convert to seconds
            best_soft = float(row['best_soft'])
            times.append(time_sec)
            costs.append(best_soft)
    
    return times, costs


def create_dual_chart(hc_file, atils_file, output_path):
    """Create dual stacked chart comparing Hill Climbing and ATILS"""
    
    print("\n📂 Loading data...")
    hc_times, hc_costs = load_hill_climbing_data(hc_file)
    atils_times, atils_costs = load_atils_data(atils_file)
    
    print(f"  ✓ Hill Climbing: {len(hc_times)} data points, max time: {max(hc_times):.1f}s")
    print(f"  ✓ ATILS: {len(atils_times)} data points, max time: {max(atils_times):.1f}s")
    
    # Create figure with two stacked subplots
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 10))
    
    # Add main title
    fig.suptitle('Perbandingan Konvergensi: Hill Climbing vs AT-ILS', 
                 fontsize=14, fontweight='bold', y=0.995)
    
    # Set background
    fig.patch.set_facecolor('white')
    
    # ==================== Plot 1: Hill Climbing ====================
    ax1.set_facecolor('white')
    ax1.plot(hc_times, hc_costs, color='#4A90D9', linewidth=1.2, label='Current Cost')
    
    # Title and labels
    ax1.set_title(
        'Hill Climbing (i30)',
        fontsize=12,
        fontweight='bold',
        pad=10
    )
    ax1.set_xlabel('Time (seconds)', fontsize=10)
    ax1.set_ylabel('Cost', fontsize=10)
    
    # Grid
    ax1.yaxis.grid(True, linestyle='-', alpha=0.3, color='gray')
    ax1.xaxis.grid(True, linestyle='-', alpha=0.3, color='gray')
    ax1.set_axisbelow(True)
    
    # Format y-axis
    ax1.get_yaxis().set_major_formatter(
        plt.FuncFormatter(lambda x, p: format(int(x), ','))
    )
    
    # Legend
    ax1.legend(loc='upper right', fontsize=9, framealpha=0.9)
    
    # Border
    for spine in ax1.spines.values():
        spine.set_visible(True)
        spine.set_color('black')
        spine.set_linewidth(1)
    
    # ==================== Plot 2: ATILS ====================
    ax2.set_facecolor('white')
    ax2.plot(atils_times, atils_costs, color='#4A90D9', linewidth=1.2, label='Current Cost')
    
    # Title and labels
    ax2.set_title(
        'AT-ILS (i30)',
        fontsize=12,
        fontweight='bold',
        pad=10
    )
    ax2.set_xlabel('Time (seconds)', fontsize=10)
    ax2.set_ylabel('Cost', fontsize=10)
    
    # Grid
    ax2.yaxis.grid(True, linestyle='-', alpha=0.3, color='gray')
    ax2.xaxis.grid(True, linestyle='-', alpha=0.3, color='gray')
    ax2.set_axisbelow(True)
    
    # Format y-axis
    ax2.get_yaxis().set_major_formatter(
        plt.FuncFormatter(lambda x, p: format(int(x), ','))
    )
    
    # Legend
    ax2.legend(loc='upper right', fontsize=9, framealpha=0.9)
    
    # Border
    for spine in ax2.spines.values():
        spine.set_visible(True)
        spine.set_color('black')
        spine.set_linewidth(1)
    
    # Tight layout with spacing
    plt.tight_layout(h_pad=3.0)
    
    # Save figure
    plt.savefig(output_path, dpi=150, bbox_inches='tight', facecolor='white')
    print(f"\n✅ Chart saved to: {output_path}")
    
    # Close plot
    plt.close(fig)
    
    return output_path


def main():
    project_dir = Path(__file__).parent.parent.resolve()
    
    # Define file paths
    hc_file = project_dir / "documents" / "hill_climbing.csv"
    atils_file = project_dir / "atils_log_one_run_11_run" / "atils_log_i30_ATILS_5_atils.csv"
    
    if not hc_file.exists():
        print(f"❌ Hill Climbing file not found: {hc_file}")
        sys.exit(1)
    
    if not atils_file.exists():
        print(f"❌ ATILS file not found: {atils_file}")
        sys.exit(1)
    
    # Output path
    output_dir = project_dir / "documents"
    output_dir.mkdir(exist_ok=True)
    output_path = output_dir / "dual_progress_hc_vs_atils_i30.png"
    
    # Create chart
    create_dual_chart(str(hc_file), str(atils_file), str(output_path))


if __name__ == "__main__":
    main()
