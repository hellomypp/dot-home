package kr.dothome;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DotHomeApplication {
    public static void main(String[] args) { SpringApplication.run(DotHomeApplication.class, args); }
}
