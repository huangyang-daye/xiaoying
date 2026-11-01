package com.xiaoying.studio;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Passwords {
    private static final int ITERATIONS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private Passwords() {}
    static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + encode(salt) + "$" + encode(pbkdf2(password, salt, ITERATIONS));
    }
    static boolean matches(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 10_000 || iterations > 1_000_000) return false;
            byte[] actual = pbkdf2(password, Base64.getDecoder().decode(parts[2]), iterations);
            return MessageDigest.isEqual(actual, Base64.getDecoder().decode(parts[3]));
        } catch (IllegalArgumentException e) { return false; }
    }
    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        try {
            PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
            try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
            finally { spec.clearPassword(); }
        } catch (GeneralSecurityException e) { throw new IllegalStateException("密码处理失败", e); }
        finally { java.util.Arrays.fill(chars, '\0'); }
    }
    private static String encode(byte[] value) { return Base64.getEncoder().withoutPadding().encodeToString(value); }
}
