package org.mapleir.ir.antlr.util;

public class Conversion {

    public static int asInt(String s, int radix) throws NumberFormatException {
        if (radix == 10) {
            return Integer.parseInt(s, radix);
        } else {
            char[] cs = s.toCharArray();
            int limit = Integer.MAX_VALUE / (radix / 2);
            int n = 0;
            for (int i = 0; i < cs.length; i++) {
                int d = Character.digit(cs[i], radix);
                if (n < 0 || n > limit || n * radix > Integer.MAX_VALUE - d)
                    throw new NumberFormatException();
                n = n * radix + d;
            }
            return n;
        }
    }

    public static long asLong(String s, int radix)
            throws NumberFormatException {
        if (radix == 10) {
            return Long.parseLong(s, radix);
        } else {
            char[] cs = s.toCharArray();
            long limit = Long.MAX_VALUE / (radix / 2);
            long n = 0;
            for (int i = 0; i < cs.length; i++) {
                int d = Character.digit(cs[i], radix);
                if (n < 0 || n > limit || n * radix > Long.MAX_VALUE - d)
                    throw new NumberFormatException();
                n = n * radix + d;
            }
            return n;
        }
    }

    public static boolean isZero(String s) {
        char[] cs = s.toCharArray();
        int base = ((cs.length > 1 && Character.toLowerCase(cs[1]) == 'x') ? 16
                : 10);
        int i = ((base == 16) ? 2 : 0);
        while (i < cs.length && (cs[i] == '0' || cs[i] == '.')) {
            i++;
        }
        return !(i < cs.length && (Character.digit(cs[i], base) > 0));
    }

    public static int chars2utf(char[] src, int sindex, byte[] dst, int dindex,
            int len) {
        int j = dindex;
        int limit = sindex + len;
        for (int i = sindex; i < limit; i++) {
            char ch = src[i];
            if (1 <= ch && ch <= 0x7F) {
                dst[j++] = (byte) ch;
            } else if (ch <= 0x7FF) {
                dst[j++] = (byte) (0xC0 | (ch >> 6));
                dst[j++] = (byte) (0x80 | (ch & 0x3F));
            } else {
                dst[j++] = (byte) (0xE0 | (ch >> 12));
                dst[j++] = (byte) (0x80 | ((ch >> 6) & 0x3F));
                dst[j++] = (byte) (0x80 | (ch & 0x3F));
            }
        }
        return j;
    }

    public static int utf2chars(byte[] src, int sindex, char[] dst, int dindex,
            int len) {
        int i = sindex;
        int j = dindex;
        int limit = sindex + len;
        while (i < limit) {
            int b = src[i++] & 0xFF;
            if (b >= 0xE0) {
                b = (b & 0x0F) << 12;
                b = b | (src[i++] & 0x3F) << 6;
                b = b | (src[i++] & 0x3F);
            } else if (b >= 0xC0) {
                b = (b & 0x1F) << 6;
                b = b | (src[i++] & 0x3F);
            }
            dst[j++] = (char) b;
        }
        return j;
    }

    public static String utf2string(byte[] src, int sindex, int len) {
        char dst[] = new char[len];
        int len1 = utf2chars(src, sindex, dst, 0, len);
        return new String(dst, 0, len1);
    }
}