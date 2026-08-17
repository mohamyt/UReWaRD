#!/usr/bin/env bash
#SBATCH --job-name=setup_sintel
#SBATCH --time=02:00:00
#SBATCH --output=%x_%j.out

# Converts MPI-Sintel training data to TFRecords.
#
# Download the dataset first (on the login node):
#   cd $WOODY
#   wget http://files.is.tue.mpg.de/sintel/MPI-Sintel-complete.zip
#   unzip MPI-Sintel-complete.zip -d sintel_raw/
#
# Expected structure after extraction:
#   sintel_raw/training/clean/<sequence>/<frame>.png
#   sintel_raw/training/flow/<sequence>/<frame>.flo
#
# Run once before fine-tuning on Sintel. Requires no GPU.

# ─── Configure these paths ───────────────────────────────────────────────────
WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"
ENV_PREFIX="${WOODY}/uflow"

DATA_DIR="${WOODY}/sintel_raw/training"  # path to extracted Sintel training split
OUT_DIR="${WOODY}/sintel_tfrecords"      # output directory

NUM_SHARDS=100

# ─── Environment ─────────────────────────────────────────────────────────────
module purge
module load python/3.12-conda
mkdir -p "${OUT_DIR}"

cd "${CODE}"

echo "=== Converting Sintel clean pass ==="
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python data_conversion/convert_sintel_to_tfrecords.py \
    --data_dir="${DATA_DIR}" \
    --output_dir="${OUT_DIR}/training/clean" \
    --num_shards=${NUM_SHARDS}

echo "=== Done: $(ls ${OUT_DIR}/training/clean | wc -l) shards ==="
