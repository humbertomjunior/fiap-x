package br.com.fiapx.auth.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter registrationsSuccess;
    private final Counter registrationsFailure;

    public AuthMetrics(MeterRegistry registry) {
        this.loginSuccess = Counter.builder("auth.login.attempts")
                .tag("status", "success")
                .description("Total de logins bem-sucedidos")
                .register(registry);

        this.loginFailure = Counter.builder("auth.login.attempts")
                .tag("status", "failure")
                .description("Total de tentativas de login que falharam")
                .register(registry);

        this.registrationsSuccess = Counter.builder("auth.registrations")
                .tag("status", "success")
                .description("Total de novos usuários registrados com sucesso")
                .register(registry);

        this.registrationsFailure = Counter.builder("auth.registrations")
                .tag("status", "failure")
                .description("Total de registros que falharam (ex: email duplicado)")
                .register(registry);
    }

    public void recordLoginSuccess() {
        loginSuccess.increment();
    }

    public void recordLoginFailure() {
        loginFailure.increment();
    }

    public void recordRegistrationSuccess() {
        registrationsSuccess.increment();
    }

    public void recordRegistrationFailure() {
        registrationsFailure.increment();
    }
}