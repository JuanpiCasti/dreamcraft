from pathlib import Path

for p in Path('resource-packs/packs/dreamcraft/assets').rglob('*.*'):
    if any(k in p.name.lower() for k in ['paper', 'scroll', 'doc', 'perm', 'policy', 'book']):
        print(p)
