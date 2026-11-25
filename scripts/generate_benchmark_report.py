#!/usr/bin/env python3
import glob
import re
import sys
import os

def parse_benchmark_results(xml_pattern):
    files = glob.glob(xml_pattern)
    if not files:
        print(f"Warning: No test XML files found matching {xml_pattern}", file=sys.stderr)
        return ""

    sections = []
    header = [
        "## 🚀 RBAC 5 大业务管理器全接口性能 Benchmark 测试报告",
        "> 本报告由 CI 在真实 PostgreSQL 环境下自动执行基准测试生成，覆盖 `AdminMgr`, `ReviewMgr`, `SessionMgr`, `SoDMgr`, `OrgMgr` 所有 100% 对外暴露业务接口。",
        ""
    ]
    sections.extend(header)

    current_section = []
    for fl in files:
        with open(fl, "r", encoding="utf-8") as f:
            content = f.read()
            match = re.search(r"<system-out><!\[CDATA\[(.*?)\]\]></system-out>", content, re.DOTALL)
            if not match:
                continue
            for line in match.group(1).split("\n"):
                line = line.strip()
                if "业务接口基准评测" in line:
                    title = line.strip("= ")
                    current_section.append(f"\n### {title}\n")
                    current_section.append("| 业务接口 / 阶段 | 采样量 | 耗时 (ms) | 单次均值 (ms) | 吞吐量 (ops/s) |")
                    current_section.append("| :--- | :---: | :---: | :---: | :---: |")
                elif line.startswith("|") and "样本:" in line:
                    parts = [p.strip() for p in line.split("|") if p.strip()]
                    if len(parts) >= 5:
                        op_name = parts[0]
                        sample = parts[1].replace("样本:", "").strip()
                        elapsed = parts[2].replace("耗时:", "").replace("ms", "").strip()
                        avg = parts[3].replace("均值:", "").replace("ms", "").strip()
                        ops = parts[4].replace("吞吐:", "").replace("ops/s", "").strip()
                        current_section.append(f"| `{op_name}` | {sample} | {elapsed} ms | **{avg} ms** | **{ops}** |")

    sections.extend(current_section)
    sections.append("\n---\n*Report generated automatically by GitHub Actions CI workflow.*")
    return "\n".join(sections)

if __name__ == "__main__":
    pattern = sys.argv[1] if len(sys.argv) > 1 else "permission-core/build/test-results/test/TEST-com.minesunny.permission.core.benchmark.AllInterfacesComprehensiveBenchmarkTest.xml"
    out_file = sys.argv[2] if len(sys.argv) > 2 else "benchmark-report.md"
    report_md = parse_benchmark_results(pattern)
    with open(out_file, "w", encoding="utf-8") as f:
        f.write(report_md)
    print(f"Successfully generated benchmark markdown to {out_file}")
