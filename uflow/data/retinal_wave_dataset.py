# coding=utf-8
# Retinal wave dataset loader for UFlow.
#
# Reads consecutive PNG frame pairs from the scenes/ directory structure.
# Scene directories are numbered (0/, 1/, 2/, ...) and contain sequential PNGs.
#
# Images are 1-bit binary PNGs (PIL mode '1'); they MUST be converted to RGB
# before numpy/TF conversion so pixel values land in {0, 255} rather than {0, 1}.
# Returns (image1, image2) float32 tensors in [0, 1], shape [H, W, 3].
#
# Design: lazy sampling — only the list of scene directories and their frame
# counts are loaded at startup (~39k entries). Pairs are sampled on-the-fly by
# a Python generator, avoiding the ~26M-entry pair list that the eager approach
# would require.

import os
import random

import numpy as np
from PIL import Image
import tensorflow as tf


_debug_count = [0]  # mutable counter for debug prints

def _load_png_pair(path1, path2, binary=False, normalize=True):
  """Load two PNG files as float32 [H, W, 3] arrays.

  binary=True : PIL mode '1' → {0.0, 1.0}, no /255.
  normalize=True (default): convert RGB → /255 → [0.0, 1.0].
  normalize=False: convert RGB, no /255 → [0.0, 255.0].
  """
  def _load(path):
    if binary:
      img = Image.open(path).convert('1')
      arr = np.array(img, dtype=np.float32)          # {0.0, 1.0}, [H, W]
      arr = np.stack([arr, arr, arr], axis=-1)        # [H, W, 3]
    elif normalize:
      img = Image.open(path).convert('RGB')
      arr = np.array(img, dtype=np.float32) / 255.0  # [H, W, 3], [0,1]
    else:
      img = Image.open(path).convert('RGB')
      arr = np.array(img, dtype=np.float32)           # [H, W, 3], [0,255]
    return arr

  img1 = _load(path1)
  img2 = _load(path2)

  if _debug_count[0] < 5:
    print('[rwave_dataset] max pixel: img1=%.4f  img2=%.4f  '
          '(binary=%s, normalize=%s)' % (img1.max(), img2.max(), binary, normalize),
          flush=True)
    _debug_count[0] += 1

  return img1, img2


def _scan_scenes(scenes_dir):
  """Return a list of (scene_path, sorted_frame_filenames) for every scene.

  Only reads one level of directory listing — O(num_scenes), not O(num_frames).
  Frame listings are deferred until a scene is actually sampled.
  """
  try:
    entries = sorted(os.listdir(scenes_dir))
  except FileNotFoundError as e:
    raise FileNotFoundError('scenes_dir not found: %s' % scenes_dir) from e

  scenes = []
  for name in entries:
    path = os.path.join(scenes_dir, name)
    if os.path.isdir(path):
      scenes.append(path)
  return scenes


def _pair_generator(scenes, delta_t_min, delta_t_max, seed):
  """Infinite generator that yields (path1, path2) pairs sampled randomly.

  At each step:
    1. Pick a random scene.
    2. List its frames (cached per scene on first access).
    3. Pick a random anchor frame i and a random dt in [delta_t_min, delta_t_max].
    4. Yield (frame_i, frame_{i+dt}).

  This keeps memory usage proportional to the number of scenes (~39k paths)
  rather than the number of pairs (~26M).
  """
  rng = random.Random(seed)
  frame_cache = {}  # scene_path -> sorted list of PNG filenames

  def get_frames(scene_path):
    if scene_path not in frame_cache:
      frames = sorted(
          f for f in os.listdir(scene_path) if f.lower().endswith('.png'))
      frame_cache[scene_path] = frames
    return frame_cache[scene_path]

  while True:
    scene = rng.choice(scenes)
    frames = get_frames(scene)
    n = len(frames)
    if n < delta_t_min + 1:
      continue
    dt = rng.randint(delta_t_min, min(delta_t_max, n - 1))
    i = rng.randint(0, n - 1 - dt)
    p1 = os.path.join(scene, frames[i])
    p2 = os.path.join(scene, frames[i + dt])
    yield p1, p2


