package org.qbicc.plugin.opt.ea;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.qbicc.graph.Call;
import org.qbicc.graph.CastValue;
import org.qbicc.graph.InstanceFieldOf;
import org.qbicc.graph.Load;
import org.qbicc.graph.LocalVariable;
import org.qbicc.graph.New;
import org.qbicc.graph.Node;
import org.qbicc.graph.ParameterValue;
import org.qbicc.graph.PhiValue;
import org.qbicc.graph.ReferenceHandle;
import org.qbicc.graph.StaticField;
import org.qbicc.graph.Value;
import org.qbicc.graph.ValueHandle;
import org.qbicc.type.definition.element.ExecutableElement;

import static java.util.stream.Collectors.groupingBy;

final class ConnectionGraph {
    // TODO Handle situations where a node has multiple points-to.

    //      Even if a reference is potentially assigned multiple New nodes (e.g. branches), the refs are currently different.
    /**
     * Points-to are edges from references to objects referenced.
     * The references are {@link ValueHandle} instances, e.g. {@link ReferenceHandle}, {@link StaticField}...etc.
     * Referenced objects are normally {@link New} instances, but they can also be {@link ParameterValue}, {@link Call} or phantom nodes.
     * <p>
     * Each method's connection graph tracks this during intra method analysis:
     * <p><ul>
     * <li>The points-to edges are used to transfer information from callee methods to caller methods.
     *    For example, imagine that a method {@code A} creates a new {@code Boo} and assigns it to variable {@code x}.
     *    Then, method {@code A} sends {@code x} as argument to method {@code B}.
     *    If method {@code B} assigns the parameter to a static variable, the parameter reference is marked as global escape.
     *    However, method {@code B} in itself doesn't know about the new {@code Boo} instance.
     * </li>
     * <li>Then, when a method analysis completes in inter-method analysis,
     *    for any references that are global escape, objects referenced also become global escape.
     *    Following the example in the previous point, the parameter reference has been marked as global escape.
     *    This gets mapped to the reference that points to the New instance, which in turn becomes global escape.
     *    This happens during inter method analysis because caller method escape value can switch to global escape,
     *    e.g. if a callee method assigns an argument to a static field.
     * </li>
     * </ul><p>
     */
    private final Map<Node, Node> pointsToEdges = new HashMap<>(); // solid (P) edges

    /**
     * A deferred edge from a node {@code p} to a node {@code q} means that {@code p} points to whatever {@code q} points to.
     * They model assignments that copy references from one to another during intra method analysis.
     * They're mostly used to link {@link ParameterValue} instances with their uses.
     * During inter method analysis these are bypassed recursively to establish points to edges.
     */
    private final Map<Node, Node> deferredEdges = new HashMap<>(); // dashed (D) edges
    private final Map<Node, Collection<InstanceFieldOf>> fieldEdges = new HashMap<>(); // solid (F) edges

    /**
     * Tracks escape value of graph nodes.
     * It includes not only {@link Value} nodes but also {@link ValueHandle} nodes.
     * The escape value of handles gets propagated eventually, using points-to edges, to {@code Value} instances.
     */
    private final Map<Node, EscapeValue> escapeValues = new HashMap<>();

    /**
     * This helps overcome the lack of direct link between {@link LocalVariable} and {@link New} nodes in the graph.
     */
    private final Map<ValueHandle, New> localNewNodes = new HashMap<>();
    private final ParameterArray parameters;
    final ExecutableElement element;

    ConnectionGraph(ExecutableElement element) {
        this.element = element;
        this.parameters = new ParameterArray(element.getSignature().getParameterTypes().size());
    }

    @Override
    public String toString() {
        return "ConnectionGraph{" +
            "name='" + element.toString() + '\'' +
            '}';
    }

    void addPointsToEdge(Node from, Node to) {
        addPointsToEdgeIfAbsent(from, to);
    }

    Node getPointsToEdge(Node from) {
        return pointsToEdges.get(from);
    }

    void addFieldEdge(Node node, InstanceFieldOf instanceField) {
        addFieldEdgeIfAbsent(node, instanceField);
    }

    Collection<InstanceFieldOf> getFieldEdges(Node node) {
        final Collection<InstanceFieldOf> fieldEdges = this.fieldEdges.get(node);
        return fieldEdges == null ? Collections.emptyList() : fieldEdges;
    }

    void addDeferredEdge(Node node, Node value) {
        addDeferredEdgeIfAbsent(node, value);
    }

    void setNoEscape(Node node) {
        setEscapeValue(node, EscapeValue.NO_ESCAPE);
    }

