package com.hypixel.hytale.math.vector;

import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.joml.Vector3ic;

/** Compatibility shim restoring the pre-JOML {@code com.hypixel.hytale.math.vector.Vector3d} surface. */
public class Vector3d extends org.joml.Vector3d {

    public static final Vector3d ZERO = new Vector3d(0, 0, 0);
    public static final Vector3d ONE = new Vector3d(1, 1, 1);

    public Vector3d() {
        super();
    }

    public Vector3d(double x, double y, double z) {
        super(x, y, z);
    }

    public Vector3d(Vector3dc v) {
        super(v);
    }

    public Vector3d(Vector3ic v) {
        super(v.x(), v.y(), v.z());
    }

    public Vector3d(Vector3fc v) {
        super(v.x(), v.y(), v.z());
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public Vector3d setX(double x) {
        this.x = x;
        return this;
    }

    public Vector3d setY(double y) {
        this.y = y;
        return this;
    }

    public Vector3d setZ(double z) {
        this.z = z;
        return this;
    }

    public Vector3d copy() {
        return new Vector3d(this);
    }

    @Override
    public Vector3d clone() {
        return new Vector3d(this);
    }

    @Override
    public Vector3d add(Vector3dc v) {
        super.add(v);
        return this;
    }

    @Override
    public Vector3d add(double x, double y, double z) {
        super.add(x, y, z);
        return this;
    }

    @Override
    public Vector3d sub(Vector3dc v) {
        super.sub(v);
        return this;
    }

    @Override
    public Vector3d sub(double x, double y, double z) {
        super.sub(x, y, z);
        return this;
    }

    @Override
    public Vector3d mul(double scalar) {
        super.mul(scalar);
        return this;
    }

    @Override
    public Vector3d negate() {
        super.negate();
        return this;
    }

    @Override
    public Vector3d zero() {
        super.zero();
        return this;
    }
}
