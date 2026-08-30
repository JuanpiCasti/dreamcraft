import zipfile
from PIL import Image
import io

with zipfile.ZipFile('resource-packs/dist/dreamcraft-resource-pack-4c6bf9ea.zip') as z:
    b_ward = z.read('assets/dreamcraft/textures/font/gui/bg_menu_ward_status.png')
    im_ward = Image.open(io.BytesIO(b_ward))
    print('bg_menu_ward_status size:', im_ward.size)
    
    b_city = z.read('assets/dreamcraft/textures/font/gui/bg_menu_city_overview.png')
    im_city = Image.open(io.BytesIO(b_city))
    print('bg_menu_city_overview size:', im_city.size)