# Keep _build_pair_list for use by infer_rwave.py (small fixed sample).
def _build_pair_list(scenes_dir, delta_t_min=1, delta_t_max=5, seed=41):
  """Enumerate a shuffled list of all valid (path1, path2) pairs.

  Used by inference (small fixed set). For training, use make_dataset()
  which samples lazily and avoids building the full ~26M-entry list.
  """
  pairs = []
  try:
    scene_names = sorted(os.listdir(scenes_dir))
  except FileNotFoundError as e:
    raise FileNotFoundError('scenes_dir not found: %s' % scenes_dir) from e

  for scene in scene_names:
    scene_path = os.path.join(scenes_dir, scene)
    if not os.path.isdir(scene_path):
      continue
    frames = sorted(
        f for f in os.listdir(scene_path) if f.lower().endswith('.png'))
    n = len(frames)
    if n < 2:
      continue
    for i in range(n):
      for dt in range(delta_t_min, delta_t_max + 1):
        j = i + dt
        if j < n:
          pairs.append((
              os.path.join(scene_path, frames[i]),
              os.path.join(scene_path, frames[j]),
          ))

  return pairs


def make_dataset(
    scenes_dir,
    height,
    width,
    delta_t_min=1,
    delta_t_max=5,
    shuffle_buffer_size=1024,
    seed=41,
    mode='train',
    binary=False,
    normalize=True,
):
  """Build a tf.data.Dataset of retinal-wave image pairs.

  Each element is a float32 tensor of shape [2, height, width, 3] (seq_len=2),
  matching the format expected by UFlow's unsupervised training pipeline.

  Uses lazy pair sampling: only scene directory paths are loaded at startup.
  Pairs are drawn on-the-fly so memory usage stays small regardless of dataset
  size.

  Args:
    scenes_dir: str, path to the scenes/ root directory.
    height: int, target image height.
    width: int, target image width.
    delta_t_min: int, minimum temporal gap between paired frames.
    delta_t_max: int, maximum temporal gap between paired frames.
    shuffle_buffer_size: int, tf.data shuffle buffer size.
    seed: int, random seed.
    mode: str, ignored (present for API compatibility).

  Returns:
    An infinite tf.data.Dataset of float32 tensors shaped [2, H, W, 3].
  """
  scenes = _scan_scenes(scenes_dir)
  if not scenes:
    raise ValueError('No scene directories found in: %s' % scenes_dir)
  print('Found %d scenes in %s' % (len(scenes), scenes_dir))

  def generator():
    yield from _pair_generator(scenes, delta_t_min, delta_t_max, seed)

  pair_ds = tf.data.Dataset.from_generator(
      generator,
      output_signature=(
          tf.TensorSpec(shape=(), dtype=tf.string),
          tf.TensorSpec(shape=(), dtype=tf.string),
      ),
  )

  clip_max = 1.0 if (binary or normalize) else 255.0

  def _load_and_resize(p1, p2):
    img1, img2 = tf.py_function(
        func=lambda a, b: _load_png_pair(
            a.numpy().decode(), b.numpy().decode(), binary=binary, normalize=normalize),
        inp=[p1, p2],
        Tout=[tf.float32, tf.float32],
    )
    img1 = tf.ensure_shape(img1, [None, None, 3])
    img2 = tf.ensure_shape(img2, [None, None, 3])
    img1 = tf.image.resize(img1, [height, width])
    img2 = tf.image.resize(img2, [height, width])
    img1 = tf.clip_by_value(img1, 0.0, clip_max)
    img2 = tf.clip_by_value(img2, 0.0, clip_max)
    imgs = tf.stack([img1, img2], axis=0)  # [2, H, W, 3]
    imgs.set_shape([2, height, width, 3])
    return imgs

  dataset = pair_ds.shuffle(buffer_size=shuffle_buffer_size, seed=seed,
                            reshuffle_each_iteration=True)
  dataset = dataset.map(_load_and_resize, num_parallel_calls=tf.data.AUTOTUNE)
  dataset = dataset.repeat()
  return dataset
