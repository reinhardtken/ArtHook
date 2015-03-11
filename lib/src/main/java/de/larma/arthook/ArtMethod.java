/*
 * Copyright 2014-2015 Marvin Wißfeld
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.larma.arthook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;

/**
 * A helper class to access {@link java.lang.reflect.ArtMethod} using reflection.
 * <p/>
 * There might exist multiple instances of this helper class at the same time, but
 * {@link #hashCode()} is the same for all of them
 */
public class ArtMethod {
    private static final String ART_METHOD_CLASS_NAME = "java.lang.reflect.ArtMethod";
    private static final String ABSTRACT_METHOD_CLASS_NAME = "java.lang.reflect.AbstractMethod";
    private static final String FIELD_ART_METHOD = "artMethod";

    private static final String FIELD_ACCESS_FLAGS = "accessFlags";
    private static final String FIELD_DEX_METHOD_INDEX = "dexMethodIndex";
    private static final String FIELD_ENTRY_POINT_FROM_JNI = "entryPointFromJni";
    private static final int FIELD_ENTRY_POINT_FROM_JNI_MR1_PRIV_INDEX = 1;
    private static final String FIELD_ENTRY_POINT_FROM_INTERPRETER = "entryPointFromInterpreter";
    private static final int FIELD_ENTRY_POINT_FROM_INTERPRETER_MR1_PRIV_INDEX = 0;
    private static final String FIELD_ENTRY_POINT_FROM_QUICK_COMPILED_CODE = "entryPointFromQuickCompiledCode";
    private static final int FIELD_ENTRY_POINT_FROM_QUICK_COMPILED_CODE_MR1_PRIV_INDEX = 2;

    private static final int LOLLIPOP_MR1_PUB_FIELDS = 36;
    private static final int LOLLIPOP_MR1_PRIV_FIELDS = Native.is64Bit() ? 24 : 12;
    private static final int LOLLIPOP_MR1_OBJECT_SIZE = LOLLIPOP_MR1_PRIV_FIELDS + LOLLIPOP_MR1_PUB_FIELDS;

    private final Object artMethod;

    /**
     * Create a new ArtMethod.
     * <p/>
     * This really creates a new ArtMethod, not only a helper.
     */
    public ArtMethod() {
        try {
            Constructor constructor = Class.forName(ART_METHOD_CLASS_NAME).getDeclaredConstructor();
            constructor.setAccessible(true);
            artMethod = constructor.newInstance();
        } catch (Throwable t) {
            throw new RuntimeException("Can't create new ArtMethod, is this a system running Art?",
                    t);
        }
    }

    private ArtMethod(Object artMethod) {
        Assertions.argumentNotNull(artMethod, "artMethod");
        this.artMethod = artMethod;
    }

    /**
     * Generate a helper for the ArtMethod instance residing in the given Method.
     *
     * @param method Any valid Method instance.
     * @return A new helper for the ArtMethod
     * @throws java.lang.NullPointerException when the {@param method} is a null pointer
     */
    public static ArtMethod of(Method method) {
        if (method == null)
            return null;
        try {
            Field artMethodField = Class.forName(ABSTRACT_METHOD_CLASS_NAME)
                    .getDeclaredField(FIELD_ART_METHOD);
            artMethodField.setAccessible(true);
            return new ArtMethod(artMethodField.get(method));
        } catch (Throwable e) {
            throw new RuntimeException(
                    "Method has no artMethod field, is this a system running Art?", e);
        }
    }

    private Object get(String name) {
        if (SDK_INT >= LOLLIPOP_MR1) {
            switch (name) {
                case FIELD_ENTRY_POINT_FROM_INTERPRETER:
                    return getMr1Priv(FIELD_ENTRY_POINT_FROM_INTERPRETER_MR1_PRIV_INDEX);
                case FIELD_ENTRY_POINT_FROM_JNI:
                    return getMr1Priv(FIELD_ENTRY_POINT_FROM_JNI_MR1_PRIV_INDEX);
                case FIELD_ENTRY_POINT_FROM_QUICK_COMPILED_CODE:
                    return getMr1Priv(FIELD_ENTRY_POINT_FROM_QUICK_COMPILED_CODE_MR1_PRIV_INDEX);
            }
        }
        return get(getField(name));
    }

    private long getMr1Priv(int num) {
        long objectAddress = Unsafe.getObjectAddress(artMethod);
        int intSize = Native.is64Bit() ? 8 : 4;
        byte[] bytes = Native.memget_verbose(objectAddress + LOLLIPOP_MR1_PUB_FIELDS + intSize * num, intSize);
        if (Native.is64Bit()) {
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
        } else {
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }
    }

    private void set(String name, Object value) {
        if (SDK_INT >= LOLLIPOP_MR1) {
            switch (name) {
                case FIELD_ENTRY_POINT_FROM_INTERPRETER:
                    setMr1Priv(FIELD_ENTRY_POINT_FROM_INTERPRETER_MR1_PRIV_INDEX, (Long) value);
                    return;
                case FIELD_ENTRY_POINT_FROM_JNI:
                    setMr1Priv(FIELD_ENTRY_POINT_FROM_JNI_MR1_PRIV_INDEX, (Long) value);
                    return;
                case FIELD_ENTRY_POINT_FROM_QUICK_COMPILED_CODE:
                    setMr1Priv(FIELD_ENTRY_POINT_FROM_QUICK_COMPILED_CODE_MR1_PRIV_INDEX, (Long) value);
                    return;
            }
        }
        set(getField(name), value);
    }

