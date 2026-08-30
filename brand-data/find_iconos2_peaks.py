from PIL import Image
import numpy as np

im = Image.open(r'C:\Users\roman\Downloads\iconos2.png').convert('RGBA')
arr = np.array(im)
a = arr[:, :, 3]

# Create a small downscaled version of alpha
thumb = Image.fromarray((a > 100).astype(np.uint8) * 255)
thumb.thumbnail((300, 300))
thumb.save('brand-data/iconos2_alpha_thumb.png')

# Find row and col projections
row_proj = np.sum(a > 100, axis=1)
col_proj = np.sum(a > 100, axis=0)

# Print peaks in rows and cols
import scipy.signal
row_peaks, _ = scipy.signal.find_peaks(row_proj, distance=150, prominence=5000)
col_peaks, _ = scipy.signal.find_peaks(col_proj, distance=150, prominence=5000)

print('Row peaks (Y centers):', row_peaks)
print('Col peaks (X centers):', col_peaks)
