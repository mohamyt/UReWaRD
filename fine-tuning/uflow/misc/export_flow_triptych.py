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

"""Exports image-1, image-2, flow triptych figures from a checkpoint."""

from pathlib import Path

from absl import app
from absl import flags
import cv2
import matplotlib.pyplot as plt
import numpy as np
import tensorflow as tf

from uflow import uflow_plotting
from uflow.uflow_net import UFlow

FLAGS = flags.FLAGS

flags.DEFINE_string('checkpoint_dir', '', 'Checkpoint directory to restore.')
flags.DEFINE_string('image_root', '', 'Root directory containing scene folders.')
flags.DEFINE_string(
    'output_dir', '', 'Directory where triptych images will be written.')
flags.DEFINE_string(
    'scene_names', '0,1,10',
    'Comma-separated scene folder names to visualize.')
flags.DEFINE_string(
    'image1_name', '00001.png', 'First image filename inside each scene folder.')
flags.DEFINE_string(
    'image2_name', '00002.png', 'Second image filename inside each scene folder.')
flags.DEFINE_integer('height', 256, 'Inference height.')
flags.DEFINE_integer('width', 256, 'Inference width.')


def _load_rgb_image(path):
  image = cv2.imread(str(path), cv2.IMREAD_COLOR)
  if image is None:
    raise ValueError(f'Could not read image: {path}')
  return cv2.cvtColor(image, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0


def _save_triptych(image1, image2, flow_rgb, output_path):
  fig, axes = plt.subplots(1, 3, figsize=(12, 4))
  items = [
      ('image-1', image1),
      ('image-2', image2),
      ('flow', flow_rgb),
  ]
  for axis, (title, image) in zip(axes, items):
    axis.imshow(image)
    axis.set_title(title)
    axis.set_xticks([])
    axis.set_yticks([])
  fig.tight_layout()
  fig.savefig(output_path, dpi=160)
  plt.close(fig)


def main(unused_argv):
  scene_names = [name.strip() for name in FLAGS.scene_names.split(',') if name]
  output_dir = Path(FLAGS.output_dir)
  output_dir.mkdir(parents=True, exist_ok=True)

  uflow = UFlow(checkpoint_dir=FLAGS.checkpoint_dir, dropout_rate=0.1)
  restore_status = uflow.restore()
  if restore_status is not None:
    restore_status.expect_partial()

  for scene in scene_names:
    image1_path = Path(FLAGS.image_root) / scene / FLAGS.image1_name
    image2_path = Path(FLAGS.image_root) / scene / FLAGS.image2_name
    image1 = _load_rgb_image(image1_path)
    image2 = _load_rgb_image(image2_path)
    flow = uflow.infer(
        tf.convert_to_tensor(image1),
        tf.convert_to_tensor(image2),
        input_height=FLAGS.height,
        input_width=FLAGS.width,
        resize_flow_to_img_res=True,
        infer_occlusion=False)
    flow_rgb = uflow_plotting.flow_to_rgb(flow.numpy())
    output_path = output_dir / f'grid_scene_{scene}.png'
    _save_triptych(image1, image2, flow_rgb, output_path)
    print(output_path)


if __name__ == '__main__':
  app.run(main)
