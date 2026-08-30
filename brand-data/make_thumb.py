from PIL import Image
im = Image.open(r'C:\Users\roman\Downloads\iconos2.png')
im.thumbnail((400, 400))
im.save('brand-data/iconos2_thumb.png')
print('Saved brand-data/iconos2_thumb.png size:', im.size)
