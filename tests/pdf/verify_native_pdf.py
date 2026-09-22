"""Verify a native Android PDF against the exact synthetic fixture manifest.

Usage: python verify_native_pdf.py /path/to/pulled/ui-evidence
Requires pypdf (see requirements.txt). Does not read production documents.
"""
import argparse
import json
from pathlib import Path


def verify_text(text: str, rows: list[dict]) -> None:
    """Require every full row exactly once, including all numeric/lifecycle cells."""
    normalized = " ".join(text.split())
    for row in rows:
        cells = row["cells"]
        block = " ".join(" ".join(cells + row["details"]).split())
        if normalized.count(block) != 1:
            raise ValueError(f"Missing, duplicate or changed PDF row: {cells[2]}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args()
    from pypdf import PdfReader

    manifest = json.loads((args.evidence / "lifecycle-expected.json").read_text())
    reader = PdfReader(args.evidence / "lifecycle.pdf", strict=True)
    if len(reader.pages) != manifest["pages"]:
        raise ValueError(f"Unexpected page count: {len(reader.pages)}")
    text = "\n".join(page.extract_text() for page in reader.pages)
    verify_text(text, manifest["rows"])
    print(json.dumps({"pages": len(reader.pages), "verified_rows": len(manifest["rows"]),
                      "all_cells_and_lifecycle": True}))


if __name__ == "__main__":
    main()
