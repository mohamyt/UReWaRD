#!/usr/bin/env bash
#SBATCH --job-name=ft_sintel
#SBATCH --gres=gpu:1
#SBATCH --time=24:00:00
#SBATCH --output=%x_%j.out

# ─── Configure these paths ───────────────────────────────────────────────────
WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"
ENV_PREFIX="${WOODY}/uflow"
RWAVE_CKPT="${WOODY}/checkpoints/<pretrain_checkpoint>"  # set to pre-trained checkpoint dir; leave empty to train from scratch
CKPT="${WOODY}/checkpoints/ft_sintel_${SLURM_JOB_ID}"
SINTEL_TF="/home/hpc/iwi9/iwi9120h/UFReWaRD/sintel_tfrecords/training/clean"
INFER_OUT="${WOODY}/inference_out"

# ─── Training hyperparameters ─────────────────────────────────────────────────
NUM_STEPS=200000
EPOCH_LEN=1000
INTERVAL=3600
LR=5e-5
BATCH=4
HEIGHT=256
WIDTH=256
# Sintel: first-order smoothness only (smooth2=0 prevents over-smoothing on Sintel)
W_CENSUS=1.25
W_SMOOTH1=2.0
W_SMOOTH2=0.0
W_SELFSUP=2.0

# ─── Environment ─────────────────────────────────────────────────────────────
module purge
module load python/3.12-conda
mkdir -p "${CKPT}" "${INFER_OUT}"

echo "Fine-tune: rwave -> Sintel | ckpt: ${CKPT}"

cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python -m uflow.uflow_main \
    --train_on="sintel:${SINTEL_TF}" \
    --checkpoint_dir="${CKPT}" \
    --init_checkpoint_dir="${RWAVE_CKPT}" \
    --height=${HEIGHT} --width=${WIDTH} \
    --batch_size=${BATCH} \
    --epoch_length=${EPOCH_LEN} \
    --num_train_steps=${NUM_STEPS} \
    --gpu_learning_rate=${LR} \
    --weight_census=${W_CENSUS} \
    --weight_smooth1=${W_SMOOTH1} \
    --weight_smooth2=${W_SMOOTH2} \
    --smoothness_edge_constant=150 \
    --weight_selfsup=${W_SELFSUP} \
    --selfsup_after_num_steps=0 \
    --selfsup_ramp_up_steps=1 \
    --frozen_teacher=false \
    --from_scratch=false \
    --reset_global_step=true \
    --reset_optimizer=true \
    --config_file="${CODE}/standard.gin" &
TRAIN_PID=$!
echo "Training PID: ${TRAIN_PID}"

LAST_CKPT=0

run_inference() {
    local ckpt_num=$1
    local steps=$(( ckpt_num * EPOCH_LEN ))
    echo "=== Inference @ ckpt-${ckpt_num} (~${steps} steps) ==="
    local out="${INFER_OUT}/ft_sintel_${steps}steps_${SLURM_JOB_ID}"
    mkdir -p "${out}"
    conda run --no-capture-output -p "${ENV_PREFIX}" \
        python infer_sintel_supervisor.py \
        --checkpoint_dir="${CKPT}" \
        --tfrecord_dir="${SINTEL_TF}" \
        --output_dir="${out}" \
        --num_pairs=50 --height=${HEIGHT} --width=${WIDTH}
}

while kill -0 "${TRAIN_PID}" 2>/dev/null; do
    sleep "${INTERVAL}"
    latest_index=$(ls "${CKPT}"/ckpt-*.index 2>/dev/null | sort -t- -k2 -n | tail -1)
    [ -z "${latest_index}" ] && continue
    ckpt_num=$(basename "${latest_index}" | grep -oP '(?<=ckpt-)\d+')
    if [ "${ckpt_num}" -gt "${LAST_CKPT}" ]; then
        LAST_CKPT="${ckpt_num}"
        run_inference "${ckpt_num}"
    fi
done

latest_index=$(ls "${CKPT}"/ckpt-*.index 2>/dev/null | sort -t- -k2 -n | tail -1)
if [ -n "${latest_index}" ]; then
    ckpt_num=$(basename "${latest_index}" | grep -oP '(?<=ckpt-)\d+')
    [ "${ckpt_num}" -gt "${LAST_CKPT}" ] && run_inference "${ckpt_num}"
fi

wait "${TRAIN_PID}"
echo "=== Done: ${CKPT} ==="
