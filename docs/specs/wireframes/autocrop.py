import sys
from PIL import Image, ImageChops

PAD = 20
BG = (245, 246, 248)  # #f5f6f8

for path in sys.argv[1:]:
    img = Image.open(path).convert("RGB")
    bg = Image.new("RGB", img.size, BG)
    diff = ImageChops.difference(img, bg)
    bbox = diff.getbbox()
    if bbox is None:
        print(f"skip (all background): {path}")
        continue
    left, top, right, bottom = bbox
    left = max(0, left - PAD)
    top = max(0, top - PAD)
    right = min(img.width, right + PAD)
    bottom = min(img.height, bottom + PAD)
    cropped = img.crop((left, top, right, bottom))
    cropped.save(path)
    print(f"{path}: {img.size} -> {cropped.size}")
