package com.hypixel.hytale.math.vector;

import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.joml.Vector3ic;

/** Compatibility shim restoring the pre-JOML {@code com.hypixel.hytale.math.vector.Vector3f} surface. */
public class Vector3f extends org.joml.Vector3f {

    public static final Vector3f ZERO = new Vector3f(0, 0, 0);
    public static final Vector3f ONE = new Vector3f(1, 1, 1);

    public Vector3f() {
        super();
    }

    public Vector3f(float x, float y, float z) {
        super(x, y, z);
    }

    public Vector3f(Vector3fc v) {
        super(v);
    }

    public Vector3f(Vector3dc v) {
        super((float) v.x(), (float) v.y(), (float) v.z());
    }

    public Vector3f(Vector3ic v) {
        super(v.x(), v.y(), v.z());
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public Vector3f setX(float x) {
        this.x = x;
        return this;
    }

    public Vector3f setY(float y) {
        this.y = y;
        return this;
    }

    public Vector3f setZ(float z) {
        this.z = z;
        return this;
    }

    public Vector3f copy() {
        return new Vector3f(this);
    }

    @Override
    public Vector3f clone() {
        return new Vector3f(this);
    }
}
