from pathlib import Path

mc_dir = Path.home() / 'AppData/Roaming/.minecraft'
print('MC dir exists:', mc_dir.exists())
if mc_dir.exists():
    for p in mc_dir.rglob('*paper*.png'):
        print('Found:', p)
        break
