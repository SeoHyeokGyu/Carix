package com.carix.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 옮겨 온 코드가 com.carix.crawler·com.carix.jobposting 패키지명을 그대로 유지하므로
 * (설계 §2) 이 클래스가 있는 com.carix.collector 아래에 들어오지 않는다. 스캔 대상을
 * 명시하지 않으면 엔티티와 리포지터리가 빈으로 만들어지지 않는다.
 */
@SpringBootApplication(scanBasePackages = {
    "com.carix.collector",
    "com.carix.crawler",
    "com.carix.jobposting"
})
@EntityScan("com.carix.jobposting")
@EnableJpaRepositories("com.carix.jobposting")
@ConfigurationPropertiesScan
public class CollectorApplication {

  public static void main(String[] args) {
    SpringApplication.run(CollectorApplication.class, args);
  }

}
