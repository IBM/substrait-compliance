package io.substrait.compliance.api.controller;

import io.substrait.compliance.api.model.dto.ReportResponse;
import io.substrait.compliance.api.model.dto.ReportSubmissionRequest;
import io.substrait.compliance.api.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * REST controller for compliance report operations.
 * 
 * <p>This controller provides endpoints for:
 * <ul>
 *   <li>Submitting compliance reports</li>
 *   <li>Querying reports with filtering</li>
 *   <li>Retrieving report details</li>
 *   <li>Getting engine history</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Compliance report submission and query endpoints")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class ReportController {
    
    private final ReportService reportService;
    
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }
    
    /**
     * Submits a new compliance report.
     */
    @PostMapping
    @Operation(
        summary = "Submit a compliance report",
        description = "Submits a new compliance test report with test results. " +
                     "The report will be validated, stored, and a summary will be returned."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Report successfully submitted",
            content = @Content(schema = @Schema(implementation = ReportResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request body"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - missing or invalid JWT token"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - insufficient permissions (requires report:write scope)"
        )
    })
    public ResponseEntity<ReportResponse> submitReport(
            @Valid @RequestBody ReportSubmissionRequest request) {
        log.info("Received report submission request for engine: {}-{}",
                request.getEngineInfo().getName(),
                request.getEngineInfo().getVersion());
        
        ReportResponse response = reportService.submitReport(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Gets a report by ID.
     */
    @GetMapping("/{reportId}")
    @Operation(
        summary = "Get report by ID",
        description = "Retrieves detailed information about a specific compliance report"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Report found",
            content = @Content(schema = @Schema(implementation = ReportResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Report not found"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized"
        )
    })
    public ResponseEntity<ReportResponse> getReport(
            @Parameter(description = "Report ID", example = "12345")
            @PathVariable Long reportId) {
        log.debug("Fetching report with ID: {}", reportId);
        
        ReportResponse response = reportService.getReport(reportId);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Queries reports with pagination and filtering.
     */
    @GetMapping
    @Operation(
        summary = "Query compliance reports",
        description = "Retrieves a paginated list of compliance reports with optional filtering"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Reports retrieved successfully"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized"
        )
    })
    public ResponseEntity<Page<ReportResponse>> queryReports(
            @Parameter(description = "Filter by engine name", example = "DuckDB")
            @RequestParam(required = false) String engineName,
            
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Sort field", example = "timestamp")
            @RequestParam(defaultValue = "timestamp") String sort,
            
            @Parameter(description = "Sort order (asc/desc)", example = "desc")
            @RequestParam(defaultValue = "desc") String order) {
        
        log.debug("Querying reports: engineName={}, page={}, size={}", engineName, page, size);
        
        // Create pageable with sorting
        Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));
        
        // Query reports
        Page<ReportResponse> reports;
        if (engineName != null && !engineName.isEmpty()) {
            reports = reportService.getReportsByEngine(engineName, pageable);
        } else {
            reports = reportService.queryReports(pageable);
        }
        
        return ResponseEntity.ok(reports);
    }
    
    /**
     * Gets report history for an engine.
     */
    @GetMapping("/engine/{engineName}/history")
    @Operation(
        summary = "Get engine report history",
        description = "Retrieves all compliance reports for a specific engine, ordered by timestamp"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "History retrieved successfully"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized"
        )
    })
    public ResponseEntity<List<ReportResponse>> getEngineHistory(
            @Parameter(description = "Engine name", example = "DuckDB")
            @PathVariable String engineName) {
        log.debug("Fetching history for engine: {}", engineName);
        
        List<ReportResponse> history = reportService.getEngineHistory(engineName);
        
        return ResponseEntity.ok(history);
    }
}

// Made with Bob
