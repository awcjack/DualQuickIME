#!/usr/bin/env python3
"""Build offline character readings from the official, pinned Unihan release.

Download https://www.unicode.org/Public/17.0.0/ucd/Unihan.zip, then run:
    python3 scripts/build_pronunciations.py /path/to/Unihan.zip
"""

import argparse
import hashlib
from pathlib import Path
import re
import zipfile

VERSION = "17.0.0"
SHA256 = "f7a48b2b545acfaa77b2d607ae28747404ce02baefee16396c5d2d7a8ef34b5e"
ROOT = Path(__file__).resolve().parents[1]


def build(archive: Path, output: Path) -> None:
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
        raise ValueError("Expected the official Unicode 17.0.0 Unihan.zip (SHA-256 mismatch)")
    readings: dict[int, dict[str, str]] = {}
    frequent_mandarin: dict[int, str] = {}
    with zipfile.ZipFile(archive) as source:
        for filename in sorted(source.namelist()):
            if not filename.endswith(".txt"):
                continue
            for line in source.read(filename).decode("utf-8").splitlines():
                if not line or line.startswith("#"):
                    continue
                code, prop, value = line.split("\t", 2)
                if prop == "kHanyuPinlu":
                    frequencies = [re.fullmatch(r"([^\s()]+)\((\d+)\)", item) for item in value.split()]
                    if not frequencies or not all(frequencies):
                        raise ValueError(f"Unexpected Mandarin frequency data: {line}")
                    # Explicit usage counts, not dictionary entry order. Keep
                    # source order only to break equal-frequency ties.
                    frequent_mandarin[int(code[2:], 16)] = max(
                        frequencies, key=lambda match: int(match[2])
                    )[1]
                    continue
                if prop not in ("kCantonese", "kMandarin"):
                    continue
                values = value.split()
                # kCantonese is the customary Jyutping reading (one per
                # character in this pinned release). Do not assume arbitrary
                # multi-value properties are frequency ordered.
                if prop == "kCantonese":
                    if len(values) != 1 or not re.fullmatch(r"[a-z]{1,6}[1-6]", values[0]):
                        raise ValueError(f"Unexpected Cantonese reading: {line}")
                # kMandarin is the customary reading; with two values the first
                # is the mainland (zh-Hans/CN) preference, the second Taiwan's.
                readings.setdefault(int(code[2:], 16), {})[prop] = values[0]

    for code, reading in frequent_mandarin.items():
        readings.setdefault(code, {})["kMandarin"] = reading

    output.mkdir(parents=True, exist_ok=True)
    for prop, filename in (("kCantonese", "jyutping.tsv"), ("kMandarin", "pinyin.tsv")):
        entries = [f"{code:X}\t{values[prop]}\n" for code, values in sorted(readings.items()) if prop in values]
        selection = "kHanyuPinlu (highest frequency), otherwise kMandarin" if prop == "kMandarin" else prop
        header = f"# Unicode Unihan {VERSION}: {selection}; see pronunciation/README.md and UNICODE-LICENSE.txt\n"
        (output / filename).write_text(header + "".join(entries), encoding="utf-8")
        print(f"{filename}: {len(entries):,} characters")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "app/src/main/assets/pronunciation")
    args = parser.parse_args()
    build(args.archive, args.output)
