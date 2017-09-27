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

import java.util.*;

public final class FrameData {
    Object[] frameLocal;
    Object[] frameStack;

    public FrameData(int nLocal, Object[] frameLocal, int nStack, Object[] frameStack) {
        this.frameLocal = Arrays.copyOf(frameLocal, nLocal);
        this.frameStack = Arrays.copyOf(frameStack, nStack);
    }
    
    public FrameData(Object[] frameLocal, Object[] frameStack) {
        this(frameLocal.length, frameLocal, frameStack.length, frameStack);
    }

    public void visitFrame(MethodVisitor mv) {
        mv.visitFrame(Opcodes.F_NEW, frameLocal.length, frameLocal, frameStack.length, frameStack);
    }

    /**
     * Return <code>true</code> if this frame has no uninitialized
     * elements, <code>false</code> otherwise,
     */
    public boolean isFullyDefined() {
        return isFrameFullyDefined(frameLocal, frameLocal.length)
            && isFrameFullyDefined(frameStack, frameStack.length);
    }

    public static boolean isFrameFullyDefined(Object[] elements, int size) {
        int i = 0;
        while (i < size) {
            if (!isElementFullyDefined(elements[i])) {
                return false;
            }
            ++i;
        }
        return true;
    }

    private static boolean isElementFullyDefined(Object el) {
        return (el != Opcodes.UNINITIALIZED_THIS)
            && !(el instanceof Label);
    }

    /**
     * @param methodDescriptor method descriptor of host method
     * @param isStatic says whether host method is static
     */
    public String getDescriptor(final String methodDescriptor, final boolean isStatic, BitSet localsRead, HashMap<Label, String> labelTypes) {
        StringBuilder b = new StringBuilder();

        b.append("(");
        int argsCount = 0;
        {
            // for non-static methods, this is the first local, and implicit
            int i = isStatic ? 0 : 1;
            while (i < frameLocal.length) {
                Object el = frameLocal[i];
                if ((localsRead == null) || localsRead.get(i)) {
                    appendFrameTypeDescriptor(b, el, labelTypes);
                    ++argsCount;
                }
                i += typeFrameSize(el);
            }
        }
        {
            int i = 0;
            while (i < frameStack.length) {
                Object el = frameStack[i];
                appendFrameTypeDescriptor(b, el, labelTypes);
                ++argsCount;
                i += typeFrameSize(el);
            }
        }

        if (argsCount > 255) {
            throw new RuntimeException("too many arguments for split method (" + argsCount + ")");
        }

        b.append(")");
        b.append(Type.getReturnType(methodDescriptor));
        return b.toString();
    }

    /**
     * Generate the code to pass the arguments before control transfer
     * to this block.
     */
    public void pushFrameArguments(MethodVisitor mv, boolean isStatic, BitSet localsRead) {
        /*
         * We'll want the locals first, so they get the same indices
         * in the target block.  Then we'll want the operands.
         * Unfortunately, there's no way to push the locals below the
         * operands (or whatever), so we'll need to copy the operands
         * into new local variables, and from there push them on top.
         */
        // FIXME: need to put the new stuff into frameLocal?
        {
            int i = 0, j = 0;
            while (i < frameStack.length) {
                Object el = frameStack[frameStack.length - i - 1];
                // storeValue will skip the TOPs, so going backwards is OK
                storeValue(mv, frameLocal.length + j, el);
                ++i;
                j += typeFrameSize(el);
            }
        }
        {
            if (!isStatic) {
                loadValue(mv, 0, frameLocal[0]); // this
            }
            int i = isStatic ? 0 : 1;
            while (i < frameLocal.length) {
                Object el = frameLocal[i];
                if ((localsRead == null) || localsRead.get(i)) {
                    loadValue(mv, i, el);
                }
                i += typeFrameSize(el);
            }
        }
        {
            int i = 0;
            // now the relative frame indices correspond to the original stack indices
            while (i < frameStack.length) {
                Object el = frameStack[i];
                loadValue(mv, frameLocal.length + frameStack.length - i - 1, el);
                i += typeFrameSize(el);
            }
        }
    }

    /**
     * Calculate the size of the code needed to push the current frame
     * in preparation for an invocation.
     */
    public int pushFrameArgumentsSize(boolean isStatic, BitSet localsRead) {
        int size = 0;
        {
            int i = 0;
            while (i < frameStack.length) {
                size += storeValueSize(frameLocal.length + i, frameStack[frameStack.length - i - 1]);
                ++i;
            }
        }
        {
            int i = 0;
            while (i < frameLocal.length) {
                if ((localsRead == null) || (!isStatic && (i == 0)) || localsRead.get(i)) {
                    size += loadValueSize(i, frameLocal[i]);
                }
                ++i;
            }
        }
        {
            int i = 0;
            while (i < frameStack.length) {
                size += loadValueSize(frameLocal.length + frameStack.length -i - 1, frameStack[i]);
                ++i;
            }
        }
        return size;
    }

