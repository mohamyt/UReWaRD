#!/usr/bin/env bash
#SBATCH --job-name=test_pretrain
#SBATCH --gres=gpu:1
#SBATCH --time=00:30:00
#SBATCH --output=/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/test_pretrain_%j.out

WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
ENV_PREFIX="${WOODY}/uflow"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/pre-training"
PEEK_ZIP="/home/hpc/iwi9/iwi9120h/UFReWaRD/uflow_clean/peek_scenes.zip"
MINI_DS="${TMPDIR}/mini_rwave_dataset"
CKPT="${TMPDIR}/test_pretrain_ckpt"

module purge
module load python/3.12-conda

echo "=== Extracting peek_scenes.zip ==="
mkdir -p "${TMPDIR}/raw"
unzip -q "${PEEK_ZIP}" -d "${TMPDIR}/raw"
echo "Scenes: $(ls ${TMPDIR}/raw/peek_staged | wc -l)"

echo "=== Converting to TFRecords ==="
mkdir -p "${MINI_DS}"
cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python -m uflow.misc.convert_image_folders_to_dataset \
    --input_dir="${TMPDIR}/raw/peek_staged" \
    --output_dir="${MINI_DS}" \
    --num_shards=1 \
    --frame_stride=2 \
    --overwrite
echo "Shards: $(ls ${MINI_DS} | wc -l)"

echo "=== Running 20-step pre-training smoke test ==="
mkdir -p "${CKPT}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python -m uflow.uflow_main \
    --train_on="custom:${MINI_DS}" \
    --checkpoint_dir="${CKPT}" \
    --height=256 --width=256 \
    --batch_size=1 \
    --epoch_length=10 \
    --num_train_steps=20 \
    --gpu_learning_rate=5e-5 \
    --weight_census=1.25 \
    --weight_smooth1=2.0 \
    --weight_smooth2=2.0 \
    --weight_selfsup=0.0 \
    --from_scratch=true \
    --config_file="${CODE}/rwave_like.gin" \
    --gin_bindings="rwave_domain_augmentation.apply_blur=True"

echo "=== Pre-training smoke test: PASSED ==="
echo "Checkpoint: $(ls ${CKPT})"
