#!/usr/bin/env python3
"""KITTI2 inference — saves strips: img1 | img2 | predicted flow | GT flow.

Uses KITTI 2015 eval tfrecords (which include ground-truth flow).
Images come at native resolution (~370x1224); everything is resized to
H x W for display and inference.
"""

import os, sys
from absl import app, flags
import numpy as np
import tensorflow as tf
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from uflow.uflow_net import UFlow
from uflow import uflow_plotting
from uflow.data import kitti as kitti_data

FLAGS = flags.FLAGS
flags.DEFINE_string('checkpoint_dir', '', 'Checkpoint directory.')
flags.DEFINE_string('kitti_eval_dir', '', 'KITTI 2015 eval TFRecord directory (with GT flow).')
flags.DEFINE_string('output_dir', './inference_out/kitti2', 'Output directory.')
flags.DEFINE_integer('num_pairs', 200, 'Number of pairs to infer (max 200 for KITTI 2015).')
flags.DEFINE_integer('height', 256, 'Display/inference height.')
flags.DEFINE_integer('width', 256, 'Display/inference width.')


def to_uint8(arr):
    return (np.clip(arr, 0.0, 1.0) * 255).astype(np.uint8)


def flow_rgb(flow_hw2):
    rgb = uflow_plotting.flow_to_rgb(flow_hw2)
    if rgb.dtype != np.uint8:
        rgb = to_uint8(rgb)
    return rgb


def main(_):
    os.makedirs(FLAGS.output_dir, exist_ok=True)

    uflow = UFlow(checkpoint_dir=FLAGS.checkpoint_dir)
    # Warm-up: creates model variables on first @tf.function trace
    uflow.infer(image1=tf.zeros([FLAGS.height, FLAGS.width, 3]),
                image2=tf.zeros([FLAGS.height, FLAGS.width, 3]),
                input_height=FLAGS.height, input_width=FLAGS.width,
                resize_flow_to_img_res=True, infer_occlusion=False)
    # Manual restore: TF 2.18 / Keras 3 automatic restore silently fails
    def _get_obj(root, sub_path):
        obj = root
        for part in sub_path.split('/'):
            if not part: continue
            if part.isdigit():
                obj = obj[int(part)]
            elif part.startswith('layer_with_weights-'):
                obj = [l for l in obj.layers if l.weights][int(part.split('-')[1])]
            else:
                obj = getattr(obj, part)
        return obj
    ckpt_path = tf.train.latest_checkpoint(FLAGS.checkpoint_dir)
    if not ckpt_path:
        raise ValueError(f'No checkpoints in {FLAGS.checkpoint_dir}')
    reader = tf.train.load_checkpoint(ckpt_path)
    ROOT = {'feature_model': uflow._feature_model, 'flow_model': uflow._flow_model}
    keys = [k for k in reader.get_variable_to_shape_map()
            if '/.ATTRIBUTES/VARIABLE_VALUE' in k and '.OPTIMIZER_SLOT' not in k
            and k.split('/')[0] in ROOT]
    n_ok = 0
    for k in keys:
        path = k.replace('/.ATTRIBUTES/VARIABLE_VALUE', '')
        top = path.split('/')[0]
        sub = '/'.join(path.split('/')[1:])
        try:
            _get_obj(ROOT[top], sub).assign(reader.get_tensor(k))
            n_ok += 1
        except Exception:
            pass
    print(f'Restored {n_ok}/{len(keys)} weights from {ckpt_path}')

    # mode='eval' returns (images, flow_uv_occ, flow_uv_noc, flow_valid_occ, flow_valid_noc)
    # at native resolution — no height/width args allowed
    ds = kitti_data.make_dataset(FLAGS.kitti_eval_dir, mode='eval').prefetch(4)

    H, W = FLAGS.height, FLAGS.width

    for idx, batch in enumerate(ds.take(FLAGS.num_pairs)):
        images, flow_gt, _, flow_valid, _ = batch

        # Resize images to H x W for inference and display
        img1_nat = images[0]  # [H_nat, W_nat, 3]
        img2_nat = images[1]
        img1 = tf.image.resize(img1_nat[None], [H, W])[0]
        img2 = tf.image.resize(img2_nat[None], [H, W])[0]

        # Run inference (input already H x W)
        pred_flow = uflow.infer(
            image1=img1, image2=img2,
            input_height=H, input_width=W,
            resize_flow_to_img_res=True,
            infer_occlusion=False,
        )  # [H, W, 2] in model's [y, x] order

        # Swap predicted to [x, y] = [u, v] to match GT convention
        pred_uv = pred_flow.numpy()[..., ::-1]

        # Resize GT flow to H x W, scaling values proportionally
        nat_h = float(flow_gt.shape[0])
        nat_w = float(flow_gt.shape[1])
        scale_y = H / nat_h
        scale_x = W / nat_w
        gt_np = flow_gt.numpy()  # [H_nat, W_nat, 2] in [u, v] order
        gt_resized = tf.image.resize(flow_gt[None], [H, W])[0].numpy()
        gt_resized[..., 0] *= scale_x   # u (x displacement)
        gt_resized[..., 1] *= scale_y   # v (y displacement)

        # Also resize valid mask and zero out invalid GT pixels in display
        valid_resized = tf.image.resize(
            tf.cast(flow_valid, tf.float32)[None], [H, W],
            method='nearest')[0].numpy()[..., 0]  # [H, W]
        gt_display = gt_resized.copy()
        gt_display[valid_resized < 0.5] = 0.0

        # Build 4-panel strip
        strip = np.concatenate([
            to_uint8(img1.numpy()),
            to_uint8(img2.numpy()),
            flow_rgb(pred_uv),
            flow_rgb(gt_display),
        ], axis=1)

        Image.fromarray(strip).save(
            os.path.join(FLAGS.output_dir, 'pair_%05d_strip.png' % idx))

        if (idx + 1) % 50 == 0 or idx == 0:
            print(f'  {idx+1}/{min(FLAGS.num_pairs, 200)}')

    print('Done ->', FLAGS.output_dir)


if __name__ == '__main__':
    app.run(main)
