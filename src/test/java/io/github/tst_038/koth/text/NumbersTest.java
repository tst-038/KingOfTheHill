package io.github.tst_038.koth.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NumbersTest {

    @Test
    void groupsThousands() {
        assertEquals("0", Numbers.format(0));
        assertEquals("999", Numbers.format(999));
        assertEquals("1,000", Numbers.format(1000));
        assertEquals("1,250,000", Numbers.format(1_250_000));
        assertEquals("-12,345", Numbers.format(-12_345));
    }
}
