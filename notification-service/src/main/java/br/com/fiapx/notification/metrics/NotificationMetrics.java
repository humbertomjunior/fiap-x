package br.com.fiapx.notification.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private final Counter emailsSuccess;
    private final Counter emailsFailure;
    private final Timer sendDuration;

    public NotificationMetrics(MeterRegistry registry) {
        this.emailsSuccess = Counter.builder("notifications.sent")
                .tag("status", "success")
                .description("Total de notificações enviadas com sucesso")
                .register(registry);

        this.emailsFailure = Counter.builder("notifications.sent")
                .tag("status", "failure")
                .description("Total de notificações que falharam ao enviar")
                .register(registry);

        this.sendDuration = Timer.builder("notification.send.duration")
                .description("Tempo para processar e enviar a notificação")
                .register(registry);
    }

    public void recordSuccess() {
        emailsSuccess.increment();
    }

    public void recordFailure() {
        emailsFailure.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(sendDuration);
    }
}