    public static int pushFrameArgumentsMaxSize(int maxStack, int maxLocals) {
        int size = 0;
        size += valueLoadStoreMaxSize(maxLocals, maxLocals + maxStack) * 2;
        size += valueLoadStoreMaxSize(0, maxLocals);
        return size;
    }

    public static int pushFrameArgumentsSparseMaxSize(int maxStack, int maxLocals) {
        return pushFrameArgumentsMaxSize(maxStack, maxLocals);
    }

    private static void storeValue(MethodVisitor mv, int index, Object el) {
        if (el == Opcodes.TOP) {
            ; // nothing
        } else if (el == Opcodes.INTEGER) {
            mv.visitVarInsn(Opcodes.ISTORE, index);
        } else if (el == Opcodes.FLOAT) {
            mv.visitVarInsn(Opcodes.FSTORE, index);
        } else if (el == Opcodes.DOUBLE) {
            mv.visitVarInsn(Opcodes.DSTORE, index);
        } else if (el == Opcodes.LONG) {
            mv.visitVarInsn(Opcodes.LSTORE, index);
        } else if (el == Opcodes.NULL) {
            mv.visitVarInsn(Opcodes.ASTORE, index);
        } else if (el == Opcodes.UNINITIALIZED_THIS) {
            mv.visitVarInsn(Opcodes.ASTORE, index);
        } else if (el instanceof String) {
            mv.visitVarInsn(Opcodes.ASTORE, index);
        } else if (el instanceof Label) {
            mv.visitVarInsn(Opcodes.ASTORE, index);
        } else {
            throw new RuntimeException("unknown frame element");
        }
    }

    private static int storeValueSize(int index, Object el) {
        if (el == Opcodes.TOP) {
            return 0; // nothing
        } else {
            if (index < 4) {
                return 1;
            } else if (index >= 256) {
                return 6;
            } else {
                return 3;
            }
        }
    }

    private static void appendFrameTypeDescriptor(StringBuilder b, Object d, HashMap<Label, String> labelTypes) {
        if (d == Opcodes.INTEGER)
            b.append("I");
        else if (d == Opcodes.FLOAT)
            b.append("F");
        else if (d == Opcodes.LONG)
            b.append("J");
        else if (d == Opcodes.DOUBLE)
            b.append("D");
        else if (d instanceof String) {
            appendFrameReferenceTypeDescriptor(b, (String) d, 0);
            b.append(";");
        } else if (d instanceof Label) {
            String name = labelTypes.get(d);
            if (name == null) {
                throw new RuntimeException("label without associated type");
            }
            appendFrameReferenceTypeDescriptor(b, name, 0);
            b.append(";");
        } else if (d == Opcodes.TOP) {
            b.append("Ljava/lang/Object;"); /* loadValue pushes a NULL */
        } else {
            // #### UNINITIALIZED_THIS is missing
            throw new RuntimeException("can't handle this frame element");
        }
    }

    private static int typeFrameSize(Object el) {
        if ((el == Opcodes.LONG) || (el == Opcodes.DOUBLE)) {
            return 2;
        } else {
            return 1;
        }
    }

    private static void appendFrameReferenceTypeDescriptor(StringBuilder b, String name, int index) {
        // internal names and descriptors don't relate well
        while (name.charAt(index) == '[') {
            b.append("[");
            ++index;
        }
        b.append("L");
        b.append(name, index, name.length());
    }


    /**
     * In a split method, reconstruct the stack from the parameters.
     */
    public void reconstructFrame(MethodVisitor mv) {
        int localSize = frameLocal.length;
        int i = 0, size = frameStack.length;
        // the relative frame indices correspond to the original stack indices
        while (i < size) {
            Object el = frameStack[i];
            loadValue(mv, localSize + i, el);
            i += typeFrameSize(el);
        }
    }

    /**
     * Calculate code size needed to reconstruct the stack from the parameters.
     */
    public int reconstructFrameSize() {
        int codeSize = 0;
        int localSize = frameLocal.length;
        int i = 0, size = frameStack.length;
        while (i < size) {
            codeSize += loadValueSize(i + localSize, frameStack[i]);
            ++i;
        }
        return codeSize;
    }

    public static int reconstructFrameMaxSize(int maxStack, int maxLocals) {
        return valueLoadStoreMaxSize(maxLocals, maxStack + maxLocals);
    }

