#!/usr/bin/env python3
"""Extract sourced word readings relevant to the bundled associated suggestions.

Pass jyut6ping3.words.dict.yaml and terra_pinyin.dict.yaml downloaded from the
pinned commits documented in assets/pronunciation/README.md.
"""
import argparse
from collections import defaultdict
import hashlib
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def marked_pinyin(syllable):
    syllable = syllable.lower().replace("v", "ü")
    match = re.fullmatch(r"([a-zü]+)([1-5])", syllable)
    if not match:
        return None
    base, tone = match[1], int(match[2])
    if tone == 5:
        return base
    vowel = next((v for v in "ae" if v in base), None)
    index = base.index(vowel) if vowel else base.index("o") if "ou" in base else max(base.rfind(v) for v in "iouü")
    if index < 0:
        return None
    marks = {"a": "āáǎà", "e": "ēéěè", "i": "īíǐì", "o": "ōóǒò", "u": "ūúǔù", "ü": "ǖǘǚǜ"}
    return base[:index] + marks[base[index]][tone - 1] + base[index + 1:]


def build(source, output, expected_hash, mandarin=False):
    if hashlib.sha256(source.read_bytes()).hexdigest() != expected_hash:
        raise ValueError("Dictionary SHA-256 mismatch; use the documented pinned source")
    wanted = set()
    active = False
    for line in (ROOT / "app/src/main/assets/associated-phrases.cin").read_text().splitlines():
        if line == "%chardef begin":
            active = True
        elif line == "%chardef end":
            active = False
        elif active and len(parts := line.split()) == 2:
            wanted.update((parts[1], "".join(parts)))
    choices = defaultdict(dict)
    for line in source.read_text().splitlines():
        parts = line.split("\t")
        if len(parts) < 2 or parts[0] not in wanted or len(parts[0]) < 2:
            continue
        word, reading = parts[:2]
        syllables = reading.split()
        if len(syllables) != len(word):
            continue
        if mandarin:
            syllables = [marked_pinyin(s) for s in syllables]
            if not all(syllables):
                continue
        elif not all(re.fullmatch(r"[a-z]{1,6}[1-6]", s) for s in syllables):
            continue
        weight = float(parts[2].rstrip("%")) if len(parts) > 2 and parts[2] else 0
        choices[word][" ".join(syllables)] = weight
    entries = {}
    for word, readings in choices.items():
        best = max(readings.values())
        winners = [reading for reading, weight in readings.items() if weight == best]
        # Unweighted alternative readings are not frequency ordered. Only use
        # an unambiguous word reading or a uniquely highest source weight.
        if len(winners) == 1:
            entries[word] = winners[0]
    output.write_text("# Rime word readings; see README.md for source and selection rules\n" +
                      "".join(f"{word}\t{reading}\n" for word, reading in sorted(entries.items())), encoding="utf-8")
    print(f"{output.name}: {len(entries):,} word readings")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("cantonese", type=Path)
    parser.add_argument("mandarin", type=Path)
    args = parser.parse_args()
    assets = ROOT / "app/src/main/assets/pronunciation"
    build(args.cantonese, assets / "jyutping-words.tsv", "54d174ad2bb997e4a678b7b076b84e4dc5914481b32467cdea3b43e88b7d5474")
    build(args.mandarin, assets / "pinyin-words.tsv", "2f881a239e09a61e5993e79257655f98f81c3789dc8aeea5f33477f710345b27", mandarin=True)
