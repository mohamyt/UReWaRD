#!/usr/bin/env bash
#SBATCH --job-name=test_infer_chairs
#SBATCH --gres=gpu:1
#SBATCH --time=00:15:00
#SBATCH --output=/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/test_infer_chairs_%j.out

WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
ENV_PREFIX="${WOODY}/uflow"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"
CKPT="${WOODY}/checkpoints/de_ps7_chairs_10_1605928"
CHAIRS_TEST="${WOODY}/flying_chairs_tfrecords/test"
OUT="${TMPDIR}/test_infer_chairs_out"

module purge
module load python/3.12-conda

echo "=== Running FlyingChairs inference smoke test (2 pairs) ==="
mkdir -p "${OUT}"
cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python infer_chairs.py \
    --checkpoint_dir="${CKPT}" \
    --tfrecord_dir="${CHAIRS_TEST}" \
    --output_dir="${OUT}" \
    --num_pairs=2 \
    --height=256 \
    --width=256

echo "Output files: $(ls ${OUT} | wc -l) strips"
echo "=== FlyingChairs inference smoke test: PASSED ==="
