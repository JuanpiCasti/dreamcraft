from PIL import Image
import urllib.request

# Check if there is an existing paper texture or create a pristine pixel-art parchment
from pathlib import Path

# Let's see if we have vanilla textures in any folder
vanilla_papers = list(Path('.').rglob('*paper*.png'))
print('Found paper pngs:', vanilla_papers)
