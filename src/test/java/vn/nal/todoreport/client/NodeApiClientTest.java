package vn.nal.todoreport.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import vn.nal.todoreport.client.NodeApiModels.Todo;
import vn.nal.todoreport.client.NodeApiModels.TodoList;

/**
 * Test client bằng HTTP giả lập: dữ liệu JSON giống hệt app Node.js trả về.
 */
class NodeApiClientTest {

    private static final String BASE = "http://node.test";

    private MockRestServiceServer server;
    private NodeApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NodeApiClient(builder, new NodeApiProperties(BASE));
    }

    @Test
    void healthParsesNodeResponse() {
        server.expect(requestTo(BASE + "/api/health"))
                .andRespond(withSuccess("""
                        {"status":"ok","uptimeSeconds":12,"timestamp":"2026-11-13T00:00:00.000Z"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.health().status()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void listTodosIgnoresUnknownFields() {
        server.expect(requestTo(BASE + "/api/todos"))
                .andRespond(withSuccess("""
                        {"todos":[{"id":1,"title":"A","done":true,"createdAt":"x","extra":1}],"total":1}
                        """, MediaType.APPLICATION_JSON));

        TodoList list = client.listTodos();

        assertThat(list.total()).isEqualTo(1);
        assertThat(list.todos()).extracting(Todo::title).containsExactly("A");
    }

    @Test
    void createTodoSendsJsonBody() {
        server.expect(requestTo(BASE + "/api/todos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"title\":\"Viết test\"}"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"id":4,"title":"Viết test","done":false,"createdAt":"x"}
                        """));

        Todo todo = client.createTodo("Viết test");

        assertThat(todo.id()).isEqualTo(4);
        assertThat(todo.done()).isFalse();
    }

    @Test
    void serverErrorIsWrappedInNodeApiException() {
        server.expect(requestTo(BASE + "/api/todos")).andRespond(withServerError());

        assertThatThrownBy(client::listTodos)
                .isInstanceOf(NodeApiException.class)
                .hasMessageContaining("GET /api/todos");
    }
}
