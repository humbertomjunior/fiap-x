package br.com.fiapx.notification.service;

import br.com.fiapx.common.event.VideoStatusEvent;
import br.com.fiapx.notification.config.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmtpNotificationEmailSenderTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Test
    void shouldBuildAndSendEmailWithoutUsingRealSmtp() {
        SmtpNotificationEmailSender sender = new SmtpNotificationEmailSender(javaMailSender, new NotificationProperties("noreply@fiapx.com"));
        VideoStatusEvent event = new VideoStatusEvent(UUID.randomUUID(), UUID.randomUUID(), "user@fiapx.com", "FINISHED", null, null, LocalDateTime.now());

        sender.send(event, "FINISHED", "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("noreply@fiapx.com");
        assertThat(message.getTo()).containsExactly("user@fiapx.com");
        assertThat(message.getSubject()).isEqualTo("Subject");
        assertThat(message.getText()).isEqualTo("Body");
    }
}
