package org.mapleir.ir.antlr.internallex;

import org.mapleir.ir.antlr.util.Conversion;

public class Name implements javax.lang.model.element.Name {

    public Table table;
    public int index;
    public int len;
    Name next;
    
    private static int hashValue(byte cs[], int start, int len) {
        int h = 0;
        int off = start;

        for (int i = 0; i < len; i++) {
            h = (h << 5) - h + cs[off++];
        }
        return h;
    }

    /** Is (the utf8 representation of) name equal to
     *  cs[start..start+len-1]?
     */
    private static boolean equals(byte[] names, int index,
                                  byte cs[], int start, int len) {
        int i = 0;
        while (i < len && names[index + i] == cs[start + i]) i++;
        return i == len;
    }

    public static Name fromUtf(Table table, byte cs[], int start, int len) {
        int h = hashValue(cs, start, len) & table.hashMask;
        Name n = table.hashes[h];
        byte[] names = table.names;
        while (n != null &&
               (n.len != len || !equals(names, n.index, cs, start, len)))
            n = n.next;
        if (n == null) {
            int nc = table.nc;
            while (nc + len > names.length) {
                byte[] newnames = new byte[names.length * 2];
                System.arraycopy(names, 0, newnames, 0, names.length);
                names = table.names = newnames;
            }
            System.arraycopy(cs, start, names, nc, len);
            n = new Name();
            n.table = table;
            n.index = nc;
            n.len = len;
            n.next = table.hashes[h];
            table.hashes[h] = n;
            table.nc = nc + len;
            if (len == 0) table.nc++;
        }
        return n;
    }

    public static Name fromUtf(Table table, byte cs[]) {
        return fromUtf(table, cs, 0, cs.length);
    }

    public static Name fromChars(Table table, char[] cs, int start, int len) {
        int nc = table.nc;
        byte[] names = table.names;
        while (nc + len * 3 >= names.length) {
            byte[] newnames = new byte[names.length * 2];
            System.arraycopy(names, 0, newnames, 0, names.length);
            names = table.names = newnames;
        }
        int nbytes =
            Conversion.chars2utf(cs, start, names, nc, len) - nc;
        int h = hashValue(names, nc, nbytes) & table.hashMask;
        Name n = table.hashes[h];
        while (n != null &&
               (n.len != nbytes ||
                !equals(names, n.index, names, nc, nbytes)))
            n = n.next;
        if (n == null) {
            n = new Name();
            n.table = table;
            n.index = nc;
            n.len = nbytes;
            n.next = table.hashes[h];
            table.hashes[h] = n;
            table.nc = nc + nbytes;
            if (nbytes == 0) table.nc++;
        }
        return n;
    }

    /** Create a name from the characters in string s.
     */
    public static Name fromString(Table table, String s) {
        char[] cs = s.toCharArray();
        return fromChars(table, cs, 0, cs.length);
    }

    /** Create a name from the characters in char sequence s.
     */
    public static Name fromString(Table table, CharSequence s) {
        return fromString(table, s.toString());
    }

    /** Return the Utf8 representation of this name.
     */
    public byte[] toUtf() {
        byte[] bs = new byte[len];
        System.arraycopy(table.names, index, bs, 0, len);
        return bs;
    }

    /** Return the string representation of this name.
     */
    @Override
    public String toString() {
        return Conversion.utf2string(table.names, index, len);
    }

    /** Copy all bytes of this name to buffer cs, starting at start.
     */
    public void getBytes(byte cs[], int start) {
        System.arraycopy(table.names, index, cs, start, len);
    }

    /** Return the hash value of this name.
     */
    @Override
    public int hashCode() {
        return index;
    }

    /** Is this name equal to other?
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof Name)
            return
                table == ((Name)other).table && index == ((Name)other).index;
        else return false;
    }

    /** Compare this name to other name, yielding -1 if smaller, 0 if equal,
     *  1 if greater.
     */
    public boolean less(Name that) {
        int i = 0;
        while (i < len && i < that.len) {
            byte thisb = table.names[index + i];
            byte thatb = that.table.names[that.index + i];
            if (thisb < thatb) return true;
            else if (thisb > thatb) return false;
            else i++;
        }
        return len < that.len;
    }

    /** Returns the length of this name.
     */
    @Override
    public int length() {
        return toString().length();
    }

    /** Returns i'th byte of this name.
     */
    public byte byteAt(int i) {
        return table.names[index + i];
    }

    /** Returns first occurrence of byte b in this name, len if not found.
     */
    public int indexOf(byte b) {
        byte[] names = table.names;
        int i = 0;
        while (i < len && names[index + i] != b) i++;
        return i;
    }

