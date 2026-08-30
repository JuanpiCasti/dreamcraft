from PIL import Image
import numpy as np

im = Image.open(r'C:\Users\roman\Downloads\iconos2.png').convert('RGBA')
arr = np.array(im)
print('Shape:', arr.shape)
a = arr[:, :, 3]
print('Alpha > 0 count:', np.sum(a > 0))
print('Alpha == 255 count:', np.sum(a == 255))
print('Unique alpha values:', len(np.unique(a)))

# Let's check a horizontal slice across the middle y=600
print('Middle row alpha:', np.unique(a[600, :]))
