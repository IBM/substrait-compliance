import { readFileSync } from 'fs';
import { join } from 'path';
import { ComplianceReport, ComplianceResult, TestStatus } from '../src/result';

// The pass-rate vectors every SDK shares (sdk/conformance/pass-rate-vectors.csv).
const lines = readFileSync(join(__dirname, '..', '..', 'conformance', 'pass-rate-vectors.csv'), 'utf8')
  .split(/\r?\n/)
  .map((l) => l.trim())
  .filter((l) => l !== '' && !l.startsWith('#'));
const vectors = lines.slice(1).map((l) => l.split(',').map(Number));

describe('pass rate vectors', () => {
  it('reads the vectors', () => {
    expect(vectors.length).toBeGreaterThan(0);
  });

  test.each(vectors)('%i passed, %i failed, %i skipped, %i error, %i unsupported -> %d%%',
    (passed, failed, skipped, error, unsupported, expected) => {
      const report = new ComplianceReport();
      const statuses: [TestStatus, number][] = [
        [TestStatus.PASSED, passed], [TestStatus.FAILED, failed], [TestStatus.SKIPPED, skipped],
        [TestStatus.ERROR, error], [TestStatus.UNSUPPORTED, unsupported],
      ];
      let id = 0;
      for (const [status, n] of statuses) {
        for (let i = 0; i < n; i++) report.addResult(new ComplianceResult(`t${id++}`, status));
      }
      expect(Math.abs(report.passRate() - expected)).toBeLessThan(1e-9);
    });
});
