#!/usr/bin/env python3
"""
Generate Substrait Compliance Leaderboard

This script aggregates compliance reports from multiple engines and generates
a leaderboard showing their compliance status.
"""

import json
import sys
import argparse
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Any, Optional


def load_compliance_report(report_path: Path) -> Optional[Dict[str, Any]]:
    """Load a compliance report from a JSON file."""
    try:
        with open(report_path, 'r') as f:
            return json.load(f)
    except Exception as e:
        print(f"Error loading {report_path}: {e}", file=sys.stderr)
        return None


def calculate_rank_emoji(rank: int) -> str:
    """Get emoji for rank position."""
    if rank == 1:
        return "🥇"
    elif rank == 2:
        return "🥈"
    elif rank == 3:
        return "🥉"
    else:
        return f"{rank}"


def get_status_emoji(pass_rate: float) -> str:
    """Get status emoji based on pass rate."""
    if pass_rate >= 95:
        return "🟢"
    elif pass_rate >= 85:
        return "🟡"
    elif pass_rate >= 70:
        return "🟠"
    else:
        return "🔴"


def format_pass_rate(pass_rate: float) -> str:
    """Format pass rate with color indicator."""
    status = get_status_emoji(pass_rate)
    return f"{status} {pass_rate:.1f}%"


def generate_leaderboard_markdown(reports: List[Dict[str, Any]], output_path: Path):
    """Generate leaderboard in Markdown format."""
    
    # Sort by pass rate (descending)
    sorted_reports = sorted(reports, key=lambda x: x.get('passRate', 0), reverse=True)
    
    # Generate markdown
    lines = [
        "# Substrait Compliance Leaderboard",
        "",
        f"Last Updated: {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}",
        "",
        "## Overall Rankings",
        "",
        "| Rank | Engine | Version | Pass Rate | Queries Passed | Status |",
        "|------|--------|---------|-----------|----------------|--------|"
    ]
    
    for i, report in enumerate(sorted_reports, 1):
        rank = calculate_rank_emoji(i)
        engine_name = report.get('engineName', 'Unknown')
        engine_version = report.get('engineVersion', 'Unknown')
        pass_rate = report.get('passRate', 0)
        passed = report.get('passed', 0)
        total = report.get('totalTests', 0)
        status = format_pass_rate(pass_rate)
        
        lines.append(
            f"| {rank} | {engine_name} | {engine_version} | {pass_rate:.1f}% | "
            f"{passed}/{total} | {status} |"
        )
    
    # Add statistics section
    lines.extend([
        "",
        "## Statistics",
        "",
        f"- **Total Engines**: {len(sorted_reports)}",
        f"- **Average Pass Rate**: {sum(r.get('passRate', 0) for r in sorted_reports) / len(sorted_reports):.1f}%",
        f"- **Highest Pass Rate**: {sorted_reports[0].get('passRate', 0):.1f}% ({sorted_reports[0].get('engineName', 'Unknown')})",
        f"- **Lowest Pass Rate**: {sorted_reports[-1].get('passRate', 0):.1f}% ({sorted_reports[-1].get('engineName', 'Unknown')})",
        ""
    ])
    
    # Add detailed results section
    lines.extend([
        "## Detailed Results",
        ""
    ])
    
    for report in sorted_reports:
        engine_name = report.get('engineName', 'Unknown')
        engine_version = report.get('engineVersion', 'Unknown')
        pass_rate = report.get('passRate', 0)
        passed = report.get('passed', 0)
        failed = report.get('failed', 0)
        skipped = report.get('skipped', 0)
        total = report.get('totalTests', 0)
        timestamp = report.get('timestamp', 'Unknown')
        
        lines.extend([
            f"### {engine_name} v{engine_version}",
            "",
            f"- **Pass Rate**: {format_pass_rate(pass_rate)}",
            f"- **Passed**: {passed}/{total}",
            f"- **Failed**: {failed}",
            f"- **Skipped**: {skipped}",
            f"- **Last Updated**: {timestamp}",
            ""
        ])
    
    # Add legend
    lines.extend([
        "## Legend",
        "",
        "- 🟢 Excellent (≥95%)",
        "- 🟡 Good (85-94%)",
        "- 🟠 Fair (70-84%)",
        "- 🔴 Needs Improvement (<70%)",
        "",
        "---",
        "",
        "*This leaderboard is automatically generated from compliance reports submitted by engine developers.*",
        "*To add your engine, implement the ComplianceEngine interface and submit your results.*",
        ""
    ])
    
    # Write to file
    with open(output_path, 'w') as f:
        f.write('\n'.join(lines))
    
    print(f"✅ Leaderboard generated: {output_path}")


def generate_leaderboard_json(reports: List[Dict[str, Any]], output_path: Path):
    """Generate leaderboard in JSON format."""
    
    # Sort by pass rate (descending)
    sorted_reports = sorted(reports, key=lambda x: x.get('passRate', 0), reverse=True)
    
    leaderboard = {
        "lastUpdated": datetime.utcnow().isoformat() + "Z",
        "totalEngines": len(sorted_reports),
        "averagePassRate": sum(r.get('passRate', 0) for r in sorted_reports) / len(sorted_reports) if sorted_reports else 0,
        "engines": []
    }
    
    for i, report in enumerate(sorted_reports, 1):
        leaderboard["engines"].append({
            "rank": i,
            "engineName": report.get('engineName', 'Unknown'),
            "engineVersion": report.get('engineVersion', 'Unknown'),
            "passRate": report.get('passRate', 0),
            "passed": report.get('passed', 0),
            "failed": report.get('failed', 0),
            "skipped": report.get('skipped', 0),
            "totalTests": report.get('totalTests', 0),
            "timestamp": report.get('timestamp', 'Unknown')
        })
    
    with open(output_path, 'w') as f:
        json.dump(leaderboard, f, indent=2)
    
    print(f"✅ Leaderboard JSON generated: {output_path}")


def main():
    parser = argparse.ArgumentParser(description='Generate Substrait Compliance Leaderboard')
    parser.add_argument('--input', '-i', type=Path, required=True,
                        help='Directory containing compliance report JSON files')
    parser.add_argument('--output', '-o', type=Path, required=True,
                        help='Output file path (markdown or json)')
    parser.add_argument('--format', '-f', choices=['markdown', 'json', 'both'], default='markdown',
                        help='Output format')
    
    args = parser.parse_args()
    
    # Load all compliance reports
    reports = []
    if args.input.is_dir():
        for report_file in args.input.glob('*.json'):
            report = load_compliance_report(report_file)
            if report:
                reports.append(report)
    else:
        print(f"Error: {args.input} is not a directory", file=sys.stderr)
        sys.exit(1)
    
    if not reports:
        print("Error: No valid compliance reports found", file=sys.stderr)
        sys.exit(1)
    
    print(f"📊 Loaded {len(reports)} compliance reports")
    
    # Generate leaderboard
    if args.format in ['markdown', 'both']:
        output_md = args.output if args.output.suffix == '.md' else args.output.with_suffix('.md')
        generate_leaderboard_markdown(reports, output_md)
    
    if args.format in ['json', 'both']:
        output_json = args.output if args.output.suffix == '.json' else args.output.with_suffix('.json')
        generate_leaderboard_json(reports, output_json)
    
    print("✅ Leaderboard generation complete")


if __name__ == '__main__':
    main()

