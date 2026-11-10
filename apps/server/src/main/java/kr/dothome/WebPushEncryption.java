package kr.dothome;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;

final class WebPushEncryption {
    private static final int RECORD_SIZE=4096;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final ECGenParameterSpec CURVE=new ECGenParameterSpec("secp256r1");
    private static final byte[] KEY_INFO="WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO="Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO="Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII);

    private WebPushEncryption(){}

    static ECParameterSpec curve() throws GeneralSecurityException {
        var parameters=AlgorithmParameters.getInstance("EC");
        parameters.init(CURVE);
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    static ECPublicKey publicKey(byte[] point) throws GeneralSecurityException {
        if(point.length!=65||point[0]!=4)throw new InvalidKeyException("Invalid P-256 point.");
        var params=curve();
        var ec=params.getCurve();
        var modulus=((ECFieldFp)ec.getField()).getP();
        var x=new BigInteger(1,point,1,32);
        var y=new BigInteger(1,point,33,32);
        if(x.compareTo(modulus)>=0||y.compareTo(modulus)>=0||
            !y.multiply(y).mod(modulus).equals(x.pow(3).add(ec.getA().multiply(x)).add(ec.getB()).mod(modulus)))
            throw new InvalidKeyException("Invalid P-256 point.");
        return (ECPublicKey)KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x,y),params));
    }

    static byte[] encoded(ECPublicKey key){
        var point=key.getW();
        byte[] bytes=new byte[65];
        bytes[0]=4;
        coordinate(point.getAffineX(),bytes,1);
        coordinate(point.getAffineY(),bytes,33);
        return bytes;
    }

    static byte[] encrypt(byte[] message,PushKeyInput keys) throws GeneralSecurityException {
        var generator=KeyPairGenerator.getInstance("EC");
        generator.initialize(CURVE);
        var decoder=Base64.getUrlDecoder();
        var salt=new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(message,decoder.decode(keys.p256dh()),decoder.decode(keys.auth()),generator.generateKeyPair(),salt);
    }

    // RFC 8291 uses one RFC 8188 aes128gcm record for each Web Push message.
    static byte[] encrypt(byte[] message,byte[] receiver,byte[] auth,KeyPair sender,byte[] salt) throws GeneralSecurityException {
        if(auth.length!=16||salt.length!=16||message.length>RECORD_SIZE-103)
            throw new InvalidKeyException("Invalid Web Push record.");
        var senderPoint=encoded((ECPublicKey)sender.getPublic());
        var agreement=KeyAgreement.getInstance("ECDH");
        agreement.init(sender.getPrivate());
        agreement.doPhase(publicKey(receiver),true);
        var shared=agreement.generateSecret();
        var keyInfo=ByteBuffer.allocate(KEY_INFO.length+131).put(KEY_INFO).put(receiver).put(senderPoint).put((byte)1).array();
        var keyMaterial=mac(mac(auth,shared),keyInfo);
        var extract=mac(salt,keyMaterial);
        var key=Arrays.copyOf(mac(extract,CEK_INFO),16);
        var nonce=Arrays.copyOf(mac(extract,NONCE_INFO),12);
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        byte[] input=Arrays.copyOf(message,message.length+1);
        input[message.length]=2;
        byte[] encrypted=cipher.doFinal(input);
        return ByteBuffer.allocate(86+encrypted.length).put(salt).putInt(RECORD_SIZE)
            .put((byte)senderPoint.length).put(senderPoint).put(encrypted).array();
    }

    private static byte[] mac(byte[] key,byte[] input) throws GeneralSecurityException {
        var mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key,"HmacSHA256"));
        return mac.doFinal(input);
    }

    private static void coordinate(BigInteger coordinate,byte[] target,int offset){
        byte[] number=coordinate.toByteArray();
        int length=Math.min(32,number.length);
        System.arraycopy(number,number.length-length,target,offset+32-length,length);
    }
}
