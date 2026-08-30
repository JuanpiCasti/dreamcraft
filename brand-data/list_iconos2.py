from PIL import Image
from pathlib import Path

im = Image.open('brand-data/iconos2_crops/cut_00.png')
print('iconos2 cut_00 size:', im.size)
cuts = sorted(list(Path('brand-data/iconos2_crops').glob('cut_*.png')))
print(f'Total cuts in iconos2_crops: {len(cuts)}')
for c in cuts:
    print(f'  {c.name}')
