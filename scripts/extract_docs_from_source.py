#!/usr/bin/env python3

"""
Extract Bandit pattern documentation directly from Python source files.
Generates clean markdown files and patterns.json with all pattern metadata.
"""

import json
import re
import sys
from pathlib import Path
from typing import List, Optional
from dataclasses import dataclass


@dataclass
class PatternInfo:
    pattern_id: str
    title: str
    description: str
    severity: str
    affected_items: List[str]  # functions, imports, calls, etc.
    full_docstring: str  # for detailed markdown
    description_for_json: str = ""  # rich description for JSON (can differ from markdown description)


def extract_docstring(file_path: Path) -> Optional[str]:
    """Extract module-level docstring from Python file."""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Match r""" ... """ or """ ... """
    match = re.search(r'r?"""(.*?)"""', content, re.DOTALL)
    if match:
        return match.group(1)

    match = re.search(r"r?'''(.*?)'''", content, re.DOTALL)
    if match:
        return match.group(1)

    return None


def _extract_pattern_id(docstring: str) -> Optional[str]:
    """Extract pattern ID from docstring."""
    match = re.search(r'[Bb](\d{3})(?:\W|$)', docstring)
    return f"B{match.group(1)}" if match else None


def _extract_title(pattern_id: str, docstring: str) -> str:
    """Extract and clean title from docstring."""
    match = re.search(rf'{pattern_id}:\s*(.+?)(?:\n|$)', docstring)
    return match.group(1).strip().strip('*_') if match else "Unknown"


def _extract_severity(docstring: str) -> str:
    """Extract and map severity level from docstring."""
    match = re.search(r'[Ss]everity:\s*(High|Medium|Low|INFO|WARNING|ERROR)', docstring)
    if not match:
        return "Warning"

    sev = match.group(1).lower()
    if sev in ['high', 'error']:
        return "Error"
    elif sev in ['low', 'info']:
        return "Info"
    else:
        return "Warning"


def _extract_affected_items(docstring: str) -> List[str]:
    """Extract affected items (functions, calls, imports) from docstring."""
    # Look for actual module/function names (with dots)
    matches = re.findall(r'(?:^|\s)([a-zA-Z_][a-zA-Z0-9_.]*(?:\.[a-zA-Z_][a-zA-Z0-9_]*)+)', docstring)
    # Filter: must have at least one dot, not private
    items = [item for item in matches if len(item.split('.')) >= 2 and not item.startswith('_')]
    return list(set(items))  # deduplicate


def _find_description_start(pattern_id: str, lines: List[str]) -> int:
    """Find the start index of description content."""
    start_idx = 0
    for i, line in enumerate(lines):
        if pattern_id in line:
            start_idx = i + 1
            break

    # Skip separator lines
    while start_idx < len(lines) and re.match(r'^[=\-*]+$', lines[start_idx].strip()):
        start_idx += 1

    return start_idx


def _is_section_marker(line: str) -> bool:
    """Check if line is a section marker (RST/Markdown)."""
    stripped = line.strip()
    return (
        re.match(r'^[:|\[]', stripped)
        or stripped.startswith('..')
        or stripped.startswith('Example')
        or stripped.startswith('Config')
    )


def _is_separator_line(line: str) -> bool:
    """Check if line is a separator/underline."""
    stripped = line.strip()
    return len(stripped) > 2 and all(c in '=-*+_' for c in stripped)


def _extract_description_for_markdown(pattern_id: str, docstring: str, title: str) -> str:
    """Extract brief description for markdown (first sentence/paragraph only).

    This is shown in markdown Description field to avoid duplication with Details.
    """
    lines = docstring.strip().split('\n')
    start_idx = _find_description_start(pattern_id, lines)

    description_lines = []

    for i in range(start_idx, len(lines)):
        line = lines[i].strip()

        # Stop at any blank line - keep description brief
        if not line:
            break

        # Skip separator lines
        if _is_separator_line(line):
            continue

        description_lines.append(line)

        # Stop after first complete sentence
        combined = ' '.join(description_lines)
        sentence_count = combined.count('.') + combined.count('!') + combined.count('?')
        if sentence_count >= 1:
            break

    description = ' '.join(description_lines)
    return description if description and len(description) > len(title) + 5 else title


