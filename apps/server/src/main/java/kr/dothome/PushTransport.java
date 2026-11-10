package kr.dothome;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class PushTransport {
    private static final Base64.Encoder BASE64=Base64.getUrlEncoder().withoutPadding();
    private static final String JWT_HEADER=BASE64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
    private final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(2)).build();
    private final String publicKey;
    private final PrivateKey privateKey;
    private final String subject;
    private final ObjectMapper json;

    PushTransport(@Value("${app.push.public-key:}") String publicKey,@Value("${app.push.private-key:}") String privateKey,
        @Value("${app.push.subject:}") String subject,ObjectMapper json){
        this.publicKey=publicKey;this.subject=subject;this.json=json;
        if(publicKey.isBlank()&&privateKey.isBlank()&&subject.isBlank()){
            this.privateKey=null;
            return;
        }
        try {
            if(!validSubject(subject))throw new GeneralSecurityException();
            var factory=KeyFactory.getInstance("EC");
            var decoder=Base64.getUrlDecoder();
            var point=decoder.decode(publicKey);
            if(point.length!=65||point[0]!=4||!BASE64.encodeToString(point).equals(publicKey))throw new GeneralSecurityException();
            var published=WebPushEncryption.publicKey(point);
            this.privateKey=factory.generatePrivate(new PKCS8EncodedKeySpec(decoder.decode(privateKey)));
            var verifier=Signature.getInstance("SHA256withECDSAinP1363Format");
            byte[] sample="dot-home-push".getBytes(StandardCharsets.UTF_8);
            verifier.initVerify(published);
            verifier.update(sample);
            if(!verifier.verify(sign(sample)))throw new GeneralSecurityException();
        } catch (GeneralSecurityException|IllegalArgumentException ex){
            throw new IllegalStateException("Web Push requires a valid matching P-256 key pair and contact subject.");
        }
    }

    boolean enabled(){return privateKey!=null;}
    String publicKey(){return publicKey;}
    String fingerprint(){return HomeService.hash(publicKey);}

    static URI endpoint(String input){
        if(input==null||input.length()>2048)throw invalidEndpoint();
        try {
            var uri=URI.create(input);
            var host=uri.getHost();
            var path=uri.getRawPath();
            if(!"https".equals(uri.getScheme())||host==null||uri.getPort()!=-1||uri.getRawUserInfo()!=null||
                uri.getRawQuery()!=null||uri.getRawFragment()!=null||path==null)throw invalidEndpoint();
            boolean supported=switch(host){
                case "fcm.googleapis.com" -> path.matches("/(?:fcm/send|wp)/[A-Za-z0-9_~.:-]+");
                case "updates.push.services.mozilla.com" -> path.matches("/wpush/v[12]/[A-Za-z0-9_-]+");
                default -> host.matches("(?:[a-z0-9-]+\\.)+push\\.apple\\.com")
                    && path.matches("/[A-Za-z0-9_-]+");
            };
            if(!supported)throw invalidEndpoint();
            return uri;
        } catch(IllegalArgumentException ex){throw invalidEndpoint();}
    }

    private static boolean validSubject(String subject){
        var uri=URI.create(subject);
        return subject.length()<=256&&(subject.matches("mailto:[^@\\s]+@[^@\\s]+")||
            ("https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getRawUserInfo()==null
                &&uri.getRawQuery()==null&&uri.getRawFragment()==null));
    }

    String authorization(URI endpoint,Instant now) throws GeneralSecurityException {
        String claims=json.writeValueAsString(Map.of("aud","https://"+endpoint.getHost(),
            "exp",now.plus(Duration.ofHours(4)).getEpochSecond(),"sub",subject));
        String signingInput=JWT_HEADER+"."+BASE64.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        return "vapid t="+signingInput+"."+BASE64.encodeToString(sign(signingInput.getBytes(StandardCharsets.US_ASCII)))+", k="+publicKey;
    }

    private byte[] sign(byte[] message) throws GeneralSecurityException {
        var signature=Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(privateKey);
        signature.update(message);
        return signature.sign();
    }

    PushResponse send(PreparedPush push,Instant now) throws IOException,InterruptedException,GeneralSecurityException {
        var uri=endpoint(push.endpoint());
        if(uri.getHost().equals("fcm.googleapis.com")&&uri.getPath().startsWith("/fcm/send/"))
            uri=URI.create("https://fcm.googleapis.com/wp/"+uri.getPath().substring("/fcm/send/".length()));
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3))
            .header("Authorization",authorization(uri,now)).header("TTL",Integer.toString(push.ttl()))
            .header("Topic",push.topic()).header("Urgency","low");
        var body=HttpRequest.BodyPublishers.noBody();
        if(push.keys()!=null){
            var encrypted=WebPushEncryption.encrypt(json.writeValueAsBytes(push.payload()),push.keys());
            body=HttpRequest.BodyPublishers.ofByteArray(encrypted);
            request.header("Content-Encoding","aes128gcm").header("Content-Type","application/octet-stream");
        }
        var response=http.send(request.POST(body).build(),HttpResponse.BodyHandlers.discarding());
        var retry=response.headers().firstValue("Retry-After").map(value->retryAfter(value,now)).orElse(Duration.ZERO);
        return new PushResponse(response.statusCode(),retry);
    }

    private static Duration retryAfter(String value,Instant now){
        try {return Duration.ofSeconds(Math.max(0,Long.parseLong(value)));}
        catch(RuntimeException ignored){
            try {return Duration.between(now,ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());}
            catch(RuntimeException invalid){return Duration.ZERO;}
        }
    }

    private static ApiException invalidEndpoint(){return ApiException.invalid("지원하는 브라우저 알림 주소를 확인해 주세요.");}
}

record PushResponse(int status,Duration retryAfter) {}
