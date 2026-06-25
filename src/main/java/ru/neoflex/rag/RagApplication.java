package ru.neoflex.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.neoflex.rag.properties.RagProperties;

@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class RagApplication {

	public static void main(String[] args) {
		SpringApplication.run(RagApplication.class, args);
	}

}
