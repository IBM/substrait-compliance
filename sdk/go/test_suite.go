package compliance

// TestSuiteMetadata contains metadata about a test suite
type TestSuiteMetadata struct {
	Name        string
	Version     string
	Description string
	Tags        []string
}

// TestCase represents a single test case within a test suite
type TestCase struct {
	ID             string
	PlanBytes      []byte
	InputData      map[string]*TableData
	ExpectedOutput *TableData
	Description    string
	Tags           []string
}

// NewTestCase creates a new test case
func NewTestCase(id string, planBytes []byte) *TestCase {
	return &TestCase{
		ID:        id,
		PlanBytes: planBytes,
		InputData: make(map[string]*TableData),
		Tags:      make([]string, 0),
	}
}

// WithInputData sets the input data
func (t *TestCase) WithInputData(data map[string]*TableData) *TestCase {
	t.InputData = data
	return t
}

// WithExpectedOutput sets the expected output
func (t *TestCase) WithExpectedOutput(output *TableData) *TestCase {
	t.ExpectedOutput = output
	return t
}

// WithDescription sets the description
func (t *TestCase) WithDescription(desc string) *TestCase {
	t.Description = desc
	return t
}

// AddTag adds a tag to the test case
func (t *TestCase) AddTag(tag string) *TestCase {
	t.Tags = append(t.Tags, tag)
	return t
}

// HasTag checks if the test case has a specific tag
func (t *TestCase) HasTag(tag string) bool {
	for _, t := range t.Tags {
		if t == tag {
			return true
		}
	}
	return false
}

// TestSuite represents a collection of related test cases
type TestSuite struct {
	Metadata  TestSuiteMetadata
	TestCases []*TestCase
}

// NewTestSuite creates a new test suite
func NewTestSuite(metadata TestSuiteMetadata) *TestSuite {
	return &TestSuite{
		Metadata:  metadata,
		TestCases: make([]*TestCase, 0),
	}
}

// AddTestCase adds a test case to the suite
func (s *TestSuite) AddTestCase(testCase *TestCase) {
	s.TestCases = append(s.TestCases, testCase)
}

// Size returns the number of test cases
func (s *TestSuite) Size() int {
	return len(s.TestCases)
}

// IsEmpty checks if the suite has no test cases
func (s *TestSuite) IsEmpty() bool {
	return len(s.TestCases) == 0
}

// GetTestsByTag returns test cases with a specific tag
func (s *TestSuite) GetTestsByTag(tag string) []*TestCase {
	result := make([]*TestCase, 0)
	for _, test := range s.TestCases {
		if test.HasTag(tag) {
			result = append(result, test)
		}
	}
	return result
}

// FindTest finds a test case by ID
func (s *TestSuite) FindTest(id string) *TestCase {
	for _, test := range s.TestCases {
		if test.ID == id {
			return test
		}
	}
	return nil
}

// Made with Bob
