package io.substrait.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Query-output comparison: the vectors shared with the Python runner
 * (sdk/conformance/comparison-vectors.json), checked against ComplianceRunner.describeMismatch.
 */
class ComparisonVectorsTest {

  private static Object decode(JsonNode v) {
    if (v.isNull()) return null;
    if (v.isObject()) {
      return v.has("double") ? (Object) Double.valueOf(v.get("double").asText())
                             : new BigDecimal(v.get("decimal").asText());
    }
    if (v.isBoolean()) return v.asBoolean();
    if (v.isIntegralNumber()) return v.asLong();
    if (v.isNumber()) return v.asDouble();
    return v.asText();
  }

  private static List<String> strings(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(n -> out.add(n.asText()));
    return out;
  }

  private static TableData table(JsonNode t) {
    List<List<Object>> rows = new ArrayList<>();
    for (JsonNode row : t.get("rows")) {
      List<Object> values = new ArrayList<>();
      row.forEach(v -> values.add(decode(v)));
      rows.add(values);
    }
    return new TableData(strings(t.get("names")), strings(t.get("types")), rows);
  }

  @Test
  void describeMismatchMatchesTheSharedVectors() throws IOException {
    JsonNode vectors = new ObjectMapper()
        .readTree(Paths.get("..", "conformance", "comparison-vectors.json").toFile())
        .get("vectors");
    assertThat(vectors.size()).isGreaterThan(0);
    ComplianceRunner runner = new ComplianceRunner(mock(ComplianceEngine.class));
    for (JsonNode v : vectors) {
      String mismatch = runner.describeMismatch(table(v.get("expected")), table(v.get("actual")));
      assertThat(mismatch == null)
          .as("%s: %s", v.get("name").asText(), mismatch)
          .isEqualTo(v.get("match").asBoolean());
    }
  }
}
