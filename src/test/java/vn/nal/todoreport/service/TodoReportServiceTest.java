package vn.nal.todoreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import vn.nal.todoreport.client.NodeApiModels.Todo;

class TodoReportServiceTest {

    @Test
    void summarizeCountsDoneAndPending() {
        List<Todo> todos = List.of(
                new Todo(1, "Tạo repository", true, null),
                new Todo(2, "Viết workflow", true, null),
                new Todo(3, "Thêm artifact", false, null));

        TodoReport report = TodoReportService.summarize(todos, "ok");

        assertThat(report.total()).isEqualTo(3);
        assertThat(report.done()).isEqualTo(2);
        assertThat(report.pending()).isEqualTo(1);
        assertThat(report.completionRate()).isEqualTo(66.7);
        assertThat(report.pendingTitles()).containsExactly("Thêm artifact");
        assertThat(report.upstreamStatus()).isEqualTo("ok");
    }

    @Test
    void summarizeEmptyListGivesZeroRate() {
        TodoReport report = TodoReportService.summarize(List.of(), "ok");

        assertThat(report.total()).isZero();
        assertThat(report.completionRate()).isZero();
        assertThat(report.pendingTitles()).isEmpty();
    }
}
