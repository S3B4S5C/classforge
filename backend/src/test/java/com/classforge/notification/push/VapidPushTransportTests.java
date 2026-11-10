package com.classforge.notification.push;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.interfaces.*;
import org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.Test;
import java.security.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VapidPushTransportTests {
    String[] pair()throws Exception{
        Security.addProvider(new BouncyCastleProvider());var g=KeyPairGenerator.getInstance("ECDH","BC");g.initialize(new ECNamedCurveGenParameterSpec("secp256r1"));var k=g.generateKeyPair();
        return new String[]{Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPublicKey)k.getPublic())),Base64.getUrlEncoder().withoutPadding().encodeToString(((ECPrivateKey)k.getPrivate()).getD().toByteArray())};
    }
    @Test void preparesEncryptedAes128PayloadAndVerifiableVapidWithoutLegacyHttpClients()throws Exception{
        var server=pair();var device=pair();
        var properties=new PushProperties(true,server[0],server[1],"https://classforge.example",true);
        var transport=new VapidPushTransport(properties);
        var subscription=new PushSubscription("hash",UUID.randomUUID(),UUID.randomUUID(),"https://fcm.googleapis.com/fcm/send/opaque",device[0],Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]),Instant.now(),Instant.now(),null);
        var request=transport.prepare(subscription,"mensaje de prueba privado");
        assertEquals("aes128gcm",request.getHeaders().get("Content-Encoding"));
        assertFalse(new String(request.getBody(),java.nio.charset.StandardCharsets.ISO_8859_1).contains("mensaje de prueba privado"));
        String header=request.getHeaders().get("Authorization");assertTrue(header.startsWith("vapid t="));
        var jws=new JsonWebSignature();jws.setCompactSerialization(header.substring(8,header.indexOf(", k=")));jws.setKey(Utils.loadPublicKey(server[0]));assertTrue(jws.verifySignature());
    }
    @Test void rejectsMismatchedVapidKeysWithoutPrintingTheirValues()throws Exception{
        var a=pair();var b=pair();var e=assertThrows(IllegalStateException.class,()->new VapidPushTransport(new PushProperties(true,a[0],b[1],"https://classforge.example",true)));
        assertFalse(e.getMessage().contains(b[1]));
    }
}
