package br.com.fiapx.notification.listener;

import br.com.fiapx.common.event.VideoStatusEvent;
import br.com.fiapx.notification.service.NotificationService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VideoStatusListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoStatusListener.class);

    private final NotificationService notificationService;

    public VideoStatusListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @SqsListener("${app.sqs.queues.video-status}")
    public void handle(VideoStatusEvent event) {
        LOGGER.info("Received video status event: videoId={} status={}", event.videoId(), event.status());
        try {
            notificationService.process(event);
        } catch (RuntimeException ex) {
            LOGGER.error("Notification processing failed for videoId={}. Message will return to the queue for retry/DLQ handling.", event.videoId(), ex);
            throw ex;
        }
    }
}
