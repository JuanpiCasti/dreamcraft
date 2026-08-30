from PIL import Image
from pathlib import Path

# Let's inspect the individual crops in icons_extracted and determine what each contains
# Let's create an image table: 7 columns, 7 rows, 160x160 each, on transparent background with labels
crops = sorted(list(Path('brand-data/prompts/icons_extracted').glob('*.png')))
table = Image.new('RGBA', (7 * 160, 7 * 160), (10, 10, 15, 255))

from PIL import ImageDraw
draw = ImageDraw.Draw(table)

for idx, c in enumerate(crops):
    col = idx % 7
    row = idx // 7
    im = Image.open(c).convert('RGBA')
    table.alpha_composite(im, (col * 160, row * 160))
    draw.rectangle([col * 160, row * 160, (col + 1) * 160 - 1, (row + 1) * 160 - 1], outline=(60, 60, 80, 255))
    draw.text((col * 160 + 4, row * 160 + 4), f'#{idx:02d}', fill=(255, 255, 0, 255))

table.save('brand-data/prompts/icons_grid.png')
print('Grid saved to brand-data/prompts/icons_grid.png')
