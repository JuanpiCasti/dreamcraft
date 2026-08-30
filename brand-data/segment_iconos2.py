from PIL import Image
import numpy as np

im = Image.open(r'C:\Users\roman\Downloads\iconos2.png').convert('RGBA')
w, h = im.size
print('Size:', w, h)

# Let's divide into a 4x4 or 3x4 grid and inspect average color and alpha
# Let's save a visual grid with borders:
debug_im = im.copy()
from PIL import ImageDraw
draw = ImageDraw.Draw(debug_im)

# Let's check non-transparent bounding boxes:
arr = np.array(im)
alpha = arr[:, :, 3]

# Check bounding box of whole content
ys, xs = np.where(alpha > 20)
print(f'Content bbox: x={xs.min()}..{xs.max()}, y={ys.min()}..{ys.max()}')

# Find distinct clusters along X and Y
x_prof = np.sum(alpha > 20, axis=0)
y_prof = np.sum(alpha > 20, axis=1)

# Threshold at 50px
print('X ranges with content:')
in_block = False
x_ranges = []
for x in range(w):
    if x_prof[x] > 50 and not in_block:
        start_x = x
        in_block = True
    elif x_prof[x] <= 50 and in_block:
        x_ranges.append((start_x, x))
        in_block = False
if in_block:
    x_ranges.append((start_x, w))

print('X ranges:', x_ranges)

in_block = False
y_ranges = []
for y in range(h):
    if y_prof[y] > 50 and not in_block:
        start_y = y
        in_block = True
    elif y_prof[y] <= 50 and in_block:
        y_ranges.append((start_y, y))
        in_block = False
if in_block:
    y_ranges.append((start_y, h))

print('Y ranges:', y_ranges)
