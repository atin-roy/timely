package com.atinroy.timely;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class TimelyApplication {

	public static void main(String[] args) {
		// The JDBC driver sends the JVM's zone to Postgres, which rejects legacy names like Asia/Calcutta.
		// Sessions carry their own timezone, so the server itself runs in UTC. Tests get this via surefire's argLine.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(TimelyApplication.class, args);
	}

}
