#!/usr/bin/env python3
"""Generate Android strings.xml from the editable Excel catalog."""

from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path, PurePosixPath
from xml.etree import ElementTree as ET
from xml.sax.saxutils import escape

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PKG_REL_NS = {"r": "http://schemas.openxmlformats.org/package/2006/relationships"}


def column_index(reference: str) -> int:
    letters = re.match(r"[A-Z]+", reference).group(0)
    result = 0
    for letter in letters:
        result = result * 26 + ord(letter) - ord("A") + 1
    return result - 1


def cell_text(cell: ET.Element, shared: list[str]) -> str:
    cell_type = cell.get("t")
    if cell_type == "inlineStr":
        return "".join(node.text or "" for node in cell.findall(".//m:t", NS))
    value = cell.find("m:v", NS)
    raw = "" if value is None else value.text or ""
    if cell_type == "s" and raw:
        return shared[int(raw)]
    return raw


def read_catalog(workbook_path: Path) -> list[tuple[str, str]]:
    with zipfile.ZipFile(workbook_path) as archive:
        shared: list[str] = []
        if "xl/sharedStrings.xml" in archive.namelist():
            root = ET.fromstring(archive.read("xl/sharedStrings.xml"))
            shared = ["".join(node.text or "" for node in item.findall(".//m:t", NS)) for item in root.findall("m:si", NS)]

        workbook = ET.fromstring(archive.read("xl/workbook.xml"))
        sheet = next((item for item in workbook.findall(".//m:sheet", NS) if item.get("name") == "Android Strings"), None)
        if sheet is None:
            raise ValueError("Workbook must contain a sheet named 'Android Strings'.")
        relation_id = sheet.get(f"{{{REL_NS}}}id")
        relationships = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        target = next(item.get("Target") for item in relationships.findall("r:Relationship", PKG_REL_NS) if item.get("Id") == relation_id)
        normalized_target = target.lstrip("/")
        sheet_path = normalized_target if normalized_target.startswith("xl/") else str(PurePosixPath("xl") / normalized_target)
        root = ET.fromstring(archive.read(sheet_path))

        rows: list[list[str]] = []
        for row in root.findall(".//m:sheetData/m:row", NS):
            values: dict[int, str] = {}
            for cell in row.findall("m:c", NS):
                values[column_index(cell.get("r", "A1"))] = cell_text(cell, shared)
            width = max(values.keys(), default=-1) + 1
            rows.append([values.get(index, "") for index in range(width)])

    header_index = next((index for index, row in enumerate(rows) if row and row[0].strip() == "Key"), None)
    if header_index is None:
        raise ValueError("Could not find the Key header in the Android Strings sheet.")

    catalog: list[tuple[str, str]] = []
    seen: set[str] = set()
    for row_number, row in enumerate(rows[header_index + 1 :], start=header_index + 2):
        key = row[0].strip() if row else ""
        value = row[1] if len(row) > 1 else ""
        if not key and not value:
            continue
        if not re.fullmatch(r"[a-z][a-z0-9_]*", key):
            raise ValueError(f"Invalid Android key '{key}' on worksheet row {row_number}.")
        if key in seen:
            raise ValueError(f"Duplicate Android key '{key}' on worksheet row {row_number}.")
        if not value.strip():
            raise ValueError(f"English text is blank for '{key}' on worksheet row {row_number}.")
        seen.add(key)
        catalog.append((key, value))
    if not catalog:
        raise ValueError("The Android string catalog is empty.")
    return catalog


def android_escape(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\r\n", "\\n").replace("\n", "\\n")
    return escape(escaped, {">": "&gt;"})


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: sync_android_strings.py <app_strings.xlsx> <strings.xml>")
    workbook_path = Path(sys.argv[1]).resolve()
    output_path = Path(sys.argv[2]).resolve()
    catalog = read_catalog(workbook_path)
    lines = ["<?xml version=\"1.0\" encoding=\"utf-8\"?>", "<!-- Generated from outputs/app_strings/app_strings.xlsx. Do not edit directly. -->", "<resources>"]
    lines.extend(f'    <string name="{key}">{android_escape(value)}</string>' for key, value in catalog)
    lines.append("</resources>")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Generated {len(catalog)} Android strings from {workbook_path}")


if __name__ == "__main__":
    main()
