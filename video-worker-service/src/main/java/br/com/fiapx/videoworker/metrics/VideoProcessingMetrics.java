package br.com.fiapx.videoworker.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class VideoProcessingMetrics {

    private final Counter videosProcessedSuccess;
    private final Counter videosProcessedFailure;
    private final Counter framesExtracted;
    private final Timer processingDuration;

    public VideoProcessingMetrics(MeterRegistry registry) {
        this.videosProcessedSuccess = Counter.builder("video.processing")
                .tag("status", "success")
                .description("Total de vídeos processados com sucesso")
                .register(registry);

        this.videosProcessedFailure = Counter.builder("video.processing")
                .tag("status", "failure")
                .description("Total de vídeos que falharam no processamento")
                .register(registry);

        this.framesExtracted = Counter.builder("video.frames.extracted")
                .description("Total de frames extraídos de vídeos")
                .register(registry);

        this.processingDuration = Timer.builder("video.processing.duration")
                .description("Tempo para processar um vídeo (extração + zip)")
                .register(registry);
    }

    public void recordSuccess() {
        videosProcessedSuccess.increment();
    }

    public void recordFailure() {
        videosProcessedFailure.increment();
    }

    public void recordFramesExtracted(int count) {
        framesExtracted.increment(count);
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(processingDuration);
    }
}