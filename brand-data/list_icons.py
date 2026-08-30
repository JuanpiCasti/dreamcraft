from pathlib import Path
crops = sorted(list(Path('brand-data/prompts/icons_extracted').glob('*.png')))
print(f'Total extracted icons: {len(crops)}')
for i, c in enumerate(crops):
    print(f'  #{i:02d}: {c.name}')
