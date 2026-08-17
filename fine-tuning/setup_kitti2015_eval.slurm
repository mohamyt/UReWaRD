#!/usr/bin/env bash
#SBATCH --job-name=setup_kitti2015_eval
#SBATCH --time=04:00:00
#SBATCH --output=%x_%j.out

# Converts KITTI 2015 optical flow data to TFRecords for evaluation.
# This dataset includes ground-truth flow and is used with infer_kitti2.py.
#
# Download the dataset first (on the login node):
#   cd $WOODY
#   wget -c "https://s3.eu-central-1.amazonaws.com/avg-kitti/data_scene_flow.zip"
#   (~2 GB — includes training images + GT flow)
#
# Run once. Requires no GPU.

# ─── Configure these paths ───────────────────────────────────────────────────
WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"
ENV_PREFIX="${WOODY}/uflow"

ZIP="${WOODY}/data_scene_flow.zip"
SCRATCH="/scratch/iwi9120h/kitti2015"
OUT_TF="${WOODY}/kitti2015_tfrecords"

# ─── Environment ─────────────────────────────────────────────────────────────
module purge
module load python/3.12-conda
mkdir -p "${SCRATCH}" "${OUT_TF}"

echo "=== Copying zip to scratch and extracting ==="
cp "${WOODY}/data_scene_flow.zip" "${SCRATCH}/data_scene_flow.zip"
unzip -q "${SCRATCH}/data_scene_flow.zip" -d "${SCRATCH}/"
rm "${SCRATCH}/data_scene_flow.zip"

echo "=== Converting to TFRecords (training split with GT flow) ==="
cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python data_conversion/convert_KITTI_flow_to_tfrecords.py \
    --data_dir="${SCRATCH}" \
    --subdirs="training"

# Converter writes to {data_dir}_training-tfrecords
SRC="${SCRATCH}_training-tfrecords"
if [ -d "${SRC}" ]; then
    mv "${SRC}" "${OUT_TF}/training"
    echo "TFRecords -> ${OUT_TF}/training"
else
    echo "ERROR: expected output dir ${SRC} not found"
    exit 1
fi

echo "=== Cleaning scratch ==="
rm -rf "${SCRATCH}"

echo "=== Done: $(ls ${OUT_TF}/training | wc -l) records ==="
