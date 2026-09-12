package ${model.basePackage};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ${model.applicationClassName} {
    public static void main(String[] args) {
        SpringApplication.run(${model.applicationClassName}.class, args);
    }
}
