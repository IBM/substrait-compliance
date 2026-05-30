package io.substrait.compliance

/**
 * Represents a single test case
 */
case class TestCase(
  name: String,
  description: Option[String] = None,
  planPath: String,
  inputTables: Map[String, TableData] = Map.empty,
  expectedOutput: Option[TableData] = None,
  shouldFail: Boolean = false,
  tags: Set[String] = Set.empty,
  metadata: Map[String, String] = Map.empty
) {
  
  /**
   * Check if test has a specific tag
   */
  def hasTag(tag: String): Boolean = tags.contains(tag)

  /**
   * Check if test has all specified tags
   */
  def hasAllTags(requiredTags: Set[String]): Boolean = {
    requiredTags.subsetOf(tags)
  }

  /**
   * Check if test has any of the specified tags
   */
  def hasAnyTag(requiredTags: Set[String]): Boolean = {
    tags.intersect(requiredTags).nonEmpty
  }

  /**
   * Get metadata value
   */
  def getMetadata(key: String): Option[String] = metadata.get(key)
}

/**
 * Represents a test suite containing multiple test cases
 */
case class TestSuite(
  name: String,
  description: Option[String] = None,
  version: String = "1.0",
  testCases: Seq[TestCase] = Seq.empty,
  metadata: Map[String, String] = Map.empty
) {
  
  /**
   * Get number of test cases
   */
  def testCount: Int = testCases.length

  /**
   * Check if suite is empty
   */
  def isEmpty: Boolean = testCases.isEmpty

  /**
   * Get test case by name
   */
  def getTestCase(name: String): Option[TestCase] = {
    testCases.find(_.name == name)
  }

  /**
   * Filter test cases by tag
   */
  def filterByTag(tag: String): Seq[TestCase] = {
    testCases.filter(_.hasTag(tag))
  }

  /**
   * Filter test cases by multiple tags (all required)
   */
  def filterByAllTags(tags: Set[String]): Seq[TestCase] = {
    testCases.filter(_.hasAllTags(tags))
  }

  /**
   * Filter test cases by multiple tags (any required)
   */
  def filterByAnyTag(tags: Set[String]): Seq[TestCase] = {
    testCases.filter(_.hasAnyTag(tags))
  }

  /**
   * Get all unique tags across test cases
   */
  def allTags: Set[String] = {
    testCases.flatMap(_.tags).toSet
  }

  /**
   * Get metadata value
   */
  def getMetadata(key: String): Option[String] = metadata.get(key)

  /**
   * Add a test case
   */
  def addTestCase(testCase: TestCase): TestSuite = {
    copy(testCases = testCases :+ testCase)
  }

  /**
   * Add multiple test cases
   */
  def addTestCases(cases: Seq[TestCase]): TestSuite = {
    copy(testCases = testCases ++ cases)
  }
}

object TestSuite {
  /**
   * Create an empty test suite
   */
  def empty(name: String): TestSuite = TestSuite(name)

  /**
   * Create a test suite with test cases
   */
  def apply(name: String, testCases: Seq[TestCase]): TestSuite = {
    TestSuite(name, None, "1.0", testCases)
  }
}

/**
 * Builder for creating test suites
 */
class TestSuiteBuilder(name: String) {
  private var description: Option[String] = None
  private var version: String = "1.0"
  private var testCases: Seq[TestCase] = Seq.empty
  private var metadata: Map[String, String] = Map.empty

  def withDescription(desc: String): TestSuiteBuilder = {
    description = Some(desc)
    this
  }

  def withVersion(ver: String): TestSuiteBuilder = {
    version = ver
    this
  }

  def addTestCase(testCase: TestCase): TestSuiteBuilder = {
    testCases = testCases :+ testCase
    this
  }

  def addTestCases(cases: Seq[TestCase]): TestSuiteBuilder = {
    testCases = testCases ++ cases
    this
  }

  def addMetadata(key: String, value: String): TestSuiteBuilder = {
    metadata = metadata + (key -> value)
    this
  }

  def build(): TestSuite = {
    TestSuite(name, description, version, testCases, metadata)
  }
}

object TestSuiteBuilder {
  def apply(name: String): TestSuiteBuilder = new TestSuiteBuilder(name)
}

// Made with Bob
