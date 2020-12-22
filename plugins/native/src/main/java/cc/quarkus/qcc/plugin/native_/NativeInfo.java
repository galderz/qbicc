package cc.quarkus.qcc.plugin.native_;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import cc.quarkus.qcc.context.AttachmentKey;
import cc.quarkus.qcc.context.CompilationContext;
import cc.quarkus.qcc.driver.Driver;
import cc.quarkus.qcc.machine.probe.CProbe;
import cc.quarkus.qcc.machine.probe.Qualifier;
import cc.quarkus.qcc.type.CompoundType;
import cc.quarkus.qcc.type.TypeSystem;
import cc.quarkus.qcc.type.ValueType;
import cc.quarkus.qcc.type.annotation.Annotation;
import cc.quarkus.qcc.type.annotation.StringAnnotationValue;
import cc.quarkus.qcc.type.definition.ClassContext;
import cc.quarkus.qcc.type.definition.DefinedTypeDefinition;
import cc.quarkus.qcc.type.definition.ValidatedTypeDefinition;
import cc.quarkus.qcc.type.definition.element.MethodElement;
import cc.quarkus.qcc.type.definition.element.NestedClassElement;
import cc.quarkus.qcc.type.descriptor.ClassTypeDescriptor;
import cc.quarkus.qcc.type.descriptor.MethodDescriptor;
import cc.quarkus.qcc.type.descriptor.TypeDescriptor;

/**
 *
 */
final class NativeInfo {
    static final AttachmentKey<NativeInfo> KEY = new AttachmentKey<>();

    private final CompilationContext ctxt;

    final ClassTypeDescriptor cNativeDesc;
    final ClassTypeDescriptor ptrDesc;
    final ClassTypeDescriptor wordDesc;
    final ClassTypeDescriptor cObjectDesc;

    final Map<TypeDescriptor, Map<String, Map<MethodDescriptor, NativeFunctionInfo>>> nativeFunctions = new ConcurrentHashMap<>();
    final Map<DefinedTypeDefinition, AtomicReference<ValueType>> nativeTypes = new ConcurrentHashMap<>();
    final Map<DefinedTypeDefinition, MethodElement> functionalInterfaceMethods = new ConcurrentHashMap<>();

    private NativeInfo(final CompilationContext ctxt) {
        this.ctxt = ctxt;
        cNativeDesc = ClassTypeDescriptor.parseClassConstant(ctxt.getBootstrapClassContext(), ByteBuffer.wrap(Native.C_NATIVE_INT_NAME.getBytes(StandardCharsets.UTF_8)));
        ptrDesc = ClassTypeDescriptor.parseClassConstant(ctxt.getBootstrapClassContext(), ByteBuffer.wrap(Native.PTR_INT_NAME.getBytes(StandardCharsets.UTF_8)));
        wordDesc = ClassTypeDescriptor.parseClassConstant(ctxt.getBootstrapClassContext(), ByteBuffer.wrap(Native.WORD_INT_NAME.getBytes(StandardCharsets.UTF_8)));
        cObjectDesc = ClassTypeDescriptor.parseClassConstant(ctxt.getBootstrapClassContext(), ByteBuffer.wrap(Native.OBJECT_INT_NAME.getBytes(StandardCharsets.UTF_8)));
    }

    static NativeInfo get(final CompilationContext ctxt) {
        NativeInfo nativeInfo = ctxt.getAttachment(KEY);
        if (nativeInfo == null) {
            NativeInfo appearing = ctxt.putAttachmentIfAbsent(KEY, nativeInfo = new NativeInfo(ctxt));
            if (appearing != null) {
                nativeInfo = appearing;
            }
        }
        return nativeInfo;
    }

