#!/bin/bash
# Check which solutions are actually feasible vs infeasible
# This script lives in scripts/ and uses project-root relative paths

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CLASSPATH="$PROJECT_ROOT/bin:$PROJECT_ROOT/json-20250107.jar:$PROJECT_ROOT"

# Prepare documents dir and capture log
DOCUMENTS_DIR="$PROJECT_ROOT/documents"
mkdir -p "$DOCUMENTS_DIR"
LOG="$DOCUMENTS_DIR/check_all_solutions_feasibility_$(date +%Y%m%d_%H%M%S).txt"
exec > >(tee "$LOG") 2>&1

echo "========================================="
echo "Checking ALL solutions in $PROJECT_ROOT/solutions_one_run/"
echo "========================================="
echo ""

SOLUTIONS_DIR="$PROJECT_ROOT/solutions_one_run"
INSTANCES_DIR="$PROJECT_ROOT/ihtc2024_competition_instances"

FEASIBLE=()
INFEASIBLE=()

for i in $(seq -f "%02g" 1 30); do
  INST="i${i}"
  SOL_FILE="$SOLUTIONS_DIR/solution_${INST}_ATILS_1.json"
  INSTANCE_FILE="$INSTANCES_DIR/${INST}.json"
  
  if [ ! -f "$SOL_FILE" ]; then
    echo "⊘ ${INST}: Solution file not found: $SOL_FILE"
    continue
  fi
  
  # Run validator from project root so classpath is correct
  OUTPUT=$(cd "$PROJECT_ROOT" && java -cp "$CLASSPATH" IHTP_Validator "$INSTANCE_FILE" "$SOL_FILE" false 2>&1)
  VIOLATIONS=$(echo "$OUTPUT" | grep "Total violations" | grep -oE "[0-9]+" | tail -1)
  
  if [ "$VIOLATIONS" = "0" ]; then
    FEASIBLE+=("$INST")
    echo "✅ ${INST}: FEASIBLE (violations = 0)"
  else
    INFEASIBLE+=("$INST")
    echo "❌ ${INST}: INFEASIBLE (violations = ${VIOLATIONS})"
  fi
done

echo ""
echo "========================================="
echo "Summary"
echo "========================================="
echo ""
echo "FEASIBLE (${#FEASIBLE[@]}): ${FEASIBLE[@]}"
echo ""
echo "INFEASIBLE (${#INFEASIBLE[@]}): ${INFEASIBLE[@]}"
echo ""

if [ ${#INFEASIBLE[@]} -gt 0 ]; then
  echo "⚠️  WARNING: ${#INFEASIBLE[@]} solutions are INFEASIBLE!"
  echo "These need to be re-run with the fixed code:"
  for inst in "${INFEASIBLE[@]}"; do
    echo "  - $inst"
  done
else
  echo "✅ All solutions are FEASIBLE! No re-run needed."
fi
