# EventGuard Starter

![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 4.x](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F)
![License MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![Build Passing](https://img.shields.io/badge/Build-Passing-brightgreen)

## The Problem & The Solution

In distributed messaging (RabbitMQ, Kafka), the same event can be delivered more than once.  
Without idempotency, consumers may apply the same business action multiple times.

EventGuard Starter adds idempotent processing to Spring Cloud Stream consumers with:

- Redis locks (default path)
- JDBC locks (relational fallback)
- Annotation-based usage without mandatory boilerplate configuration

## Quick Start

```xml
<dependency>
  <groupId>io.github.luanolegario</groupId>
  <artifactId>event-guard-starter</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Usage

```java
import io.github.luanolegario.eventguard.annotation.EventGuardListener;
import org.springframework.messaging.Message;

public class PaymentConsumer {

    @EventGuardListener(idempotencyKey = "#event.transactionId")
    public void onPayment(Message<PaymentEvent> message) {
        // business logic
    }
}
```

## Day-2 Operations

- Actuator endpoint to release a stuck lock manually
- Micrometer metrics for acquired locks, blocked duplicates, and routed failures
- JDBC lock provider support when Redis is not the selected runtime option

## Author

Luan Matheus Olegário

---

# EventGuard Starter

![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 4.x](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F)
![License MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![Build Passing](https://img.shields.io/badge/Build-Passing-brightgreen)

## O Problema e a Solução

Em mensageria distribuída (RabbitMQ, Kafka), o mesmo evento pode chegar mais de uma vez.  
Sem idempotência, o consumidor pode aplicar a mesma ação de negócio em duplicidade.

O EventGuard Starter adiciona processamento idempotente em consumidores Spring Cloud Stream com:

- Locks em Redis (caminho padrão)
- Locks via JDBC (fallback relacional)
- Uso por anotação sem exigir configuração obrigatória e repetitiva

## Início Rápido

```xml
<dependency>
  <groupId>io.github.luanolegario</groupId>
  <artifactId>event-guard-starter</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Uso

```java
import io.github.luanolegario.eventguard.annotation.EventGuardListener;
import org.springframework.messaging.Message;

public class PaymentConsumer {

    @EventGuardListener(idempotencyKey = "#event.transactionId")
    public void onPayment(Message<PaymentEvent> message) {
        // regra de negócio
    }
}
```

## Operação (Day-2)

- Endpoint Actuator para liberar lock travado manualmente
- Métricas Micrometer para locks adquiridos, duplicidades bloqueadas e falhas roteadas
- Suporte a provider JDBC quando Redis não for a opção de runtime selecionada

## Autor

Luan Matheus Olegário

