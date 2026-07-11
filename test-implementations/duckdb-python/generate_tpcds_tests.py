"""
Generate TPC-DS test cases dynamically

This script generates test case definitions for all 99 TPC-DS queries
by discovering the plan files in the plans/ directory.
"""

import os
from pathlib import Path


def generate_tpcds_test_cases(test_suites_dir):
    """Generate test case definitions for all TPC-DS queries"""
    
    tpcds_dir = Path(test_suites_dir) / "tpcds"
    plans_dir = tpcds_dir / "plans"
    
    if not plans_dir.exists():
        print(f"Error: Plans directory not found: {plans_dir}")
        return []
    
    # Find all .bin files
    plan_files = sorted([f for f in os.listdir(plans_dir) if f.endswith('.bin')])
    
    print(f"Found {len(plan_files)} plan files in {plans_dir}")
    
    test_cases = []
    
    for plan_file in plan_files:
        # Extract query number (e.g., "q01" from "q01.bin")
        query_id = plan_file.replace('.bin', '')
        query_num = int(query_id.replace('q', ''))
        
        # Create test case definition
        test_case = {
            'id': query_id,
            'description': f'TPC-DS Query {query_num}',
            'complexity': 'MEDIUM',  # Default complexity
            'planBinary': f'plans/{plan_file}',
            'planJson': f'plans/{query_id}.json',
            'inputTables': [],  # Would need to be filled in with actual table requirements
            'expectedOutput': f'expected/{query_id}.csv'
        }
        
        test_cases.append(test_case)
    
    return test_cases


def print_test_cases_yaml(test_cases):
    """Print test cases in YAML format"""
    print("\n# Generated TPC-DS test cases:")
    print("testCases:")
    
    for tc in test_cases:
        print(f"  - id: \"{tc['id']}\"")
        print(f"    description: \"{tc['description']}\"")
        print(f"    complexity: \"{tc['complexity']}\"")
        print(f"    planBinary: \"{tc['planBinary']}\"")
        print(f"    planJson: \"{tc['planJson']}\"")
        print(f"    inputTables: []")
        print(f"    expectedOutput: \"{tc['expectedOutput']}\"")
        print()


if __name__ == "__main__":
    # Find test-suites directory
    script_dir = Path(__file__).parent
    test_suites_dir = script_dir.parent.parent / "test-suites"
    
    if not test_suites_dir.exists():
        print(f"Error: test-suites directory not found: {test_suites_dir}")
        exit(1)
    
    print(f"Scanning test-suites directory: {test_suites_dir}")
    
    # Generate test cases
    test_cases = generate_tpcds_test_cases(test_suites_dir)
    
    if test_cases:
        print(f"\n✅ Generated {len(test_cases)} TPC-DS test case definitions")
        print("\nTo use these test cases, you can:")
        print("1. Copy the output below to test-suites/tpcds/metadata.yaml")
        print("2. Or use the dynamic loader in the compliance runner")
        print("\n" + "=" * 70)
        print_test_cases_yaml(test_cases)

