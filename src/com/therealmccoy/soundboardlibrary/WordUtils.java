package com.therealmccoy.soundboardlibrary;

public class WordUtils {

	/**
     * <p><code>WordUtils</code> instances should NOT be constructed in
     * standard programming. Instead, the class should be used as
     * <code>WordUtils.wrap("foo bar", 20);</code>.</p>
     *
     * <p>This constructor is public to permit tools that require a JavaBean
     * instance to operate.</p>
     */
    public WordUtils() {
        super ();
    }

    // Capitalizing
    //-----------------------------------------------------------------------
    /**
     * <p>Capitalizes all the whitespace separated words in a String.
     * Only the first letter of each word is changed. To convert the 
     * rest of each word to lowercase at the same time, 
     * use {@link #capitalizeFully(String)}.</p>
     *
     * <p>Whitespace is defined by {@link Character#isWhitespace(char)}.
     * A <code>null</code> input String returns <code>null</code>.
     * Capitalization uses the unicode title case, normally equivalent to
     * upper case.</p>
     *
     * <pre>
     * WordUtils.capitalize(null)        = null
     * WordUtils.capitalize("")          = ""
     * WordUtils.capitalize("i am FINE") = "I Am FINE"
     * </pre>
     * 
     * @param str  the String to capitalize, may be null
     * @return capitalized String, <code>null</code> if null String input
     * @see #uncapitalize(String)
     * @see #capitalizeFully(String)
     */
    public static String capitalize(String str) {
        return capitalize(str, null);
    }

    /**
     * <p>Capitalizes all the delimiter separated words in a String.
     * Only the first letter of each word is changed. To convert the 
     * rest of each word to lowercase at the same time, 
     * use {@link #capitalizeFully(String, char[])}.</p>
     *
     * <p>The delimiters represent a set of characters understood to separate words.
     * The first string character and the first non-delimiter character after a
     * delimiter will be capitalized. </p>
     *
     * <p>A <code>null</code> input String returns <code>null</code>.
     * Capitalization uses the unicode title case, normally equivalent to
     * upper case.</p>
     *
     * <pre>
     * WordUtils.capitalize(null, *)            = null
     * WordUtils.capitalize("", *)              = ""
     * WordUtils.capitalize(*, new char[0])     = *
     * WordUtils.capitalize("i am fine", null)  = "I Am Fine"
     * WordUtils.capitalize("i aM.fine", {'.'}) = "I aM.Fine"
     * </pre>
     * 
     * @param str  the String to capitalize, may be null
     * @param delimiters  set of characters to determine capitalization, null means whitespace
     * @return capitalized String, <code>null</code> if null String input
     * @see #uncapitalize(String)
     * @see #capitalizeFully(String)
     * @since 2.1
     */
    public static String capitalize(String str, char[] delimiters) {
        int delimLen = (delimiters == null ? -1 : delimiters.length);
        if (str == null || str.length() == 0 || delimLen == 0) {
            return str;
        }
        int strLen = str.length();
        StringBuffer buffer = new StringBuffer(strLen);
        boolean capitalizeNext = true;
        for (int i = 0; i < strLen; i++) {
            char ch = str.charAt(i);

            if (isDelimiter(ch, delimiters)) {
                buffer.append(ch);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                buffer.append(Character.toTitleCase(ch));
                capitalizeNext = false;
            } else {
                buffer.append(ch);
            }
        }
        return buffer.toString();
    }

    //-----------------------------------------------------------------------
    /**
     * Is the character a delimiter.
     *
     * @param ch  the character to check
     * @param delimiters  the delimiters
     * @return true if it is a delimiter
     */
    private static boolean isDelimiter(char ch, char[] delimiters) {
        if (delimiters == null) {
            return Character.isWhitespace(ch);
        }
        for (int i = 0, isize = delimiters.length; i < isize; i++) {
            if (ch == delimiters[i]) {
                return true;
            }
        }
        return false;
    }
}
