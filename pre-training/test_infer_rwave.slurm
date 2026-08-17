#!/usr/bin/env bash
#SBATCH --job-name=test_infer_rwave
#SBATCH --gres=gpu:1
#SBATCH --time=00:15:00
#SBATCH --output=/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/test_infer_rwave_%j.out

WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
ENV_PREFIX="${WOODY}/uflow"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/pre-training"
CKPT="${WOODY}/checkpoints/rwave_aug_807k"
SCENE="${TMPDIR}/test_scene"
OUT="${TMPDIR}/test_infer_rwave_out"

module purge
module load python/3.12-conda

echo "=== Generating synthetic rwave scene frames ==="
mkdir -p "${SCENE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python -c "
import os, numpy as np
from PIL import Image
scene = '${SCENE}'
for i in range(10):
    arr = np.zeros((256,256,3), dtype=np.uint8)
    arr[i*10:(i+1)*10, i*10:(i+1)*10] = 255
    Image.fromarray(arr).save(os.path.join(scene, f'{i:04d}.png'))
print('Generated 10 frames in', scene)
"

echo "=== Running rwave consecutive inference smoke test ==="
mkdir -p "${OUT}"
cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python infer_rwave_consecutive.py \
    --checkpoint_dir="${CKPT}" \
    --scene_dir="${SCENE}" \
    --output_dir="${OUT}" \
    --height=256 \
    --width=256 \
    --frame_stride=4

echo "Output files: $(ls ${OUT} | wc -l) strips"
echo "=== rwave consecutive inference smoke test: PASSED ==="
