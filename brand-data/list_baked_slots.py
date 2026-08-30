from pathlib import Path
for p in sorted(list(Path('brand-data/inspect_baked_slots').glob('*.png'))):
    print(p.name)