def _extract_description_for_json(pattern_id: str, docstring: str, title: str) -> str:
    """Extract rich, detailed description for JSON (multiple paragraphs).

    This is used in description.json for better detail and searchability.
    """
    lines = docstring.strip().split('\n')
    start_idx = _find_description_start(pattern_id, lines)

    description_lines = []
    blank_line_count = 0

    for i in range(start_idx, len(lines)):
        line = lines[i].strip()

        # Stop at Config Options, Examples, or other major section markers
        if line.startswith('**Config') or line.startswith('Config') or line.startswith(':Example'):
            break
        if line.startswith('..') and ('code-block' in line or 'seealso' in line):
            break

        if not line:
            blank_line_count += 1
            if blank_line_count >= 2:  # Stop at two blank lines
                break
            continue
        else:
            blank_line_count = 0

        if _is_separator_line(line):
            continue

        description_lines.append(line)

    description = ' '.join(description_lines)
    return description if description and len(description) > len(title) else title


def parse_single_pattern_docstring(docstring: str, file_path: Path) -> List[PatternInfo]:
    """Parse docstring from a single plugin file (one pattern)."""
    pattern_id = _extract_pattern_id(docstring)
    if not pattern_id:
        return []

    title = _extract_title(pattern_id, docstring)
    severity = _extract_severity(docstring)
    affected_items = _extract_affected_items(docstring)
    description_md = _extract_description_for_markdown(pattern_id, docstring, title)
    description_json = _extract_description_for_json(pattern_id, docstring, title)

    pattern = PatternInfo(
        pattern_id=pattern_id,
        title=title,
        description=description_md,
        severity=severity,
        affected_items=affected_items,
        full_docstring=docstring,
        description_for_json=description_json
    )

    return [pattern]


def parse_blacklist_docstring(docstring: str) -> List[PatternInfo]:
    """Parse docstring from blacklist file (multiple patterns)."""
    patterns = []

    # Strategy: Find all pattern IDs and their associated info from table rows and headings
    # First extract section headings to get descriptions and relevant content
    section_content = {}
    section_descriptions = {}

    for match in re.finditer(r'(B\d{3}(?:\s*-\s*B\d{3})?:\s*([^\n]+))\n-+\n((?:(?!B\d{3}:)[^\n]|\n(?!B\d{3}:))*?)(?=\n\nB\d{3}:|$)', docstring, re.DOTALL):
        heading = match.group(1)
        section_text = match.group(3)

        # Extract just description lines (before tables)
        # For blacklist patterns, description is usually 1-3 sentences before the table
        desc_lines = []
        in_description = False
        for line in section_text.split('\n'):
            stripped = line.strip()

            # Skip initial blank lines
            if not in_description and not stripped:
                continue

            # Stop at table markers
            if stripped.startswith('+') or stripped.startswith('|'):
                break

            # Skip separator lines
            if stripped and all(c in '=-*+_' for c in stripped):
                continue

            # Collect non-empty lines as description
            if stripped:
                in_description = True
                desc_lines.append(stripped)
            elif in_description:
                # Stop at blank line after we've started collecting description
                break

        description = ' '.join(desc_lines)

        # Extract pattern ID(s) from heading - handle ranges like "B313 - B319"
        range_match = re.match(r'B(\d{3})\s*-\s*B(\d{3})', heading)
        if range_match:
            # Generate all IDs in the range
            start_num = int(range_match.group(1))
            end_num = int(range_match.group(2))
            ids = [f"B{i:03d}" for i in range(start_num, end_num + 1)]
        else:
            # Single pattern
            ids = re.findall(r'(B\d{3})', heading)

        for pid in ids:
            section_descriptions[pid] = description
            section_content[pid] = section_text

    # Now extract all individual patterns from table rows
    # Look for table rows like "| B301 | pickle | ... | Medium |"
    for match in re.finditer(r'\|\s*(B\d{3})\s*\|\s*(\S+)\s*\|(.+?)\|\s*(High|Medium|Low|high|medium|low)\s*\|', docstring, re.DOTALL):
        pattern_id = match.group(1)
        title = match.group(2).strip()
        items_text = match.group(3)
        severity_str = match.group(4).strip().lower()

        # Map severity to level: High->Error, Medium->Warning, Low->Info
        if severity_str in ['high', 'error']:
            severity = "Error"
        elif severity_str in ['medium', 'warning']:
            severity = "Warning"
        elif severity_str in ['low', 'info']:
            severity = "Info"
        else:
            severity = "Warning"

        # Extract affected items
        affected_items = re.findall(r'-\s+([a-zA-Z0-9_.]+)', items_text)
        affected_items = list(set(affected_items))

        # Get description from section if available, otherwise use title
        description = section_descriptions.get(pattern_id, title)

        # Get only the relevant section content for this pattern, not the entire docstring
        pattern_docstring = section_content.get(pattern_id, '')

        pattern = PatternInfo(
            pattern_id=pattern_id,
            title=title,
            description=description,
            severity=severity,
            affected_items=affected_items,
            full_docstring=pattern_docstring,  # Store only relevant section, not entire module docstring
            description_for_json=description  # For blacklist, use same description for both
        )

        # Avoid duplicates
        if not any(p.pattern_id == pattern_id for p in patterns):
            patterns.append(pattern)

    return patterns


