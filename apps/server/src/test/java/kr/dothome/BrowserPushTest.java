package kr.dothome;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.math.BigInteger;
import java.security.spec.ECPrivateKeySpec;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class BrowserPushTest {
    @Test void vapidUsesMatchingP256KeysAndOnlyPublicConfigurationIsExposed() throws Exception {
        var keys=KeyPairGenerator.getInstance("EC");
        keys.initialize(new ECGenParameterSpec("secp256r1"));
        var pair=keys.generateKeyPair();
        var point=((ECPublicKey)pair.getPublic()).getW();
        byte[] raw=new byte[65];raw[0]=4;
        coordinate(point.getAffineX().toByteArray(),raw,1);
        coordinate(point.getAffineY().toByteArray(),raw,33);
        var base64=Base64.getUrlEncoder().withoutPadding();
        var publicKey=base64.encodeToString(raw);
        var privateKey=base64.encodeToString(pair.getPrivate().getEncoded());
        var json=new ObjectMapper();
        var transport=new PushTransport(publicKey,privateKey,"https://example.test",json);
        assertTrue(transport.enabled());
        var endpoint=PushTransport.endpoint("https://fcm.googleapis.com/fcm/send/test-token");
        var now=Instant.parse("2026-11-10T12:00:00Z");
        var authorization=transport.authorization(endpoint,now);
        var token=authorization.substring("vapid t=".length(),authorization.indexOf(", k="));
        var parts=token.split("\\.");
        var claims=json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertEquals("https://fcm.googleapis.com",claims.get("aud").asText());
        assertEquals(now.plusSeconds(14_400).getEpochSecond(),claims.get("exp").asLong());
        var signature=Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initVerify(pair.getPublic());
        signature.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(signature.verify(Base64.getUrlDecoder().decode(parts[2])));
        assertThrows(IllegalStateException.class,()->new PushTransport(publicKey,"invalid","https://example.test",json));
        assertFalse(new PushTransport("","","",json).enabled());
    }

    @Test void endpointsAreLimitedToKnownHttpsPushProviders(){
        assertEquals("web.push.apple.com",PushTransport.endpoint("https://web.push.apple.com/test-token").getHost());
        assertEquals("updates.push.services.mozilla.com",PushTransport.endpoint("https://updates.push.services.mozilla.com/wpush/v2/test-token").getHost());
        for(var endpoint:List.of("http://127.0.0.1:8080/healthz","https://localhost/test","https://fcm.googleapis.com.evil.test/wp/test",
            "https://fcm.googleapis.com@127.0.0.1/wp/test","https://fcm.googleapis.com:8080/wp/test",
            "https://fcm.googleapis.com/wp/../x","https://fcm.googleapis.com/wp/test?token=secret")){
            assertThrows(ApiException.class,()->PushTransport.endpoint(endpoint),endpoint);
        }
    }

    @Test void encryptedBodyMatchesRfc8291VectorAndRejectsInvalidCurvePoints() throws Exception {
        var decoder=Base64.getUrlDecoder();
        byte[] receiver=decoder.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] sender=decoder.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        var scalar=new BigInteger(1,decoder.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));
        var privateKey=KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(scalar,WebPushEncryption.curve()));
        byte[] result=WebPushEncryption.encrypt(decoder.decode("V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24"),
            receiver,decoder.decode("BTBZMqHH6r4Tts7J_aSIgg"),new KeyPair(WebPushEncryption.publicKey(sender),privateKey),
            decoder.decode("DGv6ra1nlYgDCS1FRnbzlw"));
        assertArrayEquals(decoder.decode(
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
            +"mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT"
            +"pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN"),result);
        byte[] invalid=new byte[65];
        invalid[0]=4;
        assertThrows(InvalidKeyException.class,()->WebPushEncryption.publicKey(invalid));
    }

    @Test void quietHoursUseRecipientsZoneAcrossMidnightAndDst(){
        var seoul=new PushQuietHours(1320,480,"Asia/Seoul");
        seoul.validate();
        assertTrue(seoul.includes(Instant.parse("2026-11-10T13:00:00Z")));
        assertFalse(seoul.includes(Instant.parse("2026-11-10T23:00:00Z")));
        var newYork=new PushQuietHours(1320,480,"America/New_York");
        assertTrue(newYork.includes(Instant.parse("2026-11-01T05:30:00Z")));
        assertTrue(newYork.includes(Instant.parse("2026-11-01T06:30:00Z")));
        assertFalse(new PushQuietHours(null,null,"Asia/Seoul").includes(Instant.now()));
        assertThrows(ApiException.class,()->new PushQuietHours(60,60,"Asia/Seoul").validate());
        assertThrows(ApiException.class,()->new PushQuietHours(null,60,"Asia/Seoul").validate());
        assertThrows(ApiException.class,()->new PushQuietHours(60,120,"unknown/zone").validate());
    }

    private static void coordinate(byte[] number,byte[] target,int offset){
        int length=Math.min(32,number.length);
        System.arraycopy(number,number.length-length,target,offset+32-length,length);
    }
}
