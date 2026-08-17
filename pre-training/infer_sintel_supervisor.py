#!/usr/bin/env python3
# coding=utf-8
"""Sintel inference for supervisor's TF 2.15.1 checkpoint.
Generates strip images: img1 | img2 | predicted flow | GT flow.
"""

import glob
import os
import sys

from absl import app
from absl import flags
import numpy as np
from PIL import Image
import tensorflow as tf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from uflow.uflow_net import UFlow
from uflow import uflow_plotting
from uflow.data import sintel as sintel_data

FLAGS = flags.FLAGS

flags.DEFINE_string('checkpoint_dir', '', 'Path to checkpoint directory.')
flags.DEFINE_string('tfrecord_dir', '', 'Path to sintel_tfrecords/training/clean.')
flags.DEFINE_string('output_dir', './inference_out/sintel_supervisor', 'Output directory.')
flags.DEFINE_integer('num_pairs', 50, 'Number of pairs.')
flags.DEFINE_integer('height', 256, 'Inference height.')
flags.DEFINE_integer('width', 256, 'Inference width.')


def main(unused_argv):
    if not FLAGS.checkpoint_dir:
        raise ValueError('--checkpoint_dir must be specified.')
    if not FLAGS.tfrecord_dir:
        raise ValueError('--tfrecord_dir must be specified.')

    os.makedirs(FLAGS.output_dir, exist_ok=True)

    uflow = UFlow(checkpoint_dir=FLAGS.checkpoint_dir)
    index_files = glob.glob(os.path.join(FLAGS.checkpoint_dir, 'ckpt-*.index'))
    if not index_files:
        raise ValueError(f'No checkpoints found in {FLAGS.checkpoint_dir}')
    latest = max(index_files, key=lambda f: int(os.path.basename(f).split('-')[1].split('.')[0]))
    ckpt_path = latest[:-len('.index')]
    uflow._checkpoint.restore(ckpt_path).expect_partial()
    print('Checkpoint restored:', ckpt_path)

    ds = sintel_data.make_dataset(
        path=FLAGS.tfrecord_dir,
        mode='eval-clean',
        height=FLAGS.height,
        width=FLAGS.width,
        shuffle_buffer_size=0,
        seed=42,
    )

    h, w = FLAGS.height, FLAGS.width

    for idx, batch in enumerate(ds.take(FLAGS.num_pairs)):
        # batch = [images, flow_uv, flow_valid]
        images = batch[0]   # [2, H, W, 3]
        gt_flow = batch[1]  # [H, W, 2]
        img1 = images[0]
        img2 = images[1]

        flow = uflow.infer(
            image1=img1, image2=img2,
            input_height=h, input_width=w,
            resize_flow_to_img_res=True,
            infer_occlusion=False,
        )

        def _to_uint8(t):
            arr = np.clip(t.numpy() if hasattr(t, 'numpy') else np.array(t), 0.0, 1.0)
            return (arr * 255).astype(np.uint8)

        img1_u8 = _to_uint8(img1)
        img2_u8 = _to_uint8(img2)

        flow_np = flow.numpy()
        flow_rgb = uflow_plotting.flow_to_rgb(flow_np)
        if flow_rgb.dtype != np.uint8:
            flow_rgb = (np.clip(flow_rgb, 0.0, 1.0) * 255).astype(np.uint8)

        gt_np = gt_flow.numpy() if hasattr(gt_flow, 'numpy') else np.array(gt_flow)
        gt_rgb = uflow_plotting.flow_to_rgb(gt_np)
        if gt_rgb.dtype != np.uint8:
            gt_rgb = (np.clip(gt_rgb, 0.0, 1.0) * 255).astype(np.uint8)

        strip = np.concatenate([img1_u8, img2_u8, flow_rgb, gt_rgb], axis=1)
        strip_path = os.path.join(FLAGS.output_dir, 'pair_%05d_strip.png' % idx)
        Image.fromarray(strip).save(strip_path)

        if (idx + 1) % 10 == 0 or idx == 0:
            print('Saved %d / %d -> %s' % (idx + 1, FLAGS.num_pairs, strip_path))

    print('Done. Results in:', FLAGS.output_dir)


if __name__ == '__main__':
    app.run(main)
