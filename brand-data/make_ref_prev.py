from PIL import Image
import numpy as np

im = Image.open('brand-data/prompts/referencia-menus.png')
# Let's save a preview with grid
prev = im.resize((768, 512), Image.LANCZOS)
prev.save('brand-data/prompts/referencia_menus_preview.png')
print('Preview saved.')