    void setArgEscape(Node node) {
        setEscapeValue(node, EscapeValue.ARG_ESCAPE);
    }

    void setGlobalEscape(Node node) {
        setEscapeValue(node, EscapeValue.GLOBAL_ESCAPE);
    }

    public boolean addParameter(ParameterValue param) {
        return parameters.addIfAbsent(param);
    }

    void trackLocalNew(LocalVariable localHandle, New new_) {
        localNewNodes.put(localHandle, new_);
    }

    void trackNew(New new_, EscapeValue escapeValue) {
        setEscapeValue(new_, escapeValue);
    }

    void trackParameters(List<ParameterValue> args) {
        // parameters.addAll(args);
    }

    void trackReturn(Value value) {
        if (value instanceof Load) {
            final Value localNew = localNewNodes.get(value.getValueHandle());
            if (localNew != null) {
                setEscapeValue(localNew, EscapeValue.ARG_ESCAPE);
                return;
            }
        }

        setEscapeValue(value, EscapeValue.ARG_ESCAPE);
    }

    void trackStoreStaticField(ValueHandle handle, Value value) {
        addPointsToEdgeIfAbsent(handle, value);
        setEscapeValue(handle, EscapeValue.GLOBAL_ESCAPE);
    }

    void trackStoreThisField(Value value) {
        setEscapeValue(value, EscapeValue.ARG_ESCAPE);
    }

    void trackThrowNew(New value) {
        // New allocations thrown assumed to escape as arguments
        // TODO Could it be possible to only mark as argument escaping those that escape the method?
        setEscapeValue(value, EscapeValue.ARG_ESCAPE);
    }

    void trackCast(CastValue cast) {
        addPointsToEdgeIfAbsent(cast, cast.getInput());
    }

    void fixEdgesField(New new_, ValueHandle newHandle, InstanceFieldOf instanceField) {
        addFieldEdgeIfAbsent(new_, instanceField);
        addPointsToEdgeIfAbsent(newHandle, new_);
    }

    void fixEdgesNew(ValueHandle newHandle, New new_) {
        addPointsToEdgeIfAbsent(newHandle, new_);
    }

    void fixEdgesParameterValue(ParameterValue from, InstanceFieldOf to) {
        addDeferredEdgeIfAbsent(from, to);
    }

    /**
     * Returns the escape value associated with the given node.
     * If the node is not found, its escape value is unknown.
     */
    EscapeValue getEscapeValue(Node node) {
        return EscapeValue.of(escapeValues.get(node));
    }

    /**
     * Returns the field nodes associated with the give node.
     * If the node is not found, an empty collection is returned.
     */
    Collection<InstanceFieldOf> getFields(Node node) {
         final Collection<InstanceFieldOf> fields = fieldEdges.get(node);
         return Objects.isNull(fields) ? Collections.emptyList() : fields;
    }

    Node getDeferredEdge(Node node) {
        return deferredEdges.get(node);
    }

//    void updateAtMethodEntry() {
//        // Set all parameters as arg escape
//        // TODO is this needed?
//        parameters.forEach(arg -> setEscapeValue(arg, EscapeValue.ARG_ESCAPE));
//    }

    void updateAfterInvokingMethod(Call callee, ConnectionGraph calleeCG) {
        // TODO this should really be removed, no method called that is not reachable should make it here
        if (callee.getArguments().size() > calleeCG.parameters.size())
            return;

        final List<Value> arguments = callee.getArguments();
        for (int i = 0; i < arguments.size(); i++) {
            final Value outsideArg = arguments.get(i);
            final ParameterValue insideArg = calleeCG.parameters.get(i);
            final Collection<Node> mapsToObj = new ArrayList<>();
            // First check parameter value and associated object outside the method
            updateCallerNode(insideArg, calleeCG, mapsToObj, outsideArg);
            // Then check anything pointed by parameter value
            updatePointsToCallerNodes(insideArg, List.of(outsideArg), calleeCG, mapsToObj);
        }
    }

    private void updatePointsToCallerNodes(Node calleeNode, Collection<Node> mapsToField, ConnectionGraph calleeCG, Collection<Node> mapsToObj) {
        final Node calleePointed = calleeCG.getPointsToEdge(calleeNode);
        if (Objects.nonNull(calleePointed)) {
            for (Node callerNode : mapsToField) {
                final Node callerPointed = getPointsToEdge(callerNode);
                if (Objects.nonNull(callerPointed)) {
                    updateCallerNode(calleePointed, calleeCG, mapsToObj, callerPointed);
                }
            }
        }
    }

