package vn.nal.todoreport.service;

import java.util.List;

/**
 * Báo cáo tổng hợp danh sách công việc lấy từ app Node.js.
 */
public record TodoReport(
        int total,
        int done,
        int pending,
        double completionRate,
        List<String> pendingTitles,
        String upstreamStatus) {
}
