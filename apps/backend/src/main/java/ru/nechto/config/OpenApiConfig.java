package ru.nechto.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Нечто (Stay Away!) — REST API")
                        .version("1.0.0")
                        .description("REST API для управления игровыми комнатами, лобби и сессиями в многопользовательской игре «Нечто» (Telegram Mini App).")
                        .contact(new Contact().name("Stay Away Development Team"))
                        .license(new License().name("MIT")));
    }
}
