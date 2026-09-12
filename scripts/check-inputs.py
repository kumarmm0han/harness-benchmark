#!/usr/bin/env python3
"""Verify frozen input hashes and exact template copies."""
import hashlib
from pathlib import Path

EXPECTED = {
    "PRINCIPLES.md": "b3b13f54581f60217a5ba67743dac622b79f369073456ee0ad6e8af2c2d63f06",
    "REQUIREMENTS.md": "2917d870d6141c772ce58a6e57839c963832cb02e687a70aed99429707f21beb",
    "spec.md": "567caf7763ae14218b9f21a3372a85cebe32430b5cc903b8bbeb490ea45ab016",
}
for name, expected in EXPECTED.items():
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest() == expected, f"Frozen input changed: {name}"
template = Path('spec.md').read_text().split('````markdown\n')[1].split('````')[0]
for name in ['backend/src/main/resources/template.md', 'frontend/src/template.md']:
    assert Path(name).read_text() == template, f"Template differs: {name}"
print('PASS: all three frozen input hashes and both exact template copies')
