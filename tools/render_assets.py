"""Rasterize original Figma vector artwork; never rasterize UI text/controls.
Run: uv run --no-project --with resvg-py python tools/render_assets.py
"""
from pathlib import Path
import shutil
import resvg_py

root = Path(__file__).resolve().parents[1]
source = root / "design/assets"
target = root / "app/src/main/res/drawable-nodpi"
target.mkdir(parents=True, exist_ok=True)
for asset in source.glob("*.svg"):
    png = resvg_py.svg_to_bytes(svg_path=str(asset), zoom=3, skip_system_fonts=True)
    destination = target / ("figma_" + asset.stem + ".png")
    destination.write_bytes(png)
    print(destination.name, len(png))
shutil.copyfile(source / "monstera.png", target / "figma_monstera.png")
