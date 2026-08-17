#!/usr/bin/env python3
"""FlyingChairs inference — saves strip images: img1 | img2 | pred flow | GT flow."""

import os, sys
from absl import app, flags
import numpy as np
from PIL import Image
import tensorflow as tf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from uflow.uflow_net import UFlow
from uflow import uflow_plotting
from uflow.data import generic_flow_dataset

FLAGS = flags.FLAGS
flags.DEFINE_string('checkpoint_dir', '', 'Checkpoint directory.')
flags.DEFINE_string('tfrecord_dir', '', 'FlyingChairs test TFRecord directory.')
flags.DEFINE_string('output_dir', './inference_out/chairs', 'Output directory.')
flags.DEFINE_integer('num_pairs', 50, 'Number of pairs to infer.')
flags.DEFINE_integer('height', 256, 'Inference height.')
flags.DEFINE_integer('width', 256, 'Inference width.')


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

    ds = generic_flow_dataset.make_dataset(
        FLAGS.tfrecord_dir, mode='eval',
        height=FLAGS.height, width=FLAGS.width,
        shuffle_buffer_size=0, seed=42,
        gt_flow_shape=[FLAGS.height, FLAGS.width, 2],
    )

    for idx, batch in enumerate(ds.take(FLAGS.num_pairs)):
        images = batch[0]
        img1 = np.clip(images[0].numpy(), 0.0, 1.0)
        img2 = np.clip(images[1].numpy(), 0.0, 1.0)

        flow = uflow.infer(
            image1=images[0], image2=images[1],
            input_height=FLAGS.height, input_width=FLAGS.width,
            resize_flow_to_img_res=True, infer_occlusion=False,
        )
        flow_rgb = uflow_plotting.flow_to_rgb(flow.numpy())
        if flow_rgb.dtype != np.uint8:
            flow_rgb = (np.clip(flow_rgb, 0.0, 1.0) * 255).astype(np.uint8)

        def u8(arr):
            return (arr * 255).astype(np.uint8)

        panels = [u8(img1), u8(img2), flow_rgb]

        if len(batch) >= 2:
            gt_flow = batch[1].numpy()
            gt_rgb = uflow_plotting.flow_to_rgb(gt_flow)
            if gt_rgb.dtype != np.uint8:
                gt_rgb = (np.clip(gt_rgb, 0.0, 1.0) * 255).astype(np.uint8)
            panels.append(gt_rgb)

        strip = np.concatenate(panels, axis=1)
        Image.fromarray(strip).save(
            os.path.join(FLAGS.output_dir, 'pair_%05d_strip.png' % idx))
        if (idx + 1) % 10 == 0 or idx == 0:
            print(f'  {idx+1}/{FLAGS.num_pairs}')

    print('Done ->', FLAGS.output_dir)


if __name__ == '__main__':
    app.run(main)
