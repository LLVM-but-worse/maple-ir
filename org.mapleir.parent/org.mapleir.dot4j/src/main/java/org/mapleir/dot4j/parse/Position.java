package org.mapleir.dot4j.parse;

class Position {
    final String name;
    int line;
    int col;

    public Position(String name) {
        this.name = name;
        line = col = 1;
    }

    public void newLine() {
        col = 1;
        line++;
    }

    public void newChar() {
        col++;
    }

    @Override
    public String toString() {
        return name + ":" + line + ":" + col;
    }
}
