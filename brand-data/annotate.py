from PIL import Image, ImageDraw, ImageFont
import numpy as np

im = Image.open('brand-data/prompts/updatedsprites.png').convert('RGBA')
draw = ImageDraw.Draw(im)

# Load icons list and draw bounding boxes and labels:
crops_dir = 'brand-data/prompts/icons_extracted'
centers = [
  (111, 105), (305, 99), (489, 98), (668, 98), (918, 98), (1099, 96), (1280, 97), (1485, 117), (1663, 123),
  (110, 292), (307, 278), (490, 276), (669, 276), (918, 268), (1098, 269), (1277, 269), (1485, 337), (1663, 339),
  (106, 476), (307, 461), (490, 461), (668, 459), (917, 443), (1100, 443), (1279, 443), (1485, 560), (1660, 562),
  (104, 662), (302, 646), (489, 645), (667, 645), (916, 617), (1099, 616), (1279, 617),
  (337, 795), (499, 793), (659, 795), (824, 794), (977, 794), (1128, 793), (1272, 795), (1415, 791), (1560, 793)
]

for idx, (cx, cy) in enumerate(centers):
    draw.rectangle([cx - 75, cy - 75, cx + 75, cy + 75], outline=(0, 255, 0, 200), width=2)
    draw.text((cx - 70, cy - 70), f'#{idx:02d}', fill=(255, 255, 0, 255))

im.save('brand-data/prompts/updatedsprites_annotated.png')
print('Annotated sheet saved.')
