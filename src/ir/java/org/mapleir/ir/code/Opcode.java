package org.mapleir.ir.code;

public interface Opcode {

	int CLASS_STORE    = 0x100;
	int LOCAL_STORE    = 0x101;
	int ARRAY_STORE    = 0x102;
	int FIELD_STORE    = 0x103;
	int PHI_STORE      = 0x104;
	
	int CLASS_LOAD     = 0x200;
	int LOCAL_LOAD     = 0x201;
	int ARRAY_LOAD     = 0x202;
	int FIELD_LOAD     = 0x203;
	int CONST_LOAD     = 0x204;
	
	int INVOKE         = 0x301;
	int DYNAMIC_INVOKE = 0x302;
	int POP            = 0x303;
	int RETURN         = 0x304;
	
	int ARITHMETIC     = 0x401;
	int NEGATE         = 0x402;
	
	int CLASS_JUMP     = 0x500;
	int COND_JUMP      = 0x501;
	int UNCOND_JUMP    = 0x502;
	int SWITCH_JUMP    = 0x503;
	
	int CLASS_OBJ      = 0x600;
	int UNINIT_OBJ     = 0x601;
	int INIT_OBJ       = 0x602;
	int NEW_ARRAY      = 0x603;
	
	int ARRAY_LEN      = 0x701;
	int CAST           = 0x702;
	int INSTANCEOF     = 0x703;
	int COMPARE        = 0x704;
	int CATCH          = 0x705;
	int THROW          = 0x706;
	int MONITOR        = 0x707;

	int CLASS_PHI      = 0x900;
	int PHI            = 0x901;
	int EPHI           = 0x902;

	int CLASS_RESERVED = 0x1000; // reserved for inner classes
}
