package com.classforge.notification.push;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class PushValidationTests {
    @Test void acceptsOnlyHttpsKnownPushServicesWithoutUrlCredentialsOrRedirectTargets() {
        for (String endpoint : new String[]{"https://fcm.googleapis.com/fcm/send/opaque", "https://updates.push.services.mozilla.com/wpush/v2/opaque", "https://web.push.apple.com/opaque"}) {
            assertNotNull(PushValidation.endpoint(endpoint));
        }
        for (String endpoint : new String[]{"http://fcm.googleapis.com/fcm/send/a", "https://127.0.0.1/a", "https://fcm.googleapis.com.attacker.test/a", "https://fcm.googleapis.com@127.0.0.1/a", "https://fcm.googleapis.com:8443/a", "https://fcm.googleapis.com/a?target=http://localhost", "https://fcm.googleapis.com/a#b", "https://[::1]/", "https://fcm.googleapis.com/../admin", "https://fcm.googleapis.com", "https://fcm.googleapis.com/" + "a".repeat(2049)}) {
            assertThrows(IllegalArgumentException.class, () -> PushValidation.endpoint(endpoint), endpoint);
        }
    }
    @Test void validatesP256PointAndSixteenByteAuthKeyBeforePersisting() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var publicKey = (java.security.interfaces.ECPublicKey) generator.generateKeyPair().getPublic();
        byte[] point = new byte[65]; point[0]=4;
        System.arraycopy(unsigned(publicKey.getW().getAffineX()),0,point,1,32);
        System.arraycopy(unsigned(publicKey.getW().getAffineY()),0,point,33,32);
        String key=Base64.getUrlEncoder().withoutPadding().encodeToString(point);
        String auth=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        assertDoesNotThrow(()->PushValidation.keys(key,auth));
        assertThrows(IllegalArgumentException.class,()->PushValidation.keys(key,"a"));
        assertThrows(IllegalArgumentException.class,()->PushValidation.keys("invalid",auth));
        assertThrows(IllegalArgumentException.class,()->PushValidation.keys(Base64.getUrlEncoder().encodeToString(new byte[65]),auth));
        point[1]^=17;
        String offCurve=Base64.getUrlEncoder().withoutPadding().encodeToString(point);
        assertThrows(IllegalArgumentException.class,()->PushValidation.keys(offCurve,auth));
    }
    private byte[] unsigned(java.math.BigInteger number) {
        byte[] raw=number.toByteArray(), result=new byte[32];
        int length=Math.min(32,raw.length);System.arraycopy(raw,raw.length-length,result,32-length,length);return result;
    }
    @Test void endpointHashIsStableAndDoesNotExposeTheCapabilityUrl() {
        String endpoint="https://fcm.googleapis.com/fcm/send/opaque";
        assertEquals(64,PushValidation.hash(endpoint).length());
        assertEquals(PushValidation.hash(endpoint),PushValidation.hash(endpoint));
        assertNotEquals(PushValidation.hash(endpoint),PushValidation.hash(endpoint+"2"));
    }
}