def extract_from_plugins(plugins_dir: Path) -> List[PatternInfo]:
    """Extract patterns from all plugin files."""
    patterns = []

    for py_file in sorted(plugins_dir.glob('*.py')):
        if py_file.name == '__init__.py':
            continue

        # Try module-level docstring first
        docstring = extract_docstring(py_file)
        if docstring:
            parsed = parse_single_pattern_docstring(docstring, py_file)
            patterns.extend(parsed)

        # Also extract function-level docstrings with @test.test_id decorators
        with open(py_file, 'r', encoding='utf-8') as f:
            content = f.read()

        # Find all @test.test_id("BXXX") decorators and their associated function docstrings
        for match in re.finditer(r'@test\.test_id\("(B\d{3})"\)\s*def\s+\w+\s*\([^)]*\):\s*"""(.+?)"""', content, re.DOTALL):
            pattern_id = match.group(1)
            func_docstring = match.group(2)

            # Skip if already parsed from module docstring
            if any(p.pattern_id == pattern_id for p in patterns):
                continue

            parsed = parse_single_pattern_docstring(func_docstring, py_file)
            patterns.extend(parsed)

    return patterns


def extract_from_blacklists(blacklists_dir: Path) -> List[PatternInfo]:
    """Extract patterns from blacklist files."""
    patterns = []

    for py_file in sorted(blacklists_dir.glob('*.py')):
        if py_file.name == '__init__.py':
            continue

        docstring = extract_docstring(py_file)
        if docstring:
            parsed = parse_blacklist_docstring(docstring)
            patterns.extend(parsed)

    return patterns


def clean_rst_content(text: str) -> str:
    """Clean up RST formatting for markdown."""
    lines = []
    skip_until_empty = False

    for line in text.split('\n'):
        stripped = line.strip()

        # Skip RST directives
        if stripped.startswith('..') and (stripped.startswith('.. code-block') or
                                           stripped.startswith('.. seealso') or
                                           stripped.startswith('.. versionadded') or
                                           stripped.startswith('.. versionchanged')):
            skip_until_empty = True
            continue

        # Skip the content of RST directives
        if skip_until_empty:
            if stripped == '':
                skip_until_empty = False
            continue

        # Remove RST formatting
        # Skip ASCII table borders
        if re.match(r'^[\+\-\=\|]+$', stripped):
            continue

        # Remove reST link syntax
        stripped = re.sub(r':(\w+):`([^`]+)`', r'\2', stripped)

        # Remove reST reference syntax
        stripped = re.sub(r'`([^`]+) <([^>]+)>`_', r'\1', stripped)

        # Clean up excess whitespace
        if stripped:
            lines.append(stripped)

    return '\n'.join(lines)


