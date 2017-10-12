package org.mapleir.ir.antlr;

import java.util.HashMap;
import java.util.Map;

public class MyListener extends mapleirBaseListener {

    private Map<String, Float> variables;
    
    public MyListener() {
        variables = new HashMap<>();
    }
    
//    @Override
//    public void exitAssign(AssignContext ctx) {
//        // This method is called when the parser has finished
//        // parsing the assign statement
//        
//        // Get variable name
//        String variableName = ctx.Identifier(0).getText();
//        
//        // Get value from variable or number
//        String value = ctx.Identifier().size() > 1 ? ctx.Identifier(1).getText() 
//                : ctx.Number().getText();
//        
//        // Add variable to map		
//        if(ctx.Identifier().size() > 1)
//            variables.put(variableName, variables.get(value));
//        else
//            variables.put(variableName, Float.parseFloat(value));
//    }
//    
//    @Override
//    public void exitAdd(AddContext ctx) {
//        // This method is called when the parser has finished
//        // parsing the add statement
//        
//        String variableName = ctx.Identifier().size() > 1 ? ctx.Identifier(1).getText() 
//                : ctx.Identifier(0).getText();
//        float value = ctx.Identifier().size() > 1 ? variables.get(ctx.Identifier(0).getText()) 
//                : Float.parseFloat(ctx.Number().getText());
//        
//        variables.put(variableName, variables.get(variableName) + value);
//    }
//    
//    @Override
//    public void exitPrint(PrintContext ctx) {
//        // This method is called when the parser has finished
//        // parsing the print statement
//        
//        String output = ctx.Identifier() == null ? ctx.Number().getText() 
//                : variables.get(ctx.Identifier().getText()).toString();
//        System.out.println(output);
//    }
}