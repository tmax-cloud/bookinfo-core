package org.tmaxcloud.sample.msa.book.core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(locations= "classpath:/application.test.properties")
@SpringBootTest
class CoreApplicationTests {

    @Test
    void contextLoads() {
    }

}
