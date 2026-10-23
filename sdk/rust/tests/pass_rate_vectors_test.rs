//! The pass-rate vectors every SDK shares (sdk/conformance/pass-rate-vectors.csv).

use substrait_compliance::*;

#[test]
fn pass_rate_vectors() {
    let path = concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../conformance/pass-rate-vectors.csv"
    );
    let text = std::fs::read_to_string(path).expect("read pass-rate vectors");
    let mut rows = 0;
    for line in text
        .lines()
        .map(str::trim)
        .filter(|l| !l.is_empty() && !l.starts_with('#'))
        .skip(1)
    {
        let f: Vec<&str> = line.split(',').collect();
        assert_eq!(f.len(), 6, "bad vector {line}");
        let n: Vec<usize> = f[..5].iter().map(|v| v.parse().unwrap()).collect();
        if n[4] > 0 {
            continue; // no UNSUPPORTED status in the Rust SDK
        }
        let expected: f64 = f[5].parse().unwrap();
        let mut report = ComplianceReport::new("suite", "engine");
        let statuses = [
            TestStatus::Passed,
            TestStatus::Failed,
            TestStatus::Skipped,
            TestStatus::Error,
        ];
        for (status, count) in statuses.iter().zip(&n) {
            for i in 0..*count {
                report
                    .results
                    .push(ComplianceResult::new(format!("t{i}"), *status));
            }
        }
        let got = report.get_pass_rate();
        assert!(
            (got - expected).abs() < 1e-9,
            "vector {line}: got {got}, expected {expected}"
        );
        rows += 1;
    }
    assert!(rows > 0, "no vectors read");
}
