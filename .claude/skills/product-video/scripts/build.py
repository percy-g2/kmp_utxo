#!/usr/bin/env python3
"""
product-video build.py — turn app screenshots into a self-contained product-video HTML page.

Reads a JSON config describing the app, scenes, and ticker; downscales each referenced
screenshot and inlines it (plus the display/body fonts) as data URIs into the bundled
template. The output is a single HTML file with zero external requests.

Usage:
    python build.py --config config.json --assets <screenshot-dir> --out video.html
    python build.py --config config.json --assets <dir> --out artifact.html --mode artifact

--mode standalone (default): a complete <!doctype html> document you can double-click.
--mode artifact: body-only content for publishing on claude.ai via the Artifact tool
                 (claude.ai adds the <head>/<meta charset> skeleton itself).

Needs Pillow (`pip install --break-system-packages pillow`). Config schema: see references/config-schema.md.
"""
import argparse, base64, io, json, re, sys
from pathlib import Path
from PIL import Image

SCRIPT_DIR = Path(__file__).resolve().parent


def img_data_uri(path, width, quality):
    im = Image.open(path).convert("RGB")
    h = round(im.height * width / im.width)
    im = im.resize((width, h), Image.LANCZOS)
    b = io.BytesIO()
    im.save(b, "JPEG", quality=quality, optimize=True, progressive=True)
    return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()


def font_data_uri(path):
    return "data:font/ttf;base64," + base64.b64encode(Path(path).read_bytes()).decode()


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--config", required=True)
    p.add_argument("--assets", required=True, help="directory holding the scene screenshots")
    p.add_argument("--out", required=True)
    p.add_argument("--mode", choices=["standalone", "artifact"], default="standalone")
    p.add_argument("--width", type=int, default=720, help="downscaled screenshot width (px)")
    p.add_argument("--quality", type=int, default=90, help="JPEG quality")
    p.add_argument("--template", default=str(SCRIPT_DIR / "template.html"))
    p.add_argument("--fonts", default=str(SCRIPT_DIR / "fonts"))
    args = p.parse_args()

    cfg = json.loads(Path(args.config).read_text())
    assets = Path(args.assets)
    scenes = cfg.get("scenes", [])
    if not scenes:
        sys.exit("config has no scenes")

    # inline each referenced screenshot once, keyed by its filename
    img = {}
    for s in scenes:
        name = s["img"]
        if name in img:
            continue
        f = assets / name
        if not f.exists():
            sys.exit(f"missing screenshot: {f}")
        img[name] = img_data_uri(f, args.width, args.quality)
        print(f"  inlined {name:26s} {len(img[name]) // 1024:4d} KB")

    fonts_dir = Path(args.fonts)
    font = {}
    for key, fn in [("black", "Inter-Black.ttf"), ("medium", "Inter-Medium.ttf")]:
        fp = fonts_dir / fn
        if fp.exists():
            font[key] = font_data_uri(fp)
        else:
            print(f"  (font {fn} not found — falling back to system-ui for that role)")

    payload = {"cfg": cfg, "img": img, "font": font}
    # ensure_ascii=True keeps the whole blob 7-bit — no charset dependency for glyphs,
    # arrows, or em dashes that arrive through the config.
    blob = "<script>window.__PV__ = " + json.dumps(payload, ensure_ascii=True) + ";</script>"

    tpl = Path(args.template).read_text(encoding="utf-8")
    if "<!--ASSETS_SLOT-->" not in tpl:
        sys.exit("template missing <!--ASSETS_SLOT--> marker")
    content = tpl.replace("<!--ASSETS_SLOT-->", blob)

    if args.mode == "standalone":
        content = re.sub(r"^\s*<title>.*?</title>\s*", "", content, count=1, flags=re.S)
        title = (cfg.get("app", {}).get("name", "Product") + " — Product Showcase")
        doc = (
            "<!doctype html>\n<html lang=\"en\">\n<head>\n"
            "<meta charset=\"utf-8\">\n"
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
            "<meta name=\"color-scheme\" content=\"dark\">\n"
            f"<title>{title}</title>\n</head>\n<body style=\"margin:0;background:#080b12\">\n"
            + content + "\n</body>\n</html>\n"
        )
        out = doc
    else:
        out = content

    Path(args.out).write_text(out, encoding="utf-8")
    print(f"wrote {args.out}  ({len(out.encode()) // 1024} KB, mode={args.mode})")


if __name__ == "__main__":
    main()
