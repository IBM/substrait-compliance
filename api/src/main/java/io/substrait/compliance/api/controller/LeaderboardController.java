package io.substrait.compliance.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.substrait.compliance.api.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaderboard")
@Tag(name = "Leaderboard", description = "The published compliance leaderboard")
@SecurityRequirement(name = "bearerAuth")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    @Operation(
        summary = "Get the compliance leaderboard",
        description = "Returns the published leaderboard (leaderboard.json on the project's GitHub Pages): "
            + "engines ranked by the average of their Functions and Queries pass rates, with per-suite "
            + "results and where each result comes from. Scores are computed by the repository's "
            + "leaderboard workflow, not by this API."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "The leaderboard"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Missing the report:read scope"),
        @ApiResponse(responseCode = "503", description = "The published leaderboard could not be fetched")
    })
    public ResponseEntity<JsonNode> getLeaderboard() {
        return ResponseEntity.ok(leaderboardService.getLeaderboard());
    }
}
