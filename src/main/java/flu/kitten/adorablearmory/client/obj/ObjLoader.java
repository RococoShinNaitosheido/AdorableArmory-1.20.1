package flu.kitten.adorablearmory.client.obj;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraftforge.client.model.obj.ObjMaterialLibrary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

public class ObjLoader {

    public static class Vertex {
        public float px, py, pz;
        public float u, v;
        public float nx, ny, nz;
    }

    public static class SubMesh {
        public final String material; // 来自 usemtl
        public ResourceLocation diffuse; // 来自 .mtl 的 map_Kd 可为空
        public ResourceLocation ambientMap; // map_Ka 可能为 null
        public ResourceLocation specularMap; // map_Ks 可能为 null
        public final org.joml.Vector4f ambientColor  = new org.joml.Vector4f();
        public final org.joml.Vector4f diffuseColor  = new org.joml.Vector4f(1,1,1,1);
        public final org.joml.Vector4f specularColor = new org.joml.Vector4f();
        public float specularHighlight = 0f; // Ns
        public float alpha = 1f;
        public int diffuseTintIndex = 0;
        public final List<Vertex> vertices = new ArrayList<>();
        public float minX, minY, minZ, maxX, maxY, maxZ;

        public SubMesh(String mtl) {
            this.material = mtl;
        }

        public void computeBounds() {
            if (vertices.isEmpty()) {
                minX=minY=minZ=maxX=maxY=maxZ=0;
                return;
            }
            minX=maxX=vertices.get(0).px; minY=maxY=vertices.get(0).py; minZ=maxZ=vertices.get(0).pz;
            for (var v : vertices) {
                if (v.px < minX) minX = v.px; if (v.py < minY) minY = v.py; if (v.pz < minZ) minZ = v.pz;
                if (v.px > maxX) maxX = v.px; if (v.py > maxY) maxY = v.py; if (v.pz > maxZ) maxZ = v.pz;
            }
        }
    }

    public static class TriModel {
        public final List<SubMesh> parts = new ArrayList<>();
        public final TriMesh merged = new TriMesh();
        public float minX, minY, minZ, maxX, maxY, maxZ;
        public void computeBounds() {
            if (parts.isEmpty()) {
                minX=minY=minZ=maxX=maxY=maxZ=0; return;
            }
            boolean first = true;
            for (var p : parts) {
                p.computeBounds();
                if (first) {
                    first = false;
                    minX=p.minX; minY=p.minY; minZ=p.minZ; maxX=p.maxX; maxY=p.maxY; maxZ=p.maxZ;
                } else {
                    if (p.minX<minX) minX=p.minX; if (p.minY<minY) minY=p.minY; if (p.minZ<minZ) minZ=p.minZ;
                    if (p.maxX>maxX) maxX=p.maxX; if (p.maxY>maxY) maxY=p.maxY; if (p.maxZ>maxZ) maxZ=p.maxZ;
                }
            }
            merged.minX = minX; merged.minY = minY; merged.minZ = minZ;
            merged.maxX = maxX; merged.maxY = maxY; merged.maxZ = maxZ;
        }

        public float diagLength() {
            float dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
            return Mth.sqrt(dx*dx + dy*dy + dz*dz);
        }
    }

    public static class TriMesh {
        public final List<Vertex> vertices = new ArrayList<>(); // 每3为一个三角形
        public float minX, minY, minZ, maxX, maxY, maxZ;

        public void computeBounds() {
            if (vertices.isEmpty()) {
                minX = minY = minZ = maxX = maxY = maxZ = 0;
                return;
            }
            minX = maxX = vertices.get(0).px;
            minY = maxY = vertices.get(0).py;
            minZ = maxZ = vertices.get(0).pz;
            for (var v : vertices) {
                if (v.px < minX) minX = v.px;
                if (v.py < minY) minY = v.py;
                if (v.pz < minZ) minZ = v.pz;
                if (v.px > maxX) maxX = v.px;
                if (v.py > maxY) maxY = v.py;
                if (v.pz > maxZ) maxZ = v.pz;
            }
        }

