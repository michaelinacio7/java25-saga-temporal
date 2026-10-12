# Order Saga with Temporal (Java 25)

Projeto independente para demonstrar uma Saga orquestrada pelo Temporal Java SDK. Todas as integrações de negócio são simuladas; não há cobrança real.

## Fluxo

1. Reservar estoque.
2. Simular cobrança.
3. Reservar entrega.
4. Se uma etapa falhar, executar compensações em ordem inversa: cancelar entrega, estornar pagamento e liberar estoque.

## Requisitos

- JDK 25
- Docker Desktop com Docker Compose
- Maven 3.9+

## Executar localmente

Terminal 1 — iniciar Temporal, PostgreSQL e a interface web:

```bash
docker compose up -d
```

Temporal UI: http://localhost:8088

Terminal 2 — iniciar a API e o worker:

```bash
mvn spring-boot:run
```

A API fica em http://localhost:8080. Para usar outro servidor, configure `TEMPORAL_TARGET` (padrão `localhost:7233`).

## Criar pedido de teste

Sucesso:

```bash
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d '{"orderId":"pedido-100","customerId":"cliente-1","sku":"CAMISETA-1","quantity":1,"amount":49.90,"currency":"BRL"}'
```

Falha simulada na entrega — deve executar cancelamento, estorno e liberação:

```bash
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d '{"orderId":"pedido-101","customerId":"cliente-1","sku":"FAIL_DELIVERY","quantity":1,"amount":49.90,"currency":"BRL"}'
```

Cada `orderId` gera um ID de workflow determinístico; enviar novamente o mesmo ID retorna HTTP 409 em vez de iniciar uma segunda execução.

## Testes

```bash
mvn --batch-mode verify
```

Os testes usam o ambiente de teste do Temporal e verificam sucesso e ordem de compensação sem precisar subir Docker.

## Limites e boas práticas

- O cenário é uma demonstração local, não um serviço financeiro de produção.
- Atividades e compensações de produção precisam de idempotência durável, timeouts, retries específicos, observabilidade e tratamento de resultados incertos.
- O workflow usa uma tentativa por Activity para tornar a falha simulada determinística; revise a política para o seu domínio.
- As credenciais no Compose são apenas locais. Não exponha esse ambiente publicamente.

Referências: [Temporal Java SDK](https://docs.temporal.io/develop/java) e [Temporal Java SDK no GitHub](https://github.com/temporalio/sdk-java).
