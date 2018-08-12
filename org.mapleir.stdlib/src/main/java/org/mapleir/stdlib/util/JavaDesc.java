package org.mapleir.stdlib.util;

public class JavaDesc  {
    public final String owner, name, desc;
    public final DescType descType; // FIELD or METHOD -- METHOD=argument flow or return value flow
    public final Object extraData;

    public JavaDesc(String owner, String name, String desc, DescType descType) {
        this(owner, name, desc, descType, null);
        assert (descType == DescType.FIELD);
    }

    public JavaDesc(String owner, String name, String desc, DescType descType, Object extraData) {
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.descType = descType;
        this.extraData = extraData;

        if (descType == DescType.CLASS)
            assert(name.isEmpty() && desc.isEmpty());
    }

    @Override
    public String toString() {
        return "(" + descType + ")" + owner + "#" + name + desc + (extraData != null ? "[" + extraData + "]" : "");
    }

    public boolean matches(String ownerRegex, String nameRegex, String descRegex, JavaDesc.DescType type) {
        if (type == DescType.CLASS)
            return this.owner.matches(ownerRegex);
        else
            return descType == type && this.owner.matches(ownerRegex) && this.name.matches(nameRegex) && this.desc.matches(descRegex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JavaDesc javaDesc = (JavaDesc) o;

        if (owner != null ? !owner.equals(javaDesc.owner) : javaDesc.owner != null) return false;
        if (name != null ? !name.equals(javaDesc.name) : javaDesc.name != null) return false;
        if (desc != null ? !desc.equals(javaDesc.desc) : javaDesc.desc != null) return false;
        if (descType != null ? !descType.equals(javaDesc.descType) : javaDesc.descType != null) return false;
        return extraData != null ? extraData.equals(javaDesc.extraData) : javaDesc.extraData == null;
    }

    @Override
    public int hashCode() {
        int result = owner != null ? owner.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (desc != null ? desc.hashCode() : 0);
        result = 31 * result + (descType != null ? descType.hashCode() : 0);
        result = 31 * result + (extraData != null ? extraData.hashCode() : 0);
        return result;
    }

    public enum DescType {
        FIELD,
        METHOD, // flow into method call args or out of method via return value
        CLASS,
    }
}
