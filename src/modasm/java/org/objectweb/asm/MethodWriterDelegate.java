
package org.objectweb.asm;

public abstract class MethodWriterDelegate {

	protected ClassWriter cw;

	protected int access;

	protected int name;

	protected int desc;

	protected String descriptor;

	protected String signature;

	protected int classReaderOffset;

	protected int classReaderLength;

	protected int exceptionCount;

	protected int[] exceptions;

	protected ByteVector annd;

	protected AnnotationWriter anns;

	protected AnnotationWriter ianns;

	protected AnnotationWriter[] panns;

	protected AnnotationWriter[] ipanns;

	protected int synthetics;

	protected Attribute attrs;

	protected ByteVector code;

	protected int maxStack;

	protected int maxLocals;

	protected int currentLocals;

	protected int frameCount;

	protected ByteVector stackMap;

	protected int previousFrameOffset;

	protected int[] previousFrame;

//	protected int frameIndex;

	protected int[] frame;

	protected int handlerCount;

	protected Handler firstHandler;

	protected Handler lastHandler;

	protected int localVarCount;

	protected ByteVector localVar;

	protected int localVarTypeCount;

	protected ByteVector localVarType;

	protected int lineNumberCount;

	protected ByteVector lineNumber;

	protected Attribute cattrs;

	protected boolean resize;

	protected int subroutines;

	protected Label labels;

	protected int maxStackSize;

	protected ByteVector pool;
	protected int poolSize;

	protected int version;

	public abstract void newMethod();

	public abstract void visitEnd();

	public abstract int getSize();

	public abstract void put(ByteVector out);

	public abstract void noteTooLargeOffset(Label label, int reference);

	public abstract void noteTooLargeStackMapDelta(int offset, int delta);

	public abstract void noteLocalVariable(String name, String desc, String signature, Label start, Label end,
			int index);

	public abstract void noteLineNumber(int line, Label start);
}
