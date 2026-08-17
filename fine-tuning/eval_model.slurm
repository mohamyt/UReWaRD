#!/usr/bin/env bash
#SBATCH --job-name=eval_model
#SBATCH --gres=gpu:1
#SBATCH --time=04:00:00
#SBATCH --output=%x_%j.out

# ─── Configure these paths ───────────────────────────────────────────────────
WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"
ENV_PREFIX="${WOODY}/uflow"

# Required: set before submitting
LABEL="my_model"                                         # short name for the CSV row
CKPT_DIR="${WOODY}/checkpoints/<checkpoint_dir>"         # checkpoint directory to evaluate

# Optional: leave empty to skip that dataset
SINTEL_TF="/home/hpc/iwi9/iwi9120h/UFReWaRD/sintel_tfrecords/training/clean"
CHAIRS_TF="${WOODY}/flying_chairs_tfrecords/test"
KITTI_TF="${WOODY}/kitti2015_tfrecords/training"

OUTPUT_CSV="${CODE}/eval_results.csv"
HEIGHT=256
WIDTH=256

# ─── Environment ─────────────────────────────────────────────────────────────
module purge
module load python/3.12-conda

cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python eval_model.py \
    --label="${LABEL}" \
    --checkpoint_dir="${CKPT_DIR}" \
    --output_csv="${OUTPUT_CSV}" \
    --gin_config="${CODE}/standard.gin" \
    --sintel_dir="${SINTEL_TF}" \
    --chairs_dir="${CHAIRS_TF}" \
    --kitti_dir="${KITTI_TF}" \
    --height=${HEIGHT} \
    --width=${WIDTH}

echo "=== Eval done. Results appended to ${OUTPUT_CSV} ==="
