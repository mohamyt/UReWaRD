# coding=utf-8
"""Small raw-Sintel evaluator for an existing UFlow checkpoint."""

import argparse
import os
import time

import imageio.v2 as imageio
import numpy as np
import tensorflow as tf

from uflow.data_conversion_scripts import conversion_utils
from uflow.uflow_net import UFlow


def parse_args():
  """Read command-line arguments for this small evaluation script."""
  parser = argparse.ArgumentParser()
  parser.add_argument('--sintel_dir', required=True)
  parser.add_argument('--checkpoint_dir', required=True)
  parser.add_argument('--rendering', default='clean', choices=['clean', 'final'])
  parser.add_argument('--height', type=int, default=256)
  parser.add_argument('--width', type=int, default=256)
  parser.add_argument('--max_pairs', type=int, default=0)
  return parser.parse_args()


def frame_number(path):
  """Return the integer frame number from names like frame_0001.png."""
  name = os.path.basename(path)
  return int(name.split('_')[1].split('.')[0])


def list_sintel_pairs(sintel_dir, rendering):
  """Yield image1, image2, and flow paths from the raw Sintel training split."""
  image_root = os.path.join(sintel_dir, 'training', rendering)
  flow_root = os.path.join(sintel_dir, 'training', 'flow')

  for scene_name in sorted(os.listdir(image_root)):
    scene_image_dir = os.path.join(image_root, scene_name)
    scene_flow_dir = os.path.join(flow_root, scene_name)

    image_paths = [
        os.path.join(scene_image_dir, name)
        for name in os.listdir(scene_image_dir)
        if name.endswith('.png')
    ]
    image_paths = sorted(image_paths, key=frame_number)

    flow_paths = [
        os.path.join(scene_flow_dir, name)
        for name in os.listdir(scene_flow_dir)
        if name.endswith('.flo')
    ]
    flow_paths = sorted(flow_paths, key=frame_number)

    for image1_path, image2_path, flow_path in zip(
        image_paths[:-1], image_paths[1:], flow_paths):
      yield scene_name, image1_path, image2_path, flow_path


def read_image(path):
  """Read a PNG image and scale byte values to the [0, 1] float range."""
  image = imageio.imread(path)
  image = image.astype(np.float32) / 255.0
  return image


def endpoint_error(flow_prediction, flow_ground_truth):
  """Compute per-pixel endpoint error between two flow fields."""
  squared_error = (flow_prediction - flow_ground_truth) ** 2
  return np.sqrt(np.sum(squared_error, axis=-1))


def main():
  """Restore UFlow, run Sintel pairs, and print simple metrics."""
  args = parse_args()

  model = UFlow(checkpoint_dir=args.checkpoint_dir)
  restore_status = model._checkpoint.restore(model._manager.latest_checkpoint)
  restore_status.expect_partial()

  epe_values = []
  outlier_values = []
  start_time = time.time()

  for pair_index, (scene, image1_path, image2_path, flow_path) in enumerate(
      list_sintel_pairs(args.sintel_dir, args.rendering), start=1):
    if args.max_pairs and pair_index > args.max_pairs:
      break

    image1 = read_image(image1_path)
    image2 = read_image(image2_path)

    prediction = model.infer(
        image1,
        image2,
        input_height=args.height,
        input_width=args.width).numpy()

    ground_truth_xy = conversion_utils.read_flow(flow_path)
    ground_truth_yx = ground_truth_xy[..., ::-1]

    epe = endpoint_error(prediction, ground_truth_yx)
    gt_length = np.sqrt(np.sum(ground_truth_yx ** 2, axis=-1))
    outliers = np.logical_and(epe > 3.0, epe > 0.05 * gt_length)

    epe_values.append(float(np.mean(epe)))
    outlier_values.append(float(np.mean(outliers)))

    if pair_index == 1 or pair_index % 25 == 0:
      print(
          'pair={:04d} scene={} mean_epe={:.4f}'.format(
              pair_index, scene, epe_values[-1]),
          flush=True)

  elapsed = time.time() - start_time
  print('rendering: {}'.format(args.rendering))
  print('pairs: {}'.format(len(epe_values)))
  print('mean_EPE: {:.6f}'.format(float(np.mean(epe_values))))
  print('mean_outlier_rate: {:.6f}'.format(float(np.mean(outlier_values))))
  print('seconds: {:.2f}'.format(elapsed))


if __name__ == '__main__':
  main()
