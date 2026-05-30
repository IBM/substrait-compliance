#!/usr/bin/env python3
"""
Generate Substrait Compliance Technical Report using LiteLLM and Claude

This script uses LiteLLM to call Claude (or other LLMs) to generate a
comprehensive technical report about the Substrait Compliance Framework.

Requirements:
    pip install litellm

Usage:
    python scripts/generate_technical_report.py --output docs/TECHNICAL_REPORT.md
    
    # With custom model
    python scripts/generate_technical_report.py --model claude-3-5-sonnet-20241022 --output report.md
    
    # With API key from environment
    export LITELLM_API_KEY=your_key_here
    python scripts/generate_technical_report.py
"""

import argparse
import json
import os
import sys
from pathlib import Path
from datetime import datetime
from typing import Dict, List, Any, Optional

try:
    from litellm import completion  # type: ignore[reportMissingImports]
except ImportError:
    print("Error: litellm is not installed. Install it with: pip install litellm")
    sys.exit(1)

# LiteLLM Configuration
LITELLM_BASE_URL = "https://ete-litellm.bx.cloud9.ibm.com"
LITELLM_API_KEY = os.getenv("LITELLM_API_KEY", "")
DEFAULT_MODEL = "aws/claude-sonnet-4-5"


class ProjectAnalyzer:
    """Analyze the Substrait Compliance project structure and gather information."""
    
    def __init__(self, project_root: Path):
        self.project_root = project_root
        
    def gather_project_info(self) -> Dict[str, Any]:
        """Gather comprehensive project information."""
        info = {
            "project_name": "Substrait Compliance Framework",
            "version": "1.0.0",
            "date": datetime.now().strftime("%Y-%m-%d"),
            "structure": self._analyze_structure(),
            "sdks": self._analyze_sdks(),
            "test_suites": self._analyze_test_suites(),
            "api": self._analyze_api(),
            "demo": self._analyze_demo(),
            "ci_cd": self._analyze_ci_cd(),
            "documentation": self._analyze_documentation(),
            "statistics": self._calculate_statistics()
        }
        return info
    
    def _analyze_structure(self) -> Dict[str, Any]:
        """Analyze project directory structure."""
        structure = {
            "root_dirs": [],
            "key_files": []
        }
        
        if self.project_root.exists():
            structure["root_dirs"] = [d.name for d in self.project_root.iterdir() if d.is_dir()]
            structure["key_files"] = [f.name for f in self.project_root.iterdir() if f.is_file()]
        
        return structure
    
    def _analyze_sdks(self) -> Dict[str, Any]:
        """Analyze SDK implementations."""
        sdks = {}
        sdk_dir = self.project_root / "sdk"
        
        if sdk_dir.exists():
            for lang_dir in sdk_dir.iterdir():
                if lang_dir.is_dir():
                    sdks[lang_dir.name] = {
                        "exists": True,
                        "path": str(lang_dir.relative_to(self.project_root))
                    }
        
        return sdks
    
    def _analyze_test_suites(self) -> Dict[str, Any]:
        """Analyze test suite coverage."""
        test_suites = {
            "tpch": {"exists": False, "queries": 0},
            "functions": {"exists": False, "categories": 0, "files": 0}
        }
        
        # Check TPC-H
        tpch_dir = self.project_root / "test-suites" / "tpch"
        if tpch_dir.exists():
            test_suites["tpch"]["exists"] = True
            plans_dir = tpch_dir / "plans"
            if plans_dir.exists():
                test_suites["tpch"]["queries"] = len([f for f in plans_dir.glob("*.bin")])
        
        # Check Functions
        functions_dir = self.project_root / "test-suites" / "functions"
        if functions_dir.exists():
            test_suites["functions"]["exists"] = True
            test_suites["functions"]["categories"] = len([d for d in functions_dir.iterdir() if d.is_dir()])
            test_suites["functions"]["files"] = len(list(functions_dir.rglob("*.test")))
        
        return test_suites
    
    def _analyze_api(self) -> Dict[str, Any]:
        """Analyze REST API implementation."""
        api_info = {"exists": False}
        api_dir = self.project_root / "api"
        
        if api_dir.exists():
            api_info["exists"] = True
            api_info["has_dockerfile"] = (api_dir / "Containerfile").exists()
            api_info["has_compose"] = (api_dir / "docker-compose.yml").exists()
            api_info["has_readme"] = (api_dir / "README.md").exists()
        
        return api_info
    
    def _analyze_demo(self) -> Dict[str, Any]:
        """Analyze demo system."""
        demo_info = {"exists": False}
        demo_dir = self.project_root / "demo"
        
        if demo_dir.exists():
            demo_info["exists"] = True
            demo_info["has_dashboard"] = (demo_dir / "dashboard").exists()
            demo_info["has_engines"] = (demo_dir / "engines").exists()
            demo_info["has_runner"] = (demo_dir / "runner").exists()
        
        return demo_info
    
    def _analyze_ci_cd(self) -> Dict[str, Any]:
        """Analyze CI/CD workflows."""
        ci_cd_info = {"workflows": []}
        workflows_dir = self.project_root / ".github" / "workflows"
        
        if workflows_dir.exists():
            ci_cd_info["workflows"] = [f.stem for f in workflows_dir.glob("*.yml")]
        
        return ci_cd_info
    
    def _analyze_documentation(self) -> Dict[str, Any]:
        """Analyze documentation files."""
        docs = {"files": []}
        
        # Root level docs
        for pattern in ["*.md", "docs/*.md"]:
            docs["files"].extend([str(f.relative_to(self.project_root)) 
                                 for f in self.project_root.glob(pattern)])
        
        return docs
    
    def _calculate_statistics(self) -> Dict[str, Any]:
        """Calculate project statistics."""
        stats = {
            "total_test_files": 0,
            "total_workflows": 0,
            "total_docs": 0
        }
        
        # Count test files
        test_suites_dir = self.project_root / "test-suites"
        if test_suites_dir.exists():
            stats["total_test_files"] = len(list(test_suites_dir.rglob("*.test")))
        
        # Count workflows
        workflows_dir = self.project_root / ".github" / "workflows"
        if workflows_dir.exists():
            stats["total_workflows"] = len(list(workflows_dir.glob("*.yml")))
        
        # Count docs
        stats["total_docs"] = len(list(self.project_root.glob("**/*.md")))
        
        return stats


