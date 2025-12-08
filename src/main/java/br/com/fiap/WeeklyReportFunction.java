package br.com.fiap;

import br.com.fiap.db.PostgresClient;
import br.com.fiap.models.WeeklyCourseReport;
import br.com.fiap.services.ReportService;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import java.util.Base64;
import java.util.List;
import java.util.Map;

public class WeeklyReportFunction {

    @FunctionName("weeklyReportGenerator")
    public void run(
        @TimerTrigger(name = "timerInfo", schedule = "0 */1 * * * *") // every minute for testing
        String timerInfo,
        final ExecutionContext context
    ) throws Exception {

        context.getLogger().info("Timer triggered: " + timerInfo);

        // DB and Queue config from environment variables
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");
        String queueConnection = System.getenv("QUEUE_CONNECTION_STRING");
        String queueName = System.getenv("QUEUE_NAME");

        PostgresClient db = new PostgresClient(dbUrl, dbUser, dbPassword);
        ReportService service = new ReportService(db);
        List<WeeklyCourseReport> reports = service.generateWeeklyReports();

        QueueClient queue = new QueueClientBuilder()
            .connectionString(queueConnection)
            .queueName(queueName)
            .buildClient();

        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        for (WeeklyCourseReport report : reports) {

            String messageJson = mapper.writeValueAsString(
                Map.of("type", "WEEKLY_REPORT", "payload", report)
            );

            String base64 = Base64.getEncoder().encodeToString(messageJson.getBytes());
            queue.sendMessage(base64);

            context.getLogger().info("Sent weekly report for course: " + report.getCourseTitle());
        }

        context.getLogger().info("DONE.");
    }
}