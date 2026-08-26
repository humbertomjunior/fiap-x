package br.com.fiapx.videoapi.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class VideoApiMetrics {

    private final Counter uploadsSuccess;
    private final Counter uploadsFailure;
    private final DistributionSummary uploadSize;
    private final Timer uploadDuration;

    public VideoApiMetrics(MeterRegistry registry) {
        this.uploadsSuccess = Counter.builder("video.uploads")
                .tag("status", "success")
                .description("Total de uploads de vídeo bem-sucedidos")
                .register(registry);

        this.uploadsFailure = Counter.builder("video.uploads")
                .tag("status", "failure")
                .description("Total de uploads de vídeo que falharam")
                .register(registry);

        this.uploadSize = DistributionSummary.builder("video.upload.size.bytes")
                .description("Tamanho dos vídeos enviados em bytes")
                .baseUnit("bytes")
                .register(registry);

        this.uploadDuration = Timer.builder("video.upload.duration")
                .description("Tempo para processar o upload do vídeo (validação + gravação em disco)")
                .register(registry);
    }

    public void recordUploadSuccess(long sizeBytes) {
        uploadsSuccess.increment();
        uploadSize.record(sizeBytes);
    }

    public void recordUploadFailure() {
        uploadsFailure.increment();
    }

    public Timer.Sample startUploadTimer() {
        return Timer.start();
    }

    public void stopUploadTimer(Timer.Sample sample) {
        sample.stop(uploadDuration);
    }
}
