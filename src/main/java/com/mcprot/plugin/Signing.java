package com.mcprot.plugin;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class Signing {
    private static PublicKey publicKey;

    public static void init() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] encodedKey = ByteStreams.toByteArray(Signing.class.getResourceAsStream("/public_key.der"));
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        publicKey = keyFactory.generatePublic(keySpec);
    }

    public static boolean verify(byte[] data, String signature) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        byte[] decodedSignature = Base64.getDecoder().decode(signature);
        Signature sig = Signature.getInstance("SHA512withRSA");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(decodedSignature);
    }
}
