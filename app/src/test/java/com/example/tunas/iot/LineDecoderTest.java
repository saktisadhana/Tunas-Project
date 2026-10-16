package com.example.tunas.iot;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class LineDecoderTest {
    @Test public void overlongFrameIsDiscardedThroughNewlineAndBoundaryIsAccepted() {
        LineDecoder decoder = new LineDecoder();
        List<String> lines = new ArrayList<>();
        int[] errors = {0};
        byte[] tooLong = new byte[4097];
        Arrays.fill(tooLong, (byte) 'x');
        decoder.accept(tooLong, lines::add, () -> errors[0]++);
        decoder.accept("{}\n{}\n".getBytes(StandardCharsets.UTF_8), lines::add, () -> errors[0]++);
        assertEquals(Arrays.asList("{}"), lines);
        assertEquals(1, errors[0]);
        byte[] boundary = new byte[4096];
        Arrays.fill(boundary, (byte) 'a');
        decoder.accept(boundary, lines::add, () -> errors[0]++);
        decoder.accept(new byte[]{'\n'}, lines::add, () -> errors[0]++);
        assertEquals(4096, lines.get(1).length());
    }

    @Test public void invalidUtf8IsRejectedWithoutPoisoningFollowingFrames() {
        LineDecoder decoder = new LineDecoder();
        List<String> lines = new ArrayList<>();
        int[] errors = {0};
        decoder.accept(new byte[]{(byte) 0xc3, 0x28, '\n', '{', '}', '\n'},
                lines::add, () -> errors[0]++);
        assertEquals(Arrays.asList("{}"), lines);
        assertEquals(1, errors[0]);
    }

    @Test public void framesAcrossPacketsAndSplitUtf8AreDecodedOnlyAtNewline() {
        LineDecoder decoder = new LineDecoder();
        List<String> lines = new ArrayList<>();
        byte[] bytes = "{\"name\":\"tunas 🌱\"}\n{}\r\n\n".getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) decoder.accept(new byte[]{b}, lines::add, () -> fail("valid UTF-8"));
        assertEquals(Arrays.asList("{\"name\":\"tunas 🌱\"}", "{}"), lines);
        decoder.accept("unfinished".getBytes(StandardCharsets.UTF_8), lines::add, () -> fail());
        assertEquals(2, lines.size());
    }
}
