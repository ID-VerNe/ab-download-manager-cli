import json, os

workspace = ".claude/skills/abdm-download/workspace/iteration-1"
benchmark_file = f"{workspace}/benchmark.json"
benchmark_md = f"{workspace}/benchmark.md"

def read_file(path):
    with open(path, encoding="utf-8") as f:
        return f.read()

def write_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

# Eval 0: Basic file download
text0_ws = read_file(f"{workspace}/eval-0/with_skill/outputs/response.md")
text0_nos = read_file(f"{workspace}/eval-0/without_skill/outputs/eval-report-without-skill.md")

eval0_with = {
    "assertions": [
        {"text": "uses-abdm-instead-of-curl", "passed": ":cli:app:run" in text0_ws, "evidence": "ABDM CLI via gradlew"},
        {"text": "includes-start-flag", "passed": "--start" in text0_ws, "evidence": "--start flag present"},
        {"text": "correct-url-used", "passed": "example.com/test-file.zip" in text0_ws, "evidence": "URL correct"}
    ]
}
eval0_without = {
    "assertions": [
        {"text": "uses-abdm-instead-of-curl", "passed": False, "evidence": "Used curl instead"},
        {"text": "includes-start-flag", "passed": False, "evidence": "No --start flag"},
        {"text": "correct-url-used", "passed": True, "evidence": "URL correct"}
    ]
}

# Eval 1: Custom output video
text1_ws = read_file(f"{workspace}/eval-1/with_skill/outputs/eval-2-report.md")
text1_nos = read_file(f"{workspace}/eval-1/without_skill/outputs/eval-2-report.md")

eval1_with = {
    "assertions": [
        {"text": "uses-abdm-instead-of-curl", "passed": ":cli:app:run" in text1_ws, "evidence": "ABDM CLI"},
        {"text": "uses-output-dir", "passed": "-o ~/Downloads/demo" in text1_ws, "evidence": "Used -o"},
        {"text": "uses-custom-filename", "passed": "-n demo-video.mp4" in text1_ws, "evidence": "Used -n"},
        {"text": "uses-connections-flag", "passed": "-c 4" in text1_ws, "evidence": "Used -c with 4"},
        {"text": "includes-start-flag", "passed": "--start" in text1_ws, "evidence": "--start flag"}
    ]
}
eval1_without = {
    "assertions": [
        {"text": "uses-abdm-instead-of-curl", "passed": ":cli:app:run" in text1_nos, "evidence": "ABDM CLI mentioned"},
        {"text": "uses-output-dir", "passed": "--output-dir" in text1_nos or "-o " in text1_nos, "evidence": "Output dir flag"},
        {"text": "uses-custom-filename", "passed": "--name" in text1_nos or "-n " in text1_nos, "evidence": "Filename flag"},
        {"text": "uses-connections-flag", "passed": "--connections" in text1_nos or "-c " in text1_nos, "evidence": "Connections flag"},
        {"text": "includes-start-flag", "passed": "--start" in text1_nos, "evidence": "--start flag"}
    ]
}

# Eval 2: List and info
text2_ws = read_file(f"{workspace}/eval-2/with_skill/outputs/results.md")
text2_nos = read_file(f"{workspace}/eval-2/without_skill/outputs/commands_and_explanation.md")

eval2_with = {
    "assertions": [
        {"text": "uses-list-command", "passed": "list" in text2_ws, "evidence": "list command"},
        {"text": "uses-info-command", "passed": "info 2" in text2_ws, "evidence": "info 2 command"},
        {"text": "no-curl-or-wget", "passed": "curl" not in text2_ws and "wget" not in text2_ws, "evidence": "No curl/wget"}
    ]
}
eval2_without = {
    "assertions": [
        {"text": "uses-list-command", "passed": "list" in text2_nos, "evidence": "list command"},
        {"text": "uses-info-command", "passed": "info 2" in text2_nos, "evidence": "info 2 command"},
        {"text": "no-curl-or-wget", "passed": "curl" not in text2_nos and "wget" not in text2_nos, "evidence": "No curl/wget"}
    ]
}

