from PIL import Image
from pathlib import Path

prompts = Path('brand-data/prompts')
for p in prompts.glob('*.png'):
    im = Image.open(p)
    print(f'{p.name:45s}: size={im.size}, mode={im.mode}')