    ValueType resolveNativeType(final DefinedTypeDefinition definedType) {
        AtomicReference<ValueType> ref = nativeTypes.get(definedType);
        if (ref == null) {
            return null;
        }
        ClassContext classContext = definedType.getContext();
        CompilationContext ctxt = classContext.getCompilationContext();
        ValueType resolved = ref.get();
        if (resolved == null) {
            synchronized (ref) {
                resolved = ref.get();
                if (resolved == null) {
                    CProbe.Builder pb = CProbe.builder();
                    String simpleName = null;
                    Qualifier q = Qualifier.NONE;
                    for (Annotation annotation : definedType.getVisibleAnnotations()) {
                        ClassTypeDescriptor annDesc = annotation.getDescriptor();
                        if (ProbeUtils.processCommonAnnotation(pb, annotation)) {
                            continue;
                        }
                        if (annDesc.getPackageName().equals(Native.NATIVE_PKG)) {
                            if (annDesc.getClassName().equals(Native.ANN_NAME)) {
                                simpleName = ((StringAnnotationValue) annotation.getValue("value")).getString();
                            }
                        }
                        // todo: lib (add to native info)
                    }
                    NestedClassElement enclosing = definedType.validate().getEnclosingNestedClass();
                    while (enclosing != null) {
                        DefinedTypeDefinition enclosingType = enclosing.getEnclosingType();
                        for (Annotation annotation : enclosingType.getVisibleAnnotations()) {
                            ProbeUtils.processCommonAnnotation(pb, annotation);
                        }
                        enclosing = enclosingType.validate().getEnclosingNestedClass();
                    }
                    if (simpleName == null) {
                        String fullName = definedType.getInternalName();
                        int idx = fullName.lastIndexOf('/');
                        simpleName = idx == -1 ? fullName : fullName.substring(idx + 1);
                        idx = simpleName.lastIndexOf('$');
                        simpleName = idx == -1 ? simpleName : simpleName.substring(idx + 1);
                        if (simpleName.startsWith("struct_")) {
                            q = Qualifier.STRUCT;
                            simpleName = simpleName.substring(7);
                        } else if (simpleName.startsWith("union_")) {
                            q = Qualifier.UNION;
                            simpleName = simpleName.substring(6);
                        }
                    }
                    // begin the real work
                    ValidatedTypeDefinition vt = definedType.validate();
                    NativeInfo nativeInfo = ctxt.getAttachment(NativeInfo.KEY);
                    int fc = vt.getFieldCount();
                    TypeSystem ts = ctxt.getTypeSystem();
                    CProbe.Type.Builder tb = CProbe.Type.builder();
                    tb.setName(simpleName);
                    tb.setQualifier(q);
                    for (int i = 0; i < fc; i ++) {
                        ValueType type = vt.getField(i).getType(classContext, List.of(/*todo*/));
                        // compound type
                        tb.addMember(vt.getField(i).getName());
                    }
                    CProbe.Type probeType = tb.build();
                    pb.probeType(probeType);
                    CProbe probe = pb.build();
                    CompoundType.Tag tag = q == Qualifier.NONE ? CompoundType.Tag.NONE : q == Qualifier.STRUCT ? CompoundType.Tag.STRUCT : CompoundType.Tag.UNION;
                    try {
                        CProbe.Result result = probe.run(ctxt.getAttachment(Driver.C_TOOL_CHAIN_KEY), ctxt.getAttachment(Driver.OBJ_PROVIDER_TOOL_KEY), ctxt);
                        if (result != null) {
                            CProbe.Type.Info typeInfo = result.getTypeInfo(probeType);
                            long size = typeInfo.getSize();
                            if (typeInfo.isFloating()) {
                                if (size == 4) {
                                    resolved = ts.getFloat32Type();
                                } else if (size == 8) {
                                    resolved = ts.getFloat64Type();
                                } else {
                                    resolved = ts.getCompoundType(tag, simpleName, size, (int) typeInfo.getAlign());
                                }
                            } else if (typeInfo.isSigned()) {
                                if (size == 1) {
                                    resolved = ts.getSignedInteger8Type();
                                } else if (size == 2) {
                                    resolved = ts.getSignedInteger16Type();
                                } else if (size == 4) {
                                    resolved = ts.getSignedInteger32Type();
                                } else if (size == 8) {
                                    resolved = ts.getSignedInteger64Type();
                                } else {
                                    resolved = ts.getCompoundType(tag, simpleName, size, (int) typeInfo.getAlign());
                                }
                            } else if (typeInfo.isUnsigned()) {
                                if (size == 1) {
                                    resolved = ts.getUnsignedInteger8Type();
                                } else if (size == 2) {
                                    resolved = ts.getUnsignedInteger16Type();
                                } else if (size == 4) {
                                    resolved = ts.getUnsignedInteger32Type();
                                } else if (size == 8) {
                                    resolved = ts.getUnsignedInteger64Type();
                                } else {
                                    resolved = ts.getCompoundType(tag, simpleName, size, (int) typeInfo.getAlign());
                                }
                            } else {
                                CompoundType.Member[] members = new CompoundType.Member[fc];
                                for (int i = 0; i < fc; i ++) {
                                    ValueType type = vt.getField(i).getType(classContext, List.of(/*todo*/));
                                    // compound type
                                    String name = vt.getField(i).getName();
                                    CProbe.Type.Info member = result.getTypeInfoOfMember(probeType, name);
                                    members[i] = ts.getCompoundTypeMember(name, type, (int) member.getOffset(), 1);
                                }
                                resolved = ts.getCompoundType(tag, simpleName, size, (int) typeInfo.getAlign(), members);
                            }
                        }
                        ref.set(resolved);
                    } catch (IOException e) {
                        ctxt.error(e, "Failed to define native type " + simpleName);
                        return ts.getPoisonType();
                    }
                }
            }
        }
        return resolved;
    }

