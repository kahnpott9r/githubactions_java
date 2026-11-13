package vn.nal.todoreport.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import vn.nal.todoreport.client.NodeApiClient;
import vn.nal.todoreport.client.NodeApiException;
import vn.nal.todoreport.service.TodoReport;
import vn.nal.todoreport.service.TodoReportService;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TodoReportService reportService;

    @MockitoBean
    private NodeApiClient nodeApiClient;

    @Test
    void reportReturnsSummary() throws Exception {
        given(reportService.buildReport())
                .willReturn(new TodoReport(3, 2, 1, 66.7, List.of("Thêm artifact"), "ok"));

        mvc.perform(get("/api/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.completionRate").value(66.7))
                .andExpect(jsonPath("$.pendingTitles[0]").value("Thêm artifact"));
    }

    @Test
    void upstreamFailureReturns502() throws Exception {
        given(reportService.buildReport())
                .willThrow(new NodeApiException("Gọi Node API thất bại", null));

        mvc.perform(get("/api/report"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").exists());
    }
}
