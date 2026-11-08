package com.classforge.migration;

import com.classforge.ClassForgeApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.nio.file.Path;
import java.util.*;

public class LegacyH2MigrationCli {
    public static void main(String[] args) throws Exception {
        Map<String,String> options = new HashMap<>();
        for (String arg : args) { int split = arg.indexOf('='); if (split > 0) options.put(arg.substring(0, split), arg.substring(split + 1)); }
        String source = Objects.requireNonNull(options.get("--source"), "--source es obligatorio");
        String database = options.getOrDefault("--database", "classforge");
        if (!database.matches("[A-Za-z0-9_-]{1,63}")) throw new IllegalArgumentException("Nombre de base MongoDB inválido");
        try (var app = new SpringApplicationBuilder(ClassForgeApplication.class).run("--server.port=0", "--server.address=127.0.0.1",
                "--spring.mongodb.database=" + database, "--classforge.activity.sessions.maintenance-enabled=false", "--logging.level.root=WARN")) {
            var report = app.getBean(LegacyH2MigrationService.class).migrate(Path.of(source));
            System.out.println(app.getBean(tools.jackson.databind.json.JsonMapper.class).writeValueAsString(report));
        }
    }
}
