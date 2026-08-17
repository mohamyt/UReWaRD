# Pre-training

Unsupervised optical flow pre-training on retinal wave image sequences.

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
2. Prepare the dataset: `sbatch setup_rwave_dataset.slurm`
3. Run pre-training: `sbatch pretrain.slurm`
4. Inference on wave frames: `sbatch` the relevant `infer_*.slurm`
