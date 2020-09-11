package cc.quarkus.qcc.interpreter;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cc.quarkus.qcc.graph.GraphFactory;
import cc.quarkus.qcc.type.definition.DefinedTypeDefinition;
import io.smallrye.common.constraint.Assert;

/**
 * A virtual machine.
 */
public interface JavaVM extends AutoCloseable {
    /**
     * Create a new thread.
     *
     * @param threadName the thread name
     * @param threadGroup the thread group object (or {@code null})
     * @param daemon {@code true} to make a daemon thread
     * @return the new thread
     */
    JavaThread newThread(String threadName, JavaObject threadGroup, boolean daemon);

    static JavaThread currentThread() {
        return JavaVMImpl.currentThread();
    }

    static JavaThread requireCurrentThread() {
        JavaThread javaThread = currentThread();
        if (javaThread == null) {
            throw new IllegalStateException("Thread is not attached");
        }
        return javaThread;
    }

    static JavaVM current() {
        JavaThread javaThread = currentThread();
        return javaThread == null ? null : javaThread.getVM();
    }

    static JavaVM requireCurrent() {
        return requireCurrentThread().getVM();
    }

    JavaClass getClassClass();

    JavaClass getObjectClass();

    /**
     * Define an unresolved class into this VM.
     *
     * @param name the class name (must not be {@code null})
     * @param classLoader the class loader instance ({@code null} indicates the bootstrap class loader)
     * @param bytes the class bytes (must not be {@code null})
     * @return the defined class (not {@code null})
     */
    JavaClass defineClass(String name, JavaObject classLoader, ByteBuffer bytes);

    /**
     * Define an unresolved anonymous class into this VM.
     *
     * @param hostClass the host class (must not be {@code null})
     * @param bytes the class bytes (must not be {@code null})
     * @return the defined class (not {@code null})
     */
    JavaClass defineAnonymousClass(JavaClass hostClass, ByteBuffer bytes);

    /**
     * Load a class. If the class is already loaded, it is returned without entering the VM.  Otherwise the
     * current thread must be bound to a VM thread.
     *
     * @param classLoader the class loader instance ({@code null} indicates the bootstrap class loader)
     * @param name the internal name of the class to load (must not be {@code null})
     * @return the class (not {@code null})
     * @throws Thrown if the internal JVM has thrown an exception while loading the class
     */
    JavaClass loadClass(JavaObject classLoader, String name) throws Thrown;

    /**
     * Find a loaded class, returning {@code null} if the class loader did not previously load the class.  The VM
     * is not entered.
     *
     * @param classLoader the class loader instance ({@code null} indicates the bootstrap class loader)
     * @param name the internal name of the class to load (must not be {@code null})
     * @return the class, or {@code null} if the class was not already loaded
     */
    JavaClass findLoadedClass(JavaObject classLoader, String name);

    /**
     * Deliver a "signal" to the target environment.
     *
     * @param signal the signal to deliver
     */
    void deliverSignal(Signal signal);

    /**
     * Wait for the VM to terminate, returning the exit code.
     *
     * @return the VM exit code
     * @throws InterruptedException if the calling thread was interrupted before the VM terminates
     */
    int awaitTermination() throws InterruptedException;

    /**
     * Get a deduplicated string.
     *
     * @param classLoader the class loader whose deduplication table should be used
     * @param string the string to deduplicate
     * @return the deduplicated string
     */
    String deduplicate(JavaObject classLoader, String string);

    String deduplicate(JavaObject classLoader, ByteBuffer buffer, int offset, int length, boolean expectTerminator);

    /**
     * Allocate a direct byte buffer object with the given backing buffer.  The backing content will be determined
     * by the limit and position of the given buffer at the time this method is called.  The given buffer may be
     * direct or heap-based.
     *
     * @param backingBuffer the backing buffer (must not be {@code null})
     * @return the allocated direct buffer object (not {@code null})
     */
    JavaObject allocateDirectBuffer(ByteBuffer backingBuffer);

    /**
     * Kill the VM, terminating all in-progress threads and releasing all heap objects.
     */
    void close();

    /**
     * Get a builder for a type definition.  The given class loader is only used to resolve classes, and does
     * not cause the new class to be registered to that class loader (which is something that must be done
     * by the dictionary).
     *
     * @param classLoader the class loader for the new class, or {@code null} for the bootstrap class loader
     * @return the builder
     */
    DefinedTypeDefinition.Builder newTypeDefinitionBuilder(JavaObject classLoader);

    /**
     * Get a builder for a new VM.
     *
     * @return the builder (not {@code null})
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for the VM.
     */
    class Builder {
        final List<Path> bootstrapModules = new ArrayList<>();
        final List<Path> platformModules = new ArrayList<>();
        GraphFactory graphFactory = GraphFactory.BASIC_FACTORY;
        final Map<String, String> systemProperties = new HashMap<>();

        Builder() {
        }

        /**
         * Add a bootstrap module.
         *
         * @param modulePath the path to the module JAR (must not be {@code null})
         * @return this builder
         */
        public Builder addBootstrapModule(Path modulePath) {
            bootstrapModules.add(Assert.checkNotNullParam("modulePath", modulePath));
            return this;
        }

        /**
         * Add all of the given bootstrap modules.
         *
         * @param modulePaths the paths to the module JARs (must not be {@code null})
         * @return this builder
         */
        public Builder addBootstrapModules(final List<Path> modulePaths) {
            bootstrapModules.addAll(modulePaths);
            return this;
        }

        /**
         * Add a platform (non-bootstrap) module.
         *
         * @param modulePath the path to the module JAR (must not be {@code null})
         * @return this builder
         */
        public Builder addPlatformModule(Path modulePath) {
            platformModules.add(Assert.checkNotNullParam("modulePath", modulePath));
            return this;
        }

        /**
         * Set an initial system property.
         *
         * @param propertyName  the property name (must not be {@code null})
         * @param propertyValue the property value (must not be {@code null})
         * @return this builder
         */
        public Builder setSystemProperty(String propertyName, String propertyValue) {
            systemProperties.put(Assert.checkNotNullParam("propertyName", propertyName), Assert.checkNotNullParam("propertyValue)", propertyValue));
            return this;
        }

        /**
         * Set the graph factory to use for bytecode parsing.
         *
         * @param graphFactory the graph factory to use (must not be {@code null})
         * @return this builder
         */
        public Builder setGraphFactory(final GraphFactory graphFactory) {
            this.graphFactory = Assert.checkNotNullParam("graphFactory", graphFactory);
            return this;
        }

        /**
         * Construct the new VM.
         *
         * @return the new VM (not {@code null})
         */
        public JavaVM build() {
            JavaVMImpl javaVM = new JavaVMImpl(this);
            // invoke System.initPhase1
            // invoke System.initPhase2
            // invoke System.initPhase3
            return javaVM;
        }
    }
}