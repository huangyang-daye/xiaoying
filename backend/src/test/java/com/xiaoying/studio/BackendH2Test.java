package com.xiaoying.studio;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:studio;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa","spring.datasource.password=","app.worker.enabled=false","app.storage-root=target/test-storage-h2"})
@AutoConfigureMockMvc
class BackendH2Test extends BackendContract {}
