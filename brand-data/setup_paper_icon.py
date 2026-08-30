from PIL import Image
from pathlib import Path

vanilla_paper = Image.open('brand-data/assets/minecraft/textures/item/paper.png').convert('RGBA')
print('Vanilla paper size:', vanilla_paper.size)

# Scale up 2x (32x32) with nearest neighbor for crisp Minecraft look
paper_32 = vanilla_paper.resize((32, 32), Image.NEAREST)

dest_dir = Path('resource-packs/packs/dreamcraft/assets/dreamcraft/textures/item/menu')
paper_32.save(dest_dir / 'permissions.png')
paper_32.save(dest_dir / 'paper.png')
print('Saved clean vanilla paper pixel art to menu/permissions.png and menu/paper.png')
