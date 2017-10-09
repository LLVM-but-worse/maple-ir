grammar GYOO;
program   : 'begin' statement+ 'end';
          
statement : assign | add | print ;

assign    : 'let' ID 'be' (NUMBER | ID) ;
print     : 'print' (NUMBER | ID) ;
add       : 'add' (NUMBER | ID) 'to' ID ;

ID     : [a-z]+ ;
NUMBER : [0-9]+ ;
WS     : [ \n\t\r]+ -> skip;