package com.dbbackup;

import com.dbbackup.wizard.MainMenuWizard;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DbBackupApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbBackupApplication.class, args);
    }

    @Bean
    public CommandLineRunner interactiveRunner(ApplicationContext context, MainMenuWizard mainMenuWizard) {
        return args -> {
            if (args.length == 0) {
                mainMenuWizard.start();
            }
        };
    }
}