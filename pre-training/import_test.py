import tensorflow as tf
import tensorflow_addons as tfa
import gin
import absl
import imageio
import numpy as np
import cv2

from uflow.uflow_net import UFlow
from uflow.uflow_model import PWCFlow, PWCFeaturePyramid
from uflow.data.generic_flow_dataset import make_dataset
from uflow import uflow_augmentation

print("TF:", tf.__version__, "| TFA:", tfa.__version__)
print("GPU devices:", tf.config.list_physical_devices('GPU'))
print("Pre-training imports: OK")
