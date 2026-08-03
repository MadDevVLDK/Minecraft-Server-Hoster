package com.example.minecraftserver.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.example.minecraftserver.config.AppProperties;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

@Service
@RequiredArgsConstructor
public class TotpService {

    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base32 base32 = new Base32();

    public String generateSecret() {
        byte[] buffer = new byte[getSecretSize()];
        secureRandom.nextBytes(buffer);
        return base32.encodeToString(buffer).replace("=", "");
    }

    public String buildOtpAuthUri(String accountName, String secret) {
        String issuerValue = URLEncoder.encode("Некрополь", StandardCharsets.UTF_8);
        String label = URLEncoder.encode("Некрополь:" + accountName, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuerValue + "&algorithm=SHA1&digits=6&period=" + getTimeStepSeconds();
    }

    public String generateQrCodeDataUrl(String otpAuthUri) throws MyException {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                otpAuthUri,
                BarcodeFormat.QR_CODE,
                280, 280,
                java.util.Map.of(EncodeHintType.MARGIN, 1)
            );
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception ex) {
            throw new MyException(ErrorCode.FAILED_TO_GENERATE_TOTP_QR_CODE);
        }
    }

    public boolean isCodeValid(String secret, String code) throws MyException {
        if (secret == null || secret.isBlank() || code == null || !code.matches("^[0-9]{6}$")) {
            return false;
        }

        long currentCounter = Instant.now().getEpochSecond() / getTimeStepSeconds();
        for (long offset = -1; offset <= 1; offset++) {
            if (generateCode(secret, currentCounter + offset).equals(code)) {
                return true;
            }
        }
        return false;
    }

    private String generateCode(String secret, long counter) throws MyException {
        try {
            byte[] secretBytes = base32.decode(secret);
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (Exception ex) {
            throw new MyException(ErrorCode.FAILED_TO_GENERATE_TOTP_CODE);
        }
    }

    private int getTimeStepSeconds() {
        return appProperties.getSecurity().getTotp().getTimeStepSeconds();
    }

    private int getSecretSize() {
        return appProperties.getSecurity().getTotp().getSecretSize();
    }
}