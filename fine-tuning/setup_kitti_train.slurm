#!/usr/bin/env bash
#SBATCH --job-name=setup_kitti_train
#SBATCH --time=04:00:00
#SBATCH --output=%x_%j.out

# Converts KITTI multiview data to TFRecords for fine-tuning training.
# This is the unsupervised training set (no ground-truth flow).
#
# Download the dataset first (on the login node):
#   cd $WOODY
#   wget -c "https://s3.eu-central-1.amazonaws.com/avg-kitti/data_scene_flow_multiview.zip"
#   (~1.6 GB — training images only)
#
# Run once before fine-tuning on KITTI. Requires no GPU.
# For KITTI 2015 evaluation data (with GT flow), use setup_kitti2015_eval.slurm.

# ─── Configure these paths ───────────────────────────────────────────────────
WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"
ENV_PREFIX="${WOODY}/uflow"

ZIP="${WOODY}/data_scene_flow_multiview.zip"
SCRATCH="${TMPDIR}/kitti_multiview"
OUT_TF="${WOODY}/kitti_multiview_train_384x1280-tfrecords"

HEIGHT=384
WIDTH=1280

# ─── Environment ─────────────────────────────────────────────────────────────
module purge
module load python/3.12-conda
mkdir -p "${SCRATCH}"

echo "=== Extracting KITTI multiview zip ==="
unzip -q "${ZIP}" -d "${SCRATCH}/"
DATA_DIR="${SCRATCH}/data_scene_flow_multiview"

echo "=== Converting to TFRecords ==="
cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python data_conversion/convert_KITTI_multiview_to_tfrecords.py \
    --data_dir="${DATA_DIR}" \
    --height=${HEIGHT} --width=${WIDTH} \
    --entire_sequence=false

# Converter writes to {DATA_DIR}_train_{H}x{W}-tfrecords
SRC="${DATA_DIR}_train_${HEIGHT}x${WIDTH}-tfrecords"
mkdir -p "${OUT_TF}"
mv "${SRC}"/*.tfrecord "${OUT_TF}/"

echo "=== Done: $(ls ${OUT_TF} | wc -l) records -> ${OUT_TF} ==="
