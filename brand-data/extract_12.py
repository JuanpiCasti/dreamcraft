from PIL import Image
import numpy as np
from pathlib import Path

im = Image.open(r'C:\Users\roman\Downloads\iconos2.png').convert('RGBA')
arr = np.array(im)

y_rows = [(108, 439), (459, 789), (808, 1138)]
cols = [(19, 309), (313, 602), (606, 894), (898, 1192)]

out_dir = Path('brand-data/iconos2_12')
out_dir.mkdir(parents=True, exist_ok=True)

names = []
for r, (y0, y1) in enumerate(y_rows):
    for c, (x0, x1) in enumerate(cols):
        sub = im.crop((x0, y0, x1, y1))
        # Tight crop
        sub_arr = np.array(sub)
        a = sub_arr[:, :, 3] > 20
        if np.any(a):
            ys, xs = np.where(a)
            sub = sub.crop((xs.min(), ys.min(), xs.max()+1, ys.max()+1))
        # Make square
        w, h = sub.size
        s = max(w, h) + 4
        sq = Image.new('RGBA', (s, s), (0,0,0,0))
        sq.paste(sub, ((s - w)//2, (s - h)//2))
        
        # Check average RGB where alpha > 100
        rgb = np.array(sq)[:,:,:3]
        sq_a = np.array(sq)[:,:,3] > 100
        mean_r = rgb[:,:,0][sq_a].mean()
        mean_g = rgb[:,:,1][sq_a].mean()
        mean_b = rgb[:,:,2][sq_a].mean()
        
        p = out_dir / f'row{r}_col{c}.png'
        sq.save(p)
        print(f'R{r}_C{c}: size {w}x{h}, mean RGB=({mean_r:.1f}, {mean_g:.1f}, {mean_b:.1f}) -> {p.name}')
