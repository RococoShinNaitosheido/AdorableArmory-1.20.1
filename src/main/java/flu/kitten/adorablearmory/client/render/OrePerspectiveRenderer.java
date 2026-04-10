package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.item.OrePerspectiveCore;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OrePerspectiveRenderer {

    private static final TagKey<Block> ORES_TAG = Tags.Blocks.ORES;
    private static final Predicate<BlockState> IS_ORES = state -> state.is(ORES_TAG);
    public static final int SCAN_RADIUS = 32;
    private static final int SCAN_INTERVAL_TICKS = 4;
    private static final int RESCAN_MOVE_THRESHOLD_SQR = 16 * 16;
    private static final int CHUNKS_PER_TICK = 4;
    private static final float LINE_WIDTH = 1.0f;
    private static final Color ORES_LINE_COLOR = new Color(255, 32, 225, 255);
    private static final float ORES_R = ORES_LINE_COLOR.getRed() / 255.0f;
    private static final float ORES_G = ORES_LINE_COLOR.getGreen() / 255.0f;
    private static final float ORES_B = ORES_LINE_COLOR.getBlue() / 255.0f;
    private static volatile List<RenderMesh> lastScanMeshes = Collections.emptyList();
    private static volatile Set<Long> lastScanKeysCached = Collections.emptySet();
    private static final AtomicInteger MESH_EPOCH = new AtomicInteger(0);
    private static volatile int lastMeshesEpoch = -1;
    private static int scanCooling = 0;
    private static BlockPos lastScanCenterPos = null;
    private static volatile boolean isScanInProgress = false;
    private static final Deque<ChunkPos> scanChunkQueue = new ArrayDeque<>();
    private static final List<Long> pendingFoundBlocks = new ArrayList<>();
    private static final HashSet<Long> pendingVisitedKeys = new HashSet<>();
    private static volatile Set<Long> lastFoundBlockSet = Collections.emptySet();
    private static final AtomicInteger SCAN_GENERATION = new AtomicInteger(0);
    private static volatile CompletableFuture<?> lastScanFuture = null;
    private static final ConcurrentHashMap<Long, Float> meshAlphaByKey = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, RenderMesh> activeMeshes = new ConcurrentHashMap<>();
    private static final int FADE_OUT_TICKS = 30;
    private static int fadeOutTicksRemaining = 0;
    private static final float ALPHA_EPS = 0.001f;
    private static final float ALPHA_TAU_SEC = 0.12f;
    private static long lastFrameNs = System.nanoTime();
    private static volatile VertexBuffer oreDepthVbo = null;
    private static volatile int oreDepthVertexCount = 0;
    private static volatile AABB oreDepthBounds = null;
    private static TextureTarget depthBackupTarget = null;
    private static volatile int renderBaseX = 0, renderBaseY = 0, renderBaseZ = 0; // Precision fix: render-local origin (base)
    private static final double BASE_TOO_FAR = 512.0; // blocks (tweak if you want)

    private static int align16(int v) {
        return v & ~15;
    }

    private static void cancelInProgressScanOnly() {
        SCAN_GENERATION.incrementAndGet();
        isScanInProgress = false;
        scanChunkQueue.clear();
        pendingFoundBlocks.clear();
        pendingVisitedKeys.clear();
    }

    private static void ensureDepthBackupTarget(Minecraft mc) {
        RenderTarget main = mc.getMainRenderTarget();
        if (depthBackupTarget == null || depthBackupTarget.width != main.width || depthBackupTarget.height != main.height) {
            if (depthBackupTarget != null) {
                depthBackupTarget.destroyBuffers();
            }
            depthBackupTarget = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        }
    }

    private static void bindMainTarget(Minecraft mc) {
        RenderTarget main = mc.getMainRenderTarget();
        main.bindWrite(false);
        RenderSystem.viewport(0, 0, main.width, main.height);
    }

    private static final ExecutorService SCAN_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AdorableArmoryOreScan");
        thread.setDaemon(true);
        return thread;
    });

    private static boolean playerHeldItem(LocalPlayer player) {
        return OrePerspectiveCore.hasUsableActivator(player);
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        if (player == null || level == null) {
            clearScan();
            return;
        }

        boolean handItem = playerHeldItem(player);

        if (handItem) {
            fadeOutTicksRemaining = FADE_OUT_TICKS;
        } else {
            if (fadeOutTicksRemaining > 0) {
                fadeOutTicksRemaining--;
            }
        }

        if (!handItem) {
            if (fadeOutTicksRemaining <= 0) {
                boolean anyVisibleAlpha = false;
                if (!activeMeshes.isEmpty()) {
                    for (Float v : meshAlphaByKey.values()) {
                        if (v > ALPHA_EPS) {
                            anyVisibleAlpha = true;
                            break;
                        }
                    }
                }
                if (!anyVisibleAlpha) {
                    clearScan();
                }
            }
            return;
        }

        boolean movedTooFar = (lastScanCenterPos == null) || (player.blockPosition().distSqr(lastScanCenterPos) > RESCAN_MOVE_THRESHOLD_SQR);
        boolean needsNewScan;
        if (movedTooFar) {
            needsNewScan = true;
        } else if (scanCooling > 0) {
            scanCooling--;
            needsNewScan = false;
        } else {
            needsNewScan = true;
        }

        if (movedTooFar && isScanInProgress) {
            cancelInProgressScanOnly();
        }

        if (needsNewScan && !isScanInProgress && (lastScanFuture == null || lastScanFuture.isDone())) {
            beginScan(player.blockPosition(), level);
        }

        if (isScanInProgress) {
            processChunkScanQueue(level);
        }
    }

    private static void clearScan() {
        lastScanMeshes = Collections.emptyList();
        lastScanKeysCached = Collections.emptySet();
        lastFoundBlockSet = Collections.emptySet();

        activeMeshes.clear();
        meshAlphaByKey.clear();

        destroyDepthVbo();

        SCAN_GENERATION.incrementAndGet();

        if (isScanInProgress) {
            isScanInProgress = false;
            scanChunkQueue.clear();
            pendingFoundBlocks.clear();
            pendingVisitedKeys.clear();
        }
    }

    private static void destroyDepthVbo() {
        oreDepthVertexCount = 0;
        oreDepthBounds = null;
        VertexBuffer toClose = oreDepthVbo;
        if (toClose != null) {
            RenderSystem.recordRenderCall(() -> {
                try { toClose.close(); } catch (Throwable ignored) {}
            });
            oreDepthVbo = null;
        }
    }

    private static void beginScan(BlockPos center, ClientLevel level) {
        SCAN_GENERATION.incrementAndGet();
        isScanInProgress = true;
        scanCooling = SCAN_INTERVAL_TICKS;
        lastScanCenterPos = center;

        pendingFoundBlocks.clear();
        pendingVisitedKeys.clear();
        scanChunkQueue.clear();

        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - SCAN_RADIUS);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + SCAN_RADIUS);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - SCAN_RADIUS);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + SCAN_RADIUS);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (level.hasChunk(cx, cz)) {
                    scanChunkQueue.add(new ChunkPos(cx, cz));
                }
            }
        }
    }

    private static void processChunkScanQueue(ClientLevel level) {
        if (lastScanCenterPos == null) {
            clearScan();
            return;
        }

        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            if (scanChunkQueue.isEmpty()) {
                completeScan();
                return;
            }

            ChunkPos chunkPos = scanChunkQueue.poll();
            if (chunkPos == null) continue;

            if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
                continue;
            }

            LevelChunk chunk;
            try {
                chunk = level.getChunk(chunkPos.x, chunkPos.z);
            } catch (Exception e) {
                AdorableArmory.LOGGER.debug("Chunk skipped during ore scan: {}", chunkPos, e);
                continue;
            }

            final LevelChunkSection[] sections = chunk.getSections();
            if (sections.length == 0) continue;

            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];

                if (section == null || section.hasOnlyAir() || !section.getStates().maybeHas(IS_ORES)) {
                    continue;
                }

                int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
                int worldY = sectionY << 4;
                BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (IS_ORES.test(state)) {
                                blockPos.set(chunk.getPos().getMinBlockX() + x, worldY + y, chunk.getPos().getMinBlockZ() + z);

                                if (blockPos.distSqr(lastScanCenterPos) <= SCAN_RADIUS * SCAN_RADIUS) {
                                    long key = blockPos.asLong();
                                    if (pendingVisitedKeys.add(key)) {
                                        pendingFoundBlocks.add(key);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (scanChunkQueue.isEmpty()) {
            completeScan();
        }
    }

    private static void completeScan() {
        isScanInProgress = false;

        if (pendingFoundBlocks.isEmpty()) {
            lastScanMeshes = Collections.emptyList();
            lastFoundBlockSet = Collections.emptySet();
            lastScanKeysCached = Collections.emptySet();
            destroyDepthVbo();
            return;
        }

        final List<Long> snapshotFound = List.copyOf(pendingFoundBlocks);
        final int thisGen = SCAN_GENERATION.get();
        final ClientLevel scanLevelRef = Minecraft.getInstance().level;

        final BlockPos center = lastScanCenterPos;
        final int baseX = (center == null) ? 0 : align16(center.getX());
        final int baseY = (center == null) ? 0 : align16(center.getY());
        final int baseZ = (center == null) ? 0 : align16(center.getZ());

        CompletableFuture<?> scanFuture = lastScanFuture;
        if (scanFuture == null || scanFuture.isDone()) {
            lastScanFuture = CompletableFuture
                    .supplyAsync(() -> Clustering(snapshotFound), SCAN_EXECUTOR)
                    .thenApplyAsync(clusters -> buildMeshesFromClusters(clusters, baseX, baseY, baseZ), SCAN_EXECUTOR)
                    .thenAccept(meshes -> {
                        Minecraft mc = Minecraft.getInstance();
                        LocalPlayer player = mc.player;
                        if (thisGen != SCAN_GENERATION.get()) return;
                        if (player == null || !playerHeldItem(player)) return;
                        if (Minecraft.getInstance().level != scanLevelRef) return; // 避免换世界/换维度旧结果写回

                        final List<RenderMesh> safeMeshes = (meshes == null || meshes.isEmpty()) ? Collections.emptyList() : List.copyOf(meshes);
                        final HashSet<Long> keys = new HashSet<>(Math.max(16, safeMeshes.size() * 2));
                        for (RenderMesh m : safeMeshes) keys.add(computeMeshKey(m));

                        final Set<Long> foundSet = Set.copyOf(snapshotFound);
                        final float[] depthPositions = buildDepthTriangles(foundSet, baseX, baseY, baseZ);
                        final AABB boundsWorld = computeBoundsFromBlocks(foundSet);

                        // IMPORTANT: Commit everything on render thread as one atomic swap.
                        RenderSystem.recordRenderCall(() -> {
                            if (thisGen != SCAN_GENERATION.get()) return;

                            // If base changed (teleport / big move), drop old caches to avoid "old local coords + new base"
                            boolean baseChanged = (baseX != renderBaseX || baseY != renderBaseY || baseZ != renderBaseZ);
                            if (baseChanged) {
                                activeMeshes.clear();
                                meshAlphaByKey.clear();
                                lastMeshesEpoch = -1;
                                destroyDepthVbo();
                            }
                            renderBaseX = baseX; renderBaseY = baseY; renderBaseZ = baseZ;

                            uploadDepthVbo(depthPositions);
                            oreDepthBounds = boundsWorld;

                            lastScanMeshes = safeMeshes.isEmpty() ? Collections.emptyList() : safeMeshes;
                            lastScanKeysCached = safeMeshes.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(keys);
                            lastFoundBlockSet = foundSet;

                            MESH_EPOCH.incrementAndGet();
                        });
                    })
                    .exceptionally(ex -> {
                        AdorableArmory.LOGGER.warn("Ore scan pipeline failed", ex);
                        return null;
                    });
        }
    }

    private static List<Cluster> Clustering(List<Long> found) {
        if (found == null || found.isEmpty()) return Collections.emptyList();
        HashSet<Long> remaining = new HashSet<>(found);
        ArrayList<Cluster> clusters = new ArrayList<>(8);

        final int[][] ints = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

        Deque<Long> stack = new ArrayDeque<>();
        while (!remaining.isEmpty()) {
            Iterator<Long> iterator = remaining.iterator();
            long seed = iterator.next();
            iterator.remove();
            stack.clear();
            stack.push(seed);

            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            HashSet<Long> members = new HashSet<>();

            while (!stack.isEmpty()) {
                long cur = stack.pop();
                BlockPos p = BlockPos.of(cur);
                int px = p.getX(), py = p.getY(), pz = p.getZ();

                members.add(cur);
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                minZ = Math.min(minZ, pz);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
                maxZ = Math.max(maxZ, pz);

                for (int[] n : ints) {
                    long log = BlockPos.asLong(px + n[0], py + n[1], pz + n[2]);
                    if (remaining.remove(log)) {
                        stack.push(log);
                    }
                }
            }

            clusters.add(new Cluster(minX, minY, minZ, maxX, maxY, maxZ, members));
        }

        return Collections.unmodifiableList(clusters);
    }

    private static List<RenderMesh> buildMeshesFromClusters(List<Cluster> clusters, int baseX, int baseY, int baseZ) {
        if (clusters == null || clusters.isEmpty()) return Collections.emptyList();
        ArrayList<RenderMesh> meshes = new ArrayList<>(clusters.size());

        for (Cluster cluster : clusters) {
            Map<FacePlaneKey, HashSet<Long>> faceGroups = new HashMap<>();

            for (long log : cluster.blocks) {
                BlockPos pos = BlockPos.of(log);
                final int bx = pos.getX(), by = pos.getY(), bz = pos.getZ();
                if (cluster.isExterior(bx + 1, by, bz)) faceGroups.computeIfAbsent(new FacePlaneKey(0, bx + 1), k -> new HashSet<>()).add(encodePair(by, bz));
                if (cluster.isExterior(bx - 1, by, bz)) faceGroups.computeIfAbsent(new FacePlaneKey(1, bx), k -> new HashSet<>()).add(encodePair(by, bz));
                if (cluster.isExterior(bx, by + 1, bz)) faceGroups.computeIfAbsent(new FacePlaneKey(2, by + 1), k -> new HashSet<>()).add(encodePair(bx, bz));
                if (cluster.isExterior(bx, by - 1, bz)) faceGroups.computeIfAbsent(new FacePlaneKey(3, by), k -> new HashSet<>()).add(encodePair(bx, bz));
                if (cluster.isExterior(bx, by, bz + 1)) faceGroups.computeIfAbsent(new FacePlaneKey(4, bz + 1), k -> new HashSet<>()).add(encodePair(bx, by));
                if (cluster.isExterior(bx, by, bz - 1)) faceGroups.computeIfAbsent(new FacePlaneKey(5, bz), k -> new HashSet<>()).add(encodePair(bx, by));
            }

            List<Map.Entry<FacePlaneKey, HashSet<Long>>> faces = new ArrayList<>(faceGroups.entrySet());
            faces.sort(Map.Entry.comparingByKey());

            for (Map.Entry<FacePlaneKey, HashSet<Long>> e : faces) {
                List<List<IPoint>> polys = buildPolyLinesFromFaceCells(e.getValue());
                for (List<IPoint> poly : polys) {
                    List<IPoint> simple = simplifyPolyline(poly);
                    if (simple == null || simple.size() < 2) continue;

                    int size = simple.size();
                    for (int i = 0; i < size; i++) {
                        IPoint p0 = simple.get(i);
                        IPoint p1 = (i == size - 1) ? simple.get(0) : simple.get(i + 1);
                        RenderMesh segmentMesh = buildSegmentMesh(e.getKey().faceId, e.getKey().plane, p0, p1, baseX, baseY, baseZ);
                        if (segmentMesh != null) meshes.add(segmentMesh);
                    }
                }
            }
        }
        return meshes;
    }

    private static List<List<IPoint>> buildPolyLinesFromFaceCells(HashSet<Long> cells) {
        if (cells == null || cells.isEmpty()) return Collections.emptyList();

        TreeSet<EdgeKey> edgeSet = new TreeSet<>();
        for (long log : cells) {
            int a = (int)(log >> 32);
            int b = (int)(log & 0xFFFFFFFFL);

            EdgeKey e1 = EdgeKey.of(a, b, a + 1, b); // bottom
            EdgeKey e2 = EdgeKey.of(a + 1, b, a + 1, b + 1); // right
            EdgeKey e3 = EdgeKey.of(a + 1, b + 1, a, b + 1); // top
            EdgeKey e4 = EdgeKey.of(a, b + 1, a, b); // left

            if (!edgeSet.remove(e1)) edgeSet.add(e1);
            if (!edgeSet.remove(e2)) edgeSet.add(e2);
            if (!edgeSet.remove(e3)) edgeSet.add(e3);
            if (!edgeSet.remove(e4)) edgeSet.add(e4);
        }
        if (edgeSet.isEmpty()) return Collections.emptyList();

        TreeMap<IPoint, ArrayList<IPoint>> treeMap = new TreeMap<>();
        for (EdgeKey ek : edgeSet) {
            IPoint p1 = new IPoint(ek.x1, ek.y1);
            IPoint p2 = new IPoint(ek.x2, ek.y2);
            treeMap.computeIfAbsent(p1, k -> new ArrayList<>()).add(p2);
            treeMap.computeIfAbsent(p2, k -> new ArrayList<>()).add(p1);
        }
        for (ArrayList<IPoint> list : treeMap.values()) {
            list.sort(Comparator.naturalOrder());
        }

        TreeSet<EdgeKey> remaining = new TreeSet<>(edgeSet);
        ArrayList<List<IPoint>> polyline = new ArrayList<>();

        while (!remaining.isEmpty()) {
            EdgeKey startEdge = remaining.first();
            IPoint start = new IPoint(startEdge.x1, startEdge.y1);

            ArrayList<IPoint> poly = new ArrayList<>();
            IPoint prev = null;
            IPoint cur = start;

            int safety = 0;
            final int SAFETY_LIMIT = remaining.size() * 4 + 16;

            while (true) {
                poly.add(cur);
                ArrayList<IPoint> neighbors = treeMap.get(cur);
                if (neighbors == null || neighbors.isEmpty()) break;

                IPoint next = null;

                for (IPoint point : neighbors) {
                    if (point.equals(prev)) continue;
                    EdgeKey ek = EdgeKey.of(cur.x, cur.y, point.x, point.y);
                    if (remaining.contains(ek)) { next = point; break; }
                }
                if (next == null) {

                    for (IPoint point : neighbors) {
                        EdgeKey ek = EdgeKey.of(cur.x, cur.y, point.x, point.y);
                        if (remaining.contains(ek)) { next = point; break; }
                    }
                }
                if (next == null) break;

                remaining.remove(EdgeKey.of(cur.x, cur.y, next.x, next.y));
                prev = cur;
                cur  = next;

                safety++;
                if (safety > SAFETY_LIMIT || cur.equals(start)) break;
            }

            if (!poly.isEmpty()) {

                if (!cur.equals(start)) {
                    EdgeKey back = EdgeKey.of(cur.x, cur.y, start.x, start.y);
                    if (remaining.remove(back)) {
                        poly.add(cur);
                    }
                }

                if (poly.size() >= 2 && poly.get(0).equals(poly.get(poly.size() - 1))) {
                    poly.remove(poly.size() - 1);
                }
                if (poly.size() >= 2) {

                    int minIdx = 0;
                    for (int i = 1; i < poly.size(); i++) {
                        if (poly.get(i).compareTo(poly.get(minIdx)) < 0) minIdx = i;
                    }
                    if (minIdx != 0) {
                        ArrayList<IPoint> rotated = new ArrayList<>(poly.size());
                        for (int i = 0; i < poly.size(); i++) {
                            rotated.add(poly.get((minIdx + i) % poly.size()));
                        }
                        poly = rotated;
                    }
                    polyline.add(poly);
                }
            }
        }

        polyline.sort(Comparator.comparing((List<IPoint> p) -> p.get(0)));
        return polyline;
    }

    private static List<IPoint> simplifyPolyline(List<IPoint> poly) {
        if (poly == null || poly.size() < 3) return poly;
        int size = poly.size();
        ArrayList<IPoint> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            IPoint prev = poly.get((i - 1 + size) % size);
            IPoint cur = poly.get(i);
            IPoint next = poly.get((i + 1) % size);
            long vx1 = cur.x - prev.x;
            long vy1 = cur.y - prev.y;
            long vx2 = next.x - cur.x;
            long vy2 = next.y - cur.y;
            long cross = vx1 * vy2 - vy1 * vx2;
            if (cross != 0) {
                out.add(cur);
            }
        }
        if (out.size() < 3) return poly;
        return out;
    }

    private static RenderMesh buildSegmentMesh(int faceId, int plane, IPoint p0, IPoint p1, int baseX, int baseY, int baseZ) {
        final double half = LINE_WIDTH * 0.02f;

        final double sXw, sYw, sZw, eXw, eYw, eZw;
        switch (faceId) {
            case 0, 1 -> { // ±X
                sXw = plane; sYw = p0.x; sZw = p0.y;
                eXw = plane; eYw = p1.x; eZw = p1.y;
            }
            case 2, 3 -> { // ±Y
                sYw = plane; sXw = p0.x; sZw = p0.y;
                eYw = plane; eXw = p1.x; eZw = p1.y;
            }
            default -> { // ±Z
                sZw = plane; sXw = p0.x; sYw = p0.y;
                eZw = plane; eXw = p1.x; eYw = p1.y;
            }
        }

        double dirX = eXw - sXw, dirY = eYw - sYw, dirZ = eZw - sZw;
        double segLen = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (segLen <= 1e-9) return null;

        float[] n = normalForFace(faceId);
        double nX = n[0], nY = n[1], nZ = n[2];

        double tX = dirX / segLen, tY = dirY / segLen, tZ = dirZ / segLen;
        double perpX = nY * tZ - nZ * tY;
        double perpY = nZ * tX - nX * tZ;
        double perpZ = nX * tY - nY * tX;

        double sX = sXw - baseX, sY = sYw - baseY, sZ = sZw - baseZ;
        double eX = eXw - baseX, eY = eYw - baseY, eZ = eZw - baseZ;

        double v0X = sX + perpX * half, v0Y = sY + perpY * half, v0Z = sZ + perpZ * half;
        double v1X = sX - perpX * half, v1Y = sY - perpY * half, v1Z = sZ - perpZ * half;
        double v2X = eX - perpX * half, v2Y = eY - perpY * half, v2Z = eZ - perpZ * half;
        double v3X = eX + perpX * half, v3Y = eY + perpY * half, v3Z = eZ + perpZ * half;

        float[] positions = new float[] {
                (float)v0X, (float)v0Y, (float)v0Z,
                (float)v1X, (float)v1Y, (float)v1Z,
                (float)v2X, (float)v2Y, (float)v2Z,

                (float)v2X, (float)v2Y, (float)v2Z,
                (float)v3X, (float)v3Y, (float)v3Z,
                (float)v0X, (float)v0Y, (float)v0Z
        };

        double w0X = sXw + perpX * half, w0Y = sYw + perpY * half, w0Z = sZw + perpZ * half;
        double w1X = sXw - perpX * half, w1Y = sYw - perpY * half, w1Z = sZw - perpZ * half;
        double w2X = eXw - perpX * half, w2Y = eYw - perpY * half, w2Z = eZw - perpZ * half;
        double w3X = eXw + perpX * half, w3Y = eYw + perpY * half, w3Z = eZw + perpZ * half;

        double minXf = Math.min(Math.min(w0X, w1X), Math.min(w2X, w3X));
        double minYf = Math.min(Math.min(w0Y, w1Y), Math.min(w2Y, w3Y));
        double minZf = Math.min(Math.min(w0Z, w1Z), Math.min(w2Z, w3Z));
        double maxXf = Math.max(Math.max(w0X, w1X), Math.max(w2X, w3X));
        double maxYf = Math.max(Math.max(w0Y, w1Y), Math.max(w2Y, w3Y));
        double maxZf = Math.max(Math.max(w0Z, w1Z), Math.max(w2Z, w3Z));

        int minX = (int)Math.floor(minXf), minY = (int)Math.floor(minYf), minZ = (int)Math.floor(minZf);
        int maxX = (int)Math.ceil (maxXf), maxY = (int)Math.ceil (maxYf), maxZ = (int)Math.ceil (maxZf);

        AABB box = new AABB(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
        return new RenderMesh(minX, minY, minZ, maxX, maxY, maxZ, positions, faceId, plane, p0.x, p0.y, p1.x, p1.y, box);
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        boolean handItem = playerHeldItem(mc.player);
        if (activeMeshes.isEmpty() && !handItem) return;

        ensureDepthBackupTarget(mc);

        RenderTarget main = mc.getMainRenderTarget();
        bindMainTarget(mc);
        depthBackupTarget.copyDepthFrom(main);
        bindMainTarget(mc);

        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);

        try {
            Camera camera = mc.gameRenderer.getMainCamera();
            Frustum frustum = event.getFrustum();

            PoseStack poseStack = event.getPoseStack();
            double camX = camera.getPosition().x;
            double camY = camera.getPosition().y;
            double camZ = camera.getPosition().z;

            // Teleport / base mismatch guard: avoid a single frame of huge float translation
            if (Math.abs(camX - renderBaseX) > BASE_TOO_FAR || Math.abs(camY - renderBaseY) > BASE_TOO_FAR || Math.abs(camZ - renderBaseZ) > BASE_TOO_FAR) {
                //clearScan();
                return;
            }

            poseStack.pushPose();
            // Precision fix: translate by (base - camera), which is small and float-safe
            poseStack.translate(renderBaseX - camX, renderBaseY - camY, renderBaseZ - camZ);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.clearDepth(1.0D);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false);

            drawPresetOreDepth(poseStack, event.getProjectionMatrix(), frustum);

            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            var consumer = buffers.getBuffer(ContourLineRenderType.oreLinesZTest());

            final int globalEpoch = MESH_EPOCH.get();
            if (globalEpoch != lastMeshesEpoch) {
                for (RenderMesh mesh : lastScanMeshes) {
                    activeMeshes.put(computeMeshKey(mesh), mesh);
                }
                lastMeshesEpoch = globalEpoch;
            }

            final Set<Long> keysFromLastScan = lastScanKeysCached;
            final float smoothingFactor = timeSmoothingFactor();

            List<Long> toRemove = new ArrayList<>(8);

            for (Map.Entry<Long, RenderMesh> entry : activeMeshes.entrySet()) {
                long key = entry.getKey();
                RenderMesh mesh = entry.getValue();

                boolean stillExists = keysFromLastScan.contains(key);
                boolean inFrustum = mesh.isApproxVisible(frustum);

                double distSq = mesh.distanceSqTo(camX, camY, camZ);
                float distanceTargetAlpha = (float) (1.0 - Math.min(1.0, Math.sqrt(distSq) / SCAN_RADIUS));
                if (distanceTargetAlpha < 0f) distanceTargetAlpha = 0f;

                float targetAlpha = (handItem && stillExists) ? distanceTargetAlpha : 0f;

                float current = meshAlphaByKey.getOrDefault(key, 0f);
                float newAlpha = leap(current, targetAlpha, smoothingFactor);

                if (newAlpha <= ALPHA_EPS && targetAlpha == 0f) {
                    toRemove.add(key);
                    continue;
                }

                meshAlphaByKey.put(key, newAlpha);

                if (inFrustum && newAlpha > ALPHA_EPS) {
                    float[] arr = mesh.positions;
                    PoseStack.Pose last = poseStack.last();
                    Matrix4f matrix4f = last.pose();

                    for (int i = 0; i < arr.length; i += 3) {
                        consumer.vertex(matrix4f, arr[i], arr[i + 1], arr[i + 2]).color(ORES_R, ORES_G, ORES_B, newAlpha).endVertex();
                    }
                }
            }

            for (Long k : toRemove) {
                activeMeshes.remove(k);
                meshAlphaByKey.remove(k);
            }

            buffers.endBatch(ContourLineRenderType.oreLinesZTest());
            RenderSystem.disableBlend();
            poseStack.popPose();
        } finally {
            bindMainTarget(mc);
            main.copyDepthFrom(depthBackupTarget);
            bindMainTarget(mc);

            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
        }
    }

    private static void drawPresetOreDepth(PoseStack poseStack, Matrix4f projection, Frustum frustum) {
        VertexBuffer vbo = oreDepthVbo;
        if (vbo != null && oreDepthVertexCount > 0) {
            if (oreDepthBounds != null && !frustum.isVisible(oreDepthBounds)) {
                return;
            }

            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();

            RenderSystem.setShader(GameRenderer::getPositionShader);
            ShaderInstance shader = GameRenderer.getPositionShader();

            if (shader != null) {
                vbo.bind();
                vbo.drawWithShader(poseStack.last().pose(), projection, shader);
                VertexBuffer.unbind();
            }

            RenderSystem.enableCull();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        drawOreDepth(buffers, poseStack);
        buffers.endBatch(ContourLineRenderType.oreDepth());
    }

    private static float timeSmoothingFactor() {
        long nanoTime = System.nanoTime();
        float dt = (nanoTime - lastFrameNs) / 1_000_000_000f;

        final float MAX_DT = 0.05f; // 50 ms
        if (dt > MAX_DT) dt = MAX_DT;

        lastFrameNs = nanoTime;
        float k = 1f - (float) Math.exp(-dt / ALPHA_TAU_SEC);

        final float MIN_K = 0.02f;
        final float MAX_K = 0.35f; // 35%
        if (k < MIN_K) k = MIN_K;
        if (k > MAX_K) k = MAX_K;
        return k;
    }

    private static float leap(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static long computeMeshKey(RenderMesh mesh) {
        final long FNV_OFFSET = 1469598103934665603L, FNV_PRIME = 1099511628211L;
        int ax = mesh.aX, ay = mesh.aY, bx = mesh.bX, by = mesh.bY;
        if (bx < ax || (bx == ax && by < ay)) {
            int tx = ax, ty = ay; ax = bx; ay = by; bx = tx; by = ty;
        }
        long h = FNV_OFFSET;
        int[] v = { mesh.faceId, mesh.plane, ax, ay, bx, by };
        for (int x : v) {
            h ^= (x) & 0xFFL; h *= FNV_PRIME;
            h ^= (x >>> 8) & 0xFFL; h *= FNV_PRIME;
            h ^= (x >>>16) & 0xFFL; h *= FNV_PRIME;
            h ^= (x >>>24) & 0xFFL; h *= FNV_PRIME;
        }
        return h;
    }

    private static float[] normalForFace(int faceId) {
        return switch (faceId) {
            case 0 -> new float[]{1f, 0f, 0f}; // +X
            case 1 -> new float[]{-1f, 0f, 0f}; // -X
            case 2 -> new float[]{0f, 1f, 0f}; // +Y
            case 3 -> new float[]{0f, -1f, 0f}; // -Y
            case 4 -> new float[]{0f, 0f, 1f}; // +Z
            case 5 -> new float[]{0f, 0f, -1f}; // -Z
            default -> new float[]{0f, 0f, 0f};
        };
    }

    private static long encodePair(int a, int b) {
        return ((long)a << 32) | (b & 0xFFFFFFFFL);
    }

    private record RenderMesh(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, float[] positions, int faceId, int plane, int aX, int aY, int bX, int bY, AABB box) {
        double distanceSqTo(double px, double py, double pz) {
            double dx = Math.max(minX - px, Math.max(0, px - maxX));
            double dy = Math.max(minY - py, Math.max(0, py - maxY));
            double dz = Math.max(minZ - pz, Math.max(0, pz - maxZ));
            return dx * dx + dy * dy + dz * dz;
        }
        boolean isApproxVisible(Frustum frustum) {
            return frustum.isVisible(box);
        }
    }

    private record FacePlaneKey(int faceId, int plane) implements Comparable<FacePlaneKey> {
        @Override public int compareTo(FacePlaneKey planeKey) {
            int compare = Integer.compare(this.faceId, planeKey.faceId);
            if (compare != 0) return compare;
            return Integer.compare(this.plane, planeKey.plane);
        }
    }

    private record Cluster(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, HashSet<Long> blocks) {
        boolean isExterior(int x, int y, int z) {
            return !blocks.contains(BlockPos.asLong(x, y, z));
        }
    }

    private record IPoint(int x, int y) implements Comparable<IPoint> {
        @Override public int compareTo(IPoint point) {
            int compare = Integer.compare(x, point.x);
            return (compare != 0) ? compare : Integer.compare(y, point.y);
        }
    }

    private record EdgeKey(int x1, int y1, int x2, int y2) implements Comparable<EdgeKey> {
        static EdgeKey of(int ax, int ay, int bx, int by) {
            return (ax < bx || (ax == bx && ay <= by)) ? new EdgeKey(ax, ay, bx, by) : new EdgeKey(bx, by, ax, ay);
        }
        @Override public int compareTo(EdgeKey key) {
            int compare = Integer.compare(this.x1, key.x1);
            if (compare != 0) return compare;
            compare = Integer.compare(this.y1, key.y1);
            if (compare != 0) return compare;
            compare = Integer.compare(this.x2, key.x2);
            if (compare != 0) return compare;
            return Integer.compare(this.y2, key.y2);
        }
    }

    private static void pushQuad(FloatArray out, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
        out.add(x1); out.add(y1); out.add(z1);
        out.add(x2); out.add(y2); out.add(z2);
        out.add(x3); out.add(y3); out.add(z3);

        out.add(x3); out.add(y3); out.add(z3);
        out.add(x4); out.add(y4); out.add(z4);
        out.add(x1); out.add(y1); out.add(z1);
    }

    private static void emitQuadDepth(VertexConsumer consumer, Matrix4f matrix4f, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
        consumer.vertex(matrix4f, x1,y1,z1).endVertex();
        consumer.vertex(matrix4f, x2,y2,z2).endVertex();
        consumer.vertex(matrix4f, x3,y3,z3).endVertex();
        consumer.vertex(matrix4f, x3,y3,z3).endVertex();
        consumer.vertex(matrix4f, x4,y4,z4).endVertex();
        consumer.vertex(matrix4f, x1,y1,z1).endVertex();
    }

    private static void drawOreDepth(MultiBufferSource.BufferSource buffers, PoseStack poseStack) {
        if (lastFoundBlockSet.isEmpty()) return;

        Matrix4f matrix4f = poseStack.last().pose();
        var buffer = buffers.getBuffer(ContourLineRenderType.oreDepth());

        for (long log : lastFoundBlockSet) {
            BlockPos pos = BlockPos.of(log);
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();

            // Precision fix: emit LOCAL vertices (relative to base)
            int lx = x - renderBaseX;
            int ly = y - renderBaseY;
            int lz = z - renderBaseZ;

            // +X Face
            if (!lastFoundBlockSet.contains(BlockPos.asLong(x + 1, y, z))) {
                emitQuadDepth(buffer, matrix4f, lx + 1,ly, lz, lx + 1, ly,lz + 1, lx + 1,ly + 1,lz + 1, lx + 1, ly + 1, lz);
            }
            // -X Face
            if (!lastFoundBlockSet.contains(BlockPos.asLong(x - 1, y, z))) {
                emitQuadDepth(buffer, matrix4f, lx, ly, lz, lx,ly + 1, lz, lx,ly + 1,lz + 1, lx, ly,lz + 1);
            }
            // +Y Face
            if (!lastFoundBlockSet.contains(BlockPos.asLong(x,y + 1, z))) {
                emitQuadDepth(buffer, matrix4f, lx, ly + 1, lz, lx + 1,ly + 1, lz, lx + 1,ly + 1, lz + 1, lx, ly + 1, lz + 1);
            }
            // -Y Face
            if (!lastFoundBlockSet.contains(BlockPos.asLong(x,y - 1, z))) {
                emitQuadDepth(buffer, matrix4f, lx, ly, lz, lx, ly,lz + 1, lx + 1,ly,lz + 1, lx + 1, ly, lz);
            }
            // +Z Face
            if (!lastFoundBlockSet.contains(BlockPos.asLong(x, y,z + 1))) {
                emitQuadDepth(buffer, matrix4f, lx, ly,lz + 1, lx + 1,ly,lz + 1, lx + 1,ly + 1, lz + 1, lx, ly + 1, lz + 1);
            }
            // -Z Face
            if (!lastFoundBlockSet.contains(BlockPos.asLong(x, y,z - 1))) {
                emitQuadDepth(buffer, matrix4f, lx, ly, lz, lx,ly + 1, lz, lx + 1,ly + 1, lz, lx + 1, ly, lz);
            }
        }
    }

    private static void uploadDepthVbo(float[] positions) {
        destroyDepthVbo();
        if (positions == null || positions.length == 0) {
            oreDepthVbo = null;
            oreDepthVertexCount = 0;
            return;
        }

        BufferBuilder builder = new BufferBuilder(positions.length * 4);
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        for (int i = 0; i < positions.length; i += 3) {
            builder.vertex(positions[i], positions[i + 1], positions[i + 2]).endVertex();
        }

        BufferBuilder.RenderedBuffer rendered = builder.end();
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(rendered);
        VertexBuffer.unbind();

        oreDepthVbo = vbo;
        oreDepthVertexCount = positions.length / 3;
    }

    private static AABB computeBoundsFromBlocks(Set<Long> blocks) {
        if (blocks == null || blocks.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long log : blocks) {
            BlockPos p = BlockPos.of(log);
            int x = p.getX(), y = p.getY(), z = p.getZ();
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private static float[] buildDepthTriangles(Set<Long> blocks, int baseX, int baseY, int baseZ) {
        if (blocks == null || blocks.isEmpty()) return new float[0];

        long exposedFaces = 0;
        for (long log : blocks) {
            BlockPos p = BlockPos.of(log);
            int x = p.getX(), y = p.getY(), z = p.getZ();

            if (!blocks.contains(BlockPos.asLong(x + 1, y, z))) exposedFaces++;
            if (!blocks.contains(BlockPos.asLong(x - 1, y, z))) exposedFaces++;
            if (!blocks.contains(BlockPos.asLong(x, y + 1, z))) exposedFaces++;
            if (!blocks.contains(BlockPos.asLong(x, y - 1, z))) exposedFaces++;
            if (!blocks.contains(BlockPos.asLong(x, y, z + 1))) exposedFaces++;
            if (!blocks.contains(BlockPos.asLong(x, y, z - 1))) exposedFaces++;
        }

        long needed = exposedFaces * 18L;
        if (needed > Integer.MAX_VALUE) {
            return new float[0];
        }
        FloatArray out = new FloatArray((int) needed);

        for (long log : blocks) {
            BlockPos pos = BlockPos.of(log);
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();

            // Precision fix: use LOCAL coords for VBO
            int lx = x - baseX;
            int ly = y - baseY;
            int lz = z - baseZ;

            if (!blocks.contains(BlockPos.asLong(x + 1, y, z))) {
                pushQuad(out, lx + 1,ly, lz, lx + 1, ly,lz + 1, lx + 1,ly + 1,lz + 1, lx + 1, ly + 1, lz);
            }
            if (!blocks.contains(BlockPos.asLong(x - 1, y, z))) {
                pushQuad(out, lx, ly, lz, lx,ly + 1, lz, lx,ly + 1,lz +1, lx, ly,lz + 1);
            }
            if (!blocks.contains(BlockPos.asLong(x,y + 1, z))) {
                pushQuad(out, lx, ly + 1, lz, lx + 1,ly + 1, lz, lx + 1,ly + 1, lz + 1, lx, ly + 1, lz + 1);
            }
            if (!blocks.contains(BlockPos.asLong(x,y - 1, z))) {
                pushQuad(out, lx, ly, lz, lx, ly,lz+1,  lx+1,ly  ,lz+1,  lx+1,ly  ,lz  );
            }
            if (!blocks.contains(BlockPos.asLong(x, y,z + 1))) {
                pushQuad(out, lx, ly,lz + 1, lx + 1,ly,lz + 1, lx + 1,ly + 1, lz + 1, lx, ly + 1, lz + 1);
            }
            if (!blocks.contains(BlockPos.asLong(x, y,z - 1))) {
                pushQuad(out, lx, ly, lz, lx,ly + 1, lz, lx + 1,ly + 1, lz, lx + 1, ly, lz);
            }
        }
        return out.toArray();
    }

    private static final class FloatArray {
        private float[] data;
        private int size;
        FloatArray(int cap) { data = new float[cap]; }
        void add(float v) {
            if (size == data.length) data = Arrays.copyOf(data, Math.max(16, data.length * 2));
            data[size++] = v;
        }
        float[] toArray() {
            return (size == data.length) ? data : Arrays.copyOf(data, size);
        }
    }

    public static class ContourLineRenderType extends RenderType {
        private static final RenderType ORE_DEPTH = create("ore_depth",
                DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES, 256, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_SHADER)
                        .setLayeringState(NO_LAYERING)
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setOutputState(MAIN_TARGET)
                        .setWriteMaskState(DEPTH_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .createCompositeState(false));

        private static final RenderType ORE_LINES_Z_TEST = create("ore_lines_z_test",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 256, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(MAIN_TARGET)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .createCompositeState(false));

        public static RenderType oreDepth() {
            return ORE_DEPTH;
        }

        public static RenderType oreLinesZTest() {
            return ORE_LINES_Z_TEST;
        }

        public ContourLineRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }
    }
}
