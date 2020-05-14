package cc.quarkus.qcc.type.definition;

import cc.quarkus.qcc.type.ObjectReference;
import cc.quarkus.qcc.type.QType;
import cc.quarkus.qcc.type.universe.Universe;
import cc.quarkus.qcc.type.descriptor.TypeDescriptor;
import org.objectweb.asm.tree.FieldNode;

public class FieldDefinitionNode<V extends QType> extends FieldNode implements FieldDefinition<V> {

    public FieldDefinitionNode(TypeDefinition typeDefinition,
                               TypeDescriptor<V> type,
                               final int access,
                               final String name,
                               final String descriptor,
                               final String signature,
                               final Object value) {
        super(Universe.ASM_VERSION, access, name, descriptor, signature, value);
        this.typeDefinition = typeDefinition;
        this.type = type;
        this.qvalue = QType.of(value);
    }

    @Override
    public TypeDefinition getTypeDefinition() {
        return this.typeDefinition;
    }

    @Override
    public TypeDescriptor<V> getTypeDescriptor() {
        return this.type;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public V get(ObjectReference objRef) {
        return this.typeDefinition.getField(this, objRef);
    }

    @Override
    public void put(ObjectReference objRef, V val) {
        this.typeDefinition.putField(this, objRef, val);
    }

    @Override
    public String toString() {
        return this.typeDefinition.getName() + "::" + getName();
    }

    private final TypeDefinition typeDefinition;
    private final TypeDescriptor<V> type;
    public final QType qvalue;
}