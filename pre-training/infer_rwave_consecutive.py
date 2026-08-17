#!/usr/bin/env python3
# coding=utf-8
"""Infer optical flow on consecutive frames from a single rwave scene.
Strip: img1 | img2 | predicted flow.
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

FLAGS = flags.FLAGS

flags.DEFINE_string('checkpoint_dir', '', 'Path to checkpoint directory.')
flags.DEFINE_string('scene_dir', '', 'Directory containing consecutive PNG frames.')
flags.DEFINE_string('output_dir', './inference_out/rwave_consec', 'Output directory.')
flags.DEFINE_integer('height', 256, 'Inference height.')
flags.DEFINE_integer('width', 256, 'Inference width.')
flags.DEFINE_integer('frame_stride', 4, 'Gap between paired frames. Match training stride to avoid alternating forward/backward motion.')


def load_image(path, height, width):
    img = np.array(Image.open(path).convert('RGB').resize((width, height)))
    return tf.cast(img, tf.float32) / 255.0


def flow_to_rgb(flow):
    u, v = flow[..., 0], flow[..., 1]
    magnitude = np.sqrt(u ** 2 + v ** 2)
    angle = np.arctan2(v, u)
    max_mag = magnitude.max() + 1e-6

    hue = (angle + np.pi) / (2 * np.pi)
    sat = np.clip(magnitude / max_mag, 0, 1)
    val = np.ones_like(hue)

    h6 = hue * 6.0
    i = np.floor(h6).astype(np.int32) % 6
    f = h6 - np.floor(h6)
    p = val * (1 - sat)
    q = val * (1 - f * sat)
    t = val * (1 - (1 - f) * sat)

    rgb = np.zeros((*hue.shape, 3), dtype=np.float32)
    for idx, (r, g, b) in enumerate([(val, t, p), (q, val, p), (p, val, t),
                                      (p, q, val), (t, p, val), (val, p, q)]):
        mask = i == idx
        rgb[mask, 0] = r[mask]
        rgb[mask, 1] = g[mask]
        rgb[mask, 2] = b[mask]

    return (rgb * 255).clip(0, 255).astype(np.uint8)


def main(unused_argv):
    if not FLAGS.checkpoint_dir:
        raise ValueError('--checkpoint_dir must be specified.')
    if not FLAGS.scene_dir:
        raise ValueError('--scene_dir must be specified.')

    os.makedirs(FLAGS.output_dir, exist_ok=True)

    uflow = UFlow(checkpoint_dir=FLAGS.checkpoint_dir)
    index_files = glob.glob(os.path.join(FLAGS.checkpoint_dir, 'ckpt-*.index'))
    if not index_files:
        raise ValueError(f'No checkpoints found in {FLAGS.checkpoint_dir}')
    latest = max(index_files, key=lambda f: int(os.path.basename(f).split('-')[1].split('.')[0]))
    ckpt_path = latest[:-len('.index')]
    uflow._checkpoint.restore(ckpt_path).expect_partial()
    print('Checkpoint restored:', ckpt_path)

    frames = sorted(glob.glob(os.path.join(FLAGS.scene_dir, '*.png')))
    if len(frames) < FLAGS.frame_stride + 1:
        raise ValueError(f'Need at least {FLAGS.frame_stride + 1} PNG frames in {FLAGS.scene_dir}')
    stride = FLAGS.frame_stride
    num_pairs = len(frames) - stride
    print(f'Found {len(frames)} frames, stride={stride}, generating {num_pairs} pairs.')

    for pair_idx, idx in enumerate(range(0, num_pairs, stride)):
        img1 = load_image(frames[idx], FLAGS.height, FLAGS.width)
        img2 = load_image(frames[idx + stride], FLAGS.height, FLAGS.width)

        flow = uflow.infer_no_tf_function(
            image1=img1,
            image2=img2,
            input_height=FLAGS.height,
            input_width=FLAGS.width,
            infer_occlusion=False,
        )

        img1_u8 = (img1.numpy() * 255).clip(0, 255).astype(np.uint8)
        img2_u8 = (img2.numpy() * 255).clip(0, 255).astype(np.uint8)
        flow_rgb = flow_to_rgb(flow.numpy())

        strip = np.concatenate([img1_u8, img2_u8, flow_rgb], axis=1)
        out_path = os.path.join(FLAGS.output_dir, f'pair_{pair_idx:05d}_strip.png')
        Image.fromarray(strip).save(out_path)

    print(f'Done. Strips saved to {FLAGS.output_dir}')


if __name__ == '__main__':
    app.run(main)