    private void updateCallerNode(Node calleeNode, ConnectionGraph calleeCG, Collection<Node> mapsToObj, Node callerNode) {
        if (mapsToObj.add(calleeNode)) {
            // The escape state of caller nodes is marked GlobalEscape,
            // if the escape state of the callee node is GlobalEscape.
            if (calleeCG.getEscapeValue(calleeNode).isGlobalEscape()) {
                setEscapeValue(callerNode, EscapeValue.GLOBAL_ESCAPE);
            }

            for (InstanceFieldOf calleeField : calleeCG.getFields(calleeNode)) {
                final String calleeFieldName = calleeField.getVariableElement().getName();
                final Collection<Node> callerFields = getFields(callerNode).stream()
                    .filter(field -> Objects.equals(field.getVariableElement().getName(), calleeFieldName))
                    .collect(Collectors.toList());

                updatePointsToCallerNodes(calleeField, callerFields, calleeCG, mapsToObj);
            }
        }
    }

    void updateAtMethodExit() {
        // Use by pass function to eliminate all deferred edges in the CG
        bypassAllDeferredEdges(deferredEdges);

        // Mark all nodes reachable from a global escape nodes as global escape.
        propagateGlobalEscape();

        // Mark all nodes reachable from arg escape nodes, but not global escape, as arg escape.
        propagateArgEscapeOnly();
    }

    private void bypassAllDeferredEdges(Map<Node, Node> oldDeferredEdges) {
        if (oldDeferredEdges.isEmpty()) {
            deferredEdges.clear();
            return;
        }

        Map<Node, Node> newDeferredEdges = new HashMap<>();
        for (Node node : oldDeferredEdges.values()) {
            final Node defersTo = oldDeferredEdges.get(node);
            final Node pointsTo = getPointsToEdge(node);
            if (defersTo != null || pointsTo != null) {
                for (Map.Entry<Node, Node> incoming : oldDeferredEdges.entrySet()) {
                    if (incoming.getValue().equals(node)) {
                        if (defersTo != null) {
                            newDeferredEdges.put(incoming.getKey(), defersTo);
                        }
                        if (pointsTo != null) {
                            addPointsToEdgeIfAbsent(incoming.getKey(), pointsTo);
                        }
                    }
                }
            }
        }

        bypassAllDeferredEdges(newDeferredEdges);
    }

    private void propagateGlobalEscape() {
        final List<Node> globalEscape = escapeValues.entrySet().stream()
            .filter(e -> e.getValue().isGlobalEscape())
            .map(Map.Entry::getKey)
            .toList();

        // Separate computing from filtering since it modifies the collection itself
        globalEscape.forEach(this::computeGlobalEscape);

        // Propagate global escape for all parameter values with same index
        // To do that, group all parameter values by index
        // (there can be multiple when interacting with interface or abstract/overridden methods)
        final Map<Integer, List<ParameterValue>> indexedParameterValues = escapeValues.keySet().stream()
            .filter(key -> key instanceof ParameterValue)
            .map(key -> (ParameterValue) key)
            .collect(groupingBy(ParameterValue::getIndex));
        // Then, find those global parameter values,
        // and use its index to propagate escape value to other parameters values with same index
        globalEscape.stream()
            .filter(n -> n instanceof ParameterValue)
            .map(key -> (ParameterValue) key)
            .flatMap(pv ->  indexedParameterValues.get(pv.getIndex()).stream())
            .forEach(pv -> setEscapeValue(pv, EscapeValue.GLOBAL_ESCAPE));
    }

    private void computeGlobalEscape(Node from) {
        final Node pointsTo = getPointsToEdge(from);

        if (Objects.nonNull(pointsTo) && getEscapeValue(pointsTo).isMoreThanGlobalEscape()) {
            switchToGlobalEscape(pointsTo);
        }

        getFieldEdges(from).stream()
            .filter(field -> getEscapeValue(field).isMoreThanGlobalEscape())
            .forEach(this::switchToGlobalEscape);
    }

    private void switchToGlobalEscape(Node pointsTo) {
        setEscapeValue(pointsTo, EscapeValue.GLOBAL_ESCAPE);
        computeGlobalEscape(pointsTo);
    }

    void propagateArgEscapeOnly() {
        final List<Node> argEscapeOnly = escapeValues.entrySet().stream()
            .filter(e -> e.getValue().isArgEscape())
            .map(Map.Entry::getKey)
            .toList();

        // Separate computing from filtering since it modifies the collection itself
        argEscapeOnly.forEach(this::computeArgEscapeOnly);
    }

