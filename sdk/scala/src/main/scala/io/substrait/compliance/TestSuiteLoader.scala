package io.substrait.compliance

import io.circe._
import io.circe.yaml.parser
import io.circe.generic.auto._

import scala.io.Source
import scala.util.{Try, Success, Failure}
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

/**
 * Loads test suites from YAML files
 */
class TestSuiteLoader {

  /**
   * Load a test suite from a YAML file
   */
  def loadFromFile(path: String): Try[TestSuite] = {
    Try {
      val source = Source.fromFile(path)
      try {
        val yamlContent = source.mkString
        loadFromYaml(yamlContent) match {
          case Right(suite) => suite
          case Left(error) => throw new RuntimeException(s"Failed to parse YAML: ${error.getMessage}")
        }
      } finally {
        source.close()
      }
    }
  }

  /**
   * Load a test suite from YAML string
   */
  def loadFromYaml(yamlContent: String): Either[Error, TestSuite] = {
    for {
      json <- parser.parse(yamlContent)
      suite <- parseTestSuite(json)
    } yield suite
  }

  /**
   * Load multiple test suites from a directory
   */
  def loadFromDirectory(directoryPath: String, recursive: Boolean = false): Try[Seq[TestSuite]] = {
    Try {
      val dir = Paths.get(directoryPath)
      if (!Files.exists(dir) || !Files.isDirectory(dir)) {
        throw new IllegalArgumentException(s"Directory not found: $directoryPath")
      }

      val yamlFiles = if (recursive) {
        Files.walk(dir)
          .iterator()
          .asScala
          .filter(p => Files.isRegularFile(p) && isYamlFile(p))
          .toSeq
      } else {
        Files.list(dir)
          .iterator()
          .asScala
          .filter(p => Files.isRegularFile(p) && isYamlFile(p))
          .toSeq
      }

      yamlFiles.flatMap { path =>
        loadFromFile(path.toString) match {
          case Success(suite) => Some(suite)
          case Failure(e) =>
            System.err.println(s"Failed to load test suite from ${path}: ${e.getMessage}")
            None
        }
      }
    }
  }

  /**
   * Parse test suite from JSON
   */
  private def parseTestSuite(json: Json): Either[Error, TestSuite] = {
    val cursor = json.hcursor

    for {
      name <- cursor.get[String]("name")
      description = cursor.get[String]("description").toOption
      version = cursor.get[String]("version").getOrElse("1.0")
      testCasesJson <- cursor.get[Seq[Json]]("test_cases")
      testCases <- parseTestCases(testCasesJson)
      metadata = cursor.get[Map[String, String]]("metadata").getOrElse(Map.empty)
    } yield TestSuite(name, description, version, testCases, metadata)
  }

  /**
   * Parse test cases from JSON array
   */
  private def parseTestCases(testCasesJson: Seq[Json]): Either[Error, Seq[TestCase]] = {
    testCasesJson.foldLeft[Either[Error, Seq[TestCase]]](Right(Seq.empty)) { (acc, json) =>
      acc.flatMap { cases =>
        parseTestCase(json).map(testCase => cases :+ testCase)
      }
    }
  }

  /**
   * Parse a single test case from JSON
   */
  private def parseTestCase(json: Json): Either[Error, TestCase] = {
    val cursor = json.hcursor

    for {
      name <- cursor.get[String]("name")
      description = cursor.get[String]("description").toOption
      planPath <- cursor.get[String]("plan_path")
      shouldFail = cursor.get[Boolean]("should_fail").getOrElse(false)
      tags = cursor.get[Seq[String]]("tags").getOrElse(Seq.empty).toSet
      metadata = cursor.get[Map[String, String]]("metadata").getOrElse(Map.empty)
    } yield TestCase(
      name = name,
      description = description,
      planPath = planPath,
      shouldFail = shouldFail,
      tags = tags,
      metadata = metadata
    )
  }

  /**
   * Check if a path is a YAML file
   */
  private def isYamlFile(path: Path): Boolean = {
    val fileName = path.getFileName.toString.toLowerCase
    fileName.endsWith(".yaml") || fileName.endsWith(".yml")
  }
}

object TestSuiteLoader {
  /**
   * Create a new test suite loader
   */
  def apply(): TestSuiteLoader = new TestSuiteLoader()

  /**
   * Load a test suite from a file
   */
  def loadFromFile(path: String): Try[TestSuite] = {
    new TestSuiteLoader().loadFromFile(path)
  }

  /**
   * Load test suites from a directory
   */
  def loadFromDirectory(directoryPath: String, recursive: Boolean = false): Try[Seq[TestSuite]] = {
    new TestSuiteLoader().loadFromDirectory(directoryPath, recursive)
  }
}

// Made with Bob
