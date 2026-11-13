package vn.nal.todoreport.service;

import java.util.List;

import org.springframework.stereotype.Service;

import vn.nal.todoreport.client.NodeApiClient;
import vn.nal.todoreport.client.NodeApiModels.Todo;

@Service
public class TodoReportService {

    private final NodeApiClient nodeApiClient;

    public TodoReportService(NodeApiClient nodeApiClient) {
        this.nodeApiClient = nodeApiClient;
    }

    public TodoReport buildReport() {
        String upstreamStatus = nodeApiClient.health().status();
        List<Todo> todos = nodeApiClient.listTodos().todos();
        return summarize(todos, upstreamStatus);
    }

    static TodoReport summarize(List<Todo> todos, String upstreamStatus) {
        int total = todos.size();
        int done = (int) todos.stream().filter(Todo::done).count();
        int pending = total - done;
        double completionRate = total == 0 ? 0.0 : Math.round(done * 1000.0 / total) / 10.0;
        List<String> pendingTitles = todos.stream()
                .filter(todo -> !todo.done())
                .map(Todo::title)
                .toList();

        return new TodoReport(total, done, pending, completionRate, pendingTitles, upstreamStatus);
    }
}
