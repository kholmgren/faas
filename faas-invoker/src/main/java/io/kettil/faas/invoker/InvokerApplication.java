package io.kettil.faas.invoker;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kettil.faas.Manifest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

import java.io.File;

@Slf4j
@SpringBootApplication
public class InvokerApplication {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(InvokerApplication.class, args);
    }

    public static ObjectMapper yamlMapper(){
        return new ObjectMapper(new YAMLFactory())
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @SneakyThrows
    @Bean
    public Manifest manifest(@Value("${manifest}") String manifestLocation) {
        return yamlMapper().readValue(new File(manifestLocation), Manifest.class);
    }
}
