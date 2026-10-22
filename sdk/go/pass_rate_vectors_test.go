package compliance

import (
	"bufio"
	"fmt"
	"math"
	"os"
	"strconv"
	"strings"
	"testing"
)

// The pass-rate vectors every SDK shares (sdk/conformance/pass-rate-vectors.csv).
func TestPassRateVectors(t *testing.T) {
	f, err := os.Open("../conformance/pass-rate-vectors.csv")
	if err != nil {
		t.Fatalf("open vectors: %v", err)
	}
	defer f.Close()
	scanner := bufio.NewScanner(f)
	header := true
	rows := 0
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if header {
			header = false
			continue
		}
		fields := strings.Split(line, ",")
		if len(fields) != 6 {
			t.Fatalf("bad vector %q", line)
		}
		counts := make([]int, 5)
		for i := 0; i < 5; i++ {
			if counts[i], err = strconv.Atoi(fields[i]); err != nil {
				t.Fatalf("bad vector %q: %v", line, err)
			}
		}
		expected, err := strconv.ParseFloat(fields[5], 64)
		if err != nil {
			t.Fatalf("bad vector %q: %v", line, err)
		}
		report := NewComplianceReport()
		statuses := []TestStatus{TestStatusPassed, TestStatusFailed, TestStatusSkipped, TestStatusError, TestStatusUnsupported}
		for i, status := range statuses {
			for n := 0; n < counts[i]; n++ {
				report.AddResult(NewComplianceResult(fmt.Sprintf("t%d-%d", i, n), status))
			}
		}
		if got := report.PassRate(); math.Abs(got-expected) > 1e-9 {
			t.Errorf("vector %q: PassRate() = %v, want %v", line, got, expected)
		}
		rows++
	}
	if rows == 0 {
		t.Fatal("no vectors read")
	}
}
