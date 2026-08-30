import zipfile
from pathlib import Path

mc_versions = Path.home() / 'AppData/Roaming/.minecraft/versions'
for jar in mc_versions.rglob('*.jar'):
    try:
        with zipfile.ZipFile(jar) as z:
            if 'assets/minecraft/textures/item/paper.png' in z.namelist():
                print(f'Found paper.png in {jar.name}')
                z.extract('assets/minecraft/textures/item/paper.png', 'brand-data')
                break
    except Exception:
        pass
