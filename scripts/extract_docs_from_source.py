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


def parse_single_pattern_docstring(docstring: str, file_path: Path) -> List[PatternInfo]:
    """Parse docstring from a single plugin file (one pattern)."""
    patterns = []

    # Extract pattern ID from decorator or docstring
    pattern_id_match = re.search(r'[Bb](\d{3})(?:\W|$)', docstring)
    if not pattern_id_match:
        return patterns

    pattern_id = f"B{pattern_id_match.group(1)}"

    # Extract title - usually on the first line or after the ID
    title_match = re.search(rf'{pattern_id}:\s*(.+?)(?:\n|$)', docstring)
    title = title_match.group(1).strip() if title_match else "Unknown"

    # Extract severity and map to level
    severity = "Warning"  # default
    severity_match = re.search(r'[Ss]everity:\s*(High|Medium|Low|INFO|WARNING|ERROR)', docstring)
    if severity_match:
        sev = severity_match.group(1).lower()
        if sev in ['high', 'error']:
            severity = "Error"
        elif sev in ['medium', 'warning']:
            severity = "Warning"
        elif sev in ['low', 'info']:
            severity = "Info"

    # Extract affected items (functions, calls, imports) - only from code blocks or examples
    affected_items = []
    # Look for actual module/function names (with dots)
    affected_matches = re.findall(r'(?:^|\s)([a-zA-Z_][a-zA-Z0-9_.]*(?:\.[a-zA-Z_][a-zA-Z0-9_]*)+)', docstring)
    affected_items = [item for item in affected_matches if len(item.split('.')) >= 2]  # Must have at least one dot
    affected_items = list(set(affected_items))  # deduplicate
    affected_items = [item for item in affected_items if not item.startswith('_')]  # Remove private items

    # Extract description - text after title until first section marker
    lines = docstring.strip().split('\n')

    # Skip title line
    start_idx = 0
    for i, line in enumerate(lines):
        if pattern_id in line:
            start_idx = i + 1
            break

    # Skip separator lines (===, ---, etc)
    while start_idx < len(lines) and re.match(r'^[=\-*]+$', lines[start_idx].strip()):
        start_idx += 1

    # Collect description until we hit a section marker or example
    description_lines = []
    for i in range(start_idx, len(lines)):
        line = lines[i].strip()
        if not line:
            continue
        # Stop at section markers
        if re.match(r'^[:|\[]', line) or line.startswith('..') or line.startswith('Example') or line.startswith('Config'):
            break
        # Skip lines that are too long (likely section underlines)
        if len(line) > 2 and all(c in '=-*+_' for c in line):
            continue
        description_lines.append(line)
        if len(description_lines) >= 3:  # Get first 3 lines
            break

    description = ' '.join(description_lines) if description_lines else title

    pattern = PatternInfo(
        pattern_id=pattern_id,
        title=title,
        description=description,
        severity=severity,
        affected_items=affected_items,
        full_docstring=docstring
    )
    patterns.append(pattern)
    return patterns


def parse_blacklist_docstring(docstring: str) -> List[PatternInfo]:
    """Parse docstring from blacklist file (multiple patterns)."""
    patterns = []

    # Strategy: Find all pattern IDs and their associated info from table rows and headings
    # First extract section headings to get descriptions
    section_descriptions = {}
    for match in re.finditer(r'(B\d{3}(?:\s*-\s*B\d{3})?:\s*([^\n]+))\n-+\n((?:(?!\n\n)[^\n]|\n(?!\n\n))*)', docstring, re.DOTALL):
        heading = match.group(1)
        section_desc_text = match.group(3)
        # Extract just description lines (before tables)
        desc_lines = []
        for line in section_desc_text.split('\n'):
            line = line.strip()
            if not line or line.startswith('+') or line.startswith('|'):
                break
            if not re.match(r'^[\s\-|+]+$', line):
                desc_lines.append(line)
        description = ' '.join(desc_lines)

        # Extract pattern ID(s) from heading
        ids = re.findall(r'(B\d{3})', heading)
        for pid in ids:
            section_descriptions[pid] = description

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

        pattern = PatternInfo(
            pattern_id=pattern_id,
            title=title,
            description=description,
            severity=severity,
            affected_items=affected_items,
            full_docstring=docstring  # Store full docstring for details
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
    """Generate description.json file."""
    descriptions = []

    for pattern in sorted(patterns, key=lambda p: p.pattern_id):
        desc = {
            "patternId": pattern.pattern_id,
            "title": pattern.title,
            "description": pattern.description,
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
