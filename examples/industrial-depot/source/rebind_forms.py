#!/usr/bin/env python3
"""Rebind the depot to saved form exports after the editor assigns new IDs.

Example:
  python3 examples/industrial-depot/source/rebind_forms.py \
      --saved-forms /path/to/saved-form-exports --output /tmp/building-rebound.json

The directory must contain the forms exported *after saving* them in the
editor. Matching uses their full Lichtwerk/... names. No input file is modified.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path

PACKAGE = Path(__file__).resolve().parents[1]


def form_documents(value):
    if isinstance(value, dict) and isinstance(value.get('name'), str) and 'parts' in value and 'id' in value:
        yield value
    elif isinstance(value, dict) and isinstance(value.get('forms'), dict):
        yield from value['forms'].values()


def rebind(value, references):
    if isinstance(value, str):
        return references.get(value, value)
    if isinstance(value, list):
        return [rebind(item, references) for item in value]
    if isinstance(value, dict):
        return {key: rebind(item, references) for key, item in value.items()}
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--saved-forms', required=True, type=Path, help='Directory of saved form JSON exports, or library JSON file')
    parser.add_argument('--output', required=True, type=Path, help='New building JSON; must not already exist')
    args = parser.parse_args()
    if args.output.exists():
        parser.error(f'Output already exists: {args.output}')
    source = json.loads((PACKAGE / 'library.json').read_text())
    expected = {form['name']: reference for reference, form in source['forms'].items()}
    candidates = [args.saved_forms] if args.saved_forms.is_file() else sorted(args.saved_forms.glob('*.json'))
    saved = {}
    for path in candidates:
        for form in form_documents(json.loads(path.read_text(encoding='utf-8'))):
            name = form.get('name')
            if name not in expected:
                continue
            reference = f"{form['id']}@{form['version']}"
            if name in saved and saved[name] != reference:
                parser.error(f'Multiple saved versions named {name!r}; retain the intended export only')
            saved[name] = reference
    missing = sorted(set(expected) - set(saved))
    if missing:
        parser.error('Missing saved form exports:\n' + '\n'.join(missing))
    references = {old: saved[name] for name, old in expected.items()}
    building = json.loads((PACKAGE / 'building.json').read_text())
    result = rebind(building, references)
    text = json.dumps(result, ensure_ascii=False, separators=(',', ':')) + '\n'
    if len(text) > 250000:
        parser.error('Result exceeds the editor clipboard limit')
    with args.output.open('x', encoding='utf-8') as handle:
        handle.write(text)
    print(f'Wrote {args.output}; rebound {len(references)} form references, including roof families')


if __name__ == '__main__':
    main()
