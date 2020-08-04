package cc.quarkus.qcc.interpreter;

interface FieldContainer {
    FieldSet getFieldSet();

    JavaObject getObjectFieldPlain(String name);

    JavaObject getObjectFieldVolatile(String name);

    JavaObject getObjectFieldAcquire(String name);

    long getLongFieldPlain(String name);

    long getLongFieldVolatile(String name);

    long getLongFieldAcquire(String name);

    int getIntFieldPlain(String name);

    int getIntFieldVolatile(String name);

    int getIntFieldAcquire(String name);

    void setFieldPlain(String name, JavaObject value);

    void setFieldVolatile(String name, JavaObject value);

    void setFieldRelease(String name, JavaObject value);

    void setFieldPlain(String name, long value);

    void setFieldVolatile(String name, long value);

    void setFieldRelease(String name, long value);

    void setFieldPlain(String name, int value);

    void setFieldVolatile(String name, int value);

    void setFieldRelease(String name, int value);
}