    private void setMr1Priv(int num, long value) {
        long objectAddress = Unsafe.getObjectAddress(artMethod);
        int intSize = Native.is64Bit() ? 8 : 4;
        byte[] bytes;
        if (Native.is64Bit()) {
            bytes = ByteBuffer.allocate(intSize).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
        } else {
            bytes = ByteBuffer.allocate(intSize).order(ByteOrder.LITTLE_ENDIAN).putInt((int) value).array();
        }
        Native.memput_verbose(bytes, objectAddress + LOLLIPOP_MR1_PUB_FIELDS + intSize * num);
    }

    @SuppressWarnings({"CloneDoesntCallSuperClone", "CloneDoesntDeclareCloneNotSupportedException"})
    public ArtMethod clone() {
        ArtMethod clone = new ArtMethod();
        if (SDK_INT >= LOLLIPOP_MR1) {
            long objectAddress = Unsafe.getObjectAddress(artMethod);
            long map = Native.mmap_verbose(LOLLIPOP_MR1_OBJECT_SIZE);
            Native.memcpy(objectAddress, map, LOLLIPOP_MR1_OBJECT_SIZE);
            try {
                long pointerOffset = Unsafe.objectFieldOffset(ArtMethod.class.getDeclaredField("artMethod"));
                Unsafe.putLong(clone, pointerOffset, map);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
        writeTo(clone);
        return clone;
    }

    public Method newMethod() {
        try {
            Method m = Method.class.getConstructor(Class.forName(ART_METHOD_CLASS_NAME))
                    .newInstance(artMethod);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            throw new RuntimeException("Can't create new Method", t);
        }
    }

    public void writeTo(ArtMethod target) {
        try {
            for (Field field : Class.forName(ART_METHOD_CLASS_NAME).getDeclaredFields()) {
                field.setAccessible(true);
                target.set(field, get(field));
            }
            if (SDK_INT >= LOLLIPOP_MR1) {
                // Also write priv fields:
                target.setEntryPointFromInterpreter(getEntryPointFromInterpreter());
                target.setEntryPointFromJni(getEntryPointFromJni());
                target.setEntryPointFromQuickCompiledCode(getEntryPointFromQuickCompiledCode());
            }
        } catch (ClassNotFoundException ignored) {
        }
    }

    public void setDexMethodIndex(int dexMethodIndex) {
        set(FIELD_DEX_METHOD_INDEX, dexMethodIndex);
    }

    public int getAccessFlags() {
        return (int) get(FIELD_ACCESS_FLAGS);
    }

    public void setAccessFlags(int flags) {
        set(FIELD_ACCESS_FLAGS, flags);
    }

    public long getEntryPointFromJni() {
        return (long) get(FIELD_ENTRY_POINT_FROM_JNI);
    }

    public void setEntryPointFromJni(long entryPointFromJni) {
        set(FIELD_ENTRY_POINT_FROM_JNI, entryPointFromJni);
    }

    public long getEntryPointFromInterpreter() {
        return (long) get(FIELD_ENTRY_POINT_FROM_INTERPRETER);
    }

    public void setEntryPointFromInterpreter(long entryPointFromInterpreter) {
        set(FIELD_ENTRY_POINT_FROM_INTERPRETER, entryPointFromInterpreter);
    }

    public long getEntryPointFromQuickCompiledCode() {
        return (long) get(FIELD_ENTRY_POINT_FROM_QUICK_COMPILED_CODE);
    }

    public void setEntryPointFromQuickCompiledCode(long entryPointFromQuickCompiledCode) {
        set(FIELD_ENTRY_POINT_FROM_QUICK_COMPILED_CODE, entryPointFromQuickCompiledCode);
    }

    public void makePrivate() {
        setAccessFlags(
                getAccessFlags() | Modifier.PRIVATE & ~Modifier.PUBLIC & ~Modifier.PROTECTED);
    }

    public void makeNative() {
        setAccessFlags(getAccessFlags() | Modifier.NATIVE);
    }

    public boolean isFinal() {
        return Modifier.isFinal(getAccessFlags()) || Modifier.isPrivate(getAccessFlags())
                || Modifier.isFinal(((Class) get("declaringClass")).getModifiers());
    }

    public long getAddress() {
        return Unsafe.getObjectAddress(artMethod);
    }

    private void set(Field field, Object value) {
        try {
            field.set(artMethod, value);
        } catch (IllegalAccessException ignored) {
        }
    }

    private Object get(Field field) {
        try {
            return field.get(artMethod);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private Field getField(String name) {
        Field field;
        try {
            field = Class.forName(ART_METHOD_CLASS_NAME).getDeclaredField(name);
        } catch (Throwable e) {
            throw new RuntimeException("Field " + name + " is not available on this system", e);
        }
        field.setAccessible(true);
        return field;
    }

    @Override
    public String toString() {
        return "ArtMethod{" + newMethod() + ", intern=" + artMethod + ", " +
                "entryPoint=" + getEntryPointFromQuickCompiledCode() + "}";
    }

    @Override
    public boolean equals(Object other) {
        return this == other || !(other == null || getClass() != other.getClass() || !artMethod
                .equals(((ArtMethod) other).artMethod));

    }

    @Override
    public int hashCode() {
        return artMethod.hashCode();
    }

}