package vn.nal.todoreport.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.nal.todoreport.client.NodeApiClient;
import vn.nal.todoreport.client.NodeApiException;
import vn.nal.todoreport.client.NodeApiModels.Info;
import vn.nal.todoreport.service.TodoReport;
import vn.nal.todoreport.service.TodoReportService;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final TodoReportService reportService;
    private final NodeApiClient nodeApiClient;

    public ReportController(TodoReportService reportService, NodeApiClient nodeApiClient) {
        this.reportService = reportService;
        this.nodeApiClient = nodeApiClient;
    }

    /** Báo cáo tổng hợp công việc lấy từ app Node.js. */
    @GetMapping("/report")
    public TodoReport report() {
        return reportService.buildReport();
    }

    /** Thông tin build của app Node.js mà service này đang kết nối. */
    @GetMapping("/upstream")
    public Info upstream() {
        return nodeApiClient.info();
    }

    /** Node API không gọi được -> 502 Bad Gateway. */
    @ExceptionHandler(NodeApiException.class)
    public ResponseEntity<Map<String, String>> handleUpstreamError(NodeApiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Không kết nối được tới Node API.", "detail", ex.getMessage()));
    }
}
