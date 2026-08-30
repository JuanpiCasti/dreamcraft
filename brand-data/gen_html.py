from pathlib import Path
crops = sorted(list(Path("brand-data/prompts/icons_extracted").glob("*.png")))
html = ['<html><body style="background:#222; color:#eee; font-family:sans-serif;">', '<h1>Extracted Icons</h1>', '<div style="display:flex; flex-wrap:wrap; gap:10px;">']
for c in crops:
    html.append(f'<div style="background:#333; padding:8px; width:140px; text-align:center;"><img src="icons_extracted/{c.name}" style="width:100px; height:100px;"><br><small>{c.name}</small></div>')
html.append('</div></body></html>')
Path("brand-data/prompts/icons_overview.html").write_text('\n'.join(html), encoding='utf-8')
print('HTML OK')
