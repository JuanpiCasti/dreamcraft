from PIL import Image
import numpy as np
from pathlib import Path

# Let's inspect the crops in brand-data/prompts/icons_extracted
# Which ones look like paper/scroll/document? (mostly white/yellow/tan or scroll shaped)
crops = sorted(list(Path('brand-data/prompts/icons_extracted').glob('*.png')))
print(f'Checking {len(crops)} extracted icons...')

# Also let's check icons-city.png and iconos.estate.png if there is a paper/scroll
for p in Path('brand-data/prompts').glob('*.png'):
    print('Prompt image:', p.name, Image.open(p).size)
