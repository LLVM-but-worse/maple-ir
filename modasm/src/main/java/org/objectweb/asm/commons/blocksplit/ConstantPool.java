/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.objectweb.asm.commons.blocksplit;

import org.objectweb.asm.*;

/**
 * Container for the constant pool.
 */
final public class ConstantPool {
    ByteVector pool;

    int[] bootstrapMethods;
    int bootstrapMethodCount;

    /**
     * Maximum length of the strings contained in the constant pool of the
     * class.
     */
    private int maxStringLength;

    private char[] utfDecodeBuffer;

    /**
     * Offsets of the CONSTANT_Class_info structures in the pools;
     * more precisely the offsets of the meat of those structures
     * after the tag.
     */
    int[] items;
    int poolSize;


    public ConstantPool(ByteVector pool, int poolSize, ByteVector bootstrapMethods, int bootstrapMethodCount) {
        // parse constant pool
        this.pool = pool;
        this.poolSize = poolSize;
        maxStringLength = 0;
        int n = poolSize;
        items = new int[n];
        byte[] b = pool.data;
        int max = 0;
        int index = 0;
        for (int i = 1; i < n; ++i) {
            items[i] = index + 1;
            int size;
            switch (b[index]) {
            case ClassWriter.FIELD:
            case ClassWriter.METH:
            case ClassWriter.IMETH:
            case ClassWriter.INT:
            case ClassWriter.FLOAT:
            case ClassWriter.NAME_TYPE:
            case ClassWriter.INDY:
                size = 5;
                break;
            case ClassWriter.LONG:
            case ClassWriter.DOUBLE:
                size = 9;
                ++i;
                break;
            case ClassWriter.UTF8:
                size = 3 + ByteArray.readUnsignedShort(b, index + 1);
                if (size > max) {
                    max = size;
                }
                break;
            case ClassWriter.HANDLE:
                size = 4;
                break;
                // case ClassWriter.CLASS:
                // case ClassWriter.STR:
                // case ClassWriter.MTYPE
            default:
                size = 3;
                break;
            }
            index += size;
        }
        maxStringLength = max;
        utfDecodeBuffer = new char[maxStringLength];
        
        // parse bootstrap methods
        this.bootstrapMethodCount = bootstrapMethodCount;
        if (bootstrapMethods != null) {
            this.bootstrapMethods = new int[bootstrapMethodCount];
            int x = 0;
            for (int j = 0; j < bootstrapMethodCount; j++) {
                this.bootstrapMethods[j] = x;
                x += 2 + ByteArray.readUnsignedShort(bootstrapMethods.data, x + 2) << 1;
            }
        }
    }

    public ConstantPool(ByteVector pool, int poolSize) {
        this(pool, poolSize, null, 0);
    }

    //
    // Constant pool
    //
    /**
     * Reads a class constant pool item in {@link #b b}. <i>This method is
     * intended for {@link Attribute} sub classes, and is normally not needed by
     * class generators or adapters.</i>
     *
     * @param index the index of a constant pool class item.
     * @return the String corresponding to the specified class item.
     */
    public String readClass(final int itemIndex) {
        // computes the start index of the CONSTANT_Class item in b
        // and reads the CONSTANT_Utf8 item designated by
        // the first two bytes of this CONSTANT_Class item
        int classOffset = items[itemIndex];
        return readUTF8Item(ByteArray.readUnsignedShort(pool.data, classOffset));
    }

    /**
     * Reads an UTF8 string constant pool item in {@link #b b}. <i>This method
     * is intended for {@link Attribute} sub classes, and is normally not needed
     * by class generators or adapters.</i>
     *
     * @param item of an UTF8 constant pool item.
     * @return the String corresponding to the specified UTF8 item.
     */
    public String readUTF8Item(int item) {
        int offset = items[item];
        return ByteArray.readUTF8(pool.data, offset + 2, ByteArray.readUnsignedShort(pool.data, offset), utfDecodeBuffer);
    }

