package fr.an.jira;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JiraSyncApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(JiraSyncApplication.class, args);
        } catch(Throwable ex) {
            System.out.println("Failed .. exiting!");
            throw ex;
        }
    }

}
