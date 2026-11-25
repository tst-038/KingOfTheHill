package io.github.tst_038.koth.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ShopEditorTest {

    @Test
    void aNewIdIsTheItemsUnlessTakenThenNumbered() {
        assertEquals("diamond", ShopEditor.freeId("diamond", Set.of()::contains));
        assertEquals("diamond-2", ShopEditor.freeId("diamond", Set.of("diamond")::contains));
        assertEquals("diamond-4", ShopEditor.freeId("diamond", Set.of("diamond", "diamond-2", "diamond-3")::contains));
    }
}