    /**
     * Reads a numeric or string constant pool item in {@link #b b}. <i>This
     * method is intended for {@link Attribute} sub classes, and is normally not
     * needed by class generators or adapters.</i>
     *
     * @param item the index of a constant pool item.
     * @return the {@link Integer}, {@link Float}, {@link Long}, {@link Double},
     *         {@link String}, {@link Type} or {@link Handle} corresponding to
     *         the given constant pool item.
     */
    public Object readConst(final int item) {
        int index = items[item];
        byte[] b = pool.data;
        switch (b[index - 1]) {
            case ClassWriter.INT:
                return new Integer(ByteArray.readInt(b, index));
            case ClassWriter.FLOAT:
                return new Float(Float.intBitsToFloat(ByteArray.readInt(b, index)));
            case ClassWriter.LONG:
                return new Long(ByteArray.readLong(b, index));
            case ClassWriter.DOUBLE:
                return new Double(Double.longBitsToDouble(ByteArray.readLong(b, index)));
            case ClassWriter.CLASS:
                return Type.getObjectType(readUTF8Item(ByteArray.readUnsignedShort(b, index)));
            case ClassWriter.STR:
                return readUTF8Item(ByteArray.readUnsignedShort(b, index));
            case ClassWriter.MTYPE:
                return Type.getMethodType(readUTF8Item(ByteArray.readUnsignedShort(b, index)));

            //case ClassWriter.HANDLE_BASE + [1..9]:
            default: {
                int tag = ByteArray.readByte(b, index);
                int[] items = this.items;
                int cpIndex = items[ByteArray.readUnsignedShort(b, index + 1)];
                String owner = readClass(cpIndex);
                cpIndex = items[ByteArray.readUnsignedShort(b, cpIndex + 2)];
                String name = readUTF8Item(ByteArray.readUnsignedShort(b, cpIndex));
                String desc = readUTF8Item(ByteArray.readUnsignedShort(b, cpIndex + 2));
                return new Handle(tag, owner, name, desc);
            }
        }
    }

    /**
     * Symbolic reference to method or field.
     */
    class MemberSymRef {
        final String owner;
        final String name;
        final String desc;
        public MemberSymRef(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }
    }

    /**
     * Parse a symbolic reference to a member.
     *
     * @param item index of item in constant pool
     * @returns symbolic reference
     */
    public MemberSymRef parseMemberSymRef(int item) {
        int cpIndex = items[item];
        String iowner = readClass(ByteArray.readUnsignedShort(pool.data, cpIndex));
        cpIndex = items[ByteArray.readUnsignedShort(pool.data, cpIndex + 2)];
        String iname = readUTF8Item(ByteArray.readUnsignedShort(pool.data, cpIndex));
        String idesc = readUTF8Item(ByteArray.readUnsignedShort(pool.data, cpIndex + 2));
        return new MemberSymRef(iowner, iname, idesc);
    }

    /**
     * Symbolic reference to dynamic method.
     */
    class DynamicSymRef {
        final String name;
        final String desc;
        final int bsmIndex;
        public DynamicSymRef(String name, String desc, int bsmIndex) {
            this.name = name;
            this.desc = desc;
            this.bsmIndex = bsmIndex;
        }
    }

    /**
     * Parse a symbolic reference to a dynamic method.
     *
     * @param item index of item in constant pool
     * @returns symbolic reference
     */
    public DynamicSymRef parseDynamicSymRef(int item) {
        int cpIndex = items[item];
        int bsmIndex = bootstrapMethods[ByteArray.readUnsignedShort(pool.data, cpIndex)];
        cpIndex = items[ByteArray.readUnsignedShort(pool.data, cpIndex + 2)];
        String iname = readUTF8Item(ByteArray.readUnsignedShort(pool.data, cpIndex));
        String idesc = readUTF8Item(ByteArray.readUnsignedShort(pool.data, cpIndex + 2));
        return new DynamicSymRef(iname, idesc, bsmIndex);
    }


}