from PIL import Image
import numpy as np

im = Image.open(r'C:\Users\roman\Downloads\iconos2.png')
print('Mode:', im.mode, 'Size:', im.size)
arr = np.array(im)
print('Shape:', arr.shape)
print('Corners RGB:', arr[0,0], arr[0,-1], arr[-1,0], arr[-1,-1])