class TechnicalReportGenerator:
    """Generate technical report using LiteLLM and Claude."""
    
    def __init__(self, model: str = DEFAULT_MODEL, api_key: Optional[str] = None, base_url: Optional[str] = None):
        self.model = model
        # Use configured API key and base URL by default
        self.api_key = api_key or LITELLM_API_KEY
        self.base_url = base_url or LITELLM_BASE_URL
        
        # Set environment variables for litellm
        os.environ["LITELLM_API_KEY"] = self.api_key
        if self.base_url:
            os.environ["LITELLM_BASE_URL"] = self.base_url
    
    def generate_report(self, project_info: Dict[str, Any]) -> str:
        """Generate technical report using LLM."""
        
        prompt = self._create_prompt(project_info)
        
        try:
            print(f"Calling {self.model} to generate technical report...")
            print(f"Project info size: {len(json.dumps(project_info))} characters")
            
            response = completion(
                model=self.model,
                messages=[
                    {
                        "role": "system",
                        "content": "You are a technical writer creating comprehensive documentation for open-source software projects. Create detailed, well-structured technical reports with clear sections, tables, and examples."
                    },
                    {
                        "role": "user",
                        "content": prompt
                    }
                ],
                api_key=self.api_key,
                api_base=self.base_url,
                custom_llm_provider="openai",  # Treat as OpenAI-compatible
                temperature=0.3,  # Lower for more consistent output
                max_tokens=4000,
                timeout=120,
                force_timeout=120
            )
            
            report = response.choices[0].message.content
            print(f"Generated report: {len(report)} characters")
            return report
            
        except Exception as e:
            print(f"Error calling LLM: {e}")
            return self._generate_fallback_report(project_info)
    
    def _create_prompt(self, project_info: Dict[str, Any]) -> str:
        """Create detailed prompt for LLM."""
        
        prompt = f"""Create a comprehensive technical report for the Substrait Compliance Framework.

PROJECT INFORMATION:
{json.dumps(project_info, indent=2)}

REPORT REQUIREMENTS:

1. **Executive Summary**
   - Project overview and purpose
   - Key achievements and metrics
   - Current status

2. **Architecture Overview**
   - Decentralized compliance model
   - Core components and their interactions
   - Technology stack

3. **SDK Implementation**
   - Multi-language support (Java, Python, Rust)
   - Core interfaces and design patterns
   - Usage examples

4. **Test Suite Coverage**
   - TPC-H benchmark details
   - Function-level test coverage
   - Test format and structure

5. **REST API Infrastructure**
   - API architecture and features
   - Endpoints and authentication
   - Deployment options

6. **CI/CD Automation**
   - Workflow architecture
   - Automation benefits
   - Integration guide

7. **Interactive Demo System**
   - Demo architecture
   - Dashboard features
   - Quick start guide

8. **Project Statistics**
   - Codebase metrics
   - Test coverage
   - Performance metrics

9. **Benefits Analysis**
   - For engine developers
   - For Substrait community
   - For framework maintainers

10. **Technical Innovations**
    - Key innovations and their benefits
    - Design decisions

11. **Future Roadmap**
    - Short-term goals
    - Medium-term goals
    - Long-term vision

12. **Getting Started**
    - For engine developers
    - For framework contributors

13. **Conclusion**
    - Summary of achievements
    - Call to action

FORMAT:
- Use Markdown formatting
- Include tables for structured data
- Add code examples where appropriate
- Use emojis for visual appeal (✅, 🚀, 📊, etc.)
- Create clear section hierarchies
- Include metrics and statistics
- Make it professional and comprehensive

The report should be suitable for sharing with the Substrait community and serve as both technical documentation and a showcase of the framework's capabilities.
"""
        return prompt
    
    def _generate_fallback_report(self, project_info: Dict[str, Any]) -> str:
        """Generate a basic report if LLM call fails."""
        
        report = f"""# Substrait Compliance Framework - Technical Report

**Generated:** {project_info['date']}  
**Version:** {project_info['version']}

## Executive Summary

The Substrait Compliance Framework is a comprehensive testing infrastructure for validating Substrait implementations across query engines.

## Project Structure

### SDKs
{json.dumps(project_info['sdks'], indent=2)}

### Test Suites
{json.dumps(project_info['test_suites'], indent=2)}

### Statistics
{json.dumps(project_info['statistics'], indent=2)}

## Components

- **API**: {project_info['api']['exists']}
- **Demo**: {project_info['demo']['exists']}
- **CI/CD Workflows**: {len(project_info['ci_cd']['workflows'])}

## Documentation

Available documentation files:
{chr(10).join(f"- {doc}" for doc in project_info['documentation']['files'][:10])}

---

*Note: This is a fallback report. For a comprehensive report, ensure LiteLLM is properly configured with API access.*
"""
        return report


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Generate Substrait Compliance Technical Report using LiteLLM"
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("docs/TECHNICAL_REPORT.md"),
        help="Output file path (default: docs/TECHNICAL_REPORT.md)"
    )
    parser.add_argument(
        "--model",
        type=str,
        default=DEFAULT_MODEL,
        help=f"LLM model to use (default: {DEFAULT_MODEL})"
    )
    parser.add_argument(
        "--base-url",
        type=str,
        default=LITELLM_BASE_URL,
        help=f"LiteLLM base URL (default: {LITELLM_BASE_URL})"
    )
    parser.add_argument(
        "--project-root",
        type=Path,
        default=Path.cwd(),
        help="Project root directory (default: current directory)"
    )
    parser.add_argument(
        "--api-key",
        type=str,
        help="API key for LLM (or set ANTHROPIC_API_KEY env var)"
    )
    parser.add_argument(
        "--analyze-only",
        action="store_true",
        help="Only analyze project and print info (don't generate report)"
    )
    
    args = parser.parse_args()
    
    # Analyze project
    print(f"Analyzing project at: {args.project_root}")
    analyzer = ProjectAnalyzer(args.project_root)
    project_info = analyzer.gather_project_info()
    
    if args.analyze_only:
        print("\nProject Analysis:")
        print(json.dumps(project_info, indent=2))
        return 0
    
    # Generate report
    generator = TechnicalReportGenerator(
        model=args.model,
        api_key=args.api_key,
        base_url=args.base_url
    )
    report = generator.generate_report(project_info)
    
    # Save report
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with open(args.output, 'w') as f:
        f.write(report)
    
    print(f"\n✅ Technical report generated: {args.output}")
    print(f"   Report size: {len(report)} characters")
    print(f"   Lines: {len(report.splitlines())}")
    
    return 0


if __name__ == "__main__":
    sys.exit(main())

# Made with Bob
