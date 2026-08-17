# coding=utf-8
# Copyright 2026 The Google Research Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Converts image-sequence folders into UFlow's custom TFRecord format."""

import os

from absl import app
from absl import flags
import cv2
import numpy as np
import tensorflow as tf

from uflow.data_conversion_scripts import conversion_utils

FLAGS = flags.FLAGS

flags.DEFINE_string(
    'input_dir', '', 'Directory containing scene subdirectories of frames.')
flags.DEFINE_string(
    'output_dir', '', 'Directory that will contain only TFRecord shards.')
flags.DEFINE_string(
    'image_glob', '*.png', 'Glob used to find frames inside each scene folder.')
flags.DEFINE_integer(
    'num_shards', 128, 'Number of TFRecord shards to create.')
flags.DEFINE_integer(
    'max_scenes', 0, 'Optional limit for smoke tests. Zero means all scenes.')
flags.DEFINE_integer(
    'max_pairs_per_scene', 0,
    'Optional limit on adjacent-frame pairs written per scene. Zero means all.')
flags.DEFINE_integer(
    'max_total_pairs', 0,
    'Optional limit on total adjacent-frame pairs written across all scenes. '
    'Zero means all.')
flags.DEFINE_integer(
    'frame_stride', 1,
    'Gap between paired frames. '
    'For example, 1 writes t/t+1 and 4 writes t/t+4.')
flags.DEFINE_string(
    'frame_strides', '',
    'Optional comma-separated list of gaps, such as "1,2,4". '
    'When set, this replaces --frame_stride.')
flags.DEFINE_float(
    'blur_probability', 0.0,
    'Probability of applying a small Gaussian blur to both images in a pair.')
flags.DEFINE_integer(
    'blur_kernel_size', 3,
    'Odd Gaussian blur kernel size. Use 3 for a light blur.')
flags.DEFINE_integer(
    'random_seed', 41,
    'Seed used for deterministic pair sampling and blur decisions.')
flags.DEFINE_bool(
    'overwrite', False,
    'If true, remove an existing output directory before writing new shards.')


def write_data_example(record_writer, image1, image2):
  """Write a single adjacent-frame pair as a SequenceExample."""
  if image1.shape != image2.shape:
    raise ValueError(
        f'Adjacent frames have mismatched shapes: {image1.shape} vs '
        f'{image2.shape}.')

  feature = {
      'height': conversion_utils.int64_feature(image1.shape[0]),
      'width': conversion_utils.int64_feature(image1.shape[1]),
  }
  example = tf.train.SequenceExample(
      context=tf.train.Features(feature=feature),
      feature_lists=tf.train.FeatureLists(
          feature_list={
              'images':
                  tf.train.FeatureList(feature=[
                      conversion_utils.bytes_feature(
                          image1.astype('uint8').tobytes()),
                      conversion_utils.bytes_feature(
                          image2.astype('uint8').tobytes())
                  ]),
          }))
  record_writer.write(example.SerializeToString())


def _read_image(path):
  image = cv2.imread(path, cv2.IMREAD_COLOR)
  if image is None:
    raise ValueError(f'Failed to read image: {path}')
  return cv2.cvtColor(image, cv2.COLOR_BGR2RGB)


def _list_scene_dirs(input_dir):
  scene_dirs = []
  for name in sorted(tf.io.gfile.listdir(input_dir)):
    path = os.path.join(input_dir, name)
    if tf.io.gfile.isdir(path):
      scene_dirs.append(path)
  return scene_dirs


def _list_images(scene_dir, image_glob):
  return sorted(tf.io.gfile.glob(os.path.join(scene_dir, image_glob)))


def _parse_frame_strides(frame_stride, frame_strides):
  """Return the list of frame gaps to write."""
  if frame_strides:
    strides = [int(value.strip()) for value in frame_strides.split(',')]
  else:
    strides = [frame_stride]
  strides = [stride for stride in strides if stride > 0]
  if not strides:
    raise ValueError('At least one positive frame stride is required.')
  return strides


def _sample_pairs_evenly(pair_specs, max_pairs, rng):
  """Pick up to max_pairs pairs, spread across all candidate pairs."""
  if not max_pairs or len(pair_specs) <= max_pairs:
    return pair_specs

  # Even spacing avoids taking only the first frames of each scene.
  positions = np.linspace(0, len(pair_specs) - 1, max_pairs)
  positions = np.round(positions).astype(np.int64)
  positions = sorted(set(int(position) for position in positions))

  # If rounding caused duplicates, fill the remaining slots randomly.
  if len(positions) < max_pairs:
    remaining = [i for i in range(len(pair_specs)) if i not in positions]
    rng.shuffle(remaining)
    positions.extend(sorted(remaining[:max_pairs - len(positions)]))
    positions = sorted(positions)

  return [pair_specs[position] for position in positions[:max_pairs]]


