package com.classforge.notification.push;

import nl.martijndwars.webpush.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;

/** Only encryption is delegated to web-push; no legacy/redirecting HTTP clients. */
@Component
public class VapidPushTransport implements PushTransport {
    private final Encoder encoder;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public VapidPushTransport(PushProperties properties) {
        if(!properties.enabled()){encoder=null;return;}
        try {
            Security.addProvider(new BouncyCastleProvider());
            URI subject=URI.create(properties.subject());
            if(!java.util.Set.of("https","mailto").contains(subject.getScheme()))throw new IllegalArgumentException();
            encoder=new Encoder(properties);
            if(!Utils.verifyKeyPair(encoder.getPrivateKey(),encoder.getPublicKey()))throw new IllegalArgumentException();
        }catch(Exception invalid){throw new IllegalStateException("Push enabled but VAPID configuration is invalid; check private environment values.");}
    }
    @Override public Result send(PushSubscription subscription,String payload) throws Exception {
        var encrypted=prepare(subscription,payload);
        URI target=PushValidation.endpoint(encrypted.getUrl());
        var request=java.net.http.HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(8))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(encrypted.getBody()));
        encrypted.getHeaders().forEach(request::header);
        var response=http.send(request.build(),HttpResponse.BodyHandlers.discarding());
        int retry=0;
        try { retry=Integer.parseInt(response.headers().firstValue("Retry-After").orElse("0")); }
        catch(NumberFormatException ignored){ }
        return new Result(response.statusCode(),Math.max(0,Math.min(3600,retry)));
    }
    nl.martijndwars.webpush.HttpRequest prepare(PushSubscription subscription,String payload)throws Exception {
        if(encoder==null)throw new IllegalStateException("Push disabled");
        PushValidation.endpoint(subscription.endpoint());
        return encoder.encode(subscription,payload);
    }
    private static final class Encoder extends AbstractPushService<Encoder> {
        Encoder(PushProperties properties)throws GeneralSecurityException{super(properties.publicKey(),properties.privateKey(),properties.subject());}
        nl.martijndwars.webpush.HttpRequest encode(PushSubscription subscription,String payload)throws Exception{
            return prepareRequest(new Notification(subscription.endpoint(),subscription.p256dh(),subscription.auth(),payload.getBytes(StandardCharsets.UTF_8),3600),Encoding.AES128GCM);
        }
    }
}
