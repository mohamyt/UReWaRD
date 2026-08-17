#!/usr/bin/env python3
"""Create data-efficiency TFRecord subsets for Sintel, FlyingChairs, and KITTI.

For Sintel and Chairs (single TFRecord file each): streams the first N records
into a new file — no RAM load, no data shuffle bias.

For KITTI (one TFRecord per video): creates symlinks to the first N sorted files,
so no extra disk space is needed.

Usage:
    python create_de_subsets.py \
        --sintel_src /path/to/sintel_tfrecords/training/clean/sintel@0.tfrecord \
        --sintel_out /path/to/de_tfrecords/sintel \
        --chairs_src /path/to/flying_chairs_tfrecords/train/flying_chairs@0.tfrecord \
        --chairs_out /path/to/de_tfrecords/chairs \
        --kitti_src  /path/to/kitti_raw_train_384x1280-tfrecords \
        --kitti_out  /path/to/de_tfrecords/kitti \
        [--fracs 0.01,0.02,0.05,0.10,0.25,0.50]

Skip a dataset by leaving its --*_src empty.
"""

import glob
import os
import sys

from absl import app, flags
import tensorflow as tf

FLAGS = flags.FLAGS
flags.DEFINE_string('sintel_src', '', 'Sintel training TFRecord file.')
flags.DEFINE_string('sintel_out', '', 'Output directory for Sintel subsets.')
flags.DEFINE_string('chairs_src', '', 'Chairs training TFRecord file.')
flags.DEFINE_string('chairs_out', '', 'Output directory for Chairs subsets.')
flags.DEFINE_string('kitti_src', '', 'Directory containing KITTI raw train TFRecord files.')
flags.DEFINE_string('kitti_out', '', 'Output directory for KITTI subsets (uses symlinks).')
flags.DEFINE_string('fracs', '0.01,0.02,0.05,0.10,0.25,0.50',
                    'Comma-separated fractions to create.')


def pct_name(frac):
    pct = round(frac * 100)
    return f'{pct}pct'


def count_records(path):
    return sum(1 for _ in tf.data.TFRecordDataset([path]))


def write_subset(src_path, out_path, n):
    ds = tf.data.TFRecordDataset([src_path]).take(n)
    with tf.io.TFRecordWriter(out_path) as w:
        for rec in ds:
            w.write(rec.numpy())


def make_single_file_subsets(src_path, out_base, fracs, src_name):
    if not os.path.exists(src_path):
        raise FileNotFoundError(f'Not found: {src_path}')
    basename = os.path.basename(src_path)
    print(f'Counting {src_name} records (streaming)...')
    total = count_records(src_path)
    print(f'  {src_name} total: {total} records')
    for frac in fracs:
        n = max(1, int(total * frac))
        label = pct_name(frac)
        out_dir = os.path.join(out_base, label)
        out_file = os.path.join(out_dir, basename)
        if os.path.exists(out_file):
            print(f'  {src_name}/{label}: already exists, skipping')
            continue
        os.makedirs(out_dir, exist_ok=True)
        write_subset(src_path, out_file, n)
        size_mb = os.path.getsize(out_file) // (1024 * 1024)
        print(f'  {src_name}/{label}: {n} records -> {out_file} ({size_mb} MB)')


def make_kitti_subsets(src_dir, out_base, fracs):
    files = sorted(glob.glob(os.path.join(src_dir, '*.tfrecord')))
    if not files:
        raise FileNotFoundError(f'No .tfrecord files in {src_dir}')
    print(f'KITTI total files: {len(files)}')
    for frac in fracs:
        n = max(1, int(len(files) * frac))
        label = pct_name(frac)
        out_dir = os.path.join(out_base, label)
        os.makedirs(out_dir, exist_ok=True)
        created = 0
        for src in files[:n]:
            dst = os.path.join(out_dir, os.path.basename(src))
            if not os.path.lexists(dst):
                os.symlink(src, dst)
                created += 1
        print(f'  kitti/{label}: {n} files ({created} new symlinks) -> {out_dir}')


def main(_):
    fracs = [float(x) for x in FLAGS.fracs.split(',')]

    if FLAGS.sintel_src and FLAGS.sintel_out:
        make_single_file_subsets(FLAGS.sintel_src, FLAGS.sintel_out, fracs, 'sintel')
    elif FLAGS.sintel_src or FLAGS.sintel_out:
        print('WARNING: both --sintel_src and --sintel_out must be set; skipping Sintel')

    if FLAGS.chairs_src and FLAGS.chairs_out:
        make_single_file_subsets(FLAGS.chairs_src, FLAGS.chairs_out, fracs, 'chairs')
    elif FLAGS.chairs_src or FLAGS.chairs_out:
        print('WARNING: both --chairs_src and --chairs_out must be set; skipping Chairs')

    if FLAGS.kitti_src and FLAGS.kitti_out:
        make_kitti_subsets(FLAGS.kitti_src, FLAGS.kitti_out, fracs)
    elif FLAGS.kitti_src or FLAGS.kitti_out:
        print('WARNING: both --kitti_src and --kitti_out must be set; skipping KITTI')

    print('Done.')


if __name__ == '__main__':
    app.run(main)