        public void smoothNormals() {
            // group by rounded position (simple but effective)
            Map<String, float[]> acc = new HashMap<>();
            Map<String, Integer> cnt = new HashMap<>();

            for (Vertex v : vertices) {
                String key = Math.round(v.px * 1e6) + "_" + Math.round(v.py * 1e6) + "_" + Math.round(v.pz * 1e6);
                float[] a = acc.get(key);
                if (a == null) {
                    a = new float[]{v.nx, v.ny, v.nz};
                    acc.put(key, a);
                    cnt.put(key, 1);
                } else {
                    a[0] += v.nx;
                    a[1] += v.ny;
                    a[2] += v.nz;
                    cnt.put(key, cnt.get(key) + 1);
                }
            }

            for (Vertex v : vertices) {
                String key = Math.round(v.px * 1e6) + "_" + Math.round(v.py * 1e6) + "_" + Math.round(v.pz * 1e6);
                float[] a = acc.get(key);
                int n = cnt.getOrDefault(key, 1);
                float nx = a[0] / n, ny = a[1] / n, nz = a[2] / n;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-7f) { v.nx = 0; v.ny = 0; v.nz = 1; }
                else { v.nx = nx / len; v.ny = ny / len; v.nz = nz / len; }
            }
        }

        public float diagLength() {
            float dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
            return Mth.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    // return TriModel
    public static TriModel loadWithMtl(ResourceManager mgr, ResourceLocation objLoc, boolean flipV) throws IOException {
        ResourceLocation mtlRL = null;
        var res = mgr.getResource(objLoc).orElseThrow(() -> new IOException("OBJ not found: " + objLoc));

        ObjMaterialLibrary mtlLib = null;
        Map<String, SubMesh> buckets = new LinkedHashMap<>();
        String currentMtl = "default";

        try (var in = res.open();
             var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            List<float[]> positions = new ArrayList<>();
            List<float[]> coords = new ArrayList<>();
            List<float[]> normals = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "mtllib" -> {
                        if (parts.length >= 2) {
                            mtlRL = resolveRelative(objLoc, parts[1]);
                            mtlLib = net.minecraftforge.client.model.obj.ObjLoader.INSTANCE.loadMaterialLibrary(mtlRL); // Forge ObjLoader 解析.mtl
                        }
                    }
                    case "usemtl" -> currentMtl = (parts.length >= 2) ? parts[1] : "default";
                    case "v" -> {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = Float.parseFloat(parts[3]);
                        positions.add(new float[]{x, y, z});
                    }
                    case "vt" -> {
                        float u = Float.parseFloat(parts[1]);
                        float v = Float.parseFloat(parts[2]);
                        if (flipV) v = 1.0f - v;
                        coords.add(new float[]{u, v});
                    }
                    case "vn" -> {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = Float.parseFloat(parts[3]);
                        normals.add(new float[]{x, y, z});
                    }
                    case "f" -> {
                        if (parts.length < 4) break;
                        List<Vertex> poly = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) {
                            String[] idx = parts[i].split("/");
                            int vi = parseIndex(idx, 0, positions.size());
                            int ti = parseIndex(idx, 1, coords.size());
                            int ni = parseIndex(idx, 2, normals.size());
                            float[] p = positions.get(vi);
                            float[] t = (ti >= 0) ? coords.get(ti) : new float[]{0f, 0f};
                            float[] n = (ni >= 0) ? normals.get(ni) : null;

                            Vertex vtx = new Vertex();
                            vtx.px = p[0]; vtx.py = p[1]; vtx.pz = p[2];
                            vtx.u = t[0];  vtx.v = t[1];
                            if (n != null) { vtx.nx = n[0]; vtx.ny = n[1]; vtx.nz = n[2]; }
                            poly.add(vtx);
                        }

                        for (int i = 1; i + 1 < poly.size(); i++) {
                            Vertex a = copy(poly.get(0));
                            Vertex b = copy(poly.get(i));
                            Vertex c = copy(poly.get(i + 1));
                            if ((a.nx==0 && a.ny==0 && a.nz==0) || (b.nx==0 && b.ny==0 && b.nz==0) || (c.nx==0 && c.ny==0 && c.nz==0)) {
                                float[] fn = faceNormal(a,b,c);
                                a.nx=b.nx=c.nx=fn[0];
                                a.ny=b.ny=c.ny=fn[1];
                                a.nz=b.nz=c.nz=fn[2];
                            }

                            buckets.computeIfAbsent(currentMtl, SubMesh::new).vertices.add(a);
                            buckets.get(currentMtl).vertices.add(b);
                            buckets.get(currentMtl).vertices.add(c);
                        }
                    }
                    default -> {}
                }
            }
        }

        if (mtlLib != null) {
            for (var entry : buckets.entrySet()) {
                String mtlName = entry.getKey();
                SubMesh sm = entry.getValue();
                ObjMaterialLibrary.Material material = null;
                try {
                    material = mtlLib.getMaterial(mtlName);
                } catch (NoSuchElementException ignore) {}
                if (material != null) {
                    // 颜色
                    sm.ambientColor.set(material.ambientColor);
                    sm.diffuseColor.set(material.diffuseColor);
                    sm.specularColor.set(material.specularColor);
                    sm.specularHighlight = material.specularHighlight;
                    sm.diffuseTintIndex = material.diffuseTintIndex;

                    float d = material.dissolve;  // 默认为 1
                    float Tr = material.transparency;   // 默认为 0
                    sm.alpha = Mth.clamp(d * (1f - Tr), 0f, 1f);

                    ResourceLocation baseForMaps = (mtlRL != null) ? mtlRL : objLoc;
                    if (notBlank(material.diffuseColorMap)) {
                        sm.diffuse = toResourceLocation(material.diffuseColorMap, baseForMaps);
                    }
                    if (notBlank(material.ambientColorMap)) {
                        sm.ambientMap = toResourceLocation(material.ambientColorMap, baseForMaps);
                    }
                    if (notBlank(material.specularColorMap)) {
                        sm.specularMap = toResourceLocation(material.specularColorMap, baseForMaps);
                    }
                }
            }
        }

        TriModel model = new TriModel();
        model.parts.addAll(buckets.values());

        for (var sm : model.parts) {
            model.merged.vertices.addAll(sm.vertices);
        }

        model.computeBounds();
        boolean anyVN = model.merged.vertices.stream().anyMatch(v -> !(v.nx==0 && v.ny==0 && v.nz==0));
        if (!anyVN) model.merged.smoothNormals();

        return model;
    }

    private static boolean notBlank(String string) {
        return string != null && !string.isEmpty() && !string.trim().isEmpty();
    }

    private static ResourceLocation toResourceLocation(String mapPathRaw, ResourceLocation baseRL) {
        String s = mapPathRaw.trim().replace('\\', '/');

        int colon = s.indexOf(':');
        if (colon >= 0) {
            String ns = s.substring(0, colon);
            String path = s.substring(colon + 1).replace('\\','/').toLowerCase(Locale.ROOT);
            return new ResourceLocation(ns, path);
        }

        String base = baseRL.getPath().replace('\\', '/');
        int slash = base.lastIndexOf('/');
        String dir = (slash >= 0) ? base.substring(0, slash + 1) : "";
        String normalized = Paths.get(dir + s).normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return new ResourceLocation(baseRL.getNamespace(), normalized);
    }

    public static TriMesh load(ResourceManager mgr, ResourceLocation objLoc, boolean flipV) throws IOException {
        TriModel model = loadWithMtl(mgr, objLoc, flipV);
        return model.merged;
    }

    private static ResourceLocation resolveRelative(ResourceLocation objLoc, String relRaw) {
        String rel = relRaw.replace('\\', '/');
        String base = objLoc.getPath().replace('\\', '/');
        int slash = base.lastIndexOf('/');
        String dir = (slash >= 0) ? base.substring(0, slash + 1) : "";

        String combined = dir + rel;
        String normalized = Paths.get(combined).normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return new ResourceLocation(objLoc.getNamespace(), normalized);
    }

    private static int parseIndex(String[] idx, int slot, int size) {
        if (idx.length <= slot || idx[slot].isEmpty()) return -1;
        int raw = Integer.parseInt(idx[slot]);
        if (raw > 0) return raw - 1;
        if (raw < 0) return size + raw; // -1 表示最后一个
        return -1;
    }

    private static Vertex copy(Vertex v) {
        Vertex r = new Vertex();
        r.px = v.px; r.py = v.py; r.pz = v.pz;
        r.u = v.u; r.v = v.v;
        r.nx = v.nx; r.ny = v.ny; r.nz = v.nz;
        return r;
    }

    private static float[] faceNormal(Vertex a, Vertex b, Vertex c) {
        float ux = b.px - a.px, uy = b.py - a.py, uz = b.pz - a.pz;
        float vx = c.px - a.px, vy = c.py - a.py, vz = c.pz - a.pz;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-7f) return new float[]{0, 0, 1};
        return new float[]{nx / len, ny / len, nz / len};
    }
}
