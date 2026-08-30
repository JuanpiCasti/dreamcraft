from PIL import Image

im_sync = Image.open('brand-data/prompts/Sync-ui-conbg.png')
im_ref = Image.open('brand-data/prompts/referencia-menus.png')
print('Sync-ui-conbg:', im_sync.size)
print('referencia-menus:', im_ref.size)
