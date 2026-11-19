# API de Interoperabilidade em Saúde — HL7 FHIR R4

Projeto acadêmico de **Informática Biomédica (UFCSPA)** que demonstra a exposição de dados de um sistema ambulatorial por meio de uma API REST e de recursos padronizados **HL7 FHIR R4**.

## Tecnologias

- Java 17 e Spring Boot 3.5.16
- HAPI FHIR 8.10.1 (FHIR R4)
- Spring Data JPA e banco H2 em memória
- Maven e testes com JUnit / Spring Boot Test
- JSON e HTTP REST

## Funcionalidades

O projeto contém uma API própria em `/api` para dados ambulatoriais e uma interface FHIR em `/fhir` com providers para:

- **Patient** — dados de pacientes
- **Observation** — sinais vitais
- **AllergyIntolerance** — alergias e intolerâncias

A implementação organiza responsabilidades em controllers, serviços, repositórios, mappers e resource providers. O endpoint `/fhir/metadata` apresenta o CapabilityStatement do servidor.

> **Escopo:** outros conceitos ambulatoriais, como atendimentos e vacinação, aparecem na API própria, mas não devem ser confundidos com recursos FHIR implementados. O banco é recriado ao iniciar a aplicação.

## Como executar

**Pré-requisitos:** JDK 17 e Maven instalados.

Na pasta do projeto, execute:

```bash
mvn spring-boot:run
```

A aplicação usa `http://localhost:8080`.

Exemplos de endpoints:

```http
GET http://localhost:8080/fhir/metadata
GET http://localhost:8080/fhir/Patient/1
GET http://localhost:8080/fhir/Observation/1
GET http://localhost:8080/fhir/AllergyIntolerance/1
GET http://localhost:8080/api/pacientes
```

Para criar recursos, consulte `requisicoes.http` e os exemplos JSON na pasta `exemplos/`. O arquivo `.http` pode ser usado no cliente HTTP do IntelliJ IDEA ou na extensão REST Client do VS Code.

## Testes

Para executar os testes automatizados:

```bash
mvn test
```

O código inclui testes para **Patient**, **Observation**, **AllergyIntolerance** e configuração do servidor. Os resultados devem ser verificados em uma execução local; não há promessa de execução validada neste repositório.

## Organização

```text
src/main/java/       Código da aplicação e da integração FHIR
src/main/resources/  Configuração e dados iniciais
src/test/java/       Testes automatizados
exemplos/            Payloads JSON válidos e inválidos
requisicoes.http     Chamadas HTTP de exemplo
pom.xml              Dependências e build Maven
```

## Dados e finalidade

Os registros de demonstração em `data.sql` são identificados no projeto como **fictícios**. Esta aplicação foi criada para fins acadêmicos e de demonstração, não para uso clínico ou produção.

## Autor

[Lorenzo Mayer Silva](https://github.com/LorenzooMayer) — Bacharelado em Informática Biomédica, UFCSPA.
