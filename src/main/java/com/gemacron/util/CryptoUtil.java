/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.gemacron.util;

/**
 *
 * @author gemacron
 */
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Utilidad para cifrado simétrico AES.
 * Protege las credenciales almacenadas en la base de datos SQLite local.
 */
public class CryptoUtil {

    // Llave estática de 16 bytes (128 bits) para AES
    private static final String SECRET_KEY = "FxLogSecKey2026!"; 
    private static final String ALGORITHM = "AES";

    public static String encrypt(String plainText) throws Exception {
        if (plainText == null || plainText.isEmpty()) return "";
        
        SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public static String decrypt(String encryptedText) throws Exception {
        if (encryptedText == null || encryptedText.isEmpty()) return "";
        
        SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes);
    }
}