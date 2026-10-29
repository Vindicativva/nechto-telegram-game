package ru.nechto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NechtoApplication {

	public static void main(String[] args) {
		SpringApplication.run(NechtoApplication.class, args);
	}

}
