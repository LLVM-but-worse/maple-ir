package org.rsdeob;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.ir.expr.ArithmeticExpression.Operator;
import org.rsdeob.stdlib.ir.stat.ConditionalJumpStatement.ComparisonType;

import static org.objectweb.asm.Type.*;
import static org.rsdeob.stdlib.ir.expr.ArithmeticExpression.Operator.REM;
import static org.rsdeob.stdlib.ir.expr.ArithmeticExpression.Operator.XOR;
import static org.rsdeob.stdlib.ir.stat.ConditionalJumpStatement.ComparisonType.LE;

/**
 * Java used "have no useful dynamic casting"! It's super effective! ecx's blood pressure rose!
 */
@SuppressWarnings("Duplicates")
public class ExpressionEvaluatorAutogen {
	public static void main(String[] args) throws Exception {
		generateComparison();
	}

	private static void generateBitwise() {
		System.out.println("switch (operator) {");
		for (int ordinal = 5; ordinal <= XOR.ordinal(); ordinal++) {
			Operator operator = Operator.values()[ordinal];
			System.out.println("\tcase " + operator.name() + ":");
			for (int ordinalL = 1; ordinalL < 9; ordinalL++) {
				if (ordinalL == FLOAT)
					continue;
				if (ordinalL == DOUBLE)
					continue;
				Type typeL = new Type(ordinalL, null, 0, 0);
				String typeNameL = typeL.getClassName();
				System.out.println("\t\tif (lhsType == " + typeNameL.toUpperCase() + "_TYPE) {");
				for (int ordinalR = 1; ordinalR < 9; ordinalR++) {
					if (ordinalL == BOOLEAN && ordinalR != BOOLEAN)
						continue;
					if (ordinalL != BOOLEAN && ordinalR == BOOLEAN)
						continue;
					if (ordinalR == FLOAT)
						continue;
					if (ordinalR == DOUBLE)
						continue;
					Type typeR = new Type(ordinalR, null, 0, 0);
					String typeNameR = typeR.getClassName();
					System.out.println("\t\t\tif (rhsType == " + typeNameR.toUpperCase() + "_TYPE)");
					System.out.println("\t\t\t\tresult = (" + typeNameL + ") lhsValue " + operator.getSign() + " (" + typeNameR + ") rhsValue;");
				}
				System.out.println("\t\t}");
			}
			System.out.println("\t\tbreak;");
		}
		System.out.println("\tdefault:");
		System.out.println("\t\tthrow new IllegalArgumentException(\"Invalid bitwise operator \" + operator.name());");
		System.out.println("}");
	}

	private static void generateOther() {
		System.out.println("switch (operator) {");
		for (int ordinal = 0; ordinal <= REM.ordinal(); ordinal++) {
			Operator operator = Operator.values()[ordinal];
			System.out.println("\tcase " + operator.name() + ":");
			for (int ordinalL = 2; ordinalL < 9; ordinalL++) {
				Type typeL = new Type(ordinalL, null, 0, 0);
				String typeNameL = typeL.getClassName();
				System.out.println("\t\tif (lhsType == " + typeNameL.toUpperCase() + "_TYPE) {");
				for (int ordinalR = 2; ordinalR < 9; ordinalR++) {
					Type typeR = new Type(ordinalR, null, 0, 0);
					String typeNameR = typeR.getClassName();
					System.out.println("\t\t\tif (rhsType == " + typeNameR.toUpperCase() + "_TYPE)");
					System.out.println("\t\t\t\tresult = (" + typeNameL + ") lhsValue " + operator.getSign() + " (" + typeNameR + ") rhsValue;");
				}
				System.out.println("\t\t}");
			}
			System.out.println("\t\tbreak;");
		}
		System.out.println("\tdefault:");
		System.out.println("\t\tthrow new IllegalArgumentException(\"Invalid operator \" + operator.name());");
		System.out.println("}");
	}
	
	private static void generateComparison() {
		System.out.println("switch (cjs.getComparisonType()) {");
		for (int ordinal = 0; ordinal < LE.ordinal(); ordinal++) {
			ComparisonType type = ComparisonType.values()[ordinal];
			System.out.println("\tcase " + type.name() + ":");
			for (int ordinalL = 2; ordinalL < 9; ordinalL++) {
				Type typeL = new Type(ordinalL, null, 0, 0);
				String typeNameL = typeL.getClassName();
				System.out.println("\t\tif (lhsType == " + typeNameL.toUpperCase() + "_TYPE) {");
				for (int ordinalR = 2; ordinalR < 9; ordinalR++) {
					Type typeR = new Type(ordinalR, null, 0, 0);
					String typeNameR = typeR.getClassName();
					System.out.println("\t\t\tif (rhsType == " + typeNameR.toUpperCase() + "_TYPE)");
					System.out.println("\t\t\t\treturn (" + typeNameL + ") lhsValue " + type.getSign() + " (" + typeNameR + ") rhsValue;");
				}
				System.out.println("\t\t}");
			}
			System.out.println("\t\tbreak;");
		}
		System.out.println("\tdefault:");
		System.out.println("\t\tthrow new IllegalArgumentException(\"Invalid operator \" + operator.name());");
		System.out.println("}");
	}
}