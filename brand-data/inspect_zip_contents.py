import zipfile

with zipfile.ZipFile('resource-packs/dist/dreamcraft-resource-pack-4c6bf9ea.zip') as z:
    for name in z.namelist():
        if any(k in name for k in ['invite', 'roles', 'ward_status', 'city_overview']):
            info = z.getinfo(name)
            print(f'{name}: size={info.file_size}')
