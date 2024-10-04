package com.example.spring_ai_demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.core.io.Resource;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@SpringBootApplication
public class SpringAiDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringAiDemoApplication.class, args);
	}

	private final Logger logger = LoggerFactory.getLogger(SpringAiDemoApplication.class);

	private final ChatClient chatClient;

	private final VectorStore vectorStore;

	private final JdbcClient db;

	public SpringAiDemoApplication(ChatClient.Builder chatClientBuilder, VectorStore vectorStore,
								   DataSource dataSource) {
		this.chatClient = chatClientBuilder.build();
		this.vectorStore = vectorStore;
		this.db = JdbcClient.create(dataSource);
	}

	@Bean
	ApplicationRunner demo() {
		return args -> {
			basic();
//			questionAnswer();
//			functionCalling(false);
		};
	}

	private void basic() {
		var response = this.chatClient.prompt()
				.user("Tell me about solar system")
				.call()
				.content();

		logger.info("\n\n Response: {} \n\n", response);
	}

	/**
	 * Retrieval Augmented Generation
	 */
	private void questionAnswer() {
		var response = chatClient
				.prompt()
				.user("Do you have any friendly dog?")
				.advisors(new QuestionAnswerAdvisor(vectorStore))
				.call()
				.content();

		logger.info("\n\n Response: {} \n\n", response);
	}

	private void functionCalling(boolean parallelCalls) {
		var response = chatClient
				.prompt()
				.options(OpenAiChatOptions.builder().withParallelToolCalls(parallelCalls).build())
				.user("What is the status of my policy H001, H002, H004 and H003?")
				.functions("policyStatus")
				.call()
				.content();

		logger.info("\n\n Response: {} \n\n", response);
	}

	record Policy(String id) {
	}

	record Status(String name) {
	}

	@Bean
	@Description("Get the status of a single payment transaction")
	Function<Policy, Status> policyStatus() {
		return transaction -> {
			logger.info("Single transaction: " + transaction);
			return DATASET.get(transaction);
		};
	}

	static final Map<Policy, Status> DATASET = Map.of(
			new Policy("H001"), new Status("pending"),
			new Policy("H002"), new Status("approved"),
			new Policy("H003"), new Status("rejected")
	);

//	@Bean
	ApplicationRunner memory() {
		return args -> {
			var memory = new InMemoryChatMemory();
			// if you're using OpenAI you should be using the MessageChatMemoryAdvisor
			var promptChatMemoryAdvisor = new PromptChatMemoryAdvisor(memory);
			System.out.println( chatClient
					.prompt()
					.user("Hi, my name is John. How are you?")
					.advisors(promptChatMemoryAdvisor)
					.call()
					.content()
			);
			System.out.println( chatClient
					.prompt()
					.user("what's my name?")
					.advisors(promptChatMemoryAdvisor)
					.call()
					.content()
			);
		};
	}

//	@Bean
	ApplicationRunner pdfLoader(@Value("classpath:/data/benefits.pdf") Resource pdf) {
		return args -> {
			this.db.sql("delete from vector_store").update();
			var config = PdfDocumentReaderConfig
					.builder()
					.withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
							.withNumberOfBottomTextLinesToDelete(3)
							.build())
					.build();

			var pdfReader = new PagePdfDocumentReader(pdf, config);
			var textSplitter = new TokenTextSplitter();
			logger.info("Parsing document, splitting, creating embeddings and storing in vector store...");
			var docs = textSplitter.apply(pdfReader.get());

			this.vectorStore.accept(docs);
			logger.info("Done parsing document, splitting, creating embeddings and storing in vector store");
		};
	}

//	@Bean
	ApplicationRunner txtLoader(@Value("classpath:/data/congo.txt") Resource txtResource) {
		return args -> {
			this.db.sql("delete from vector_store").update();
			// Transform
			var tokenTextSplitter = new TokenTextSplitter();
			logger.info("Parsing document, splitting, creating embeddings and storing in vector store...");

			DocumentReader reader = new TextReader(txtResource);
			var splitDocuments = tokenTextSplitter.split(reader.read());

			// tag as external knowledge in the vector store's metadata
			for (var splitDocument : splitDocuments) {
				splitDocument.getMetadata().putAll(Map.of(
						"filename", txtResource.getFilename(),
						"version", 1
				));
			}

			// Load
			this.vectorStore.write(splitDocuments);
			logger.info("Done parsing document, splitting, creating embeddings and storing in vector store");
		};
	}

//	@Bean
	ApplicationRunner dbLoader(DogRepository dogRepository) {
		return args -> {
			db.sql("delete from vector_store").update();
			dogRepository.findAll().forEach(dog -> {
				var document = new Document("id: %s, name: %s, description: %s".formatted(
						dog.id(), dog.name(), dog.description()
				));
				vectorStore.add(List.of(document));
			});
			logger.info("Done parsing document, splitting, creating embeddings and storing in vector store");
		};
	}
}

record Dog(@Id Integer id, String name, String description) {
}
interface DogRepository extends ListCrudRepository<Dog, Integer> {
}
