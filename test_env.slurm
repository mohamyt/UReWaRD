#!/usr/bin/env bash
#SBATCH --job-name=test_env
#SBATCH --gres=gpu:1
#SBATCH --cpus-per-task=4
#SBATCH --time=00:30:00
#SBATCH --output=/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/test_env_%j.out

# Tests that the uflow conda env (created from environment.yml on the login node)
# has all packages needed to run both pre-training and fine-tuning.

WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
ENV_PREFIX="${WOODY}/uflow"
CODE_PT="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/pre-training"
CODE_FT="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"

module purge
module load python/3.12-conda

echo "=== Env: ${ENV_PREFIX} ==="
conda run -p "${ENV_PREFIX}" python --version

echo "=== Import test: pre-training ==="
cd "${CODE_PT}"
conda run --no-capture-output -p "${ENV_PREFIX}" python import_test.py

echo "=== Import test: fine-tuning ==="
cd "${CODE_FT}"
conda run --no-capture-output -p "${ENV_PREFIX}" python import_test.py

echo "=== Environment test complete ==="
