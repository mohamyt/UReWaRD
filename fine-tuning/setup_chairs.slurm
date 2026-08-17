#!/usr/bin/env bash
#SBATCH --job-name=setup_chairs
#SBATCH --time=02:00:00
#SBATCH --output=%x_%j.out

# Converts FlyingChairs training data to TFRecords.
#
# Download the dataset first (on the login node):
#   cd $WOODY
#   wget https://lmb.informatik.uni-freiburg.de/data/FlyingChairs/FlyingChairs.zip
#   unzip FlyingChairs.zip -d chairs_raw/
#
# Expected structure after extraction:
#   chairs_raw/FlyingChairs_release/data/<id>_img1.ppm  <id>_img2.ppm  <id>_flow.flo
#   chairs_raw/FlyingChairs_release/FlyingChairs_train_val.txt
#
# Run once before fine-tuning on FlyingChairs. Requires no GPU.

# ─── Configure these paths ───────────────────────────────────────────────────
WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"
ENV_PREFIX="${WOODY}/uflow"

DATA_DIR="${WOODY}/chairs_raw/FlyingChairs_release"  # path to extracted FlyingChairs
OUT_TRAIN="${WOODY}/flying_chairs_tfrecords/train"
OUT_TEST="${WOODY}/flying_chairs_tfrecords/test"

# ─── Environment ─────────────────────────────────────────────────────────────
module purge
module load python/3.12-conda
mkdir -p "${OUT_TRAIN}" "${OUT_TEST}"

cd "${CODE}"

echo "=== Converting FlyingChairs (train split) ==="
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python data_conversion/convert_flying_chairs_to_tfrecords.py \
    --data_dir="${DATA_DIR}/data" \
    --output_dir="${OUT_TRAIN}" \
    --train_split_file="${CODE}/uflow/files/chairs_train_val.txt" \
    --num_shards=1

echo "=== Converting FlyingChairs (test/val split) ==="
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python data_conversion/convert_flying_chairs_to_tfrecords.py \
    --data_dir="${DATA_DIR}/data" \
    --output_dir="${OUT_TEST}" \
    --train_split_file="${CODE}/uflow/files/chairs_train_val.txt" \
    --num_shards=1

echo "=== Done ==="
echo "Train: $(ls ${OUT_TRAIN} | wc -l) shards"
echo "Test:  $(ls ${OUT_TEST}  | wc -l) shards"