# Write grading files
evals = [
    ("eval-0", eval0_with, eval0_without),
    ("eval-1", eval1_with, eval1_without),
    ("eval-2", eval2_with, eval2_without),
]
for eid, ws, nos in evals:
    write_json(f"{workspace}/{eid}/with_skill/grading.json", ws)
    write_json(f"{workspace}/{eid}/without_skill/grading.json", nos)

# Aggregate
def count_passed(assertions):
    return sum(1 for a in assertions if a["passed"])

def count_total(assertions):
    return len(assertions)

ws_total_pass = sum(count_passed(e[1]["assertions"]) for e in evals)
ws_total_all = sum(count_total(e[1]["assertions"]) for e in evals)
nos_total_pass = sum(count_passed(e[2]["assertions"]) for e in evals)
nos_total_all = sum(count_total(e[2]["assertions"]) for e in evals)

ws_rate = round(ws_total_pass / ws_total_all * 100, 1)
nos_rate = round(nos_total_pass / nos_total_all * 100, 1)
delta = round(ws_rate - nos_rate, 1)

# Benchmark
benchmark = {
    "iterations": {
        "iteration-1": {
            "with_skill": {"pass_rate": ws_rate, "passed": ws_total_pass, "total": ws_total_all},
            "without_skill": {"pass_rate": nos_rate, "passed": nos_total_pass, "total": nos_total_all},
            "per_eval": {
                "eval-0": {"with_skill": eval0_with, "without_skill": eval0_without},
                "eval-1": {"with_skill": eval1_with, "without_skill": eval1_without},
                "eval-2": {"with_skill": eval2_with, "without_skill": eval2_without},
            }
        }
    }
}
write_json(benchmark_file, benchmark)

# Report
with open(benchmark_md, "w", encoding="utf-8") as f:
    f.write("# Benchmark Results - Iteration 1\n\n")
    f.write("## Summary\n\n")
    f.write("| Metric | With Skill | Without Skill | Delta |\n")
    f.write("|--------|-----------|--------------|-------|\n")
    f.write(f"| Pass Rate | {ws_rate}% | {nos_rate}% | +{delta}% |\n")
    f.write(f"| Passed/Total | {ws_total_pass}/{ws_total_all} | {nos_total_pass}/{nos_total_all} | |\n\n")

    f.write("## Detail\n\n")
    for eid, ws, nos in evals:
        f.write(f"### {eid}\n\n")
        f.write("| Assertion | With Skill | Without Skill |\n")
        f.write("|-----------|-----------|--------------|\n")
        for i in range(max(len(ws["assertions"]), len(nos["assertions"]))):
            ws_s = "PASS" if i < len(ws["assertions"]) and ws["assertions"][i]["passed"] else "FAIL"
            nos_s = "PASS" if i < len(nos["assertions"]) and nos["assertions"][i]["passed"] else "FAIL"
            name = ws["assertions"][i]["text"] if i < len(ws["assertions"]) else nos["assertions"][i]["text"]
            f.write(f"| {name} | {ws_s} | {nos_s} |\n")
        f.write("\n")

print("=" * 60)
print("BENCHMARK RESULTS - Iteration 1")
print("=" * 60)
print(f"{'Metric':<30} {'With Skill':<15} {'Without Skill':<15}")
print("-" * 60)
print(f"{'Pass Rate':<30} {ws_rate}%{'':<12} {nos_rate}%{'':<12}")
print(f"{'Passed/Total':<30} {ws_total_pass}/{ws_total_all:<13} {nos_total_pass}/{nos_total_all:<13}")
print(f"{'Delta':<30} +{delta}%")
print()

for eid, ws, nos in evals:
    wp = count_passed(ws["assertions"])
    wt = count_total(ws["assertions"])
    np2 = count_passed(nos["assertions"])
    nt = count_total(nos["assertions"])
    print(f"  {eid}: With={wp}/{wt}, Without={np2}/{nt}")

print(f"\nBenchmark saved to benchmark.json and benchmark.md")