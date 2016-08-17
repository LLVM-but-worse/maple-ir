package org.mapleir.ir.code;

public interface Opcode {

	int CLASS_STORE =  0x0;
	int LOCAL_STORE =  0x1;
	int ARRAY_STORE =  0x2;
	int FIELD_STORE =  0x3;
	int PHI_STORE   =  0x4;
	
	int CLASS_LOAD  = 0x10;
	int LOCAL_LOAD  = 0x11;
	int ARRAY_LOAD  = 0x12;
	int FIELD_LOAD  = 0x13;
	int CONST_LOAD  = 0x14;
	
	int INVOKE      = 0x21;
	int POP         = 0x22;
	int RETURN      = 0x23;
	
	int ARITHMETIC  = 0x31;
	int NEGATE      = 0x32;
	
	int CLASS_JUMP  = 0x40;
	int COND_JUMP   = 0x41;
	int UNCOND_JUMP = 0x42;
	int SWITCH_JUMP = 0x43;
	
	int CLASS_OBJ   = 0x50;
	int UNINIT_OBJ  = 0x51;
	int INIT_OBJ    = 0x52;
	int NEW_ARRAY   = 0x53;
	
	int ARRAY_LEN   = 0x61;
	int CAST        = 0x62;
	int INSTANCEOF  = 0x63;
	int COMPARE     = 0x64;
	int CATCH       = 0x65;
	int THROW       = 0x66;
	int MONITOR     = 0x67;

	int PHI         = 0x91;
}