    private void computeArgEscapeOnly(Node from) {
        final Node pointsTo = getPointsToEdge(from);
        if (Objects.nonNull(pointsTo) && getEscapeValue(pointsTo).isMoreThanArgEscape()) {
            switchToArgEscape(pointsTo);
        }

        getFieldEdges(from).stream()
            .filter(field -> getEscapeValue(field).isMoreThanArgEscape())
            .forEach(this::switchToArgEscape);
    }

    private void switchToArgEscape(Node to) {
        setEscapeValue(to, EscapeValue.ARG_ESCAPE);
        computeArgEscapeOnly(to);
    }

    ConnectionGraph union(ConnectionGraph other) {
        if (Objects.nonNull(other)) {
            this.pointsToEdges.putAll(other.pointsToEdges);
            this.deferredEdges.putAll(other.deferredEdges);
            this.fieldEdges.putAll(other.fieldEdges);

            final Map<Node, EscapeValue> mergedEscapeValues = mergeEscapeValues(other);
            this.escapeValues.clear();
            this.escapeValues.putAll(mergedEscapeValues);

            this.localNewNodes.putAll(other.localNewNodes);
            this.parameters.addAll(other.parameters);
        }

        return this;
    }

    void resolveReturnedPhiValues() {
        final List<Value> possibleNewValues = this.escapeValues.entrySet().stream()
            .filter(entry -> entry.getKey() instanceof PhiValue && entry.getValue().isArgEscape())
            .flatMap(entry -> ((PhiValue) entry.getKey()).getPossibleValues().stream())
            .filter(value -> value instanceof New && getEscapeValue(value).isMoreThanArgEscape())
            .toList();

        // Separate computing from filtering since it modifies the collection itself
        possibleNewValues.forEach(value -> setEscapeValue(value, EscapeValue.ARG_ESCAPE));
    }

    /**
     * Validate the escape state value of New nodes in the connection graph.
     * If New nodes exist that are not amongst the supported ones,
     * their escape state value must be pessimistically set to global escape.
     *
     * When data flow graphs are not fully handled,
     * any New nodes found along the way must be pessimistically set their escape state value to global.
     * This is done to avoid relying on escape state values that haven't been calculated precisely enough.
     * When precision cannot be guaranteed, the escape estate value cannot be relied upon.
     *
     * This method assumes that only no escape, or argument escape, verified New nodes are passed in.
     */
    void validateNewNodes(List<New> supported) {
        final List<Node> unsupportedNewNodes = this.escapeValues.entrySet().stream()
            // Find all non-global escape nodes in the connection graph
            .filter(e -> e.getKey() instanceof New && e.getValue().notGlobalEscape())
            // Find those that are not verified
            .filter(e -> !supported.contains(e.getKey()))
            .map(Map.Entry::getKey)
            .toList();

        // Unverified nodes get their escape state pessimistically set to global escape
        unsupportedNewNodes.forEach(node -> setEscapeValue(node, EscapeValue.GLOBAL_ESCAPE));
    }

    private Map<Node, EscapeValue> mergeEscapeValues(ConnectionGraph other) {
        final Map<Node, EscapeValue> result = new HashMap<>(this.escapeValues);
        other.escapeValues.forEach((key, value) -> result.merge(key, value, EscapeValue::merge));
        return result;
    }

    private boolean addFieldEdgeIfAbsent(Node from, InstanceFieldOf to) {
        return fieldEdges
            .computeIfAbsent(from, obj -> new ArrayList<>())
            .add(to);
    }

    private boolean addPointsToEdgeIfAbsent(Node from, Node to) {
        return pointsToEdges.putIfAbsent(from, to) == null;
    }

    private boolean addDeferredEdgeIfAbsent(Node from, Node to) {
        return deferredEdges.putIfAbsent(from, to) == null;
    }

    private boolean setEscapeValue(Node node, EscapeValue escapeValue) {
        final EscapeValue prev = escapeValues.get(node);
        if (prev == null || prev != EscapeValue.merge(prev, escapeValue)) {
            escapeValues.put(node, escapeValue);
            return true;
        }

        return false;
    }

    private static final class ParameterArray {
        private final ParameterValue[] elements;

        public ParameterArray(int size) {
            this.elements = new ParameterValue[size];
        }

        boolean addIfAbsent(ParameterValue elem) {
            if (elements[elem.getIndex()] != null) {
                return false;
            }

            elements[elem.getIndex()] = elem;
            return true;
        }

        ParameterValue get(int index) {
            return elements[index];
        }

        void addAll(ParameterArray other) {
            for (int i = 0; i < elements.length; i++) {
                elements[i] = other.get(i);
            }
        }

        int size() {
            return elements.length;
        }
    }
}
