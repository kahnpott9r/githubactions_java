package vn.nal.todoreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TodoReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodoReportApplication.class, args);
    }
}
