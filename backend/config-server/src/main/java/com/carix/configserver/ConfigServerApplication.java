package com.carix.configserver;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

	/**
	 * Spring Cloud Config 의 git private-key 는 키 내용을 받는다. 파일 경로가 아니다.
	 * 컨테이너는 deploy key 를 파일로 마운트하므로 기동 시 읽어 프로퍼티로 옮긴다.
	 */
	public static void main(String[] args) throws Exception {
		String keyFile = System.getenv("CONFIG_GIT_PRIVATE_KEY_FILE");
		if (keyFile != null && !keyFile.isBlank()) {
			System.setProperty("CONFIG_GIT_PRIVATE_KEY", Files.readString(Path.of(keyFile)));
		}
		SpringApplication.run(ConfigServerApplication.class, args);
	}
}
