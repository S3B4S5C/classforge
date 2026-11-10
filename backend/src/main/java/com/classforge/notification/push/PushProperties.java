package com.classforge.notification.push;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
@ConfigurationProperties("classforge.push")
public record PushProperties(@DefaultValue("false") boolean enabled,@DefaultValue("") String publicKey,
        @DefaultValue("") String privateKey,@DefaultValue("https://classforge-tiago-15124.eastus.cloudapp.azure.com") String subject,
        @DefaultValue("true") boolean workerEnabled) {
    @Override public String toString(){return "PushProperties[enabled="+enabled+"]";}
}