    public ValueType getTypeOfFunctionalInterface(final DefinedTypeDefinition definedType) {
        MethodElement method = getFunctionalInterfaceMethod(definedType);
        if (method == null) {
            return ctxt.getTypeSystem().getFunctionType(ctxt.getTypeSystem().getVoidType());
        }
        return method.getType(definedType.getContext(), List.of(/* todo */));
    }

    public MethodElement getFunctionalInterfaceMethod(final DefinedTypeDefinition definedType) {
        MethodElement element = functionalInterfaceMethods.get(definedType);
        if (element != null) {
            return element;
        }
        try {
            element = computeFunctionalInterfaceMethod(definedType.validate(), new HashSet<>(), null);
        } catch (IllegalArgumentException ignored) {
        }
        if (element != null) {
            MethodElement appearing = functionalInterfaceMethods.putIfAbsent(definedType, element);
            if (appearing != null) {
                return appearing;
            }
        } else {
            ctxt.error("Interface \"%s\" is not a functional interface", definedType.getInternalName());
        }
        return element;
    }

    private MethodElement computeFunctionalInterfaceMethod(final ValidatedTypeDefinition type, final HashSet<ValidatedTypeDefinition> visited, MethodElement found) {
        if (visited.add(type)) {
            int methodCount = type.getMethodCount();
            for (int i = 0; i < methodCount; i ++) {
                MethodElement method = type.getMethod(i);
                if (method.isAbstract() && method.isPublic()) {
                    if (found == null) {
                        found = method;
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
            }
            int intCnt = type.getInterfaceCount();
            for (int i = 0; i < intCnt; i ++) {
                found = computeFunctionalInterfaceMethod(type.getInterface(i), visited, found);
            }
        }
        return found;
    }

    public NativeFunctionInfo getFunctionInfo(final TypeDescriptor owner, final String name, final MethodDescriptor descriptor) {
        return nativeFunctions.getOrDefault(owner, Map.of()).getOrDefault(name, Map.of()).get(descriptor);
    }

    public void registerFunctionInfo(final TypeDescriptor owner, final String name, final MethodDescriptor descriptor, NativeFunctionInfo info) {
        nativeFunctions.computeIfAbsent(owner, NativeInfo::newMap).computeIfAbsent(name, NativeInfo::newMap).put(descriptor, info);
    }

    private static <K, V> Map<K, V> newMap(final Object key) {
        return new ConcurrentHashMap<>();
    }
}