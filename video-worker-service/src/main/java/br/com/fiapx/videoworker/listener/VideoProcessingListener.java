package br.com.fiapx.videoworker.listener;

import br.com.fiapx.common.event.VideoProcessingEvent;
import br.com.fiapx.videoworker.service.VideoProcessingOrchestrator;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class VideoProcessingListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoProcessingListener.class);

    private final VideoProcessingOrchestrator orchestrator;
    private final Environment environment;

    public VideoProcessingListener(VideoProcessingOrchestrator orchestrator, Environment environment) {
        this.orchestrator = orchestrator;
        this.environment = environment;
    }

    @SqsListener("${spring.cloud.aws.sqs.queues.video-uploaded:video-uploaded-queue}")
    public void handle(VideoProcessingEvent event) {
        logLocalSqsReceive(event);
        LOGGER.info("Starting processing for videoId={}", event.videoId());
        try {
            orchestrator.process(event);
        } catch (RuntimeException ex) {
            LOGGER.error("Processing failed for videoId={}. Message will return to the queue for retry/DLQ handling.", event.videoId(), ex);
            throw ex;
        }
    }

    private void logLocalSqsReceive(VideoProcessingEvent event) {
        if (environment.matchesProfiles("local")) {
            LOGGER.info("[LOCAL SQS RECEIVE] queue=video-uploaded-queue payload={}", event);
        }
    }
}
