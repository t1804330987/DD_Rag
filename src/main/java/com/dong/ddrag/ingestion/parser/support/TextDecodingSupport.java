package com.dong.ddrag.ingestion.parser.support;

import com.dong.ddrag.common.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class TextDecodingSupport {

    private static final byte[] UTF_8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF_16_LE_BOM = new byte[]{(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF_16_BE_BOM = new byte[]{(byte) 0xFE, (byte) 0xFF};
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final List<Charset> FALLBACK_CHARSETS = List.of(StandardCharsets.UTF_8, GB18030);

    private TextDecodingSupport() {
    }

    public static String decode(InputStream inputStream, String failureMessage) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            if (hasBom(bytes, UTF_8_BOM)) {
                return decodeStrict(bytes, UTF_8_BOM.length, StandardCharsets.UTF_8);
            }
            if (hasBom(bytes, UTF_16_LE_BOM)) {
                return decodeStrict(bytes, UTF_16_LE_BOM.length, StandardCharsets.UTF_16LE);
            }
            if (hasBom(bytes, UTF_16_BE_BOM)) {
                return decodeStrict(bytes, UTF_16_BE_BOM.length, StandardCharsets.UTF_16BE);
            }
            return decodeWithoutBom(bytes, failureMessage);
        } catch (IOException exception) {
            throw new BusinessException(failureMessage, exception);
        }
    }

    private static boolean hasBom(byte[] bytes, byte[] bom) {
        return bytes.length >= bom.length && Arrays.equals(Arrays.copyOf(bytes, bom.length), bom);
    }

    private static String decodeStrict(byte[] bytes, int offset, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer charBuffer = decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
        return charBuffer.toString();
    }

    private static String decodeWithoutBom(byte[] bytes, String failureMessage) throws CharacterCodingException {
        CharacterCodingException lastException = null;
        for (Charset charset : FALLBACK_CHARSETS) {
            try {
                return decodeStrict(bytes, 0, charset);
            } catch (CharacterCodingException exception) {
                lastException = exception;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new CharacterCodingException();
    }
}
