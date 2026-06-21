# Benchmark Results - Iteration 1

## Summary

| Metric | With Skill | Without Skill | Delta |
|--------|-----------|--------------|-------|
| Pass Rate | 100.0% | 81.8% | +18.2% |
| Passed/Total | 11/11 | 9/11 | |

## Detail

### eval-0

| Assertion | With Skill | Without Skill |
|-----------|-----------|--------------|
| uses-abdm-instead-of-curl | PASS | FAIL |
| includes-start-flag | PASS | FAIL |
| correct-url-used | PASS | PASS |

### eval-1

| Assertion | With Skill | Without Skill |
|-----------|-----------|--------------|
| uses-abdm-instead-of-curl | PASS | PASS |
| uses-output-dir | PASS | PASS |
| uses-custom-filename | PASS | PASS |
| uses-connections-flag | PASS | PASS |
| includes-start-flag | PASS | PASS |

### eval-2

| Assertion | With Skill | Without Skill |
|-----------|-----------|--------------|
| uses-list-command | PASS | PASS |
| uses-info-command | PASS | PASS |
| no-curl-or-wget | PASS | PASS |

