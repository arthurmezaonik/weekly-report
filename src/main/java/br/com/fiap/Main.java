package br.com.fiap;

import br.com.fiap.db.PostgresClient;
import br.com.fiap.models.WeeklyCourseReport;
import br.com.fiap.services.ReportService;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Main {
    public static void main(String[] args) throws Exception {

        Properties props = loadProps();

        PostgresClient db = new PostgresClient(
            props.getProperty("db.url"),
            props.getProperty("db.user"),
            props.getProperty("db.password")
        );

        ReportService service = new ReportService(db);
        List<WeeklyCourseReport> reports = service.generateWeeklyReports();

        QueueClient queue = new QueueClientBuilder()
            .connectionString(props.getProperty("azure.queue.connection"))
            .queueName(props.getProperty("azure.queue.name"))
            .buildClient();

        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        for (WeeklyCourseReport report : reports) {

            String messageJson = mapper.writeValueAsString(
                Map.of(
                    "type", "WEEKLY_REPORT",
                    "payload", report
                )
            );

            String base64 = Base64.getEncoder().encodeToString(messageJson.getBytes());
            queue.sendMessage(base64);

            System.out.println("Sent weekly report for course: " + report.getCourseTitle());
        }

        System.out.println("DONE.");
    }

    private static Properties loadProps() throws IOException {
        Properties props = new Properties();

        try (InputStream in = Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream("application.properties")) {

            if (in == null) {
                throw new FileNotFoundException("application.properties not found in src/main/resources");
            }

            props.load(in);
        }

        return props;
    }
}