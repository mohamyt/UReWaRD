# Attribution

This codebase contains code adapted from external sources. Each component is
described below with its original author(s) and repository.

---

## retinal-wave-simulator/

**Author:** Benjamin Cappell (2023 update of the original model by Keith Godfrey)  
**Repository:** https://github.com/BennyCa/Retinal-Wave-Simulator  
**License:** see `retinal-wave-simulator/LICENSE`

The simulator implements the retinal wave model described in Godfrey & Swindale
and was extended by Cappell to support batch processing and PNG image output.
The code in this directory is used as-is from Cappell's repository.

---

## pre-training/uflow/  and  fine-tuning/uflow/

**Authors:** Google Research (UFlow)  
**Repository:** https://github.com/google-research/google-research/tree/master/uflow  
**License:** Apache 2.0 (copyright headers preserved in each source file)  
**Paper:** Jonschkowski et al., "What Matters in Unsupervised Optical Flow", NeurIPS 2020

The `uflow/` package in both `pre-training/` and `fine-tuning/` is adapted from
the above repository. Modifications made for this project:

- Ported to Python 3.12 and TensorFlow 2.15.1
  (original targets Python 3.8 / TF 2.x with `tensorflow_addons`)
- `tensorflow_addons` dependency removed; `tfa.image.rotate` replaced with a
  pure-TF implementation using `tf.raw_ops.ImageProjectiveTransformV3`
- `tf.compat.v1` API calls updated to TF 2 equivalents
- `gin.tf` import removed (`tf.estimator` absent in TF 2.15)
- Global step management refactored to a module-level `tf.Variable`
- Optimizer updated from `AdamOptimizer` (v1 compat) to `tf.keras.optimizers.Adam`
- Added `retinal_wave_dataset.py` for loading retinal wave image sequences as
  a `tf.data.Dataset`

The top-level scripts (`pretrain.slurm`, `finetune_*.slurm`, `infer_*.py`,
`setup_*.slurm`) are original work built on top of the UFlow API.
