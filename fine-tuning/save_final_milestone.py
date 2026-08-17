#!/usr/bin/env python3
"""Save the final (post-walltime) checkpoint as a milestone.

Copies the latest regular checkpoint into checkpoint_dir/milestones/
and updates the TF checkpoint manifest so the milestone manager finds it.

Usage:
    python save_final_milestone.py <checkpoint_dir>
"""
import os, re, shutil, sys

if len(sys.argv) < 2:
    sys.exit("Usage: save_final_milestone.py <checkpoint_dir>")

ckpt_dir = os.path.abspath(sys.argv[1])
ms_dir = os.path.join(ckpt_dir, "milestones")

if not os.path.isdir(ckpt_dir):
    sys.exit(f"Checkpoint dir not found: {ckpt_dir}")
os.makedirs(ms_dir, exist_ok=True)

# ── Find the latest regular checkpoint ────────────────────────────────────────
manifest = os.path.join(ckpt_dir, "checkpoint")
if not os.path.exists(manifest):
    sys.exit(f"No checkpoint manifest at {manifest}")

content = open(manifest).read()
# TF checkpoint manifest format:  model_checkpoint_path: "ckpt-265"
m = re.search(r'model_checkpoint_path:\s+"([^"]+)"', content)
if not m:
    sys.exit(f"Cannot parse checkpoint manifest: {manifest}")

latest_name = m.group(1)  # e.g. "ckpt-265"
latest_base = os.path.join(ckpt_dir, latest_name)

# Verify files exist
exts = [".index", ".data-00000-of-00001"]
for ext in exts:
    if not os.path.exists(latest_base + ext):
        sys.exit(f"Checkpoint file missing: {latest_base + ext}")

# ── Copy to milestones/ with a 'final_' prefix ────────────────────────────────
final_name = "final_" + latest_name  # e.g. "final_ckpt-265"
for ext in exts:
    src = latest_base + ext
    dst = os.path.join(ms_dir, final_name + ext)
    if os.path.exists(dst):
        print(f"Already exists, skipping: {dst}")
    else:
        shutil.copy2(src, dst)
        print(f"Copied {os.path.basename(src)} -> milestones/{final_name + ext}")

# ── Update milestones/checkpoint manifest ────────────────────────────────────
ms_manifest = os.path.join(ms_dir, "checkpoint")
if os.path.exists(ms_manifest):
    ms_content = open(ms_manifest).read()
else:
    ms_content = ""

if final_name in ms_content:
    print(f"Milestone manifest already contains {final_name}")
else:
    # Append the new entry and update model_checkpoint_path
    lines = [l for l in ms_content.strip().split("\n") if l.strip()]
    # Remove old model_checkpoint_path line
    lines = [l for l in lines if not l.startswith("model_checkpoint_path:")]
    # Add the new final as model_checkpoint_path and an all_ entry
    new_lines = [f'model_checkpoint_path: "{final_name}"'] + lines + \
                [f'all_model_checkpoint_paths: "{final_name}"']
    with open(ms_manifest, "w") as f:
        f.write("\n".join(new_lines) + "\n")
    print(f"Updated milestones/checkpoint to include {final_name}")

# ── Report step count ─────────────────────────────────────────────────────────
ckpt_num = int(re.search(r"ckpt-(\d+)$", latest_name).group(1))
print(f"\nFinal milestone: {final_name} (checkpoint #{ckpt_num})")
print(f"  checkpoint_dir: {ckpt_dir}")
print(f"  milestones_dir: {ms_dir}")
