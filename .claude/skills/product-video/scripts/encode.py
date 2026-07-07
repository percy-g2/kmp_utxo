#!/usr/bin/env python3
"""
product-video encode.py — render the built HTML into an MP4.

Renders each beat (intro, one per scene, outro) to a still PNG with headless Chrome using
the template's `#shot&...` capture deep-links, then encodes them into an H.264 MP4 with a
gentle Ken-Burns push per beat and crossfade dissolves between them (via ffmpeg).

Usage:
    python encode.py --html video.html --config config.json --out product-video.mp4
    python encode.py --html video.html --config config.json --out vertical.mp4 --portrait

Needs Chrome/Chromium and ffmpeg on PATH (or pass --chrome). Rendering uses file:// so the
HTML must be self-contained (as produced by build.py).
"""
import argparse, json, shutil, subprocess, sys, tempfile
from pathlib import Path

CHROME_CANDIDATES = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    shutil.which("google-chrome") or "", shutil.which("chromium") or "",
    shutil.which("chromium-browser") or "",
]


def find_chrome(override):
    if override:
        return override
    for c in CHROME_CANDIDATES:
        if c and Path(c).exists():
            return c
    sys.exit("Chrome/Chromium not found — pass --chrome <path>")


def render(chrome, html, frag, out, w, h, scale):
    subprocess.run([
        chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-color-profile=srgb",
        f"--force-device-scale-factor={scale}", f"--window-size={w},{h}",
        "--virtual-time-budget=4500", f"--screenshot={out}", f"file://{html}#{frag}",
    ], check=True, capture_output=True)


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--html", required=True)
    p.add_argument("--config", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--chrome", default=None)
    p.add_argument("--fps", type=int, default=30)
    p.add_argument("--scale", type=int, default=2, help="device-scale-factor for crisp text")
    p.add_argument("--portrait", action="store_true", help="1080x1920 vertical (Reels/Shorts) instead of 1920x1080")
    p.add_argument("--intro", type=float, default=2.8)
    p.add_argument("--hold", type=float, default=3.0)
    p.add_argument("--outro", type=float, default=4.2)
    p.add_argument("--xfade", type=float, default=0.6)
    p.add_argument("--crf", type=int, default=18)
    args = p.parse_args()

    chrome = find_chrome(args.chrome)
    ffmpeg = shutil.which("ffmpeg") or sys.exit("ffmpeg not found on PATH")
    html = str(Path(args.html).resolve())
    n = len(json.loads(Path(args.config).read_text()).get("scenes", []))
    if n == 0:
        sys.exit("config has no scenes")

    W, H = (1080, 1920) if args.portrait else (1920, 1080)
    beats = [("00_intro", "shot&intro", args.intro)]
    beats += [(f"{i:02d}", f"shot&s={i}", args.hold) for i in range(1, n + 1)]
    beats += [("99_outro", "shot&outro", args.outro)]

    tmp = Path(tempfile.mkdtemp(prefix="pv_"))
    print(f"rendering {len(beats)} frames at {W*args.scale}x{H*args.scale} -> {tmp}")
    for name, frag, _ in beats:
        render(chrome, html, frag, str(tmp / f"{name}.png"), W, H, args.scale)

    # stage 1: per-beat Ken Burns clip (capped with -frames:v so zoompan doesn't loop)
    clips = []
    for name, _, dur in beats:
        N = max(2, round(dur * args.fps)); inc = 0.05 / N
        vf = (f"zoompan=z='min(zoom+{inc:.6f}\\,1.05)':d={N}:"
              f"x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':fps={args.fps}:s={W}x{H},setsar=1,format=yuv420p")
        clip = str(tmp / f"{name}.mp4")
        subprocess.run([ffmpeg, "-y", "-loop", "1", "-i", str(tmp / f"{name}.png"), "-vf", vf,
                        "-frames:v", str(N), "-c:v", "libx264", "-crf", "12", "-pix_fmt", "yuv420p", clip],
                       check=True, capture_output=True)
        clips.append((clip, dur))

    # stage 2: crossfade the clips + fade from/to black
    ins, fc, prev, running = [], [], "0:v", clips[0][1]
    for c, _ in clips:
        ins += ["-i", c]
    for i in range(1, len(clips)):
        off = running - args.xfade; out = f"x{i}"
        fc.append(f"[{prev}][{i}:v]xfade=transition=fade:duration={args.xfade}:offset={off:.3f}[{out}]")
        running += clips[i][1] - args.xfade; prev = out
    fc.append(f"[{prev}]fade=t=in:st=0:d=0.5,fade=t=out:st={running - 0.6:.3f}:d=0.6,format=yuv420p[v]")
    subprocess.run([ffmpeg, "-y", *ins, "-filter_complex", ";".join(fc), "-map", "[v]",
                    "-c:v", "libx264", "-crf", str(args.crf), "-preset", "medium",
                    "-pix_fmt", "yuv420p", "-movflags", "+faststart", args.out],
                   check=True, capture_output=True)
    shutil.rmtree(tmp, ignore_errors=True)
    print(f"wrote {args.out}  ({round(running, 1)}s, {W}x{H})")


if __name__ == "__main__":
    main()
