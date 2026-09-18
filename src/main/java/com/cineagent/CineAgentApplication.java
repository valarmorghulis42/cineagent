package com.cineagent;

import com.cineagent.common.config.HoldProperties;
import com.cineagent.common.config.ReminderProperties;
import com.cineagent.identity.service.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({HoldProperties.class, ReminderProperties.class, JwtProperties.class})
public class CineAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(CineAgentApplication.class, args);
	}

}
