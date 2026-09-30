#!/usr/bin/env python3
"""Read-only handoff server. Explicit loopback/tailnet bind; no directory browsing."""
import argparse
import html
import ipaddress
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
ARTIFACTS = ROOT / "mobile/artifacts"
APK = "tend-0.1.0-alpha02.apk"
DOCS = {
    "/README.md": ROOT / "mobile/README.md",
    "/QA.md": ROOT / "mobile/QA.md",
    "/DECISIONS.md": ROOT / "mobile/DECISIONS.md",
    "/THEMES.md": ROOT / "docs/TEND_THEMES.md",
}
STYLE = """body{font:17px/1.6 system-ui;margin:2rem auto;padding:0 1rem;max-width:1100px;background:#141c19;color:#d2dfd5}a{color:#94c8a6}h1,h2{line-height:1.2}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:20px}img{width:100%;height:330px;object-fit:contain;background:#0f1512;border-radius:12px}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:15px/1.6 ui-monospace,monospace}.download{display:inline-block;padding:12px 20px;border:1px solid #94c8a6;border-radius:10px}small{color:#809a87}"""


def page(title, content):
    return (f'<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>{html.escape(title)}</title><style>{STYLE}</style></head><body>{content}</body></html>').encode()


class Handler(BaseHTTPRequestHandler):
    def do_HEAD(self):
        self.do_GET(head=True)

    def do_GET(self, head=False):
        target = urlsplit(self.path).path
        images = {
            "/screenshots/" + p.relative_to(ARTIFACTS / "themes").as_posix(): p
            for p in (ARTIFACTS / "themes").rglob("*.png")
            if p.is_file() and not p.is_symlink()
        }
        content_type = "text/html; charset=utf-8"
        if target == "/":
            preferred = [
                "/screenshots/web/dark-1440x960.png",
                "/screenshots/web/appearance-settings-light.png",
                "/screenshots/android-pixel8/01-dark-home.png",
                "/screenshots/android-pixel8/02-light-draft.png",
                "/screenshots/web/calendar-light.png",
                "/screenshots/android-pixel8/03-light-calendar.png",
            ]
            gallery = "".join(f'<a href="{url}"><img loading="lazy" src="{url}" alt="{html.escape(url.split("/")[-1])}"><small>{html.escape(url.split("/")[-1])}</small></a>' for url in preferred if url in images)
            all_images = "".join(f'<li><a href="{html.escape(url)}">{html.escape(url.removeprefix("/screenshots/"))}</a></li>' for url in sorted(images))
            body = page("Tend alpha02", f'''<h1>Tend · ship-synchronized themes</h1><p>Android alpha02 follows your ship's Omarchy colors and light/dark mode. Install over alpha01 to keep your accounts.</p><p><a class="download" href="/{APK}">Download signed Android APK</a> · <a href="/{APK}.sha256">SHA-256</a></p><p>Web: Settings → Appearance. Android: Accounts → Appearance. Choose Tend default to opt out locally.</p><p><a href="/README.md">Installation</a> · <a href="/QA.md">Verification report</a> · <a href="/THEMES.md">Theme contract</a> · <a href="/DECISIONS.md">Decisions</a></p><p><small>Test colors below are QA examples, not new defaults. Gall logic and both plugins are unchanged. Physical GrapheneOS/Android 17 verification is still needed.</small></p><h2>Screenshots</h2><div class="grid">{gallery}</div><details><summary>All screenshots and viewport checks</summary><ul>{all_images}</ul></details>''')
        elif target in DOCS:
            body = page(target[1:], f'<p><a href="/">← Downloads</a></p><pre>{html.escape(DOCS[target].read_text())}</pre>')
        elif target in (f"/{APK}", f"/{APK}.sha256") and (ARTIFACTS / target[1:]).is_file():
            body = (ARTIFACTS / target[1:]).read_bytes()
            content_type = "application/vnd.android.package-archive" if target.endswith(".apk") else "text/plain; charset=utf-8"
        elif target in images:
            body = images[target].read_bytes()
            content_type = "image/png"
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'")
        if target.endswith(".apk"):
            self.send_header("Content-Disposition", f'attachment; filename="{APK}"')
        self.end_headers()
        if not head:
            self.wfile.write(body)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", required=True)
    parser.add_argument("--port", type=int, default=9999)
    args = parser.parse_args()
    address = ipaddress.ip_address(args.bind)
    if not (address.is_loopback or address in ipaddress.ip_network("100.64.0.0/10")):
        parser.error("Bind only to an explicit loopback or Tailscale IPv4 address")
    ThreadingHTTPServer((args.bind, args.port), Handler).serve_forever()
