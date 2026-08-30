from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/10-bloque-synt.png').convert('RGBA')
arr = np.array(im)

# Let's inspect where alpha is fully opaque (255) in object 0 (around x=59..828, y=47..816)
obj0 = arr[47:816, 59:828]
opaque = obj0[:, :, 3] == 255
ys, xs = np.where(opaque)
print(f'Active fully opaque box: x={xs.min()}..{xs.max()} (w={xs.max()-xs.min()+1}), y={ys.min()}..{ys.max()} (h={ys.max()-ys.min()+1})')

obj1 = arr[48:816, 946:1715]
opaque1 = obj1[:, :, 3] == 255
ys1, xs1 = np.where(opaque1)
print(f'Inactive fully opaque box: x={xs1.min()}..{xs1.max()} (w={xs1.max()-xs1.min()+1}), y={ys1.min()}..{ys1.max()} (h={ys1.max()-ys1.min()+1})')