def generate_markdown(pattern: PatternInfo, output_dir: Path) -> None:
    """Generate a markdown file for a pattern."""
    content = f"""# {pattern.pattern_id}: {pattern.title}

**Severity:** {pattern.severity}

## Description

{pattern.description}

"""

    if pattern.affected_items:
        content += """## Affected Items

"""
        for item in sorted(set(pattern.affected_items)):
            content += f"- `{item}`\n"
        content += "\n"

    # Add the full docstring content (cleaned up)
    if pattern.full_docstring.strip():
        content += """## Details

"""
        cleaned = clean_rst_content(pattern.full_docstring)
        content += cleaned

    output_file = output_dir / f"{pattern.pattern_id}.md"
    output_file.write_text(content)
    print(f"Generated {output_file.name}")


def generate_patterns_json(patterns: List[PatternInfo], output_file: Path, version: str = "1.9.4") -> None:
    """Generate patterns.json file."""
    pattern_specs = []

    for pattern in sorted(patterns, key=lambda p: p.pattern_id):
        spec = {
            "patternId": pattern.pattern_id,
            "level": pattern.severity,  # Use the severity level directly (Error, Warning, or Info)
            "category": "Security",
            "parameters": [],
            "languages": [],
            "enabled": True
        }
        pattern_specs.append(spec)

    tool_spec = {
        "name": "bandit",
        "version": version,
        "patterns": pattern_specs
    }

    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(tool_spec, f, indent=2)

    print(f"Generated {output_file.name} with {len(pattern_specs)} patterns")


def generate_description_json(patterns: List[PatternInfo], output_file: Path) -> None:
    """Generate description.json file with rich descriptions."""
    descriptions = []

    for pattern in sorted(patterns, key=lambda p: p.pattern_id):
        # Use rich description for JSON if available, otherwise use markdown description
        json_description = pattern.description_for_json if pattern.description_for_json else pattern.description
        desc = {
            "patternId": pattern.pattern_id,
            "title": pattern.title,
            "description": json_description,
            "parameters": []
        }
        descriptions.append(desc)

    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(descriptions, f, indent=2)

    print(f"Generated {output_file.name} with {len(descriptions)} descriptions")


def main():
    if len(sys.argv) < 2:
        print("Usage: python extract_docs_from_source.py <bandit_dir> [output_dir] [version]")
        print("Example: python extract_docs_from_source.py ./bandit ./docs 1.9.4")
        sys.exit(1)

    bandit_dir = Path(sys.argv[1])
    output_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("./docs")
    version = sys.argv[3] if len(sys.argv) > 3 else "1.9.4"

    if not bandit_dir.exists():
        print(f"Error: {bandit_dir} not found")
        sys.exit(1)

    plugins_dir = bandit_dir / "bandit" / "plugins"
    blacklists_dir = bandit_dir / "bandit" / "blacklists"

    if not plugins_dir.exists():
        print(f"Error: {plugins_dir} not found")
        sys.exit(1)

    print(f"Extracting from {plugins_dir}")
    print(f"Extracting from {blacklists_dir}")

    # Extract all patterns
    all_patterns = []
    all_patterns.extend(extract_from_plugins(plugins_dir))
    all_patterns.extend(extract_from_blacklists(blacklists_dir))

    print(f"\nFound {len(all_patterns)} total patterns")
    for p in sorted(all_patterns, key=lambda x: x.pattern_id):
        print(f"  {p.pattern_id}: {p.title}")

    # Create output directories
    description_dir = output_dir / "description"
    description_dir.mkdir(parents=True, exist_ok=True)

    # Generate outputs
    print("\nGenerating documentation...")
    for pattern in all_patterns:
        generate_markdown(pattern, description_dir)

    generate_patterns_json(all_patterns, output_dir / "patterns.json", version)
    generate_description_json(all_patterns, description_dir / "description.json")

    print(f"\nDone! Generated docs in {output_dir}")


if __name__ == "__main__":
    main()
