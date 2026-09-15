package fr.an.projectanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProjectAnalysisApplicationMain {

    public static void main(String[] args) {
        try {
            SpringApplication.run(ProjectAnalysisApplicationMain.class, args);
        } catch(Throwable ex) {
            System.out.println("Failed .. exiting!");
            throw ex;
        }
    }

}