    /**
     * In a split method with a sparse frame, reconstruct frame and
     * stack from parameters.
     */
    public void reconstructFrameSparse(MethodVisitor mv, boolean isStatic, BitSet readLocals) {
        int frameCount = readLocals.cardinality();
        if (!isStatic && readLocals.get(0)) {
            --frameCount; // remove "this" from the locals transferred
        }
        int m = isStatic ? 0 : 1;
        int[] is = new int[frameCount];
        int[] js = new int[frameCount];
        int frameSize;
        {
            int j = 0;
            int i = m;
            int k = 0;
            while (i < frameLocal.length) {
                int size = typeFrameSize(frameLocal[i]); 
                if (readLocals.get(i)) {
                    is[k] = i;
                    js[k] = j;
                    ++k;
                    j += size;
                }
                i += size;
            }
            frameSize = j;
        }
        /*
         * We need to reconstruct the stack first because reconstructing
         * the locals may overwrite them.
         */
        {
            int i = 0, size = frameStack.length;
            // the relative frame indices correspond to the original stack indices
            while (i < size) {
                Object el = frameStack[i];
                loadValue(mv, frameSize + m + i, el);
                i += typeFrameSize(el);
            }
        }

        {
            int k = frameCount - 1;
            while (k >= 0) {
                int i = is[k];
                int j = js[k];
                Object el = frameLocal[i];
                loadValue(mv, j + m, el);
                storeValue(mv, i, el);
                --k;
            }
        }
    }

    /**
     * In a split method with a sparse frame, calculate code size
     * needed to reconstruct frame and stack from parameters.
     */
    public int reconstructFrameSparseSize(BitSet readLocals) {
        int codeSize = 0;
        {
            int i = 0, j = 0;
            while (i < frameLocal.length) {
                if (readLocals.get(i)) {
                    Object el = frameLocal[i];
                    codeSize += loadValueSize(j, el);
                    codeSize += storeValueSize(i, el);
                    ++j;
                }
                ++i;
            }
        }
        {
            int argsCount = readLocals.cardinality();
            int i = 0, size = frameStack.length;
            // the relative frame indices correspond to the original stack indices
            while (i < size) {
                codeSize += loadValueSize(argsCount + i, frameStack[i]);
                ++i;
            }
        }
        return codeSize;
    }

    public static int reconstructFrameSparseMaxSize(int maxStack, int maxLocals) {
        return valueLoadStoreMaxSize(0, maxLocals) * 2 // one for the loads, one for the stores
            + valueLoadStoreMaxSize(maxLocals, maxStack + maxLocals);
    }

    private static void loadValue(MethodVisitor mv, int index, Object el) {
        if (el == Opcodes.TOP) {
            mv.visitInsn(Opcodes.ACONST_NULL); /* must do *something* ... */
        } else if (el == Opcodes.INTEGER) {
            mv.visitVarInsn(Opcodes.ILOAD, index);
        } else if (el == Opcodes.FLOAT) {
            mv.visitVarInsn(Opcodes.FLOAD, index);
        } else if (el == Opcodes.DOUBLE) {
            mv.visitVarInsn(Opcodes.DLOAD, index);
        } else if (el == Opcodes.LONG) {
            mv.visitVarInsn(Opcodes.LLOAD, index);
        } else if (el == Opcodes.NULL) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else if (el == Opcodes.UNINITIALIZED_THIS) {
            mv.visitVarInsn(Opcodes.ALOAD, index);
        } else if (el instanceof String) {
            mv.visitVarInsn(Opcodes.ALOAD, index);
        } else if (el instanceof Label) {
            mv.visitVarInsn(Opcodes.ALOAD, index);
        } else {
            throw new RuntimeException("unknown frame element");
        }
    }

    private static int loadValueSize(int index, Object el) {
        if (el == Opcodes.TOP) {
            return 0; // nothing
        } else if ((el == Opcodes.NULL) || (el == Opcodes.UNINITIALIZED_THIS) || (el instanceof Label)) {
            return 1;
        } else {
            if (index < 4) {
                return 1;
            } else if (index >= 256) {
                return 6;
            } else {
                return 3;
            }
        }
    }

    /**
     * Calculate the maximum size of either storing or loading a bunch
     * of locals at indices.
     *
     * @param start inclusive
     * @param end exclusive
     */
    private static int valueLoadStoreMaxSize(int start, int end) {
        int size = 0;
        if (start < 4) {
            int m = Math.min(4, end);
            size += m - start;
            start = m;
        }
        if (start < 256) {
            int m = Math.min(256, end);
            size += (m - start) * 3;
            start = m;
        }
        size += (end - start) * 6;
        return size;
    }
    

}