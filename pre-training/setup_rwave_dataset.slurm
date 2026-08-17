#!/usr/bin/env bash
#SBATCH --job-name=setup_rwave_dataset
#SBATCH --nodes=1 --ntasks=1 --cpus-per-task=16 --mem=64G
#SBATCH --time=12:00:00
#SBATCH --output=%x_%j.out

# Converts a zip of retinal wave scene directories into a TFRecord dataset
# ready for pre-training.
#
# Input zip structure expected:
#   <zip_root>/
#     <top_dir>/          (e.g. all_scenes_5k/)
#       0/  1/  2/  ...   (one directory per scene, containing sequential PNGs)
#
# Run once before pre-training. Requires no GPU.

# ─── Configure these paths ───────────────────────────────────────────────────
WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/pre-training"
ENV_PREFIX="${WOODY}/uflow"

SCENES_ZIP="${WOODY}/scenes_5kthreshold.zip"  # zip produced by the retinal wave simulator pipeline
TOP_DIR="all_scenes_5k"                        # top-level directory name inside the zip
OUT_DS="${WOODY}/rwave_5kthresh_dataset"       # output tfrecord dataset directory

# ─── Conversion settings ──────────────────────────────────────────────────────
NUM_SHARDS=256    # number of output .tfrecord shards
FRAME_STRIDE=4    # sample every Nth consecutive frame pair within each scene

# ─── Environment ─────────────────────────────────────────────────────────────
module purge
module load python/3.12-conda

set -e

echo "=== Extracting ${SCENES_ZIP} to TMPDIR ==="
echo "TMPDIR: ${TMPDIR}  available: $(df -h ${TMPDIR} | tail -1)"
DATA_DIR="${TMPDIR}/data"
mkdir -p "${DATA_DIR}"
unzip -q "${SCENES_ZIP}" -d "${DATA_DIR}"

INPUT_DIR="${DATA_DIR}/${TOP_DIR}"
echo "Scenes found: $(ls ${INPUT_DIR} | wc -l)"
echo "Sample frames in scene 0: $(ls ${INPUT_DIR}/0 | wc -l)"

echo "=== Converting to TFRecords ==="
rm -rf "${OUT_DS}"
mkdir -p "${OUT_DS}"

cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python -m uflow.misc.convert_image_folders_to_dataset \
    --input_dir="${INPUT_DIR}" \
    --output_dir="${OUT_DS}" \
    --num_shards=${NUM_SHARDS} \
    --frame_stride=${FRAME_STRIDE} \
    --overwrite

echo "=== Done ==="
echo "Dataset: ${OUT_DS}"
du -sh "${OUT_DS}"
