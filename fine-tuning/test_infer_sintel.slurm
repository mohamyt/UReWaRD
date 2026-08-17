#!/usr/bin/env bash
#SBATCH --job-name=test_infer_sintel
#SBATCH --gres=gpu:1
#SBATCH --time=00:15:00
#SBATCH --output=/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/test_infer_sintel_%j.out

WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
ENV_PREFIX="${WOODY}/uflow"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"
CKPT="${WOODY}/checkpoints/de_ps7_sintel_10_1605922"
SINTEL_EVAL="/home/hpc/iwi9/iwi9120h/UFReWaRD/sintel_tfrecords/training/clean"
OUT="${TMPDIR}/test_infer_sintel_out"

module purge
module load python/3.12-conda

echo "=== Running Sintel inference smoke test (2 pairs) ==="
mkdir -p "${OUT}"
cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python infer_sintel_supervisor.py \
    --checkpoint_dir="${CKPT}" \
    --tfrecord_dir="${SINTEL_EVAL}" \
    --output_dir="${OUT}" \
    --num_pairs=2 \
    --height=256 \
    --width=256

echo "Output files: $(ls ${OUT} | wc -l) strips"
echo "=== Sintel inference smoke test: PASSED ==="