def _maybe_blur(image1, image2, blur_probability, blur_kernel_size, rng):
  """Optionally apply the same light blur to both images."""
  if blur_probability <= 0.0:
    return image1, image2
  if rng.random_sample() >= blur_probability:
    return image1, image2

  # OpenCV requires an odd positive kernel size.
  kernel_size = max(1, int(blur_kernel_size))
  if kernel_size % 2 == 0:
    kernel_size += 1

  image1 = cv2.GaussianBlur(image1, (kernel_size, kernel_size), 0)
  image2 = cv2.GaussianBlur(image2, (kernel_size, kernel_size), 0)
  return image1, image2


def convert_image_folders(input_dir, output_dir, image_glob, num_shards,
                          max_scenes, max_pairs_per_scene, max_total_pairs,
                          frame_stride, frame_strides, blur_probability,
                          blur_kernel_size, random_seed, overwrite):
  """Convert frame pairs from scene folders into sharded TFRecords."""
  if num_shards <= 0:
    raise ValueError('--num_shards must be positive.')
  if not tf.io.gfile.exists(input_dir):
    raise ValueError(f'Input directory does not exist: {input_dir}')

  strides = _parse_frame_strides(frame_stride, frame_strides)
  rng = np.random.RandomState(random_seed)

  if tf.io.gfile.exists(output_dir):
    existing_files = tf.io.gfile.listdir(output_dir)
    if existing_files:
      if not overwrite:
        raise ValueError(
            f'Output directory is not empty: {output_dir}. '
            'Pass --overwrite to recreate it.')
      tf.io.gfile.rmtree(output_dir)

  tf.io.gfile.makedirs(output_dir)

  shard_prefix = os.path.join(output_dir, f'big_scenes@{num_shards}')
  shard_paths = conversion_utils.generate_sharded_filenames(shard_prefix)
  writers = [tf.io.TFRecordWriter(path) for path in shard_paths]

  try:
    total_scenes = 0
    total_pairs = 0
    skipped_pairs = 0

    for scene_dir in _list_scene_dirs(input_dir):
      if max_scenes and total_scenes >= max_scenes:
        break

      image_paths = _list_images(scene_dir, image_glob)
      if len(image_paths) < min(strides) + 1:
        continue

      pair_specs = []
      for stride in strides:
        for index in range(len(image_paths) - stride):
          pair_specs.append((index, stride))
      pair_specs = _sample_pairs_evenly(pair_specs, max_pairs_per_scene, rng)

      scene_pairs = 0
      for index, stride in pair_specs:
        if max_total_pairs and total_pairs >= max_total_pairs:
          break

        try:
          # Read both frames before writing so corrupt pairs are easy to skip.
          image1 = _read_image(image_paths[index])
          image2 = _read_image(image_paths[index + stride])
        except ValueError as error:
          # A few bad PNG files should not abort a long dataset conversion.
          skipped_pairs += 1
          print(f'Skipping unreadable pair: {error}')
          continue

        image1, image2 = _maybe_blur(
            image1, image2, blur_probability, blur_kernel_size, rng)

        writer = writers[total_pairs % num_shards]
        write_data_example(writer, image1, image2)
        total_pairs += 1
        scene_pairs += 1

      total_scenes += 1
      if total_scenes % 100 == 0:
        print(f'Converted {total_scenes} scenes and {total_pairs} pairs.')
      if max_total_pairs and total_pairs >= max_total_pairs:
        break

    print(
        f'Finished conversion: {total_scenes} scenes, {total_pairs} pairs, '
        f'{skipped_pairs} skipped pairs.')
  finally:
    for writer in writers:
      writer.close()


def main(unused_argv):
  convert_image_folders(
      input_dir=FLAGS.input_dir,
      output_dir=FLAGS.output_dir,
      image_glob=FLAGS.image_glob,
      num_shards=FLAGS.num_shards,
      max_scenes=FLAGS.max_scenes,
      max_pairs_per_scene=FLAGS.max_pairs_per_scene,
      max_total_pairs=FLAGS.max_total_pairs,
      frame_stride=FLAGS.frame_stride,
      frame_strides=FLAGS.frame_strides,
      blur_probability=FLAGS.blur_probability,
      blur_kernel_size=FLAGS.blur_kernel_size,
      random_seed=FLAGS.random_seed,
      overwrite=FLAGS.overwrite)


if __name__ == '__main__':
  app.run(main)
