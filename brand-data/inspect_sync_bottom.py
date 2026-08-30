from PIL import Image
import numpy as np

# Let's inspect Sync-panel.png or Sync-ui-conbg.png
# We want to see where the buttons are located in the designer image
im = Image.open('brand-data/prompts/Sync-ui-conbg.png').convert('RGBA')
# The image is 2048x1536
# Let's check where the X is located in Sync-ui-conbg.png
# Find bright red or white X shapes in the bottom area:
w, h = im.size
crop_bottom = im.crop((w // 4, int(h * 0.55), int(w * 0.8), int(h * 0.95)))
crop_bottom.save('brand-data/prompts/sync_ui_bottom.png')
print('Saved sync_ui_bottom.png')
