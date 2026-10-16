package com.example.tunas.iot;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.function.Consumer;

/** Newline-delimited notifications may split even inside a UTF-8 code point. */
final class LineDecoder {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean discarding;

    void accept(byte[] bytes, Consumer<String> onLine, Runnable onInvalid) {
        for (byte b : bytes) {
            if (discarding) {
                if (b == '\n') discarding = false;
                continue;
            }
            if (b == '\n') {
                byte[] frame = buffer.toByteArray();
                buffer.reset();
                int length = frame.length;
                if (length > 0 && frame[length - 1] == '\r') length--;
                if (length > 0) {
                    try {
                        onLine.accept(StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(frame, 0, length)).toString());
                    } catch (CharacterCodingException invalid) {
                        onInvalid.run();
                    }
                }
            } else if (buffer.size() == 4096) {
                buffer.reset();
                discarding = true;
                onInvalid.run();
            } else {
                buffer.write(b);
            }
        }
    }
}
