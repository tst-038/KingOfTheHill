package io.github.tst_038.koth.text;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Writes points and costs with their thousands grouped, the way players read them: "1,250,000". */
public final class Numbers {

    private Numbers() {
    }

    public static String format(long number) {
        // A new format each call: DecimalFormat isn't thread-safe, and placeholders may be asked off the server thread.
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(number);
    }
}
