package org.mapleir.stdlib.util;

public interface IDataFlowElement extends IHasJavaDesc {
    DataflowUse.DataflowType getDataflowType();

    default DataflowUse getDataflow() {
        return new DataflowUse(getJavaDesc(), this, getDataflowType());
    }
}
