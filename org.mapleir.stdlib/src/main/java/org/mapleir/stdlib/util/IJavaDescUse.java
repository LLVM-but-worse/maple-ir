package org.mapleir.stdlib.util;

public interface IJavaDescUse extends IHasJavaDesc {
    JavaDescUse.UseType getDataUseType();

    default JavaDescUse getDataUse() {
        return new JavaDescUse(getJavaDesc(), this, getDataUseType());
    }
}
