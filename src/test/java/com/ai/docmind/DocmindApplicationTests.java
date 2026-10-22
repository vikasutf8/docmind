package com.ai.docmind;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"app.providers.pg.enabled=false",
	"spring.ai.vectorstore.type=none",
	"spring.ai.model.chat=none",
	"spring.ai.model.embedding=none"
})
class DocmindApplicationTests {

	@Test
	void contextLoads() {
	}
}
