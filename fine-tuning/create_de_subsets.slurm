#!/usr/bin/env bash
#SBATCH --job-name=create_de_subsets
#SBATCH --gres=gpu:1
#SBATCH --cpus-per-task=4
#SBATCH --time=04:00:00
#SBATCH --output=%x_%j.out

# Creates data-efficiency TFRecord subsets at 1/2/5/10/25/50% of each dataset.
# GPU slot required by cluster policy even though this job is CPU-only.
#
# Sintel and Chairs: streams the first N records into a new TFRecord file.
# KITTI: creates symlinks to the first N sorted files (no extra disk space).

WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
HPC="/home/hpc/iwi9/iwi9120h/UFReWaRD"
CODE="${HPC}/Codebase/fine-tuning"
ENV_PREFIX="${WOODY}/uflow"

# Source TFRecord paths (adjust if your layout differs)
SINTEL_SRC="${HPC}/sintel_tfrecords/training/clean/sintel@0.tfrecord"
SINTEL_OUT="${HPC}/de_tfrecords/sintel"

CHAIRS_SRC="${WOODY}/flying_chairs_tfrecords/train/flying_chairs@0.tfrecord"
CHAIRS_OUT="${WOODY}/de_tfrecords/chairs"

KITTI_SRC="${HPC}/kitti_raw_train_384x1280-tfrecords"
KITTI_OUT="${HPC}/de_tfrecords/kitti"

module purge
module load python/3.12-conda

echo "=== Creating DE TFRecord subsets ==="
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python "${CODE}/create_de_subsets.py" \
    --sintel_src="${SINTEL_SRC}" \
    --sintel_out="${SINTEL_OUT}" \
    --chairs_src="${CHAIRS_SRC}" \
    --chairs_out="${CHAIRS_OUT}" \
    --kitti_src="${KITTI_SRC}" \
    --kitti_out="${KITTI_OUT}" \
    --fracs="0.01,0.02,0.05,0.10,0.25,0.50"

echo "=== Done ==="
