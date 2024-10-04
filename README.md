# spring-ai-demo

## My First Spring AI Application (RAG &amp; Function calling)

STEP-1: Create openai account & Generate api-keys (secret key) to access LLM (Large Langage Model)
https://platform.openai.com

STEP-2: Create AI application using https://start.spring.io by adding below dependencies
- OpenAI
- PGVector Vector Database
- Spring Web
- Spring Data JDBC

STEP-3: Download pgvector Image and start the container
```
docker pull ankane/pgvector
docker run --name pgvector-demo -e POSTGRES_PASSWORD=test -p 5432:5432 ankane/pgvector
```
