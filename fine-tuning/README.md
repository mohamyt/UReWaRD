# Fine-tuning

Supervised fine-tuning of a pre-trained UFlow model on downstream optical flow datasets
(MPI Sintel, FlyingChairs, KITTI).

## Attribution

The `uflow/` package is adapted from the UFlow codebase by Google Research:

> **Repository:** https://github.com/google-research/google-research/tree/master/uflow  
> **Paper:** Jonschkowski et al., "What Matters in Unsupervised Optical Flow", NeurIPS 2020  
> **License:** Apache 2.0

See `../ATTRIBUTION.md` for a full description of modifications made.

## Usage

1. Set up the conda environment from `environment.yml` (login node only):
   ```bash
   mamba env create -f environment.yml -p /path/to/env
   ```
2. Prepare datasets: `sbatch setup_sintel.slurm`, `setup_chairs.slurm`, etc.
3. (Optional) Create data-efficiency subsets: `sbatch create_de_subsets.slurm`
4. Fine-tune: `sbatch finetune_sintel.slurm` (set `RWAVE_CKPT` inside first)
5. Evaluate: `sbatch eval_model.slurm` (set `LABEL` and `CKPT_DIR` inside; appends to `eval_results.csv`)
6. Inference: `sbatch` the relevant `test_infer_*.slurm`, or run `infer_sintel_supervisor.py` / `infer_chairs.py` / `infer_kitti2.py` directly

## Checkpoint restore (TF 2.18)

All scripts use a manual weight-restore approach (warm-up call + `_get_obj` traversal + `.assign()`) because the standard Keras 3 object-graph restore silently fails under TF 2.18.
