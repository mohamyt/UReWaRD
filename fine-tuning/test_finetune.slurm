#!/usr/bin/env bash
#SBATCH --job-name=test_finetune
#SBATCH --gres=gpu:1
#SBATCH --time=00:30:00
#SBATCH --output=/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/test_finetune_%j.out

WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
ENV_PREFIX="${WOODY}/uflow"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"
SINTEL_TF="/home/hpc/iwi9/iwi9120h/UFReWaRD/sintel_tfrecords/training/clean"
CKPT="${TMPDIR}/test_finetune_ckpt"

module purge
module load python/3.12-conda

echo "=== Running 20-step fine-tuning smoke test (Sintel, from scratch) ==="
mkdir -p "${CKPT}"
cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python -m uflow.uflow_main \
    --train_on="sintel:${SINTEL_TF}" \
    --checkpoint_dir="${CKPT}" \
    --height=256 --width=256 \
    --batch_size=1 \
    --epoch_length=10 \
    --num_train_steps=20 \
    --gpu_learning_rate=5e-5 \
    --weight_census=1.25 \
    --weight_smooth1=2.0 \
    --weight_smooth2=0.0 \
    --weight_selfsup=0.0 \
    --from_scratch=true \
    --reset_global_step=true \
    --reset_optimizer=true \
    --config_file="${CODE}/standard.gin"

echo "=== Fine-tuning smoke test: PASSED ==="
echo "Checkpoint: $(ls ${CKPT})"
