package vn.nal.todoreport.client;

import java.util.function.Supplier;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import vn.nal.todoreport.client.NodeApiModels.Health;
import vn.nal.todoreport.client.NodeApiModels.Info;
import vn.nal.todoreport.client.NodeApiModels.NewTodo;
import vn.nal.todoreport.client.NodeApiModels.Todo;
import vn.nal.todoreport.client.NodeApiModels.TodoList;
import vn.nal.todoreport.client.NodeApiModels.TodoStatus;

/**
 * HTTP client gọi API của app Node.js (repo githubactions).
 */
@Component
public class NodeApiClient {

    private final RestClient restClient;

    /**
     * Builder do Spring Boot cấu hình sẵn (timeout lấy từ spring.http.client.*).
     */
    public NodeApiClient(RestClient.Builder builder, NodeApiProperties properties) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Health health() {
        return call("GET /api/health", () -> restClient.get().uri("/api/health").retrieve().body(Health.class));
    }

    public Info info() {
        return call("GET /api/info", () -> restClient.get().uri("/api/info").retrieve().body(Info.class));
    }

    public TodoList listTodos() {
        return call("GET /api/todos", () -> restClient.get().uri("/api/todos").retrieve().body(TodoList.class));
    }

    public Todo createTodo(String title) {
        return call("POST /api/todos", () -> restClient.post().uri("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new NewTodo(title))
                .retrieve()
                .body(Todo.class));
    }

    public Todo updateTodo(long id, boolean done) {
        return call("PATCH /api/todos/" + id, () -> restClient.patch().uri("/api/todos/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TodoStatus(done))
                .retrieve()
                .body(Todo.class));
    }

    public Todo deleteTodo(long id) {
        return call("DELETE /api/todos/" + id,
                () -> restClient.delete().uri("/api/todos/{id}", id).retrieve().body(Todo.class));
    }

    private static <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException ex) {
            throw new NodeApiException("Gọi Node API thất bại: " + operation + " - " + ex.getMessage(), ex);
        }
    }
}
