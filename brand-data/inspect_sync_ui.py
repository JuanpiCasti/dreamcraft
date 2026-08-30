from PIL import Image

im = Image.open('brand-data/prompts/Sync-ui-conbg.png')
# In Sync-ui-conbg, the menu is centered.
# Let's inspect where the menu is located:
from PIL import ImageEnhance
# Let's find the bounding box of the menu inside 2048x1536
import numpy as np
arr = np.array(im)
# The frame is around 2018x1521
# Let's sample crops from the bottom row of slots
# In slot coordinates:
# Row 4, 5 has the buttons: Permisos, Matriz, Apagar, Transferir, Cerrar
w, h = im.size
print(f'Sync-ui-conbg size: {w}x{h}')
