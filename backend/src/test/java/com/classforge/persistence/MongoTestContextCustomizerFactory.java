package com.classforge.persistence;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.*;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.*;

/** Each integration-test context uses a private database on a real MongoDB replica set. */
public class MongoTestContextCustomizerFactory implements ContextCustomizerFactory {
    @Override public ContextCustomizer createContextCustomizer(Class<?> type, List<ContextConfigurationAttributes> attributes) {
        return new Database(type.getName());
    }
    private record Database(String owner) implements ContextCustomizer {
        @Override public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration config) {
            String database = "cf_test_" + UUID.randomUUID().toString().replace("-", "");
            TestPropertyValues.of("spring.mongodb.database=" + database,
                    "classforge.activity.sessions.maintenance-enabled=false").applyTo(context);
            // The scheduler acceptance test explicitly enables it in its annotation.
            if (owner.endsWith("ProjectSessionMaintenanceTests")) {
                TestPropertyValues.of("classforge.activity.sessions.maintenance-enabled=true").applyTo(context);
            }
            context.addApplicationListener(event -> {
                if (event instanceof ContextClosedEvent closed && closed.getApplicationContext() == context) {
                    if (!database.matches("cf_test_[0-9a-f]{32}")) throw new IllegalStateException("Invalid test database scope");
                    context.getBean(MongoTemplate.class).getDb().drop();
                }
            });
        }
    }
}
