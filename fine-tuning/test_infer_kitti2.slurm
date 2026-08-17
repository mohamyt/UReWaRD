#!/usr/bin/env bash
#SBATCH --job-name=test_infer_kitti2
#SBATCH --gres=gpu:1
#SBATCH --time=00:15:00
#SBATCH --output=/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/test_infer_kitti2_%j.out

WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
ENV_PREFIX="${WOODY}/uflow"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"
CKPT="${WOODY}/checkpoints/de_ps7_kitti_10_1605925"
KITTI2_EVAL="${WOODY}/kitti2015_tfrecords/training"
OUT="${TMPDIR}/test_infer_kitti2_out"

module purge
module load python/3.12-conda

echo "=== Running KITTI 2015 inference smoke test (2 pairs) ==="
mkdir -p "${OUT}"
cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python infer_kitti2.py \
    --checkpoint_dir="${CKPT}" \
    --kitti_eval_dir="${KITTI2_EVAL}" \
    --output_dir="${OUT}" \
    --num_pairs=2 \
    --height=256 \
    --width=256

echo "Output files: $(ls ${OUT} | wc -l) strips"
echo "=== KITTI 2015 inference smoke test: PASSED ==="
