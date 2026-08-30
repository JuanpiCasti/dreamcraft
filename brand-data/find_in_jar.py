import zipfile
from pathlib import Path

# Search in data/
jars = list(Path('data').glob('*.jar'))
print('Jars in data:', jars)
for j in jars:
    with zipfile.ZipFile(j) as z:
        for name in z.namelist():
            if 'paper.png' in name.lower():
                print(f'Found {name} in {j.name}')
                z.extract(name, 'brand-data/extracted_vanilla')
