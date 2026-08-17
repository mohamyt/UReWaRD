#!/usr/bin/env python3
"""Evaluate a UFlow checkpoint on Sintel, FlyingChairs, and/or KITTI 2015.

Appends one row per model to a CSV file (creates it if it doesn't exist).

Usage:
    python eval_model.py \
        --label my_model \
        --checkpoint_dir /path/to/ckpt \
        --output_csv results.csv \
        [--sintel_dir /path/to/sintel_tfrecords/training/clean] \
        [--chairs_dir /path/to/flying_chairs_tfrecords/test] \
        [--kitti_dir  /path/to/kitti2015_tfrecords/training]
"""

import csv
import os
import sys
from functools import partial

from absl import app, flags
import tensorflow as tf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gin
from uflow import uflow_augmentation
from uflow.uflow_net import UFlow
from uflow.data import sintel as sintel_mod
from uflow.data import generic_flow_dataset as chairs_mod
from uflow.data import kitti as kitti_mod
from uflow.data.data_utils import evaluate as eval_fn

FLAGS = flags.FLAGS
flags.DEFINE_string('label', '', 'Model label (goes into CSV row).')
flags.DEFINE_string('checkpoint_dir', '', 'Path to checkpoint directory.')
flags.DEFINE_string('output_csv', 'eval_results.csv', 'CSV file to append results to.')
flags.DEFINE_string('gin_config', 'standard.gin', 'Gin config file.')
flags.DEFINE_string('sintel_dir', '', 'Sintel clean training TFRecord directory.')
flags.DEFINE_string('chairs_dir', '', 'FlyingChairs test TFRecord directory.')
flags.DEFINE_string('kitti_dir', '', 'KITTI 2015 eval TFRecord directory.')
flags.DEFINE_integer('height', 256, 'Eval height.')
flags.DEFINE_integer('width', 256, 'Eval width.')

CSV_FIELDS = ['label', 'steps_k', 'sintel_epe', 'chairs_epe', 'kitti_epe']


def manual_restore(model, ckpt_dir):
    def _get_obj(root, sub_path):
        obj = root
        for part in sub_path.split('/'):
            if not part:
                continue
            if part.isdigit():
                obj = obj[int(part)]
            elif part.startswith('layer_with_weights-'):
                obj = [l for l in obj.layers if l.weights][int(part.split('-')[1])]
            else:
                obj = getattr(obj, part)
        return obj

    ckpt_path = tf.train.latest_checkpoint(ckpt_dir)
    if not ckpt_path:
        raise ValueError(f'No checkpoints in {ckpt_dir}')
    step_k = int(os.path.basename(ckpt_path).split('-')[1])
    reader = tf.train.load_checkpoint(ckpt_path)
    ROOT = {'feature_model': model._feature_model, 'flow_model': model._flow_model}
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
    print(f'Restored {n_ok}/{len(keys)} weights from ckpt-{step_k}', flush=True)
    return step_k


def main(_):
    if not FLAGS.label:
        raise ValueError('--label is required')
    if not FLAGS.checkpoint_dir:
        raise ValueError('--checkpoint_dir is required')
    if not any([FLAGS.sintel_dir, FLAGS.chairs_dir, FLAGS.kitti_dir]):
        raise ValueError('At least one of --sintel_dir, --chairs_dir, --kitti_dir must be set')

    H, W = FLAGS.height, FLAGS.width

    gin_path = FLAGS.gin_config
    if not os.path.isabs(gin_path):
        gin_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), gin_path)
    gin.parse_config_files_and_bindings([gin_path], [])

    build_selfsup = partial(uflow_augmentation.build_selfsup_transformations,
                            crop_height=0, crop_width=0,
                            max_shift_height=0, max_shift_width=0, resize=False)
    model = UFlow(checkpoint_dir='', build_selfsup_transformations=build_selfsup)

    # Warm-up: creates model variables on first @tf.function trace
    model.infer(tf.zeros([H, W, 3]), tf.zeros([H, W, 3]))
    step_k = manual_restore(model, FLAGS.checkpoint_dir)

    print(f'=== {FLAGS.label} [ckpt-{step_k}] ===', flush=True)

    sintel_epe = float('nan')
    chairs_epe = float('nan')
    kitti_epe  = float('nan')

    if FLAGS.sintel_dir:
        ds = sintel_mod.make_dataset(FLAGS.sintel_dir, mode='eval-occlusion').prefetch(1)
        m = eval_fn(model.infer, ds, H, W, progress_bar=False, plot_dir='',
                    num_plots=0, max_num_evals=10000, prefix='sintel', has_occlusion=True)
        sintel_epe = float(m.get('sintel-EPE', float('nan')))
        print(f'  Sintel EPE = {sintel_epe:.4f}', flush=True)

    if FLAGS.chairs_dir:
        ds = chairs_mod.make_dataset(FLAGS.chairs_dir, mode='eval').prefetch(1)
        m = eval_fn(model.infer, ds, H, W, progress_bar=False, plot_dir='',
                    num_plots=0, max_num_evals=1000, prefix='chairs', has_occlusion=False)
        chairs_epe = float(m.get('chairs-EPE', float('nan')))
        print(f'  Chairs EPE = {chairs_epe:.4f}', flush=True)

    if FLAGS.kitti_dir:
        ds = kitti_mod.make_dataset(FLAGS.kitti_dir, mode='eval').prefetch(1)
        m = kitti_mod.evaluate(model.infer, ds, H, W, progress_bar=False,
                               plot_dir='', num_plots=0, prefix='kitti')
        kitti_epe = float(m.get('kitti-EPE(occ)', float('nan')))
        print(f'  KITTI EPE  = {kitti_epe:.4f}', flush=True)

    row = {
        'label':      FLAGS.label,
        'steps_k':    step_k,
        'sintel_epe': f'{sintel_epe:.4f}' if sintel_epe == sintel_epe else '',
        'chairs_epe': f'{chairs_epe:.4f}' if chairs_epe == chairs_epe else '',
        'kitti_epe':  f'{kitti_epe:.4f}'  if kitti_epe  == kitti_epe  else '',
    }

    write_header = not os.path.exists(FLAGS.output_csv)
    with open(FLAGS.output_csv, 'a', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=CSV_FIELDS)
        if write_header:
            writer.writeheader()
        writer.writerow(row)

    print(f'Appended to {FLAGS.output_csv}', flush=True)


if __name__ == '__main__':
    app.run(main)
