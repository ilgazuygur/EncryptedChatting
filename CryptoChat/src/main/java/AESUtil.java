import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class AESUtil {

    //Cipher is the encryption engine that performs AES encryption and decryption
    //Encrypting the text message using the provided AES key and returning it as a Base64 string
    public static String encrypt(String textMessage, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedBytes = cipher.doFinal(textMessage.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    //Cipher is the encryption engine that performs AES encryption and decryption
    //Decrypting the Base64 encoded message using the provided AES key and returning plain text
    public static String decrypt(String textMessage, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedBytes = Base64.getDecoder().decode(textMessage);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes);
    }

    //KeyGenerator is a tool that generates a new AES secret key
    //Generating a hard coded 128 bit AES key and returning it as a SecretKey object
    public static SecretKey getFixedKey() throws Exception {
        byte[] keyBytes = "1234567890123456".getBytes(); 
        return new SecretKeySpec(keyBytes, "AES");
    }   
}