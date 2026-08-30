from PIL import Image
import numpy as np

im_old = Image.open('brand-data/prompts/Dreamcraft-menu-sprites.png').convert('RGBA')
im_new = Image.open('brand-data/prompts/updatedsprites.png').convert('RGBA')

# Old key icon crops according to graphic-assets.md:
old_keys = {
    'close': im_old.crop((1041-30, 366-30, 1041+30, 366+30)),
    'kick': im_old.crop((1041-30, 312-30, 1041+30, 312+30)),
    'invite': im_old.crop((1304-30, 313-30, 1304+30, 313+30)),
    'roles': im_old.crop((1041-30, 466-30, 1041+30, 466+30)),
    'members': im_old.crop((939-30, 466-30, 939+30, 466+30)),
    'confirm': im_old.crop((1150-30, 415-30, 1150+30, 415+30)),
    'deposit': im_old.crop((1370, 195, 1396, 223)),
}

# Compare each old key to the 43 new icons:
from pathlib import Path
crops_dir = Path('brand-data/prompts/icons_extracted')
new_crops = {}
for f in sorted(list(crops_dir.glob('*.png'))):
    new_crops[f.stem] = Image.open(f).convert('RGBA').resize((64, 64), Image.LANCZOS)

for k, old_im in old_keys.items():
    old_im64 = np.array(old_im.resize((64, 64), Image.LANCZOS), dtype=float)
    scores = []
    for name, n_im in new_crops.items():
        n_arr = np.array(n_im, dtype=float)
        # diff in normalized RGB
        diff = np.mean(np.abs(old_im64[:,:,:3] - n_arr[:,:,:3]))
        scores.append((diff, name))
    scores.sort()
    top = ', '.join([f'{s[1]} ({s[0]:.1f})' for s in scores[:4]])
    print(f'Old {k:10s} matches best with: {top}')
