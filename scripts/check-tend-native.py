#!/usr/bin/env python3
"""Check native source closure and preview JS. This is NOT a Hoon compiler."""
import json
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
code = root / "code"
lock = json.loads((root / "dev/grubbery/dependencies.json").read_text())
assert re.fullmatch(r"[0-9a-f]{40}", lock["grubbery"]["commit"])
assert re.fullmatch(r"[0-9a-f]{64}", lock["pill"]["sha256"])
for path in code.rglob("*.hoon"):
    for target in re.findall(r"^/<\s+\S+\s+(\S+)", path.read_text(), re.M):
        imported = code / target.lstrip("/") if target.startswith("/") else path.parent / target
        if not imported.is_file():
            raise SystemExit(f"Missing native source import: {path.relative_to(root)} -> {target}")
for name, nexus in json.loads((code / "bill.json").read_text()).items():
    if not (code / "nex" / (nexus.lstrip("/") + ".hoon")).is_file():
        raise SystemExit(f"Missing bill nexus for {name}")
html = (code / "nex/tend/index.html").read_text()
with tempfile.TemporaryDirectory(prefix="tend-native-check-") as temp:
    script = Path(temp) / "preview.js"
    script.write_text(html.split("<script>", 1)[1].split("</script>", 1)[0])
    subprocess.run(["node", "--check", str(script)], check=True)
print("Native source imports, bill, dependency pins and preview JavaScript checked; Hoon compilation NOT performed.")
