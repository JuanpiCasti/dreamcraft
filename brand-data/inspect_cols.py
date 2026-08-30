from PIL import Image
import numpy as np

# Let's inspect the shapes of row2 (Sync) across the 4 columns
# Let's see where the highest intensity / white / distinct shapes are
for c in range(4):
    im = Image.open(f'brand-data/iconos2_12/row2_col{c}.png').convert('RGBA')
    # Let's save a visual representation or inspect
    print(f'Column {c}:')
    # Let's save 64x64 thumbs for row0, row1, row2
    for r in range(3):
        im_rc = Image.open(f'brand-data/iconos2_12/row{r}_col{c}.png')
        im_rc.resize((64, 64), Image.LANCZOS).save(f'brand-data/col{c}_r{r}.png')
print('Thumbnails saved for comparison.')
