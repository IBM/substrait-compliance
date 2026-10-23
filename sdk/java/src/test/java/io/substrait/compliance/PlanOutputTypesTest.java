package io.substrait.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The decimal scales a plan declares, as scripts/run_plan_compliance.py works them out. */
class PlanOutputTypesTest {

  private static com.fasterxml.jackson.databind.JsonNode plan(String relative) throws IOException {
    Path path = Paths.get("..", "..", "test-suites", "tpch").resolve(relative);
    String json = Files.readAllLines(path).stream()
        .filter(line -> !line.trim().startsWith("//"))
        .collect(Collectors.joining("\n"));
    return new ObjectMapper().readTree(json);
  }

  @Test
  void q01DeclaresDecimalAggregates() throws IOException {
    assertThat(PlanOutputTypes.declaredScales(plan("plans/q01.json")))
        .isEqualTo(Arrays.asList(null, null, 2, 2, 4, 6, 2, 2, 2, null));
  }

  @Test
  void bothEncodingsDeclareTheSameScales() throws IOException {
    for (int q = 1; q <= 22; q++) {
      String name = String.format("q%02d.json", q);
      assertThat(PlanOutputTypes.declaredScales(plan("plans-substrait-0.81/" + name)))
          .as(name)
          .isNotNull()
          .isEqualTo(PlanOutputTypes.declaredScales(plan("plans/" + name)));
    }
  }

  @Test
  void unknownRelationGivesNoScales() throws IOException {
    assertThat(PlanOutputTypes.declaredScales(new ObjectMapper()
        .readTree("{\"relations\":[{\"root\":{\"input\":{\"exchange\":{}}}}]}"))).isNull();
  }
}
