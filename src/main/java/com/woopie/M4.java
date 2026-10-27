package com.woopie;

/** Tiny row-major 4x4 matrix, so the mod has no JOML dependency. */
final class M4 {
    final double[] m = new double[16];

    static M4 identity() { M4 r = new M4(); r.m[0] = r.m[5] = r.m[10] = r.m[15] = 1; return r; }

    static M4 translate(double x, double y, double z) { M4 r = identity(); r.m[3] = x; r.m[7] = y; r.m[11] = z; return r; }

    static M4 scale(double x, double y, double z) { M4 r = identity(); r.m[0] = x; r.m[5] = y; r.m[10] = z; return r; }

    /** Euler degrees, matrix = Rz * Ry * Rx (X applied to a vertex first). */
    static M4 rotateZYX(double xd, double yd, double zd) {
        double x = Math.toRadians(xd), y = Math.toRadians(yd), z = Math.toRadians(zd);
        double cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y), cz = Math.cos(z), sz = Math.sin(z);
        M4 r = identity();
        r.m[0] = cz * cy;  r.m[1] = cz * sy * sx - sz * cx;  r.m[2] = cz * sy * cx + sz * sx;
        r.m[4] = sz * cy;  r.m[5] = sz * sy * sx + cz * cx;  r.m[6] = sz * sy * cx - cz * sx;
        r.m[8] = -sy;      r.m[9] = cy * sx;                 r.m[10] = cy * cx;
        return r;
    }

    M4 mul(M4 o) {
        M4 r = new M4();
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += m[i * 4 + k] * o.m[k * 4 + j];
                r.m[i * 4 + j] = s;
            }
        return r;
    }

    double[] point(double x, double y, double z) {
        return new double[]{
                m[0] * x + m[1] * y + m[2] * z + m[3],
                m[4] * x + m[5] * y + m[6] * z + m[7],
                m[8] * x + m[9] * y + m[10] * z + m[11]};
    }

    double[] dir(double x, double y, double z) {
        double[] d = {
                m[0] * x + m[1] * y + m[2] * z,
                m[4] * x + m[5] * y + m[6] * z,
                m[8] * x + m[9] * y + m[10] * z};
        double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        if (l > 1e-9) { d[0] /= l; d[1] /= l; d[2] /= l; }
        return d;
    }
}