    /** Returns last occurrence of byte b in this name, -1 if not found.
     */
    public int lastIndexOf(byte b) {
        byte[] names = table.names;
        int i = len - 1;
        while (i >= 0 && names[index + i] != b) i--;
        return i;
    }

    /** Does this name start with prefix?
     */
    public boolean startsWith(Name prefix) {
        int i = 0;
        while (i < prefix.len &&
               i < len &&
               table.names[index + i] == prefix.table.names[prefix.index + i])
            i++;
        return i == prefix.len;
    }

    /** Does this name end with suffix?
     */
    public boolean endsWith(Name suffix) {
        int i = len - 1;
        int j = suffix.len - 1;
        while (j >= 0 && i >= 0 &&
               table.names[index + i] == suffix.table.names[suffix.index + j]) {
            i--; j--;
        }
        return j < 0;
    }

    /** Returns the sub-name starting at position start, up to and
     *  excluding position end.
     */
    public Name subName(int start, int end) {
        if (end < start) end = start;
        return fromUtf(table, table.names, index + start, end - start);
    }

    /** Replace all `from' bytes in this name with `to' bytes.
     */
    public Name replace(byte from, byte to) {
        byte[] names = table.names;
        int i = 0;
        while (i < len) {
            if (names[index + i] == from) {
                byte[] bs = new byte[len];
                System.arraycopy(names, index, bs, 0, i);
                bs[i] = to;
                i++;
                while (i < len) {
                    byte b = names[index + i];
                    bs[i] = b == from ? to : b;
                    i++;
                }
                return fromUtf(table, bs, 0, len);
            }
            i++;
        }
        return this;
    }

    /** Return the concatenation of this name and name `n'.
     */
    public Name append(Name n) {
        byte[] bs = new byte[len + n.len];
        getBytes(bs, 0);
        n.getBytes(bs, len);
        return fromUtf(table, bs, 0, bs.length);
    }

    /** Return the concatenation of this name, the given ASCII
     *  character, and name `n'.
     */
    public Name append(char c, Name n) {
        byte[] bs = new byte[len + n.len + 1];
        getBytes(bs, 0);
        bs[len] = (byte)c;
        n.getBytes(bs, len+1);
        return fromUtf(table, bs, 0, bs.length);
    }

    /** An arbitrary but consistent complete order among all Names.
     */
    public int compareTo(Name other) {
        return other.index - index;
    }

    /** Return the concatenation of all names in the array `ns'.
     */
    public static Name concat(Table table, Name ns[]) {
        int len = 0;
        for (int i = 0; i < ns.length; i++)
            len = len + ns[i].len;
        byte[] bs = new byte[len];
        len = 0;
        for (int i = 0; i < ns.length; i++) {
            ns[i].getBytes(bs, len);
            len = len + ns[i].len;
        }
        return fromUtf(table, bs, 0, len);
    }

    @Override
    public char charAt(int index) {
        return toString().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    @Override
    public boolean contentEquals(CharSequence cs) {
        return this.toString().equals(cs.toString());
    }

    public static class Table {
        /** The hash table for names.
         */
        private Name[] hashes;

        /** The array holding all encountered names.
         */
        public byte[] names;

        /** The mask to be used for hashing
         */
        private int hashMask;

        /** The number of filled bytes in `names'.
         */
        private int nc = 0;

        /** Allocator
         *  @param hashSize the (constant) size to be used for the hash table
         *                  needs to be a power of two.
         *  @param nameSize the initial size of the name table.
         */
        public Table(int hashSize, int nameSize) {
            hashMask = hashSize - 1;
            hashes = new Name[hashSize];
            names = new byte[nameSize];
        }

        public Table() {
            this(0x8000, 0x20000);
        }

        /** Create a name from the bytes in cs[start..start+len-1].
         *  Assume that bytes are in utf8 format.
         */
        public Name fromUtf(byte cs[], int start, int len) {
            return Name.fromUtf(this, cs, start, len);
        }

        /** Create a name from the bytes in array cs.
         *  Assume that bytes are in utf8 format.
         */
        public Name fromUtf(byte cs[]) {
            return Name.fromUtf(this, cs, 0, cs.length);
        }

        /** Create a name from the characters in cs[start..start+len-1].
         */
        public Name fromChars(char[] cs, int start, int len) {
            return Name.fromChars(this, cs, start, len);
        }

        /** Create a name from the characters in string s.
         */
        public Name fromString(CharSequence s) {
            return Name.fromString(this, s);
        }
    }

    public boolean isEmpty() {
        return len == 0;
    }
}