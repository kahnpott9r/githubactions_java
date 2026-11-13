package vn.nal.todoreport.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Các DTO ánh xạ JSON trả về từ app Node.js.
 * Bỏ qua field lạ để app Node có thể thêm field mới mà không làm hỏng Java.
 */
public final class NodeApiModels {

    private NodeApiModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Health(String status, long uptimeSeconds, String timestamp) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Todo(long id, String title, boolean done, String createdAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TodoList(List<Todo> todos, int total) {

        public TodoList {
            todos = todos == null ? List.of() : List.copyOf(todos);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(String app, String version, String environment, String node, String commit) {
    }

    public record NewTodo(String title) {
    }

    public record TodoStatus(boolean done) {
    }
}
