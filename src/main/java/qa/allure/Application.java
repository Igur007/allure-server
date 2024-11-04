package qa.allure;

import com.vaadin.flow.spring.annotation.EnableVaadin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import qa.allure.properties.AllureProperties;
import qa.allure.properties.BasicProperties;
import qa.allure.properties.CleanUpProperties;
import qa.allure.properties.TmsProperties;

// @ImportAutoConfiguration({FeignAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class})
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, ErrorMvcAutoConfiguration.class})
@EnableCaching
@EnableTransactionManagement
@EnableConfigurationProperties({AllureProperties.class, CleanUpProperties.class, BasicProperties.class, TmsProperties.class})
@EnableVaadin
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
