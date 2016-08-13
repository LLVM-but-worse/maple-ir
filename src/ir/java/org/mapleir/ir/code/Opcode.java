package org.mapleir.ir.code;

public interface Opcode {

	int LOCAL_STORE =  1;
	int ARRAY_STORE =  2;
	int FIELD_STORE =  3;
	int PHI_STORE   =  4;
	
	int LOCAL_LOAD  = 11;
	int ARRAY_LOAD  = 12;
	int FIELD_LOAD  = 13;
	int CONST_LOAD  = 14;
	
	int INVOKE      = 21;
	int POP         = 22;
	int RETURN      = 23;
	
	int ARITHMETIC  = 31;
	int NEGATE      = 32;
	
	int COND_JUMP   = 41;
	int UNCOND_JUMP = 42;
	int SWITCH_JUMP = 43;
	
	int UNINIT_OBJ  = 51;
	int INIT_OBJ    = 52;
	int NEW_ARRAY   = 53;
	
	int ARRAY_LEN   = 61;
	int CAST        = 62;
	int INSTANCEOF  = 63;
	int COMPARE     = 64;
	int CATCH       = 65;
	int THROW       = 66;
	int MONITOR     = 67;

	int PHI         = 91;
}