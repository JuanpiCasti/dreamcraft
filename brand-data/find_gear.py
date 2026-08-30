from PIL import Image
import numpy as np

# Let's inspect Sync-ui-conbg.png and referencia-menus.png around the bottom right where 'apagar núcleo' is!
im = Image.open('brand-data/prompts/Sync-ui-conbg.png')
print('Sync-ui-conbg size:', im.size)
