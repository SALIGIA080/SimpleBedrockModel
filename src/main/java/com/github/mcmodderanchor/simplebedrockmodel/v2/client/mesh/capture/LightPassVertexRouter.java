package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** 根据捕获时的 UV2 将完整图元送往普通或固定发光 pass。 */
public class LightPassVertexRouter implements VertexConsumer {
    private final VertexConsumer ordinary;
    private final VertexConsumer emissive;
    private final int primitiveSize;
    private int primitiveVertices;
    private boolean primitiveEmissive;
    private float x, y, z;
    private int red = 255, green = 255, blue = 255, alpha = 255;
    private int defaultRed = 255, defaultGreen = 255, defaultBlue = 255, defaultAlpha = 255;
    private float u, v;
    private int overlay, lightU, lightV;
    private float normalX, normalY, normalZ;

    public LightPassVertexRouter(VertexConsumer ordinary, VertexConsumer emissive, PrimitiveTopology mode) {
        this.ordinary = ordinary;
        this.emissive = emissive;
        this.primitiveSize = switch (mode) {
            case QUADS -> 4;
            case TRIANGLES -> 3;
            default -> throw new IllegalArgumentException("Unsupported geometry mode: " + mode);
        };
    }

    private boolean hasVertex;

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (this.hasVertex) {
            this.finishVertex();
        }
        this.x = x; this.y = y; this.z = z;
        this.hasVertex = true;
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        this.red = red; this.green = green; this.blue = blue; this.alpha = alpha;
        return this;
    }

    @Override
    public VertexConsumer setColor(int packed) {
        this.red = (packed >> 16) & 0xFF;
        this.green = (packed >> 8) & 0xFF;
        this.blue = packed & 0xFF;
        this.alpha = (packed >> 24) & 0xFF;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.u = u; this.v = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setOverlay(int overlay) {
        this.overlay = overlay;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        this.lightU = u; this.lightV = v;
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        this.normalX = x; this.normalY = y; this.normalZ = z;
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }

    private void finishVertex() {
        boolean fixed = this.lightU != 0 || this.lightV != 0;
        if (this.primitiveVertices == 0) {
            this.primitiveEmissive = fixed;
        } else if (this.primitiveEmissive != fixed) {
            throw new IllegalArgumentException("Mixed light values within one mesh primitive");
        }

        VertexConsumer target = fixed ? this.emissive : this.ordinary;
        target.addVertex(this.x, this.y, this.z)
                .setColor(this.red, this.green, this.blue, this.alpha)
                .setUv(this.u, this.v)
                .setOverlay(this.overlay)
                .setUv2(this.lightU, this.lightV)
                .setNormal(this.normalX, this.normalY, this.normalZ);

        this.primitiveVertices = (this.primitiveVertices + 1) % this.primitiveSize;
        this.red = this.defaultRed; this.green = this.defaultGreen;
        this.blue = this.defaultBlue; this.alpha = this.defaultAlpha;
        this.u = this.v = 0;
        this.overlay = this.lightU = this.lightV = 0;
        this.normalX = this.normalY = this.normalZ = 0;
    }
}
