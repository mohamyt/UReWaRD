#!/usr/bin/env bash
#SBATCH --job-name=ft_kitti
#SBATCH --gres=gpu:1
#SBATCH --time=24:00:00
#SBATCH --output=%x_%j.out

# ─── Configure these paths ───────────────────────────────────────────────────
WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/fine-tuning"
ENV_PREFIX="${WOODY}/uflow"
RWAVE_CKPT="${WOODY}/checkpoints/<pretrain_checkpoint>"  # set to pre-trained checkpoint dir; leave empty to train from scratch
CKPT="${WOODY}/checkpoints/ft_kitti_${SLURM_JOB_ID}"
KITTI_TF="/home/hpc/iwi9/iwi9120h/UFReWaRD/kitti_raw_train_384x1280-tfrecords"
INFER_OUT="${WOODY}/inference_out"

# ─── Training hyperparameters ─────────────────────────────────────────────────
NUM_STEPS=200000
EPOCH_LEN=1000
INTERVAL=3600
LR=5e-5
BATCH=4
HEIGHT=256
WIDTH=256
# KITTI: second-order smoothness only + higher self-supervision weight
# smooth1=0 avoids penalising sharp flow edges common in driving scenes
W_CENSUS=1.25
W_SMOOTH1=0.0
W_SMOOTH2=2.0
W_SELFSUP=4.0

# ─── Environment ─────────────────────────────────────────────────────────────
module purge
module load python/3.12-conda
mkdir -p "${CKPT}" "${INFER_OUT}"

echo "Fine-tune: rwave -> KITTI | ckpt: ${CKPT}"

cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python -m uflow.uflow_main \
    --train_on="kitti:${KITTI_TF}" \
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
    local out="${INFER_OUT}/ft_kitti_${steps}steps_${SLURM_JOB_ID}"
    mkdir -p "${out}"
    # Use infer_kitti.py for KITTI raw training set visualisation
    # Use infer_kitti2.py for KITTI 2015 evaluation set (ground-truth available)
    conda run --no-capture-output -p "${ENV_PREFIX}" \
        python infer_kitti.py \
        --checkpoint_dir="${CKPT}" \
        --tfrecord_dir="${KITTI_TF}" \
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
