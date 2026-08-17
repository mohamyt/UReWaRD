#!/usr/bin/env bash
#SBATCH --job-name=rwave_pretrain
#SBATCH --gres=gpu:1
#SBATCH --time=24:00:00
#SBATCH --output=%x_%j.out

# ─── Configure these paths ───────────────────────────────────────────────────
WOODY="/home/woody/iwi9/iwi9120h/UFReWaRD"       # persistent storage root
CODE="/home/hpc/iwi9/iwi9120h/UFReWaRD/Codebase/pre-training"
ENV_PREFIX="${WOODY}/uflow"              # conda env with TF 2.18
CUSTOM_DS="${WOODY}/rwave_5kthresh_dataset"       # pre-converted tfrecord dataset
SCENES_ZIP="${WOODY}/scenes_5kthreshold.zip"      # raw wave zip (for consecutive inference)
SINTEL_TF="/home/hpc/iwi9/iwi9120h/UFReWaRD/sintel_tfrecords/training/clean"
CKPT="${WOODY}/checkpoints/rwave_pretrain_${SLURM_JOB_ID}"
INFER_OUT="${WOODY}/inference_out"

# ─── Training hyperparameters ─────────────────────────────────────────────────
NUM_STEPS=1000000
EPOCH_LEN=1000      # checkpoint frequency (steps)
INTERVAL=3600       # inference interval (seconds)
LR=5e-5
BATCH=4
HEIGHT=256
WIDTH=256
# Loss weights — keep smooth weights low for binary wave data
W_CENSUS=1.25
W_SMOOTH1=2.0
W_SMOOTH2=2.0
W_SELFSUP=2.0
# Set apply_blur=True to add Gaussian blur domain augmentation (rwave_aug regime)
# Set apply_blur=False for plain photometric/geometric augmentation only
BLUR=True

# ─── Environment ─────────────────────────────────────────────────────────────
module purge
module load python/3.12-conda
mkdir -p "${CKPT}" "${INFER_OUT}"

echo "Pre-training on retinal waves | ckpt: ${CKPT}"

# Extract one scene for consecutive inference monitoring
mkdir -p "${TMPDIR}/raw"
unzip -q "${SCENES_ZIP}" "all_scenes_5k/0/*" -d "${TMPDIR}/raw"
SCENE_DIR="${TMPDIR}/scene0"
mkdir -p "${SCENE_DIR}"
mv "${TMPDIR}/raw/all_scenes_5k/0/"*.png "${SCENE_DIR}/"
echo "Scene 0 frames: $(ls ${SCENE_DIR} | wc -l)"

# ─── Training ────────────────────────────────────────────────────────────────
cd "${CODE}"
conda run --no-capture-output -p "${ENV_PREFIX}" \
    python -m uflow.uflow_main \
    --train_on="custom:${CUSTOM_DS}" \
    --checkpoint_dir="${CKPT}" \
    --height=${HEIGHT} --width=${WIDTH} \
    --batch_size=${BATCH} \
    --epoch_length=${EPOCH_LEN} \
    --num_train_steps=${NUM_STEPS} \
    --gpu_learning_rate=${LR} \
    --weight_census=${W_CENSUS} \
    --weight_smooth1=${W_SMOOTH1} \
    --weight_smooth2=${W_SMOOTH2} \
    --weight_selfsup=${W_SELFSUP} \
    --frozen_teacher=false \
    --selfsup_after_num_steps=0 \
    --selfsup_ramp_up_steps=1 \
    --from_scratch=true \
    --config_file="${CODE}/rwave_like.gin" \
    --gin_bindings="rwave_domain_augmentation.apply_blur=${BLUR}" &
TRAIN_PID=$!
echo "Training PID: ${TRAIN_PID}"

LAST_CKPT=0

# Periodic inference — runs on both Sintel and consecutive wave frames
run_inference() {
    local ckpt_num=$1
    local steps=$(( ckpt_num * EPOCH_LEN ))
    echo "=== Inference: ckpt-${ckpt_num} (~${steps} steps) ==="

    local out_sintel="${INFER_OUT}/pretrain_sintel_${steps}steps_${SLURM_JOB_ID}"
    mkdir -p "${out_sintel}"
    conda run --no-capture-output -p "${ENV_PREFIX}" \
        python infer_sintel_supervisor.py \
        --checkpoint_dir="${CKPT}" \
        --tfrecord_dir="${SINTEL_TF}" \
        --output_dir="${out_sintel}" \
        --num_pairs=50 --height=${HEIGHT} --width=${WIDTH}

    local out_consec="${INFER_OUT}/pretrain_consec_${steps}steps_${SLURM_JOB_ID}"
    mkdir -p "${out_consec}"
    conda run --no-capture-output -p "${ENV_PREFIX}" \
        python infer_rwave_consecutive.py \
        --checkpoint_dir="${CKPT}" \
        --scene_dir="${SCENE_DIR}" \
        --output_dir="${out_consec}" \
        --height=${HEIGHT} --width=${WIDTH} \
        --frame_stride=4
}

while kill -0 "${TRAIN_PID}" 2>/dev/null; do
    sleep "${INTERVAL}"
    [ ! -d "${CKPT}" ] && continue
    latest_index=$(ls "${CKPT}"/ckpt-*.index 2>/dev/null | sort -t- -k2 -n | tail -1)
    [ -z "${latest_index}" ] && continue
    ckpt_num=$(basename "${latest_index}" | grep -oP '(?<=ckpt-)\d+')
    if [ "${ckpt_num}" -gt "${LAST_CKPT}" ]; then
        LAST_CKPT="${ckpt_num}"
        run_inference "${ckpt_num}"
    fi
done

# Final inference on exit
latest_index=$(ls "${CKPT}"/ckpt-*.index 2>/dev/null | sort -t- -k2 -n | tail -1)
if [ -n "${latest_index}" ]; then
    ckpt_num=$(basename "${latest_index}" | grep -oP '(?<=ckpt-)\d+')
    [ "${ckpt_num}" -gt "${LAST_CKPT}" ] && run_inference "${ckpt_num}"
fi

wait "${TRAIN_PID}"
echo "=== Done. Checkpoint at: ${CKPT} ==="
