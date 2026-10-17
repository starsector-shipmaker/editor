#!/usr/bin/env python3
import os
import sys
import argparse
import re
from collections import defaultdict
import subprocess

SRC_DIR = "src/main/java"

def find_duplicates(min_lines=6):
    print(f"Scanning for duplicate code blocks (minimum {min_lines} lines)...\n")
    block_map = defaultdict(list)
    
    for root, _, files in os.walk(SRC_DIR):
        for file in files:
            if file.endswith(".java"):
                path = os.path.join(root, file)
                with open(path, 'r', encoding='utf-8') as f:
                    lines = f.readlines()
                
                # Clean lines for hashing (remove whitespace/comments for rough matching)
                clean_lines = [re.sub(r'//.*$', '', l).strip() for l in lines]
                
                for i in range(len(clean_lines) - min_lines + 1):
                    # Skip blocks that are entirely empty or just braces
                    block = tuple(clean_lines[i:i+min_lines])
                    if all(len(l) < 3 for l in block):
                        continue
                        
                    block_hash = hash(block)
                    block_map[block_hash].append((path, i + 1))
    
    found = False
    for block_hash, occurrences in block_map.items():
        if len(occurrences) > 1:
            # Filter out overlapping/adjacent duplicates in the same file to reduce noise
            unique_files = set([o[0] for o in occurrences])
            if len(unique_files) > 1:
                found = True
                print(f"Found identical block across {len(occurrences)} locations:")
                for path, line_num in occurrences:
                    print(f"  - {path}:{line_num}")
                print("-" * 40)
                
    if not found:
        print("No significant duplicates found.")

def check_standards():
    print("Checking against .agents/skills/starsector-coding-standards/SKILL.md...\n")
    violations = 0
    
    for root, _, files in os.walk(SRC_DIR):
        for file in files:
            if file.endswith(".java"):
                path = os.path.join(root, file)
                with open(path, 'r', encoding='utf-8') as f:
                    lines = f.readlines()
                    
                for i, line in enumerate(lines):
                    # Check for swallowed exceptions
                    if "catch (" in line or "catch(" in line:
                        catch_block = "".join(lines[i:i+3])
                        if "log.error" not in catch_block and "e.printStackTrace()" not in catch_block and "throw" not in catch_block:
                            print(f"[{path}:{i+1}] WARNING: Potentially swallowed exception (No log.error found immediately).")
                            violations += 1
                            
                    # Check for basic sysout (should use Log4j2)
                    if "System.out.print" in line:
                        print(f"[{path}:{i+1}] WARNING: System.out.println used. Should use Log4j2.")
                        violations += 1
                        
    print(f"\nScan complete. Found {violations} potential violations.")

def patch_regex(pattern, replacement, dry_run=False):
    print(f"Patching '{pattern}' -> '{replacement}'...\n")
    rx = re.compile(pattern)
    files_changed = 0
    
    for root, _, files in os.walk(SRC_DIR):
        for file in files:
            if file.endswith(".java"):
                path = os.path.join(root, file)
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
                    
                new_content, subs = rx.subn(replacement, content)
                
                if subs > 0:
                    print(f"Matched {subs} times in {path}")
                    if not dry_run:
                        with open(path, 'w', encoding='utf-8') as f:
                            f.write(new_content)
                    files_changed += 1
                    
    print(f"\nPatch complete. {'Would have changed' if dry_run else 'Changed'} {files_changed} files.")

def verify_build():
    print("Running maven verification build...")
    result = subprocess.run(["mvn", "package", "-DskipTests"], capture_output=True, text=True)
    if result.returncode == 0:
        print("Build SUCCESSFUL.")
    else:
        print("Build FAILED.")
        print(result.stdout[-1000:])
        
def main():
    parser = argparse.ArgumentParser(description="Starsector Shipmaker Multi-Use Toolbox")
    subparsers = parser.add_subparsers(dest="command", required=True)
    
    subparsers.add_parser("find-dupes", help="Scan the codebase for duplicate logic blocks")
    subparsers.add_parser("check-standards", help="Audit the codebase for starsector-coding-standards/SKILL.md violations")
    subparsers.add_parser("verify", help="Run a verification build (mvn package)")
    
    patch_parser = subparsers.add_parser("patch", help="Regex find and replace across the codebase")
    patch_parser.add_argument("pattern", help="Regex pattern to find")
    patch_parser.add_argument("replacement", help="Replacement string")
    patch_parser.add_argument("--dry-run", action="store_true", help="Preview changes without saving")
    
    args = parser.parse_args()
    
    if args.command == "find-dupes":
        find_duplicates()
    elif args.command == "check-standards":
        check_standards()
    elif args.command == "verify":
        verify_build()
    elif args.command == "patch":
        patch_regex(args.pattern, args.replacement, args.dry_run)

if __name__ == "__main__":
    main()
