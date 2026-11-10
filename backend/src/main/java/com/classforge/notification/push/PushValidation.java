package com.classforge.notification.push;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class PushValidation {
    private static final Set<String> PROVIDERS=Set.of("fcm.googleapis.com","updates.push.services.mozilla.com","web.push.apple.com");
    private PushValidation() { }
    public static URI endpoint(String value) {
        try {
            if(value==null||value.length()>2048)throw new IllegalArgumentException();
            URI uri=URI.create(value);
            if(!"https".equals(uri.getScheme())||!PROVIDERS.contains(uri.getHost())||uri.getRawUserInfo()!=null
                    ||(uri.getPort()!=-1&&uri.getPort()!=443)||uri.getRawQuery()!=null||uri.getRawFragment()!=null
                    ||uri.getRawPath()==null||uri.getRawPath().length()<2||!uri.equals(uri.normalize())
                    ||Arrays.asList(uri.getRawPath().split("/")).contains("..")
                    ||uri.getRawPath().toLowerCase(Locale.ROOT).contains("%2e"))throw new IllegalArgumentException();
            return uri;
        } catch(RuntimeException invalid) { throw new IllegalArgumentException("Unsupported push endpoint"); }
    }
    public static void keys(String publicKey,String auth) {
        try {
            byte[] point=decode(publicKey), secret=decode(auth);
            if(point.length!=65||point[0]!=4||secret.length!=16)throw new IllegalArgumentException();
            if(!SECNamedCurves.getByName("secp256r1").getCurve().decodePoint(point).isValid())throw new IllegalArgumentException();
        } catch(RuntimeException invalid) { throw new IllegalArgumentException("Invalid push subscription keys"); }
    }
    private static byte[] decode(String value) {
        if(value==null||value.length()>100||!value.matches("[A-Za-z0-9_-]+={0,2}"))throw new IllegalArgumentException();
        return Base64.getUrlDecoder().decode(value);
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
