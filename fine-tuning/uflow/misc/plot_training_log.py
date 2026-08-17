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

"""Plots training losses from a saved UFlow train.log file."""

import re
from pathlib import Path

from absl import app
from absl import flags
import matplotlib.pyplot as plt

FLAGS = flags.FLAGS

flags.DEFINE_string('log_path', '', 'Path to a UFlow train.log file.')
flags.DEFINE_string('output_path', '', 'Where to save the rendered loss plot.')


def main(unused_argv):
  pattern = re.compile(r'^(\d+) -- (.*)$')
  lines = Path(FLAGS.log_path).read_text().splitlines()

  epochs = []
  series = {}
  for line in lines:
    match = pattern.match(line.strip())
    if not match:
      continue
    epochs.append(int(match.group(1)))
    for item in match.group(2).split(', '):
      key, value = item.split(': ')
      try:
        numeric_value = float(value)
      except ValueError:
        continue
      series.setdefault(key, []).append(numeric_value)

  output_path = Path(FLAGS.output_path)
  output_path.parent.mkdir(parents=True, exist_ok=True)

  keys = [
      key for key in ('total-loss', 'census-loss', 'smooth2-loss')
      if key in series
  ]
  plt.figure(figsize=(10, 6))
  for key in keys:
    plt.plot(epochs[:len(series[key])], series[key], label=key)
  plt.xlabel('Epoch')
  plt.ylabel('Loss')
  plt.title('UFlow Training Loss')
  plt.grid(True, alpha=0.3)
  plt.legend()
  plt.tight_layout()
  plt.savefig(output_path, dpi=160)
  plt.close()
  print(output_path)


if __name__ == '__main__':
  app.run(main)
