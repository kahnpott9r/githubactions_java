package vn.nal.todoreport.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import vn.nal.todoreport.client.NodeApiClient;
import vn.nal.todoreport.client.NodeApiModels.Todo;
import vn.nal.todoreport.service.TodoReport;

/**
 * Integration test với app Node.js THẬT (repo githubactions).
 *
 * Yêu cầu app Node đang chạy tại NODE_API_BASE_URL (mặc định http://localhost:3000).
 * Chạy: ./mvnw -Pintegration verify
 *
 * Đây là "contract test": nếu repo Node đổi format JSON, test này sẽ fail.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NodeApiIntegrationIT {

    @Autowired
    private NodeApiClient client;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void nodeAppIsHealthy() {
        assertThat(client.health().status()).isEqualTo("ok");
    }

    @Test
    void nodeAppReportsBuildInfo() {
        assertThat(client.info().app()).isEqualTo("ci-cd-playground");
        assertThat(client.info().version()).isNotBlank();
    }

    @Test
    void todoLifecycleAgainstRealNodeApi() {
        Todo created = client.createTodo("Tạo từ Java integration test");
        try {
            assertThat(created.id()).isPositive();
            assertThat(created.done()).isFalse();

            assertThat(client.listTodos().todos()).extracting(Todo::id).contains(created.id());

            Todo updated = client.updateTodo(created.id(), true);
            assertThat(updated.done()).isTrue();
        } finally {
            client.deleteTodo(created.id());
        }

        assertThat(client.listTodos().todos()).extracting(Todo::id).doesNotContain(created.id());
    }

    @Test
    void reportEndpointAggregatesNodeData() {
        TodoReport report = rest.getForObject("/api/report", TodoReport.class);

        assertThat(report.upstreamStatus()).isEqualTo("ok");
        assertThat(report.total()).isEqualTo(report.done() + report.pending());
    }
}
