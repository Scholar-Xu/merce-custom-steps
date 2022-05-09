package com.inforefiner.custom.util;


import java.io.Serializable;

/**
 * Base64编码的字符串加密解密处理器
 *
 * @author jingwei.yang
 * @date 2019年11月21日 上午9:49:56
 */
public abstract class AbstractCryptor implements Serializable {

    private static final long serialVersionUID = -8826365457221462416L;

    private String prefix;
    private String suffix;

    public AbstractCryptor() {
    }

    public AbstractCryptor(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public boolean isEncrypted(String ciphertext) {
        if (ciphertext == null) {
            return false;
        }
        final String trimmedValue = ciphertext.trim();
        return (trimmedValue.startsWith(prefix) && trimmedValue.endsWith(suffix));
    }

    public boolean supports(String ciphertext) {
        return isEncrypted(ciphertext);
    }

    public String unwrapEncryptedValue(String ciphertext) {
        return ciphertext.substring(prefix.length(), (ciphertext.length() - suffix.length()));
    }

    public String decrypt(String ciphertext) {
        if (ciphertext != null && isEncrypted(ciphertext)) {
            return decryptCiphertext(unwrapEncryptedValue(ciphertext));
        }
        return ciphertext;
    }

    public String encrypt(String plaintext) {
        StringBuffer buff = new StringBuffer();
        if (plaintext != null && !isEncrypted(plaintext)) {
            return buff.append(prefix).append(encryptPlaintext(plaintext)).append(suffix).toString();
        }
        return plaintext;
    }

    /**
     * 解密没有标识包裹的密文
     *
     * @param unwrapEncryptedValue 没有包裹的密文本体
     * @return 明文
     */
    abstract String decryptCiphertext(String ciphertext);

    /**
     * 加密明文
     *
     * @param plaintext 明文
     * @return 密文，没有包裹前后缀
     */
    abstract String encryptPlaintext(String plaintext);

}
